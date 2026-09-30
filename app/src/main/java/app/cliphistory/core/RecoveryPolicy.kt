package app.cliphistory.core

data class RecoveryOptions(
    val setupCompleted: Boolean = false,
    val automatic: Boolean = true,
    val afterBoot: Boolean = true,
    val explicitlyStopped: Boolean = false,
    val activatedBoot: Int = -1
)

/** Unknown boot identity is never interpreted as permission to resume after reboot. */
fun mayRecover(options: RecoveryOptions, boot: Int?, explicitlyActivatedThisSession: Boolean = false): Boolean =
    options.setupCompleted && options.automatic && !options.explicitlyStopped &&
        (explicitlyActivatedThisSession || (boot != null &&
            (options.afterBoot || options.activatedBoot == boot)))

fun mayConnect(options: RecoveryOptions, boot: Int?, explicit: Boolean, explicitlyActivatedThisSession: Boolean): Boolean =
    explicit || mayRecover(options, boot, explicitlyActivatedThisSession)

/** Public exit descriptions are not stable. Recognize only the verified AOSP
 * task-removal descriptions; an unknown user-requested exit still fails closed. */
fun isDeliberateUserStop(userRequested: Boolean, description: String?): Boolean =
    userRequested && description != "[REMOVE TASK] remove task" && description != "remove task"

/** Only a deliberate owner stop after the last explicit activation blocks recovery. */
fun isNewUserStop(exitTime: Long, userRequested: Boolean, explicitStartTime: Long, acknowledgedExit: Long,
    description: String? = null): Boolean =
    isDeliberateUserStop(userRequested, description) && exitTime > explicitStartTime && exitTime > acknowledgedExit
