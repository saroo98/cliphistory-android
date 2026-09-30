package app.cliphistory.client

import android.content.*
import android.app.ActivityManager
import android.app.ApplicationExitInfo
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
    private var attaching:IClipboardDaemon?=null
    @Volatile var message="Start Shizuku, then tap Connect.";private set
    private var connecting=false
    private var permissionDenied=false
    private var connectionEpoch=0
    val preferences=RecorderPreferences(context)
    @Volatile private var options=RecoveryOptions()
    private var boot:Int?=null
    private var ready=false
    private var activatedThisSession=false
    private var pendingStop=false
    private var resumeOnConnect=false
    private var recoveryActive=false
    private var retries=0
    private val retry=Runnable { connect(false) }
    var recoveryMessage="";private set
    private val args=Shizuku.UserServiceArgs(ComponentName(context,ClipboardUserService::class.java))
        .daemon(true).processNameSuffix("clipboard").debuggable(false).version(BuildConfig.VERSION_CODE).tag("cliphistory-v1")
    private val observer=object:IHistoryObserver.Stub() {
        override fun onChanged() { main.post { changed() } }
    }
    private val connection:ServiceConnection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            if(pendingStop || (!activatedThisSession && options.explicitlyStopped))return
            val service=IClipboardDaemon.Stub.asInterface(binder)
            attaching=service
            val epoch=connectionEpoch
            io.execute {
                var response:Bundle
                try {
                    binder.linkToDeath({ main.post { if(remote?.asBinder()===binder) disconnected("Recorder stopped. Tap Connect to restart.") } },0)
                    val (a,b)=PrivateHistory.openForDaemon(context)
                    response=try { service.attach(a,b,Shizuku.getBinder() ?: error("SHIZUKU_STOPPED")) } finally { a.close();b.close() }
                    if(response.getBoolean("ok") && resumeOnConnect)response=service.setPaused(false)
                    if(response.getBoolean("ok") && !preferences.options().setupCompleted && !preferences.completeSetup()) {
                        // Attach may already have registered the privileged listener. A
                        // failed first-setup write must not leave an unreported recorder.
                        try { service.setPaused(true) } finally { Shizuku.unbindUserService(args,connection,true) }
                        response=Bundle().apply { putBoolean("ok",false);putString("issue","PREFERENCES_NOT_SAVED") }
                    }
                    if(response.getBoolean("ok")) service.registerObserver(observer)
                } catch(t:Throwable) {
                    response=Bundle().apply { putBoolean("ok",false);putString("issue","CONNECT_FAILED_${t.javaClass.simpleName}") }
                }
                main.post {
                    if(attaching===service)attaching=null
                    if(epoch!=connectionEpoch || pendingStop) {
                        io.execute { try { service.unregisterObserver(observer) } catch (_:Exception) {} }
                        return@post
                    }
                    connecting=false
                    if(response.getBoolean("ok")) {
                        options=preferences.options();remote=service;message="Connected";resumeOnConnect=false
                        cancelRetries();startRecoveryIfAllowed()
                    } else { remote=null;message="Connection failed: ${response.getString("issue")}";cancelRetries() }
                    changed()
                }
            }
        }
        override fun onServiceDisconnected(name:ComponentName) { disconnected("Recorder disconnected. Tap Connect.") }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky { main.post {
            if(ready){cancelRetries();startRecoveryIfAllowed();connect(false)} else changed()
        } }
        Shizuku.addBinderDeadListener { main.post { disconnected("Shizuku stopped. Start Shizuku, then reconnect.") } }
        Shizuku.addRequestPermissionResultListener { requestCode, grant ->
            if(requestCode==71)main.post {
                permissionDenied=grant!=PackageManager.PERMISSION_GRANTED
                if(!permissionDenied && !pendingStop && activatedThisSession && !options.explicitlyStopped)connectAllowed()
                else { connecting=false;message="Permission denied. Allow ClipHistory in Shizuku's authorised apps.";changed() }
            }
        }
        io.execute {
            try {
                val exits=context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null,0,16)
                val stop=exits.firstOrNull { isNewUserStop(it.timestamp,it.reason==ApplicationExitInfo.REASON_USER_REQUESTED,
                    preferences.explicitStartTime,preferences.acknowledgedExit,it.description) }
                if(stop!=null && !preferences.stop(stop.timestamp))error("PREFERENCES_NOT_SAVED")
                val loaded=preferences.options();val marker=preferences.bootCount()
                main.post { options=loaded;boot=marker;ready=true;startRecoveryIfAllowed();connect(false) }
            } catch (_:Exception) {
                main.post { ready=true;message="Cannot verify the previous stop. Tap Connect to start explicitly.";changed() }
            }
        }
    }
    fun addListener(listener:()->Unit) { listeners.add(listener) }
    fun removeListener(listener:()->Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
    private fun disconnected(reason:String) { connectionEpoch++;connecting=false;remote=null;attaching=null;message=reason;changed();scheduleRetry() }
    fun connected()=remote?.asBinder()?.isBinderAlive==true
    val initialized get()=ready
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
        if(askPermission) {
            if(pendingStop)return
            io.execute {
                val wasStopped=preferences.options().explicitlyStopped
                val saved=preferences.activate();val loaded=preferences.options();val marker=preferences.bootCount()
                main.post {
                    if(!saved){message="Could not save recording preferences. Try again.";changed();return@post}
                    options=loaded;boot=marker;ready=true;activatedThisSession=true
                    resumeOnConnect=wasStopped;cancelRetries()
                    if(wasStopped) {
                        try { Shizuku.unbindUserService(args,connection,true) } catch (_:Exception) { }
                        remote=null;connecting=false;connectionEpoch++
                    }
                    startRecoveryIfAllowed()
                    connectAllowed(true)
                }
            }
            return
        }
        if(!ready || pendingStop || !mayConnect(options,boot,false,activatedThisSession)) {
            if(options.explicitlyStopped)message="Recorder stopped by you. Tap Connect to start it again."
            changed();return
        }
        connectAllowed()
    }
    private fun connectAllowed(askPermission:Boolean=false) {
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
    private fun cancelRetries() { main.removeCallbacks(retry);retries=0 }
    private fun scheduleRetry() {
        main.removeCallbacks(retry)
        if(!recoveryActive || pendingStop || !mayRecover(options,boot,activatedThisSession))return
        val delays=longArrayOf(1000,3000,10_000)
        if(retries>=delays.size || !Shizuku.pingBinder())return
        main.postDelayed(retry,delays[retries++])
    }
    fun setRecoveryActive(value:Boolean) {
        recoveryActive=value
        if(value){cancelRetries();connect(false)} else main.removeCallbacks(retry)
    }
    fun recoveryAllowed()=ready && !pendingStop && mayRecover(options,boot,activatedThisSession)
    private fun startRecoveryIfAllowed() {
        if(!recoveryAllowed() || recoveryActive)return
        try { context.startForegroundService(Intent(context,RecordingRecoveryService::class.java));recoveryMessage="" }
        catch (_:Exception) { recoveryMessage="Android limited background recovery. Open the app to enable it." }
    }
    fun updateRecovery(automatic:Boolean?=null,afterBoot:Boolean?=null,callback:(Boolean)->Unit) {
        io.execute {
            val saved=(automatic?.let { preferences.setAutomatic(it) } ?: true) &&
                (afterBoot?.let { preferences.setAfterBoot(it) } ?: true)
            val loaded=preferences.options();val marker=preferences.bootCount()
            main.post {
                options=loaded;boot=marker
                if(!recoveryAllowed()) { main.removeCallbacks(retry);context.stopService(Intent(context,RecordingRecoveryService::class.java)) }
                else startRecoveryIfAllowed()
                callback(saved);changed()
            }
        }
    }
    fun stopRecording(callback:(Bundle)->Unit) {
        if(pendingStop)return
        pendingStop=true;connectionEpoch++;main.removeCallbacks(retry)
        val service=remote ?: attaching
        io.execute {
            val result=Bundle()
            if(!preferences.stop()) {
                result.putBoolean("ok",false);result.putString("issue","PREFERENCES_NOT_SAVED")
            } else {
                try {
                    try {
                        if(service?.asBinder()?.isBinderAlive==true) {
                            val paused=service.setPaused(true)
                            check(paused.getBoolean("ok")) { paused.getString("issue","PAUSE_NOT_SAVED").orEmpty() }
                        }
                    } finally {
                        // A failed pause write must not leave the independent helper capturing.
                        if(Shizuku.pingBinder())Shizuku.unbindUserService(args,connection,true)
                    }
                    result.putBoolean("ok",true)
                } catch (_:Exception) { result.putBoolean("ok",false);result.putString("issue","STOP_NOT_CONFIRMED_RETRY") }
            }
            val loaded=preferences.options()
            main.post {
                options=loaded;pendingStop=false;activatedThisSession=false
                if(options.explicitlyStopped) {
                    remote=null;connecting=false;message="Recorder stopped by you. Tap Connect to start it again."
                    context.stopService(Intent(context,RecordingRecoveryService::class.java))
                }
                callback(result);changed()
            }
        }
    }
    fun cancelPageReads() { pageRequest.incrementAndGet() }
    /** Independent bounded read: a tile must not invalidate the Activity's pagination. */
    fun requestLatest(count:Int=3,callback:(Page)->Unit) {
        require(count in 1..3)
        val service=remote
        io.execute {
            val result=try {
                if(service!=null && service.asBinder().isBinderAlive) {
                    val status=service.status();val page=service.page("",0,count)
                    if(!status.getBoolean("ok") || !page.getBoolean("ok"))
                        Page(status,emptyList(),0,0,false,page.getString("issue")?:"READ_FAILED")
                    else {
                        val ids=page.getLongArray("ids")?:longArrayOf()
                        val times=page.getLongArray("times")?:longArrayOf()
                        val previews=page.getStringArray("previews")?:emptyArray()
                        Page(status,ids.indices.map { Row(ids[it],times[it],previews[it]) },page.getInt("total"),page.getLong("generation"),false)
                    }
                } else offlinePage("",0).let { it.copy(rows=it.rows.take(count)) }
            } catch (_:Exception) { try { offlinePage("",0).let { it.copy(rows=it.rows.take(count)) } } catch(e:Exception){failedPage(e,true)} }
            main.post { callback(result) }
        }
    }
    private fun offlinePage(query:String,offset:Int):Page {
        val snapshot=PrivateHistory.readOffline(context)
        val matched=snapshot.entries.filter { it.text.contains(query,ignoreCase=true) }
        val status=Bundle().apply { putInt("count",snapshot.entries.size);putInt("limit",snapshot.limit);putBoolean("paused",snapshot.paused);putInt("duplicateMode",snapshot.duplicateMode.value);putBoolean("explicitStop",options.explicitlyStopped) }
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
