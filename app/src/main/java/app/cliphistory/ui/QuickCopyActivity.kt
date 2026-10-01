package app.cliphistory.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.content.res.ColorStateList
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

/** A visible floating Activity, independent of transient tile service bindings. */
class QuickCopyActivity:Activity() {
    private val client get()=(application as app.cliphistory.ClipApplication).daemon
    private val context:Context get()=this
    private lateinit var settings:AppSettings
    private val main=Handler(Looper.getMainLooper())
    private lateinit var rows:LinearLayout
    private lateinit var notice:TextView
    private var token=0L
    private var closed=false
    private var loading=false
    private var dirty=false
    private var copying=false
    private var populatedHeight=0
    private var nextNotice:Int?=null
    private val reload=Runnable { refresh() }
    private val changed:()->Unit={
        if(loading)dirty=true else { main.removeCallbacks(reload);main.postDelayed(reload,120) }
    }
    private val preferences=SharedPreferences.OnSharedPreferenceChangeListener { _,_ ->
        if(!settings.tileEnabled)dismiss() else ScreenPrivacy.apply(this,settings)
    }
    private val locked=object:BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent){dismiss()} }
    private var observing=false
    private fun trace(event:String) { if(app.cliphistory.BuildConfig.DEBUG)android.util.Log.d("ClipHistoryTileTrace","${android.os.SystemClock.uptimeMillis()} dialog $event") }
    private fun unlocked()=!getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun dismiss()=finish()
    private fun openApp() {
        startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP));finish()
    }
    override fun attachBaseContext(base:Context) { super.attachBaseContext(AppSettings.themedContext(base)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);settings=AppSettings(this)
        ScreenPrivacy.apply(this,settings)
        if(!settings.tileEnabled || !unlocked()){finish();return}
        rows=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        // Two text lines can be slightly taller than 64dp even at the default
        // font scale. Reserve measured row space before the asynchronous read.
        val sample=entryButton("\n")
        sample.measure(View.MeasureSpec.makeMeasureSpec(dp(320),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        populatedHeight=3*(sample.measuredHeight+dp(8));rows.minimumHeight=populatedHeight
        notice=label(getString(R.string.quick_loading),14f,true)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        // QS already animates its own collapse. Keep one stable, immediate panel
        // instead of stacking a dialog animation and a loading-height transition.
        window?.setWindowAnimations(0)
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
        setContentView(scroll);setFinishOnTouchOutside(true)
        window.setLayout(minOf(dp(360),resources.displayMetrics.widthPixels-dp(32)),-2)
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
    override fun onStart() {
        trace("start")
        super.onStart();closed=false
        if(!settings.tileEnabled || !unlocked()){dismiss();return}
        client.addListener(changed);settings.register(preferences)
        registerReceiver(locked,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
        observing=true;main.post(reload)
    }
    override fun onStop() {
        closed=true;token++;main.removeCallbacksAndMessages(null)
        if(observing) {
            client.removeListener(changed);settings.unregister(preferences)
            try { unregisterReceiver(locked) } catch (_:Exception) { }
            observing=false
        }
        super.onStop()
        if(!isChangingConfigurations)finish()
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);refresh() }
    override fun onResume() {
        super.onResume();ScreenPrivacy.apply(this,settings)
        if(!settings.tileEnabled || !unlocked())finish()
    }
    fun refresh() {
        if(closed || isFinishing || copying)return
        if(!unlocked()){dismiss();return}
        if(loading){dirty=true;return}
        val request=++token;loading=true;dirty=false
        trace("read requested")
        client.requestLatest { page ->
            trace("read returned rows=${page.rows.size} error=${page.issue.isNotEmpty()}")
            if(closed || request!=token || isFinishing)return@requestLatest
            loading=false
            if(!unlocked()){dismiss();return@requestLatest}
            rows.removeAllViews()
            rows.minimumHeight=if(page.issue.isEmpty() && page.rows.isNotEmpty())populatedHeight else 0
            if(page.issue.isNotEmpty()) {
                notice.text=context.getString(R.string.quick_failed)
                action(R.string.quick_retry){refresh()};action(R.string.open_app){dismiss();openApp()}
            } else {
                notice.text=context.getString(nextNotice ?: if(page.rows.isEmpty())R.string.quick_empty else if(page.offline)R.string.quick_offline else R.string.quick_hint)
                nextNotice=null
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
    },LinearLayout.LayoutParams(-1,-2)) }
    private fun copy(id:Long) {
        if(copying || closed || !unlocked())return
        copying=true;loading=false;val request=++token
        client.getText(id) { result ->
            if(closed || request!=token || isFinishing)return@getText
            copying=false
            if(!unlocked()){dismiss();return@getText}
            if(result.issue.isNotEmpty()) {
                notice.text=context.getString(R.string.quick_failed);rows.removeAllViews()
                rows.minimumHeight=0
                action(R.string.quick_retry){refresh()};action(R.string.open_app){dismiss();openApp()};return@getText
            }
            val text=result.text
            if(text==null){nextNotice=R.string.quick_missing;refresh();return@getText}
            try {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name),text))
                dismiss()
            } catch (_:Exception) { notice.text=context.getString(R.string.quick_copy_failed) }
        }
    }
}
