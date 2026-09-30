package app.cliphistory.client

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import app.cliphistory.core.RecoveryOptions

class RecorderPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("recorder_recovery", Context.MODE_PRIVATE)
    private fun flag(key:String,default:Boolean,invalid:Boolean=default)=try { prefs.getBoolean(key,default) } catch (_:ClassCastException) { invalid }
    fun options() = RecoveryOptions(
        flag("setup_completed",false),flag("automatic",true,false),
        flag("after_boot",true,false),flag("explicit_stop",false,true),
        try { prefs.getInt("activated_boot",-1) } catch (_:ClassCastException) { -1 }
    )
    val explicitStartTime: Long get() = try { prefs.getLong("explicit_start_time",0) } catch (_:ClassCastException) { 0 }
    val acknowledgedExit: Long get() = try { prefs.getLong("acknowledged_exit",0) } catch (_:ClassCastException) { 0 }
    fun bootCount(): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).takeIf { it >= 0 }
    } catch (_: Exception) { null }
    // Call critical writes on the client worker. Failure must not be acknowledged as saved.
    private fun save(change:SharedPreferences.Editor.()->Unit):Boolean {
        val previous=prefs.all
        if(prefs.edit().apply(change).commit())return true
        // SharedPreferences changes its memory even when commit fails. Restore it before
        // callers read options, so a failed Stop cannot hide an independently running helper.
        val restore=prefs.edit().clear()
        previous.forEach { (key,value) -> when(value) {
            is Boolean->restore.putBoolean(key,value)
            is Int->restore.putInt(key,value)
            is Long->restore.putLong(key,value)
            is Float->restore.putFloat(key,value)
            is String->restore.putString(key,value)
            is Set<*>->restore.putStringSet(key,value.filterIsInstance<String>().toSet())
        } }
        restore.commit()
        return false
    }
    fun activate():Boolean=save { putBoolean("explicit_stop",false);putInt("activated_boot",bootCount() ?: -1);putLong("explicit_start_time",System.currentTimeMillis()) }
    fun completeSetup():Boolean=save { putBoolean("setup_completed",true) }
    fun stop(exitTime:Long=0):Boolean=save { putBoolean("explicit_stop",true);putLong("acknowledged_exit",maxOf(acknowledgedExit,exitTime)) }
    fun setAutomatic(value:Boolean):Boolean=save { putBoolean("automatic",value) }
    fun setAfterBoot(value:Boolean):Boolean=save { putBoolean("after_boot",value) }
}
