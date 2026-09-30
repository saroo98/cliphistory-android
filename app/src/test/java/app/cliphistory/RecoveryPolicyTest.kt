package app.cliphistory

import app.cliphistory.core.*
import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    private val ready = RecoveryOptions(setupCompleted = true, activatedBoot = 12)
    @Test fun incompleteSetupNeverAutomaticallyStarts() { assertFalse(mayRecover(RecoveryOptions(), 12)) }
    @Test fun deliberateStopWinsOverEveryBackgroundTrigger() {
        assertFalse(mayRecover(ready.copy(explicitlyStopped = true), 12, true))
    }
    @Test fun recoveryOffDoesNotBindAutomatically() { assertFalse(mayRecover(ready.copy(automatic = false), 12)) }
    @Test fun rebootOffStillAllowsSameBootReconnect() {
        val off = ready.copy(afterBoot = false)
        assertTrue(mayRecover(off, 12)); assertFalse(mayRecover(off, 13))
    }
    @Test fun rebootEnabledAllowsLaterBoot() { assertTrue(mayRecover(ready, 13)) }
    @Test fun unknownBootFailsClosedUntilExplicitSessionActivation() {
        assertFalse(mayRecover(ready, null)); assertTrue(mayRecover(ready, null, true))
    }
    @Test fun explicitStartIsIndependentOfRecoverySwitches() {
        assertTrue(mayConnect(ready.copy(automatic = false, explicitlyStopped = true), null, true, false))
        assertFalse(mayConnect(ready.copy(explicitlyStopped = true), 12, false, false))
    }
    @Test fun onlyNewUserRequestedExitsBlockRecovery() {
        assertTrue(isNewUserStop(30, true, 20, 25))
        assertFalse(isNewUserStop(30, false, 20, 25))
        assertFalse(isNewUserStop(30, true, 30, 0))
        assertFalse(isNewUserStop(30, true, 20, 30))
    }
    @Test fun verifiedRecentsRemovalDoesNotStopTheIndependentRecorder() {
        assertFalse(isNewUserStop(30, true, 20, 0,"[REMOVE TASK] remove task"))
        assertFalse(isNewUserStop(30, true, 20, 0,"remove task"))
        assertTrue(isNewUserStop(30, true, 20, 0,"[stop app] fully stop app by user request"))
        assertTrue(isNewUserStop(30, true, 20, 0,null))
        assertTrue(isNewUserStop(30, true, 20, 0,"unrecognized OEM remove task exit"))
    }
}
