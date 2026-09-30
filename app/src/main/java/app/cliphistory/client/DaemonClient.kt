package app.cliphistory.client

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import app.cliphistory.BuildConfig
import app.cliphistory.core.*
import app.cliphistory.daemon.ClipboardUserService
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ipc.IHistoryObserver
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class DaemonClient(private val context: Context) {
    data class Row(val id:Long,val time:Long,val preview:String)
    data class Page(val status:Bundle,val rows:List<Row>,val total:Int,val generation:Long,val offline:Boolean,val issue:String="")
    data class TextResult(val text:String?,val issue:String="")
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor { r -> Thread(r,"ClipHistory-client") }
    private val listeners=LinkedHashSet<() -> Unit>()
    private val pageRequest=AtomicLong(0)
    @Volatile private var remote:IClipboardDaemon?=null
    @Volatile var message="Start Shizuku, then tap Connect.";private set
    private var connecting=false
    private var permissionDenied=false
    private var connectionEpoch=0
    private val args=Shizuku.UserServiceArgs(ComponentName(context,ClipboardUserService::class.java))
        .daemon(true).processNameSuffix("clipboard").debuggable(false).version(BuildConfig.VERSION_CODE).tag("cliphistory-v1")
    private val observer=object:IHistoryObserver.Stub() {
        override fun onChanged() { main.post { changed() } }
    }
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            val service=IClipboardDaemon.Stub.asInterface(binder)
            val epoch=connectionEpoch
            io.execute {
                var response:Bundle
                try {
                    binder.linkToDeath({ main.post { if(remote?.asBinder()===binder) disconnected("Recorder stopped. Tap Connect to restart.") } },0)
                    val (a,b)=PrivateHistory.openForDaemon(context)
                    response=try { service.attach(a,b,Shizuku.getBinder() ?: error("SHIZUKU_STOPPED")) } finally { a.close();b.close() }
                    if(response.getBoolean("ok")) service.registerObserver(observer)
                } catch(t:Throwable) {
                    response=Bundle().apply { putBoolean("ok",false);putString("issue","CONNECT_FAILED_${t.javaClass.simpleName}") }
                }
                main.post {
                    if(epoch!=connectionEpoch)return@post
                    connecting=false
                    if(response.getBoolean("ok")) { remote=service;message="Connected" }
                    else { remote=null;message="Connection failed: ${response.getString("issue")}" }
                    changed()
                }
            }
        }
        override fun onServiceDisconnected(name:ComponentName) { disconnected("Recorder disconnected. Tap Connect.") }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky { main.post { if(listeners.isNotEmpty())connect(false) else changed() } }
        Shizuku.addBinderDeadListener { main.post { disconnected("Shizuku stopped. Start Shizuku, then reconnect.") } }
        Shizuku.addRequestPermissionResultListener { requestCode, grant ->
            if(requestCode==71)main.post {
                permissionDenied=grant!=PackageManager.PERMISSION_GRANTED
                if(!permissionDenied)connect(false)
                else { connecting=false;message="Permission denied. Allow ClipHistory in Shizuku's authorised apps.";changed() }
            }
        }
    }
    fun addListener(listener:()->Unit) { listeners.add(listener) }
    fun removeListener(listener:()->Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
    private fun disconnected(reason:String) { connectionEpoch++;connecting=false;remote=null;message=reason;changed() }
    fun connected()=remote?.asBinder()?.isBinderAlive==true
    fun connectionStage():ConnectionStage = try {
        when {
            connected() -> ConnectionStage.CONNECTED
            connecting -> ConnectionStage.CONNECTING
            context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")==null -> ConnectionStage.MISSING
            !Shizuku.pingBinder() -> ConnectionStage.STOPPED
            Shizuku.getVersion()<13 -> ConnectionStage.UNSUPPORTED
            Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED ->
                if(permissionDenied || Shizuku.shouldShowRequestPermissionRationale()) ConnectionStage.DENIED else ConnectionStage.PERMISSION
            Shizuku.getUid()!=2000 -> ConnectionStage.UNSUPPORTED
            else -> ConnectionStage.FAILED
        }
    } catch (_:Exception) { ConnectionStage.STOPPED }
    fun connect(askPermission:Boolean) {
        if(connected()) {
            if(askPermission && !connecting) {
                connecting=true;connectionEpoch++
                connection.onServiceConnected(ComponentName(context,ClipboardUserService::class.java),remote!!.asBinder())
            } else changed()
            return
        }
        if(connecting)return
        try {
            if(!Shizuku.pingBinder()) { message="Shizuku is not running. Open Shizuku and press Start.";changed();return }
            if(Shizuku.getVersion()<13) { message="Update Shizuku; API 13 or newer is required.";changed();return }
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                message="Tap Connect and allow Shizuku access."
                if(askPermission) {
                    if(Shizuku.shouldShowRequestPermissionRationale()) message="Open Shizuku → Authorised applications → allow ClipHistory."
                    else Shizuku.requestPermission(71)
                }
                changed();return
            }
            if(Shizuku.getUid()!=2000) { message="Use Shizuku's wireless-debugging (non-root) mode for this build.";changed();return }
            connecting=true;message="Connecting recorder and checking private storage…";changed()
            val epoch=++connectionEpoch
            Shizuku.bindUserService(args,connection)
            main.postDelayed({ if(connecting && epoch==connectionEpoch)disconnected("Connection timed out. Check Shizuku and tap Connect again.") },30_000)
        } catch(t:Throwable) { disconnected("Cannot connect: ${t.javaClass.simpleName}") }
    }
    fun requestPage(query:String,offset:Int,callback:(Page)->Unit) {
        val service=remote
        val token=pageRequest.incrementAndGet()
        io.execute {
            if(token!=pageRequest.get())return@execute
            val result=try {
                if(service!=null && service.asBinder().isBinderAlive) {
                    val status=service.status();val page=service.page(query.take(512),offset,40)
                    if(!status.getBoolean("ok") || !page.getBoolean("ok")) {
                        Page(status,emptyList(),0,0,false,page.getString("issue")?:status.getString("issue")?:"READ_FAILED")
                    } else {
                        val ids=page.getLongArray("ids")?:longArrayOf();val times=page.getLongArray("times")?:longArrayOf()
                        val previews=page.getStringArray("previews")?:emptyArray()
                        val rows=ids.indices.map { Row(ids[it],times[it],previews[it]) }
                        Page(status,rows,page.getInt("total"),page.getLong("generation"),false)
                    }
                } else {
                    offlinePage(query,offset)
                }
            } catch(t:Exception) {
                if(t is RemoteException) {
                    main.post { if(remote===service)disconnected("Recorder disconnected. Saved history is available offline.") }
                    try { offlinePage(query,offset) } catch(failure:Exception){failedPage(failure,true)}
                } else failedPage(t,service==null)
            }
            main.post { if(token==pageRequest.get())callback(result) }
        }
    }
    fun cancelPageReads() { pageRequest.incrementAndGet() }
    private fun offlinePage(query:String,offset:Int):Page {
        val snapshot=PrivateHistory.readOffline(context)
        val matched=snapshot.entries.filter { it.text.contains(query,ignoreCase=true) }
        val status=Bundle().apply { putInt("count",snapshot.entries.size);putInt("limit",snapshot.limit);putBoolean("paused",snapshot.paused) }
        return Page(status,matched.drop(offset).take(40).map { Row(it.id,it.timestamp,SearchPreview.snippet(it.text,query)) },matched.size,snapshot.generation,true)
    }
    private fun readIssue(t:Exception)=when(t) {
        is StoreException->t.reason
        is SecurityException->"HISTORY_ACCESS_DENIED"
        else->"HISTORY_READ_FAILED_${t.javaClass.simpleName}"
    }
    private fun failedPage(t:Exception,offline:Boolean):Page {
        val issue=readIssue(t)
        return Page(Bundle().apply { putString("issue",issue) },emptyList(),0,0,offline,issue)
    }
    fun getText(id:Long,callback:(TextResult)->Unit) {
        val service=remote
        io.execute {
            fun offlineText()=TextResult(PrivateHistory.readOffline(context).entries.firstOrNull { it.id==id }?.text)
            val value=try {
                if(service!=null && service.asBinder().isBinderAlive) {
                    val entry=service.entry(id)
                    if(entry.getBoolean("ok"))TextResult(entry.getString("text"))
                    else TextResult(null,entry.getString("issue","HISTORY_READ_FAILED").orEmpty())
                }else offlineText()
            } catch(t:Exception) {
                if(t is RemoteException) {
                    main.post { if(remote===service)disconnected("Recorder disconnected. Saved history is available offline.") }
                    try { offlineText() } catch(failure:Exception){TextResult(null,readIssue(failure))}
                }else TextResult(null,readIssue(t))
            }
            main.post { callback(value) }
        }
    }
    fun command(action:(IClipboardDaemon)->Bundle,callback:(Bundle)->Unit) {
        val service=remote
        if(service==null) { callback(Bundle().apply { putBoolean("ok",false);putString("issue","Connect Shizuku before changing history.") });return }
        io.execute {
            val result=try { action(service) } catch(t:Throwable) { Bundle().apply { putBoolean("ok",false);putString("issue","COMMAND_FAILED_${t.javaClass.simpleName}") } }
            main.post { callback(result);changed() }
        }
    }
    fun openShizuku() {
        val launch=context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if(launch!=null)context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else { message="Shizuku is not installed in this phone profile.";changed() }
    }
}
