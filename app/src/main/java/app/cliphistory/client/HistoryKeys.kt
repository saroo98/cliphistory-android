package app.cliphistory.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import app.cliphistory.core.StoreException
import java.io.File
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Only the owning application UID calls Keystore. The recorder receives a RAM-only data key. */
object HistoryKeys {
    data class Material(val bytes: ByteArray, val storageId: ByteArray, val pending: Boolean, val security: String)
    private data class Stamp(val inode: Long, val size: Long, val modified: Long, val nanos: Long)
    private var cachedPath = ""
    private var cachedStamp: Stamp? = null
    private var cached: Material? = null
    private const val MAGIC = 0x434C4B31 // CLK1
    private const val VERSION = 1
    private const val RECORD_SIZE = 69 // key + storage identity + phase + GCM tag
    private const val FILE_SIZE = 20 + RECORD_SIZE
    private fun file(context: Context) = File(context.noBackupFilesDir, "history-key.bin")
    private fun alias(context: Context) = context.packageName + ".history-wrap.v1"
    private fun stamp(file: File) = if (!file.exists()) null else Os.stat(file.path).let {
        Stamp(it.st_ino, it.st_size, it.st_mtim.tv_sec, it.st_mtim.tv_nsec)
    }
    // A lone .new file never completed AtomicFile's durable publication. History
    // cannot have been encrypted yet; prepare checks for ciphertext before retrying.
    fun exists(context: Context) = file(context).exists() || File(file(context).path + ".bak").exists()
    private fun aad(context: Context) = ByteBuffer.allocate(8 + context.packageName.toByteArray(Charsets.UTF_8).size)
        .putInt(MAGIC).putInt(VERSION).put(context.packageName.toByteArray(Charsets.UTF_8)).array()
    private fun wrappingKey(context: Context, create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias(context), null) as? SecretKey)?.let { return it }
        if (!create) throw StoreException("HISTORY_KEY_UNAVAILABLE")
        fun generate(strong: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(alias(context), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(strong).build()
            return KeyGenerator.getInstance("AES", "AndroidKeyStore").run { init(spec); generateKey() }
        }
        return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try { generate(true) } catch (error: ProviderException) {
                val unsupported = error is StrongBoxUnavailableException ||
                    (Build.VERSION.SDK_INT >= 33 &&
                        (error.cause as? android.security.KeyStoreException)?.numericErrorCode ==
                        android.security.KeyStoreException.ERROR_UNIMPLEMENTED)
                if (!unsupported) throw error
                generate(false)
            }
        } else generate(false)
    }
    @Suppress("DEPRECATION")
    private fun security(key: SecretKey) = try {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if(Build.VERSION.SDK_INT<31) {
            if(info.isInsideSecureHardware)"Secure hardware (type not reported)" else "Software"
        } else when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Trusted environment"
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software"
            else -> "Not confirmed"
        }
    } catch (_: Exception) { "Not confirmed" }
    @Synchronized fun load(context: Context): Material {
        val target = file(context)
        val current = stamp(target)
        cached?.takeIf { cachedPath == target.path && cachedStamp == current && current != null &&
            !File(target.path + ".bak").exists() && !File(target.path + ".new").exists() }?.let { return it }
        try {
            val encoded = AtomicFile(target).openRead().use { input ->
                val data = ByteArray(FILE_SIZE)
                try { DataInputStream(input).readFully(data) }
                catch (_:EOFException) { throw StoreException("HISTORY_KEY_RECORD_INVALID") }
                if (input.read()!=-1) throw StoreException("HISTORY_KEY_RECORD_INVALID")
                data
            }
            val header = ByteBuffer.wrap(encoded)
            if (header.int != MAGIC || header.int != VERSION) throw StoreException("HISTORY_KEY_RECORD_INVALID")
            val nonce = ByteArray(12).also(header::get)
            val wrap = wrappingKey(context, false)
            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, wrap, GCMParameterSpec(128, nonce)); updateAAD(aad(context))
                doFinal(encoded, 20, RECORD_SIZE)
            }
            try {
                if (plain.size != 53 || plain[52].toInt() !in 0..1) throw StoreException("HISTORY_KEY_RECORD_INVALID")
                return Material(plain.copyOfRange(0, 32), plain.copyOfRange(32, 48), plain[52].toInt() == 0, security(wrap)).also {
                    // Bytes 48..51 bind the encrypted record to its schema, too.
                    if (ByteBuffer.wrap(plain, 48, 4).int != VERSION) throw StoreException("HISTORY_KEY_RECORD_INVALID")
                    cached = it; cachedPath = target.path; cachedStamp = stamp(target)
                }
            } finally { plain.fill(0) }
        } catch (error: StoreException) { throw error }
        catch (_: Exception) { throw StoreException("HISTORY_KEY_UNAVAILABLE") }
    }
    @Synchronized fun createPending(context: Context): Material {
        if (exists(context)) return load(context)
        val random = SecureRandom()
        val wrap = try { wrappingKey(context, true) } catch (_: Exception) { throw StoreException("HISTORY_KEY_UNAVAILABLE") }
        val material = Material(ByteArray(32).also(random::nextBytes), ByteArray(16).also(random::nextBytes), true, security(wrap))
        persist(context, material, wrap)
        return load(context)
    }
    @Synchronized fun complete(context: Context, material: Material): Material {
        if (!material.pending) return material
        persist(context, material.copy(pending = false), wrappingKey(context, false))
        return load(context)
    }
    private fun persist(context: Context, material: Material, wrap: SecretKey) {
        val plain = ByteBuffer.allocate(53).put(material.bytes).put(material.storageId).putInt(VERSION)
            .put(if (material.pending) 0.toByte() else 1.toByte()).array()
        val encoded = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrap); cipher.updateAAD(aad(context))
            val encrypted = cipher.doFinal(plain)
            check(cipher.iv.size == 12 && encrypted.size == RECORD_SIZE)
            ByteBuffer.allocate(FILE_SIZE).putInt(MAGIC).putInt(VERSION).put(cipher.iv).put(encrypted).array()
        } finally { plain.fill(0) }
        val atomic = AtomicFile(file(context))
        val output = atomic.startWrite()
        try {
            output.write(encoded); output.fd.sync()
            atomic.finishWrite(output)
            Os.chmod(file(context).path, 384)
            val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
            cached = null; cachedStamp = null; cachedPath = ""
        } catch (_: Exception) { atomic.failWrite(output); throw StoreException("HISTORY_KEY_NOT_SAVED") }
    }
    @Synchronized fun invalidate() { cached = null; cachedStamp = null; cachedPath = "" }
}
