package app.cliphistory

import app.cliphistory.core.isDragShadowFailure
import org.junit.Assert.*
import org.junit.Test

class PlatformQuirksTest {
    @Test fun onlyTheAndroidElevenDragShadowFailureIsTolerated() {
        assertTrue(isDragShadowFailure(IllegalStateException("Drag shadow dimensions must be positive")))
        assertFalse(isDragShadowFailure(IllegalStateException("Something else")))
        assertFalse(isDragShadowFailure(IllegalArgumentException("Drag shadow dimensions must be positive")))
        assertFalse(isDragShadowFailure(IllegalStateException()))
    }
}
