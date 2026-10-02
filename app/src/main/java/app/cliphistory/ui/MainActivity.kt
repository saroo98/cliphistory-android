package app.cliphistory.ui

import android.app.*
import android.content.*
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.*
import android.text.*
import android.text.style.BackgroundColorSpan
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import app.cliphistory.ClipApplication
import app.cliphistory.R
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.*
import app.cliphistory.ipc.IClipboardDaemon
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity:Activity() {
    companion object { const val ACTION_OPEN_HISTORY="app.cliphistory.OPEN_HISTORY" }
    private val client get()=(application as ClipApplication).daemon
    private val main=Handler(Looper.getMainLooper())
    private lateinit var ui:Ui
    private lateinit var home:HistoryHome
    private lateinit var pages:DetailPages
    private lateinit var settings:AppSettings
    private lateinit var root:FrameLayout
    private var rows=emptyList<DaemonClient.Row>()
    private var latest=Bundle()
    private var offline=true
    private var visible=false
    private var request=0
    private var generation=0L
    private var loadedQuery=""
    private var page:View?=null
    private var pageKind=""
    private var previewId=-1L
    private var navigation=0L
    private var restoredCount=40
    private var restoredAnchor:HistoryHome.Anchor?=null
    private var diagnosticPage:DetailPages.DiagnosticPage?=null
    private var settingsPage:SettingsPage.Page?=null
    private var pendingWelcome=false
    private var restoredSettingsScroll=0
    private var helpFromSettings=false
    private var helpSettingsScroll=0
    private var helpScroll=0
    private val helpTopics=mutableSetOf<Int>()
    private var helpConnected=false
    private var licensesFromSettings=false
    private var settingsFocus=""
    private var reopenAppearance=false
    private var restoredLicenseScroll=0
    private var operationIssue=""
    private var snackbar:View?=null
    private var undoToken=0L
    private val dialogs=mutableSetOf<Dialog>()
    private var menu:PopupWindow?=null
    private var recorderContent:LinearLayout?=null
    private var recorderDialog:Dialog?=null
    private val changed:()->Unit={ if(visible){refresh();main.post { maybeShowPrompts() }} }
    private val searchChanged=Runnable { if(visible)refresh(reset=true) }
    private val back=OnBackInvokedCallback { backPage() }
    private fun s(id:Int)=getString(id)

    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(AppSettings.themedContext(newBase)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val openHistory=intent.action==ACTION_OPEN_HISTORY
        if(openHistory)intent.action=null
        settings=AppSettings(this)
        ScreenPrivacy.apply(this,settings)
        @Suppress("DEPRECATION")
        window.setDecorFitsSystemWindows(false)
        val light=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK!=Configuration.UI_MODE_NIGHT_YES
        ui=Ui(this);pages=DetailPages(ui)
        pendingWelcome=state?.getBoolean("pending_welcome") ?: isLauncherIntent(intent)
        restoredSettingsScroll=state?.getInt("settings_scroll",0)?:0
        helpFromSettings=state?.getBoolean("help_from_settings",false)?:false
        helpSettingsScroll=state?.getInt("help_settings_scroll",0)?:0
        helpScroll=state?.getInt("help_scroll",0)?:0
        state?.getIntArray("help_topics")?.let { helpTopics.addAll(it.toList()) }
        licensesFromSettings=state?.getBoolean("licenses_from_settings",false)?:false
        settingsFocus=state?.getString("settings_focus").orEmpty()
        reopenAppearance=state?.getBoolean("reopen_appearance",false)?:false
        restoredLicenseScroll=state?.getInt("licenses_scroll",0)?:0
        root=FrameLayout(this).apply { setBackgroundColor(ui.color(R.color.canvas)) }
        home=HistoryHome(ui,::showMenu,{ client.connect(true) },{ client.openShizuku() },::help,::recorder,
            { command({it.setPaused(false)}) },::connectionTest,{ refresh() },{ refresh(append=true) },::diagnostics,::hideKeyboard,::preview,::copyEntry,::entryActions)
        root.addView(home.root,FrameLayout.LayoutParams(-1,-1));setContentView(root)
        window.insetsController?.setSystemBarsAppearance(if(light)WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime=insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            home.keyboard(ime.bottom>bars.bottom)
            insets
        }
        root.requestApplyInsets()
        home.search.setText(state?.getString("query").orEmpty())
        loadedQuery=home.search.text.toString()
        restoredCount=state?.getInt("loaded_count",40)?.coerceIn(40,MAX_LIMIT)?:40
        if(state?.containsKey("anchor_position")==true)restoredAnchor=HistoryHome.Anchor(state.getLong("anchor_id",-1),state.getInt("anchor_position"),state.getInt("anchor_top"))
        home.clear.visibility=if(home.search.text.isEmpty())View.GONE else View.VISIBLE
        home.search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                home.clear.visibility=if(s.isNullOrEmpty())View.GONE else View.VISIBLE
                request++;client.cancelPageReads();main.removeCallbacks(searchChanged);main.postDelayed(searchChanged,150)
            }
            override fun afterTextChanged(s:Editable?){}
        })
        home.search.setOnEditorActionListener { _,_,_ -> hideKeyboard();true }
        home.list.setOnItemClickListener { _,_,position,_ ->
            val index=position-home.list.headerViewsCount
            if(index in 0 until home.adapter.count)copyEntry(home.adapter.getItem(index).id)
        }
        home.list.setOnItemLongClickListener { _,_,position,_ ->
            val index=position-home.list.headerViewsCount
            if(index in 0 until home.adapter.count)entryActions(home.adapter.getItem(index));true
        }
        updateCopyHint()
        when(if(openHistory)null else state?.getString("page")) {
            "help"->showHelp()
            "licenses"->showLicenses()
            "preview"->{previewId=state?.getLong("preview",-1)?:-1;pageKind="restore-preview"}
            "diagnostics"->pageKind="restore-diagnostics"
            "settings"->showSettings()
        }
    }
    override fun onStart() {
        super.onStart();visible=true;applyPrivacy();client.addListener(changed);client.connect(false);refresh()
    }
    override fun onPostResume() {
        super.onPostResume();main.post {
            if(reopenAppearance && pageKind=="settings") { reopenAppearance=false;appearance() }
            else maybeShowPrompts()
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.action==ACTION_OPEN_HISTORY) {
            intent.action=null
            dialogs.toList().forEach { it.dismiss() };menu?.dismiss()
            closePage();hideKeyboard()
        }
        if(isLauncherIntent(intent)){pendingWelcome=true;main.post { maybeShowPrompts() }}
    }
    private fun isLauncherIntent(intent:Intent?)=intent?.action==Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
    private fun applyPrivacy() {
        ScreenPrivacy.apply(this,settings);dialogs.forEach { ScreenPrivacy.apply(it.window,settings) }
    }
    override fun onStop() {
        visible=false;request++;navigation++;client.cancelPageReads();client.removeListener(changed);main.removeCallbacksAndMessages(null)
        removeNotice()
        dialogs.toList().forEach { it.dismiss() };menu?.dismiss();super.onStop()
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("query",home.search.text.toString());out.putString("page",pageKind);out.putLong("preview",previewId)
        out.putInt("loaded_count",maxOf(rows.size,restoredCount))
        out.putBoolean("pending_welcome",pendingWelcome)
        if(pageKind=="settings")out.putInt("settings_scroll",findScroll(page)?.scrollY?:0)
        out.putBoolean("help_from_settings",helpFromSettings)
        out.putInt("help_settings_scroll",helpSettingsScroll)
        out.putInt("help_scroll",if(pageKind=="help")findScroll(page)?.scrollY?:0 else helpScroll)
        out.putIntArray("help_topics",helpTopics.toIntArray())
        out.putBoolean("licenses_from_settings",licensesFromSettings)
        out.putString("settings_focus",settingsFocus)
        out.putBoolean("reopen_appearance",reopenAppearance)
        if(pageKind=="licenses")out.putInt("licenses_scroll",findScroll(page)?.scrollY?:0)
        val anchor=restoredAnchor?:home.anchor()
        out.putLong("anchor_id",anchor.id);out.putInt("anchor_position",anchor.position);out.putInt("anchor_top",anchor.top)
        super.onSaveInstanceState(out)
    }
    private fun hideKeyboard() { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(home.search.windowToken,0);home.search.clearFocus() }
    private fun updateCopyHint() { home.hint.text=s(if(settings.returnAfterCopy)R.string.copy_hint else R.string.stay_hint) }
    private fun refresh(append:Boolean=false,reset:Boolean=false) {
        val token=++request;val query=home.search.text.toString();val offset=if(append)rows.size else 0
        if(append && query!=loadedQuery) { refresh(reset=true);return }
        val wanted=if(reset || query!=loadedQuery)40 else maxOf(restoredCount,40,rows.size)
        home.more.isEnabled=false
        fun receive(result:DaemonClient.Page) {
            if(!visible || token!=request)return
            if(append && result.generation!=generation) { refresh();return }
            rows=if(append)(rows+result.rows).distinctBy { it.id } else result.rows
            loadedQuery=query
            latest=Bundle(result.status).apply {
                if(result.issue.isNotEmpty())putString("issue",result.issue)
                putBoolean("historyReadFailed",result.issue.isNotEmpty())
                putString("operationIssue",operationIssue);putString("recoveryMessage",client.recoveryMessage);putLong("checkedAt",System.currentTimeMillis())
            }
            offline=result.offline;generation=result.generation
            syncUndo(latest)
            home.show(result,rows,recorderState(client.connectionStage(),latest.getBoolean("paused"),latest.getBoolean("active"),latest.getString("selfTest")=="PASS",latest.getString("issue").orEmpty().isNotEmpty()),query)
            if(reset){restoredAnchor=null;restoredCount=40;home.list.setSelection(0)}
            else restoredAnchor?.let { anchor -> home.list.post { home.restore(anchor) };restoredAnchor=null }
            restoredCount=maxOf(40,rows.size)
            recorderContent?.let { fillRecorder(it,recorderDialog!!) }
            if(pageKind=="diagnostics")diagnosticPage?.update?.invoke(latest,offline)
            if(pageKind=="settings")settingsPage?.update?.invoke(latest)
            if(pageKind=="help" && helpConnected!=client.connected())showHelp()
            when(pageKind) {
                "restore-preview"->{pageKind="";preview(previewId)}
                "restore-diagnostics"->{pageKind="";diagnostics()}
            }
            main.post { maybeShowPrompts() }
        }
        // Binder pages remain bounded to 40 items. Refresh all previously loaded pages
        // before replacing the list so observer updates do not discard the scroll anchor.
        fun read(offsetToRead:Int,accumulated:List<DaemonClient.Row>,expected:Long?) {
            client.requestPage(query,offsetToRead) { result ->
                if(!visible || token!=request)return@requestPage
                if(expected!=null && expected!=result.generation) { refresh(reset=true);return@requestPage }
                val combined=accumulated+result.rows
                if(!append && result.issue.isEmpty() && result.rows.isNotEmpty() && combined.size<minOf(wanted,result.total))
                    read(combined.size,combined,result.generation)
                else receive(result.copy(rows=combined))
            }
        }
        read(offset,emptyList(),null)
    }
    private fun track(dialog:Dialog):Dialog {
        val origin=currentFocus
        dialogs.add(dialog);dialog.setOnDismissListener {
            dialogs.remove(dialog)
            if(recorderDialog===dialog){recorderDialog=null;recorderContent=null}
            if(visible) {
                if(origin?.isAttachedToWindow==true && origin.isShown)origin.requestFocus()
                else if(pageKind=="settings")page?.findViewWithTag<View>(settingsFocus)?.requestFocus()
            }
        };return dialog
    }
    private fun sheet(title:Int,body:(LinearLayout,Dialog)->Unit)=track(ui.sheet(s(title),body))
    private fun message(title:Int,body:Int) { confirm(title,body,R.string.ok,false,showCancel=false) {} }
    private fun confirm(title:Int,body:Int,positive:Int,danger:Boolean=false,showCancel:Boolean=true,action:()->Unit) {
        val box=ui.column().apply {
            setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(16));background=ui.shape(ui.color(R.color.surface),24)
            accessibilityPaneTitle=s(title)
        }
        val dialog=Dialog(this)
        box.addView(ui.title(s(title)).apply { isAccessibilityHeading=true });box.addView(ui.space(16));ui.paragraph(box,s(body))
        val actions=LinearLayout(this).apply { gravity=Gravity.END }
        if(resources.configuration.fontScale>1.3f)actions.orientation=LinearLayout.VERTICAL
        if(showCancel)actions.addView(ui.button(s(R.string.cancel)) { dialog.dismiss() })
        actions.addView(ui.button(s(positive),danger=danger) { dialog.dismiss();action() });box.addView(actions)
        val scroll=ScrollView(this).apply { addView(box) };dialog.setContentView(scroll)
        ScreenPrivacy.apply(dialog.window,settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        track(dialog).show();dialog.window?.setLayout(minOf(ui.dp(400),windowManager.currentWindowMetrics.bounds.width()-ui.dp(40)),-2)
    }
    private fun removeNotice() {
        snackbar?.let { root.removeView(it) };snackbar=null
        listOfNotNull(home.root,page).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin=0;view.layoutParams=it }
        }
    }
    private fun addNotice(notice:View) {
        removeNotice();snackbar=notice
        root.addView(notice,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(ui.dp(20),0,ui.dp(20),ui.dp(8)) })
        notice.post {
            if(snackbar===notice)listOfNotNull(home.root,page).forEach { view ->
                (view.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin=notice.height+ui.dp(16);view.layoutParams=it }
            }
        }
    }
    private fun syncUndo(status:Bundle) {
        // A failed status read is not evidence that the daemon consumed Undo.
        if(client.connected() && !status.containsKey("undoToken"))return
        val next=if(client.connected())status.getLong("undoToken",0) else 0
        if(next!=undoToken) { removeNotice();undoToken=next }
        if(undoToken!=0L && snackbar==null)showUndoNotice()
    }
    private fun showUndoNotice(message:Int=R.string.deleted) {
        if(undoToken==0L)return
        val expected=undoToken
        val notice=ui.column().apply {
            id=R.id.delete_undo_notice;tag=expected
            setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8))
            background=ui.shape(ui.color(R.color.secondary_surface),14)
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(ui.text(s(message),14f))
            if(message!=R.string.deleted)addView(ui.text(s(R.string.deleted),13f,true))
        }
        val actions=LinearLayout(this).apply {
            if(resources.configuration.fontScale>1.3f)orientation=LinearLayout.VERTICAL
        }
        actions.addView(ui.button(s(R.string.port_undo)) {
            command({it.undoDelete(expected)}) { response ->
                notifyUser(if(response.getBoolean("undoAlreadyPresent"))R.string.port_already_saved else R.string.port_restored)
            }
        })
        actions.addView(ui.button(s(R.string.port_dismiss_notice)) { command({it.dismissUndo(expected)}) })
        notice.addView(actions);addNotice(notice)
    }
    private fun notifyUser(id:Int) {
        if(undoToken!=0L) { showUndoNotice(id);return }
        val notice=ui.text(s(id),14f).apply {
            setTextColor(ui.color(R.color.on_accent));background=ui.shape(ui.accent,14);setPadding(ui.dp(20),ui.dp(14),ui.dp(20),ui.dp(14))
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addNotice(notice)
        val timeout=getSystemService(AccessibilityManager::class.java)
            ?.getRecommendedTimeoutMillis(2200,AccessibilityManager.FLAG_CONTENT_TEXT)?.coerceAtLeast(2200)?.toLong()?:2200L
        main.postDelayed({if(snackbar===notice)removeNotice()},timeout)
    }
    private fun putClipboard(label:String,text:String):Boolean=try {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label,text));true
    } catch (_:Exception) {false}
    private fun copyText(label:String,text:String,onCopied:()->Unit) {
        if(putClipboard(label,text))onCopied() else showCopyFailure(label,text,onCopied)
    }
    private fun showCopyFailure(label:String,text:String,onCopied:()->Unit) {
        sheet(R.string.copy_failed_title) { box,dialog ->
            ui.paragraph(box,s(R.string.port_copy_retry_body))
            box.addView(ui.button(s(R.string.port_retry),primary=true) {
                if(putClipboard(label,text)){dialog.dismiss();onCopied()}
            })
            box.addView(ui.button(s(R.string.view_full)) {
                dialog.dismiss()
                sheet(R.string.port_full_text) { full,d ->
                    full.addView(ui.text(text,17f).apply { setTextIsSelectable(true);isSaveEnabled=false })
                    full.addView(ui.space(16))
                    full.addView(ui.button(s(R.string.port_retry),primary=true) {
                        if(putClipboard(label,text)){d.dismiss();onCopied()}
                    })
                    full.addView(ui.button(s(R.string.cancel)){d.dismiss()})
                }
            })
            box.addView(ui.button(s(R.string.cancel)){dialog.dismiss()})
        }
    }
    private fun copyEntry(id:Long) {
        val token=++navigation
        client.getText(id) { result ->
            if(!visible || isFinishing || isDestroyed || token!=navigation)return@getText
            if(result.issue.isNotEmpty()){operationIssue=result.issue;notifyUser(R.string.history_read_failed);refresh();return@getText}
            operationIssue=""
            val text=result.text
            if(text==null){notifyUser(R.string.entry_gone);closePage();refresh();return@getText}
            copyText(s(R.string.app_name),text) {
                if(settings.returnAfterCopy)finish() else {
                    notifyUser(R.string.copied)
                    val index=rows.indexOfFirst { it.id==id }+home.list.headerViewsCount-home.list.firstVisiblePosition
                    val row=home.list.getChildAt(index)
                    row?.setBackgroundColor(ui.color(R.color.selected))
                    row?.postDelayed({row.setBackgroundColor(Color.TRANSPARENT)},300)
                }
            }
        }
    }
    private fun command(action:(IClipboardDaemon)->Bundle,after:((Bundle)->Unit)?=null) {
        commandFor(R.string.operation_failed,R.string.operation_body,action,after)
    }
    private fun commandFor(failureTitle:Int,failureBody:Int,action:(IClipboardDaemon)->Bundle,after:((Bundle)->Unit)?=null) {
        if(!client.connected()){message(R.string.not_connected,R.string.connect_mutation);return}
        val token=navigation
        client.command(action) { response ->
            if(!visible || isFinishing || isDestroyed)return@command
            if(!response.getBoolean("ok")) {
                operationIssue=response.getString("issue","OPERATION_FAILED")
                if(operationIssue=="ENTRY_MISSING") { if(token==navigation)notifyUser(R.string.entry_gone) }
                else if(operationIssue=="UNDO_NOT_AVAILABLE") { if(token==navigation)notifyUser(R.string.port_undo_unavailable) }
                else if(operationIssue=="UNDO_HISTORY_FULL" && token==navigation)sheet(R.string.port_undo_full_title) { box,dialog ->
                    ui.paragraph(box,s(R.string.port_undo_full_body))
                    box.addView(ui.button(s(R.string.history_limit),primary=true){dialog.dismiss();showSettings();limitDialog()})
                    box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
                }
                else if(token==navigation)sheet(failureTitle) { box,dialog ->
                ui.paragraph(box,s(failureBody))
                box.addView(ui.button(s(R.string.view_diagnostics)){dialog.dismiss();diagnostics()})
                box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
                }
            }
            else {operationIssue="";latest=response;syncUndo(response);if(token==navigation)after?.invoke(response)}
            refresh()
        }
    }
    private fun delete(id:Long) { commandFor(R.string.port_delete_failed,R.string.port_delete_failed_body,{it.deleteEntry(id)}) { closePage();notifyUser(R.string.deleted) } }
    private fun entryActions(row:DaemonClient.Row) {
        hideKeyboard()
        sheet(R.string.saved_text) { box,dialog ->
            box.addView(ui.text(row.preview).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END })
            box.addView(ui.text(android.text.format.DateUtils.getRelativeTimeSpanString(row.time),13f,true));box.addView(ui.space(16))
            ui.actionRow(box,s(if(settings.returnAfterCopy)R.string.copy_return else R.string.copy),R.drawable.ic_copy){dialog.dismiss();copyEntry(row.id)}
            ui.actionRow(box,s(R.string.view_full),R.drawable.ic_eye){dialog.dismiss();preview(row.id)}
            ui.actionRow(box,s(R.string.delete),R.drawable.ic_delete,client.connected(),true){dialog.dismiss();delete(row.id)}
            if(!client.connected())ui.paragraph(box,s(R.string.connect_mutation))
        }
    }
    private fun showPage(kind:String,view:View) {
        navigation++
        if(pageKind=="help")helpScroll=findScroll(page)?.scrollY?:helpScroll
        if(kind!="diagnostics")diagnosticPage=null
        hideKeyboard();page?.let { root.removeView(it) }
        if(page==null)onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,back)
        pageKind=kind;page=view;home.root.visibility=View.GONE;root.addView(view,FrameLayout.LayoutParams(-1,-1))
        snackbar?.let { notice ->
            (view.layoutParams as FrameLayout.LayoutParams).apply { bottomMargin=notice.height+ui.dp(16);view.layoutParams=this }
            notice.bringToFront()
        }
    }
    private fun closePage() {
        navigation++
        if(pageKind=="help")helpScroll=findScroll(page)?.scrollY?:helpScroll
        if(page==null){pageKind="";previewId=-1;return}
        page?.let { root.removeView(it) };page=null;pageKind="";previewId=-1;diagnosticPage=null
        helpFromSettings=false;helpSettingsScroll=0
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(back);home.root.visibility=View.VISIBLE
    }
    private fun backPage() {
        when(pageKind) {
            "licenses"->if(licensesFromSettings) { licensesFromSettings=false;restoredSettingsScroll=helpSettingsScroll;showSettings() } else showHelp()
            "help"->if(helpFromSettings) {
                restoredSettingsScroll=helpSettingsScroll;helpFromSettings=false;showSettings()
            } else closePage()
            else->closePage()
        }
    }
    private fun preview(id:Long) {
        val token=++navigation
        client.getText(id) { result ->
            if(!visible || isFinishing || isDestroyed || token!=navigation)return@getText
            if(result.issue.isNotEmpty()){operationIssue=result.issue;notifyUser(R.string.history_read_failed);refresh();return@getText}
            operationIssue=""
            val text=result.text
            if(text==null){notifyUser(R.string.entry_gone);return@getText}
            previewId=id
            showPage("preview",pages.page(s(R.string.saved_text),::closePage) { content,footer ->
                rows.firstOrNull { it.id==id }?.let { content.addView(ui.text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(it.time)),13f,true));content.addView(ui.space(20)) }
                val query=home.search.text.toString()
                val highlighted=SpannableString(text)
                var match=if(query.isEmpty())-1 else text.indexOf(query,ignoreCase=true)
                val firstMatch=match
                while(match>=0) {
                    highlighted.setSpan(BackgroundColorSpan(ui.color(R.color.selected)),match,match+query.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    match=text.indexOf(query,match+query.length,ignoreCase=true)
                }
                val full=ui.text(highlighted,17f).apply { tag="preview-text";setTextIsSelectable(true);isSaveEnabled=false }
                content.addView(full)
                full.post {
                    val scroll=findScroll(page)
                    scroll?.tag="preview-scroll"
                    if(firstMatch>=0)full.layout?.let { layout ->
                        scroll?.scrollTo(0,(full.top+layout.getLineTop(layout.getLineForOffset(firstMatch))-ui.dp(20)).coerceAtLeast(0))
                    }
                }
                footer.addView(ui.button(s(if(settings.returnAfterCopy)R.string.copy_return else R.string.copy),primary=true){copyEntry(id)})
                footer.addView(ui.button(s(R.string.delete),danger=true){delete(id)}.apply { isEnabled=client.connected() })
                if(!client.connected())footer.addView(ui.text(s(R.string.connect_mutation),13f,true))
            })
        }
    }
    private fun help() { helpFromSettings=false;helpSettingsScroll=0;showHelp() }
    private fun settingsHelp() {
        helpFromSettings=true;helpSettingsScroll=findScroll(page)?.scrollY?:0;showHelp()
    }
    private fun showHelp() {
        if(pageKind=="help")helpScroll=findScroll(page)?.scrollY?:helpScroll
        helpConnected=client.connected()
        val view=pages.help(::backPage,::openWebsite,{client.openShizuku()},{client.connect(true)},helpConnected,helpTopics) {
            helpScroll=findScroll(page)?.scrollY?:0;showLicenses()
        }
        val scroll=helpScroll
        showPage("help",view);view.post { findScroll(view)?.scrollTo(0,scroll) }
    }
    private fun showLicenses() {
        if(pageKind=="help")licensesFromSettings=false
        if(pageKind=="settings") { licensesFromSettings=true;helpSettingsScroll=findScroll(page)?.scrollY?:0 }
        val view=pages.licenses(::backPage)
        val scroll=restoredLicenseScroll;restoredLicenseScroll=0
        showPage("licenses",view);view.post { findScroll(view)?.scrollTo(0,scroll) }
    }
    private fun openWebsite(url:String) {
        try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }
        catch (_:ActivityNotFoundException){notifyUser(R.string.browser_missing)}
    }
    private fun diagnostics() {
        val details=pages.diagnostics(latest,offline,::closePage,copy={
            copyText(s(R.string.diagnostics),pages.report(latest,offline)){notifyUser(R.string.copied)}
        },healthAction={ action ->
            when(action) {
                DetailPages.DiagnosticAction.RETRY->refresh()
                DetailPages.DiagnosticAction.CONNECT->client.connect(true)
                DetailPages.DiagnosticAction.RESUME->command({it.setPaused(false)})
                DetailPages.DiagnosticAction.TEST->connectionTest()
            }
        })
        diagnosticPage=details;showPage("diagnostics",details.view)
    }
    private fun showMenu(anchor:View) {
        hideKeyboard()
        val box=ui.column().apply { setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));background=ui.shape(ui.color(R.color.surface),16) }
        val popup=PopupWindow(this).apply { isFocusable=true;isOutsideTouchable=true;setBackgroundDrawable(ui.shape(ui.color(R.color.surface),16));elevation=ui.dp(6).toFloat();width=ui.dp(280) }
        fun option(id:Int,enabled:Boolean=true,danger:Boolean=false,action:()->Unit) {
            ui.actionRow(box,s(id),enabled=enabled,danger=danger){popup.dismiss();action()}
        }
        option(if(latest.getBoolean("paused"))R.string.resume_recording else R.string.pause_recording,client.connected()){command({it.setPaused(!latest.getBoolean("paused"))})}
        option(R.string.settings,action=::showSettings)
        option(R.string.diagnostics,action=::diagnostics)
        option(R.string.help_privacy,action=::help)
        box.addView(ui.divider())
        option(R.string.clear_history,client.connected(),true){confirm(R.string.clear_title,R.string.clear_body,R.string.clear_history,true){commandFor(R.string.port_clear_failed,R.string.port_clear_failed_body,{it.clearHistory()}){notifyUser(R.string.cleared)}}}
        if(!client.connected())box.addView(ui.text(s(R.string.connect_mutation),13f,true).apply { setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)) })
        popup.contentView=ScrollView(this).apply { addView(box);isVerticalScrollBarEnabled=false }
        val maxHeight=minOf(ui.dp(580),popup.getMaxAvailableHeight(anchor))
        popup.contentView.measure(View.MeasureSpec.makeMeasureSpec(popup.width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST))
        popup.height=popup.contentView.measuredHeight;menu=popup
        popup.showAsDropDown(anchor,0,0,Gravity.END)
    }
    private fun limitDialog() {
        sheet(R.string.history_limit) { box,dialog ->
            ui.paragraph(box,s(R.string.limit_body));box.addView(ui.text(s(R.string.saved_texts),13f,true))
            val field=EditText(this).apply {
                inputType=InputType.TYPE_CLASS_NUMBER;setText(java.text.NumberFormat.getIntegerInstance().apply { isGroupingUsed=false }.format(latest.getInt("limit",DEFAULT_LIMIT)));selectAll()
                textSize=18f;setTextColor(ui.ink);background=ui.shape(ui.color(R.color.surface),12,ui.muted)
                setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));contentDescription=s(R.string.saved_texts)
                filters=arrayOf(InputFilter.LengthFilter(4))
            }
            box.addView(field,LinearLayout.LayoutParams(-1,-2));box.addView(ui.space(12));ui.paragraph(box,s(R.string.limit_warning))
            val actions=LinearLayout(this).apply { gravity=Gravity.END }
            if(resources.configuration.fontScale>1.3f)actions.orientation=LinearLayout.VERTICAL
            actions.addView(ui.button(s(R.string.cancel)){dialog.dismiss()})
            actions.addView(ui.button(s(R.string.save),primary=true){
                val value=field.text.toString().toIntOrNull()
                if(value==null || value !in MIN_LIMIT..MAX_LIMIT){field.error=s(R.string.limit_invalid);field.requestFocus();return@button}
                dialog.dismiss();commandFor(R.string.port_limit_failed,R.string.port_limit_failed_body,{it.setLimit(value)})
            });box.addView(actions)
        }
    }
    private fun appearance() {
        sheet(R.string.appearance) { box,dialog ->
            ui.paragraph(box,s(R.string.appearance_body))
            val radios=RadioGroup(this)
            listOf(R.string.system,R.string.light,R.string.dark).forEachIndexed { index,label ->
                radios.addView(RadioButton(this).apply { id=View.generateViewId();text=s(label);textSize=16f;setTextColor(ui.ink);minimumHeight=ui.dp(52);tag=AppSettings.modes[index];isChecked=settings.appearance==tag })
            }
            radios.setOnCheckedChangeListener { group,id ->
                val mode=group.findViewById<RadioButton>(id).tag as String
                if(mode!=settings.appearance){settings.appearance=mode;reopenAppearance=true;dialog.dismiss();recreate()}
            }
            box.addView(radios);box.addView(ui.space(12));ui.paragraph(box,s(R.string.system_hint))
            box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
        }
    }
    private fun addTile() {
        sheet(R.string.open_faster) { box,dialog ->
            ui.paragraph(box,s(R.string.tile_body))
            box.addView(ui.button(s(R.string.add_tile),primary=true) { dialog.dismiss();requestTile() })
            box.addView(ui.button(s(R.string.not_now)){dialog.dismiss()});ui.paragraph(box,s(R.string.tile_manual))
        }
    }
    private fun requestTile() {
        if(!settings.tileEnabled){notifyUser(R.string.tile_disabled_notice);return}
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(ComponentName(this,ClipboardTileService::class.java),s(R.string.tile_name),Icon.createWithResource(this,R.drawable.ic_cliphistory_system),mainExecutor) { result ->
                if(visible)notifyUser(if(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)R.string.tile_available else R.string.tile_manual)
            }
        } catch (_:Exception){message(R.string.add_tile_menu,R.string.tile_manual)}
    }
    private fun connectionTest() {
        if(!client.connected()){message(R.string.not_connected,R.string.connect_mutation);return}
        confirm(R.string.test_title,R.string.test_body,R.string.run_test){
            val nonce="ClipHistory self-test ${UUID.randomUUID()}"
            command({it.armTest(nonce)}) {
                copyText(s(R.string.connection_test),nonce) {
                    recorder();main.postDelayed({if(visible)refresh()},10_500)
                }
            }
        }
    }
    private fun recorder() {
        recorderDialog?.dismiss()
        val dialog=sheet(R.string.recorder) { box,d ->
            val body=ui.column();box.addView(body);recorderContent=body;fillRecorder(body,d)
        }
        recorderDialog=dialog
    }
    private fun findScroll(view:View?):ScrollView? {
        if(view is ScrollView)return view
        if(view is ViewGroup)for(index in 0 until view.childCount)findScroll(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun showSettings() {
        val created=SettingsPage(ui,pages,settings,client).create(latest,::closePage,{ action ->
            settingsFocus=action.name
            when(action) {
                SettingsPage.Action.LIMIT->limitDialog()
                SettingsPage.Action.DUPLICATES->duplicateDialog()
                SettingsPage.Action.START->client.connect(true)
                SettingsPage.Action.STOP->confirm(R.string.stop_recorder,R.string.stop_recorder_body,R.string.stop_recorder) {
                    client.stopRecording { result ->
                        if(visible) {
                            if(!result.getBoolean("ok")){operationIssue=result.getString("issue","");notifyUser(R.string.preference_failed)}
                            refresh()
                        }
                    }
                }
                SettingsPage.Action.TILE_ADD->addTile()
                SettingsPage.Action.TILE_MODE->tileMode()
                SettingsPage.Action.APPEARANCE->appearance()
                SettingsPage.Action.MOTION->motionChoice()
                SettingsPage.Action.WELCOME_FREQUENCY->welcomeFrequency()
                SettingsPage.Action.BATTERY->backgroundHelp()
                SettingsPage.Action.WELCOME->showWelcome()
                SettingsPage.Action.SUPPORT->support()
                SettingsPage.Action.GUIDE->settingsHelp()
                SettingsPage.Action.NOTIFICATIONS->requestRecoveryNotifications()
                SettingsPage.Action.SOURCE->openWebsite("https://github.com/saroo98/cliphistory-android")
                SettingsPage.Action.LICENSES->showLicenses()
            }
        },{ issue ->
            if(issue.isNotEmpty()){operationIssue=issue;notifyUser(R.string.preference_failed)}
            applyPrivacy();updateCopyHint();refresh()
        })
        settingsPage=created;showPage("settings",created.view)
        val scroll=restoredSettingsScroll;restoredSettingsScroll=0
        created.view.post {
            created.view.findViewWithTag<View>(settingsFocus)?.requestFocus()
            findScroll(created.view)?.scrollTo(0,scroll)
        }
    }
    private fun duplicateDialog() {
        if(!client.connected()){message(R.string.not_connected,R.string.connect_mutation);return}
        sheet(R.string.duplicate_handling) { box,dialog ->
            ui.paragraph(box,s(R.string.duplicates_body))
            val radios=RadioGroup(this)
            listOf(DuplicateMode.UNIQUE_TEXT to R.string.duplicates_unique,DuplicateMode.CONSECUTIVE_ONLY to R.string.duplicates_consecutive).forEach { (mode,label) ->
                radios.addView(RadioButton(this).apply {
                    id=View.generateViewId();text=s(label);textSize=16f;setTextColor(ui.ink);minimumHeight=ui.dp(52)
                    tag=mode.value;isChecked=latest.getInt("duplicateMode")==mode.value
                })
            }
            var saving=false
            radios.setOnCheckedChangeListener { group,id ->
                if(saving)return@setOnCheckedChangeListener
                val selected=group.findViewById<RadioButton>(id).tag as Int
                saving=true
                (0 until radios.childCount).forEach { radios.getChildAt(it).isEnabled=false }
                client.command({it.setDuplicateMode(selected)}) { response ->
                    if(!visible || !dialog.isShowing)return@command
                    if(response.containsKey("duplicateMode"))latest=response
                    if(!response.getBoolean("ok")) {
                        operationIssue=response.getString("issue","OPERATION_FAILED")
                        (0 until radios.childCount).map { radios.getChildAt(it) as RadioButton }.forEach { it.isChecked=it.tag==latest.getInt("duplicateMode") }
                        notifyUser(R.string.preference_failed)
                    } else { latest=response;operationIssue="" }
                    (0 until radios.childCount).forEach { radios.getChildAt(it).isEnabled=true }
                    saving=false;refresh()
                }
            }
            box.addView(radios);box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
        }
    }
    private fun choiceSheet(title:Int,labels:List<Int>,selected:Int,changed:(Int)->Unit) {
        sheet(title) { box,dialog ->
            val radios=RadioGroup(this)
            labels.forEachIndexed { index,label ->
                radios.addView(RadioButton(this).apply {
                    id=View.generateViewId();tag=index;text=s(label);textSize=16f;setTextColor(ui.ink);minimumHeight=ui.dp(52);isChecked=index==selected
                })
            }
            radios.setOnCheckedChangeListener { group,id ->
                changed(group.findViewById<RadioButton>(id).tag as Int)
                dialog.window?.setWindowAnimations(if(ScreenPrivacy.motionEnabled(settings))R.style.SheetMotion else 0)
                settingsPage?.update?.invoke(latest)
            }
            box.addView(radios);box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
        }
    }
    private fun tileMode()=choiceSheet(R.string.html_port_tile_opens,listOf(R.string.quick_copy,R.string.open_full_history),if(settings.tileMode=="quick")0 else 1) { settings.tileMode=if(it==0)"quick" else "history" }
    private fun motionChoice()=choiceSheet(R.string.html_port_motion_preference,listOf(R.string.motion_system,R.string.html_port_motion_reduced),if(settings.motion)0 else 1) { settings.motion=it==0 }
    private fun welcomeFrequency()=choiceSheet(R.string.html_port_welcome_frequency,listOf(R.string.welcome_once,R.string.welcome_each,R.string.welcome_off),listOf("once","each","never").indexOf(settings.welcome)) { settings.welcome=listOf("once","each","never")[it] }
    private fun requestRecoveryNotifications() {
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),72)
        else {
            val intent=Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)
            try { startActivity(intent) } catch (_:Exception) { BackgroundSettings(this).open(packageName) }
        }
    }
    private fun backgroundHelp(suggestion:Boolean=false) {
        val dialog=sheet(R.string.background_reliability) { box,d ->
            val battery=BackgroundSettings(this)
            ui.paragraph(box,s(R.string.background_optional));ui.paragraph(box,battery.status())
            if(client.recoveryMessage.isNotEmpty())ui.paragraph(box,client.recoveryMessage)
            box.addView(ui.button(s(R.string.open_app_battery),primary=true) { d.dismiss();if(!battery.open(packageName))notifyUser(R.string.battery_manual) })
            val other=ui.column().apply { visibility=View.GONE }
            box.addView(ui.button(s(R.string.port_battery_options)) { other.visibility=if(other.visibility==View.GONE)View.VISIBLE else View.GONE })
            ui.actionRow(other,s(R.string.open_shizuku_battery)) { d.dismiss();if(!battery.open("moe.shizuku.privileged.api"))notifyUser(R.string.battery_manual) }
            ui.actionRow(other,s(R.string.battery_optimization_list)) { d.dismiss();if(!battery.openOptimizationList())notifyUser(R.string.battery_manual) }
            ui.actionRow(other,s(R.string.recovery_notification)) { d.dismiss();requestRecoveryNotifications() }
            box.addView(other)
            box.addView(ui.button(s(R.string.not_now)){d.dismiss()})
        }
        if(suggestion)dialog.setOnDismissListener {
            dialogs.remove(dialog)
            if(visible)settings.backgroundSeen=true
        }
    }
    fun showBackgroundGuide() { backgroundHelp() }
    fun runConnectionGuideTest() { connectionTest() }
    private fun maybeShowPrompts() {
        if(!visible || isFinishing || isDestroyed || dialogs.isNotEmpty() || menu?.isShowing==true || page!=null)return
        if(pendingWelcome) {
            pendingWelcome=false
            if(settings.welcome=="each" || (settings.welcome=="once" && !settings.welcomeSeen)) { showWelcome();return }
        }
        if(settings.backgroundSuggestion && !settings.backgroundSeen && client.preferences.options().setupCompleted)
            backgroundHelp(true)
    }
    private fun showWelcome() {
        val dialog=sheet(R.string.welcome_title) { box,d ->
            box.addView(ui.brandIdentity());box.addView(ui.space(16))
            ui.paragraph(box,s(R.string.welcome_body))
            box.addView(ui.button(s(R.string.port_welcome_setup),primary=true){settings.welcomeSeen=true;d.dismiss();help()})
            box.addView(ui.button(s(R.string.port_welcome_explore)){settings.welcomeSeen=true;d.dismiss();main.post { maybeShowPrompts() }})
        }
        dialog.setOnDismissListener {
            dialogs.remove(dialog)
            if(visible)settings.welcomeSeen=true else pendingWelcome=true
        }
    }
    private fun support() {
        sheet(R.string.support_project) { box,dialog ->
            ui.paragraph(box,s(R.string.support_optional))
            ui.actionRow(box,s(R.string.leave_feedback)) { dialog.dismiss();openWebsite("https://github.com/saroo98/cliphistory-android/issues/new") }
            ui.actionRow(box,s(R.string.star_github)) { dialog.dismiss();openWebsite("https://github.com/saroo98/cliphistory-android") }
            ui.actionRow(box,s(R.string.share_app)) {
                dialog.dismiss()
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT,"https://github.com/saroo98/cliphistory-android"),s(R.string.share_app)))
            }
        }
    }
    private fun fillRecorder(box:LinearLayout,dialog:Dialog) {
        box.removeAllViews()
        val test=if(offline)"NOT_RUN" else latest.getString("selfTest","NOT_RUN")
        val title=when(test){"PASS"->R.string.test_passed;"WAITING"->R.string.test_waiting;"NOT_RUN"->R.string.test_not_run;else->R.string.test_failed}
        box.addView(ui.title(s(title),18f));box.addView(ui.space(12))
        pages.keyValue(box,s(R.string.clipboard_event),s(if(test=="PASS" || test=="STORAGE_FAILED")R.string.received else R.string.not_confirmed))
        pages.keyValue(box,s(R.string.storage_check),s(if(!offline && latest.getBoolean("storageVerified"))R.string.passed else R.string.not_confirmed))
        pages.keyValue(box,s(R.string.recording),s(if(!offline && latest.getBoolean("active"))R.string.active else R.string.inactive))
        if(!offline && latest.getLong("queueDrops")>0) {
            pages.keyValue(box,s(R.string.missed_copies),latest.getLong("queueDrops").toString())
            ui.paragraph(box,s(R.string.missed_body))
        }
        if(test=="STORAGE_FAILED")ui.paragraph(box,s(R.string.test_storage_failed))
        if(test=="NO_CALLBACK_RECEIVED")ui.paragraph(box,s(R.string.test_failed_body))
        box.addView(ui.space(16));ui.paragraph(box,s(R.string.test_boundary))
        box.addView(ui.button(s(R.string.run_connection_test),primary=true){dialog.dismiss();connectionTest()}.apply { isEnabled=client.connected() && test!="WAITING" })
        box.addView(ui.button(s(R.string.view_diagnostics)){dialog.dismiss();diagnostics()})
        box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
    }
}
