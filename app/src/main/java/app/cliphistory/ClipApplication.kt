package app.cliphistory
import android.app.Application
import app.cliphistory.client.DaemonClient
class ClipApplication:Application() {
    val daemon:DaemonClient by lazy { DaemonClient(this) }
}
