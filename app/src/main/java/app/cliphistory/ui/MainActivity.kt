package app.cliphistory.ui

import android.app.*
import android.content.*
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.*
import android.text.*
import android.view.*
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
    private var snackbar:TextView?=null
    private val dialogs=mutableSetOf<Dialog>()
    private var menu:PopupWindow?=null
    private var recorderContent:LinearLayout?=null
    private var recorderDialog:Dialog?=null
    private val changed:()->Unit={ if(visible)refresh() }
    private val searchChanged=Runnable { if(visible)refresh(reset=true) }
    private val back=OnBackInvokedCallback { closePage() }
    private fun s(id:Int)=getString(id)

    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(AppSettings.themedContext(newBase)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        @Suppress("DEPRECATION")
        window.setDecorFitsSystemWindows(false)
        val light=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK!=Configuration.UI_MODE_NIGHT_YES
        settings=AppSettings(this);ui=Ui(this);pages=DetailPages(ui)
        root=FrameLayout(this).apply { setBackgroundColor(ui.color(R.color.canvas)) }
        home=HistoryHome(ui,::showMenu,{ client.connect(true) },{ client.openShizuku() },::help,::recorder,
            { command({it.setPaused(false)}) },::connectionTest,{ refresh() },{ refresh(append=true) },::diagnostics)
        root.addView(home.root,FrameLayout.LayoutParams(-1,-1));setContentView(root)
        window.insetsController?.setSystemBarsAppearance(if(light)WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime=insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            home.hint.visibility=if(ime.bottom>bars.bottom)View.GONE else View.VISIBLE
            insets
        }
        root.requestApplyInsets()
        home.search.setText(state?.getString("query").orEmpty())
        home.clear.visibility=if(home.search.text.isEmpty())View.GONE else View.VISIBLE
        home.search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                home.clear.visibility=if(s.isNullOrEmpty())View.GONE else View.VISIBLE
                request++;main.removeCallbacks(searchChanged);main.postDelayed(searchChanged,150)
            }
            override fun afterTextChanged(s:Editable?){}
        })
        home.search.setOnEditorActionListener { _,_,_ -> hideKeyboard();true }
        home.list.setOnItemClickListener { _,_,position,_ -> if(position<home.adapter.count)copyEntry(home.adapter.getItem(position).id) }
        home.list.setOnItemLongClickListener { _,_,position,_ ->
            if(position<home.adapter.count)entryActions(home.adapter.getItem(position));true
        }
        updateCopyHint()
        when(state?.getString("page")) {
            "help"->help()
            "preview"->{previewId=state.getLong("preview",-1);pageKind="restore-preview"}
            "diagnostics"->pageKind="restore-diagnostics"
        }
    }
    override fun onStart() {
        super.onStart();visible=true;client.addListener(changed);client.connect(false);refresh()
    }
    override fun onStop() {
        visible=false;request++;client.removeListener(changed);main.removeCallbacksAndMessages(null)
        dialogs.toList().forEach { it.dismiss() };menu?.dismiss();super.onStop()
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("query",home.search.text.toString());out.putString("page",pageKind);out.putLong("preview",previewId)
        super.onSaveInstanceState(out)
    }
    private fun hideKeyboard() { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(home.search.windowToken,0);home.search.clearFocus() }
    private fun updateCopyHint() { home.hint.text=s(if(settings.returnAfterCopy)R.string.copy_hint else R.string.stay_hint) }
    private fun refresh(append:Boolean=false,reset:Boolean=false) {
        val token=++request;val query=home.search.text.toString();val offset=if(append)rows.size else 0
        if(append && query!=loadedQuery) { refresh(reset=true);return }
        val wanted=if(reset || query!=loadedQuery)40 else maxOf(40,rows.size)
        home.more.isEnabled=false
        fun receive(result:DaemonClient.Page) {
            if(!visible || token!=request)return
            if(append && result.generation!=generation) { refresh();return }
            rows=if(append)(rows+result.rows).distinctBy { it.id } else result.rows
            loadedQuery=query
            latest=result.status;offline=result.offline;generation=result.generation
            home.show(result,rows,recorderState(client.connectionStage(),latest.getBoolean("paused"),latest.getBoolean("active"),latest.getString("selfTest")=="PASS",latest.getString("issue").orEmpty().isNotEmpty()),query)
            if(reset)home.list.setSelection(0)
            recorderContent?.let { fillRecorder(it,recorderDialog!!) }
            when(pageKind) {
                "restore-preview"->{pageKind="";preview(previewId)}
                "restore-diagnostics"->{pageKind="";diagnostics()}
            }
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
        dialogs.add(dialog);dialog.setOnDismissListener {
            dialogs.remove(dialog)
            if(recorderDialog===dialog){recorderDialog=null;recorderContent=null}
        };return dialog
    }
    private fun sheet(title:Int,body:(LinearLayout,Dialog)->Unit)=track(ui.sheet(s(title),body))
    private fun message(title:Int,body:Int) { confirm(title,body,R.string.ok,false,showCancel=false) {} }
    private fun confirm(title:Int,body:Int,positive:Int,danger:Boolean=false,showCancel:Boolean=true,action:()->Unit) {
        val box=ui.column().apply { setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(16));background=ui.shape(ui.color(R.color.surface),24) }
        val dialog=Dialog(this)
        box.addView(ui.title(s(title)));box.addView(ui.space(16));ui.paragraph(box,s(body))
        val actions=LinearLayout(this).apply { gravity=Gravity.END }
        if(showCancel)actions.addView(ui.button(s(R.string.cancel)) { dialog.dismiss() })
        actions.addView(ui.button(s(positive),danger=danger) { dialog.dismiss();action() });box.addView(actions)
        val scroll=ScrollView(this).apply { addView(box) };dialog.setContentView(scroll)
        dialog.window?.apply { addFlags(WindowManager.LayoutParams.FLAG_SECURE);setBackgroundDrawableResource(android.R.color.transparent) }
        track(dialog).show();dialog.window?.setLayout(minOf(ui.dp(400),windowManager.currentWindowMetrics.bounds.width()-ui.dp(40)),-2)
    }
    private fun notifyUser(id:Int) {
        snackbar?.let { root.removeView(it) }
        val notice=ui.text(s(id),14f).apply {
            setTextColor(ui.color(R.color.on_accent));background=ui.shape(ui.accent,14);setPadding(ui.dp(20),ui.dp(14),ui.dp(20),ui.dp(14))
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        snackbar=notice;root.addView(notice,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(ui.dp(20),0,ui.dp(20),ui.dp(8)) })
        main.postDelayed({root.removeView(notice);if(snackbar===notice)snackbar=null},2200)
    }
    private fun putClipboard(label:String,text:String):Boolean=try {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label,text));true
    } catch (_:Exception) {message(R.string.copy_failed_title,R.string.copy_failed_body);false}
    private fun copyEntry(id:Long) {
        client.getText(id) { text ->
            if(!visible || isFinishing || isDestroyed)return@getText
            if(text==null){notifyUser(R.string.entry_gone);closePage();refresh();return@getText}
            if(putClipboard(s(R.string.app_name),text)) {
                if(settings.returnAfterCopy)finish() else {
                    notifyUser(R.string.copied)
                    val index=rows.indexOfFirst { it.id==id }-home.list.firstVisiblePosition
                    val row=home.list.getChildAt(index)
                    row?.setBackgroundColor(ui.color(R.color.selected))
                    row?.postDelayed({row.setBackgroundColor(Color.TRANSPARENT)},300)
                }
            }
        }
    }
    private fun command(action:(IClipboardDaemon)->Bundle,after:((Bundle)->Unit)?=null) {
        if(!client.connected()){message(R.string.not_connected,R.string.connect_mutation);return}
        client.command(action) { response ->
            if(!visible || isFinishing || isDestroyed)return@command
            if(!response.getBoolean("ok"))sheet(R.string.operation_failed) { box,dialog ->
                ui.paragraph(box,s(R.string.operation_body))
                box.addView(ui.button(s(R.string.view_diagnostics)){dialog.dismiss();diagnostics()})
                box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
            }
            else {latest=response;after?.invoke(response)}
            refresh()
        }
    }
    private fun delete(id:Long) { command({it.deleteEntry(id)}) { closePage();notifyUser(R.string.deleted) } }
    private fun entryActions(row:DaemonClient.Row) {
        hideKeyboard()
        sheet(R.string.saved_text) { box,dialog ->
            box.addView(ui.text(row.preview).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END })
            box.addView(ui.text(android.text.format.DateUtils.getRelativeTimeSpanString(row.time),13f,true));box.addView(ui.space(16))
            ui.actionRow(box,s(R.string.copy),R.drawable.ic_copy){dialog.dismiss();copyEntry(row.id)}
            ui.actionRow(box,s(R.string.view_full),R.drawable.ic_eye){dialog.dismiss();preview(row.id)}
            ui.actionRow(box,s(R.string.delete),R.drawable.ic_delete,client.connected(),true){dialog.dismiss();delete(row.id)}
            if(!client.connected())ui.paragraph(box,s(R.string.connect_mutation))
        }
    }
    private fun showPage(kind:String,view:View) {
        hideKeyboard();page?.let { root.removeView(it) }
        if(page==null)onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,back)
        pageKind=kind;page=view;home.root.visibility=View.GONE;root.addView(view,FrameLayout.LayoutParams(-1,-1))
    }
    private fun closePage() {
        if(page==null)return
        page?.let { root.removeView(it) };page=null;pageKind="";previewId=-1
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(back);home.root.visibility=View.VISIBLE
    }
    private fun preview(id:Long) {
        client.getText(id) { text ->
            if(!visible || isFinishing || isDestroyed)return@getText
            if(text==null){notifyUser(R.string.entry_gone);return@getText}
            previewId=id
            showPage("preview",pages.page(s(R.string.saved_text),::closePage) { content,footer ->
                rows.firstOrNull { it.id==id }?.let { content.addView(ui.text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(it.time)),13f,true));content.addView(ui.space(20)) }
                content.addView(ui.text(text,17f).apply { setTextIsSelectable(true);isSaveEnabled=false })
                footer.addView(ui.button(s(if(settings.returnAfterCopy)R.string.copy_return else R.string.copy),primary=true){copyEntry(id)})
                footer.addView(ui.button(s(R.string.delete),danger=true){delete(id)}.apply { isEnabled=client.connected() })
                if(!client.connected())footer.addView(ui.text(s(R.string.connect_mutation),13f,true))
            })
        }
    }
    private fun help() { showPage("help",pages.help(::closePage)) }
    private fun diagnostics() {
        val snapshot=Bundle(latest);val disconnected=offline
        showPage("diagnostics",pages.diagnostics(snapshot,disconnected,::closePage) {
            if(putClipboard(s(R.string.diagnostics),pages.report(snapshot,disconnected)))notifyUser(R.string.copied)
        })
    }
    private fun showMenu(anchor:View) {
        hideKeyboard()
        val box=ui.column().apply { setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));background=ui.shape(ui.color(R.color.surface),16) }
        val popup=PopupWindow(this).apply { isFocusable=true;isOutsideTouchable=true;setBackgroundDrawable(ui.shape(ui.color(R.color.surface),16));elevation=ui.dp(6).toFloat();width=ui.dp(280) }
        fun option(id:Int,enabled:Boolean=true,danger:Boolean=false,action:()->Unit) {
            ui.actionRow(box,s(id),enabled=enabled,danger=danger){popup.dismiss();action()}
        }
        option(if(latest.getBoolean("paused"))R.string.resume_recording else R.string.pause_recording,client.connected()){command({it.setPaused(!latest.getBoolean("paused"))})}
        option(R.string.history_limit,client.connected(),action=::limitDialog)
        option(R.string.add_tile_menu,action=::addTile)
        option(R.string.connection_test,client.connected(),action=::connectionTest)
        option(R.string.diagnostics,action=::diagnostics)
        option(R.string.appearance,action=::appearance)
        option(R.string.copy_behavior,action=::copyBehavior)
        option(R.string.help_privacy,action=::help)
        box.addView(ui.divider())
        option(R.string.clear_history,client.connected(),true){confirm(R.string.clear_title,R.string.clear_body,R.string.clear_history,true){command({it.clearHistory()}){notifyUser(R.string.cleared)}}}
        if(!client.connected())box.addView(ui.text(s(R.string.connect_mutation),13f,true).apply { setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)) })
        popup.contentView=ScrollView(this).apply { addView(box);isVerticalScrollBarEnabled=false }
        popup.height=minOf(ui.dp(580),popup.getMaxAvailableHeight(anchor));menu=popup
        popup.showAsDropDown(anchor,0,0,Gravity.END)
    }
    private fun limitDialog() {
        sheet(R.string.history_limit) { box,dialog ->
            ui.paragraph(box,s(R.string.limit_body));box.addView(ui.text(s(R.string.saved_texts),13f,true))
            val field=EditText(this).apply {
                inputType=InputType.TYPE_CLASS_NUMBER;setText(latest.getInt("limit",DEFAULT_LIMIT).toString());selectAll()
                textSize=18f;setTextColor(ui.ink);background=ui.shape(ui.color(R.color.surface),12,ui.color(R.color.divider))
                setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));contentDescription=s(R.string.saved_texts)
                filters=arrayOf(InputFilter.LengthFilter(4))
            }
            box.addView(field,LinearLayout.LayoutParams(-1,-2));box.addView(ui.space(12));ui.paragraph(box,s(R.string.limit_warning))
            val actions=LinearLayout(this).apply { gravity=Gravity.END }
            actions.addView(ui.button(s(R.string.cancel)){dialog.dismiss()})
            actions.addView(ui.button(s(R.string.save),primary=true){
                val value=field.text.toString().toIntOrNull()
                if(value==null || value !in MIN_LIMIT..MAX_LIMIT){field.error=s(R.string.limit_invalid);return@button}
                dialog.dismiss();command({it.setLimit(value)})
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
                if(mode!=settings.appearance){settings.appearance=mode;dialog.dismiss();recreate()}
            }
            box.addView(radios);box.addView(ui.space(12));ui.paragraph(box,s(R.string.system_hint))
        }
    }
    private fun copyBehavior() {
        sheet(R.string.copy_behavior) { box,_ ->
            box.addView(Switch(this).apply {
                text=s(R.string.return_after_copy);textSize=16f;setTextColor(ui.ink);minimumHeight=ui.dp(52)
                isChecked=settings.returnAfterCopy;setOnCheckedChangeListener { _,checked -> settings.returnAfterCopy=checked;updateCopyHint() }
            })
            ui.paragraph(box,s(R.string.return_body));ui.paragraph(box,s(R.string.stay_body))
        }
    }
    private fun addTile() {
        sheet(R.string.open_faster) { box,dialog ->
            box.addView(ImageView(this).apply { setImageResource(R.drawable.ic_clipboard);imageTintList=android.content.res.ColorStateList.valueOf(ui.accent) },LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)))
            box.addView(ui.space(16));ui.paragraph(box,s(R.string.tile_body))
            box.addView(ui.button(s(R.string.add_tile),primary=true) { dialog.dismiss();requestTile() })
            box.addView(ui.button(s(R.string.not_now)){dialog.dismiss()});ui.paragraph(box,s(R.string.tile_manual))
        }
    }
    private fun requestTile() {
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(ComponentName(this,ClipboardTileService::class.java),s(R.string.tile_name),Icon.createWithResource(this,R.drawable.ic_clipboard),mainExecutor) { result ->
                if(visible)notifyUser(if(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)R.string.tile_available else R.string.tile_manual)
            }
        } catch (_:Exception){message(R.string.add_tile_menu,R.string.tile_manual)}
    }
    private fun connectionTest() {
        if(!client.connected()){message(R.string.not_connected,R.string.connect_mutation);return}
        confirm(R.string.test_title,R.string.test_body,R.string.run_test){
            val nonce="ClipHistory self-test ${UUID.randomUUID()}"
            command({it.armTest(nonce)}) {
                if(putClipboard(s(R.string.connection_test),nonce)) {
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
    private fun fillRecorder(box:LinearLayout,dialog:Dialog) {
        box.removeAllViews()
        val test=if(offline)"NOT_RUN" else latest.getString("selfTest","NOT_RUN")
        val title=when(test){"PASS"->R.string.test_passed;"WAITING"->R.string.test_waiting;"NOT_RUN"->R.string.test_not_run;else->R.string.test_failed}
        box.addView(ui.title(s(title),18f));box.addView(ui.space(12))
        pages.keyValue(box,s(R.string.clipboard_event),s(if(test=="PASS" || test=="STORAGE_FAILED")R.string.received else R.string.not_confirmed))
        pages.keyValue(box,s(R.string.storage_check),s(if(!offline && latest.getBoolean("storageVerified"))R.string.passed else R.string.not_confirmed))
        pages.keyValue(box,s(R.string.recording),s(if(!offline && latest.getBoolean("active"))R.string.active else R.string.inactive))
        if(test=="STORAGE_FAILED")ui.paragraph(box,s(R.string.test_storage_failed))
        if(test=="NO_CALLBACK_RECEIVED")ui.paragraph(box,s(R.string.test_failed_body))
        box.addView(ui.space(16));ui.paragraph(box,s(R.string.test_boundary))
        box.addView(ui.button(s(R.string.run_connection_test)){dialog.dismiss();connectionTest()}.apply { isEnabled=client.connected() && test!="WAITING" })
        box.addView(ui.button(s(R.string.view_diagnostics)){dialog.dismiss();diagnostics()})
        box.addView(ui.button(s(R.string.done)){dialog.dismiss()})
    }
}
