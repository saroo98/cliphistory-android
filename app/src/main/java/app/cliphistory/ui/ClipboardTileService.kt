package app.cliphistory.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.view.WindowManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cliphistory.R
import java.lang.ref.WeakReference

class ClipboardTileService:TileService() {
    private val settings by lazy { AppSettings(this) }
    companion object {
        // The attached QS window owns its dialog. A transient service unbind
        // must not close it; a weak reference also permits detached windows to die.
        private var quick:WeakReference<QuickCopyDialog>?=null
    }
    private val preferences=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key==null || key=="tile_mode" || key=="tile_enabled")publishTile()
    }
    private fun trace(event:String) { if(app.cliphistory.BuildConfig.DEBUG)android.util.Log.d("ClipHistoryTileTrace","${android.os.SystemClock.uptimeMillis()} tile $event") }
    override fun onCreate() { super.onCreate();trace("created") }
    private fun historyLaunch():PendingIntent {
        val intent=Intent(this,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.action=MainActivity.ACTION_OPEN_HISTORY
        return PendingIntent.getActivity(this,0,intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    override fun onStartListening() {
        super.onStartListening()
        trace("listening tile=${qsTile!=null}")
        settings.register(preferences);publishTile()
    }
    override fun onStopListening() { settings.unregister(preferences);super.onStopListening() }
    override fun onTileRemoved() { quick?.get()?.dismiss();quick=null;super.onTileRemoved() }
    override fun onDestroy() { settings.unregister(preferences);super.onDestroy() }
    private fun publishTile() {
        qsTile?.apply {
            val title=packageManager.getServiceInfo(ComponentName(this@ClipboardTileService,ClipboardTileService::class.java),0).loadLabel(packageManager)
            val detail=getString(if(settings.tileMode=="quick")R.string.quick_copy else R.string.tile_subtitle)
            val description="$title, $detail"
            val availability=if(settings.tileEnabled)Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            val launch=if(settings.tileEnabled && settings.tileMode=="history")historyLaunch() else null
            // onBind acknowledges the connection. Only changed state needs an
            // update; unchanged updates lower our priority against other tiles.
            if(label?.toString()==title.toString() && subtitle?.toString()==detail &&
                contentDescription?.toString()==description && state==availability && activityLaunchForClick==launch)return
            label=title;subtitle=detail;contentDescription=description;state=availability
            // A direct Activity launch asks SystemUI to expand the tile into a
            // task, even for a tiny floating window. Quick mode uses showDialog.
            activityLaunchForClick=launch
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if(!settings.tileEnabled)return
        val action:()->Unit={
            if(settings.tileEnabled && !isLocked) {
                if(settings.tileMode=="history")startActivityAndCollapse(historyLaunch())
                else {
                    val existing=quick?.get()?.takeIf { it.isShowing && it.window?.decorView?.isAttachedToWindow==true }
                    val dialog=existing ?: QuickCopyDialog(this) { startActivityAndCollapse(historyLaunch()) }
                    quick=WeakReference(dialog)
                    dialog.setOnDismissListener { if(quick?.get()===dialog)quick=null }
                    try { showDialog(dialog);existing?.refresh() }
                    catch(_:WindowManager.BadTokenException) {
                        dialog.dismiss();quick=null
                        // Use full history if Android rejects the dialog window.
                        startActivityAndCollapse(historyLaunch())
                    }
                }
            }
        }
        if(isLocked)unlockAndRun(action) else action()
    }
}
