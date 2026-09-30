package app.cliphistory

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Instrumentation-only frame observation. No listener or test API ships in the app. */
object FrameEvidence {
    private val thread=HandlerThread("validation-frames").apply { start() }
    private val handler=Handler(thread.looper)
    private val lock=Any()
    private var started=0L
    private var loaded=0L
    private val frames=JSONArray()
    private val layouts=JSONArray()
    fun begin()=synchronized(lock) {
        started=System.nanoTime();loaded=0L
        while(frames.length()>0)frames.remove(frames.length()-1)
        while(layouts.length()>0)layouts.remove(layouts.length()-1)
    }
    fun observe(activity:Activity) {
        val decor=activity.window.decorView
        fun ready(view:View):Boolean {
            if(view is TextView && view.text.toString()=="Synthetic newest text")return true
            return view is ViewGroup && (0 until view.childCount).any { ready(view.getChildAt(it)) }
        }
        val listener=object:ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                val contentReady=ready(decor)
                synchronized(lock) {
                    if(layouts.length()<16)layouts.put(JSONObject().apply {
                        put("ready",contentReady);put("width",decor.width);put("height",decor.height)
                        if(layouts.length()<2) {
                            val textLayouts=JSONArray()
                            fun measure(view:View) {
                                if(view is TextView)textLayouts.put(JSONObject().apply {
                                    put("type",view.javaClass.simpleName);put("sp_px",view.textSize)
                                    put("height",view.height);put("lines",view.lineCount)
                                })
                                if(view is ViewGroup)(0 until view.childCount).forEach { measure(view.getChildAt(it)) }
                            }
                            measure(decor);put("text_layouts",textLayouts)
                        }
                    })
                }
                if(contentReady) {
                    synchronized(lock) { if(loaded==0L)loaded=System.nanoTime() }
                }
                return true
            }
        }
        decor.viewTreeObserver.addOnPreDrawListener(listener)
        activity.window.addOnFrameMetricsAvailableListener({_,metrics,dropped ->
            synchronized(lock) {
                frames.put(JSONObject().apply {
                    put("vsync_ns",metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                    put("total_ns",metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                    put("draw_ns",metrics.getMetric(FrameMetrics.DRAW_DURATION))
                    put("layout_ns",metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                    put("animation_ns",metrics.getMetric(FrameMetrics.ANIMATION_DURATION))
                    put("deadline_ns",metrics.getMetric(FrameMetrics.DEADLINE))
                    put("first_draw",metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==1L)
                    put("dropped_reports",dropped)
                })
            }
        },handler)
    }
    fun snapshot():JSONObject=synchronized(lock) { JSONObject().apply {
        put("tap_ns",started);put("loaded_predraw_ns",loaded)
        put("loading_ms",if(loaded>started && started>0)(loaded-started)/1_000_000.0 else -1.0)
        put("frames",JSONArray(frames.toString()))
        put("layouts",JSONArray(layouts.toString()))
        put("scope","App window frame metrics and first loaded pre-draw; excludes SystemUI's frames and compositor presentation")
    } }
}
