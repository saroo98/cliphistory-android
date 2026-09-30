package app.cliphistory.ui

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    fun help(back:()->Unit,website:(String)->Unit,manager:()->Unit,connect:()->Unit,licenses:()->Unit):View=page(s(R.string.help_privacy),back) { content,_ ->
        content.addView(ui.title(s(R.string.help_setup),18f));content.addView(ui.space(8))
        listOf(R.string.setup_install,R.string.setup_debugging,R.string.setup_pair,R.string.setup_start,R.string.setup_authorise).forEach {
            ui.paragraph(content,s(it))
        }
        ui.actionRow(content,s(R.string.download_shizuku)){website("https://shizuku.rikka.app/download/")}
        ui.actionRow(content,s(R.string.official_setup_guide)){website("https://shizuku.rikka.app/guide/setup/")}
        ui.actionRow(content,s(R.string.open_shizuku),action=manager)
        ui.actionRow(content,s(R.string.connect),action=connect)
        content.addView(ui.space(20))
        listOf(
            R.string.help_gboard to R.string.help_gboard_body,
            R.string.help_restart to R.string.help_restart_body,
            R.string.help_local to R.string.help_local_body,
            R.string.help_sensitive to R.string.help_sensitive_body,
            R.string.help_limits to R.string.help_limits_body,
            R.string.help_screen to R.string.help_screen_body
        ).forEach { (title,body) ->
            content.addView(ui.title(s(title),18f));content.addView(ui.space(8));ui.paragraph(content,s(body));content.addView(ui.space(8))
        }
        content.addView(ui.text(ui.activity.getString(R.string.version_license,BuildConfig.VERSION_NAME),13f,true))
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
    fun keyValue(parent:LinearLayout,key:String,value:String):TextView {
        val block=ui.column().apply { setPadding(0,ui.dp(10),0,ui.dp(10)) }
        val text=ui.text(value,16f)
        block.addView(ui.text(key,13f,true));block.addView(text);parent.addView(block);parent.addView(ui.divider());return text
    }
    data class DiagnosticPage(val view:View,val update:(Bundle,Boolean)->Unit)
    fun diagnostics(b:Bundle,offline:Boolean,back:()->Unit,copy:()->Unit):DiagnosticPage {
        val values=LinkedHashMap<String,TextView>()
        val view=page(s(R.string.diagnostics),back) { content,root ->
        diagnosticRows(b,offline).forEach { (key,value) -> values[key]=keyValue(content,key,value) }
        val extra=ui.column().apply { visibility=View.GONE }
        advanced(b,offline).forEach { (key,value) -> values[key]=keyValue(extra,key,value) }
        ui.actionRow(content,s(R.string.advanced_details)) { extra.visibility=if(extra.visibility==View.VISIBLE)View.GONE else View.VISIBLE }
        content.addView(extra);content.addView(ui.space(16));ui.paragraph(content,s(R.string.report_privacy));ui.paragraph(content,s(R.string.test_boundary))
        root.addView(ui.button(s(R.string.copy_report),primary=true,action=copy))
        root.addView(ui.text(s(R.string.copies_clipboard),13f,true).apply { gravity=Gravity.CENTER;setPadding(0,ui.dp(8),0,ui.dp(8)) })
        }
        return DiagnosticPage(view) { current,disconnected ->
            (diagnosticRows(current,disconnected)+advanced(current,disconnected)).forEach { (key,value) ->
                values[key]?.let { if(it.text.toString()!=value)it.text=value }
            }
        }
    }
}
