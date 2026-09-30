package app.cliphistory

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import app.cliphistory.ui.*
import java.io.File

/** Test APK only. Seeds synthetic private files; no fixture entry point exists in production. */
class UiSmokeInstrumentation:Instrumentation() {
    private lateinit var activity:MainActivity
    private var assertions=0
    private val evidence get()=File(targetContext.filesDir,"ui-evidence").apply { mkdirs() }
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    private fun checkThat(condition:Boolean,message:String) { check(condition){message};assertions++ }
    private fun idle() { waitForIdleSync();SystemClock.sleep(180);waitForIdleSync() }
    private fun until(message:String,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end) {
            var okay=false;runOnMainSync { okay=condition() };if(okay){assertions++;return};SystemClock.sleep(60)
        };error(message)
    }
    private fun all(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { all(root.getChildAt(it)) } else emptyList())
    private fun texts(root:View)=all(root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun field(name:String):Any?=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun setField(name:String,value:Any?) { MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.set(activity,value) }
    private fun invoke(name:String,vararg args:Any) {
        val method=MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==args.size }
        method.isAccessible=true;method.invoke(activity,*args)
    }
    private fun dialog():Dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().last()
    private fun click(root:View,label:String) {
        var view=all(root).firstOrNull { it is TextView && it.text.toString()==label } ?: error("Missing control: $label")
        while(!view.isClickable && view.parent is View)view=view.parent as View
        checkThat(view.isEnabled && view.isClickable,"Control unavailable: $label")
        view.performClick()
    }
    private fun capture(name:String,modal:Dialog?=null) {
        idle();SystemClock.sleep(450);waitForIdleSync()
        runOnMainSync {
            val decor=activity.window.decorView
            val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);decor.draw(canvas)
            modal?.window?.decorView?.let { sheet ->
                canvas.drawColor(Color.argb(85,0,0,0));val location=IntArray(2);sheet.getLocationOnScreen(location)
                canvas.save();canvas.translate(location[0].toFloat(),location[1].toFloat());sheet.draw(canvas);canvas.restore()
            }
            File(evidence,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
    override fun onStart() {
        val report=Bundle()
        try {
            val samples=listOf("Let's meet at 10:30 by the library.","https://example.com/notes","Keep the interface quiet. Make the next action obvious.","Oats\nCoffee\nGreen apples","The final draft is ready for review. I've added the updated measurements and the installation notes.","A little less, but better.")
            val now=System.currentTimeMillis()
            val entries=(0 until 73).map { i -> Entry(73L-i,now-i*240_000L,if(i<6)samples[i] else "Saved example ${73-i}") }
            val (first,second)=PrivateHistory.openForDaemon(targetContext)
            listOf(first,second).forEach { pfd -> FramedSlot(FdAccess(pfd,targetContext.applicationInfo.uid)).use { it.writeAndSync(SnapshotCodec.encode(Snapshot(1,100,74,false,entries))) } }
            AppSettings(targetContext).apply { appearance="light";returnAfterCopy=true }
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            until("History did not load") { (field("home") as HistoryHome).adapter.count==40 }
            checkThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"History must remain secure")
            capture("S07-setup-with-offline-history")
            val home=field("home") as HistoryHome
            val status=Bundle().apply {putInt("count",73);putInt("limit",100);putBoolean("active",true);putBoolean("storageVerified",true);putString("selfTest","PASS")}
            val fixtureRows=entries.take(40).map { DaemonClient.Row(it.id,it.timestamp,it.text.take(180)) }
            runOnMainSync { home.show(DaemonClient.Page(status,fixtureRows,73,1,false),fixtureRows,RecorderState.RECORDING,"") }
            capture("S01-light-recording-presentation")
            runOnMainSync { home.search.setText("notes") }
            until("Search failed") { home.adapter.count==2 }
            capture("S03-search")
            runOnMainSync { home.search.setText("mountain") }
            until("Empty search failed") { texts(activity.window.decorView).contains("No matching text") }
            capture("S04-no-matches")
            runOnMainSync { home.clear.performClick() }
            until("Clear search failed") { home.adapter.count==40 }
            runOnMainSync { home.more.performClick() }
            until("Pagination failed") { home.adapter.count==73 }
            runOnMainSync { home.list.setSelection(50);invoke("refresh",false,false) }
            idle()
            until("Refresh discarded loaded history") { home.adapter.count==73 && home.more.isEnabled }
            runOnMainSync { home.list.setSelection(0) }
            runOnMainSync { invoke("showMenu",home.clear) }
            idle()
            runOnMainSync {
                val popup=field("menu") as PopupWindow
                checkThat(texts(popup.contentView).contains("Appearance"),"Overflow omitted appearance")
                checkThat(texts(popup.contentView).contains("Clear history"),"Overflow omitted clear")
                popup.dismiss()
            }
            runOnMainSync { invoke("entryActions",home.adapter.getItem(2)) }
            idle();capture("S14-entry-actions",dialog())
            runOnMainSync { click(dialog().window!!.decorView,"View full text") }
            until("Preview failed") { field("pageKind")=="preview" }
            capture("S24-full-text")
            AppSettings(targetContext).returnAfterCopy=false
            runOnMainSync { invoke("copyEntry",71L) }
            until("Copy-and-stay failed") { texts(activity.window.decorView).contains("Copied") }
            runOnMainSync {
                checkThat(!activity.isFinishing,"Copy-and-stay closed the Activity")
                val clip=activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip
                checkThat(clip?.getItemAt(0)?.text.toString()==samples[2],"Copy did not restore full text")
                invoke("closePage")
                invoke("copyBehavior")
            }
            idle();capture("S26-copy-behavior",dialog())
            runOnMainSync {
                val toggle=all(dialog().window!!.decorView).filterIsInstance<Switch>().single()
                checkThat(!toggle.isChecked,"Preference not read")
                toggle.performClick();checkThat(AppSettings(targetContext).returnAfterCopy,"Preference not persisted")
                dialog().dismiss();invoke("appearance")
            }
            idle();capture("S25-appearance",dialog())
            val monitor=addMonitor(MainActivity::class.java.name,null,false)
            runOnMainSync { click(dialog().window!!.decorView,"Dark") }
            activity=waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: error("Appearance did not recreate")
            removeMonitor(monitor)
            until("Dark reload failed") { (field("home") as HistoryHome).adapter.count==40 }
            checkThat(AppSettings(targetContext).appearance=="dark","Dark preference not persisted")
            checkThat(activity.getColor(R.color.canvas)==Color.parseColor("#151B17"),"Dark palette not applied")
            capture("S02-dark-offline")
            runOnMainSync { invoke("help") };capture("S21-help")
            runOnMainSync { invoke("closePage");invoke("diagnostics") };capture("S20-diagnostics-offline")
            runOnMainSync {
                checkThat(texts(field("page") as View).none { it==samples[2] },"Diagnostics exposed clip text")
                invoke("closePage");invoke("limitDialog")
            }
            idle();capture("S15-limit",dialog())
            runOnMainSync {
                val input=all(dialog().window!!.decorView).filterIsInstance<EditText>().single()
                input.setText("501");click(dialog().window!!.decorView,"Save")
                checkThat(input.error.toString()=="Enter a number from 20 to 500.","Invalid limit accepted")
            }
            capture("S16-invalid-limit",dialog())
            runOnMainSync { dialog().dismiss();invoke("addTile") };capture("S22-add-tile",dialog())
            runOnMainSync { dialog().dismiss() }
            // Presentation fixtures prove rendering only, not Shizuku connectivity.
            for(scene in listOf(RecorderState.RECORDING,RecorderState.LISTENING,RecorderState.CONNECTING,RecorderState.PAUSED,RecorderState.STOPPED,RecorderState.PERMISSION,RecorderState.DENIED,RecorderState.ATTENTION)) {
                runOnMainSync { (field("home") as HistoryHome).show(DaemonClient.Page(status,fixtureRows,73,1,false),fixtureRows,scene,"") }
                capture("state-${scene.name.lowercase()}")
            }
            runOnMainSync { setField("latest",status);setField("offline",false);invoke("recorder") }
            capture("S19-test-pass-presentation",dialog())
            runOnMainSync {
                val body=field("recorderContent") as LinearLayout
                val count=body.childCount
                invoke("fillRecorder",body,dialog());invoke("fillRecorder",body,dialog())
                checkThat(body.childCount==count,"Recorder updates duplicated content")
            }
            runOnMainSync { dialog().dismiss();(field("home") as HistoryHome).loading() };capture("S05-loading-presentation")
            runOnMainSync {
                (field("home") as HistoryHome).show(DaemonClient.Page(Bundle(),emptyList(),0,1,false),emptyList(),RecorderState.LISTENING,"")
            };capture("S06-empty-presentation")
            runOnMainSync {
                (field("home") as HistoryHome).show(DaemonClient.Page(Bundle(),emptyList(),0,1,true,"READ_FAILED"),emptyList(),RecorderState.STOPPED,"")
                checkThat(texts(activity.window.decorView).contains("History could not be read"),"Read failure appeared empty")
            };capture("S28-read-error-presentation")
            runOnMainSync { invoke("copyEntry",71L) }
            until("Default copy did not return") { activity.isFinishing }
            report.putString("stream","PASS: $assertions assertions. Synthetic history, UI fixtures and preferences only; no Shizuku runtime claim.\n")
            finish(Activity.RESULT_OK,report)
        } catch(t:Throwable) {
            report.putString("stream","FAIL after $assertions assertions: ${t.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED,report)
        }
    }
}
