package app.cliphistory.daemon

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import app.cliphistory.core.RandomAccess
import java.io.IOException

/** Retains an owning PFD; uses positional I/O so clients never share a seek offset. */
class FdAccess(private val pfd: ParcelFileDescriptor, ownerUid: Int) : RandomAccess {
    class DetachedFile : IOException("PRIVATE_STORAGE_WAS_REMOVED")
    val identity: Pair<Long,Long>
    init {
        val stat = Os.fstat(pfd.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == ownerUid) { "PRIVATE_REGULAR_FILE_REQUIRED" }
        identity=stat.st_dev to stat.st_ino
    }
    override fun size(): Long = io {
        val stat=Os.fstat(pfd.fileDescriptor)
        if(stat.st_nlink<=0)throw DetachedFile()
        stat.st_size
    }
    override fun read(offset: Long, destination: ByteArray, start: Int, count: Int): Int = io {
        Os.pread(pfd.fileDescriptor, destination, start, count, offset)
    }
    override fun write(offset: Long, source: ByteArray, start: Int, count: Int): Int = io {
        Os.pwrite(pfd.fileDescriptor, source, start, count, offset)
    }
    override fun sync() { io { Os.fsync(pfd.fileDescriptor) } }
    override fun close() = pfd.close()
    private inline fun <T> io(action: () -> T): T = try { action() } catch (_: android.system.ErrnoException) { throw IOException("PRIVATE_FILE_IO_FAILED") }
}
