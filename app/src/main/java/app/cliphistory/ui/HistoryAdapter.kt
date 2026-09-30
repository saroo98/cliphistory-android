package app.cliphistory.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import app.cliphistory.client.DaemonClient
import app.cliphistory.R

class HistoryAdapter(private val ui:Ui,private val preview:(DaemonClient.Row)->Unit):BaseAdapter() {
    private val context get()=ui.activity
    private var rows:List<DaemonClient.Row> = emptyList()
    private var query=""
    fun replace(value:List<DaemonClient.Row>,search:String="") { rows=value;query=search;notifyDataSetChanged() }
    override fun getCount()=rows.size
    override fun getItem(position:Int)=rows[position]
    override fun getItemId(position:Int)=rows[position].id
    override fun hasStableIds()=true
    private fun dp(value:Int)=(context.resources.displayMetrics.density*value).toInt()
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
        val layout=(convertView as? LinearLayout)?:LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(16),0,dp(16))
            val body=ui.column().apply {
                addView(ui.text("",16f).apply { maxLines=3;ellipsize=TextUtils.TruncateAt.END })
                addView(ui.text("",13f,true).apply { setPadding(0,dp(8),0,0) })
            }
            addView(body,LinearLayout.LayoutParams(0,-2,1f))
            addView(ui.icon(R.drawable.ic_eye,context.getString(R.string.view_full)) {}.apply { isFocusable=false })
        }
        val row=getItem(position)
        val preview=SpannableString(row.preview)
        if(query.isNotEmpty()) {
            var start=row.preview.indexOf(query,ignoreCase=true)
            while(start>=0) {
                preview.setSpan(BackgroundColorSpan(context.getColor(R.color.selected)),start,start+query.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                start=row.preview.indexOf(query,start+query.length,ignoreCase=true)
            }
        }
        val body=layout.getChildAt(0) as LinearLayout
        (body.getChildAt(0) as TextView).text=preview
        val now=System.currentTimeMillis()
        (body.getChildAt(1) as TextView).text=if(now-row.time in 0 until DateUtils.MINUTE_IN_MILLIS)context.getString(R.string.just_now)
            else DateUtils.getRelativeTimeSpanString(row.time,now,DateUtils.MINUTE_IN_MILLIS)
        layout.getChildAt(1).setOnClickListener { this.preview(row) }
        layout.accessibilityDelegate=object:View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host:View,info:AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.action_view_text,context.getString(R.string.view_full)))
            }
            override fun performAccessibilityAction(host:View,action:Int,args:android.os.Bundle?):Boolean {
                if(action==R.id.action_view_text){this@HistoryAdapter.preview(row);return true}
                return super.performAccessibilityAction(host,action,args)
            }
        }
        return layout
    }
}
