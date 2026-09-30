package app.cliphistory.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.*
import app.cliphistory.client.DaemonClient
import app.cliphistory.R

class HistoryAdapter(private val context:android.content.Context):BaseAdapter() {
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
            orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(16),dp(4),dp(16))
            addView(TextView(context).apply { textSize=16f;setTextColor(context.getColor(R.color.ink));maxLines=3;ellipsize=TextUtils.TruncateAt.END;setLineSpacing(dp(3).toFloat(),1f) })
            addView(TextView(context).apply { textSize=13f;setTextColor(context.getColor(R.color.muted));setPadding(0,dp(8),0,0) })
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
        (layout.getChildAt(0) as TextView).text=preview
        (layout.getChildAt(1) as TextView).text=DateUtils.getRelativeTimeSpanString(row.time,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS)
        return layout
    }
}
