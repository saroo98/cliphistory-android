package app.cliphistory.daemon

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import app.cliphistory.core.StoreException
import app.cliphistory.core.isDeliberateUserStop

/** Event-driven checks under shell identity, before both reading and committing a copy. */
class OwnerStopGuard(private val context:Context,private val uid:Int) {
    private val startedAt=System.currentTimeMillis()
    @Volatile var issue="";private set
    fun check():Boolean {
        val identity=Binder.clearCallingIdentity()
        return try {
            val info=context.packageManager.getApplicationInfo(context.packageName,PackageManager.ApplicationInfoFlags.of(0))
            if(info.uid!=uid || !info.enabled || info.flags and ApplicationInfo.FLAG_STOPPED!=0) {
                issue="OWNER_STOPPED";false
            } else {
                val exits=context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName,0,16)
                val stopped=exits.any { isDeliberateUserStop(it.reason==ApplicationExitInfo.REASON_USER_REQUESTED,it.description) && it.timestamp>startedAt }
                issue=if(stopped)"OWNER_STOPPED" else ""
                !stopped
            }
        } catch (_:Exception) { issue="OWNER_STOP_CHECK_UNAVAILABLE";false }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    fun verify() { if(!check())throw StoreException(issue) }
}
