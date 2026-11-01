package app.cliphistory

import android.app.Instrumentation
import android.content.*
import android.os.Bundle
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.ipc.IClipboardDaemon
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test APK only. Core regressions use private synthetic memory, never personal history. */
class PhoneVerificationChecks(private val test:Instrumentation,private val arguments:Bundle) {
    private var count=0
    private val log=StringBuilder()
    private fun verify(value:Boolean,message:String) { check(value){message};count++;log.append("PASS $message\n") }
    private class MemorySlot:Slot {
        var bytes=byteArrayOf()
        var beforeWrite:(()->Unit)?=null
        override fun read()=bytes.copyOf()
        override fun writeAndSync(bytes:ByteArray) { beforeWrite?.invoke();this.bytes=bytes.copyOf() }
    }
    private fun core() {
        var now=HistoryTime(7,1000)
        val a=MemorySlot();val b=MemorySlot()
        var published=emptyList<Entry>()
        val repo=HistoryRepository(a,b,HistoryClock { now }) { published=it }
        repo.initialize(true);repo.setSensitivePolicy(true,true,5)
        val inspect:()->Unit={
            repo.state.entries.firstOrNull { it.text=="Synthetic private" }?.let { entry ->
                verify(published.any { it.sensitive && it.text==entry.text && it.expiry==entry.expiry },
                    "Committed sensitivity/deadline is available during recovery-copy I/O")
            }
        }
        a.beforeWrite=inspect;b.beforeWrite=inspect
        repo.capture("Synthetic private",1,true)
        now=HistoryTime(7,2000);repo.setSensitivePolicy(true,true,1)
        a.beforeWrite=null;b.beforeWrite=null
        val observed=HistoryTime(7,61_999)
        val inherited=sensitiveMatch(published,"Synthetic private",observed) ?: error("Missing committed classification")
        now=HistoryTime(7,62_001);repo.expire()
        verify(repo.capture("Synthetic private",2,true,observed,inherited.expiry)==CaptureResult.SENSITIVE,
            "A queued recopy is rejected after its inherited deadline")
        verify(listOf(a,b).all { SnapshotCodec.decode(it.read()).entries.none { entry -> entry.sensitive } },
            "Expired queued text is absent from both recovery copies")
        now=HistoryTime(7,100_000);repo.capture("Synthetic Undo private",3,true)
        val id=repo.state.entries.single().id;repo.delete(id)
        val beforeExpiry=HistoryTime(7,159_999)
        now=HistoryTime(7,160_001)
        verify(repo.capture("Synthetic Undo private",4,false,beforeExpiry)==CaptureResult.SENSITIVE,
            "A delayed unmarked recopy cannot downgrade expired sensitive Undo")
        verify(repo.undoToken==0L && repo.available.entries.isEmpty(),"Expired Undo cannot restore sensitive text")
    }
    private fun await(message:String,timeout:Long=10_000,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<end) { if(condition()){verify(true,message);return};SystemClock.sleep(80) }
        error(message)
    }
    private fun shell(command:String) { test.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
    } }
    private fun nodes(root:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(root==null)emptyList()
        else listOf(root)+(0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun clipboard(action:String,text:String?=null,marked:Boolean=false) {
        // UiAutomation executes argv directly, so use controlled single-token payloads.
        check(text==null || text.matches(Regex("Synthetic_[A-Za-z_]+")))
        val component="app.cliphistory.validation.test/app.cliphistory.SyntheticClipboardActivity"
        shell("am start -W -n $component -a $action"+(if(text==null)"" else " --es text $text --ez sensitive $marked"))
    }
    private fun live() {
        check(arguments.getString("productionPaused")=="acknowledged") { "Pause production through its acknowledged UI first" }
        await("Shizuku service reaches the validation process") { runCatching { Shizuku.getUid();Shizuku.checkSelfPermission();true }.getOrDefault(false) }
        if(Shizuku.checkSelfPermission()!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            test.runOnMainSync { Shizuku.requestPermission(1729) }
            await("Shizuku presents its validation permission confirmation") {
                nodes(test.uiAutomation.rootInActiveWindow).any { it.packageName?.toString()=="moe.shizuku.privileged.api" && it.text?.toString()=="Allow all the time" }
            }
            var allow=nodes(test.uiAutomation.rootInActiveWindow).single { it.packageName?.toString()=="moe.shizuku.privileged.api" && it.text?.toString()=="Allow all the time" }
            while(!allow.isClickable)allow=allow.parent?:error("No permission action")
            check(allow.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            log.append("Temporary validation permission granted through the Shizuku confirmation.\n")
            await("Validation Shizuku permission is acknowledged") { Shizuku.checkSelfPermission()==android.content.pm.PackageManager.PERMISSION_GRANTED }
        }
        check(Shizuku.getUid()==2000) { "Shell-mode Shizuku required" }
        val directory=File(test.targetContext.noBackupFilesDir,"phone-check-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context=object:ContextWrapper(test.targetContext) { override fun getNoBackupFilesDir()=directory }
        val material=PrivateHistory.prepare(context) { }
        val (first,second)=PrivateHistory.openForDaemon(context)
        listOf(first,second).mapIndexed { index,fd -> PrivateHistory.encryptedSlot(context,fd,index,material) }.let { slots ->
            try { HistoryRepository(slots[0],slots[1],AndroidHistoryClock(context)).setPaused(true) }
            finally { slots.forEach { it.close() } }
        }
        val args=Shizuku.UserServiceArgs(ComponentName(context,app.cliphistory.daemon.ClipboardUserService::class.java))
            .daemon(true).processNameSuffix("phonecheck").version(BuildConfig.VERSION_CODE).tag("cliphistory-phone-verification")
        val connected=CountDownLatch(1)
        var binder:IBinder?=null
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,value:IBinder) { binder=value;connected.countDown() }
            override fun onServiceDisconnected(name:ComponentName) { }
        }
        var service:IClipboardDaemon?=null
        var checkpoint=false
        test.runOnMainSync { Shizuku.bindUserService(args,connection) }
        try {
            verify(connected.await(20,TimeUnit.SECONDS),"Isolated physical-phone helper starts")
            val remote=IClipboardDaemon.Stub.asInterface(binder!!);service=remote
            fun attach(boot:Int):Bundle {
                val (a,b)=PrivateHistory.openForDaemon(context);val key=material.bytes.copyOf()
                return try { remote.attachEncrypted(a,b,Shizuku.getBinder()!!,key,material.storageId,boot) }
                finally { key.fill(0);a.close();b.close() }
            }
            verify(attach(-1).getBoolean("ok"),"Unknown boot identity attaches without claiming a timed clock")
            val boot=AndroidHistoryClock(context).now().boot;check(boot>=0)
            verify(attach(boot).getBoolean("bootIdentityKnown"),"The same helper recovers a verified boot identity")
            verify(attach(boot+1).getString("issue")=="ENCRYPTION_IDENTITY_CHANGED" && attach(-1).getString("issue")=="ENCRYPTION_IDENTITY_CHANGED","Conflicting or downgraded boot identity is rejected")
            verify(attach(boot).getBoolean("ok"),"Rejected identity changes leave the correct helper usable")
            verify(remote.setSensitivePolicy(true,true,5).getBoolean("ok"),"Sensitive policy is acknowledged")
            val changed=remote.changeSensitivePolicy(minutes=1)
            verify(changed.getBoolean("captureSensitive") && changed.getBoolean("autoDeleteSensitive") && changed.getInt("sensitiveMinutes")==1,"A field-only duration update preserves both live policy switches")
            clipboard(SyntheticClipboardActivity.CHECKPOINT)
            await("Original clipboard checkpointed in foreground RAM only") {
                nodes(test.uiAutomation.rootInActiveWindow).any { it.text?.toString()=="Clipboard checkpoint ready" }
            }
            checkpoint=true
            verify(remote.setPaused(false).getBoolean("ok"),"Isolated recorder is active")
            fun entries()=remote.page("",0,40).getLongArray("ids")!!.map { remote.entry(it) }
            clipboard(SyntheticClipboardActivity.ACTION,"Synthetic_private",true)
            await("Marked sensitive text is captured on the physical phone") { remote.status().getInt("count")==1 && entries().single().getString("text")=="Synthetic_private" }
            val entry=entries().single();val expiry=entry.getLong("expiryTime")
            clipboard(SyntheticClipboardActivity.READ_SYNTHETIC_STATE)
            val source=nodes(test.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.firstOrNull { it.startsWith("Synthetic clipboard state:") }
            log.append("$source\n")
            log.append("Isolated capture metadata: fixtureMatches=${entry.getString("text")=="Synthetic_private"}, sensitive=${entry.getBoolean("sensitive")}, remainingMs=${expiry-SystemClock.elapsedRealtime()}, capture=${remote.status().getBoolean("captureSensitive")}, autoDelete=${remote.status().getBoolean("autoDeleteSensitive")}, minutes=${remote.status().getInt("sensitiveMinutes")}\n")
            verify(entry.getBoolean("sensitive") && expiry>SystemClock.elapsedRealtime(),"Captured sensitivity has a real elapsed-time deadline")
            clipboard(SyntheticClipboardActivity.ACTION,"Synthetic_separator")
            await("Ordinary text is captured alongside sensitive text") { remote.status().getInt("count")==2 }
            clipboard(SyntheticClipboardActivity.ACTION,"Synthetic_private")
            await("Interleaved recopy moves the existing text to the top") { entries().first().getString("text")=="Synthetic_private" }
            verify(remote.status().getInt("count")==2 && entries().first().getBoolean("sensitive") && entries().first().getLong("expiryTime")==expiry,"Recopy neither duplicates nor downgrades nor extends sensitive text")
            log.append("Waiting for the one-minute device elapsed-time deadline.\n")
            await("Deadline removes sensitive text from both encrypted snapshots",70_000) {
                if(SystemClock.elapsedRealtime()<=expiry)return@await false
                val clean=entries().none { it.getBoolean("sensitive") }
                clean && (0..1).all { index ->
                    val file=File(directory,if(index==0)"history-a.bin" else "history-b.bin")
                    PrivateHistory.encryptedSlot(context,ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),index,material).use {
                        SnapshotCodec.decode(it.read()).entries.none { row -> row.sensitive }
                    }
                }
            }
            verify(PrivateHistory.readOffline(context).entries.single().text=="Synthetic_separator","Offline decryption agrees with durable expiry")
        } finally {
            try { service?.setPaused(true) } finally {
                try { test.runOnMainSync { Shizuku.unbindUserService(args,connection,true) } }
                finally {
                    if(checkpoint) {
                        clipboard(SyntheticClipboardActivity.RESTORE)
                        await("Original clipboard restored and checkpoint released") { nodes(test.uiAutomation.rootInActiveWindow).any { it.text?.toString()=="Clipboard checkpoint restored" } }
                    }
                    HistoryKeys.invalidate();PrivateHistory.invalidate()
                }
            }
        }
    }
    fun run(mode:String):String {
        check(BuildConfig.DEBUG && test.targetContext.packageName=="app.cliphistory.validation")
        check(arguments.getString("allowPhone")=="isolated-synthetic") { "Explicit isolated phone verification required" }
        try { when(mode) { "phone-core"->core();"phone-live"->live();else->error("Unknown phone verification") } }
        catch(error:Throwable) { throw IllegalStateException(log.toString(),error) }
        return log.append("PASS $count phone verification assertions ($mode). No production history access.\n").toString()
    }
}
