package app.cliphistory

import app.cliphistory.core.*
import org.junit.Assert.*
import org.junit.Test

/** Which running services can record, and what the user is told when one can't. */
class ServiceModeTest {
    @Test fun shellServiceWithCurrentApiCanRecord() {
        assertNull(serviceStage(13, 2000))
        assertNull(serviceStage(14, 2000))
    }
    @Test fun rootServiceIsNamedSoTheUserKnowsWhatToChange() {
        // Root reads make Android clear the clipboard, so root must stay refused.
        assertEquals(ConnectionStage.ROOT_MODE, serviceStage(13, 0))
        assertEquals(RecorderState.ROOT_MODE, recorderState(ConnectionStage.ROOT_MODE))
    }
    @Test fun outdatedServiceIsReportedBeforeItsUserId() {
        assertEquals(ConnectionStage.OUTDATED, serviceStage(12, 2000))
        assertEquals(ConnectionStage.OUTDATED, serviceStage(12, 0))
        assertEquals(RecorderState.OUTDATED, recorderState(ConnectionStage.OUTDATED))
    }
    @Test fun otherUserIdsStayUnsupported() {
        for (uid in listOf(1000, 10123, -1)) assertEquals(ConnectionStage.UNSUPPORTED, serviceStage(13, uid))
    }
    @Test fun setupProblemsOfferSetupHelp() {
        val setup = RecorderState.values().filter { it.needsServiceSetup }.toSet()
        assertEquals(setOf(RecorderState.MISSING, RecorderState.UNSUPPORTED, RecorderState.OUTDATED, RecorderState.ROOT_MODE), setup)
    }
}
