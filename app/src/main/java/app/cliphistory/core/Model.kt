package app.cliphistory.core

const val DEFAULT_LIMIT = 100
const val MIN_LIMIT = 20
const val MAX_LIMIT = 500
const val MAX_TEXT_BYTES = 65_536
const val MAX_SNAPSHOT_BYTES = MAX_LIMIT * (MAX_TEXT_BYTES + 32) + 1024

data class Entry(val id: Long, val timestamp: Long, val text: String)
data class Snapshot(
    val generation: Long = 0, val limit: Int = DEFAULT_LIMIT,
    val nextId: Long = 1, val paused: Boolean = false,
    val entries: List<Entry> = emptyList()
)
enum class CaptureResult { SAVED, DUPLICATE, EMPTY, SENSITIVE, TOO_LARGE, PAUSED, INVALID_TEXT }
class StoreException(val reason: String) : java.io.IOException(reason)
interface Slot : java.io.Closeable {
    fun read(): ByteArray
    fun writeAndSync(bytes: ByteArray)
    override fun close() {}
}
