package app.cliphistory

import android.app.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ui.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Runs only in the separate test APK, on synthetic files in the target sandbox. */
class ReleaseChecks(private val test:Instrumentation) {
    private lateinit var activity:MainActivity
    private val context get()=test.targetContext
    private val log=StringBuilder()
    private var checks=0
    private fun main(action:()->Unit) {
        var failure:Throwable?=null
        test.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
        failure?.let { throw it }
    }
    private fun field(name:String):Any?=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun call(name:String,vararg args:Any) {
        MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==args.size }.apply { isAccessible=true }.invoke(activity,*args)
    }
    private fun home()=field("home") as HistoryHome
    private fun checkThat(okay:Boolean,message:String) {
        check(okay){message};checks++;log.append("PASS $message\n")
        test.sendStatus(0,Bundle().apply { putString("stream","PASS $message\n") })
    }
    private fun until(message:String,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+12000
        while(SystemClock.uptimeMillis()<end) {
            var okay=false;main { okay=condition() }
            if(okay){checkThat(true,message);return};SystemClock.sleep(40)
        };error(message)
    }
    private fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList())
    private fun texts()=views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    private fun seed(count:Int,length:Int=0) {
        stopRecorderForFixture(test)
        PrivateHistory.invalidate()
        val entries=(count.toLong() downTo 1L).map { Entry(it,System.currentTimeMillis()-it*60000,if(length==0)"Synthetic clip $it" else (if(it%2==0L)"A" else "B").repeat(length)) }
        val start=SystemClock.elapsedRealtime()
        val bytes=SnapshotCodec.encode(Snapshot(1,if(count>100)500 else 100,count+1L,false,entries,
            if(length>0)DuplicateMode.CONSECUTIVE_ONLY else DuplicateMode.UNIQUE_TEXT))
        log.append("Encode ${bytes.size} bytes: ${SystemClock.elapsedRealtime()-start} ms\n")
        val (a,b)=PrivateHistory.openForDaemon(context)
        listOf(a,b).forEach { FramedSlot(FdAccess(it,context.applicationInfo.uid)).use { slot -> slot.writeAndSync(bytes) } }
        AppSettings(context).apply { appearance="light";returnAfterCopy=false;welcome="never";backgroundSuggestion=false;allowScreenshots=false }
    }
    private fun launch(expected:Int=40) {
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        until("Initial $expected entries loaded") { home().adapter.count==expected }
    }
    private fun capture(name:String) {
        test.waitForIdleSync();SystemClock.sleep(300)
        main {
            val view=activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(context.filesDir,"ui-evidence").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun recreate(orientation:Int) {
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        main { activity.requestedOrientation=orientation }
        activity=test.waitForMonitorWithTimeout(monitor,10000) as? MainActivity ?: error("Orientation did not recreate Activity")
        test.removeMonitor(monitor)
    }
    private fun regression() {
        seed(73);launch()
        main { home().more.performClick() };until("All 73 entries loaded") { home().adapter.count==73 }
        main { home().list.setSelectionFromTop(50,-9) };SystemClock.sleep(300)
        var anchor:HistoryHome.Anchor?=null;main { anchor=home().anchor() }
        recreate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        until("Rotation preserves loaded entries and entry anchor") { home().adapter.count==73 && home().anchor().id==anchor!!.id }
        capture("regression-landscape")
        recreate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        until("Return rotation preserves loaded entries") { home().adapter.count==73 }
        val daemon=(activity.application as ClipApplication).daemon
        val executor=DaemonClient::class.java.getDeclaredField("io").apply { isAccessible=true }.get(daemon) as ExecutorService
        val waiting=CountDownLatch(1);val release=CountDownLatch(1)
        executor.execute { waiting.countDown();release.await(5,TimeUnit.SECONDS) }
        check(waiting.await(5,TimeUnit.SECONDS))
        main { call("preview",71L);call("help") };release.countDown();SystemClock.sleep(450)
        main { checkThat(field("pageKind")=="help","Delayed preview cannot replace Help");call("closePage") }
        main { call("copyEntry",71L) };until("Copy feedback appears") { texts().contains("Copied") }
        main { activity.moveTaskToBack(true) }
        until("History completes background transition") { field("visible")==false }
        main { activity.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        until("History returns to foreground") { field("visible")==true }
        main { checkThat(!texts().contains("Copied"),"Backgrounding removes expired feedback") }
        val missing=CountDownLatch(1)
        daemon.getText(Long.MAX_VALUE) { checkThat(it.text==null && it.issue.isEmpty(),"Missing entry is distinct from read failure");missing.countDown() }
        check(missing.await(5,TimeUnit.SECONDS))
        File(context.noBackupFilesDir,"history-a.bin").writeBytes(byteArrayOf(0,0,0,8,1,2,3,4))
        File(context.noBackupFilesDir,"history-b.bin").writeBytes(byteArrayOf(0,0,0,8,5,6,7,8))
        try { PrivateHistory.readOffline(context);error("Corruption hidden by cache") }
        catch(e:StoreException) { checkThat(true,"File changes invalidate cached history (${e.reason})") }
        main { call("refresh",false,false);call("diagnostics") }
        until("Diagnostics reports current file corruption") { texts().any { it.contains("BOTH_SNAPSHOTS_UNREADABLE") } }
        capture("regression-read-error-diagnostics")
        main { call("closePage");activity.finish() };test.waitForIdleSync()
        seed(73)
    }
    private fun capacity() {
        seed(500,MAX_TEXT_BYTES)
        repeat(2) { i ->
            val start=SystemClock.elapsedRealtime();val snapshot=PrivateHistory.readOffline(context)
            checkThat(snapshot.entries.size==500,"Capacity snapshot ${i+1} readable")
            log.append("Offline read ${i+1}: ${SystemClock.elapsedRealtime()-start} ms\n")
        }
        var start=SystemClock.elapsedRealtime();launch();log.append("Launch to page: ${SystemClock.elapsedRealtime()-start} ms\n")
        start=SystemClock.elapsedRealtime();main { home().more.performClick() };until("Capacity second page loaded") { home().adapter.count==80 }
        log.append("Next page: ${SystemClock.elapsedRealtime()-start} ms\n")
        start=SystemClock.elapsedRealtime();main { call("preview",499L) };until("Full maximum length text opens") { field("pageKind")=="preview" }
        log.append("Full text: ${SystemClock.elapsedRealtime()-start} ms; heap limit ${Runtime.getRuntime().maxMemory()/1048576} MiB\n")
        main { activity.finish() };test.waitForIdleSync();seed(73)
    }
    private fun layout() {
        seed(73);launch()
        if(context.resources.configuration.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            recreate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            until("Landscape entries restored") { home().adapter.count==40 }
        }
        until("Landscape window has input focus") { activity.hasWindowFocus() }
        main { home().search.requestFocus();activity.getSystemService(InputMethodManager::class.java).showSoftInput(home().search,InputMethodManager.SHOW_IMPLICIT) }
        until("Keyboard visible") { activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        SystemClock.sleep(700);capture("regression-layout-ime")
        main { log.append("List ${home().list.height}px, window ${activity.window.decorView.height}px, IME ${activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom}px\n") }
        val resultLocation=IntArray(2)
        main {
            checkThat(home().results.visibility==View.VISIBLE && home().results.isEnabled,"Keyboard dismissal action remains available at ${context.resources.configuration.fontScale} text scale")
            if(home().list.height>40)checkThat(true,"History remains visible alongside keyboard")
            else log.append("System keyboard occupies available landscape content; results action restores history.\n")
            home().results.getLocationOnScreen(resultLocation)
            resultLocation[0]+=home().results.width/2;resultLocation[1]+=home().results.height/2
            checkThat(resultLocation[1]<activity.windowManager.currentWindowMetrics.bounds.height()-activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom,"Results control is above the system keyboard")
        }
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,resultLocation[0].toFloat(),resultLocation[1].toFloat(),0)
            test.sendPointerSync(event);event.recycle()
        }
        until("Results action closes keyboard") { !activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        main { checkThat(home().list.height>300 && home().adapter.count==40,"Large-text landscape results remain reachable") }
        capture("regression-layout-results")
        main { call("limitDialog") };SystemClock.sleep(400)
        main {
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().last()
            val decor=dialog.window!!.decorView
            checkThat(decor.height<=activity.windowManager.currentWindowMetrics.bounds.height(),"Sheet fits current window")
            val handle=views(decor).first { it.contentDescription?.toString()?.startsWith("Close panel")==true }
            handle.performClick();checkThat(!dialog.isShowing,"Sheet handle dismisses panel")
        }
        main { activity.finish() }
    }
    private fun live() {
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val client=(activity.application as ClipApplication).daemon
        main { client.connect(true) }
        until("Actual Shizuku connection") { client.connected() }
        val service=DaemonClient::class.java.getDeclaredField("remote").apply { isAccessible=true }.get(client) as IClipboardDaemon
        fun status()=service.status()
        fun awaitCount(count:Int) {
            val end=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime()<end) { if(status().getInt("count")==count)return;SystemClock.sleep(80) }
            error("Synthetic capture count did not reach $count")
        }
        var producerStarted=false
        fun produce(text:String,sensitive:Boolean=false,oversized:Boolean=false) {
            if(!producerStarted) {
                test.sendStatus(0,Bundle().apply { putString("stream","Producer package: ${test.context.packageName}, target: ${context.packageName}\n") })
                main { activity.startActivity(Intent().setClassName(test.context.packageName,SyntheticClipboardActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
                until("Synthetic producer is foreground") { field("visible")==false }
                SystemClock.sleep(350);producerStarted=true
            }
            context.sendBroadcast(Intent(SyntheticClipboardActivity.ACTION).setPackage(test.context.packageName)
                .putExtra("text",text).putExtra("sensitive",sensitive).putExtra("oversized",oversized))
        }
        val initial=status()
        checkThat(initial.getInt("uid")==2000 && initial.getBoolean("listening") && initial.getBoolean("storageVerified"),"Shell listener and durable private storage, API ${Build.VERSION.SDK_INT}")
        // Preserve the phone clipboard as an opaque ClipData object. Never print or inspect its contents.
        var original:ClipData?=null
        main { original=activity.getSystemService(ClipboardManager::class.java).primaryClip }
        try {
            checkThat(service.setLimit(20).getBoolean("ok"),"Set validation history limit")
            checkThat(service.clearHistory().getBoolean("ok"),"Clear only synthetic validation history")
            checkThat(service.setDuplicateMode(DuplicateMode.UNIQUE_TEXT.value).getBoolean("ok"),"Select exact unique-text policy")
            val nonce="ClipHistory self-test ${java.util.UUID.randomUUID()}"
            checkThat(service.armTest(nonce).getBoolean("ok"),"Arm live callback test")
            main { activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Validation",nonce)) }
            val deadline=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime()<deadline && status().getString("selfTest")!="PASS")SystemClock.sleep(60)
            checkThat(status().getString("selfTest")=="PASS" && status().getInt("count")==0,"Real self-test passes without saving nonce")
            produce("Synthetic validation background clip");awaitCount(1)
            until("Another app captures while history is backgrounded") { field("visible")==false }
            produce("Synthetic validation background clip");SystemClock.sleep(500)
            checkThat(status().getInt("count")==1,"Consecutive duplicates skipped")
            service.setPaused(true);produce("Synthetic validation pause canary");SystemClock.sleep(600)
            checkThat(status().getBoolean("paused") && status().getInt("count")==1,"Pause prevents capture")
            service.setPaused(false)
            var before=status().getLong("sensitiveSkipped")
            produce("Synthetic validation sensitive canary",sensitive=true);SystemClock.sleep(700)
            checkThat(status().getInt("count")==1 && status().getLong("sensitiveSkipped")>before,"Marked sensitive payload skipped")
            before=status().getLong("oversizedSkipped")
            produce("",oversized=true)
            val oversizeDeadline=SystemClock.elapsedRealtime()+8000
            while(SystemClock.elapsedRealtime()<oversizeDeadline && status().getLong("oversizedSkipped")==before)SystemClock.sleep(100)
            val oversizedStatus=status()
            log.append("Oversize counters: saved=${oversizedStatus.getLong("saved")}, count=${oversizedStatus.getInt("count")}, oversized=${oversizedStatus.getLong("oversizedSkipped")}, unsupported=${oversizedStatus.getLong("unsupportedSkipped")}\n")
            checkThat(oversizedStatus.getInt("count")==1 && oversizedStatus.getLong("oversizedSkipped")>before,"Oversized payload skipped")
            val unicode="Synthetic validation Unicode\nکوردی فارسی 中文 🙂\n  spaces  "
            produce(unicode);awaitCount(2)
            val id=service.page("validation Unicode",0,40).getLongArray("ids")!!.first()
            checkThat(service.entry(id).getString("text")==unicode,"Full Unicode and whitespace preserved")
            checkThat(service.entry(Long.MAX_VALUE).getBoolean("ok") && service.entry(Long.MAX_VALUE).getString("text")==null,"Live missing entry distinguished from error")
            service.clearHistory()
            fun captured(text:String) {
                val beforeSaved=status().getLong("saved")
                produce(text)
                val end=SystemClock.elapsedRealtime()+8000
                while(SystemClock.elapsedRealtime()<end && status().getLong("saved")==beforeSaved)SystemClock.sleep(30)
                check(status().getLong("saved")>beforeSaved) { "Synthetic distinct capture not acknowledged" }
            }
            val a="Synthetic duplicate A";val b="Synthetic duplicate B"
            captured(a)
            val oldId=service.page("",0,3).getLongArray("ids")!!.first()
            captured(b);captured(a);captured(b)
            val unique=service.page("",0,3)
            checkThat(status().getInt("count")==2 && unique.getLongArray("ids")!!.map { service.entry(it).getString("text") }==listOf(b,a),"Actual A/B/A/B produces exactly B,A")
            checkThat(service.entry(oldId).getString("text")==null,"Recopy invalidates the old ID without returning another text")
            val staleDelete=service.deleteEntry(oldId)
            checkThat(!staleDelete.getBoolean("ok") && staleDelete.getString("issue")=="ENTRY_MISSING","Stale deletion reports missing rather than success")
            checkThat(service.setDuplicateMode(DuplicateMode.CONSECUTIVE_ONLY.value).getBoolean("ok"),"Consecutive-only policy acknowledged")
            service.clearHistory();captured(a);captured(b);captured(a);captured(b)
            checkThat(status().getInt("count")==4,"Consecutive-only mode preserves four nonconsecutive copies")
            service.setDuplicateMode(DuplicateMode.UNIQUE_TEXT.value)
            PrivateHistory.invalidate()
            val offline=PrivateHistory.readOffline(context)
            checkThat(offline.entries.map { it.text }==listOf(b,a) && offline.duplicateMode==DuplicateMode.UNIQUE_TEXT,"Mirrored offline history agrees after policy cleanup")
            val dropped=status().getLong("queueDrops")
            val sessionStart=SystemClock.elapsedRealtime()
            repeat(50) { captured("Synthetic sustained copy $it") }
            checkThat(status().getInt("count")==20 && status().getLong("queueDrops")==dropped,"Sustained fifty-copy session retains limit without queue drops")
            log.append("Fifty-copy session: ${SystemClock.elapsedRealtime()-sessionStart} ms\n")
            main { activity.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            until("History returns after background capture") { field("visible")==true }
            capture("live-connected-api-${Build.VERSION.SDK_INT}")
        } finally {
            service.setPaused(true)
            main { activity.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            until("Foreground for clipboard restoration") { field("visible")==true }
            main {
                val clipboard=activity.getSystemService(ClipboardManager::class.java)
                original?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip()
            }
            service.clearHistory();service.setLimit(DEFAULT_LIMIT);service.setPaused(false)
        }
        connectionFailures(client,service)
    }
    private fun connectionFailures(client:DaemonClient,service:IClipboardDaemon) {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Persistence/permission failure fixtures require a disposable emulator" }
        test.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        val directory=File(context.applicationInfo.dataDir,"shared_prefs")
        val prefsFile=File(directory,"recorder_recovery.xml")
        val preferences=context.getSharedPreferences("recorder_recovery",Context.MODE_PRIVATE)
        fun writable(value:Boolean) {
            check(directory.setWritable(value,false) && prefsFile.setWritable(value,false)) { "Preference failure fixture unavailable" }
        }
        fun helperAbsent():Boolean=test.uiAutomation.executeShellCommand("ps -A -o NAME").use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { reader ->
                reader.lineSequence().none { it.trim()==context.packageName+":clipboard" }
            }
        }
        // The notification Stop action must retain supervision and report a failed
        // durable write, rather than quietly abandoning an active shell recorder.
        writable(false)
        try {
            main { context.startService(Intent(context,RecordingRecoveryService::class.java).setAction(RecordingRecoveryService.STOP)) }
            until("Notification reports failed durable Stop") {
                context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                    it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()==context.getString(R.string.recovery_stop_failed)
                }
            }
            checkThat(!client.preferences.options().explicitlyStopped && client.connected(),"Failed notification Stop retains recording state and supervision")
        } finally { writable(true) }

        val connection=DaemonClient::class.java.getDeclaredField("connection").apply { isAccessible=true }.get(client) as ServiceConnection
        val remote=DaemonClient::class.java.getDeclaredField("remote").apply { isAccessible=true }
        val stopped=CountDownLatch(1)
        var stoppedOk=false
        main {
            remote.set(client,null)
            connection.onServiceConnected(ComponentName(context,app.cliphistory.daemon.ClipboardUserService::class.java),service.asBinder())
            client.stopRecording { stoppedOk=it.getBoolean("ok");stopped.countDown() }
        }
        check(stopped.await(12,TimeUnit.SECONDS))
        checkThat(stoppedOk,"Stop during attachment is acknowledged")
        until("Stop during attachment removes the independent helper") { helperAbsent() }
        checkThat(PrivateHistory.readOffline(context).paused,"Stop during attachment saves pause before shutdown")

        main { client.connect(true) }
        until("Explicit Start reconnects after attachment stop") { client.connected() }
        val restarted=remote.get(client) as IClipboardDaemon
        checkThat(!restarted.status().getBoolean("paused"),"Explicit Start resumes a deliberately stopped recorder")
        check(preferences.edit().putBoolean("setup_completed",false).commit())
        writable(false)
        try {
            main { connection.onServiceConnected(ComponentName(context,app.cliphistory.daemon.ClipboardUserService::class.java),restarted.asBinder()) }
            until("Failed first-setup persistence removes attached helper") { !client.connected() && helperAbsent() }
            checkThat(!client.preferences.options().setupCompleted && PrivateHistory.readOffline(context).paused,
                "Failed setup is not reported complete and saves pause before removing capture")
        } finally { writable(true) }
        check(preferences.edit().putBoolean("setup_completed",true).commit())
    }
    fun run(suite:String):String {
        try { when(suite) { "regression"->regression();"capacity"->capacity();"layout"->layout();"live"->live();else->error("Unknown suite") } }
        catch(t:Throwable) { throw IllegalStateException(log.toString(),t) }
        return log.append("PASS $checks release assertions ($suite). Synthetic data only.\n").toString()
    }
}
