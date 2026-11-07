package app.cliphistory.ui

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.isDragShadowFailure

/** Keep Android's preview protection when a sensitive history entry is copied. */
object ClipboardPayload {
    /** Toolbar, hardware shortcuts and accessibility copy share the same check. */
    class CopyText(context:Context):TextView(context) {
        var copyAction:((()->Unit)->Unit)?=null
        override fun onTextContextMenuItem(id:Int):Boolean {
            if(id==android.R.id.copy && copyAction!=null) { copyAction?.invoke { clearFocus() };return true }
            return super.onTextContextMenuItem(id)
        }
        /** Android 11 throws when a long-press starts dragging a selection whose drag shadow is
         * empty (seen on a realme RMX3231 in Firebase Test Lab). Ignore that long-press instead of crashing. */
        override fun performLongClick():Boolean = try { super.performLongClick() } catch (error:IllegalStateException) {
            if(isDragShadowFailure(error))false else throw error
        }
    }
    fun text(label:String,value:String,sensitive:Boolean=false):ClipData = ClipData.newPlainText(label,value).apply {
        if(sensitive)description.extras=PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true) }
    }
    fun protectSelection(view:TextView,client:DaemonClient,id:Long,onFailure:()->Unit) {
        check(view is CopyText)
        view.copyAction={ finish ->
            val start=minOf(view.selectionStart,view.selectionEnd)
            val end=maxOf(view.selectionStart,view.selectionEnd)
            client.getText(id) { result ->
                val value=result.text
                if(view.isAttachedToWindow && view.isShown) {
                    if(value==null || client.expired(result.expiry) || start<0 || end>value.length || start==end || view.text.toString()!=value) {
                        finish();onFailure()
                    } else try {
                        view.context.getSystemService(ClipboardManager::class.java).setPrimaryClip(text("ClipHistory",value.substring(start,end),result.sensitive))
                        finish()
                    } catch (_:Exception) { onFailure() }
                }
            }
        }
        view.customSelectionActionModeCallback=object:ActionMode.Callback {
            override fun onCreateActionMode(mode:ActionMode,menu:Menu)=true
            override fun onPrepareActionMode(mode:ActionMode,menu:Menu)=false
            override fun onDestroyActionMode(mode:ActionMode) { }
            override fun onActionItemClicked(mode:ActionMode,item:MenuItem):Boolean {
                if(item.itemId!=android.R.id.copy)return false
                view.copyAction?.invoke { mode.finish() }
                return true
            }
        }
    }
}
