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
    diagnostics:()->Unit
) {
    val root=ui.column()
    val search=EditText(ui.activity)
    val list=ListView(ui.activity)
    val adapter=HistoryAdapter(ui.activity)
    val clear=ui.icon(R.drawable.ic_close,s(R.string.clear_search)) { search.setText("") }
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
    private fun s(id:Int)=ui.activity.getString(id)

    init {
        root.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(8))
        root.setBackgroundColor(ui.color(R.color.canvas));root.isFocusableInTouchMode=true
        val bar=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(64) }
        bar.addView(ui.title(s(R.string.app_name),28f),LinearLayout.LayoutParams(0,-2,1f))
        val overflow=ui.icon(R.drawable.ic_more,s(R.string.more_options)) {}
        overflow.setOnClickListener { menu(overflow) };bar.addView(overflow);root.addView(bar)
        root.addView(status)
        val searchBox=LinearLayout(ui.activity).apply {
            gravity=Gravity.CENTER_VERTICAL;background=ui.shape(ui.color(R.color.secondary_surface),16)
        }
        searchBox.addView(ImageView(ui.activity).apply {
            setImageResource(R.drawable.ic_search);imageTintList=ColorStateList.valueOf(ui.muted)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)).apply { marginStart=ui.dp(16);marginEnd=ui.dp(10) })
        search.apply {
            hint=s(R.string.search_hint);contentDescription=s(R.string.search_hint);isSingleLine=true;textSize=16f
            isSaveEnabled=false;setTextColor(ui.ink);setHintTextColor(ui.muted);background=null
            setPadding(0,0,0,0);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters=arrayOf(InputFilter.LengthFilter(512));minimumHeight=ui.dp(52)
        }
        searchBox.addView(search,LinearLayout.LayoutParams(0,-1,1f));searchBox.addView(clear)
        clear.visibility=View.GONE;root.addView(searchBox,LinearLayout.LayoutParams(-1,ui.dp(52)))
        count.setPadding(0,ui.dp(16),0,ui.dp(8));root.addView(count)
        list.apply {
            isSaveEnabled=false;divider=ColorDrawable(ui.color(R.color.divider));dividerHeight=ui.dp(1)
            selector=ui.ripple(Color.TRANSPARENT,8);isVerticalScrollBarEnabled=false
            addFooterView(more,null,false);adapter=this@HistoryHome.adapter
        }
        more.visibility=View.GONE
        empty.apply {
            gravity=Gravity.CENTER;setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(36))
            addView(progress,LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)))
            emptyIcon.setImageResource(R.drawable.ic_clipboard);emptyIcon.imageTintList=ColorStateList.valueOf(ui.muted)
            addView(emptyIcon,LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)))
            addView(ui.space(20));emptyTitle.gravity=Gravity.CENTER;addView(emptyTitle)
            addView(ui.space(8));emptyBody.gravity=Gravity.CENTER;addView(emptyBody)
            addView(ui.space(12));addView(retryButton);addView(diagnosticsButton)
        }
        val frame=FrameLayout(ui.activity)
        frame.addView(list,FrameLayout.LayoutParams(-1,-1));frame.addView(empty,FrameLayout.LayoutParams(-1,-1))
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        hint.gravity=Gravity.CENTER;hint.setPadding(0,ui.dp(12),0,ui.dp(8));root.addView(hint)
        loading()
    }
    fun loading() {
        status.removeAllViews()
        status.addView(ui.text(s(R.string.checking),14f,true).apply { minimumHeight=ui.dp(48);gravity=Gravity.CENTER_VERTICAL })
        count.text="";list.visibility=View.GONE;empty.visibility=View.VISIBLE
        progress.visibility=View.VISIBLE;emptyIcon.visibility=View.GONE;retryButton.visibility=View.GONE;diagnosticsButton.visibility=View.GONE
        emptyTitle.text=s(R.string.loading);emptyBody.text=""
    }
    fun show(page:DaemonClient.Page,rows:List<DaemonClient.Row>,state:RecorderState,query:String) {
        val anchor=list.firstVisiblePosition
        val anchorId=if(anchor<adapter.count)adapter.getItemId(anchor) else -1L
        val top=list.getChildAt(0)?.top?:0
        adapter.replace(rows,query)
        if(anchor>0)rows.indexOfFirst { it.id==anchorId }.takeIf { it>=0 }?.let { list.setSelectionFromTop(it,top) }
        val total=page.status.getInt("count");val limit=page.status.getInt("limit",DEFAULT_LIMIT)
        val counts=if(query.isEmpty())ui.activity.getString(R.string.saved_count,total,limit)
            else ui.activity.getString(R.string.match_count,page.total,total,limit)
        count.text=if(page.issue.isNotEmpty())"" else if(page.offline)ui.activity.getString(R.string.offline_count,counts) else counts
        more.visibility=if(rows.size<page.total)View.VISIBLE else View.GONE;more.isEnabled=true
        list.visibility=if(rows.isEmpty())View.GONE else View.VISIBLE
        empty.visibility=if(rows.isEmpty())View.VISIBLE else View.GONE;progress.visibility=View.GONE;emptyIcon.visibility=View.VISIBLE
        val failed=page.issue.isNotEmpty()
        emptyTitle.text=s(if(failed)R.string.history_failed else if(query.isNotEmpty())R.string.no_matches else R.string.empty_title)
        emptyBody.text=s(if(failed)R.string.files_preserved else if(query.isNotEmpty())R.string.try_word else R.string.empty_body)
        emptyIcon.setImageResource(if(failed)R.drawable.ic_warning else if(query.isNotEmpty())R.drawable.ic_search else R.drawable.ic_clipboard)
        retryButton.visibility=if(failed)View.VISIBLE else View.GONE
        diagnosticsButton.visibility=if(failed)View.VISIBLE else View.GONE
        status(state,total>0)
    }
    private fun status(state:RecorderState,hasHistory:Boolean) {
        status.removeAllViews();status.setPadding(0,0,0,ui.dp(12));status.background=null
        val compact=state in listOf(RecorderState.RECORDING,RecorderState.LISTENING,RecorderState.PAUSED)
        if(compact) {
            val row=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(48) }
            if(state==RecorderState.PAUSED)row.addView(ImageView(ui.activity).apply {
                setImageResource(R.drawable.ic_pause);imageTintList=ColorStateList.valueOf(ui.muted)
            },LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)).apply { marginEnd=ui.dp(10) })
            else row.addView(View(ui.activity).apply { background=ui.shape(ui.accent,4) },LinearLayout.LayoutParams(ui.dp(7),ui.dp(7)).apply { marginEnd=ui.dp(10) })
            val label=ui.text(s(when(state){RecorderState.RECORDING->R.string.recording;RecorderState.PAUSED->R.string.paused;else->R.string.listening}),14f)
            row.addView(label,LinearLayout.LayoutParams(0,-2,1f));row.background=ui.ripple(Color.TRANSPARENT)
            row.setOnClickListener { recorder() };row.isFocusable=true;row.contentDescription=s(R.string.recorder)
            if(state==RecorderState.PAUSED)row.addView(ui.button(s(R.string.resume),action=resume))
            if(state==RecorderState.LISTENING)row.addView(ui.button(s(R.string.connection_test),action=test))
            status.addView(row);return
        }
        val (title,body)=when(state) {
            RecorderState.MISSING->R.string.missing_title to R.string.missing_body
            RecorderState.STOPPED->if(hasHistory)R.string.unavailable_title to R.string.unavailable_body else R.string.start_recording to R.string.start_shizuku
            RecorderState.PERMISSION->R.string.permission_title to R.string.permission_body
            RecorderState.DENIED->R.string.denied_title to R.string.denied_body
            RecorderState.CONNECTING->R.string.connecting to R.string.checking_storage
            RecorderState.UNSUPPORTED->R.string.unsupported_title to R.string.unsupported_body
            else->R.string.attention_title to R.string.attention_body
        }
        val box=ui.column().apply { background=ui.shape(ui.color(R.color.secondary_surface),16);setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8)) }
        box.addView(ui.title(s(title),16f));box.addView(ui.space(4));box.addView(ui.text(s(body),14f,true))
        val actions=LinearLayout(ui.activity)
        when(state) {
            RecorderState.CONNECTING->actions.addView(ProgressBar(ui.activity).apply { isIndeterminate=true },LinearLayout.LayoutParams(ui.dp(24),ui.dp(36)))
            RecorderState.MISSING->{ actions.addView(ui.button(s(R.string.setup_help),action=help));actions.addView(ui.button(s(R.string.try_again),action=connect)) }
            RecorderState.PERMISSION->actions.addView(ui.button(s(R.string.connect),action=connect))
            else->{actions.addView(ui.button(s(R.string.open_shizuku),action=openShizuku));actions.addView(ui.button(s(R.string.reconnect),action=connect))}
        }
        box.addView(actions);status.addView(box)
    }
}
