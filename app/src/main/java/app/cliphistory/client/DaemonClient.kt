package app.cliphistory.client

import android.content.*
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.pm.PackageManager
import android.os.*
import app.cliphistory.BuildConfig
import app.cliphistory.R
import app.cliphistory.core.*
import app.cliphistory.daemon.ClipboardUserService
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ipc.IHistoryObserver
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Preserve policy fields the user is not changing, rather than trusting an old UI page. */
internal fun IClipboardDaemon.changeSensitivePolicy(capture:Boolean?=null,autoDelete:Boolean?=null,minutes:Int?=null):Bundle {
    val current=status()
    return if(!current.getBoolean("ok"))current else setSensitivePolicy(
        capture?:current.getBoolean("captureSensitive"),
        autoDelete?:current.getBoolean("autoDeleteSensitive"),
        minutes?:current.getInt("sensitiveMinutes"))
}

class DaemonClient(private val context: Context) {
    data class Row(val id:Long,val time:Long,val preview:String,val sensitive:Boolean=false,val expiry:Expiry?=null)
    data class Page(val status:Bundle,val rows:List<Row>,val total:Int,val generation:Long,val offline:Boolean,val issue:String="")
    data class TextResult(val text:String?,val issue:String="",val sensitive:Boolean=false,val expiry:Expiry?=null)
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor { r -> Thread(r,"ClipHistory-client") }
    private val listeners=LinkedHashSet<() -> Unit>()
    @Volatile private var remote:IClipboardDaemon?=null
    private var attaching:IClipboardDaemon?=null
    @Volatile var message="Start your service app, then tap Connect.";private set
    private var connectionIssue=""
    private var connecting=false
    private var permissionDenied=false
    private var connectionEpoch=0
    val preferences=RecorderPreferences(context)
    @Volatile private var options=RecoveryOptions()
    private var boot:Int?=null
    @Volatile private var historyBoot=-1
    private var ready=false
    private var storageReady=false
    @Volatile var encryptionSecurity="Not confirmed";private set
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
                    val material=HistoryKeys.load(context)
                    val transferredKey=material.bytes.copyOf()
                    response=try {
                        historyBoot=AndroidHistoryClock(context).now().boot
                        service.attachEncrypted(a,b,Shizuku.getBinder() ?: error("SHIZUKU_STOPPED"),transferredKey,material.storageId,historyBoot)
                    } finally { transferredKey.fill(0);a.close();b.close() }
                    if(response.getBoolean("ok") && (response.getInt("encryptionVersion")!=1 ||
                        !java.security.MessageDigest.isEqual(response.getByteArray("storageIdentity"),material.storageId)))
                        throw StoreException("ENCRYPTION_IDENTITY_CHANGED")
                    if(response.getBoolean("ok") && resumeOnConnect)response=service.setPaused(false)
                    if(response.getBoolean("ok") && !preferences.options().setupCompleted && !preferences.completeSetup()) {
                        // Attach may already have registered the privileged listener. A
                        // failed first-setup write must not leave an unreported recorder.
                        try { service.setPaused(true) } finally { Shizuku.unbindUserService(args,connection,true) }
                        response=Bundle().apply { putBoolean("ok",false);putString("issue","PREFERENCES_NOT_SAVED") }
                    }
                    if(response.getBoolean("ok")) service.registerObserver(observer)
                } catch(t:Throwable) {
                    val code=if(t is StoreException)t.reason else "CONNECT_FAILED_${t.javaClass.simpleName}"
                    response=Bundle().apply { putBoolean("ok",false);putString("issue",code) }
                }
                main.post {
                    if(attaching===service)attaching=null
                    if(epoch!=connectionEpoch || pendingStop) {
                        io.execute { try { service.unregisterObserver(observer) } catch (_:Exception) {} }
                        return@post
                    }
                    connecting=false
                    if(response.getBoolean("ok")) {
                        options=preferences.options();remote=service;message="Connected";connectionIssue="";resumeOnConnect=false
                        cancelRetries();startRecoveryIfAllowed()
                    } else {
                        remote=null;connectionIssue=response.getString("issue").orEmpty()
                        message="Connection failed: $connectionIssue";cancelRetries()
                    }
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
        Shizuku.addBinderDeadListener { main.post { disconnected("Service stopped. Start it again, then reconnect.") } }
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
                    preferences.explicitStartTime,preferences.acknowledgedExit,it.description,context.packageName) }
                if(stop!=null && !preferences.stop(stop.timestamp))error("PREFERENCES_NOT_SAVED")
                val loaded=preferences.options();val marker=preferences.bootCount()
                historyBoot=marker?:-1
                val prepared=try { prepareStorage();true } catch (_:Exception) { false }
                main.post { options=loaded;boot=marker;ready=true;storageReady=prepared;startRecoveryIfAllowed();connect(false) }
            } catch (_:Exception) {
                main.post { ready=true;message="Cannot verify the previous stop. Tap Connect to start explicitly.";changed() }
            }
        }
    }
    private fun prepareStorage() {
        val material=PrivateHistory.prepare(context) { stopPreviousWriter() }
        encryptionSecurity=material.security
    }
    private fun stopPreviousWriter(onlyIfOutdated:Boolean=false) {
        if(!PrivateHistory.hasHistory(context)) {
            val installed=context.packageManager.getPackageInfo(context.packageName,0)
            if(installed.firstInstallTime==installed.lastUpdateTime)return
        }
        // A missing provider Binder is not proof that an old recorder has stopped.
        if(!Shizuku.pingBinder()) {
            throw StoreException("START_SHIZUKU_TO_UPGRADE_HISTORY")
        }
        if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)
            throw StoreException("ALLOW_SHIZUKU_TO_UPGRADE_HISTORY")
        val received=CountDownLatch(1)
        val old=AtomicReference<IBinder?>()
        val temporary=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) { old.set(binder);received.countDown() }
            override fun onServiceDisconnected(name:ComponentName) { received.countDown() }
        }
        try {
            val version=Shizuku.peekUserService(args,temporary)
            if(version<0 || (onlyIfOutdated && version==BuildConfig.VERSION_CODE))return
            if(!received.await(8,TimeUnit.SECONDS))throw StoreException("PREVIOUS_RECORDER_NOT_CONFIRMED")
            val binder=old.get() ?: throw StoreException("PREVIOUS_RECORDER_NOT_CONFIRMED")
            val dead=CountDownLatch(1)
            val death=IBinder.DeathRecipient { dead.countDown() }
            if(binder.isBinderAlive) {
                binder.linkToDeath(death,0)
                try {
                    Shizuku.unbindUserService(args,temporary,true)
                    if(!dead.await(8,TimeUnit.SECONDS) && binder.isBinderAlive)throw StoreException("PREVIOUS_RECORDER_NOT_STOPPED")
                } finally { try { binder.unlinkToDeath(death,0) } catch (_:Exception) {} }
            }
        } finally { try { Shizuku.unbindUserService(args,temporary,false) } catch (_:Exception) {} }
    }
    fun addListener(listener:()->Unit) { listeners.add(listener) }
    fun removeListener(listener:()->Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
    private fun disconnected(reason:String,issue:String="") { connectionEpoch++;connecting=false;remote=null;attaching=null;message=reason;connectionIssue=issue;changed();scheduleRetry() }
    fun connected()=remote?.asBinder()?.isBinderAlive==true
    val initialized get()=ready
    fun expired(expiry:Expiry?)=expiry?.expired(HistoryTime(historyBoot,SystemClock.elapsedRealtime()))==true
    fun expiryDelay(expiry:Expiry?)=expiry?.let { if(expired(it))0L else (it.elapsed-SystemClock.elapsedRealtime()).coerceAtLeast(0) }
    private fun rows(bundle:Bundle):List<Row> {
        val ids=bundle.getLongArray("ids")?:longArrayOf()
        val times=bundle.getLongArray("times")?:longArrayOf()
        val previews=bundle.getStringArray("previews")?:emptyArray()
        val marked=bundle.getBooleanArray("sensitive")
        val boots=bundle.getIntArray("expiryBoots")
        val deadlines=bundle.getLongArray("expiryTimes")
        return ids.indices.map { i -> Row(ids[i],times[i],previews[i],marked?.getOrNull(i)?:false,
            deadlines?.getOrNull(i)?.takeIf { it>0 }?.let { Expiry(boots?.getOrNull(i)?:-1,it) }) }
    }
    private fun policy(bundle:Bundle,snapshot:Snapshot) {
        bundle.putBoolean("captureSensitive",snapshot.captureSensitive)
        bundle.putBoolean("autoDeleteSensitive",snapshot.autoDeleteSensitive)
        bundle.putInt("sensitiveMinutes",snapshot.sensitiveMinutes)
    }
    fun installedManagers()=MANAGERS.filter {
        context.packageManager.getLaunchIntentForPackage(it)!=null
    }
    companion object {
        /** Must match the manifest's package queries. */
        val MANAGERS=listOf("moe.shizuku.privileged.api","com.churan.shizako",
            "eu.darken.porter","com.hamondev.shevery","af.shizuku.plus.api")
    }
    /** A shortcut is safe only when the installed manager is unambiguous. */
    fun managerPackage():String?=installedManagers().singleOrNull()
    fun connectionStage():ConnectionStage = try {
        when {
            connected() -> ConnectionStage.CONNECTED
            connecting -> ConnectionStage.CONNECTING
            !Shizuku.pingBinder() -> if(installedManagers().isEmpty())
                ConnectionStage.MISSING else ConnectionStage.STOPPED
            Shizuku.getVersion()<MIN_SERVICE_API -> ConnectionStage.OUTDATED
            Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED ->
                if(permissionDenied || Shizuku.shouldShowRequestPermissionRationale()) ConnectionStage.DENIED else ConnectionStage.PERMISSION
            else -> serviceStage(Shizuku.getVersion(),Shizuku.getUid()) ?: ConnectionStage.FAILED
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
            if(!Shizuku.pingBinder()) { message="Start or restart your compatible service, then reconnect.";changed();return }
            if(Shizuku.getVersion()<MIN_SERVICE_API) { message=context.getString(R.string.outdated_body);changed();return }
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                message="Tap Connect and allow service access."
                if(askPermission) {
                    if(Shizuku.shouldShowRequestPermissionRationale()) message="Allow ClipHistory in your service app's authorised applications."
                    else Shizuku.requestPermission(71)
                }
                changed();return
            }
            when(serviceStage(Shizuku.getVersion(),Shizuku.getUid())) {
                ConnectionStage.ROOT_MODE -> { message=context.getString(R.string.root_mode_body);changed();return }
                null -> Unit
                else -> { message=context.getString(R.string.unsupported_body);changed();return }
            }
            if(!storageReady) {
                connecting=true;message="Checking private history…";changed()
                io.execute {
                    val issue=try { prepareStorage();"" } catch(e:Exception) { readIssue(e) }
                    main.post {
                        connecting=false;storageReady=issue.isEmpty()
                        if(storageReady)connectAllowed(askPermission) else {message="History protection needs attention: $issue";changed()}
                    }
                }
                return
            }
            connecting=true
            if(askPermission || connectionIssue!="HELPER_START_TIMEOUT")connectionIssue=""
            message="Connecting recorder and checking private storage…";changed()
            val epoch=++connectionEpoch
            var bindRequested=false
            io.execute {
                // Also confirm replacement of an older encrypted recorder before
                // a new version attaches another writer to the same files.
                val issue=try { stopPreviousWriter(onlyIfOutdated=true);"" } catch(e:Exception) { readIssue(e) }
                main.post {
                    if(!connecting || epoch!=connectionEpoch)return@post
                    if(issue.isNotEmpty())disconnected("Cannot replace recorder: $issue")
                    else try { Shizuku.bindUserService(args,connection);bindRequested=true }
                    catch(t:Throwable) { disconnected("Cannot connect: ${t.javaClass.simpleName}") }
                }
            }
            main.postDelayed({ if(connecting && epoch==connectionEpoch) {
                if(bindRequested && attaching==null)disconnected(context.getString(R.string.helper_start_failed_message),"HELPER_START_TIMEOUT")
                else disconnected("Connection timed out. Check Shizuku and tap Connect again.","CONNECTION_TIMEOUT")
            } },30_000)
        } catch(t:Throwable) { disconnected("Cannot connect: ${t.javaClass.simpleName}") }
    }
    /** One screen's page reads. A newer request or [cancel] from the same screen makes its older
     * reads stale; other screens are unaffected. Android stops a closing screen after the next
     * one has started, so an app-wide token would drop the new screen's first page. */
    class PageReads {
        internal val latest=AtomicLong(0)
        fun cancel() { latest.incrementAndGet() }
    }
    fun requestPage(reads:PageReads,query:String,offset:Int,callback:(Page)->Unit) {
        val service=remote
        val token=reads.latest.incrementAndGet()
        io.execute {
            if(token!=reads.latest.get())return@execute
            val result=try {
                if(service!=null && service.asBinder().isBinderAlive) {
                    val status=service.status();val page=service.page(query.take(512),offset,40)
                    if(!status.getBoolean("ok") || !page.getBoolean("ok")) {
                        Page(status,emptyList(),0,0,false,page.getString("issue")?:status.getString("issue")?:"READ_FAILED")
                    } else {
                        Page(status,rows(page),page.getInt("total"),page.getLong("generation"),false)
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
            main.post { if(token==reads.latest.get()) {
                result.status.putString("encryptionSecurity",encryptionSecurity)
                result.status.putString("connectionMessage",message)
                result.status.putString("connectionIssue",connectionIssue)
                callback(result.copy(rows=result.rows.filterNot { expired(it.expiry) }))
            } }
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
    fun shouldStopRecovery()=ready && !pendingStop && !recoveryAllowed()
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
                // The stop moved the epoch, so an in-flight connect can no longer finish
                // and its timeout no longer fires. Never leave the UI stuck on Connecting.
                options=loaded;pendingStop=false;connecting=false;attaching=null
                if(options.explicitlyStopped) {
                    activatedThisSession=false
                    remote=null;connecting=false;connectionIssue="";message="Recorder stopped by you. Tap Connect to start it again."
                    context.stopService(Intent(context,RecordingRecoveryService::class.java))
                }
                callback(result);changed()
            }
        }
    }
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
                        Page(status,rows(page),page.getInt("total"),page.getLong("generation"),false)
                    }
                } else offlinePage("",0).let { it.copy(rows=it.rows.take(count)) }
            } catch (_:Exception) { try { offlinePage("",0).let { it.copy(rows=it.rows.take(count)) } } catch(e:Exception){failedPage(e,true)} }
            main.post {
                result.status.putString("encryptionSecurity",encryptionSecurity)
                result.status.putString("connectionMessage",message)
                result.status.putString("connectionIssue",connectionIssue)
                callback(result.copy(rows=result.rows.filterNot { expired(it.expiry) }))
            }
        }
    }
    private fun offlinePage(query:String,offset:Int):Page {
        val snapshot=PrivateHistory.readOffline(context)
        historyBoot=AndroidHistoryClock(context).now().boot
        val matched=snapshot.entries.filter { it.text.contains(query,ignoreCase=true) }
        val status=Bundle().apply {
            putInt("count",snapshot.entries.size);putInt("limit",snapshot.limit);putBoolean("paused",snapshot.paused)
            putInt("duplicateMode",snapshot.duplicateMode.value);putBoolean("explicitStop",options.explicitlyStopped)
            policy(this,snapshot);putInt("encryptionVersion",1);putString("encryptionSecurity",encryptionSecurity)
            putBoolean("bootIdentityKnown",historyBoot>=0)
        }
        return Page(status,matched.drop(offset).take(40).map { Row(it.id,it.timestamp,listPreview(it.text,query,it.sensitive),it.sensitive,it.expiry) },matched.size,snapshot.generation,true)
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
            historyBoot=AndroidHistoryClock(context).now().boot
            fun offlineText():TextResult {
                val entry=PrivateHistory.readOffline(context).entries.firstOrNull { it.id==id }
                return TextResult(entry?.text,sensitive=entry?.sensitive?:false,expiry=entry?.expiry)
            }
            val value=try {
                if(service!=null && service.asBinder().isBinderAlive) {
                    val entry=service.entry(id)
                    if(entry.getBoolean("ok"))TextResult(entry.getString("text"),sensitive=entry.getBoolean("sensitive"),
                        expiry=entry.getLong("expiryTime").takeIf { it>0 }?.let { Expiry(entry.getInt("expiryBoot",-1),it) })
                    else TextResult(null,entry.getString("issue","HISTORY_READ_FAILED").orEmpty())
                }else offlineText()
            } catch(t:Exception) {
                if(t is RemoteException) {
                    main.post { if(remote===service)disconnected("Recorder disconnected. Saved history is available offline.") }
                    try { offlineText() } catch(failure:Exception){TextResult(null,readIssue(failure))}
                }else TextResult(null,readIssue(t))
            }
            main.post { callback(if(expired(value.expiry))value.copy(text=null) else value) }
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
    fun openShizuku(packageName:String):Boolean {
        val launch=context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true }
        catch (_:ActivityNotFoundException) { false }
        catch (_:SecurityException) { false }
    }
}
