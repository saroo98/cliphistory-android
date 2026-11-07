package app.cliphistory.client

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.cliphistory.ClipApplication
import app.cliphistory.R
import app.cliphistory.ui.MainActivity

/** Optional Android supervisor; only the authorized Shizuku helper reads the clipboard. */
class RecordingRecoveryService : Service() {
    private val client get()=(application as ClipApplication).daemon
    private var stopFailed=false
    private var displayedMessage=0
    private val changed:()->Unit={ if(client.shouldStopRecovery())stopSelf() else updateNotification() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,getString(R.string.recovery_channel),NotificationManager.IMPORTANCE_LOW)
                .apply { description=getString(R.string.recovery_channel_body);setShowBadge(false) })
        displayedMessage=notificationMessage()
        val notice=notification(displayedMessage)
        if(Build.VERSION.SDK_INT>=34)startForeground(7,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7,notice)
        client.addListener(changed);client.setRecoveryActive(true)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP)client.stopRecording {
            if(client.preferences.options().explicitlyStopped)stopSelf()
            else { stopFailed=true;updateNotification() }
        }
        else if(!client.initialized)return START_STICKY
        else if(!client.recoveryAllowed()){stopSelf();return START_NOT_STICKY}
        return if(client.shouldStopRecovery())START_NOT_STICKY else START_STICKY
    }
    private fun notificationMessage()=if(stopFailed)R.string.recovery_stop_failed else if(client.connected())R.string.recovery_connected else R.string.recovery_waiting
    private fun notification(message:Int):Notification {
        val open=PendingIntent.getActivity(this,7,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,8,Intent(this,RecordingRecoveryService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_cliphistory_system)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(message))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
        // After a reboot the user usually only needs to start the service app: one tap.
        if(message==R.string.recovery_waiting)serviceAppIntent()?.let { launch ->
            builder.addAction(Notification.Action.Builder(null,getString(R.string.open_shizuku),
                PendingIntent.getActivity(this,9,launch,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
        }
        return builder.addAction(Notification.Action.Builder(null,getString(R.string.open_app),open).build())
            .addAction(Notification.Action.Builder(null,getString(R.string.stop_recorder),stop).build()).build()
    }
    /** Only when exactly one known service app is installed; otherwise ClipHistory asks which. */
    private fun serviceAppIntent():Intent?=try {
        client.managerPackage()?.let { packageManager.getLaunchIntentForPackage(it) }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (_:Exception) { null }
    private fun updateNotification() {
        val message=notificationMessage()
        // Clipboard changes do not change this notification; avoid Android dropping a real state change.
        if(message==displayedMessage)return
        getSystemService(NotificationManager::class.java).notify(7,notification(message))
        displayedMessage=message
    }
    override fun onDestroy() { client.removeListener(changed);client.setRecoveryActive(false);super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
    companion object { const val CHANNEL="recording_recovery";const val STOP="app.cliphistory.STOP_RECORDER" }
}
