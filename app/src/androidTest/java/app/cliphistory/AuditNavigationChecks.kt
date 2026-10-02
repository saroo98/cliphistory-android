package app.cliphistory

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import app.cliphistory.client.PrivateHistory
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import app.cliphistory.ui.*

/** Test APK only. Exercises nested navigation against owned synthetic offline history. */
class AuditNavigationChecks(private val test:Instrumentation) {
    private val context get()=test.targetContext
    private lateinit var activity:MainActivity
    private var assertions=0
    private val log=StringBuilder()
    private fun main(block:()->Unit)=test.runOnMainSync { block() }
    private fun checkThat(value:Boolean,message:String) {
        check(value){message};assertions++;log.append("PASS $message\n")
    }
    private fun await(message:String,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+8000
        while(SystemClock.elapsedRealtime()<end) {
            var ready=false;main { ready=condition() }
            if(ready){checkThat(true,message);return}
            SystemClock.sleep(60)
        }
        error(message)
    }
    private fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun call(name:String,vararg args:Any) {
        MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==args.size }
            .apply { isAccessible=true }.invoke(activity,*args)
    }
    private fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList())
    private fun texts(root:View)=views(root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun page()=field("page") as View
    private fun scroll()=views(page()).filterIsInstance<ScrollView>().first()
    private fun click(root:View,id:Int) {
        val label=context.getString(id)
        val target=views(root).firstOrNull {
            it.isClickable && (it.contentDescription?.toString()==label || (it is TextView && it.text.toString()==label))
        }?:error("Missing clickable control: $label")
        checkThat(target.isEnabled,"Control enabled: $label");target.performClick()
    }
    private fun toolbarBack()=main { click(page(),R.string.back) }
    private fun systemBack()=test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun recreate() {
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        try {
            main { activity.recreate() }
            activity=test.waitForMonitorWithTimeout(monitor,8000) as? MainActivity?:error("Activity recreation timed out")
        } finally { test.removeMonitor(monitor) }
        await("History survives recreation") { (field("home") as HistoryHome).adapter.count==3 }
    }
    private fun openMenu() {
        click(activity.window.decorView,R.string.more_options)
        val popup=field("menu") as PopupWindow
        val choices=views(popup.contentView).filter { it.isClickable }.mapNotNull { it.contentDescription?.toString() }
        checkThat(choices==listOf(R.string.pause_recording,R.string.settings,R.string.diagnostics,R.string.help_privacy,R.string.clear_history).map { context.getString(it) },
            "Overflow contains only five durable actions")
    }
    private fun seed() {
        stopRecorderForFixture(test)
        val entries=listOf(Entry(3,3,"Audit newest text\nExact whitespace  "),Entry(2,2,"Audit second text"),Entry(1,1,"Audit third text"))
        val (a,b)=PrivateHistory.openForDaemon(context)
        listOf(a,b).forEach { FramedSlot(FdAccess(it,context.applicationInfo.uid)).use { slot ->
            slot.writeAndSync(SnapshotCodec.encode(Snapshot(1,137,4,false,entries)))
        } }
        PrivateHistory.invalidate()
        AppSettings(context).apply { appearance="system";welcome="never";backgroundSuggestion=false;returnAfterCopy=true }
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        await("Synthetic offline history loads") { (field("home") as HistoryHome).adapter.count==3 }
    }
    private fun settingsState() {
        main {
            openMenu();click((field("menu") as PopupWindow).contentView,R.string.settings)
        }
        await("Settings opens from overflow") { field("pageKind")=="settings" }
        main {
            val labels=texts(page())
            checkThat(labels.contains(context.getString(R.string.audit_limit_summary,137)),"Settings displays stored history capacity")
            checkThat(labels.contains(context.getString(R.string.system)),"Settings distinguishes System appearance")
            checkThat(views(page()).filterIsInstance<TextView>().single { it.text.toString()==context.getString(R.string.audit_offline_controls) }.visibility==View.VISIBLE,
                "Offline Settings explains disabled recorder controls")
            checkThat(!views(page()).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.pause_recording) }.isEnabled,
                "Pause remains unavailable offline")
            checkThat(labels.indexOf(context.getString(R.string.stop_recorder))<labels.indexOf(context.getString(R.string.audit_recovery_heading)) &&
                labels.indexOf(context.getString(R.string.audit_recovery_heading))<labels.indexOf(context.getString(R.string.automatic_recovery)),
                "Recovery follows separate recorder controls")
            val saved=Bundle(field("latest") as Bundle)
            val settingsPage=field("settingsPage") as SettingsPage.Page
            settingsPage.update(Bundle().apply { putString("issue","HISTORY_READ_FAILED") })
            checkThat(texts(page()).count { it==context.getString(R.string.not_confirmed) }==2,
                "Unreadable history does not invent a capacity or duplicate policy")
            settingsPage.update(saved)
            checkThat(texts(page()).contains(context.getString(R.string.audit_limit_summary,137)),
                "Confirmed Settings values return after a successful read")
        }
    }
    private fun navigation() {
        var settingsScroll=0;var helpScroll=0;var licensesScroll=0
        main { scroll().scrollTo(0,scroll().getChildAt(0).height) }
        await("Settings scroll reaches About") { scroll().scrollY>0 }
        main { settingsScroll=scroll().scrollY;click(page(),R.string.always_on_guide) }
        await("Settings guide opens") { field("pageKind")=="help" }
        main { scroll().scrollTo(0,scroll().getChildAt(0).height) }
        await("Guide scroll reaches licenses") { scroll().scrollY>0 }
        main { helpScroll=scroll().scrollY;click(page(),R.string.licenses) }
        await("Licenses opens from guide") { field("pageKind")=="licenses" }
        main { scroll().scrollTo(0,400) }
        await("Licenses is scrollable") { scroll().scrollY>0 }
        main { licensesScroll=scroll().scrollY }
        recreate()
        await("Licenses page and scroll survive recreation") { field("pageKind")=="licenses" && scroll().scrollY==licensesScroll }
        toolbarBack()
        await("Toolbar Back restores guide scroll") { field("pageKind")=="help" && scroll().scrollY==helpScroll }
        systemBack()
        await("System Back restores Settings and scroll") { field("pageKind")=="settings" && scroll().scrollY==settingsScroll }
        main { click(page(),R.string.always_on_guide) }
        await("Guide reopens from restored Settings") { field("pageKind")=="help" }
        main { scroll().scrollTo(0,200) }
        await("Guide has a scroll position to restore") { scroll().scrollY>0 }
        main { helpScroll=scroll().scrollY }
        recreate()
        await("Guide page and scroll survive recreation") { field("pageKind")=="help" && scroll().scrollY==helpScroll }
        toolbarBack()
        await("Toolbar Back restores originating Settings after recreation") { field("pageKind")=="settings" && scroll().scrollY==settingsScroll }
        toolbarBack()
        await("Settings Back returns Home") { field("pageKind")=="" }
        main { openMenu();click((field("menu") as PopupWindow).contentView,R.string.help_privacy) }
        await("Home guide opens") { field("pageKind")=="help" }
        systemBack()
        await("Home guide Back returns Home") { field("pageKind")=="" }
    }
    private fun copyLabels() {
        main { call("entryActions",(field("home") as HistoryHome).adapter.getItem(0)) }
        test.waitForIdleSync()
        main {
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single { it.isShowing }
            checkThat(texts(dialog.window!!.decorView).contains(context.getString(R.string.copy_return)),"Action sheet identifies copy-and-return")
            dialog.dismiss();AppSettings(context).returnAfterCopy=false
            call("entryActions",(field("home") as HistoryHome).adapter.getItem(0))
        }
        test.waitForIdleSync()
        main {
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single { it.isShowing }
            checkThat(texts(dialog.window!!.decorView).contains(context.getString(R.string.copy)) &&
                !texts(dialog.window!!.decorView).contains(context.getString(R.string.copy_return)),"Action sheet identifies copy-and-stay")
            click(dialog.window!!.decorView,R.string.copy)
        }
        await("Copy-and-stay confirms success") { texts(activity.window.decorView).contains(context.getString(R.string.copied)) }
        main {
            checkThat(!activity.isFinishing,"Copy-and-stay retains Activity")
            checkThat(activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()=="Audit newest text\nExact whitespace  ",
                "Action-sheet copy preserves exact complete text")
            AppSettings(context).returnAfterCopy=true
        }
    }
    private fun tileNavigation() {
        main { call("showSettings") }
        context.startActivity(Intent(context,MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_HISTORY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        await("Explicit tile destination returns Settings to History") { field("pageKind")=="" }
        main {
            checkThat(activity.intent.action==null,"History destination is consumed")
            call("showSettings")
        }
        recreate()
        await("Later recreation preserves Settings after consumed tile destination") { field("pageKind")=="settings" }
        toolbarBack()
    }
    fun run():String {
        check(BuildConfig.DEBUG && (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || BuildConfig.APPLICATION_ID.endsWith(".validation"))) {
            "Audit navigation tests require an emulator or isolated validation app"
        }
        try {
            seed();settingsState();navigation();copyLabels();tileNavigation()
            return "PASS: $assertions audit navigation assertions. Owned synthetic offline history; no Shizuku runtime claim.\n$log"
        } finally { if(::activity.isInitialized)main { activity.finish() } }
    }
}
