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
    private val ownerStop=OwnerStopGuard(context,ownerUid)
    private val userId = ownerUid / 100000
    private val callbacks = RemoteCallbackList<IHistoryObserver>()
    private val worker = ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(256), { task -> Thread(task,"ClipHistory-store") }, ThreadPoolExecutor.AbortPolicy())
    @Volatile private var lease: IBinder? = null
    @Volatile private var leaseAlive = false
    private var leaseDeath: IBinder.DeathRecipient? = null
    private val stopping = AtomicBoolean(false)
    @Volatile private var historyBoot=-1
    // App-owned Settings attribution is unavailable in shell. The authorized
    // app supplies its verified boot count at attachment; reboot kills the lease.
    private val clock=HistoryClock { HistoryTime(historyBoot,SystemClock.elapsedRealtime()) }
    private val deadlines=ScheduledThreadPoolExecutor(1) { task -> Thread(task,"ClipHistory-expiry") }
        .apply { removeOnCancelPolicy=true }
    @Volatile private var expiryTask:ScheduledFuture<*>?=null
    private var expiryFailures=0
    @Volatile private var captureSensitive=false
    @Volatile private var retainedSensitive=emptyList<Entry>()
    private var attachedFileIds: List<Pair<Long,Long>> = emptyList()
    private var attachedStorageId=byteArrayOf()
    private var attachedKeyDigest=byteArrayOf()
    private var captureEpoch=0L
    private var repository: HistoryRepository? = null
    private var slots: List<Slot> = emptyList()
    private var bridge: PlatformClipboardBridge? = null
    private var lastIssue = ""
    private var saved = 0L
    private var lastSaved = 0L
    private var sensitive = 0L
    private var sensitiveSaved = 0L
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
        first.close();second.close()
        return failure("ENCRYPTION_REQUIRED")
    }
    override fun attachEncrypted(first: ParcelFileDescriptor, second: ParcelFileDescriptor, shizukuServer: IBinder,
        dataKey: ByteArray, storageIdentity: ByteArray, bootCount:Int): Bundle {
        requireOwner()
        return serial {
            var transferred = false
            try {
                // Never relax: a root helper makes Android clear the clipboard (see serviceStage).
                check(Process.myUid()==SHELL_UID) { "START_SHIZUKU_USING_WIRELESS_DEBUGGING" }
                if(dataKey.size!=32 || storageIdentity.size!=16 || bootCount< -1)throw StoreException("INVALID_ENCRYPTION_KEY")
                check(userId==0) { "PRIMARY_PHONE_PROFILE_REQUIRED" }
                ownerStop.verify()
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
                    historyBoot=bootCount
                    val firstSlot = EncryptedSlot(FramedSlot(accessA,EncryptedSlot.MAX_ENCRYPTED_BYTES),dataKey,storageIdentity,BuildConfig.APPLICATION_ID,0)
                    val secondSlot = EncryptedSlot(FramedSlot(accessB,EncryptedSlot.MAX_ENCRYPTED_BYTES),dataKey,storageIdentity,BuildConfig.APPLICATION_ID,1)
                    val created = HistoryRepository(firstSlot,secondSlot,clock) { retainedSensitive=it }
                    if(created.state.generation==0L)throw StoreException("BOTH_SNAPSHOTS_UNREADABLE")
                    created.initialize(forceMirror=true)
                    check(created.verifyBothDurable()) { "STORAGE_VERIFICATION_FAILED" }
                    repository = created; slots = listOf(firstSlot,secondSlot);attachedFileIds=incomingIds; transferred = true
                    attachedStorageId=storageIdentity.copyOf()
                    attachedKeyDigest=java.security.MessageDigest.getInstance("SHA-256").digest(dataKey)
                    storageVerified = true
                } else if(!java.security.MessageDigest.isEqual(attachedStorageId,storageIdentity) ||
                    !java.security.MessageDigest.isEqual(attachedKeyDigest,java.security.MessageDigest.getInstance("SHA-256").digest(dataKey)) || (historyBoot>=0 && historyBoot!=bootCount)) {
                    throw StoreException("ENCRYPTION_IDENTITY_CHANGED")
                }
                // The authorized owner may recover a temporarily unavailable
                // clock. A surviving lease must never change a known boot ID.
                historyBoot=bootCount
                captureSensitive=repo().state.captureSensitive
                publishSensitivity()
                if(bridge?.registered!=true) {
                    bridge?.close()
                    val epoch=++captureEpoch
                    val createdBridge=PlatformClipboardBridge(userId, { packet -> received(packet,epoch) }, {
                        if(ownerStop.check())true else { shutdownProcess();false }
                    }, { captureSensitive },{ retainedSensitive },clock) { issue ->
                        enqueue { if(epoch==captureEpoch) { lastIssue=issue;notifyChanged() } }
                    }
                    bridge=createdBridge
                    createdBridge.start()
                }
                // Retry can clear an old storage/read fault only after a new durable write.
                if(lastIssue.isNotEmpty()) {
                    repo().initialize(forceMirror=true); storageVerified=repo().verifyBothDurable()
                    check(storageVerified) { "STORAGE_VERIFICATION_FAILED" }
                }
                lastIssue=""
                statusInternal()
            } finally {
                dataKey.fill(0)
                if(!transferred) { try { first.close() } catch (_: Exception) {}; try { second.close() } catch (_: Exception) {} }
            }
        }
    }
    override fun setSensitivePolicy(captureSensitive:Boolean,autoDelete:Boolean,durationMinutes:Int):Bundle {
        requireOwner()
        return serial {
            try { repo().setSensitivePolicy(captureSensitive,autoDelete,durationMinutes) }
            finally { this.captureSensitive=repo().state.captureSensitive }
            notifyChanged();statusInternal()
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
        if(!ownerStop.check()){shutdownProcess();return@enqueue}
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
                packet.sensitive && !repo().state.captureSensitive -> sensitive++
                packet.oversized -> oversized++
                packet.text==null -> unsupported++
                else -> when(repo().capture(packet.text, packet.time,packet.sensitive,packet.capturedAt,packet.inheritedExpiry)) {
                    CaptureResult.SAVED -> { saved++; if(repo().state.entries.first().sensitive)sensitiveSaved++
                        lastSaved=packet.time; storageVerified=true; lastIssue="" }
                    CaptureResult.SENSITIVE -> sensitive++
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
        scheduleExpiry();notifyChanged()
    }
    private fun repo()=repository ?: error("STORAGE_NOT_CONNECTED")
    private fun enqueue(action: () -> Unit):Boolean {
        return try { worker.execute(action);true } catch (_: RejectedExecutionException) { queueDrops.incrementAndGet();false }
    }
    /** One deadline, no periodic work when there are no timed entries. */
    private fun scheduleExpiry(failed:Boolean=false) {
        expiryTask?.cancel(false);expiryTask=null
        if(stopping.get())return
        val delay=repository?.expiryDelay() ?: return
        val retries=longArrayOf(1000,3000,10_000)
        if(!failed)expiryFailures=0
        else if(expiryFailures>=retries.size)return
        // An early tick finds nothing expired and simply schedules the next one.
        val wait=if(failed)retries[expiryFailures++] else expiryWait(delay)
        fun submit(attempt:Int) {
            if(stopping.get())return
            if(!enqueue {
                var error=false
                try {
                    if(repo().expire(retryCleanup=true)) {
                        if(lastIssue=="EXPIRY_CLEANUP_INCOMPLETE")lastIssue=""
                        notifyChanged()
                    }
                } catch (_:Exception) { error=true;lastIssue="EXPIRY_CLEANUP_INCOMPLETE";notifyChanged() }
                finally { scheduleExpiry(error) }
            } && attempt<retries.size) {
                expiryTask=deadlines.schedule({submit(attempt+1)},retries[attempt],TimeUnit.MILLISECONDS)
            }
        }
        expiryTask=deadlines.schedule({submit(0)},wait,TimeUnit.MILLISECONDS)
    }
    private fun safeIssue(t: Throwable): String = issueCode(t)
    private fun serial(action: () -> Bundle): Bundle {
        return try {
            worker.submit<Bundle> {
                try { action() } catch(t:Throwable) {
                    lastIssue=safeIssue(t); notifyChanged(); statusInternal().apply { putBoolean("ok",false) }
                } finally { publishSensitivity();scheduleExpiry() }
            }.get(20,TimeUnit.SECONDS)
        } catch (_: TimeoutException) { failure("BUSY_OPERATION_MAY_STILL_COMPLETE")
        } catch (_: Exception) { failure("DAEMON_BUSY_OR_UNAVAILABLE") }
    }
    private fun failure(issue: String)=Bundle().apply { putBoolean("ok",false);putString("issue",issue) }
    private fun statusInternal(): Bundle {
        val testState=probe.status(SystemClock.elapsedRealtime())
        val snapshot=repository?.available
        // A past missed event is irreversible history, not a current storage or
        // listener failure. Keep the counter visible without claiming capture stopped.
        val problem=lastIssue
        return Bundle().apply {
            putBoolean("ok",true); putInt("uid",Process.myUid());putInt("sdk",Build.VERSION.SDK_INT)
            putInt("encryptionVersion",1);putByteArray("storageIdentity",attachedStorageId.copyOf())
            putBoolean("listening",bridge?.registered==true);putBoolean("connected",snapshot!=null)
            putBoolean("paused",snapshot?.paused?:false);putBoolean("storageVerified",storageVerified)
            putBoolean("leaseAlive",leaseAlive)
            putBoolean("active",leaseAlive && bridge?.registered==true && snapshot!=null && !snapshot.paused && storageVerified && problem.isEmpty())
            putInt("count",snapshot?.entries?.size?:0);putInt("limit",snapshot?.limit?:DEFAULT_LIMIT)
            putInt("duplicateMode",snapshot?.duplicateMode?.value?:DuplicateMode.UNIQUE_TEXT.value)
            putBoolean("captureSensitive",snapshot?.captureSensitive?:false)
            putBoolean("autoDeleteSensitive",snapshot?.autoDeleteSensitive?:true)
            putInt("sensitiveMinutes",snapshot?.sensitiveMinutes?:5)
            putBoolean("bootIdentityKnown",clock.now().boot>=0)
            putLong("generation",snapshot?.generation?:0);putLong("saved",saved);putLong("lastSaved",lastSaved)
            putLong("undoToken",repository?.undoToken?:0);putLong("undoEntryId",repository?.undoEntryId?:-1)
            putLong("sensitiveSkipped",sensitive);putLong("oversizedSkipped",oversized);putLong("unsupportedSkipped",unsupported)
            putLong("sensitiveSaved",sensitiveSaved)
            putLong("queueDrops",queueDrops.get());putString("issue",problem);putString("selfTest",testState)
            putString("signature",bridge?.signature?:"Not connected")
            putBoolean("recovered",repository?.recoveredFromDamagedSlot?:false)
            putBoolean("ownerStopVerified",ownerStop.issue.isEmpty())
        }
    }
    private fun publishSensitivity() { retainedSensitive=repository?.sensitiveEntries?:emptyList() }
    private fun notifyChanged() {
        publishSensitivity()
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
                putStringArray("previews",page.map { listPreview(it.text,query,it.sensitive) }.toTypedArray())
                putBooleanArray("sensitive",page.map { it.sensitive }.toBooleanArray())
                putIntArray("expiryBoots",page.map { it.expiry?.boot?:-1 }.toIntArray())
                putLongArray("expiryTimes",page.map { it.expiry?.elapsed?:0 }.toLongArray())
            }
        }
    }
    override fun text(id:Long):String? {
        requireOwner()
        return try { worker.submit<String?> { repo().entry(id)?.text }.get(20,TimeUnit.SECONDS) }
        catch (_: Exception) { null }
    }
    override fun entry(id:Long):Bundle {
        requireOwner()
        return serial {
            val entry=repo().entry(id)
            Bundle().apply {
                putBoolean("ok",true);putString("text",entry?.text);putBoolean("sensitive",entry?.sensitive?:false)
                putInt("expiryBoot",entry?.expiry?.boot?:-1);putLong("expiryTime",entry?.expiry?.elapsed?:0)
            }
        }
    }
    override fun deleteEntry(id:Long):Bundle { requireOwner();return serial {
        if(!repo().delete(id)) failure("ENTRY_MISSING") else { notifyChanged();statusInternal() }
    } }
    override fun undoDelete(token:Long):Bundle { requireOwner();return serial {
        try {
            val result=repo().undoDelete(token);notifyChanged()
            statusInternal().apply { putBoolean("undoAlreadyPresent",result==HistoryRepository.UndoResult.ALREADY_PRESENT) }
        } catch(error:StoreException) {
            if(error.reason!="UNDO_HISTORY_FULL" && error.reason!="UNDO_NOT_AVAILABLE")throw error
            statusInternal().apply { putBoolean("ok",false);putString("issue",error.reason) }
        }
    } }
    override fun dismissUndo(token:Long):Bundle { requireOwner();return serial {
        try { repo().dismissUndo(token);notifyChanged();statusInternal() }
        catch(error:StoreException) {
            if(error.reason!="UNDO_NOT_AVAILABLE")throw error
            statusInternal().apply { putBoolean("ok",false);putString("issue",error.reason) }
        }
    } }
    override fun clearHistory():Bundle { requireOwner();return serial { repo().clear();notifyChanged();statusInternal() } }
    override fun setLimit(limit:Int):Bundle { requireOwner();return serial { repo().setLimit(limit);notifyChanged();statusInternal() } }
    override fun setPaused(paused:Boolean):Bundle { requireOwner();return serial { repo().setPaused(paused);notifyChanged();statusInternal() } }
    override fun setDuplicateMode(mode:Int):Bundle { requireOwner();return serial {
        repo().setDuplicateMode(DuplicateMode.fromValue(mode));notifyChanged();statusInternal()
    } }
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
        expiryTask?.cancel(false);deadlines.shutdownNow()
        Thread({
            bridge?.close();worker.shutdown()
            val finished=try { worker.awaitTermination(5,TimeUnit.SECONDS) } catch (_:InterruptedException) { false }
            if(finished)slots.forEach { try { it.close() } catch (_:Exception) {} }
            callbacks.kill()
            // Never wait for the worker from the worker itself or race a live writer's FDs.
            kotlin.system.exitProcess(0)
        },"ClipHistory-stop").start()
    }
}
