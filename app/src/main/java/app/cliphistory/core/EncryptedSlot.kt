package app.cliphistory.core

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encryption surrounds the existing snapshot and durable framing. No plaintext writes. */
class EncryptedSlot(
    private val slot: Slot, dataKey: ByteArray, storageId: ByteArray,
    applicationId: String, slotId: Int, private val allowLegacy: Boolean = false
) : Slot {
    private var key: SecretKeySpec? = SecretKeySpec(dataKey, "AES")
    private val associated: ByteArray
    init {
        require(dataKey.size == 32 && storageId.size == 16 && slotId in 0..1)
        val owner = applicationId.toByteArray(Charsets.UTF_8)
        require(owner.size in 1..256)
        associated = ByteBuffer.allocate(8 + 16 + 1 + 4 + owner.size)
            .putInt(MAGIC).putInt(VERSION).put(storageId).put(slotId.toByte())
            .putInt(owner.size).put(owner).array()
    }
    override fun read(): ByteArray {
        val bytes = slot.read()
        if (bytes.isEmpty()) return bytes
        if (!isEncrypted(bytes)) {
            if (allowLegacy) { SnapshotCodec.decode(bytes); return bytes }
            throw StoreException("ENCRYPTED_HISTORY_REQUIRED")
        }
        if (bytes.size !in (OVERHEAD + 1)..MAX_ENCRYPTED_BYTES) throw StoreException("ENCRYPTED_SIZE_INVALID")
        val input = ByteBuffer.wrap(bytes)
        input.int
        if (input.int != VERSION) throw StoreException("UNSUPPORTED_ENCRYPTION_VERSION")
        val nonce = ByteArray(12).also(input::get)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key ?: throw StoreException("HISTORY_KEY_CLOSED"), GCMParameterSpec(128, nonce))
                updateAAD(associated)
                doFinal(bytes, 20, bytes.size - 20)
            }
        } catch (_: GeneralSecurityException) { throw StoreException("HISTORY_AUTHENTICATION_FAILED") }
    }
    override fun writeAndSync(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_SNAPSHOT_BYTES) throw StoreException("SNAPSHOT_SIZE_INVALID")
        val nonce = ByteArray(12).also(random::nextBytes)
        val encrypted = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key ?: throw StoreException("HISTORY_KEY_CLOSED"), GCMParameterSpec(128, nonce))
                updateAAD(associated); doFinal(bytes)
            }
        } catch (_: GeneralSecurityException) { throw StoreException("HISTORY_ENCRYPTION_FAILED") }
        val envelope = ByteBuffer.allocate(20 + encrypted.size)
            .putInt(MAGIC).putInt(VERSION).put(nonce).put(encrypted).array()
        slot.writeAndSync(envelope)
    }
    override fun close() { key = null; slot.close() }
    companion object {
        private const val MAGIC = 0x434C4531 // CLE1
        private const val VERSION = 1
        const val OVERHEAD = 36 // version header + nonce + authentication tag
        const val MAX_ENCRYPTED_BYTES = MAX_SNAPSHOT_BYTES + OVERHEAD
        private val random = SecureRandom()
        fun isEncrypted(bytes: ByteArray) = bytes.size >= 4 && ByteBuffer.wrap(bytes, 0, 4).int == MAGIC
    }
}
