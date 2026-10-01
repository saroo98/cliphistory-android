package app.cliphistory

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cliphistory.ui.DetailPages
import app.cliphistory.ui.MainActivity
import app.cliphistory.ui.Ui

/** Synthetic presentation checks. Does not seed history or read the clipboard. */
class AuditDiagnosticChecks(private val test:Instrumentation) {
    private val log=StringBuilder()
    private var count=0
    private fun main(action:()->Unit) {
        if(Looper.myLooper()==Looper.getMainLooper()) { action();return }
        var failure:Throwable?=null
        test.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
        failure?.let { throw it }
    }
    private fun checkThat(value:Boolean,message:String) {
        check(value) { message };count++;log.append("PASS $message\n")
        test.sendStatus(0,Bundle().apply { putString("stream","PASS $message\n") })
    }
    private fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup)(0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList())
    private fun value(root:View,key:String):String {
        val block=views(root).filterIsInstance<LinearLayout>().single {
            it.childCount==2 && (it.getChildAt(0) as? TextView)?.text?.toString()==key && it.getChildAt(1) is TextView
        }
        return (block.getChildAt(1) as TextView).text.toString()
    }
    private fun fixture(code:String?=null)=Bundle().apply {
        code?.let { putString("selfTest",it) }
        putString("recoveryMessage","Synthetic recovery preference")
        putString("issue","Synthetic recorder issue")
        putString("operationIssue","Synthetic copy issue")
        putString("signature","Synthetic API signature")
    }
    /** Pass an existing host, or run independently through UiSmokeInstrumentation. */
    fun run(host:Activity?=null):String {
        check(BuildConfig.DEBUG && (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || BuildConfig.APPLICATION_ID.endsWith(".validation"))) { "Synthetic tests require an emulator or the isolated validation application" }
        count=0;log.setLength(0)
        val activity=host?:test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try { main {
            val pages=DetailPages(Ui(activity))
            val key=activity.getString(R.string.connection_test)
            val initial=pages.diagnostics(fixture(),false,{}, {})
            checkThat(value(initial.view,key)==activity.getString(R.string.audit_test_not_run),"Missing self-test renders Not run")
            checkThat(pages.report(fixture(),false).lineSequence().any { it=="$key: NOT_RUN" },"Missing self-test report retains NOT_RUN")
            val duplicateKey=activity.getString(R.string.duplicate_handling)
            val unknownPolicy=activity.getString(R.string.not_confirmed)
            checkThat(value(initial.view,duplicateKey)==unknownPolicy,"Missing duplicate policy renders Not confirmed")
            checkThat(pages.report(fixture(),false).lineSequence().any { it=="$duplicateKey: $unknownPolicy" },"Missing duplicate policy report does not invent a preference")
            for((mode,label) in listOf(0 to R.string.duplicates_unique,1 to R.string.duplicates_consecutive)) {
                val known=fixture().apply { putInt("duplicateMode",mode) }
                val expected=activity.getString(label)
                checkThat(value(pages.diagnostics(known,false,{},{}).view,duplicateKey)==expected,"Initial known duplicate policy $mode remains accurate")
                initial.update(known,false)
                checkThat(value(initial.view,duplicateKey)==expected,"Updated known duplicate policy $mode remains accurate")
                checkThat(pages.report(known,false).lineSequence().any { it=="$duplicateKey: $expected" },"Report preserves known duplicate policy $mode")
            }
            initial.update(fixture(),true)
            checkThat(value(initial.view,duplicateKey)==unknownPolicy,"Failed offline update clears the previous duplicate policy")
            val cases=listOf(
                "PASS" to R.string.passed,
                "WAITING" to R.string.audit_test_waiting,
                "NOT_RUN" to R.string.audit_test_not_run,
                "STORAGE_FAILED" to R.string.audit_test_storage_failed,
                "NO_CALLBACK_RECEIVED" to R.string.audit_test_no_event
            )
            for((raw,label) in cases) {
                val data=fixture(raw)
                val expected=activity.getString(label)
                val page=pages.diagnostics(data,false,{}, {})
                checkThat(value(page.view,key)==expected,"Initial $raw uses readable self-test text")
                initial.update(data,false)
                checkThat(value(initial.view,key)==expected,"Updated $raw uses readable self-test text")
                val lines=pages.report(data,false).lineSequence().toList()
                checkThat(lines.contains("$key: $raw") && !lines.contains("$key: $expected"),"Copied report preserves raw $raw code")
            }
            val unknown=fixture("FUTURE_SYNTHETIC_CODE")
            initial.update(unknown,false)
            checkThat(value(initial.view,key)=="FUTURE_SYNTHETIC_CODE" && pages.report(unknown,false).contains("$key: FUTURE_SYNTHETIC_CODE\n"),"Unknown diagnostic code remains available on screen and in report")
            val offline=fixture("PASS")
            initial.update(offline,true)
            val disconnected=activity.getString(R.string.not_connected)
            checkThat(value(initial.view,key)==disconnected,"Disconnect replaces stale self-test result")
            checkThat(pages.report(offline,true).lineSequence().any { it=="$key: $disconnected" },"Offline report identifies unavailable self-test")
            val report=pages.report(fixture("STORAGE_FAILED"),false)
            checkThat(report.contains("${activity.getString(R.string.issue)}: Synthetic recorder issue\nSynthetic copy issue\n"),"Copied report retains both raw issue messages")
            checkThat(report.contains("${activity.getString(R.string.api_signature)}: Synthetic API signature\n"),"Copied report retains raw API signature")
            checkThat(report.endsWith(activity.getString(R.string.report_privacy)+"\n"+activity.getString(R.string.test_boundary)),"Copied report retains privacy and test boundary text")
        } } finally { if(host==null)main { activity.finish() } }
        return log.append("PASS $count diagnostic presentation/report assertions, API ${Build.VERSION.SDK_INT}. Synthetic validation only.\n").toString()
    }
}
