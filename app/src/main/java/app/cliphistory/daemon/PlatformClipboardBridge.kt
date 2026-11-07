package app.cliphistory.daemon

import android.content.ClipData
import android.os.*
import android.os.Process
import app.cliphistory.core.ClipboardArguments
import app.cliphistory.core.ClipboardCopyTracker
import app.cliphistory.core.HistoryClock
import app.cliphistory.core.HistoryTime
import app.cliphistory.core.Entry
import app.cliphistory.core.Expiry
import app.cliphistory.core.sensitiveMatch
import app.cliphistory.core.MAX_TEXT_BYTES
import app.cliphistory.core.StoreException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Hidden APIs stay exclusively inside the Shizuku shell process. */
class PlatformClipboardBridge(
    private val userId: Int,
    private val onCopy: (CopiedText) -> Unit,
    private val canRead: () -> Boolean,
    private val captureSensitive: () -> Boolean,
    private val sensitiveEntries: () -> List<Entry>,
    private val clock: HistoryClock,
    private val onFailure: (String) -> Unit
) : java.io.Closeable {
    data class CopiedText(val text: String?, val sensitive: Boolean, val oversized: Boolean, val time: Long, val capturedAt: HistoryTime, val inheritedExpiry: Expiry? = null)
    private lateinit var clipboard: Any
    private lateinit var systemBinder: IBinder
    private lateinit var listener: Any
    private lateinit var readMethod: Method
    private lateinit var removeMethod: Method
    private val copies=ClipboardCopyTracker()
    @Volatile var registered = false; private set
    var signature: String = "Not connected"; private set
    private val death = IBinder.DeathRecipient { registered = false; onFailure("SYSTEM_CLIPBOARD_DISCONNECTED") }

    fun start() {
        // Shell only: as root, Android clears the clipboard instead of reading it (see serviceStage).
        check(Process.myUid() == app.cliphistory.core.SHELL_UID) { "WIRELESS_SHIZUKU_SHELL_REQUIRED" }
        val serviceManager = Class.forName("android.os.ServiceManager")
        systemBinder = serviceManager.getMethod("getService", String::class.java).invoke(null, "clipboard") as? IBinder
            ?: error("SYSTEM_CLIPBOARD_UNAVAILABLE")
        val iface = Class.forName("android.content.IClipboard")
        val stub = Class.forName("android.content.IClipboard\$Stub")
        clipboard = stub.getMethod("asInterface", IBinder::class.java).invoke(null, systemBinder)
        val callbackClass = Class.forName(ClipboardArguments.LISTENER)
        val listenerBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) { reply?.writeString(ClipboardArguments.LISTENER); return true }
                if (code == IBinder.FIRST_CALL_TRANSACTION) {
                    if (getCallingUid() != 1000) throw SecurityException("SYSTEM_CALLBACK_REQUIRED")
                    data.enforceInterface(ClipboardArguments.LISTENER)
                    val identity = clearCallingIdentity()
                    try { readImmediately() } finally { restoreCallingIdentity(identity) }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        listener = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> listenerBinder
                "dispatchPrimaryClipChanged" -> { readImmediately(); null }
                "toString" -> "ClipHistoryPrimaryClipListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> throw UnsupportedOperationException("UNKNOWN_CALLBACK_METHOD")
            }
        }
        listenerBinder.attachInterface(listener as IInterface, ClipboardArguments.LISTENER)
        signature=iface.methods.filter { it.name in setOf("getPrimaryClip","addPrimaryClipChangedListener","removePrimaryClipChangedListener") }
            .joinToString("; ") { it.name+"("+it.parameterTypes.joinToString(",") { type -> type.simpleName }+")" }.take(1200)
        readMethod = select(iface, "getPrimaryClip", false)
        val add = select(iface, "addPrimaryClipChangedListener", true)
        removeMethod = select(iface, "removePrimaryClipChangedListener", true)
        signature = "read=" + readMethod.parameterTypes.joinToString(",") { it.simpleName } + "; listen=" + add.parameterTypes.joinToString(",") { it.simpleName }
        systemBinder.linkToDeath(death, 0)
        try {
            // Probe access without importing old clipboard text into the archive.
            check(canRead()) { "OWNER_STOPPED" }
            // Remember it before subscribing: its pending classification is not a new copy.
            rememberCurrentCopy(invoke(readMethod) as? ClipData)
            invoke(add)
            registered = true
        } catch (t: Throwable) {
            try { invoke(removeMethod) } catch (_: Throwable) {}
            registered = false
            systemBinder.unlinkToDeath(death, 0)
            throw t
        }
    }

    private fun select(iface: Class<*>, name: String, needsListener: Boolean): Method {
        val candidates = iface.methods.filter { it.name == name }.sortedByDescending { it.parameterCount }
        for (method in candidates) {
            if ((method.parameterTypes.firstOrNull()?.name == ClipboardArguments.LISTENER) != needsListener) continue
            try { ClipboardArguments.resolve(method.parameterTypes.map { it.name }, listener, userId); return method }
            catch (_: java.io.IOException) {}
        }
        throw StoreException("UNSUPPORTED_CLIPBOARD_SIGNATURE_$name")
    }
    private fun invoke(method: Method): Any? {
        val identity = Binder.clearCallingIdentity()
        try { return method.invoke(clipboard, *ClipboardArguments.resolve(method.parameterTypes.map { it.name }, listener, userId)) }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    private fun rememberCurrentCopy(clip:ClipData?) {
        if(clip==null)return
        val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        if (sensitive && !captureSensitive())return
        val raw = if (clip.itemCount == 1) clip.getItemAt(0).text else null
        if(raw!=null && raw.length<=MAX_TEXT_BYTES)copies.isRepeat(clip.description.timestamp,raw.toString(),sensitive)
    }
    private fun readImmediately() {
        try {
            if(!canRead())return
            // Keep the classification snapshot throughout the Binder read. The
            // worker may expire its copy while getPrimaryClip is in flight.
            val retained=sensitiveEntries()
            val capturedAt=clock.now()
            val time=System.currentTimeMillis()
            val clip = invoke(readMethod) as? ClipData ?: return
            val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (sensitive && !captureSensitive()) { onCopy(CopiedText(null, true, false, time,capturedAt)); return }
            // Do not coerce URIs, read providers, or resolve HTML/Intents.
            val raw = if (clip.itemCount == 1) clip.getItemAt(0).text else null
            if (raw != null && raw.length > MAX_TEXT_BYTES) { onCopy(CopiedText(null, sensitive, true, time,capturedAt)); return }
            val text=raw?.toString()
            // Source metadata can change without another copy. Track the source flag,
            // not inherited expiry, so a late notification cannot restore deleted text.
            if(text!=null && copies.isRepeat(clip.description.timestamp,text,sensitive))return
            val inherited=text?.let { sensitiveMatch(retained,it,capturedAt) }
            onCopy(CopiedText(text, sensitive || inherited!=null, false, time,capturedAt,inherited?.expiry))
        } catch (_: Throwable) { onFailure("CLIPBOARD_READ_FAILED") }
    }
    override fun close() {
        if (registered) try { invoke(removeMethod) } catch (_: Throwable) {}
        registered = false
        if (::systemBinder.isInitialized) try { systemBinder.unlinkToDeath(death, 0) } catch (_: Throwable) {}
    }
}
