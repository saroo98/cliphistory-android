package app.cliphistory.daemon

import android.content.Context
import android.os.*
import android.os.Process
import app.cliphistory.core.*
import app.cliphistory.BuildConfig
import app.cliphistory.ipc.IClipboardDaemon
import app.cliphistory.ipc.IHistoryObserver
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Shizuku creates this Binder in a distinct shell-UID process, not an Android Service. */
class ClipboardUserService(context: Context) : IClipboardDaemon.Stub() {
    private val ownerUid = context.applicationInfo.uid
    private val userId = ownerUid / 100000
    private val callbacks = RemoteCallbackList<IHistoryObserver>()
    private val worker = ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(256), { task -> Thread(task,"ClipHistory-store") }, ThreadPoolExecutor.AbortPolicy())
    @Volatile private var lease: IBinder? = null
    @Volatile private var leaseAlive = false
    private var leaseDeath: IBinder.DeathRecipient? = null
    private val stopping = AtomicBoolean(false)
    private var attachedFileIds: List<Pair<Long,Long>> = emptyList()
    private var captureEpoch=0L
    private var repository: HistoryRepository? = null
    private var slots: List<Slot> = emptyList()
    private var bridge: PlatformClipboardBridge? = null
    private var lastIssue = ""
    private var saved = 0L
    private var lastSaved = 0L
    private var sensitive = 0L
    private var oversized = 0L
    private var unsupported = 0L
    private val queueDrops = AtomicLong(0)
    private val probe = ProbeTracker()
    private var storageVerified = false
    init {
        require(context.packageName == BuildConfig.APPLICATION_ID && ownerUid >= 10000) { "INVALID_OWNER_CONTEXT" }
    }
    private fun requireOwner() {
        if (Binder.getCallingUid() != ownerUid) throw SecurityException("OWNER_UID_REQUIRED")
    }

    override fun attach(first: ParcelFileDescriptor, second: ParcelFileDescriptor, shizukuServer: IBinder): Bundle {
        requireOwner()
        return serial {
            var transferred = false
            try {
                check(Process.myUid()==2000) { "START_SHIZUKU_USING_WIRELESS_DEBUGGING" }
                check(userId==0) { "PRIMARY_PHONE_PROFILE_REQUIRED" }
                attachLease(shizukuServer)
                val accessA=FdAccess(first,ownerUid);val accessB=FdAccess(second,ownerUid)
                val incomingIds=listOf(accessA.identity,accessB.identity)
                check(incomingIds[0]!=incomingIds[1]) { "DISTINCT_PRIVATE_FILES_REQUIRED" }
                if(repository!=null && attachedFileIds!=incomingIds) {
                    // Android's Clear storage can replace files without killing a shell process.
                    // Never resurrect the daemon's old in-memory history after that explicit wipe.
                    captureEpoch++;bridge?.close();bridge=null
                    slots.forEach { try { it.close() } catch (_:Exception) {} }
                    slots=emptyList();repository=null;storageVerified=false
                }
                if(repository==null) {
                    val firstSlot = FramedSlot(accessA)
                    val secondSlot = FramedSlot(accessB)
                    val created = HistoryRepository(firstSlot,secondSlot)
                    created.initialize(); created.initialize() // Exercise writes to BOTH private descriptors.
                    check(created.verifyDurable()) { "STORAGE_VERIFICATION_FAILED" }
                    repository = created; slots = listOf(firstSlot,secondSlot);attachedFileIds=incomingIds; transferred = true
                    storageVerified = true
                }
                if(bridge?.registered!=true) {
                    bridge?.close()
                    val epoch=++captureEpoch
                    val createdBridge=PlatformClipboardBridge(userId, { packet -> received(packet,epoch) }) { issue ->
                        enqueue { if(epoch==captureEpoch) { lastIssue=issue;notifyChanged() } }
                    }
                    bridge=createdBridge
                    createdBridge.start()
                }
                // Retry can clear an old storage/read fault only after a new durable write.
                if(lastIssue.isNotEmpty()) {
                    repo().initialize(); storageVerified=repo().verifyDurable()
                    check(storageVerified) { "STORAGE_VERIFICATION_FAILED" }
                }
                lastIssue=""
                statusInternal()
            } finally {
                if(!transferred) { try { first.close() } catch (_: Exception) {}; try { second.close() } catch (_: Exception) {} }
            }
        }
    }

    /** A Shizuku-server lease, NOT a UI-process lease. Closing the UI does not stop capture. */
    private fun attachLease(server: IBinder) {
        check(!stopping.get() && server.isBinderAlive) { "SHIZUKU_STOPPED" }
        if(lease === server && leaseAlive)return
        check(server.interfaceDescriptor=="moe.shizuku.server.IShizukuService") { "INVALID_SHIZUKU_SERVER" }
        val old=lease;val oldDeath=leaseDeath
        if(old!=null && oldDeath!=null)try { old.unlinkToDeath(oldDeath,0) } catch (_:Exception) {}
        lease=server;leaseAlive=true
        val recipient=IBinder.DeathRecipient { if(lease === server) shutdownProcess() }
        leaseDeath=recipient
        try { server.linkToDeath(recipient,0) } catch(t:Throwable) { leaseAlive=false;throw t }
    }

    private fun received(packet: PlatformClipboardBridge.CopiedText,epoch:Long) = enqueue {
        if(!leaseAlive || stopping.get() || epoch!=captureEpoch)return@enqueue
        when(probe.match(packet.text,SystemClock.elapsedRealtime())) {
            ProbeTracker.Match.IGNORE -> return@enqueue
            ProbeTracker.Match.CURRENT -> {
                try { repo().initialize();storageVerified=repo().verifyDurable() }
                catch (_:Throwable) { storageVerified=false }
                probe.complete(storageVerified);notifyChanged();return@enqueue
            }
            ProbeTracker.Match.NOT_TEST -> Unit
        }
        try {
            when {
                packet.sensitive -> sensitive++
                packet.oversized -> oversized++
                packet.text==null -> unsupported++
                else -> when(repo().capture(packet.text, packet.time)) {
                    CaptureResult.SAVED -> { saved++; lastSaved=packet.time; storageVerified=true; lastIssue="" }
                    CaptureResult.TOO_LARGE -> oversized++
                    CaptureResult.INVALID_TEXT -> unsupported++
                    else -> Unit
                }
            }
        } catch(t:Throwable) {
            // The user removed app storage; discard this helper's memory along with it.
            if(t is FdAccess.DetachedFile)kotlin.system.exitProcess(0)
            storageVerified=false;lastIssue=safeIssue(t)
        }
        notifyChanged()
    }
    private fun repo()=repository ?: error("STORAGE_NOT_CONNECTED")
    private fun enqueue(action: () -> Unit) {
        try { worker.execute(action) } catch (_: RejectedExecutionException) { queueDrops.incrementAndGet() }
    }
    private fun safeIssue(t: Throwable): String = when(t) {
        is java.lang.reflect.InvocationTargetException -> t.targetException?.let { safeIssue(it) } ?: "PLATFORM_INVOCATION_FAILED"
        is StoreException -> t.reason
        is SecurityException -> "ANDROID_PERMISSION_DENIED"
        else -> "OPERATION_FAILED_${t.javaClass.simpleName}"
    }
    private fun serial(action: () -> Bundle): Bundle {
        return try {
            worker.submit<Bundle> {
                try { action() } catch(t:Throwable) {
                    lastIssue=safeIssue(t); notifyChanged(); statusInternal().apply { putBoolean("ok",false) }
                }
            }.get(20,TimeUnit.SECONDS)
        } catch (_: TimeoutException) { failure("BUSY_OPERATION_MAY_STILL_COMPLETE")
        } catch (_: Exception) { failure("DAEMON_BUSY_OR_UNAVAILABLE") }
    }
    private fun failure(issue: String)=Bundle().apply { putBoolean("ok",false);putString("issue",issue) }
    private fun statusInternal(): Bundle {
        val testState=probe.status(SystemClock.elapsedRealtime())
        val snapshot=repository?.state
        // A past missed event is irreversible history, not a current storage or
        // listener failure. Keep the counter visible without claiming capture stopped.
        val problem=lastIssue
        return Bundle().apply {
            putBoolean("ok",true); putInt("uid",Process.myUid());putInt("sdk",Build.VERSION.SDK_INT)
            putBoolean("listening",bridge?.registered==true);putBoolean("connected",snapshot!=null)
            putBoolean("paused",snapshot?.paused?:false);putBoolean("storageVerified",storageVerified)
            putBoolean("leaseAlive",leaseAlive)
            putBoolean("active",leaseAlive && bridge?.registered==true && snapshot!=null && !snapshot.paused && storageVerified && problem.isEmpty())
            putInt("count",snapshot?.entries?.size?:0);putInt("limit",snapshot?.limit?:DEFAULT_LIMIT)
            putLong("generation",snapshot?.generation?:0);putLong("saved",saved);putLong("lastSaved",lastSaved)
            putLong("sensitiveSkipped",sensitive);putLong("oversizedSkipped",oversized);putLong("unsupportedSkipped",unsupported)
            putLong("queueDrops",queueDrops.get());putString("issue",problem);putString("selfTest",testState)
            putString("signature",bridge?.signature?:"Not connected")
            putBoolean("recovered",repository?.recoveredFromDamagedSlot?:false)
        }
    }
    private fun notifyChanged() {
        val count=callbacks.beginBroadcast()
        try { for(i in 0 until count) try { callbacks.getBroadcastItem(i).onChanged() } catch (_: RemoteException) {} }
        finally { callbacks.finishBroadcast() }
    }
    override fun status():Bundle { requireOwner(); return serial { statusInternal() } }
    override fun page(query:String,offset:Int,count:Int):Bundle {
        requireOwner()
        return serial {
            require(query.length<=512 && offset>=0 && count in 1..40) { "INVALID_PAGE_REQUEST" }
            val entries=repo().search(query)
            val page=entries.drop(offset).take(count)
            Bundle().apply {
                putBoolean("ok",true);putLong("generation",repo().state.generation);putInt("total",entries.size)
                putLongArray("ids",page.map { it.id }.toLongArray());putLongArray("times",page.map { it.timestamp }.toLongArray())
                putStringArray("previews",page.map { SearchPreview.snippet(it.text,query) }.toTypedArray())
            }
        }
    }
    override fun text(id:Long):String? {
        requireOwner()
        return try { worker.submit<String?> { repo().state.entries.firstOrNull { it.id==id }?.text }.get(20,TimeUnit.SECONDS) }
        catch (_: Exception) { null }
    }
    override fun entry(id:Long):Bundle {
        requireOwner()
        return serial {
            Bundle().apply { putBoolean("ok",true);putString("text",repo().state.entries.firstOrNull { it.id==id }?.text) }
        }
    }
    override fun deleteEntry(id:Long):Bundle { requireOwner();return serial { repo().delete(id);notifyChanged();statusInternal() } }
    override fun clearHistory():Bundle { requireOwner();return serial { repo().clear();notifyChanged();statusInternal() } }
    override fun setLimit(limit:Int):Bundle { requireOwner();return serial { repo().setLimit(limit);notifyChanged();statusInternal() } }
    override fun setPaused(paused:Boolean):Bundle { requireOwner();return serial { repo().setPaused(paused);notifyChanged();statusInternal() } }
    override fun registerObserver(observer:IHistoryObserver) { requireOwner();callbacks.register(observer) }
    override fun unregisterObserver(observer:IHistoryObserver) { requireOwner();callbacks.unregister(observer) }
    override fun armTest(nonce:String):Bundle {
        requireOwner()
        return serial {
            check(bridge?.registered==true && repository!=null) { "CONNECT_FIRST" }
            probe.arm(nonce,SystemClock.elapsedRealtime());statusInternal()
        }
    }
    override fun verifyStorage():Bundle {
        requireOwner()
        return serial { storageVerified=repo().verifyDurable();statusInternal() }
    }
    override fun destroy() {
        val caller=Binder.getCallingUid()
        if(caller!=ownerUid && caller!=2000 && caller!=0)throw SecurityException("SHIZUKU_OR_OWNER_REQUIRED")
        shutdownProcess()
    }
    private fun shutdownProcess() {
        if(!stopping.compareAndSet(false,true))return
        // This also gates already-queued captures before draining the worker.
        leaseAlive=false
        bridge?.close();worker.shutdown()
        val finished=try { worker.awaitTermination(5,TimeUnit.SECONDS) } catch (_:InterruptedException) { false }
        if(finished)slots.forEach { try { it.close() } catch (_:Exception) {} }
        callbacks.kill()
        // If storage is wedged, process death closes its FDs; never race a live writer by closing them here.
        kotlin.system.exitProcess(0)
    }
}
