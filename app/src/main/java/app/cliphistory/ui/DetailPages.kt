package app.cliphistory.ui

import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import app.cliphistory.BuildConfig
import app.cliphistory.R
import java.text.NumberFormat

class DetailPages(private val ui:Ui) {
    private fun s(id:Int)=ui.activity.getString(id)
    fun page(title:String,back:()->Unit,body:(LinearLayout,LinearLayout)->Unit):View {
        val root=ui.column().apply { setBackgroundColor(ui.color(R.color.canvas));setPadding(ui.dp(20),0,ui.dp(20),ui.dp(8));accessibilityPaneTitle=title }
        val bar=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(64) }
        bar.addView(ui.icon(R.drawable.ic_back,s(R.string.back),back))
        bar.addView(ui.title(title).apply { isAccessibilityHeading=true },LinearLayout.LayoutParams(0,-2,1f));root.addView(bar)
        val content=ui.column().apply { setPadding(0,ui.dp(16),0,ui.dp(20)) }
        root.addView(ScrollView(ui.activity).apply { isFillViewport=true;addView(content);isVerticalScrollBarEnabled=false },LinearLayout.LayoutParams(-1,0,1f))
        body(content,root);return root
    }
    private fun disclosure(parent:LinearLayout,value:String,body:View,initial:Boolean=false,heading:Boolean=false,changed:(Boolean)->Unit={}):View {
        val row=LinearLayout(ui.activity).apply {
            gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(56);setPadding(0,ui.dp(12),0,ui.dp(12))
            background=ui.ripple(android.graphics.Color.TRANSPARENT,12);isClickable=true;isFocusable=true
            contentDescription=value;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;isAccessibilityHeading=heading
        }
        row.addView(ui.title(value,16f).apply { isAccessibilityHeading=heading;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(0,-2,1f))
        val chevron=ImageView(ui.activity).apply {
            setImageResource(R.drawable.ic_chevron);imageTintList=ColorStateList.valueOf(ui.muted)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(chevron,LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)).apply { marginStart=ui.dp(12) })
        fun expand(value:Boolean) {
            body.visibility=if(value)View.VISIBLE else View.GONE;chevron.rotation=if(value)90f else 0f
            row.stateDescription=s(if(value)R.string.details_expanded else R.string.details_collapsed);changed(value)
        }
        body.visibility=if(initial)View.VISIBLE else View.GONE;chevron.rotation=if(initial)90f else 0f
        row.stateDescription=s(if(initial)R.string.details_expanded else R.string.details_collapsed)
        row.setOnClickListener { expand(body.visibility!=View.VISIBLE) }
        row.accessibilityDelegate=object:View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host:View,info:AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info);info.className=Button::class.java.name
                info.addAction(if(body.visibility==View.VISIBLE)AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE else AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
            }
            override fun performAccessibilityAction(host:View,action:Int,args:Bundle?):Boolean {
                when(action) {
                    AccessibilityNodeInfo.ACTION_EXPAND->{expand(true);return true}
                    AccessibilityNodeInfo.ACTION_COLLAPSE->{expand(false);return true}
                }
                return super.performAccessibilityAction(host,action,args)
            }
        }
        parent.addView(row);parent.addView(body);parent.addView(ui.divider());return row
    }
    fun help(back:()->Unit,website:(String)->Unit,manager:()->Unit,connect:()->Unit,connected:Boolean,openTopics:MutableSet<Int>,licenses:()->Unit):View=page(s(R.string.help_privacy),back) { content,_ ->
        lateinit var questions:TextView
        ui.actionRow(content,s(R.string.html_port_clipboard_questions)) {
            (content.parent as? ScrollView)?.scrollTo(0,questions.top);questions.requestFocus()
        }
        content.addView(ui.title(s(R.string.help_setup),18f).apply { isAccessibilityHeading=true })
        fun step(number:Int,heading:Int,paragraphs:List<Int>,actions:(LinearLayout)->Unit) {
            content.addView(ui.space(24))
            content.addView(ui.title(ui.activity.getString(R.string.html_port_setup_step,number,s(heading)),17f).apply { isAccessibilityHeading=true });content.addView(ui.space(12))
            paragraphs.forEach { ui.paragraph(content,s(it).replaceFirst(Regex("^\\d+\\.\\s*"),"")) }
            val buttons=LinearLayout(ui.activity).apply {
                orientation=if(ui.activity.resources.configuration.fontScale>1.3f)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            }
            actions(buttons);content.addView(buttons);content.addView(ui.space(20));content.addView(ui.divider())
        }
        fun action(parent:LinearLayout,label:Int,primary:Boolean=false,click:()->Unit) {
            val vertical=parent.orientation==LinearLayout.VERTICAL
            parent.addView(ui.button(s(label),primary=primary,action=click),LinearLayout.LayoutParams(if(vertical)-1 else 0,-2,if(vertical)0f else 1f))
        }
        step(1,R.string.html_port_setup_install,listOf(R.string.setup_install)) { action(it,R.string.download_shizuku,true){website("https://shizuku.rikka.app/download/")} }
        step(2,R.string.html_port_setup_pair,listOf(R.string.setup_debugging,R.string.setup_pair)) {
            action(it,R.string.official_setup_guide){website("https://shizuku.rikka.app/guide/setup/")};action(it,R.string.open_shizuku,click=manager)
        }
        step(3,R.string.html_port_setup_start,listOf(R.string.setup_start)) { action(it,R.string.open_shizuku,click=manager) }
        step(4,R.string.html_port_setup_connect,listOf(R.string.setup_authorise)) {
            if(connected) {
                ui.paragraph(content,s(R.string.html_port_connected_test_advice))
                action(it,R.string.connection_test){ (ui.activity as MainActivity).runConnectionGuideTest() }
            } else action(it,R.string.connect,click=connect)
        }
        step(5,R.string.html_port_setup_background,listOf(R.string.setup_background)) {
            action(it,R.string.background_reliability){ (ui.activity as MainActivity).showBackgroundGuide() }
        }
        step(6,R.string.html_port_setup_reboot,listOf(R.string.setup_reboot)) {
            action(it,R.string.open_shizuku,click=manager)
            if(connected)ui.paragraph(content,s(R.string.html_port_connected_reboot_advice)) else action(it,R.string.connect,click=connect)
        }
        content.addView(ui.space(28))
        questions=ui.title(s(R.string.html_port_using_clipboard),18f).apply { isAccessibilityHeading=true;isFocusable=true;isFocusableInTouchMode=true }
        content.addView(questions);content.addView(ui.space(12))
        fun topics(entries:List<Pair<Int,Int>>) {
            entries.forEach { (title,body) ->
                val text=ui.column();ui.paragraph(text,s(body))
                disclosure(content,s(title),text,title in openTopics,heading=true) { expanded -> if(expanded)openTopics.add(title) else openTopics.remove(title) }
            }
        }
        topics(listOf(R.string.help_gboard to R.string.help_gboard_body,R.string.help_restart to R.string.help_restart_body,R.string.help_limits to R.string.help_limits_body))
        content.addView(ui.space(28));content.addView(ui.title(s(R.string.html_port_privacy_saved_text),18f).apply { isAccessibilityHeading=true });content.addView(ui.space(12))
        topics(listOf(R.string.help_local to R.string.help_local_body,R.string.help_sensitive to R.string.help_sensitive_body,R.string.help_screen to R.string.help_screen_body))
        content.addView(ui.space(24));content.addView(ui.text(ui.activity.getString(R.string.version_license,BuildConfig.VERSION_NAME),13f,true))
        ui.actionRow(content,s(R.string.source_code)){website("https://github.com/saroo98/cliphistory-android")}
        ui.actionRow(content,s(R.string.licenses),action=licenses)
    }
    fun licenses(back:()->Unit):View=page(s(R.string.licenses),back) { content,_ ->
        val notice=ui.activity.assets.open("licenses/NOTICES.txt").bufferedReader().use { it.readText() }
        content.addView(ui.text(notice,14f).apply { setTextIsSelectable(true) })
    }
    fun diagnosticRows(b:Bundle,offline:Boolean):List<Pair<String,String>> {
        fun yes(value:Boolean)=s(if(value)R.string.yes else R.string.no)
        fun number(key:String)=if(offline)s(R.string.not_connected) else NumberFormat.getIntegerInstance().format(b.getLong(key))
        fun flag(key:String,on:Int,off:Int)=if(offline)s(R.string.not_connected) else s(if(b.getBoolean(key))on else off)
        return listOf(
            s(R.string.app_name) to BuildConfig.VERSION_NAME,
            s(R.string.device) to Build.MODEL,
            s(R.string.android_version) to "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
            s(R.string.mode) to s(if(offline)R.string.offline_mode else R.string.recorder_mode),
            s(R.string.duplicate_handling) to s(if(!b.containsKey("duplicateMode"))R.string.not_confirmed
                else if(b.getInt("duplicateMode")==1)R.string.duplicates_consecutive else R.string.duplicates_unique),
            s(R.string.automatic_recovery) to (b.getString("recoveryMessage").orEmpty().ifEmpty {
                val options=app.cliphistory.client.RecorderPreferences(ui.activity).options()
                s(if(options.automatic && !options.explicitlyStopped)R.string.enabled else R.string.disabled)
            }),
            s(R.string.last_checked) to b.getLong("checkedAt").takeIf { it>0 }?.let { java.text.DateFormat.getTimeInstance().format(java.util.Date(it)) }.orEmpty(),
            s(R.string.daemon_uid) to if(offline)s(R.string.not_connected) else b.getInt("uid").toString(),
            s(R.string.listener) to flag("listening",R.string.registered,R.string.not_registered),
            s(R.string.storage_check) to flag("storageVerified",R.string.passed,R.string.not_confirmed),
            s(R.string.connection_test) to if(offline)s(R.string.not_connected) else b.getString("selfTest","NOT_RUN"),
            s(R.string.session_saved) to number("saved"),s(R.string.sensitive_skipped) to number("sensitiveSkipped"),
            s(R.string.oversized_skipped) to number("oversizedSkipped"),s(R.string.unsupported_skipped) to number("unsupportedSkipped"),
            s(R.string.queue_drops) to number("queueDrops"),
            s(R.string.recovered_snapshot) to if(offline)s(R.string.not_connected) else yes(b.getBoolean("recovered")),
            s(R.string.issue) to listOf(b.getString("issue","").orEmpty(),b.getString("operationIssue","").orEmpty()).filter { it.isNotEmpty() }.distinct().joinToString("\n").ifEmpty { s(R.string.none) }
        )
    }
    fun advanced(b:Bundle,offline:Boolean)=listOf(
        s(R.string.security_patch) to Build.VERSION.SECURITY_PATCH,
        s(R.string.generation) to if(offline)s(R.string.not_connected) else b.getLong("generation").toString(),
        s(R.string.api_signature) to b.getString("signature",s(R.string.not_connected))
    )
    fun report(b:Bundle,offline:Boolean)=(diagnosticRows(b,offline)+advanced(b,offline)).joinToString("\n") { "${it.first}: ${it.second}" }+"\n\n"+s(R.string.report_privacy)+"\n"+s(R.string.test_boundary)
    private fun displayedValue(key:String,value:String):String {
        if(key!=s(R.string.connection_test))return value
        return when(value) {
            "PASS"->s(R.string.passed)
            "WAITING"->s(R.string.audit_test_waiting)
            "NOT_RUN"->s(R.string.audit_test_not_run)
            "STORAGE_FAILED"->s(R.string.audit_test_storage_failed)
            "NO_CALLBACK_RECEIVED"->s(R.string.audit_test_no_event)
            else->value
        }
    }
    fun keyValue(parent:LinearLayout,key:String,value:String):TextView {
        val block=ui.column().apply { setPadding(0,ui.dp(10),0,ui.dp(10)) }
        val text=ui.text(value,16f)
        block.addView(ui.text(key,13f,true));block.addView(text);parent.addView(block);parent.addView(ui.divider());return text
    }
    data class DiagnosticPage(val view:View,val update:(Bundle,Boolean)->Unit)
    enum class DiagnosticAction { RETRY, CONNECT, RESUME, TEST }
    private data class Health(val title:Int,val body:Int,val action:DiagnosticAction,val label:Int,val enabled:Boolean=true)
    private fun health(b:Bundle,offline:Boolean):Health {
        val issue=b.getString("issue").orEmpty()
        val readFailed=b.getBoolean("historyReadFailed") || issue=="BOTH_SNAPSHOTS_UNREADABLE" || issue.startsWith("HISTORY_READ_FAILED") || issue=="HISTORY_ACCESS_DENIED"
        if(readFailed)return Health(R.string.history_failed,R.string.html_port_read_retry_advice,DiagnosticAction.RETRY,R.string.try_again)
        if(offline)return Health(R.string.html_port_recorder_disconnected,R.string.html_port_connect_advice,DiagnosticAction.CONNECT,R.string.connect)
        if(issue.isNotEmpty() || b.containsKey("storageVerified") && !b.getBoolean("storageVerified") || b.containsKey("listening") && !b.getBoolean("listening"))
            return Health(R.string.attention_title,R.string.attention_body,DiagnosticAction.TEST,R.string.run_connection_test,b.getString("selfTest")!="WAITING")
        if(b.getBoolean("paused"))return Health(R.string.paused,R.string.html_port_resume_advice,DiagnosticAction.RESUME,R.string.resume)
        return when(b.getString("selfTest","NOT_RUN")) {
            "PASS"->Health(R.string.test_passed,R.string.html_port_test_passed_body,DiagnosticAction.TEST,R.string.run_connection_test)
            "WAITING"->Health(R.string.test_waiting,R.string.html_port_test_waiting_body,DiagnosticAction.TEST,R.string.run_connection_test,false)
            "NOT_RUN"->Health(R.string.html_port_recorder_test_not_run,R.string.html_port_test_advice,DiagnosticAction.TEST,R.string.run_connection_test)
            "STORAGE_FAILED"->Health(R.string.test_failed,R.string.test_storage_failed,DiagnosticAction.TEST,R.string.run_connection_test)
            else->Health(R.string.test_failed,R.string.html_port_test_retry_advice,DiagnosticAction.TEST,R.string.run_connection_test)
        }
    }
    fun diagnostics(b:Bundle,offline:Boolean,back:()->Unit,copy:()->Unit,healthAction:((DiagnosticAction)->Unit)?=null):DiagnosticPage {
        val values=LinkedHashMap<String,TextView>()
        val essential=setOf(s(R.string.connection_test),s(R.string.storage_check),s(R.string.issue))
        var currentHealth=health(b,offline)
        lateinit var healthTitle:TextView
        lateinit var healthBody:TextView
        var healthButton:Button?=null
        val view=page(s(R.string.diagnostics),back) { content,root ->
            val summary=ui.column().apply { background=ui.shape(ui.color(R.color.secondary_surface),16);setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8)) }
            healthTitle=ui.title(s(currentHealth.title),16f).apply { isAccessibilityHeading=true;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
            healthBody=ui.text(s(currentHealth.body),14f,true)
            summary.addView(healthTitle);summary.addView(ui.space(4));summary.addView(healthBody)
            if(healthAction!=null) {
                healthButton=ui.button(s(currentHealth.label)) { healthAction(currentHealth.action) }.apply { isEnabled=currentHealth.enabled }
                summary.addView(healthButton)
            } else summary.addView(ui.space(8))
            content.addView(summary);content.addView(ui.space(12))
            val rows=diagnosticRows(b,offline)
            rows.filter { it.first in essential }.forEach { (key,value) -> values[key]=keyValue(content,key,displayedValue(key,value)) }
            val extra=ui.column()
            (rows.filterNot { it.first in essential }+advanced(b,offline)).forEach { (key,value) -> values[key]=keyValue(extra,key,displayedValue(key,value)) }
            disclosure(content,s(R.string.advanced_details),extra)
            content.addView(ui.space(24));ui.paragraph(content,s(R.string.report_privacy));ui.paragraph(content,s(R.string.test_boundary))
            root.addView(ui.button(s(R.string.copy_report),primary=true,action=copy))
            root.addView(ui.text(s(R.string.copies_clipboard),13f,true).apply { gravity=Gravity.CENTER;setPadding(0,ui.dp(8),0,ui.dp(8)) })
        }
        return DiagnosticPage(view) { current,disconnected ->
            currentHealth=health(current,disconnected)
            val title=s(currentHealth.title);val body=s(currentHealth.body)
            if(healthTitle.text.toString()!=title)healthTitle.text=title
            if(healthBody.text.toString()!=body)healthBody.text=body
            healthButton?.let { control ->
                val label=s(currentHealth.label)
                if(control.text.toString()!=label)control.text=label
                control.isEnabled=currentHealth.enabled
            }
            (diagnosticRows(current,disconnected)+advanced(current,disconnected)).forEach { (key,value) ->
                val displayed=displayedValue(key,value)
                values[key]?.let { if(it.text.toString()!=displayed)it.text=displayed }
            }
        }
    }
}
