package app.cliphistory.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.*
import android.widget.*
import app.cliphistory.R

private class SheetHandle(context:Context):FrameLayout(context) {
    override fun performClick():Boolean { super.performClick();return true }
}

/** Small native View vocabulary shared by history, sheets and detail pages. */
class Ui(val activity: Activity) {
    fun dp(v:Int)=(v*activity.resources.displayMetrics.density).toInt()
    fun color(id:Int)=activity.getColor(id)
    val ink get()=color(R.color.ink)
    val muted get()=color(R.color.muted)
    val accent get()=color(R.color.accent)
    fun shape(fill:Int,radius:Int=0,stroke:Int?=null)=GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat()
        stroke?.let { setStroke(dp(1),it) }
    }
    fun ripple(fill:Int,radius:Int=14)=RippleDrawable(ColorStateList.valueOf(color(R.color.selected)),shape(fill,radius),null)
    fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    fun text(value:CharSequence,size:Float=16f,secondary:Boolean=false)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(if(secondary)muted else ink)
        setLineSpacing(dp(3).toFloat(),1f)
    }
    fun title(value:CharSequence,size:Float=22f)=text(value,size).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    fun button(value:String,primary:Boolean=false,danger:Boolean=false,action:()->Unit)=Button(activity).apply {
        text=value;isAllCaps=false;textSize=14f;minHeight=dp(48);minimumHeight=dp(48);minWidth=0;minimumWidth=0
        setPadding(dp(16),dp(8),dp(16),dp(8));stateListAnimator=null
        val foreground=if(primary)color(R.color.on_accent) else if(danger)color(R.color.danger) else accent
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(muted,foreground)))
        background=ripple(if(primary)if(danger)color(R.color.danger) else accent else android.graphics.Color.TRANSPARENT)
        setOnClickListener { action() }
    }
    fun icon(drawable:Int,label:String,action:()->Unit)=ImageButton(activity).apply {
        setImageResource(drawable);imageTintList=ColorStateList.valueOf(ink);contentDescription=label
        background=ripple(android.graphics.Color.TRANSPARENT,24);setPadding(dp(12),dp(12),dp(12),dp(12))
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));setOnClickListener { action() }
    }
    fun divider()=View(activity).apply { setBackgroundColor(color(R.color.divider));layoutParams=LinearLayout.LayoutParams(-1,dp(1)) }
    fun space(height:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(height)) }
    fun paragraph(parent:LinearLayout,value:CharSequence) { parent.addView(text(value,15f,true));parent.addView(space(16)) }
    fun actionRow(parent:LinearLayout,value:String,icon:Int?=null,enabled:Boolean=true,danger:Boolean=false,action:()->Unit) {
        val row=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(52);setPadding(dp(12),dp(4),dp(12),dp(4))
            background=ripple(android.graphics.Color.TRANSPARENT,12);isEnabled=enabled
            isFocusable=enabled;isClickable=enabled;contentDescription=value
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityDelegate=object:View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host:View,info:android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host,info);info.className="android.widget.Button"
                }
            }
            if(enabled)setOnClickListener { action() }
        }
        icon?.let { res -> row.addView(ImageView(activity).apply {
            setImageResource(res);imageTintList=ColorStateList.valueOf(if(!enabled)muted else if(danger)color(R.color.danger) else ink)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(22),dp(22)).apply { marginEnd=dp(16) }) }
        row.addView(text(value).apply {
            setTextColor(if(!enabled)muted else if(danger)color(R.color.danger) else ink)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(-1,-2))
        parent.addView(row)
    }
    fun sheet(title:String,content:(LinearLayout,Dialog)->Unit):Dialog {
        val dialog=Dialog(activity)
        dialog.window?.apply { addFlags(WindowManager.LayoutParams.FLAG_SECURE);setBackgroundDrawableResource(android.R.color.transparent) }
        val box=column().apply { setPadding(dp(20),dp(12),dp(20),dp(20));background=shape(color(R.color.surface),24) }
        val handle=SheetHandle(activity).apply { minimumHeight=dp(48);contentDescription=activity.getString(R.string.dismiss_panel);isClickable=true;isFocusable=true }
        handle.addView(View(activity).apply { background=shape(color(R.color.divider),2) },FrameLayout.LayoutParams(dp(32),dp(4),Gravity.CENTER))
        handle.setOnClickListener { dialog.dismiss() }
        var downY=0f
        handle.setOnTouchListener { _,event ->
            val decor=dialog.window?.decorView
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{downY=event.rawY;true}
                MotionEvent.ACTION_MOVE->{decor?.translationY=(event.rawY-downY).coerceAtLeast(0f);true}
                MotionEvent.ACTION_UP->{
                    if(event.rawY-downY>dp(72))dialog.dismiss()
                    else if(event.rawY-downY<android.view.ViewConfiguration.get(activity).scaledTouchSlop)handle.performClick()
                    else if(android.animation.ValueAnimator.areAnimatorsEnabled())decor?.animate()?.translationY(0f)?.setDuration(120)?.start()
                    else decor?.translationY=0f
                    true
                }
                MotionEvent.ACTION_CANCEL->{decor?.translationY=0f;true}
                else->false
            }
        }
        box.addView(handle,LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(title(title));box.addView(space(20));content(box,dialog)
        val scroll=object:ScrollView(activity) {
            override fun onMeasure(w:Int,h:Int) {
                val screenLimit=(activity.windowManager.currentWindowMetrics.bounds.height()*.82).toInt()
                val available=if(MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED)screenLimit else minOf(screenLimit,MeasureSpec.getSize(h))
                super.onMeasure(w,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport=false;addView(box);isVerticalScrollBarEnabled=false }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM);setLayout(-1,-2)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setWindowAnimations(R.style.SheetMotion)
        }
        dialog.show();dialog.window?.setLayout(minOf(dp(600),activity.windowManager.currentWindowMetrics.bounds.width()),-2)
        return dialog
    }
}
