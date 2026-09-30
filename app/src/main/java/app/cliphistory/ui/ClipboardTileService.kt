package app.cliphistory.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cliphistory.R

class ClipboardTileService:TileService() {
    private fun trace(event:String) { if(app.cliphistory.BuildConfig.DEBUG)android.util.Log.d("ClipHistoryTileTrace","${android.os.SystemClock.uptimeMillis()} tile $event") }
    override fun onCreate() { super.onCreate();trace("created") }
    private fun launch():PendingIntent {
        val quick=AppSettings(this).tileMode=="quick"
        val intent=Intent(this,if(quick)QuickCopyActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if(quick)intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(this,if(quick)2 else 0,intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    override fun onTileAdded() {
        trace("added")
        super.onTileAdded();requestListeningState(this,ComponentName(this,ClipboardTileService::class.java))
    }
    override fun onStartListening() {
        super.onStartListening()
        trace("listening tile=${qsTile!=null}")
        qsTile?.apply {
            label=packageManager.getServiceInfo(ComponentName(this@ClipboardTileService,ClipboardTileService::class.java),0).loadLabel(packageManager)
            subtitle=getString(if(AppSettings(this@ClipboardTileService).tileMode=="quick")R.string.quick_copy else R.string.tile_subtitle)
            contentDescription="$label, $subtitle";state=Tile.STATE_INACTIVE
            // API 34 lets SystemUI launch this immutable Activity intent directly,
            // without waiting for a transient service click binding.
            activityLaunchForClick=launch();updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if(!AppSettings(this).tileEnabled)return
        val action:()->Unit={startActivityAndCollapse(launch())}
        if(isLocked)unlockAndRun(action) else action()
    }
}
