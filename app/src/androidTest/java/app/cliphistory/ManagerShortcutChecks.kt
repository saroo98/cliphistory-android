package app.cliphistory

import android.app.*
import android.content.*
import android.view.*
import android.widget.*
import app.cliphistory.ui.AppSettings
import app.cliphistory.ui.MainActivity

/** Uses only the isolated validation app. Never changes a service's authorization. */
class ManagerShortcutChecks(private val test:Instrumentation) {
    fun run():String {
        val context=test.targetContext
        check(context.packageName=="app.cliphistory.validation")
        val packages=listOf("moe.shizuku.privileged.api","com.churan.shizako","eu.darken.porter",
            "com.hamondev.shevery","af.shizuku.plus.api").filter { context.packageManager.getLaunchIntentForPackage(it)!=null }
        check(packages.size>=2) { "Install two service managers in this test fixture" }
        AppSettings(context).apply { welcome="never";backgroundSuggestion=false }
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList())
        fun call(name:String) { MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity) }
        fun dialogs()=(MainActivity::class.java.getDeclaredField("dialogs").apply { isAccessible=true }.get(activity) as Set<*>).filterIsInstance<Dialog>().filter { it.isShowing }
        var assertions=0
        test.runOnMainSync {
            call("openManager")
            check(dialogs().size==1) { "Multiple managers must offer a chooser, not a dead-end notice" };assertions++
            val dialog=dialogs().single()
            val text=views(dialog.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            packages.forEach { pkg -> check(text.any { it.contains(pkg) }) { "Missing selectable manager: $pkg" };assertions++ }
            views(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text.toString()==context.getString(R.string.cancel) }.performClick()
            check(dialogs().isEmpty());assertions++
        }
        packages.forEach { pkg ->
            var launched:Intent?=null
            val monitor=object:Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult {
                    launched=intent
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }
            }
            test.addMonitor(monitor)
            try {
                test.runOnMainSync {
                    call("openManager")
                    var row:View=views(dialogs().single().window!!.decorView).filterIsInstance<TextView>().single { it.text.toString().contains(pkg) }
                    while(!row.isClickable)row=row.parent as View
                    row.performClick()
                    check(dialogs().isEmpty());assertions++
                }
                test.waitForIdleSync()
                check(launched?.component?.packageName==pkg) { "Selected manager was not launched: $pkg" };assertions++
                test.runOnMainSync {
                    activity.showBackgroundGuide()
                    fun click(label:String) {
                        var row:View=views(dialogs().single().window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }
                        while(!row.isClickable)row=row.parent as View
                        row.performClick()
                    }
                    click(context.getString(R.string.port_battery_options))
                    click(context.getString(R.string.open_shizuku_battery))
                    click(pkg)
                    check(dialogs().isEmpty());assertions++
                }
                test.waitForIdleSync()
                check(launched?.data?.schemeSpecificPart==pkg && launched?.component?.packageName=="com.android.settings") {
                    "Battery shortcut did not target the chosen manager in system Settings"
                };assertions++
            } finally { test.removeMonitor(monitor) }
        }
        test.runOnMainSync { activity.finish() }
        return "PASS $assertions manager shortcut assertions.\n"
    }
}
