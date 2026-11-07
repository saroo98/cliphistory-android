package app.cliphistory.core

data class HistoryTime(val boot: Int, val elapsed: Long)
data class Expiry(val boot: Int, val elapsed: Long)
fun interface HistoryClock { fun now(): HistoryTime }
fun Expiry.expired(now: HistoryTime) = now.boot < 0 || boot != now.boot || now.elapsed >= elapsed
fun Entry.expired(now: HistoryTime) = expiry?.expired(now) == true
fun Snapshot.available(now: HistoryTime) = copy(entries = entries.filterNot { it.expired(now) })
/** Capture keeps this immutable match even if queued cleanup later removes it. */
fun sensitiveMatch(entries: List<Entry>, text: String, observed: HistoryTime): Entry? = entries.asSequence()
    .filter { it.sensitive && it.text == text && !it.expired(observed) }
    .minByOrNull { it.expiry?.elapsed ?: Long.MAX_VALUE }
fun sensitiveDeadline(now: HistoryTime, minutes: Int): Expiry {
    if(minutes !in 1..1440)throw StoreException("SENSITIVE_TIMER_MUST_BE_1_TO_1440")
    val duration=minutes*60_000L
    if(now.boot<0 || now.elapsed<0 || now.elapsed>Long.MAX_VALUE-duration)throw StoreException("BOOT_IDENTITY_UNAVAILABLE")
    return Expiry(now.boot,now.elapsed+duration)
}

/** Longest single wait for the expiry timer. Timer delays pause while the phone sleeps but
 * deadlines use elapsed real time, so re-check at least once a minute of awake time. */
const val MAX_EXPIRY_WAIT_MS = 60_000L
fun expiryWait(delayMs: Long): Long = delayMs.coerceIn(0L, MAX_EXPIRY_WAIT_MS)
