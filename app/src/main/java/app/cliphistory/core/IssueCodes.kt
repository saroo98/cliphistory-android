package app.cliphistory.core

private val ISSUE_CODE = Regex("^[A-Z][A-Z0-9_]{2,79}$")

/**
 * Turns a failure into a constant code for status and diagnostics. Only fixed codes
 * written by this app are passed through; free exception text could contain user data.
 */
fun issueCode(t: Throwable): String = when (t) {
    is java.lang.reflect.InvocationTargetException -> t.targetException?.let(::issueCode) ?: "PLATFORM_INVOCATION_FAILED"
    is StoreException -> t.reason
    is SecurityException -> "ANDROID_PERMISSION_DENIED"
    is IllegalStateException, is IllegalArgumentException ->
        t.message?.takeIf { ISSUE_CODE.matches(it) } ?: "OPERATION_FAILED_${t.javaClass.simpleName}"
    else -> "OPERATION_FAILED_${t.javaClass.simpleName}"
}

/** Connection failures the user can act on, each with its own explanation. */
enum class IssueKind { NONE, SHELL_MODE_REQUIRED, MAIN_PROFILE_REQUIRED, CLIPBOARD_NOT_SUPPORTED, OTHER }

fun issueKind(code: String?): IssueKind = when {
    code.isNullOrEmpty() -> IssueKind.NONE
    code == "START_SHIZUKU_USING_WIRELESS_DEBUGGING" || code == "WIRELESS_SHIZUKU_SHELL_REQUIRED" -> IssueKind.SHELL_MODE_REQUIRED
    code == "PRIMARY_PHONE_PROFILE_REQUIRED" -> IssueKind.MAIN_PROFILE_REQUIRED
    code == "SYSTEM_CLIPBOARD_UNAVAILABLE" || code.startsWith("UNSUPPORTED_CLIPBOARD_SIGNATURE") -> IssueKind.CLIPBOARD_NOT_SUPPORTED
    else -> IssueKind.OTHER
}
