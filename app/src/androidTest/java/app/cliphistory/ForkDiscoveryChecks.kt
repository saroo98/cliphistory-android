package app.cliphistory

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.ConnectionStage
import rikka.shizuku.Shizuku

/** Run only on a disposable emulator with a shell-mode fork and no granted client access. */
class ForkDiscoveryChecks(private val test:Instrumentation) {
    fun run(suite:String,arguments:Bundle):String {
        requireDisposableEmulator(test)
        val context=test.targetContext
        check(context.packageName=="app.cliphistory.validation")
        if(suite=="fork-ambiguous") {
            check(context.packageManager.getLaunchIntentForPackage("com.churan.shizako")!=null)
            check(context.packageManager.getLaunchIntentForPackage("eu.darken.porter")!=null)
            var manager:String?="unchecked"
            test.runOnMainSync { manager=DaemonClient(context).managerPackage() }
            check(manager==null) { "Multiple managers must not select the wrong app: $manager" }
            return "PASS Multiple installed managers require the user to open their chosen app.\n"
        }
        if(suite=="fork-stopped") {
            check(!Shizuku.pingBinder()) { "The fixture service must be stopped" }
            var stage:ConnectionStage?=null
            var manager:String?=null
            test.runOnMainSync { DaemonClient(context).also { stage=it.connectionStage();manager=it.managerPackage() } }
            check(stage==ConnectionStage.STOPPED) { "An installed stopped fork should be recognized, got $stage" }
            check(manager==arguments.getString("expected_manager")) { "Wrong service shortcut: $manager" }
            return "PASS An installed stopped compatible service is recognized.\n"
        }
        val until=SystemClock.elapsedRealtime()+15_000
        while(!Shizuku.pingBinder() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(100)
        check(Shizuku.pingBinder()) { "No compatible service Binder arrived" }
        check(Shizuku.getVersion()>=13 && Shizuku.getUid()==2000) { "The fixture needs API 13+ in shell mode" }
        check(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) { "The fixture must not be pre-authorized" }
        check(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")==null) {
            "The fixture must not expose an official Shizuku launcher"
        }
        var stage:ConnectionStage?=null
        test.runOnMainSync { stage=DaemonClient(context).connectionStage() }
        check(stage==ConnectionStage.PERMISSION) { "A running compatible fork should request permission, got $stage" }
        return "PASS A running compatible shell service is recognized without the official Shizuku launcher.\n"
    }
}
