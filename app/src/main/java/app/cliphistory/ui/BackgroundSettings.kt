package app.cliphistory.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

class BackgroundSettings(private val activity:Activity) {
    fun status():String {
        val exempt=activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName)
        val restricted=activity.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        return activity.getString(app.cliphistory.R.string.port_battery_status,
            activity.getString(if(exempt)app.cliphistory.R.string.port_battery_off else app.cliphistory.R.string.port_battery_on),
            activity.getString(if(restricted)app.cliphistory.R.string.port_background_restricted else app.cliphistory.R.string.port_background_allowed))
    }
    /** Undocumented AOSP action is best effort. Only trusted system Settings can handle it. */
    fun open(packageName:String):Boolean {
        val data=Uri.fromParts("package",packageName,null)
        return launch(Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL",data)) ||
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,data))
    }
    fun openOptimizationList()=launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    private fun launch(intent:Intent):Boolean {
        val resolved=activity.packageManager.queryIntentActivities(intent,0).firstOrNull {
            it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0
        } ?: return false
        return try {
            activity.startActivity(intent.setComponent(android.content.ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name)))
            true
        } catch (_:Exception) { false }
    }
}
