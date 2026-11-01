package app.cliphistory.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Android can notify again when it classifies the same copy, even after history is cleared. */
class ClipboardCopyTracker {
    // One keyed signature in RAM, never plaintext or a persisted secret verifier.
    private val key=SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes),"HmacSHA256")
    private var lastTimestamp=0L
    private var lastSignature=byteArrayOf()
    @Synchronized fun isRepeat(timestamp:Long,text:String,sensitive:Boolean):Boolean {
        if(timestamp<=0) {
            lastTimestamp=0;lastSignature.fill(0);lastSignature=byteArrayOf();return false
        }
        val bytes=text.toByteArray(Charsets.UTF_8)
        val signature=try {
            Mac.getInstance("HmacSHA256").run {
                init(key);update(if(sensitive)1.toByte() else 0.toByte());doFinal(bytes)
            }
        } finally { bytes.fill(0) }
        val repeated=timestamp==lastTimestamp && MessageDigest.isEqual(signature,lastSignature)
        lastSignature.fill(0);lastSignature=signature;lastTimestamp=timestamp
        return repeated
    }
}
