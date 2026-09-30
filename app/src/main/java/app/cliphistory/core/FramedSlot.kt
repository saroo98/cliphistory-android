package app.cliphistory.core

import java.io.IOException
import java.nio.ByteBuffer

interface RandomAccess : java.io.Closeable {
    fun size(): Long
    fun read(offset: Long, destination: ByteArray, start: Int, count: Int): Int
    fun write(offset: Long, source: ByteArray, start: Int, count: Int): Int
    fun sync()
}

/**
 * Length-prefixed snapshots, deliberately without truncate/chmod/rename.
 * Android permits shell to read/write an app-provided FD, not change its attributes.
 * Obsolete tail bytes are overwritten with zeros after a smaller snapshot is written.
 */
class FramedSlot(private val file: RandomAccess) : Slot {
    override fun read(): ByteArray {
        val size = file.size()
        if (size == 0L) return byteArrayOf()
        if (size < 4L || size > MAX_SNAPSHOT_BYTES + 4L) throw StoreException("FRAME_SIZE_INVALID")
        val header = ByteArray(4); readFully(0, header)
        val length = ByteBuffer.wrap(header).int
        if (length !in 1..MAX_SNAPSHOT_BYTES || length.toLong() + 4 > size) throw StoreException("FRAME_LENGTH_INVALID")
        return ByteArray(length).also { readFully(4, it) }
    }
    override fun writeAndSync(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_SNAPSHOT_BYTES) throw StoreException("FRAME_PAYLOAD_INVALID")
        val oldSize = file.size()
        if (oldSize > MAX_SNAPSHOT_BYTES + 4L) throw StoreException("FRAME_SIZE_INVALID")
        // Invalidate the target while writing. The other slot remains available.
        writeFully(0, ByteArray(4))
        writeFully(4, bytes)
        val zeros = ByteArray(4096)
        var at = bytes.size + 4L
        while (at < oldSize) {
            val count = minOf(zeros.size.toLong(), oldSize-at).toInt()
            writeFully(at, if (count==zeros.size) zeros else ByteArray(count)); at += count
        }
        writeFully(0, ByteBuffer.allocate(4).putInt(bytes.size).array())
        file.sync()
    }
    private fun readFully(offset: Long, bytes: ByteArray) {
        var done=0
        while(done<bytes.size) {
            val count=file.read(offset+done,bytes,done,bytes.size-done)
            if(count<=0)throw StoreException("FRAME_TRUNCATED")
            done+=count
        }
    }
    private fun writeFully(offset: Long, bytes: ByteArray) {
        var done=0
        while(done<bytes.size) {
            val count=file.write(offset+done,bytes,done,bytes.size-done)
            if(count<=0)throw IOException("WRITE_MADE_NO_PROGRESS")
            done+=count
        }
    }
    override fun close()=file.close()
}
