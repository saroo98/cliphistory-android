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

/** Pre-Android 14 package replacement also uses USER_REQUESTED. Recognize only
 * verified AOSP descriptions, with replacement tied to this exact package.
 * An unknown user-requested exit still fails closed. */
fun isDeliberateUserStop(userRequested: Boolean, description: String?, packageName: String? = null): Boolean =
    userRequested && description != "[REMOVE TASK] remove task" && description != "remove task" &&
        (packageName == null || description != "stop $packageName due to installPackageLI")

/** Only a deliberate owner stop after the last explicit activation blocks recovery. */
fun isNewUserStop(exitTime: Long, userRequested: Boolean, explicitStartTime: Long, acknowledgedExit: Long,
    description: String? = null, packageName: String? = null): Boolean =
    isDeliberateUserStop(userRequested, description, packageName) && exitTime > explicitStartTime && exitTime > acknowledgedExit
