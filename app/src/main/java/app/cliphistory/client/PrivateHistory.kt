package app.cliphistory.client

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import java.io.File

object PrivateHistory {
    private data class Stamp(val device:Long,val inode:Long,val size:Long,val modified:Long,val modifiedNanos:Long,val changed:Long,val changedNanos:Long)
    private var cachedPath=""
    private var cachedStamps=emptyList<Stamp?>()
    private var cachedSnapshot:Snapshot?=null
    private fun stamps(context:Context)=files(context).map { file ->
        if(!file.exists())null else Os.stat(file.absolutePath).let {
            Stamp(it.st_dev,it.st_ino,it.st_size,it.st_mtim.tv_sec,it.st_mtim.tv_nsec,it.st_ctim.tv_sec,it.st_ctim.tv_nsec)
        }
    }
    @Synchronized fun invalidate() { cachedSnapshot=null;cachedStamps=emptyList();cachedPath="" }
    private fun files(context: Context): List<File> = listOf("history-a.bin","history-b.bin").map { File(context.noBackupFilesDir,it) }
    fun openForDaemon(context: Context): Pair<ParcelFileDescriptor,ParcelFileDescriptor> {
        val files=files(context)
        val opened=ArrayList<ParcelFileDescriptor>()
        try {
            for(file in files) {
                val descriptor=ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
                opened.add(descriptor)
                Os.chmod(file.absolutePath, 384) // 0600, owner read/write only.
            }
            // Make new directory entries durable while still running as the app UID.
            val directory=Os.open(context.noBackupFilesDir.absolutePath,OsConstants.O_RDONLY,0)
            try {
                check(OsConstants.S_ISDIR(Os.fstat(directory).st_mode)) { "PRIVATE_DIRECTORY_REQUIRED" }
                Os.fsync(directory)
            } finally { Os.close(directory) }
            return opened[0] to opened[1]
        } catch(t:Throwable) { opened.forEach { try { it.close() } catch (_:Exception) {} };throw t }
    }
    @Synchronized fun readOffline(context:Context):Snapshot {
        val path=context.noBackupFilesDir.absolutePath
        var before=stamps(context)
        cachedSnapshot?.takeIf { cachedPath==path && cachedStamps==before }?.let { return it }
        // A disconnected helper may still be completing one write. Cache only a
        // stable, fully validated read; file replacement and nanosecond writes invalidate it.
        repeat(3) {
            val state=try { readUncached(context) } catch(e:Exception) {
                val after=stamps(context)
                if(before==after)throw e
                before=after;return@repeat
            }
            val after=stamps(context)
            if(before==after){cachedPath=path;cachedStamps=after;cachedSnapshot=state;return state}
            before=after
        }
        throw StoreException("HISTORY_CHANGING_TRY_AGAIN")
    }
    private fun readUncached(context: Context): Snapshot {
        val opened=ArrayList<Slot>()
        try {
            for(file in files(context)) {
                opened.add(if(!file.exists()) object:Slot {
                    override fun read()=byteArrayOf()
                    override fun writeAndSync(bytes:ByteArray) { error("READ_ONLY") }
                } else FramedSlot(FdAccess(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),context.applicationInfo.uid)))
            }
            return HistoryRepository(opened[0],opened[1]).state
        } finally { opened.forEach { try { it.close() } catch (_:Exception) {} } }
    }
}
