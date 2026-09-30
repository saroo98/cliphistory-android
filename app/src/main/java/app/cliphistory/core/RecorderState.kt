package app.cliphistory.core

enum class ConnectionStage { MISSING, STOPPED, PERMISSION, DENIED, CONNECTING, CONNECTED, FAILED, UNSUPPORTED }
enum class RecorderState { MISSING, STOPPED, PERMISSION, DENIED, CONNECTING, LISTENING, RECORDING, PAUSED, ATTENTION, UNSUPPORTED }

/** Connection state wins over stale data from the previous helper process. */
fun recorderState(
    connection: ConnectionStage,
    paused: Boolean = false,
    active: Boolean = false,
    testPassed: Boolean = false,
    hasIssue: Boolean = false
): RecorderState = when (connection) {
    ConnectionStage.MISSING -> RecorderState.MISSING
    ConnectionStage.STOPPED -> RecorderState.STOPPED
    ConnectionStage.PERMISSION -> RecorderState.PERMISSION
    ConnectionStage.DENIED -> RecorderState.DENIED
    ConnectionStage.CONNECTING -> RecorderState.CONNECTING
    ConnectionStage.UNSUPPORTED -> RecorderState.UNSUPPORTED
    ConnectionStage.FAILED -> RecorderState.ATTENTION
    ConnectionStage.CONNECTED -> when {
        hasIssue -> RecorderState.ATTENTION
        paused -> RecorderState.PAUSED
        !active -> RecorderState.ATTENTION
        testPassed -> RecorderState.RECORDING
        else -> RecorderState.LISTENING
    }
}
