package app.cliphistory

import app.cliphistory.core.*
import org.junit.Assert.*
import org.junit.Test

class RecorderStateTest {
    @Test fun listeningIsNotVerifiedRecording() {
        assertEquals(RecorderState.LISTENING, recorderState(ConnectionStage.CONNECTED, false, true, false, false))
        assertEquals(RecorderState.RECORDING, recorderState(ConnectionStage.CONNECTED, false, true, true, false))
    }
    @Test fun disconnectedNeverDisplaysStaleActiveStatus() {
        assertEquals(RecorderState.STOPPED, recorderState(ConnectionStage.STOPPED, false, true, true, false))
    }
    @Test fun errorsOverridePauseAndActiveFlags() {
        assertEquals(RecorderState.ATTENTION, recorderState(ConnectionStage.CONNECTED, true, true, true, true))
    }
    @Test fun pauseIsNeutralAndNeverActive() {
        assertEquals(RecorderState.PAUSED, recorderState(ConnectionStage.CONNECTED, true, false, true, false))
    }
    @Test fun unavailableListenerDoesNotBecomeRecordingAfterOldTestPass() {
        assertEquals(RecorderState.ATTENTION, recorderState(ConnectionStage.CONNECTED, false, false, true, false))
    }
    @Test fun setupStatesStayDistinct() {
        assertEquals(RecorderState.MISSING, recorderState(ConnectionStage.MISSING))
        assertEquals(RecorderState.PERMISSION, recorderState(ConnectionStage.PERMISSION))
        assertEquals(RecorderState.DENIED, recorderState(ConnectionStage.DENIED))
        assertEquals(RecorderState.CONNECTING, recorderState(ConnectionStage.CONNECTING))
        assertEquals(RecorderState.UNSUPPORTED, recorderState(ConnectionStage.UNSUPPORTED))
    }
    @Test fun setupHelpPointsAtTheNextStep() {
        assertEquals(1, nextSetupStep(ConnectionStage.MISSING, false))
        assertEquals(2, nextSetupStep(ConnectionStage.STOPPED, false))
        assertEquals(3, nextSetupStep(ConnectionStage.STOPPED, true))
        listOf(ConnectionStage.PERMISSION, ConnectionStage.DENIED, ConnectionStage.CONNECTING, ConnectionStage.FAILED)
            .forEach { assertEquals(4, nextSetupStep(it, true)) }
        assertEquals(SETUP_DONE, nextSetupStep(ConnectionStage.CONNECTED, false))
        listOf(ConnectionStage.UNSUPPORTED, ConnectionStage.OUTDATED, ConnectionStage.ROOT_MODE)
            .forEach { assertNull(nextSetupStep(it, true)) }
    }
}
