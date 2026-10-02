package app.cliphistory

import android.app.*
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ui.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/** Test APK only. Every history and clipboard fixture requires a disposable emulator. */
class HtmlPortChecks(private val test:Instrumentation,private val arguments:Bundle=Bundle()) {
    private val context get()=test.targetContext
    private val client get()=(context.applicationContext as ClipApplication).daemon
    private lateinit var activity:MainActivity
    private var assertions=0
    private val log=StringBuilder()
    private val lateText=(1..80).joinToString("\n") { line ->
        if(line==55)"lateNeedle · کوردی 中文 🙂 · LATENEEDLE\n  exact spaces  "
        else "Synthetic paragraph $line keeps its complete text and original spacing.  "
    }
    private fun main(action:()->Unit) {
        if(Looper.myLooper()==Looper.getMainLooper())action() else test.runOnMainSync { action() }
    }
    private fun checkThat(value:Boolean,message:String) {
        check(value){message};assertions++;log.append("PASS $message\n")
        test.sendStatus(0,Bundle().apply { putString("stream","PASS $message\n") })
    }
    private fun await(message:String,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+12_000
        while(SystemClock.elapsedRealtime()<end) {
            var ready=false;main { ready=condition() }
            if(ready){checkThat(true,message);return}
            SystemClock.sleep(60)
        }
        error(message)
    }
    private fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun call(name:String,vararg values:Any) {
        MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==values.size }
            .apply { isAccessible=true }.invoke(activity,*values)
    }
    private fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList())
    private fun texts(root:View)=views(root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun home()=field("home") as HistoryHome
    private fun page()=field("page") as View
    private fun scroll()=views(page()).filterIsInstance<ScrollView>().single()
    private fun dialog()=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single { it.isShowing }
    private fun control(root:View,id:Int):View {
        val label=context.getString(id)
        return views(root).firstOrNull { it.isClickable && (it.contentDescription?.toString()==label || it is TextView && it.text.toString()==label) }
            ?:error("Missing native control: $label")
    }
    private fun click(root:View,id:Int) {
        val target=control(root,id);checkThat(target.isEnabled,"Enabled control: ${context.getString(id)}");target.performClick()
    }
    private fun tagged(name:String)=views(page()).single { it.tag==name }
    private fun clickTagged(name:String) {
        val target=tagged(name);checkThat(target.isEnabled && target.isClickable,"Native Settings action $name is available");target.performClick()
    }
    private fun summary(name:String)=views(tagged(name)).filterIsInstance<TextView>().last().text.toString()
    private fun rotate() {
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        try {
            val next=if(activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            main { activity.requestedOrientation=next }
            activity=test.waitForMonitorWithTimeout(monitor,10_000) as? MainActivity?:error("HTML port rotation timed out")
        } finally { test.removeMonitor(monitor) }
        test.waitForIdleSync()
    }
    private fun capture(name:String,window:Window=activity.window) {
        test.waitForIdleSync()
        main {
            val view=window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val theme=if(view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)"dark" else "light"
            val fontScale=view.resources.configuration.fontScale
            File(context.filesDir,"ui-evidence").apply { mkdirs() }.resolve("html-port-$theme-font$fontScale-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun seed(entries:List<Entry> = listOf(Entry(3,3,lateText),Entry(2,2,"Synthetic second\nExact whitespace  "),Entry(1,1,"Synthetic oldest text")),limit:Int=100,paused:Boolean=false,theme:String=arguments.getString("theme","light")!!) {
        check(theme in listOf("light","dark")) { "HTML port capture theme must be light or dark" }
        stopRecorderForFixture(test)
        if(::activity.isInitialized)main { activity.finish() }
        val (a,b)=PrivateHistory.openForDaemon(context)
        val snapshot=Snapshot(1,limit,(entries.maxOfOrNull { it.id }?:0)+1,paused,entries)
        listOf(a,b).forEach { FramedSlot(FdAccess(it,context.applicationInfo.uid)).use { slot -> slot.writeAndSync(SnapshotCodec.encode(snapshot)) } }
        PrivateHistory.invalidate()
        AppSettings(context).apply {
            appearance=theme;welcome="never";backgroundSuggestion=false;allowScreenshots=false
            hideRecents=true;returnAfterCopy=false;tileEnabled=true;tileMode="quick";motion=true
        }
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        await("Owned synthetic history loads (${entries.size} entries)") { home().adapter.count==minOf(entries.size,40) && (field("latest") as Bundle).containsKey("limit") }
    }
    private fun settings() {
        seed();main { call("showSettings") }
        main {
            val switches=views(page()).filterIsInstance<Switch>()
            val expected=listOf(R.string.pause_recording,R.string.automatic_recovery,R.string.resume_after_boot,R.string.background_suggestion,
                R.string.tile_enabled,R.string.return_after_copy,R.string.allow_screenshots,R.string.hide_recents).map { context.getString(it) }
            checkThat(switches.map { it.text.toString() }==expected,"Settings contains exactly the eight existing native Switch labels")
            checkThat(views(page()).none { it is RadioButton },"Choice preferences are rows in Settings")
            checkThat(!switches.first().isEnabled && !tagged("DUPLICATES").isEnabled && !tagged("LIMIT").isEnabled,"Offline recorder mutations remain disabled")
            checkThat(tagged("START").visibility==View.VISIBLE && tagged("STOP").visibility==View.GONE,"Offline Settings shows only the Connect or Start recorder action")
            val summaries=mapOf("DUPLICATES" to R.string.duplicates_unique,"TILE_MODE" to R.string.quick_copy,"MOTION" to R.string.motion_system,
                "WELCOME_FREQUENCY" to R.string.welcome_off,"APPEARANCE" to if(AppSettings(context).appearance=="dark")R.string.dark else R.string.light)
            summaries.forEach { (name,id) ->
                checkThat(summary(name)==context.getString(id),"Settings $name displays its persisted summary")
                val node=tagged(name).createAccessibilityNodeInfo()
                checkThat(node.className==Button::class.java.name && node.contentDescription.toString().contains(summary(name)),"Settings $name exposes its summary and button role")
            }
            checkThat(summary("LIMIT")==context.getString(R.string.audit_limit_summary,100),"History limit summary uses the saved capacity")
            val content=scroll().getChildAt(0) as LinearLayout
            fun heading(id:Int)=(0 until content.childCount).single { index ->
                (content.getChildAt(index) as? TextView)?.let { it.isAccessibilityHeading && it.text.toString()==context.getString(id) }==true
            }
            val automatic=heading(R.string.html_port_automatic_resumption);val optional=heading(R.string.html_port_optional_setup);val quick=heading(R.string.quick_access)
            checkThat((automatic+1 until optional).count { content.getChildAt(it) is Switch }==2,"Automatic resumption contains two native controls")
            checkThat((optional+1 until quick).count { content.getChildAt(it).isClickable }==4,"Optional setup contains its four separate actions")
            val welcome=heading(R.string.html_port_welcome_heading);val about=heading(R.string.about)
            checkThat(welcome>heading(R.string.appearance) && about>welcome,"Welcome is a separate group before About")
            checkThat(texts(page()).contains(context.getString(R.string.version_license,BuildConfig.VERSION_NAME)),"About shows the actual build version")
            checkThat(tagged("SOURCE").isClickable && tagged("LICENSES").isClickable,"About exposes source and license actions")
        }
        capture("settings")
        fun choose(name:String,labels:List<Int>,selected:Int,changed:()->Boolean) {
            main {
                clickTagged(name)
                val radios=views(dialog().window!!.decorView).filterIsInstance<RadioButton>()
                checkThat(radios.map { it.text.toString() }==labels.map { context.getString(it) },"$name opens the native radio choices")
                radios[selected].performClick();click(dialog().window!!.decorView,R.string.done)
            }
            await("$name selection and row summary persist") { changed() }
        }
        choose("TILE_MODE",listOf(R.string.quick_copy,R.string.open_full_history),1) { AppSettings(context).tileMode=="history" && summary("TILE_MODE")==context.getString(R.string.open_full_history) }
        choose("MOTION",listOf(R.string.motion_system,R.string.html_port_motion_reduced),1) { !AppSettings(context).motion && summary("MOTION")==context.getString(R.string.html_port_motion_reduced) }
        choose("WELCOME_FREQUENCY",listOf(R.string.welcome_once,R.string.welcome_each,R.string.welcome_off),1) { AppSettings(context).welcome=="each" && summary("WELCOME_FREQUENCY")==context.getString(R.string.welcome_each) }
        main { clickTagged("APPEARANCE") }
        main {
            checkThat(views(dialog().window!!.decorView).filterIsInstance<RadioButton>().map { it.text.toString() }==listOf(R.string.system,R.string.light,R.string.dark).map { context.getString(it) },"Appearance opens three native radio choices")
            val handle=control(dialog().window!!.decorView,R.string.dismiss_panel) as ViewGroup
            val grip=handle.getChildAt(0).background as GradientDrawable
            checkThat(contrast(grip.color!!.defaultColor,activity.getColor(R.color.surface))>=3.0,"Selected-theme sheet grip remains distinct from its surface")
            click(dialog().window!!.decorView,R.string.done)
            clickTagged("LICENSES")
        }
        await("Licenses opens directly from About") { field("pageKind")=="licenses" }
        main { checkThat(texts(page()).any { it.contains("Permission is hereby granted") && it.contains("END OF TERMS AND CONDITIONS") },"About reaches complete runtime license notices");click(page(),R.string.back) }
        await("License Back returns to Settings") { field("pageKind")=="settings" }
        val configured=CountDownLatch(1)
        main { client.updateRecovery(automatic=false,afterBoot=true) { checkThat(it,"Recovery fixture preferences saved");configured.countDown() } }
        check(configured.await(8,TimeUnit.SECONDS))
        await("Reboot choice stays checked and disabled while recovery is off") {
            val boot=views(page()).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.resume_after_boot) }
            boot.isChecked && !boot.isEnabled && texts(page()).contains(context.getString(R.string.html_port_boot_disabled))
        }
        main { AppSettings(context).welcome="never";captureSettingsContrast();click(page(),R.string.back) }
    }
    private fun contrast(a:Int,b:Int):Double {
        fun luminance(color:Int):Double {
            fun channel(value:Int):Double { val v=value/255.0;return if(v<=.04045)v/12.92 else ((v+.055)/1.055).pow(2.4) }
            return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color))
        }
        val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    private fun captureSettingsContrast() {
        val toggle=views(page()).filterIsInstance<Switch>().single { it.text.toString()==context.getString(R.string.allow_screenshots) }
        for(checked in listOf(false,true)) {
            val state=if(checked)intArrayOf(android.R.attr.state_enabled,android.R.attr.state_checked) else intArrayOf(android.R.attr.state_enabled,-android.R.attr.state_checked)
            val thumb=toggle.thumbTintList!!.getColorForState(state,0);val track=toggle.trackTintList!!.getColorForState(state,0)
            checkThat(contrast(thumb,track)>=3.0,"Native Switch thumb remains distinct in ${if(checked)"On" else "Off"} state")
            if(!checked)checkThat(contrast(track,activity.getColor(R.color.canvas))>=3.0,"Off Switch track remains distinct from its page")
        }
    }
    private fun help() {
        main { call("showSettings") }
        test.waitForIdleSync()
        await("New Settings page is laid out before its scroll fixture") {
            field("pageKind")=="settings" && scroll().isLaidOut && scroll().height>0 && scroll().getChildAt(0).height>scroll().height
        }
        main { scroll().scrollTo(0,500) }
        await("Settings has a scroll position to return to") { scroll().scrollY>0 }
        var settingsY=0;var helpY=0
        main { settingsY=scroll().scrollY;clickTagged("GUIDE") }
        await("Guide opens from Settings") { field("pageKind")=="help" }
        test.waitForIdleSync()
        await("New Help page is laid out before its local jump") { scroll().isLaidOut && scroll().height>0 && scroll().getChildAt(0).height>scroll().height }
        capture("help-top")
        main {
            val content=scroll().getChildAt(0) as LinearLayout
            val children=(0 until content.childCount).map { content.getChildAt(it) }
            val headings=listOf(R.string.html_port_setup_install,R.string.html_port_setup_pair,R.string.html_port_setup_start,
                R.string.html_port_setup_connect,R.string.html_port_setup_background,R.string.html_port_setup_reboot)
            val actions=listOf(listOf(R.string.download_shizuku),listOf(R.string.official_setup_guide,R.string.open_shizuku),listOf(R.string.open_shizuku),
                listOf(R.string.connect),listOf(R.string.background_reliability),listOf(R.string.open_shizuku,R.string.connect))
            val indices=headings.mapIndexed { index,id -> children.indexOfFirst { it is TextView && it.isAccessibilityHeading && it.text.toString()==context.getString(R.string.html_port_setup_step,index+1,context.getString(id)) } }
            checkThat(indices.all { it>=0 } && indices==indices.sorted(),"Help contains six numbered native setup sections in order")
            indices.forEachIndexed { step,start ->
                val end=indices.getOrNull(step+1)?:children.indexOfFirst { it is TextView && it.text.toString()==context.getString(R.string.html_port_using_clipboard) }
                val labels=children.subList(start+1,end).flatMap(::views).filterIsInstance<Button>().map { it.text.toString() }
                checkThat(labels==actions[step].map { context.getString(it) },"Help step ${step+1} keeps its actions beside its instructions")
            }
            click(page(),R.string.html_port_clipboard_questions)
        }
        await("Clipboard questions jumps within the native Help scroll") { scroll().scrollY>0 }
        fun faq(id:Int)=views(page()).single { it.isClickable && it.contentDescription?.toString()==context.getString(id) }
        main {
            for(id in listOf(R.string.help_gboard,R.string.help_restart,R.string.help_limits,R.string.help_local,R.string.help_sensitive,R.string.help_screen)) {
                val row=faq(id);val node=row.createAccessibilityNodeInfo()
                checkThat(row.isAccessibilityHeading && row.stateDescription==context.getString(R.string.details_collapsed) &&
                    node.className==Button::class.java.name && node.actionList.any { it.id==AccessibilityNodeInfo.ACTION_EXPAND },"FAQ ${context.getString(id)} starts as a native accessible disclosure")
            }
            listOf(R.string.help_gboard,R.string.help_sensitive).forEach { id -> checkThat(faq(id).performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND,null),"FAQ ${context.getString(id)} accepts expansion") }
        }
        test.waitForIdleSync()
        await("Expanded Help layout settles before its scroll fixture") { !scroll().isLayoutRequested }
        capture("help-expanded")
        // Keep a scroll offset valid in both orientations, independently of FAQ expansion.
        main { scroll().scrollTo(0,600) }
        await("Expanded Help has a stable scroll position") { scroll().scrollY>0 && faq(R.string.help_gboard).stateDescription==context.getString(R.string.details_expanded) }
        main { helpY=scroll().scrollY;click(page(),R.string.licenses) }
        await("Licenses opens from the expanded Help") { field("pageKind")=="licenses" }
        main { click(page(),R.string.back) }
        await("Back preserves Help FAQ expansion and scroll") { field("pageKind")=="help" && scroll().scrollY==helpY && faq(R.string.help_gboard).stateDescription==context.getString(R.string.details_expanded) && faq(R.string.help_sensitive).stateDescription==context.getString(R.string.details_expanded) }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("System Back restores the originating Settings scroll") { field("pageKind")=="settings" && scroll().scrollY==settingsY }
        main { clickTagged("GUIDE") }
        await("Reopening Help preserves expansion and scroll") { field("pageKind")=="help" && scroll().scrollY==helpY && faq(R.string.help_sensitive).stateDescription==context.getString(R.string.details_expanded) }
        rotate()
        await("Rotation preserves Help disclosure state and scroll") { field("pageKind")=="help" && scroll().scrollY==helpY && faq(R.string.help_gboard).stateDescription==context.getString(R.string.details_expanded) && faq(R.string.help_sensitive).stateDescription==context.getString(R.string.details_expanded) }
        rotate()
        main { call("closePage") }
    }
    private fun diagnostics() {
        main { call("diagnostics") }
        capture("diagnostics-essentials")
        main {
            fun rows()=views(page()).filterIsInstance<LinearLayout>().filter { it.childCount==2 && it.getChildAt(0) is TextView && it.getChildAt(1) is TextView }
            val visible=rows().filter { it.isShown }.map { (it.getChildAt(0) as TextView).text.toString() }
            checkThat(visible.size==3 && visible.toSet()==listOf(R.string.connection_test,R.string.storage_check,R.string.issue).map { context.getString(it) }.toSet(),"Diagnostics initially shows only its three important value rows")
            val advanced=control(page(),R.string.advanced_details)
            checkThat(advanced.stateDescription==context.getString(R.string.details_collapsed),"Diagnostic Advanced details begins collapsed")
            advanced.performClick()
            checkThat(rows().all { it.isShown } && texts(page()).contains(context.getString(R.string.api_signature)),"Advanced details exposes the remaining diagnostic facts")
        }
        test.waitForIdleSync()
        main { scroll().scrollTo(0,control(page(),R.string.advanced_details).top) }
        capture("diagnostics-advanced")
        main {
            control(page(),R.string.advanced_details).performClick()
            val rows=views(page()).filterIsInstance<LinearLayout>().filter { it.childCount==2 && it.getChildAt(0) is TextView && it.getChildAt(1) is TextView }
            checkThat(rows.count { it.isShown }==3,"Advanced details collapses to the three important rows")
            checkThat(texts(page()).none { it==lateText },"Diagnostics does not expose saved text")
            call("closePage")
        }
    }
    private fun fullText() {
        main { home().search.setText("lateNeedle") }
        await("History search finds text beyond its initial preview") { home().adapter.count==1 && home().adapter.getItem(0).preview.contains("lateNeedle",ignoreCase=true) }
        var anchor=HistoryHome.Anchor(-1,0,0)
        await("The matched History row exposes its visible native View action") {
            views(home().list).filterIsInstance<Button>().any { it.text.toString()==context.getString(R.string.port_view) && it.contentDescription?.toString()?.contains("lateNeedle",ignoreCase=true)==true && it.isLaidOut && it.width>0 && it.height>0 }
        }
        main {
            anchor=home().anchor()
            val view=views(home().list).filterIsInstance<Button>().single { it.text.toString()==context.getString(R.string.port_view) && it.contentDescription?.toString()?.contains("lateNeedle",ignoreCase=true)==true }
            checkThat(view.isFocusable && view.isEnabled,"The matched History View control is a focusable native action")
            val minimum=Ui(activity).dp(48);val density=activity.resources.displayMetrics.density
            checkThat(view.width>=minimum && view.height>=minimum,"History View target measures ${view.width/density} by ${view.height/density} dp and meets 48dp in both dimensions")
            view.performClick()
        }
        await("Full text opens and scrolls to the late match") { field("pageKind")=="preview" && scroll().scrollY>0 }
        capture("full-text-late-match")
        main {
            val full=views(page()).filterIsInstance<TextView>().single { it.tag=="preview-text" }
            val spanned=full.text as? Spanned?:error("Full text has no match spans")
            val spans=spanned.getSpans(0,spanned.length,BackgroundColorSpan::class.java)
            val first=lateText.indexOf("lateNeedle");val second=lateText.indexOf("LATENEEDLE")
            checkThat(first>180 && full.isTextSelectable && full.text.toString()==lateText,"Full text preserves exact complete selectable text")
            checkThat(spans.map { spanned.getSpanStart(it) }.sorted()==listOf(first,second) && spans.all { spanned.getSpanEnd(it)-spanned.getSpanStart(it)=="lateNeedle".length },"Full text highlights every case-insensitive late search match")
            val matchY=full.top+full.layout.getLineTop(full.layout.getLineForOffset(first))-scroll().scrollY
            checkThat(matchY in 0 until scroll().height,"The first late match is within the full-text viewport")
            scroll().scrollTo(0,0)
            checkThat(home().anchor()==anchor && home().search.text.toString()=="lateNeedle","Full-text scrolling stays local to its page")
            click(page(),R.string.back)
        }
        await("Full-text Back preserves the History search") { field("pageKind")=="" && home().adapter.count==1 && home().search.text.toString()=="lateNeedle" }
        main { home().search.setText("") }
        await("Clearing the late query returns complete history") { home().adapter.count==3 }
    }
    private fun copyRecovery() {
        val exact="Synthetic copy-recovery text\nکوردی 中文 🙂\n  exact spaces  "
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        var completed=0
        val copied:()->Unit={ completed++ }
        main {
            clipboard.setPrimaryClip(ClipData.newPlainText("Validation","Synthetic copy-recovery sentinel"))
            // This presents the real recovery UI; it does not induce an Android copy denial.
            call("showCopyFailure","Validation",exact,copied)
            val decor=dialog().window!!.decorView
            checkThat(control(decor,R.string.port_retry).isEnabled && control(decor,R.string.view_full).isEnabled,"Synthetic copy-failure presentation provides Retry and complete text")
            checkThat(dialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"Copy-recovery sheet protects its text")
        }
        capture("copy-recovery-presentation",dialog().window!!)
        main { click(dialog().window!!.decorView,R.string.view_full) }
        test.waitForIdleSync()
        capture("copy-recovery-full-text-presentation",dialog().window!!)
        main {
            val text=views(dialog().window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()==exact }
            checkThat(text.isTextSelectable,"Copy recovery exposes exact selectable complete text")
            checkThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()=="Synthetic copy-recovery sentinel" && completed==0,"Viewing manual copy recovery leaves the synthetic clipboard unchanged")
            click(dialog().window!!.decorView,R.string.port_retry)
            checkThat(completed==1 && clipboard.primaryClip?.getItemAt(0)?.text?.toString()==exact,"Recovery Retry writes the complete text and completes its action")
        }
        await("Successful Retry dismisses recovery and keeps this action on screen") { (field("dialogs") as Set<*>).isEmpty() && !activity.isFinishing }
        checkThat(PrivateHistory.readOffline(context).entries.size==3,"Copy recovery leaves saved history unchanged")
    }
    private fun quickStates() {
        seed(entries=emptyList())
        lateinit var quick:QuickCopyDialog
        var opened=0
        main {
            quick=QuickCopyDialog(activity) {
                opened++
                context.startActivity(Intent(context,MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_HISTORY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            // Inspect creation before attachment starts its genuine asynchronous read.
            quick.create()
            val skeletons=views(quick.window!!.decorView).filter { it.tag=="quick-skeleton" }
            checkThat(skeletons.size==3 && skeletons.all { !it.isClickable && it.animation==null && it.importantForAccessibility==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS },
                "Quick loading uses three static non-interactive skeleton rows")
            checkThat(quick.window!!.attributes.windowAnimations==0,"Quick loading has no panel entrance animation")
            // Draw the created native loading decor before attachment starts its real read.
            val decor=quick.window!!.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(quick.window!!.attributes.width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(activity.windowManager.currentWindowMetrics.bounds.height(),View.MeasureSpec.AT_MOST))
            decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
        }
        capture("quick-loading-presentation",quick.window!!)
        main { quick.show() }
        try {
            await("Empty Quick copy offers Open app") { texts(quick.window!!.decorView).contains(context.getString(R.string.quick_empty)) && views(quick.window!!.decorView).filterIsInstance<Button>().any { it.text.toString()==context.getString(R.string.open_app) } }
            capture("quick-empty",quick.window!!)
            main {
                val rows=QuickCopyDialog::class.java.getDeclaredField("rows").apply { isAccessible=true }.get(quick) as LinearLayout
                checkThat(rows.minimumHeight==0 && rows.childCount==1,"Empty Quick copy releases absent-entry space")
                checkThat((rows.getChildAt(0).layoutParams as LinearLayout.LayoutParams).topMargin>0,"Quick empty action retains its measured gap")
                click(quick.window!!.decorView,R.string.open_app)
            }
            await("Empty Open app closes Quick copy and returns Main focus") { opened==1 && !quick.isShowing && activity.hasWindowFocus() && field("pageKind")=="" }
        } finally { main { quick.dismiss() } }
    }
    private fun darkContrast() {
        seed(theme="dark");main { call("showSettings");captureSettingsContrast();call("appearance") }
        main {
            val handle=control(dialog().window!!.decorView,R.string.dismiss_panel) as ViewGroup
            val grip=handle.getChildAt(0).background as GradientDrawable
            checkThat(contrast(grip.color!!.defaultColor,activity.getColor(R.color.surface))>=3.0,"Dark sheet grip remains distinct from its surface")
            checkThat(handle.minimumHeight>=Ui(activity).dp(48) && handle.isFocusable,"Native sheet grip retains its labelled 48dp target")
            click(dialog().window!!.decorView,R.string.done)
        }
        capture("settings-contrast")
        main { AppSettings(context).appearance="light" }
    }
    private fun liveUndo() {
        check(context.packageName.endsWith(".validation")) { "Live Undo fixtures require the isolated validation package on a disposable emulator" }
        val theme=arguments.getString("theme","light")!!
        check(theme in listOf("light","dark")) { "HTML port capture theme must be light or dark" }
        val entries=(21 downTo 1).map { id -> Entry(id.toLong(),id.toLong(),if(id==11)"Synthetic Undo exact text\nکوردی 中文 🙂\n  preserved spaces  " else "Synthetic Undo entry $id") }
        seed(entries,21,paused=true,theme=theme)
        val configured=CountDownLatch(1)
        main { client.updateRecovery(automatic=false,afterBoot=false) { checkThat(it,"Live Undo recovery fixture saved");configured.countDown() } }
        check(configured.await(8,TimeUnit.SECONDS))
        check(client.preferences.activate() && client.preferences.completeSetup())
        try {
            main { client.connect(true) }
            await("Live Undo attaches the actual Shizuku recorder") { client.connected() }
            val daemon=DaemonClient::class.java.getDeclaredField("remote").apply { isAccessible=true }.get(client) as IClipboardDaemon
            val initial=daemon.status()
            checkThat(initial.getBoolean("ok") && initial.getInt("uid")==2000 && initial.getBoolean("paused") && initial.getBoolean("listening"),"Live Undo uses a paused real shell listener")
            main {
                call("help")
                val labels=views(page()).filterIsInstance<Button>().map { it.text.toString() }
                checkThat(labels.count { it==context.getString(R.string.connection_test) }==1 && !labels.contains(context.getString(R.string.connect)),"Connected Help offers a connection test and avoids redundant Connect actions")
                click(page(),R.string.connection_test)
                checkThat(texts(dialog().window!!.decorView).contains(context.getString(R.string.test_title)),"Connected guide reaches the real connection-test confirmation")
                click(dialog().window!!.decorView,R.string.cancel);call("closePage")
            }
            fun retained():List<Entry> {
                val result=daemon.page("",0,40);check(result.getBoolean("ok")) { "Live Undo history read failed" }
                val ids=result.getLongArray("ids")!!;val times=result.getLongArray("times")!!
                return ids.indices.map { index -> Entry(ids[index],times[index],daemon.entry(ids[index]).getString("text")?:error("Live Undo text missing")) }
            }
            fun notice():View?=activity.window.decorView.findViewById(R.id.delete_undo_notice)
            fun pending(id:Long):Long {
                val state=daemon.status()
                checkThat(state.getLong("undoToken")>0 && state.getLong("undoEntryId")==id,"Daemon status exposes the authoritative pending deletion $id")
                return state.getLong("undoToken")
            }
            main { call("showSettings") }
            await("Connected Settings exposes only Stop recorder") { tagged("START").visibility==View.GONE && tagged("STOP").visibility==View.VISIBLE && views(page()).filterIsInstance<Switch>().first().isEnabled }
            main {
                clickTagged("DUPLICATES")
                checkThat(views(dialog().window!!.decorView).filterIsInstance<RadioButton>().map { it.text.toString() }==listOf(R.string.duplicates_unique,R.string.duplicates_consecutive).map { context.getString(it) },"Connected duplicate preference opens two native radio choices")
                click(dialog().window!!.decorView,R.string.done);clickTagged("LIMIT")
                checkThat(views(dialog().window!!.decorView).filterIsInstance<EditText>().single().text.toString()=="21","Connected capacity preference opens its saved native field")
                click(dialog().window!!.decorView,R.string.cancel);click(page(),R.string.back)
                call("delete",11L)
            }
            await("Actual native deletion exposes persistent Undo and Dismiss notice") { home().adapter.count==20 && notice()?.let { texts(it).contains(context.getString(R.string.port_undo)) && texts(it).contains(context.getString(R.string.port_dismiss_notice)) }==true }
            val token=pending(11)
            main {
                val shown=checkNotNull(notice())
                // Test this UI response shape directly; no daemon/RPC failure is fabricated.
                call("syncUndo",Bundle().apply { putString("issue","SYNTHETIC_STATUS_READ_FAILED") })
                checkThat(field("undoToken")==token && notice()===shown && shown.tag==token &&
                    control(shown,R.string.port_undo).isEnabled && control(shown,R.string.port_dismiss_notice).isEnabled,
                    "Synthetic issue-only status presentation preserves the existing Undo token and notice")
            }
            checkThat(daemon.status().getLong("undoToken")==token,"UI status-metadata-loss fixture leaves the actual daemon Undo token unchanged")
            SystemClock.sleep(2500)
            main { checkThat(notice()?.tag==token,"Undo notice persists beyond transient feedback");call("showSettings") }
            await("Navigation retains the daemon Undo token") { field("pageKind")=="settings" && notice()?.tag==token }
            main { click(page(),R.string.back) }
            rotate()
            await("Rotation restores Undo from daemon status") { notice()?.tag==token && home().adapter.count==20 }
            rotate()
            val afterDelete=retained()
            checkThat(afterDelete==entries.filterNot { it.id==11L },"Delete retains every other exact entry in order")
            checkThat(daemon.setLimit(20).getBoolean("ok"),"Undo full fixture reduces only unused capacity")
            val beforeFailure=daemon.status()
            main { call("refresh",false,false);click(checkNotNull(notice()),R.string.port_undo) }
            await("Full Undo opens the specific recovery explanation") { (field("dialogs") as Set<*>).filterIsInstance<Dialog>().singleOrNull { it.isShowing }?.let { texts(it.window!!.decorView).contains(context.getString(R.string.port_undo_full_title)) }==true }
            val refused=daemon.undoDelete(token)
            checkThat(!refused.getBoolean("ok") && refused.getString("issue")=="UNDO_HISTORY_FULL" && refused.getLong("undoToken")==token,"Full history rejects Undo and retains its authoritative token")
            checkThat(daemon.status().getLong("generation")==beforeFailure.getLong("generation") && retained()==afterDelete,"Rejected Undo preserves every retained entry and generation without eviction")
            checkThat(daemon.status().getBoolean("listening") && daemon.status().getString("issue").isNullOrEmpty(),"Expected Undo capacity refusal leaves the recorder healthy")
            checkThat(daemon.setLimit(21).getBoolean("ok"),"Increasing capacity permits an exact restore")
            main { click(dialog().window!!.decorView,R.string.done);call("refresh",false,false);click(checkNotNull(notice()),R.string.port_undo) }
            await("Native Undo restores the deleted entry and clears its notice") { home().adapter.count==21 && notice()==null }
            checkThat(retained()==entries && daemon.status().getLong("undoToken")==0L,"Undo restores original IDs, timestamps, full text and ordering")
            PrivateHistory.invalidate()
            checkThat(PrivateHistory.readOffline(context).entries==entries,"Exact Undo contents are durably available offline")
            main { call("delete",11L) }
            await("First replacement Undo notice appears") { home().adapter.count==20 && notice()!=null }
            val stale=pending(11)
            main { call("delete",8L) }
            await("New deletion replaces the single pending Undo") { home().adapter.count==19 && (field("latest") as Bundle).getLong("undoEntryId")==8L }
            val current=pending(8)
            val retainedBeforeStale=retained()
            val staleResult=daemon.undoDelete(stale)
            checkThat(current!=stale && !staleResult.getBoolean("ok") && staleResult.getString("issue")=="UNDO_NOT_AVAILABLE" && retained()==retainedBeforeStale,"A stale Undo token cannot restore or alter retained history")
            main { click(checkNotNull(notice()),R.string.port_undo) }
            await("Native one-level Undo restores only the most recent deletion") { home().adapter.count==20 && notice()==null }
            checkThat(retained()==entries.filterNot { it.id==11L },"One-level Undo does not resurrect the preceding deletion")
            main { call("delete",8L) }
            await("Dismiss fixture exposes its current Undo notice") { home().adapter.count==19 && notice()!=null }
            main { click(checkNotNull(notice()),R.string.port_dismiss_notice) }
            await("Dismiss notice clears daemon Undo without restoring deleted text") { notice()==null && home().adapter.count==19 && (field("latest") as Bundle).getLong("undoToken")==0L }
            checkThat(retained()==entries.filterNot { it.id==11L || it.id==8L },"Dismiss preserves exact retained history")
            capture("undo-complete")
        } finally {
            val stopped=CountDownLatch(1)
            main { client.stopRecording { stopped.countDown() } }
            check(stopped.await(12,TimeUnit.SECONDS)) { "Live Undo recorder stop timed out" }
            checkThat(client.preferences.options().explicitlyStopped,"Live Undo recorder stopped durably after fixtures")
            main { AppSettings(context).appearance="light" }
        }
    }
    fun run(suite:String):String {
        requireDisposableEmulator(test)
        try {
            when(suite) {
                "html-port"->{settings();help();diagnostics();fullText();copyRecovery();quickStates();darkContrast()}
                "html-port-live"->liveUndo()
                else->error("Unknown HTML port suite")
            }
            return log.append("PASS $assertions HTML port assertions ($suite), API ${Build.VERSION.SDK_INT}. Owned disposable emulator fixtures only.\n").toString()
        } finally { if(::activity.isInitialized)main { activity.finish() } }
    }
}
