package app.cliphistory

import android.app.Instrumentation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    val finished=CountDownLatch(1)
    var confirmed=false
    var issue=""
    test.runOnMainSync {
        (test.targetContext.applicationContext as ClipApplication).daemon.stopRecording {
            confirmed=it.getBoolean("ok");issue=it.getString("issue","").orEmpty();finished.countDown()
        }
    }
    check(finished.await(12,TimeUnit.SECONDS)){"Validation recorder stop timed out"}
    val name=test.targetContext.packageName+":clipboard"
    val deadline=SystemClock.elapsedRealtime()+12_000
    while(true) {
        val exists=test.uiAutomation.executeShellCommand("ps -A -o NAME").use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { input -> input.lineSequence().any { it.trim()==name } }
        }
        if(!exists) {
            check(confirmed || (test.targetContext.applicationContext as ClipApplication).daemon.preferences.options().explicitlyStopped) { "Validation stop was not saved: $issue" }
            return
        }
        check(SystemClock.elapsedRealtime()<deadline){"Validation helper still writing; fixture not seeded"}
        SystemClock.sleep(60)
    }
}
