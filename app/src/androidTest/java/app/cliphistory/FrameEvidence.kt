package app.cliphistory

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Test-only observation of the actual QS dialog's loaded pre-draw. */
object FrameEvidence {
    private val main=Handler(Looper.getMainLooper())
    private val lock=Any()
    private var started=0L
    private var loaded=0L
    private var root:View?=null
    private val layouts=JSONArray()
    private fun contains(view:View,text:String):Boolean =
        (view is TextView && view.text.toString()==text) ||
            (view is ViewGroup && (0 until view.childCount).any { contains(view.getChildAt(it),text) })
    private val predraw=ViewTreeObserver.OnPreDrawListener {
        root?.let { view ->
            val ready=contains(view,"Synthetic newest text")
            synchronized(lock) {
                if(ready && loaded==0L)loaded=System.nanoTime()
                if(layouts.length()<16)layouts.put(JSONObject().apply {
                    put("width",view.width);put("height",view.height);put("ready",ready)
                })
            }
        }
        true
    }
    private val watch=object:Choreographer.FrameCallback {
        override fun doFrame(time:Long) {
            val window=WindowInspector.getGlobalWindowViews().firstOrNull { it.isAttachedToWindow && contains(it,"Quick copy") }
            if(window!=null) {
                root=window;window.viewTreeObserver.addOnPreDrawListener(predraw)
            } else if(System.nanoTime()-started<2_000_000_000L)Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private fun stop() {
        Choreographer.getInstance().removeFrameCallback(watch)
        root?.let { if(it.viewTreeObserver.isAlive)it.viewTreeObserver.removeOnPreDrawListener(predraw) }
        root=null
    }
    fun begin() {
        synchronized(lock) {
            started=System.nanoTime();loaded=0L
            while(layouts.length()>0)layouts.remove(layouts.length()-1)
        }
        main.post { stop();Choreographer.getInstance().postFrameCallback(watch) }
    }
    fun snapshot():JSONObject {
        main.post { stop() }
        return synchronized(lock) { JSONObject().apply {
            put("tap_ns",started);put("loaded_predraw_ns",loaded)
            put("loading_ms",if(loaded>started && started>0)(loaded-started)/1_000_000.0 else -1.0)
            put("layouts",JSONArray(layouts.toString()))
            put("scope","First observed loaded app pre-draw; excludes SystemUI/accessibility delay and compositor presentation")
        } }
    }
}
