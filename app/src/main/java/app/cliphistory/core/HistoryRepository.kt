package app.cliphistory.core

import java.io.IOException
import java.security.SecureRandom

/** Single-writer repository. Call only from the daemon's serial worker. */
class HistoryRepository(private val a: Slot, private val b: Slot,
    private val clock: HistoryClock = HistoryClock { HistoryTime(-1,0) },
    private val sensitivityChanged: ((List<Entry>) -> Unit)? = null) {
    private data class PendingDelete(val entry: Entry, val token: Long)
    enum class UndoResult { RESTORED, ALREADY_PRESENT }
    private var pendingDelete: PendingDelete? = null
    val undoToken: Long get() = pendingDelete?.takeUnless { it.entry.expired(clock.now()) }?.token ?: 0
    val undoEntryId: Long get() = pendingDelete?.takeUnless { it.entry.expired(clock.now()) }?.entry?.id ?: -1
    private var cleanupPending=false
    private val slots = arrayOf(a, b)
    private var active = -1
    var recoveredFromDamagedSlot = false; private set
    var state: Snapshot; private set
    val available: Snapshot get() = state.available(clock.now())
    val sensitiveEntries: List<Entry> get() = state.entries.filter { it.sensitive } + listOfNotNull(pendingDelete?.entry?.takeIf { it.sensitive })

    init { state = loadState() }

    private fun loadState(): Snapshot {
        var sawNonempty = false
        var damaged = false
        val valid = ArrayList<Pair<Int, Snapshot>>()
        for ((index, slot) in slots.withIndex()) {
            // An unreadable slot is not silently treated as empty: preserve evidence and stop.
            val bytes = try { slot.read() } catch (_: StoreException) {
                sawNonempty = true; damaged = true; continue
            }
            if (bytes.isNotEmpty()) {
                sawNonempty = true
                try { valid.add(index to SnapshotCodec.decode(bytes)) } catch (_: IOException) { damaged = true }
            }
        }
        if (valid.isEmpty()) {
            if (sawNonempty) throw StoreException("BOTH_SNAPSHOTS_UNREADABLE")
            active = -1
            return Snapshot()
        }
        val newest = valid.maxBy { it.second.generation }
        active = newest.first
        recoveredFromDamagedSlot = damaged
        return newest.second
    }

    /** Forces an actual descriptor write and fsync before startup may report ready. */
    fun initialize(forceMirror: Boolean = false) {
        val migrated = state.available(clock.now()).uniqueProjection()
        commit(migrated, mirror = forceMirror || migrated.entries != state.entries)
    }

    fun capture(text: String, time: Long, sensitive: Boolean = false, capturedAt: HistoryTime? = null, inheritedExpiry: Expiry? = null): CaptureResult {
        val observed=capturedAt?:clock.now()
        val inherited=sensitiveMatch(sensitiveEntries,text,observed)
        expire()
        if (state.paused) return CaptureResult.PAUSED
        if (sensitive && !state.captureSensitive) return CaptureResult.SENSITIVE
        if (text.isEmpty()) return CaptureResult.EMPTY
        if (text.length > MAX_TEXT_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_TEXT_BYTES) return CaptureResult.TOO_LARGE
        if (String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8) != text) return CaptureResult.INVALID_TEXT
        val now=clock.now()
        val marked=sensitive || inherited!=null || inheritedExpiry!=null
        if(marked && !state.captureSensitive)return CaptureResult.SENSITIVE
        val fresh=if(marked && state.autoDeleteSensitive) {
            val captured=observed
            if(captured.boot!=now.boot)throw StoreException("BOOT_IDENTITY_UNAVAILABLE")
            if(captured.elapsed<0 || captured.elapsed>now.elapsed)throw StoreException("INVALID_CAPTURE_TIME")
            sensitiveDeadline(captured,state.sensitiveMinutes)
        } else null
        // Queue backlog must not extend retention or save an event already expired.
        val expiry=if(state.autoDeleteSensitive)listOfNotNull(inherited?.expiry,inheritedExpiry,fresh).minByOrNull { it.elapsed } else null
        if(expiry?.expired(now)==true)return CaptureResult.SENSITIVE
        val promoted=state.entries.map { if(marked && it.text==text)it.copy(sensitive=true,expiry=expiry) else it }
        val promotedUndo=pendingDelete?.let { if(marked && it.entry.text==text)it.copy(entry=it.entry.copy(sensitive=true,expiry=expiry)) else it }
        if(state.entries.firstOrNull()?.text==text) {
            if(promoted!=state.entries || promotedUndo!=pendingDelete)commit(state.copy(entries=promoted),mirror=true) { pendingDelete=promotedUndo }
            return CaptureResult.DUPLICATE
        }
        if (state.nextId == Long.MAX_VALUE) throw StoreException("ID_EXHAUSTED")
        val entry = Entry(state.nextId, time.coerceAtLeast(0), text,marked,expiry)
        val retained = if (state.duplicateMode == DuplicateMode.UNIQUE_TEXT)
            promoted.filterNot { it.text == text } else promoted
        val evictedSensitive=retained.drop(state.limit-1).any { it.sensitive }
        commit(state.copy(nextId = state.nextId + 1, entries = (listOf(entry) + retained).take(state.limit)),mirror=marked || evictedSensitive) { pendingDelete=promotedUndo }
        return CaptureResult.SAVED
    }

    fun setLimit(limit: Int) {
        expire()
        if (limit !in MIN_LIMIT..MAX_LIMIT) throw StoreException("LIMIT_MUST_BE_20_TO_500")
        val shrunk = state.entries.size > limit
        commit(state.copy(limit = limit, entries = state.entries.take(limit)), mirror = shrunk)
    }
    fun setPaused(paused: Boolean) { expire();commit(state.copy(paused = paused), mirror = true) }
    fun setDuplicateMode(mode: DuplicateMode) {
        expire()
        // A retry must clean both slots even when the first durable write already
        // changed the selected mode and only the backup cleanup failed.
        commit(state.copy(duplicateMode = mode).uniqueProjection(), mirror = true)
    }
    fun delete(id: Long): Boolean {
        expire()
        val entry = state.entries.firstOrNull { it.id == id } ?: return false
        val pending = PendingDelete(entry, newUndoToken())
        commit(state.copy(entries = state.entries.filterNot { it.id == id }), mirror = true) {
            pendingDelete = pending
        }
        return true
    }
    fun undoDelete(token: Long): UndoResult {
        expire()
        val pending = requirePendingDelete(token)
        if (state.duplicateMode == DuplicateMode.UNIQUE_TEXT && state.entries.any { it.text == pending.entry.text }) {
            pendingDelete = null
            return UndoResult.ALREADY_PRESENT
        }
        if (state.entries.size >= state.limit) throw StoreException("UNDO_HISTORY_FULL")
        val restored = (state.entries + pending.entry).sortedByDescending { it.id }
        commit(state.copy(entries = restored), mirror = true) { pendingDelete = null }
        return UndoResult.RESTORED
    }
    fun dismissUndo(token: Long) { requirePendingDelete(token); pendingDelete = null }
    private fun requirePendingDelete(token: Long): PendingDelete = pendingDelete?.takeIf { it.token == token && !it.entry.expired(clock.now()) }
        ?: throw StoreException("UNDO_NOT_AVAILABLE")
    private fun newUndoToken(): Long {
        var token: Long
        do { token = undoTokens.nextLong() and Long.MAX_VALUE } while (token == 0L || token == undoToken)
        return token
    }
    fun clear() { commit(state.copy(entries = emptyList()), mirror = true) { pendingDelete = null } }
    fun search(query: String): List<Entry> = available.entries.let { entries -> if(query.isEmpty())entries else entries.filter { it.text.contains(query,ignoreCase=true) } }
    fun entry(id:Long):Entry? = available.entries.firstOrNull { it.id==id }
    fun expire(retryCleanup: Boolean = false): Boolean {
        val now=clock.now()
        val undoExpired=pendingDelete?.entry?.expired(now)==true
        if(undoExpired)pendingDelete=null
        val kept=state.entries.filterNot { it.expired(now) }
        if(kept!=state.entries || (retryCleanup && cleanupPending)) {
            commit(state.copy(entries=kept),mirror=true)
            return true
        }
        return undoExpired
    }
    fun expiryDelay():Long? {
        if(cleanupPending)return 1000
        val now=clock.now()
        return (state.entries+listOfNotNull(pendingDelete?.entry)).mapNotNull { it.expiry }
            .minOfOrNull { if(it.expired(now))0L else (it.elapsed-now.elapsed).coerceAtLeast(0) }
    }
    fun setSensitivePolicy(capture:Boolean,autoDelete:Boolean,minutes:Int) {
        if(minutes !in 1..1440)throw StoreException("SENSITIVE_TIMER_MUST_BE_1_TO_1440")
        expire()
        val now=clock.now()
        val deadline=if(capture && autoDelete && (state.entries.any { it.sensitive } || pendingDelete?.entry?.sensitive==true))
            sensitiveDeadline(now,minutes) else null
        fun update(entry:Entry):Entry? = when {
            !entry.sensitive -> entry
            !capture -> null
            !autoDelete -> entry.copy(expiry=null)
            else -> entry.copy(expiry=listOfNotNull(entry.expiry,deadline).minByOrNull { it.elapsed })
        }
        val pending=pendingDelete?.let { value -> update(value.entry)?.let { value.copy(entry=it) } }
        commit(state.copy(captureSensitive=capture,autoDeleteSensitive=autoDelete,sensitiveMinutes=minutes,
            entries=state.entries.mapNotNull(::update)),mirror=true) { pendingDelete=pending }
    }

    private fun commit(candidate: Snapshot, mirror: Boolean = false, onDurable: () -> Unit = {}) {
        if (state.generation == Long.MAX_VALUE) throw StoreException("GENERATION_EXHAUSTED")
        val next = candidate.copy(generation = state.generation + 1)
        val encoded = SnapshotCodec.encode(next)
        val target = if (active == 0) 1 else 0
        slots[target].writeAndSync(encoded)
        // Do not expose a change until its first durable snapshot has been acknowledged.
        active = target; state = next
        // Undo is RAM-only, but must follow the durable mutation even when its mirror fails.
        onDurable()
        // Clipboard callbacks can arrive while the recovery mirror is blocked.
        // Publish the committed classification before that potentially slow I/O.
        sensitivityChanged?.invoke(sensitiveEntries)
        if (mirror) {
            try { slots[1-target].writeAndSync(encoded) }
            catch (_: IOException) { cleanupPending=true;throw StoreException("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED") }
            cleanupPending=false
        }
    }
    fun verifyDurable(): Boolean {
        val valid = slots.mapNotNull { slot -> try { SnapshotCodec.decode(slot.read()) } catch (_: IOException) { null } }
        return valid.any { it == state }
    }
    fun verifyBothDurable(): Boolean = slots.all { slot ->
        try { SnapshotCodec.decode(slot.read()) == state } catch (_: IOException) { false }
    }
    private companion object { val undoTokens = SecureRandom() }
}
