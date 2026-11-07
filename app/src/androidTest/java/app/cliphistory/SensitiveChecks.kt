package app.cliphistory

import android.app.Dialog
import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ui.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** All seeded history and clipboard writes are restricted to the owned disposable emulator. */
class SensitiveChecks(private val test:Instrumentation) {
    private val context get()=test.targetContext
    private lateinit var activity:MainActivity
    private val client get()=(context.applicationContext as ClipApplication).daemon
    private val log=StringBuilder()
    private var count=0
    private fun main(action:()->Unit)=test.runOnMainSync(action)
    private fun verify(value:Boolean,message:String) { check(value){message};count++;log.append("PASS $message\n");test.sendStatus(0,Bundle().apply { putString("stream","PASS $message\n") }) }
    private fun await(message:String,condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+12000
        while(SystemClock.elapsedRealtime()<deadline) { if(condition()){verify(true,message);return};SystemClock.sleep(40) };error(message)
    }
    private fun nodes(root:android.view.accessibility.AccessibilityNodeInfo?):List<android.view.accessibility.AccessibilityNodeInfo> = if(root==null)emptyList()
        else listOf(root)+(0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun call(name:String,vararg args:Any) { MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==args.size }.apply { isAccessible=true }.invoke(activity,*args) }
    private fun texts()=views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    private fun dialog()=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single { it.isShowing }
    private fun click(root:View,label:String) {
        var view=views(root).first { it is TextView && it.text.toString()==label }
        while(!view.isClickable && view.parent is View)view=view.parent as View
        check(view.isEnabled);view.performClick()
    }
    private fun loaded(expected:Int) {
        try { await("History loads $expected available entries") { var result=false;main { result=(field("home") as HistoryHome).adapter.count==expected };result } }
        catch(error:Throwable) {
            main { log.append("Load failure metadata: visible=${field("visible")}, count=${(field("home") as HistoryHome).adapter.count}, issue=${(field("latest") as Bundle).getString("issue")}, initialized=${client.initialized}, boot=${DaemonClient::class.java.getDeclaredField("historyBoot").apply { isAccessible=true }.get(client)}\n") }
            log.append("Authenticated available fixture entries: ${PrivateHistory.readOffline(context).entries.size}\n")
            throw error
        }
    }
    private fun launch() {
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    private fun seed(deadlineMs:Long?=null,theme:String="light") {
        val now=AndroidHistoryClock(context).now()
        val entries=listOf(Entry(3,3,"Synthetic private 🌿\n exact spaces  ",true,deadlineMs?.let { Expiry(now.boot,now.elapsed+it) }),
            Entry(2,2,"Synthetic normal second"),Entry(1,1,"Synthetic normal third"))
        writeFixturePayload(test,SnapshotCodec.encode(Snapshot(10,100,4,false,entries,captureSensitive=true)))
        AppSettings(context).apply { appearance=theme;returnAfterCopy=false;welcome="never";backgroundSuggestion=false;allowScreenshots=true }
        launch()
        deadlineMs?.let { log.append("Fixture remaining milliseconds after launch: ${now.elapsed+it-SystemClock.elapsedRealtime()}\n") }
        loaded(3)
    }
    private fun capture(name:String) {
        main {
            val view=activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            File(context.filesDir,"ui-evidence").apply { mkdirs() }.resolve(name+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun offlineUi() {
        for(theme in listOf("light","dark")) {
            seed(theme=theme)
            main { call("showSettings") }
            val controls=views(activity.window.decorView).filterIsInstance<Switch>()
            main {
                val capture=controls.single { it.text.toString()==context.getString(R.string.capture_sensitive) }
                val auto=controls.single { it.text.toString()==context.getString(R.string.auto_delete_sensitive) }
                verify(capture.isChecked && auto.isChecked && !capture.isEnabled && !auto.isEnabled,"$theme offline sensitive controls show saved state and are disabled")
                val duration=views(activity.window.decorView).single { it.tag==SettingsPage.Action.SENSITIVE_DURATION.name }
                verify(!duration.isEnabled,"$theme offline duration mutation is disabled")
                val privacy=controls.single { it.text.toString()==context.getString(R.string.allow_screenshots) }
                privacy.performClick();verify(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0,"$theme screenshot preference still works")
                privacy.performClick()
                val scroll=views(activity.window.decorView).filterIsInstance<ScrollView>().first()
                scroll.scrollTo(0,(capture.parent as View).top+capture.top-100)
            }
            SystemClock.sleep(150);capture("sensitive-settings-$theme")
            main { activity.finish() };test.waitForIdleSync()
        }
        seed(8000)
        main {
            val hidden=context.getString(R.string.sensitive_hidden)
            val all=views(activity.window.decorView)
            verify(texts().contains(hidden) && texts().none { it.contains("Synthetic private") },"Sensitive list entry is shown as hidden")
            verify(all.none { it.contentDescription?.contains("Synthetic private")==true },"Sensitive list entry is not spoken by TalkBack")
        }
        main { call("preview",3L) }
        await("Sensitive full text opens without an authentication step") { var found=false;main { found=texts().contains("Synthetic private 🌿\n exact spaces  ") };found }
        await("Expired full text closes and disappears from history") { var found=false;main { found=field("pageKind")=="" && (field("home") as HistoryHome).adapter.count==2 };found }
        verify(PrivateHistory.readOffline(context).entries.size==2,"Expired text is inaccessible offline")
        main { activity.finish() };seed(8000)
        lateinit var quick:QuickCopyDialog
        main { quick=QuickCopyDialog(activity){ };quick.show() }
        val hiddenLabel=context.getString(R.string.sensitive_hidden)
        await("Quick panel shows the unexpired sensitive entry as hidden") { var found=false;main {
            val shown=views(quick.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            found=shown.contains(hiddenLabel) && shown.none { it.startsWith("Synthetic private") } };found }
        await("Quick panel refreshes while held across expiry") { var hidden=false;main { hidden=views(quick.window!!.decorView).filterIsInstance<TextView>().none { it.text.toString()==hiddenLabel } };hidden }
        main { quick.dismiss();activity.finish() };seed(6000)
        val retryExpiry=PrivateHistory.readOffline(context).entries.first().expiry!!
        log.append("History retry fixture remaining milliseconds: ${retryExpiry.elapsed-SystemClock.elapsedRealtime()}\n")
        verify(retryExpiry.elapsed-SystemClock.elapsedRealtime()>500,"Retry fixture remains unexpired after launch")
        main { call("historyCopyFailure",3L,retryExpiry) }
        await("History copy retry is visible") { var showing=false;main { showing=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().any { it.isShowing } };showing }
        await("Copy retry disappears at the entry deadline") { var gone=false;main { gone=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().none { it.isShowing } };gone }
        main { activity.finish() };seed(6000)
        main {
            quick=QuickCopyDialog(activity){ };quick.show()
        }
        await("Quick panel is loaded before retry fixture") { var ready=false;main { ready=views(quick.window!!.decorView).filterIsInstance<Button>().any { it.text.toString()==hiddenLabel } };ready }
        main {
            val expiry=PrivateHistory.readOffline(context).entries.first().expiry!!
            QuickCopyDialog::class.java.getDeclaredMethod("showCopyFailure",Long::class.javaPrimitiveType,Expiry::class.java).apply { isAccessible=true }.invoke(quick,3L,expiry)
        }
        await("Quick copy retry is visible before expiry") { var present=false;main { present=views(quick.window!!.decorView).filterIsInstance<Button>().any { it.text.toString()==context.getString(R.string.port_retry) } };present }
        await("Quick retry is withheld at expiry") { var absent=false;main { absent=views(quick.window!!.decorView).filterIsInstance<Button>().none { it.text.toString()==context.getString(R.string.port_retry) } };absent }
        main { quick.dismiss();activity.finish() }
    }
    private fun live() {
        seed()
        main { client.connect(true) }
        await("Real Shizuku recorder attaches encrypted storage") { client.connected() }
        val service=DaemonClient::class.java.getDeclaredField("remote").apply { isAccessible=true }.get(client) as IClipboardDaemon
        fun durableStates():List<Snapshot> = listOf("history-a.bin","history-b.bin").mapIndexed { index,name ->
            val file=File(context.noBackupFilesDir,name)
            val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
            PrivateHistory.encryptedSlot(context,descriptor,index).use { SnapshotCodec.decode(it.read()) }
        }
        fun policy(capture:Boolean,auto:Boolean=true,minutes:Int=5) { verify(service.setSensitivePolicy(capture,auto,minutes).getBoolean("ok"),"Sensitive policy is durably acknowledged") }
        fun produce(text:String,marked:Boolean) {
            main { activity.startActivity(Intent(SyntheticClipboardActivity.ACTION).setClassName(test.context.packageName,SyntheticClipboardActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("text",text).putExtra("sensitive",marked)) }
        }
        fun entries()=service.page("",0,40).getLongArray("ids")!!.map { service.entry(it) }
        // Emulator clipboard sharing re-posts each copy from the host as plain text, which drops
        // Android's sensitive flag for every app. Detect that instead of reporting a false failure.
        fun sensitiveFlagSurvives():String {
            produce("Synthetic_private",true);SystemClock.sleep(1500)
            main { activity.startActivity(Intent(SyntheticClipboardActivity.READ_SYNTHETIC_STATE).setClassName(test.context.packageName,SyntheticClipboardActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
            var state=""
            await("Synthetic clipboard state is readable") {
                state=nodes(test.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.firstOrNull { it.startsWith("Synthetic clipboard state:") }.orEmpty()
                state.isNotEmpty()
            }
            return state
        }
        val environment=sensitiveFlagSurvives()
        log.append("$environment\n")
        check(environment.endsWith("marked=true")) {
            "EMULATOR_CLIPBOARD_SYNC_STRIPS_SENSITIVE_FLAG: turn off the emulator's clipboard sharing (Extended controls > Settings), then rerun."
        }
        try {
            service.clearHistory();policy(false)
            val skipped=service.status().getLong("sensitiveSkipped")
            produce("Synthetic source-sensitive",true)
            await("Sensitive capture is off by default policy") { service.status().getLong("sensitiveSkipped")>skipped }
            verify(service.status().getInt("count")==0,"Sensitive flag is skipped without saving its text")
            policy(true,true,1)
            val prepared=service.status()
            log.append("Recorder status before timed capture: bootKnown=${prepared.getBoolean("bootIdentityKnown")}, listening=${prepared.getBoolean("listening")}, paused=${prepared.getBoolean("paused")}, issue=${prepared.getString("issue")}\n")
            verify(prepared.getBoolean("bootIdentityKnown"),"Recorder establishes the Android boot identity")
            produce("Synthetic source-sensitive",true)
            await("Opt-in captures source-sensitive text") { service.status().getInt("count")==1 }
            val initial=entries().first();val expiry=initial.getLong("expiryTime")
            verify(initial.getBoolean("sensitive") && expiry>SystemClock.elapsedRealtime(),"Source-sensitive metadata reaches the entry response")
            verify(durableStates().all { it.entries.single().text=="Synthetic source-sensitive" && it.entries.single().sensitive },"Both encrypted snapshots authenticate and preserve the sensitive capture")
            verify(service.status().getLong("sensitiveSaved")>0,"Saved and skipped sensitive counters are distinct")
            produce("Synthetic separator",false)
            await("Normal capture still works") { service.status().getInt("count")==2 }
            produce("Synthetic source-sensitive",false)
            await("Recopy moves the existing exact text to the top") { entries().first().getString("text")=="Synthetic source-sensitive" }
            verify(entries().first().getBoolean("sensitive") && entries().first().getLong("expiryTime")==expiry,"Unmarked recopy preserves sensitivity and the original deadline")
            val id=service.page("",0,3).getLongArray("ids")!!.first()
            main { activity.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            await("History foregrounds for copy") { var visible=false;main { visible=field("visible")==true };visible }
            main { activity.getSystemService(ClipboardManager::class.java).clearPrimaryClip();call("copyEntry",id) }
            await("History copy retains Android's sensitive preview flag") { var marked=false;main { marked=activity.getSystemService(ClipboardManager::class.java).primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)==true };marked }
            lateinit var quick:QuickCopyDialog
            main { activity.getSystemService(ClipboardManager::class.java).clearPrimaryClip();quick=QuickCopyDialog(activity){ };quick.show() }
            val hiddenLabel=context.getString(R.string.sensitive_hidden)
            await("Sensitive quick-copy entry is listed but hidden") { var present=false;main {
                val buttons=views(quick.window!!.decorView).filterIsInstance<Button>().map { it.text.toString() }
                present=buttons.contains(hiddenLabel) && buttons.none { it=="Synthetic source-sensitive" } };present }
            main { click(quick.window!!.decorView,hiddenLabel) }
            await("Quick copy dismisses and retains the sensitive flag") { var marked=false;main { marked=!quick.isShowing && activity.getSystemService(ClipboardManager::class.java).primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)==true };marked }
            main { call("preview",id) }
            await("Sensitive full-text preview is selectable") { var ready=false;main { ready=views(activity.window.decorView).filterIsInstance<TextView>().any { it.tag=="preview-text" && it.text.toString()=="Synthetic source-sensitive" } };ready }
            main {
                val selected=views(activity.window.decorView).filterIsInstance<TextView>().single { it.tag=="preview-text" }
                activity.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
                selected.requestFocus();android.text.Selection.setSelection(selected.text as android.text.Spannable,0,9)
                selected.onTextContextMenuItem(android.R.id.copy)
            }
            await("Native selected-text copy preserves sensitivity") { var marked=false;main { val clip=activity.getSystemService(ClipboardManager::class.java).primaryClip;marked=clip?.getItemAt(0)?.text?.toString()=="Synthetic" && clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)==true };marked }
            main { call("closePage") }
            policy(false)
            verify(service.status().getInt("count")==1 && entries().none { it.getBoolean("sensitive") },"Disabling capture purges sensitive text while retaining normal text")
            // Exercise each real UI control and native duration input, with persisted acknowledgements.
            main { call("showSettings") }
            await("Connected sensitive control is available") { var enabled=false;main { enabled=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.capture_sensitive) }.isEnabled };enabled }
            main { views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.capture_sensitive) }.performClick() }
            await("Capture switch saves true") { service.status().getBoolean("captureSensitive") }
            await("Acknowledged capture switch is available") { var ready=false;main {
                val control=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.capture_sensitive) }
                ready=control.isChecked && control.isEnabled
            };ready }
            main {
                // A control may acknowledge its RPC before Main's next page read.
                // Hold that presentation snapshot stale while a real policy RPC runs.
                val stale=Bundle(field("latest") as Bundle).apply { putBoolean("captureSensitive",false);putBoolean("autoDeleteSensitive",false) }
                MainActivity::class.java.getDeclaredField("latest").apply { isAccessible=true }.set(activity,stale)
                call("saveSensitiveDuration",6)
                val deadline=SystemClock.elapsedRealtime()+3000
                while(service.status().getInt("sensitiveMinutes")!=6 && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(10)
                val saved=service.status()
                verify(saved.getInt("sensitiveMinutes")==6 && saved.getBoolean("captureSensitive") && saved.getBoolean("autoDeleteSensitive"),"Changing duration preserves capture and automatic deletion despite a stale presentation snapshot")
            }
            main { call("sensitiveDuration") }
            main { click(dialog().window!!.decorView,context.resources.getQuantityString(R.plurals.sensitive_minutes,15,15)) }
            await("Native duration choice saves 15 minutes") { service.status().getInt("sensitiveMinutes")==15 }
            main { call("customSensitiveDuration");val field=views(dialog().window!!.decorView).filterIsInstance<EditText>().single();field.setText("0");click(dialog().window!!.decorView,context.getString(R.string.save));verify(field.error!=null,"Custom duration rejects zero")
                field.setText("1441");click(dialog().window!!.decorView,context.getString(R.string.save));verify(field.error!=null,"Custom duration rejects 1441")
                field.setText("7");click(dialog().window!!.decorView,context.getString(R.string.save)) }
            await("Custom duration saves seven minutes") { service.status().getInt("sensitiveMinutes")==7 }
            main { views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.auto_delete_sensitive) }.performClick() }
            main { verify(service.status().getBoolean("autoDeleteSensitive"),"Indefinite retention does not save before confirmation");click(dialog().window!!.decorView,context.getString(R.string.cancel)) }
            verify(service.status().getBoolean("autoDeleteSensitive"),"Cancel preserves auto-delete")
            main { views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.auto_delete_sensitive) }.performClick();click(dialog().window!!.decorView,context.getString(R.string.sensitive_indefinite_confirm)) }
            await("Confirmed indefinite retention saves false") { !service.status().getBoolean("autoDeleteSensitive") }
            await("Indefinite retention is reflected by the available switch") { var saved=false;main { val control=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.auto_delete_sensitive) };saved=!control.isChecked && control.isEnabled };saved }
            main { views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.auto_delete_sensitive) }.performClick() }
            await("Auto-delete can be re-enabled") { service.status().getBoolean("autoDeleteSensitive") }
            policy(true,true,1);service.clearHistory();produce("Synthetic scheduled expiry",true)
            await("Timed sensitive text is stored") { service.status().getInt("count")==1 }
            val until=service.entry(service.page("",0,3).getLongArray("ids")!!.first()).getLong("expiryTime")
            log.append("Waiting for real elapsed-realtime deadline; no wall-clock manipulation.\n")
            while(SystemClock.elapsedRealtime()<until+100)SystemClock.sleep(minOf(1000,(until+100-SystemClock.elapsedRealtime()).coerceAtLeast(1)))
            await("Native deadline removes timed text from both snapshots") {
                service.status().getInt("count")==0 && try { durableStates().all { it.entries.isEmpty() } } catch (_:java.io.IOException) { false }
            }
            verify(PrivateHistory.readOffline(context).entries.isEmpty(),"Offline access agrees with the completed durable expiry")
            verify(service.page("",0,3).getInt("total")==0,"Expired text is not pageable")
        } finally {
            service.setPaused(true);service.setSensitivePolicy(false,true,5);service.clearHistory()
            main { activity.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            SystemClock.sleep(200)
            main { activity.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
            service.setPaused(false)
        }
    }
    fun run(mode:String):String {
        requireDisposableEmulator(test)
        try { when(mode) { "sensitive-ui"->offlineUi();"sensitive-live"->live();else->error("Unknown sensitive mode") } }
        catch(error:Throwable) { throw IllegalStateException(log.toString(),error) }
        return log.append("PASS $count sensitive assertions ($mode). Synthetic emulator only.\n").toString()
    }
}
