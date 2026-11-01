package app.cliphistory

import android.app.Instrumentation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import app.cliphistory.client.PrivateHistory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test-only encryption uses the production envelope after proving the helper has stopped. */
fun writeFixturePayload(test:Instrumentation,bytes:ByteArray) {
    stopRecorderForFixture(test)
    val context=test.targetContext
    val material=PrivateHistory.prepare(context) { /* stopRecorderForFixture already proved exclusive ownership. */ }
    val (a,b)=PrivateHistory.openForDaemon(context)
    listOf(a,b).forEachIndexed { index,descriptor ->
        PrivateHistory.encryptedSlot(context,descriptor,index,material).use { it.writeAndSync(bytes) }
    }
    PrivateHistory.invalidate()
}

/** Seeding history or changing the clipboard is permitted only on disposable emulators. */
fun requireDisposableEmulator(test:Instrumentation) {
    check(BuildConfig.DEBUG && (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))) {
        "Synthetic history and clipboard fixtures require a disposable emulator"
    }
    check(test.targetContext.packageName==BuildConfig.APPLICATION_ID) { "Fixture target does not match the test build" }
}

/** No test fixture may race the independently running single writer. */
fun stopRecorderForFixture(test:Instrumentation) {
    requireDisposableEmulator(test)
    val name=test.targetContext.packageName+":clipboard"
    fun exists()=test.uiAutomation.executeShellCommand("ps -A -o NAME").use { fd ->
        ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { input -> input.lineSequence().any { it.trim()==name } }
    }
    var stopped=false
    test.runOnMainSync { stopped=(test.targetContext.applicationContext as ClipApplication).daemon.preferences.options().explicitlyStopped }
    // Already-stopped fixtures need no new Stop notification. Reissuing it while
    // a read-error dialog is open would trigger an unrelated automatic reload.
    if(stopped && !exists())return
    val finished=CountDownLatch(1)
    var confirmed=false
    var issue=""
    test.runOnMainSync {
        (test.targetContext.applicationContext as ClipApplication).daemon.stopRecording {
            confirmed=it.getBoolean("ok");issue=it.getString("issue","").orEmpty();finished.countDown()
        }
    }
    check(finished.await(12,TimeUnit.SECONDS)){"Validation recorder stop timed out"}
    val deadline=SystemClock.elapsedRealtime()+12_000
    while(true) {
        if(!exists()) {
            check(confirmed || (test.targetContext.applicationContext as ClipApplication).daemon.preferences.options().explicitlyStopped) { "Validation stop was not saved: $issue" }
            return
        }
        check(SystemClock.elapsedRealtime()<deadline){"Validation helper still writing; fixture not seeded"}
        SystemClock.sleep(60)
    }
}
