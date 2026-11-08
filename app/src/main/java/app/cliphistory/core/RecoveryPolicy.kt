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

/** How long after an explicit Connect a connection waiting on the service app still counts as in progress. */
const val CONNECT_IN_PROGRESS_MS = 10 * 60_000L

/**
 * A force stop that lands while an explicit Connect is waiting for the user's answer in the
 * service app (its permission prompt, or a switch away from root or Deny) is that service app
 * restarting ClipHistory to apply the answer. Sui does this after "Always (Shell)" and after
 * ClipHistory is switched to Shell. No recorder was running, so it is not the user's stop: the
 * Connect continues. Any other force stop, including one after a failed or finished Connect,
 * still counts as the user's stop.
 */
fun isRestartDuringConnect(exitTime: Long, explicitStartTime: Long, waitingForService: Boolean): Boolean =
    waitingForService && explicitStartTime > 0 && exitTime > explicitStartTime &&
        exitTime - explicitStartTime <= CONNECT_IN_PROGRESS_MS
