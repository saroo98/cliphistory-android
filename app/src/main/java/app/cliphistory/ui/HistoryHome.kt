package app.cliphistory.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.widget.*
import app.cliphistory.R
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.*

class HistoryHome(
    private val ui:Ui,
    menu:(View)->Unit,
    private val connect:()->Unit,
    private val openShizuku:()->Unit,
    private val help:()->Unit,
    private val recorder:()->Unit,
    private val resume:()->Unit,
    private val test:()->Unit,
    retry:()->Unit,
    load:()->Unit,
    diagnostics:()->Unit,
    dismissKeyboard:()->Unit,
    preview:(Long)->Unit,
    copy:(Long)->Unit,
    options:(DaemonClient.Row)->Unit
) {
    val root=ui.column()
    val search=EditText(ui.activity)
    val list=ListView(ui.activity)
    val adapter=HistoryAdapter(ui,{preview(it.id)},copy,options)
    val clear=ui.icon(R.drawable.ic_close,s(R.string.clear_search)) { search.setText("") }
    val results=ui.icon(R.drawable.ic_keyboard_down,s(R.string.show_results),dismissKeyboard)
    val more=ui.button(s(R.string.load_more),action=load)
    val hint=ui.text(s(R.string.copy_hint),13f,true)
    private val status=ui.column()
    private val count=ui.text("",13f,true)
    private val empty=ui.column()
    private val emptyIcon=ImageView(ui.activity)
    private val emptyTitle=ui.title(s(R.string.empty_title),20f)
    private val emptyBody=ui.text(s(R.string.empty_body),15f,true)
    private val progress=ProgressBar(ui.activity)
    private val retryButton=ui.button(s(R.string.try_again),action=retry)
    private val diagnosticsButton=ui.button(s(R.string.diagnostics),action=diagnostics)
    private val bar=LinearLayout(ui.activity)
    private val header=ui.column()
    private var renderedStatus:Triple<RecorderState,Boolean,Boolean>?=null
    private var keyboardShowing=false
    private var copyableRows=false
    data class Anchor(val id:Long,val position:Int,val top:Int)
    fun anchor():Anchor {
        val position=list.firstVisiblePosition
        val index=position-list.headerViewsCount
        return Anchor(if(index in 0 until adapter.count)adapter.getItemId(index) else -1L,position,list.getChildAt(0)?.top?:0)
    }
    fun restore(anchor:Anchor) {
        val index=(0 until adapter.count).firstOrNull { adapter.getItemId(it)==anchor.id }
        list.setSelectionFromTop(if(index!=null)index+list.headerViewsCount else anchor.position.coerceIn(0,adapter.count),anchor.top)
    }
    fun keyboard(showing:Boolean) {
        keyboardShowing=showing
        bar.visibility=if(showing)View.GONE else View.VISIBLE
        header.visibility=if(showing)View.GONE else View.VISIBLE
        results.visibility=if(showing)View.VISIBLE else View.GONE
        updateHint()
    }
    private fun updateHint() {
        hint.visibility=if(!copyableRows || keyboardShowing || ui.activity.resources.configuration.screenHeightDp<600 || ui.activity.resources.configuration.fontScale>1.3f)View.GONE else View.VISIBLE
    }
    private fun s(id:Int)=ui.activity.getString(id)

    init {
        root.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(8))
        root.setBackgroundColor(ui.color(R.color.canvas));root.isFocusableInTouchMode=true
        bar.apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(64) }
        bar.addView(ui.title(s(R.string.app_name),28f),LinearLayout.LayoutParams(0,-2,1f))
        val overflow=ui.icon(R.drawable.ic_more,s(R.string.more_options)) {}
        overflow.setOnClickListener { menu(overflow) };bar.addView(overflow);root.addView(bar)
        val searchBox=LinearLayout(ui.activity).apply {
            gravity=Gravity.CENTER_VERTICAL;background=ui.shape(ui.color(R.color.secondary_surface),16)
        }
        searchBox.addView(ImageView(ui.activity).apply {
            setImageResource(R.drawable.ic_search);imageTintList=ColorStateList.valueOf(ui.muted)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)).apply { marginStart=ui.dp(16);marginEnd=ui.dp(10) })
        search.apply {
            hint=s(R.string.search_hint);isSingleLine=true;textSize=16f
            isSaveEnabled=false;setTextColor(ui.ink);setHintTextColor(ui.muted);background=null
            setPadding(0,ui.dp(8),0,ui.dp(8));inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters=arrayOf(InputFilter.LengthFilter(512));minimumHeight=ui.dp(52)
        }
        searchBox.minimumHeight=ui.dp(52)
        searchBox.addView(search,LinearLayout.LayoutParams(0,-2,1f));searchBox.addView(clear);searchBox.addView(results)
        clear.visibility=View.GONE;root.addView(searchBox,LinearLayout.LayoutParams(-1,-2))
        count.setPadding(0,ui.dp(8),0,ui.dp(8))
        empty.apply {
            gravity=Gravity.CENTER;setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(36))
            addView(progress,LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)))
            emptyIcon.setImageResource(R.drawable.ic_clipboard);emptyIcon.imageTintList=ColorStateList.valueOf(ui.muted)
            addView(emptyIcon,LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)))
            addView(ui.space(20));emptyTitle.gravity=Gravity.CENTER;addView(emptyTitle)
            addView(ui.space(8));emptyBody.gravity=Gravity.CENTER;addView(emptyBody)
            addView(ui.space(12));addView(retryButton);addView(diagnosticsButton)
        }
        header.apply { setPadding(0,ui.dp(8),0,0);addView(status);addView(count) }
        list.apply {
            isSaveEnabled=false;divider=ColorDrawable(ui.color(R.color.divider));dividerHeight=ui.dp(1)
            selector=ui.ripple(Color.TRANSPARENT,8);isVerticalScrollBarEnabled=false
            addHeaderView(header,null,false);addFooterView(empty,null,false);addFooterView(more,null,false)
            adapter=this@HistoryHome.adapter
        }
        more.visibility=View.GONE
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        hint.gravity=Gravity.CENTER;hint.setPadding(0,ui.dp(12),0,ui.dp(8));root.addView(hint)
        keyboard(false);loading()
    }
    fun loading() {
        renderedStatus=null
        status.removeAllViews()
        status.addView(ui.text(s(R.string.checking),14f,true).apply { minimumHeight=ui.dp(48);gravity=Gravity.CENTER_VERTICAL })
        count.text="";empty.visibility=View.VISIBLE
        progress.visibility=View.VISIBLE;emptyIcon.visibility=View.GONE;retryButton.visibility=View.GONE;diagnosticsButton.visibility=View.GONE
        emptyTitle.text=s(R.string.loading);emptyBody.text=""
    }
    fun show(page:DaemonClient.Page,rows:List<DaemonClient.Row>,state:RecorderState,query:String) {
        val anchor=anchor()
        adapter.replace(rows,query)
        if(anchor.position>0)restore(anchor)
        val total=page.status.getInt("count");val limit=page.status.getInt("limit",DEFAULT_LIMIT)
        val counts=if(query.isEmpty())ui.activity.resources.getQuantityString(R.plurals.saved_count,total,total,limit)
            else ui.activity.resources.getQuantityString(R.plurals.match_count,page.total,page.total,total,limit)
        count.text=if(page.issue.isNotEmpty())"" else if(page.offline)ui.activity.getString(R.string.offline_count,counts) else counts
        more.visibility=if(rows.size<page.total)View.VISIBLE else View.GONE;more.isEnabled=true
        empty.visibility=if(rows.isEmpty())View.VISIBLE else View.GONE;progress.visibility=View.GONE;emptyIcon.visibility=View.VISIBLE
        val failed=page.issue.isNotEmpty()
        copyableRows=rows.isNotEmpty() && !failed;updateHint()
        emptyTitle.text=s(if(failed)R.string.history_failed else if(query.isNotEmpty())R.string.no_matches else R.string.empty_title)
        emptyBody.text=s(when {
            failed->R.string.files_preserved
            query.isNotEmpty()->R.string.try_word
            state==RecorderState.PAUSED->R.string.port_empty_paused
            page.offline->R.string.port_empty_offline
            state in listOf(RecorderState.RECORDING,RecorderState.LISTENING)->R.string.empty_body
            else->R.string.port_empty_setup
        })
        emptyIcon.setImageResource(if(failed)R.drawable.ic_warning else if(query.isNotEmpty())R.drawable.ic_search else R.drawable.ic_clipboard)
        retryButton.visibility=if(failed)View.VISIBLE else View.GONE
        diagnosticsButton.visibility=if(failed)View.VISIBLE else View.GONE
        status(state,total>0,page.status.getBoolean("explicitStop"))
    }
    private fun status(state:RecorderState,hasHistory:Boolean,stoppedByUser:Boolean) {
        val key=Triple(state,hasHistory,stoppedByUser)
        if(renderedStatus==key)return
        renderedStatus=key
        status.removeAllViews();status.setPadding(0,0,0,ui.dp(12));status.background=null
        val compact=!stoppedByUser && state in listOf(RecorderState.RECORDING,RecorderState.LISTENING,RecorderState.PAUSED)
        if(compact) {
            val row=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(48) }
            if(state==RecorderState.PAUSED)row.addView(ImageView(ui.activity).apply {
                setImageResource(R.drawable.ic_pause);imageTintList=ColorStateList.valueOf(ui.muted)
            },LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)).apply { marginEnd=ui.dp(10) })
            else row.addView(View(ui.activity).apply { background=ui.shape(ui.accent,4) },LinearLayout.LayoutParams(ui.dp(7),ui.dp(7)).apply { marginEnd=ui.dp(10) })
            val label=ui.text(s(when(state){RecorderState.RECORDING->R.string.recording;RecorderState.PAUSED->R.string.paused;else->R.string.listening}),14f)
            row.addView(label,LinearLayout.LayoutParams(0,-2,1f));row.background=ui.ripple(Color.TRANSPARENT)
            row.setOnClickListener { recorder() };row.isFocusable=true;row.contentDescription="${label.text}. ${s(R.string.recorder)}"
            if(state==RecorderState.PAUSED)row.addView(ui.button(s(R.string.resume),action=resume))
            if(state==RecorderState.LISTENING)row.addView(ui.button(s(R.string.connection_test),action=test))
            status.addView(row);return
        }
        val (title,body)=if(stoppedByUser)R.string.user_stopped_title to R.string.user_stopped_body else when(state) {
            RecorderState.MISSING->R.string.missing_title to R.string.missing_body
            RecorderState.STOPPED->if(hasHistory)R.string.unavailable_title to R.string.unavailable_body else R.string.start_recording to R.string.start_shizuku
            RecorderState.PERMISSION->R.string.permission_title to R.string.permission_body
            RecorderState.DENIED->R.string.denied_title to R.string.denied_body
            RecorderState.CONNECTING->R.string.connecting to R.string.checking_storage
            RecorderState.UNSUPPORTED->R.string.unsupported_title to R.string.unsupported_body
            else->R.string.attention_title to R.string.attention_body
        }
        if(hasHistory) {
            val row=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(48) }
            row.addView(ui.text(s(title),14f,true),LinearLayout.LayoutParams(0,-2,1f))
            val label=when {
                stoppedByUser->R.string.start_recorder
                state==RecorderState.CONNECTING->R.string.connecting
                state in listOf(RecorderState.MISSING,RecorderState.UNSUPPORTED)->R.string.setup_help
                state==RecorderState.STOPPED->R.string.open_shizuku
                state==RecorderState.PERMISSION->R.string.connect
                else->R.string.reconnect
            }
            row.addView(ui.button(s(label)) {
                when {
                    stoppedByUser->connect()
                    state in listOf(RecorderState.MISSING,RecorderState.UNSUPPORTED)->help()
                    state==RecorderState.STOPPED->openShizuku()
                    else->connect()
                }
            }.apply { isEnabled=state!=RecorderState.CONNECTING })
            status.addView(row);return
        }
        val box=ui.column().apply { background=ui.shape(ui.color(R.color.secondary_surface),16);setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8)) }
        box.addView(ui.title(s(title),16f));box.addView(ui.space(4));box.addView(ui.text(s(body),14f,true))
        val actions=LinearLayout(ui.activity)
        if(stoppedByUser)actions.addView(ui.button(s(R.string.start_recorder),action=connect)) else when(state) {
            RecorderState.CONNECTING->actions.addView(ProgressBar(ui.activity).apply { isIndeterminate=true },LinearLayout.LayoutParams(ui.dp(24),ui.dp(36)))
            RecorderState.MISSING->{ actions.addView(ui.button(s(R.string.setup_help),action=help));actions.addView(ui.button(s(R.string.try_again),action=connect)) }
            RecorderState.PERMISSION->actions.addView(ui.button(s(R.string.connect),action=connect))
            else->{actions.addView(ui.button(s(R.string.open_shizuku),action=openShizuku));actions.addView(ui.button(s(R.string.reconnect),action=connect))}
        }
        box.addView(actions);status.addView(box)
    }
}
