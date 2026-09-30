package app.cliphistory.ui

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cliphistory.R

class ClipboardTileService:TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { label=getString(R.string.tile_name);subtitle=getString(R.string.tile_subtitle);state=Tile.STATE_INACTIVE;updateTile() }
    }
    override fun onClick() {
        super.onClick()
        val launch={
            val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        }
        if(isLocked)unlockAndRun(launch) else launch()
    }
}
