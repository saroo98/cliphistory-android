package app.cliphistory.client

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.cliphistory.core.HistoryClock
import app.cliphistory.core.HistoryTime

class AndroidHistoryClock(private val context: Context) : HistoryClock {
    private var boot=-1
    override fun now(): HistoryTime {
        // Boot count cannot change without terminating this process. Retry an unavailable read.
        if(boot<0)boot=try { Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT).coerceAtLeast(-1) }
            catch (_:Exception) { -1 }
        return HistoryTime(boot,SystemClock.elapsedRealtime())
    }
}
