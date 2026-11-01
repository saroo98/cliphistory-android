package app.cliphistory

import android.app.Instrumentation
import android.os.SystemClock
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/** Export known metadata only. Explicit pause arguments also operate the native pause control. */
class PhoneObservationChecks(private val test:Instrumentation,private val arguments:Bundle=Bundle()) {
    private val packageName="app.cliphistory"
    private fun nodes(root:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(root==null)emptyList()
        else listOf(root)+(0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun current()=nodes(test.uiAutomation.rootInActiveWindow).filter { it.packageName?.toString()==packageName }
    private fun shell(command:String) { test.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } } }
    private fun waitFor(predicate:(List<AccessibilityNodeInfo>)->Boolean):List<AccessibilityNodeInfo> {
        val until=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<until) { val items=current();if(predicate(items))return items;SystemClock.sleep(100) }
        error("Production UI metadata was not available; no history exported")
    }
    private fun click(items:List<AccessibilityNodeInfo>,label:String) {
        var item=items.first { it.text?.toString()==label || it.contentDescription?.toString()==label }
        while(!item.isClickable)item=item.parent?:error("No clickable parent")
        check(item.isEnabled && item.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    fun run():String {
        check(BuildConfig.DEBUG && test.targetContext.packageName=="app.cliphistory.validation")
        shell("am start -W -a app.cliphistory.OPEN_HISTORY -n app.cliphistory/app.cliphistory.ui.MainActivity")
        val count=Regex("^(\\d+) / (\\d+) saved(?: · Offline)?$")
        val home=waitFor { it.any { node -> count.matches(node.text?.toString().orEmpty()) } }
        val match=home.firstNotNullOf { count.matchEntire(it.text?.toString().orEmpty()) }
        val states=setOf("Recording","Listening","Paused","Saved history","Recording unavailable","Recorder stopped")
        val result=JSONObject().put("count",match.groupValues[1].toInt()).put("limit",match.groupValues[2].toInt())
            .put("recorderState",home.mapNotNull { it.text?.toString() }.firstOrNull { it in states }?:"Not observed")
        click(home,"More options")
        click(waitFor { it.any { node -> node.text?.toString()=="Settings" } },"Settings")
        waitFor { it.any { node -> node.text?.toString()=="Automatic recovery" } }
        arguments.getString("pause")?.let { requested ->
            check(requested in setOf("true","false"))
            val desired=requested=="true"
            val before=current().single { it.className?.toString()=="android.widget.Switch" && it.text?.toString()=="Pause recording" }
            check(before.isEnabled) { "Recorder must be connected before changing pause" }
            val original=before.isChecked
            if(original!=desired)click(current(),"Pause recording")
            waitFor { it.any { node -> node.text?.toString()=="Pause recording" && node.isEnabled && node.isChecked==desired } }
            shell("input keyevent KEYCODE_BACK")
            return "PRODUCTION_PAUSE ${JSONObject().put("before",original).put("after",desired)}\nPASS Production pause acknowledged without reading or deleting history.\n"
        }
        val controls=JSONObject()
        val labels=setOf("Pause recording","Automatic recovery","Resume after reboot","Suggest background setup once","Return after copying","Quick Settings tile enabled","Allow screenshots","Hide history in Recents","Capture sensitive text","Auto-delete sensitive text")
        for(index in 0..7) {
            val settings=current()
            settings.filter { it.className?.toString()=="android.widget.Switch" && it.text?.toString() in labels }
                .forEach { controls.put(it.text.toString(),it.isChecked) }
            val scroll=settings.firstOrNull { it.isScrollable }?:break
            if(!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break
            SystemClock.sleep(250)
        }
        result.put("visibleSettings",controls)
        shell("input keyevent KEYCODE_BACK")
        click(waitFor { it.any { node -> node.contentDescription?.toString()=="More options" } },"More options")
        click(waitFor { it.any { node -> node.text?.toString()=="Diagnostics" } },"Diagnostics")
        click(waitFor { it.any { node -> node.contentDescription?.toString()=="Advanced details" } },"Advanced details")
        val diagnostics=JSONObject()
        val diagnosticLabels=setOf("History encryption","Key protection","Expiry clock")
        val permittedValues=setOf("AES-256-GCM","StrongBox","Trusted environment","Software","Not confirmed","Passed","Boot identity unavailable; timed text is withheld")
        for(index in 0..9) {
            val items=current()
            items.filter { it.text?.toString() in diagnosticLabels }.forEach { label ->
                val parent=label.parent
                val value=(0 until (parent?.childCount?:0)).mapNotNull { parent?.getChild(it)?.text?.toString() }.firstOrNull { it in permittedValues }
                if(value!=null)diagnostics.put(label.text.toString(),value)
            }
            val scroll=items.firstOrNull { it.isScrollable }?:break
            if(!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break
            SystemClock.sleep(250)
        }
        result.put("visibleEncryption",diagnostics)
        shell("input keyevent KEYCODE_BACK")
        return "PRODUCTION_METADATA $result\nPASS Production observation exported only count, limit, recorder state and known settings. No clipboard writes or history text.\n"
    }
}
