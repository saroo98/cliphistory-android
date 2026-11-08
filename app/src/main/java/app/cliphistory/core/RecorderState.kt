package app.cliphistory.core

enum class ConnectionStage { MISSING, STOPPED, PERMISSION, DENIED, CONNECTING, CONNECTED, FAILED, UNSUPPORTED, OUTDATED, ROOT_MODE }
enum class RecorderState { MISSING, STOPPED, PERMISSION, DENIED, CONNECTING, LISTENING, RECORDING, PAUSED, ATTENTION, UNSUPPORTED, OUTDATED, ROOT_MODE }

const val SHELL_UID = 2000
const val ROOT_UID = 0
const val MIN_SERVICE_API = 13

/**
 * Classifies a running, authorized service. Only shell-mode services can record.
 *
 * Android's clipboard service only serves a caller that owns the package it names, and no
 * app package belongs to root. A root-started helper reading as "com.android.shell" makes
 * Android clear the user's clipboard instead (reproduced on API 36, 7 Nov 2026), and
 * Android changes credentials per thread, so the helper cannot switch to shell afterwards.
 * Keep these checks: they protect the user's clipboard.
 */
fun serviceStage(apiVersion: Int, uid: Int): ConnectionStage? = when {
    apiVersion < MIN_SERVICE_API -> ConnectionStage.OUTDATED
    uid == ROOT_UID -> ConnectionStage.ROOT_MODE
    uid != SHELL_UID -> ConnectionStage.UNSUPPORTED
    else -> null
}

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
    ConnectionStage.OUTDATED -> RecorderState.OUTDATED
    ConnectionStage.ROOT_MODE -> RecorderState.ROOT_MODE
    ConnectionStage.FAILED -> RecorderState.ATTENTION
    ConnectionStage.CONNECTED -> when {
        hasIssue -> RecorderState.ATTENTION
        paused -> RecorderState.PAUSED
        !active -> RecorderState.ATTENTION
        testPassed -> RecorderState.RECORDING
        else -> RecorderState.LISTENING
    }
}

/** Setup states that the user fixes in the service app, not in ClipHistory. */
val RecorderState.needsServiceSetup: Boolean
    get() = this == RecorderState.MISSING || this == RecorderState.UNSUPPORTED ||
        this == RecorderState.OUTDATED || this == RecorderState.ROOT_MODE

/**
 * What to tell the user while setting up. Sui asks "root or Shell" in its own prompt and can
 * serve ClipHistory as Shell while other apps keep root, so Sui users get Sui steps instead of
 * "start your service with wireless debugging". The root refusal itself stays unchanged.
 */
enum class SetupAdvice { STANDARD, SUI_CHOOSE_SHELL, SUI_SWITCH_TO_SHELL }

fun setupAdvice(state: RecorderState, serviceIsSui: Boolean): SetupAdvice = when {
    !serviceIsSui -> SetupAdvice.STANDARD
    state == RecorderState.PERMISSION -> SetupAdvice.SUI_CHOOSE_SHELL
    state == RecorderState.DENIED || state == RecorderState.ROOT_MODE -> SetupAdvice.SUI_SWITCH_TO_SHELL
    else -> SetupAdvice.STANDARD
}

/** States whose fix is explained under "Rooted phone" in Setup help, which then opens there. */
fun rootedHelpApplies(state: RecorderState, serviceIsSui: Boolean): Boolean =
    state == RecorderState.ROOT_MODE || setupAdvice(state, serviceIsSui) != SetupAdvice.STANDARD
