package app.cliphistory.core

/**
 * Android 11 throws from TextView's long-press when it starts dragging a selection whose
 * drag shadow is empty (Firebase Test Lab, realme RMX3231, 7 Nov 2026). Only that exact
 * framework failure is tolerated; anything else still crashes so it gets reported.
 */
fun isDragShadowFailure(error: Throwable): Boolean =
    error is IllegalStateException && error.message?.contains("Drag shadow dimensions must be positive") == true
