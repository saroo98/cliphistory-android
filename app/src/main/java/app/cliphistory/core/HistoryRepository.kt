package app.cliphistory.core

import java.io.IOException
import java.security.SecureRandom

/** Single-writer repository. Call only from the daemon's serial worker. */
class HistoryRepository(private val a: Slot, private val b: Slot) {
    private data class PendingDelete(val entry: Entry, val token: Long)
    enum class UndoResult { RESTORED, ALREADY_PRESENT }
    private var pendingDelete: PendingDelete? = null
    val undoToken: Long get() = pendingDelete?.token ?: 0
    val undoEntryId: Long get() = pendingDelete?.entry?.id ?: -1
    private val slots = arrayOf(a, b)
    private var active = -1
    var recoveredFromDamagedSlot = false; private set
    var state: Snapshot; private set

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
    fun initialize() {
        val migrated = state.uniqueProjection()
        commit(migrated, mirror = migrated.entries != state.entries)
    }

    fun capture(text: String, time: Long, sensitive: Boolean = false): CaptureResult {
        if (state.paused) return CaptureResult.PAUSED
        if (sensitive) return CaptureResult.SENSITIVE
        if (text.isEmpty()) return CaptureResult.EMPTY
        if (text.length > MAX_TEXT_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_TEXT_BYTES) return CaptureResult.TOO_LARGE
        if (String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8) != text) return CaptureResult.INVALID_TEXT
        if (state.entries.firstOrNull()?.text == text) return CaptureResult.DUPLICATE
        if (state.nextId == Long.MAX_VALUE) throw StoreException("ID_EXHAUSTED")
        val entry = Entry(state.nextId, time.coerceAtLeast(0), text)
        val retained = if (state.duplicateMode == DuplicateMode.UNIQUE_TEXT)
            state.entries.filterNot { it.text == text } else state.entries
        commit(state.copy(nextId = state.nextId + 1, entries = (listOf(entry) + retained).take(state.limit)))
        return CaptureResult.SAVED
    }

    fun setLimit(limit: Int) {
        if (limit !in MIN_LIMIT..MAX_LIMIT) throw StoreException("LIMIT_MUST_BE_20_TO_500")
        val shrunk = state.entries.size > limit
        commit(state.copy(limit = limit, entries = state.entries.take(limit)), mirror = shrunk)
    }
    fun setPaused(paused: Boolean) { commit(state.copy(paused = paused), mirror = true) }
    fun setDuplicateMode(mode: DuplicateMode) {
        // A retry must clean both slots even when the first durable write already
        // changed the selected mode and only the backup cleanup failed.
        commit(state.copy(duplicateMode = mode).uniqueProjection(), mirror = true)
    }
    fun delete(id: Long): Boolean {
        val entry = state.entries.firstOrNull { it.id == id } ?: return false
        val pending = PendingDelete(entry, newUndoToken())
        commit(state.copy(entries = state.entries.filterNot { it.id == id }), mirror = true) {
            pendingDelete = pending
        }
        return true
    }
    fun undoDelete(token: Long): UndoResult {
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
    private fun requirePendingDelete(token: Long): PendingDelete = pendingDelete?.takeIf { it.token == token }
        ?: throw StoreException("UNDO_NOT_AVAILABLE")
    private fun newUndoToken(): Long {
        var token: Long
        do { token = undoTokens.nextLong() and Long.MAX_VALUE } while (token == 0L || token == undoToken)
        return token
    }
    fun clear() { commit(state.copy(entries = emptyList()), mirror = true) { pendingDelete = null } }
    fun search(query: String): List<Entry> = if (query.isEmpty()) state.entries else state.entries.filter { it.text.contains(query, ignoreCase = true) }

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
        if (mirror) {
            try { slots[1-target].writeAndSync(encoded) }
            catch (_: IOException) { throw StoreException("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED") }
        }
    }
    fun verifyDurable(): Boolean {
        val valid = slots.mapNotNull { slot -> try { SnapshotCodec.decode(slot.read()) } catch (_: IOException) { null } }
        return valid.any { it == state }
    }
    private companion object { val undoTokens = SecureRandom() }
}
