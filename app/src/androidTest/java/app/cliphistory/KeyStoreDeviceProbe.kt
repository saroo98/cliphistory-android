package app.cliphistory

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import app.cliphistory.client.HistoryKeys
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory

/** Explicitly invoked only against the separate validation package. */
class KeyStoreDeviceProbe(private val test: Instrumentation) {
    fun run(): String {
        val context = test.targetContext
        check(BuildConfig.DEBUG && context.packageName == "app.cliphistory.validation")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        fun probe(strongBox: Boolean): String {
            val alias = context.packageName + ".probe." + UUID.randomUUID()
            check(!store.containsAlias(alias))
            return try {
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false)
                    .setIsStrongBoxBacked(strongBox).build()
                val key = KeyGenerator.getInstance("AES", "AndroidKeyStore").run { init(spec); generateKey() }
                val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore")
                    .getKeySpec(key, KeyInfo::class.java) as KeyInfo
                @Suppress("DEPRECATION")
                "success; hardware=${info.isInsideSecureHardware}"
            } catch (error: Exception) {
                generateSequence(error as Throwable?) { it.cause }.take(5)
                    .joinToString(" -> ") {
                        val code = if (Build.VERSION.SDK_INT >= 33 && it is android.security.KeyStoreException)
                            " code=${it.numericErrorCode}" else ""
                        it.javaClass.name + code + ": " + (it.message ?: "")
                    }
            } finally {
                if (store.containsAlias(alias)) store.deleteEntry(alias)
            }
        }
        val strongAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val strong = if (strongAvailable) probe(true) else "not advertised"
        val standard = probe(false)
        val detail = "StrongBox advertised=$strongAvailable; StrongBox=$strong; default=$standard"
        try {
            check(HistoryKeys.createPending(context).bytes.size == 32)
            check(HistoryKeys.load(context).bytes.size == 32)
        } catch (error: Exception) {
            throw IllegalStateException("$detail; history key creation failed", error)
        }
        return "PASS history wrapping key creation and reload. $detail\n"
    }
}
