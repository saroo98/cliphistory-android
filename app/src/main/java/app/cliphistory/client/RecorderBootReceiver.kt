package app.cliphistory.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import app.cliphistory.ClipApplication

class RecorderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        if(!context.getSystemService(UserManager::class.java).isUserUnlocked)return
        // Application initialization reconciles user stops and boot identity on its worker.
        (context.applicationContext as ClipApplication).daemon
    }
}
