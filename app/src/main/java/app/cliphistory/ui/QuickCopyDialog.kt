package app.cliphistory.ui

import android.app.Dialog
import android.app.KeyguardManager
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.*
import app.cliphistory.R
import app.cliphistory.client.DaemonClient

/** A native QS dialog: no activity/task launch or overlay permission. */
class QuickCopyDialog(base:Context,private val openHistory:()->Unit):Dialog(
    ContextThemeWrapper(base,R.style.QuickCopyTheme).apply {
        val appearance=AppSettings(base).appearance
        if(appearance!="system")applyOverrideConfiguration(Configuration().apply {
            uiMode=if(appearance=="dark")Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
    }
) {
    private val client get()=(context.applicationContext as app.cliphistory.ClipApplication).daemon
    private lateinit var settings:AppSettings
    private val main=Handler(Looper.getMainLooper())
    private lateinit var rows:LinearLayout
    private lateinit var notice:TextView
    private var token=0L
    private var closed=false
    private var loading=false
    private var dirty=false
    private var copying=false
    private var copyFailure=false
    private var populatedHeight=0
    private var nextNotice:Int?=null
    private val reload=Runnable { refresh() }
    private val changed:()->Unit={
        if(loading || copyFailure)dirty=true else { main.removeCallbacks(reload);main.postDelayed(reload,120) }
    }
    private val preferences=SharedPreferences.OnSharedPreferenceChangeListener { _,_ ->
        if(closed)return@OnSharedPreferenceChangeListener
        if(!settings.tileEnabled)dismiss() else ScreenPrivacy.apply(window,settings)
    }
    private val locked=object:BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent){dismiss()} }
    private var observing=false
    private fun trace(event:String) { if(app.cliphistory.BuildConfig.DEBUG)android.util.Log.d("ClipHistoryTileTrace","${android.os.SystemClock.uptimeMillis()} dialog $event") }
    private fun unlocked()=!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun openApp() {
        // Launch while SystemUI's dialog token is still granted.
        openHistory();dismiss()
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);settings=AppSettings(context)
        ScreenPrivacy.apply(window,settings)
        rows=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        // Two text lines can be slightly taller than 64dp even at the default
        // font scale. Reserve measured row space before the asynchronous read.
        val sample=entryButton("\n")
        sample.measure(View.MeasureSpec.makeMeasureSpec(dp(320),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        populatedHeight=3*(sample.measuredHeight+dp(8));rows.minimumHeight=populatedHeight
        notice=label(context.getString(R.string.quick_loading),14f,true)
        val panelWidth=minOf(dp(360),context.resources.displayMetrics.widthPixels-dp(32))-dp(40)
        notice.minimumHeight=listOf(R.string.quick_loading,R.string.quick_hint,R.string.quick_offline).maxOf { id ->
            label(context.getString(id),14f,true).apply {
                measure(View.MeasureSpec.makeMeasureSpec(panelWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            }.measuredHeight
        }
        showSkeleton(sample.measuredHeight)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        val box=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(20))
            background=shape(context.getColor(R.color.surface),24);accessibilityPaneTitle=context.getString(R.string.quick_copy)
        }
        val heading=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        heading.addView(label(context.getString(R.string.quick_copy),20f).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);isAccessibilityHeading=true
        },LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_close);imageTintList=ColorStateList.valueOf(context.getColor(R.color.ink))
            contentDescription=context.getString(R.string.quick_close);setPadding(dp(12),dp(12),dp(12),dp(12))
            background=RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.selected)),shape(android.graphics.Color.TRANSPARENT,24),null)
            setOnClickListener { dismiss() }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        box.addView(heading);box.addView(notice);box.addView(rows)
        val scroll=object:ScrollView(context) {
            override fun onMeasure(w:Int,h:Int) {
                val max=(context.resources.displayMetrics.heightPixels*.75).toInt()
                super.onMeasure(w,MeasureSpec.makeMeasureSpec(max,MeasureSpec.AT_MOST))
            }
        }.apply { addView(box);isVerticalScrollBarEnabled=false }
        setContentView(scroll);setCanceledOnTouchOutside(true)
        window!!.apply {
            setLayout(minOf(dp(360),context.resources.displayMetrics.widthPixels-dp(32)),-2)
            // Decor installation can restore the theme's animation; override it afterward.
            setWindowAnimations(0)
        }
    }
    private fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun shape(color:Int,radius:Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(radius).toFloat() }
    private fun label(text:String,size:Float,secondary:Boolean=false)=TextView(context).apply {
        this.text=text;textSize=size;setTextColor(context.getColor(if(secondary)R.color.muted else R.color.ink))
        setPadding(0,dp(6),0,dp(6));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private fun entryButton(preview:String)=Button(context).apply {
        text=preview;isAllCaps=false;textSize=16f;gravity=Gravity.START or Gravity.CENTER_VERTICAL
        maxLines=2;ellipsize=TextUtils.TruncateAt.END;minimumHeight=dp(64);stateListAnimator=null
        setTextColor(context.getColor(R.color.ink));setPadding(dp(12),dp(12),dp(12),dp(12))
        background=RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.selected)),shape(context.getColor(R.color.secondary_surface),14),null)
    }
    private fun showSkeleton(rowHeight:Int=populatedHeight/3-dp(8)) {
        rows.removeAllViews();rows.minimumHeight=populatedHeight
        repeat(3) {
            rows.addView(FrameLayout(context).apply {
                tag="quick-skeleton"
                background=shape(context.getColor(R.color.secondary_surface),14)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(View(context).apply { background=shape(context.getColor(R.color.divider),3) },
                    FrameLayout.LayoutParams(dp(180),dp(10),Gravity.CENTER_VERTICAL).apply { marginStart=dp(16) })
            },LinearLayout.LayoutParams(-1,rowHeight).apply { topMargin=dp(8) })
        }
    }
    private fun retryRead() {
        copyFailure=false;notice.text=context.getString(R.string.quick_loading);showSkeleton();refresh()
    }
    override fun onAttachedToWindow() {
        trace("start")
        super.onAttachedToWindow();closed=false
        ScreenPrivacy.apply(window,settings)
        client.addListener(changed);settings.register(preferences)
        context.applicationContext.registerReceiver(locked,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
        observing=true;main.post(reload)
    }
    private fun stopObserving() {
        closed=true;token++;main.removeCallbacksAndMessages(null)
        if(observing) {
            client.removeListener(changed);settings.unregister(preferences)
            try { context.applicationContext.unregisterReceiver(locked) } catch (_:Exception) { }
            observing=false
        }
    }
    override fun onDetachedFromWindow() { stopObserving();super.onDetachedFromWindow() }
    override fun onStop() { stopObserving();super.onStop() }
    override fun dismiss() {
        try { super.dismiss() } catch(error:IllegalArgumentException) {
            // SystemUI may have removed its QS token/window before the service
            // cleanup. Dialog still completes onStop in its dismissal finally.
            if(window?.decorView?.isAttachedToWindow==true)throw error
        }
    }
    fun refresh() {
        if(closed || !isShowing || copying || copyFailure)return
        if(!settings.tileEnabled || !unlocked()){dismiss();return}
        if(loading){dirty=true;return}
        val request=++token;loading=true;dirty=false
        trace("read requested")
        client.requestLatest { page ->
            trace("read returned rows=${page.rows.size} error=${page.issue.isNotEmpty()}")
            if(closed || request!=token || !isShowing)return@requestLatest
            loading=false
            if(!unlocked()){dismiss();return@requestLatest}
            rows.removeAllViews()
            rows.minimumHeight=if(page.issue.isEmpty() && page.rows.isNotEmpty())populatedHeight else 0
            if(page.issue.isNotEmpty()) {
                notice.text=context.getString(R.string.quick_failed)
                action(R.string.quick_retry){retryRead()};action(R.string.open_app){openApp()}
            } else {
                notice.text=context.getString(nextNotice ?: if(page.rows.isEmpty())R.string.quick_empty else if(page.offline)R.string.quick_offline else R.string.quick_hint)
                nextNotice=null
                if(page.rows.isEmpty())action(R.string.open_app){openApp()}
                page.rows.forEach { row ->
                    val button=entryButton(row.preview).apply {
                        setOnClickListener { copy(row.id) }
                    }
                    rows.addView(button,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
                }
            }
            if(dirty){dirty=false;main.post(reload)}
        }
    }
    private fun action(id:Int,click:()->Unit) { rows.addView(entryButton(context.getString(id)).apply {
        maxLines=3;minimumHeight=dp(48);setOnClickListener { click() }
    },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) }) }
    private fun copy(id:Long) {
        if(copying || closed || !unlocked())return
        copying=true;loading=false;val request=++token
        client.getText(id) { result ->
            if(closed || request!=token || !isShowing)return@getText
            copying=false
            if(!unlocked()){dismiss();return@getText}
            if(result.issue.isNotEmpty()) {
                notice.text=context.getString(R.string.quick_failed);rows.removeAllViews()
                rows.minimumHeight=0
                action(R.string.quick_retry){retryRead()};action(R.string.open_app){openApp()};return@getText
            }
            val text=result.text
            if(text==null){nextNotice=R.string.quick_missing;refresh();return@getText}
            writeCopy(text)
        }
    }
    private fun writeCopy(text:String) {
        if(closed || !isShowing || !unlocked())return
        try {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name),text));dismiss()
        } catch (_:Exception) { showCopyFailure(text) }
    }
    private fun showCopyFailure(text:String) {
        copyFailure=true;main.removeCallbacks(reload)
        notice.text=context.getString(R.string.copy_failed_title)
        rows.removeAllViews();rows.minimumHeight=0
        action(R.string.port_retry){writeCopy(text)}
        action(R.string.view_full) {
            rows.removeAllViews()
            rows.addView(label(text,16f).apply { setTextIsSelectable(true);isSaveEnabled=false })
            action(R.string.port_retry){writeCopy(text)}
            action(R.string.back){showCopyFailure(text)}
        }
        action(R.string.cancel){copyFailure=false;refresh()}
    }
}
