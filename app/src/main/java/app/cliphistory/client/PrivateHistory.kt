package app.cliphistory.client

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import java.io.File

object PrivateHistory {
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
    fun readOffline(context: Context): Snapshot {
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
