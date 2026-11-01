package app.cliphistory

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

/** The phone-safe mode creates only new, isolated test files and never touches the clipboard. */
class EncryptionChecks(private val test:Instrumentation,private val arguments:Bundle) {
    private val log=StringBuilder()
    private var checks=0
    private fun verify(value:Boolean,message:String) { check(value){message};checks++;log.append("PASS $message\n") }
    private fun context():Context {
        val directory=File(test.targetContext.noBackupFilesDir,"crypto-check-${UUID.randomUUID()}").apply { check(mkdirs()) }
        return object:ContextWrapper(test.targetContext) { override fun getNoBackupFilesDir()=directory }
    }
    private fun raw(context:Context,index:Int):Slot {
        val file=File(context.noBackupFilesDir,if(index==0)"history-a.bin" else "history-b.bin")
        return FramedSlot(FdAccess(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE),context.applicationInfo.uid),EncryptedSlot.MAX_ENCRYPTED_BYTES)
    }
    private fun legacy(snapshot:Snapshot,version:Int):ByteArray {
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(0x434C4831);stream.writeInt(version);stream.writeLong(snapshot.generation)
            stream.writeInt(snapshot.limit);stream.writeLong(snapshot.nextId);stream.writeBoolean(snapshot.paused)
            if(version>=2)stream.writeByte(snapshot.duplicateMode.value)
            stream.writeInt(snapshot.entries.size)
            snapshot.entries.forEach { entry ->
                val bytes=entry.text.toByteArray(Charsets.UTF_8)
                stream.writeLong(entry.id);stream.writeLong(entry.timestamp);stream.writeInt(bytes.size);stream.write(bytes)
            }
        }
        val bytes=output.toByteArray();return bytes+MessageDigest.getInstance("SHA-256").digest(bytes)
    }
    private fun rejected(message:String,action:()->Unit) {
        var denied=false
        try { action() } catch(_:java.io.IOException) { denied=true }
        verify(denied,message)
    }
    private fun encrypted(context:Context,expected:Snapshot?=null) {
        val material=HistoryKeys.load(context)
        verify(!material.pending,"Migration phase is durably complete")
        for(index in 0..1) {
            raw(context,index).use { slot ->
                val bytes=slot.read()
                verify(EncryptedSlot.isEncrypted(bytes),"Slot $index has the encrypted envelope")
                expected?.entries?.forEach { entry -> verify(!String(bytes,Charsets.UTF_8).contains(entry.text),"Slot $index has no text canary") }
            }
            val file=File(context.noBackupFilesDir,if(index==0)"history-a.bin" else "history-b.bin")
            PrivateHistory.encryptedSlot(context,ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),index,material).use {
                val decoded=SnapshotCodec.decode(it.read())
                expected?.let { value -> verify(decoded==value,"Slot $index authenticates and preserves exact state") }
            }
        }
        val keyRecord=File(context.noBackupFilesDir,"history-key.bin").readBytes()
        verify(!keyRecord.asList().windowed(32).any { it==material.bytes.asList() },"The stored key record does not contain its plaintext data key")
        log.append("Observed Keystore security level: ${material.security}\n")
    }
    private fun keyRoundTrip() {
        val context=context()
        val material=PrivateHistory.prepare(context) { }
        val bytes=material.bytes.copyOf();val identity=material.storageId.copyOf()
        HistoryKeys.invalidate()
        val reloaded=HistoryKeys.load(context)
        verify(bytes.contentEquals(reloaded.bytes) && identity.contentEquals(reloaded.storageId),"Keystore wrapping survives cache invalidation")
        encrypted(context,PrivateHistory.readOffline(context))
        val record=File(context.noBackupFilesDir,"history-key.bin")
        val original=record.readBytes()
        try {
            for(malformed in listOf(original.copyOf(original.size-1),original+byteArrayOf(0))) {
                record.writeBytes(malformed);HistoryKeys.invalidate()
                rejected("An incorrectly sized wrapped key fails closed") { HistoryKeys.load(context) }
                verify(record.readBytes().contentEquals(malformed),"Invalid key length does not replace the stored record")
            }
            record.writeBytes(original.copyOf().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() });HistoryKeys.invalidate()
            rejected("A modified wrapped key fails closed") { HistoryKeys.load(context) }
            verify(record.readBytes().contentEquals(original).not(),"A failed key load does not replace the damaged record")
        } finally { record.writeBytes(original);HistoryKeys.invalidate();PrivateHistory.invalidate() }
    }
    private fun migration() {
        requireDisposableEmulator(test)
        val entries=listOf(Entry(3,9,"Synthetic migration 🌿\n exact whitespace  "),Entry(1,1,"Synthetic older text"))
        for(version in 1..2)for(interruption in 0..2) {
            val context=context()
            val older=Snapshot(4,137,4,true,entries,DuplicateMode.UNIQUE_TEXT)
            val newest=older.copy(generation=5,paused=false)
            raw(context,0).use { it.writeAndSync(legacy(older,version)) }
            raw(context,1).use { it.writeAndSync(legacy(newest,version)) }
            if(interruption==0)File(context.noBackupFilesDir,"history-key.bin.new").writeBytes(byteArrayOf(1,2,3))
            if(interruption>0) {
                val material=HistoryKeys.createPending(context)
                EncryptedSlot(raw(context,0),material.bytes,material.storageId,context.packageName,0,true).use { it.writeAndSync(SnapshotCodec.encode(newest.copy(generation=6))) }
                if(interruption==2)EncryptedSlot(raw(context,1),material.bytes,material.storageId,context.packageName,1,true).use { it.writeAndSync(SnapshotCodec.encode(newest.copy(generation=6))) }
            }
            var stopped=false
            PrivateHistory.prepare(context) { stopped=true }
            val state=PrivateHistory.readOffline(context)
            verify(stopped,"Migration establishes exclusive ownership, legacy v$version / interruption $interruption")
            verify(state.entries==entries && state.limit==137 && !state.paused && state.nextId==4L,"Migration preserves the newest history and settings")
            encrypted(context,state)
            raw(context,0).use { it.writeAndSync(legacy(newest.copy(generation=Long.MAX_VALUE-1),version)) }
            PrivateHistory.invalidate()
            verify(PrivateHistory.readOffline(context)==state,"Completed migration rejects a newer injected plaintext snapshot")
        }
        val context=context()
        val snapshot=Snapshot(5,100,2,false,listOf(Entry(1,1,"Synthetic missing-key canary")))
        val material=HistoryKeys.createPending(context)
        EncryptedSlot(raw(context,0),material.bytes,material.storageId,context.packageName,0).use { it.writeAndSync(SnapshotCodec.encode(snapshot)) }
        check(File(context.noBackupFilesDir,"history-key.bin").delete());HistoryKeys.invalidate()
        rejected("Ciphertext without its key does not create a replacement key") { PrivateHistory.prepare(context) { } }
        verify(!HistoryKeys.exists(context),"Missing-key failure preserves evidence")
    }
    private fun offlineExpiry() {
        requireDisposableEmulator(test)
        val context=context();val material=PrivateHistory.prepare(context) { }
        val now=AndroidHistoryClock(context).now()
        val state=Snapshot(10,100,4,false,listOf(
            Entry(3,3,"Synthetic soon-expiring text",true,Expiry(now.boot,now.elapsed+1500)),
            Entry(2,2,"Synthetic indefinite text",true),Entry(1,1,"Synthetic ordinary text")),captureSensitive=true)
        for(index in 0..1)EncryptedSlot(raw(context,index),material.bytes,material.storageId,context.packageName,index).use { it.writeAndSync(SnapshotCodec.encode(state)) }
        verify(PrivateHistory.readOffline(context).entries.size==3,"Offline initial read includes unexpired text")
        android.os.SystemClock.sleep(1600)
        verify(PrivateHistory.readOffline(context).entries.map { it.id }==listOf(2L,1L),"Cached offline access rechecks expiry without file changes")
    }
    private fun missingSnapshots() {
        requireDisposableEmulator(test)
        val context=context();PrivateHistory.prepare(context) { }
        val record=File(context.noBackupFilesDir,"history-key.bin").readBytes()
        val first=File(context.noBackupFilesDir,"history-a.bin")
        val second=File(context.noBackupFilesDir,"history-b.bin")
        first.writeBytes(byteArrayOf());PrivateHistory.invalidate()
        verify(PrivateHistory.readOffline(context).generation>0,"One empty slot recovers from the authenticated copy")
        second.writeBytes(byteArrayOf());PrivateHistory.invalidate()
        rejected("A completed store with both slots empty fails closed") { PrivateHistory.readOffline(context) }
        check(first.delete() && second.delete());PrivateHistory.invalidate()
        rejected("A completed store with both slots missing fails closed") { PrivateHistory.readOffline(context) }
        verify(record.contentEquals(File(context.noBackupFilesDir,"history-key.bin").readBytes()),"Missing snapshots do not replace the wrapped key")
    }
    private fun storedAssertion() {
        requireDisposableEmulator(test)
        val expected=arguments.getString("expectedHash") ?: error("Expected synthetic hash required")
        val state=PrivateHistory.readOffline(test.targetContext)
        val found=state.entries.any { entry -> MessageDigest.getInstance("SHA-256").digest(entry.text.toByteArray()).joinToString("") { "%02x".format(it) }==expected }
        verify(found,"The independent recorder captured the expected synthetic payload")
    }
    private fun storedStatus() {
        requireDisposableEmulator(test)
        val state=PrivateHistory.readOffline(test.targetContext)
        val status=org.json.JSONObject().put("generation",state.generation).put("count",state.entries.size)
            .put("paused",state.paused).put("limit",state.limit).put("duplicateMode",state.duplicateMode.value)
        verify(true,"Authenticated offline metadata is readable")
        log.append("STORED_STATUS $status\n")
    }
    private fun storedUpgrade() {
        requireDisposableEmulator(test)
        val expected=arguments.getString("expectedHash") ?: error("Expected legacy state hash required")
        val state=PrivateHistory.readOffline(test.targetContext)
        val actual=MessageDigest.getInstance("SHA-256").digest(SnapshotCodec.encode(state.copy(generation=1)))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        verify(actual==expected,"Live upgrade preserves every committed entry, ID, timestamp and setting")
        encrypted(test.targetContext,state)
        for(name in listOf("history-a.bin","history-b.bin","history-key.bin")) {
            val mode=android.system.Os.stat(File(test.targetContext.noBackupFilesDir,name).path).st_mode and 511
            verify(mode==384,"Private $name remains owner-only")
        }
    }
    private fun storedFingerprint() {
        requireDisposableEmulator(test)
        val state=PrivateHistory.readOffline(test.targetContext)
        val hash=MessageDigest.getInstance("SHA-256").digest(SnapshotCodec.encode(state.copy(generation=1)))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        verify(state.entries.any { it.sensitive } && state.entries.any { !it.sensitive },"Encrypted upgrade fixture contains sensitive and ordinary text")
        log.append("STORED_FINGERPRINT $hash\n")
        storedStatus()
        encrypted(test.targetContext,state)
    }
    private fun upgradeFixture() {
        requireDisposableEmulator(test)
        val now=AndroidHistoryClock(test.targetContext).now()
        val state=Snapshot(10,137,4,false,listOf(
            Entry(3,3,"Synthetic timed upgrade text",true,Expiry(now.boot,now.elapsed+300_000)),
            Entry(2,2,"Synthetic indefinite upgrade text",true),
            Entry(1,1,"Synthetic ordinary upgrade text")),captureSensitive=true,sensitiveMinutes=7)
        writeFixturePayload(test,SnapshotCodec.encode(state))
        storedFingerprint()
    }
    fun run(mode:String):String {
        try { when(mode) {
            "key-check"->keyRoundTrip()
            "encryption"->{keyRoundTrip();migration();offlineExpiry();missingSnapshots()}
            "missing-snapshots"->missingSnapshots()
            "stored-assert"->storedAssertion()
            "stored-status"->storedStatus()
            "stored-upgrade"->storedUpgrade()
            "stored-fingerprint"->storedFingerprint()
            "upgrade-fixture"->upgradeFixture()
            else->error("Unknown encryption mode")
        } } catch(error:Throwable) { throw IllegalStateException(log.toString(),error) }
        return log.append("PASS $checks encryption assertions ($mode). No clipboard writes.\n").toString()
    }
}
