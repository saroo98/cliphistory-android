package app.cliphistory.client

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.cliphistory.ClipApplication
import app.cliphistory.R
import app.cliphistory.ui.MainActivity

/** Optional Android supervisor; only the authorized Shizuku helper reads the clipboard. */
class RecordingRecoveryService : Service() {
    private val client get()=(application as ClipApplication).daemon
    private var stopFailed=false
    private val changed:()->Unit={ if(client.initialized && !client.recoveryAllowed())stopSelf() else updateNotification() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,getString(R.string.recovery_channel),NotificationManager.IMPORTANCE_LOW)
                .apply { description=getString(R.string.recovery_channel_body);setShowBadge(false) })
        startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        client.addListener(changed);client.setRecoveryActive(true)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP)client.stopRecording {
            if(client.preferences.options().explicitlyStopped)stopSelf()
            else { stopFailed=true;updateNotification() }
        }
        else if(!client.initialized)return START_STICKY
        else if(!client.recoveryAllowed()){stopSelf();return START_NOT_STICKY}
        return if(client.recoveryAllowed())START_STICKY else START_NOT_STICKY
    }
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,7,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,8,Intent(this,RecordingRecoveryService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(if(stopFailed)R.string.recovery_stop_failed else if(client.connected())R.string.recovery_connected else R.string.recovery_waiting))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .addAction(Notification.Action.Builder(null,getString(R.string.open_app),open).build())
            .addAction(Notification.Action.Builder(null,getString(R.string.stop_recorder),stop).build()).build()
    }
    private fun updateNotification() { getSystemService(NotificationManager::class.java).notify(7,notification()) }
    override fun onDestroy() { client.removeListener(changed);client.setRecoveryActive(false);super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
    companion object { const val CHANNEL="recording_recovery";const val STOP="app.cliphistory.STOP_RECORDER" }
}
