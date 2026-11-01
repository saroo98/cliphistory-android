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
    private fun stamps(context:Context)=(files(context)+File(context.noBackupFilesDir,"history-key.bin")).map { file ->
        if(!file.exists())null else Os.stat(file.absolutePath).let {
            Stamp(it.st_dev,it.st_ino,it.st_size,it.st_mtim.tv_sec,it.st_mtim.tv_nsec,it.st_ctim.tv_sec,it.st_ctim.tv_nsec)
        }
    }
    @Synchronized fun invalidate() { cachedSnapshot=null;cachedStamps=emptyList();cachedPath="" }
    private fun files(context: Context): List<File> = listOf("history-a.bin","history-b.bin").map { File(context.noBackupFilesDir,it) }
    fun hasHistory(context: Context) = files(context).any { it.exists() && it.length() > 0 }
    /** The caller must establish exclusive ownership before a pending migration writes. */
    @Synchronized fun prepare(context: Context, stopPreviousWriter: () -> Unit): HistoryKeys.Material {
        val existing = if(HistoryKeys.exists(context)) HistoryKeys.load(context) else null
        if(existing != null && !existing.pending) return existing
        stopPreviousWriter()
        val (first,second)=openForDaemon(context)
        val raw=listOf(first,second).map { FramedSlot(FdAccess(it,context.applicationInfo.uid),EncryptedSlot.MAX_ENCRYPTED_BYTES) }
        var encrypted=emptyList<Slot>()
        try {
            val material=existing ?: run {
                if(raw.any { slot -> try { EncryptedSlot.isEncrypted(slot.read()) } catch (_:java.io.IOException) { false } })
                    throw StoreException("HISTORY_KEY_UNAVAILABLE")
                HistoryRepository(raw[0],raw[1]) // Validate legacy data before creating a key.
                HistoryKeys.createPending(context)
            }
            encrypted=raw.mapIndexed { index,slot -> EncryptedSlot(slot,material.bytes,material.storageId,context.packageName,index,true) }
            val repository=HistoryRepository(encrypted[0],encrypted[1],AndroidHistoryClock(context))
            repository.initialize(forceMirror=true)
            if(!repository.verifyBothDurable())throw StoreException("ENCRYPTED_MIGRATION_NOT_VERIFIED")
            val completed=HistoryKeys.complete(context,material)
            invalidate()
            return completed
        } finally {
            (if(encrypted.isEmpty())raw else encrypted).forEach { try { it.close() } catch (_:Exception) {} }
        }
    }
    fun encryptedSlot(context:Context,descriptor:ParcelFileDescriptor,index:Int,material:HistoryKeys.Material=HistoryKeys.load(context)):Slot {
        if(material.pending)throw StoreException("HISTORY_MIGRATION_REQUIRED")
        return EncryptedSlot(FramedSlot(FdAccess(descriptor,context.applicationInfo.uid),EncryptedSlot.MAX_ENCRYPTED_BYTES),
            material.bytes,material.storageId,context.packageName,index)
    }
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
        val material=HistoryKeys.load(context)
        if(material.pending)throw StoreException("HISTORY_MIGRATION_REQUIRED")
        val path=context.noBackupFilesDir.absolutePath
        var before=stamps(context)
        val clock=AndroidHistoryClock(context)
        cachedSnapshot?.takeIf { cachedPath==path && cachedStamps==before }?.let { return it.available(clock.now()) }
        // A disconnected helper may still be completing one write. Cache only a
        // stable, fully validated read; file replacement and nanosecond writes invalidate it.
        repeat(3) {
            val state=try { readUncached(context) } catch(e:Exception) {
                val after=stamps(context)
                if(before==after)throw e
                before=after;return@repeat
            }
            val after=stamps(context)
            if(before==after){cachedPath=path;cachedStamps=after;cachedSnapshot=state;return state.available(clock.now())}
            before=after
        }
        throw StoreException("HISTORY_CHANGING_TRY_AGAIN")
    }
    private fun readUncached(context: Context): Snapshot {
        val opened=ArrayList<Slot>()
        try {
            for((index,file) in files(context).withIndex()) {
                opened.add(if(!file.exists()) object:Slot {
                    override fun read()=byteArrayOf()
                    override fun writeAndSync(bytes:ByteArray) { error("READ_ONLY") }
                } else encryptedSlot(context,ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),index))
            }
            val state=HistoryRepository(opened[0],opened[1]).state
            // A completed migration always has a durable nonzero generation,
            // including an intentionally empty history. Missing files are damage.
            if(state.generation==0L)throw StoreException("BOTH_SNAPSHOTS_UNREADABLE")
            return state.uniqueProjection()
        } finally { opened.forEach { try { it.close() } catch (_:Exception) {} } }
    }
}
