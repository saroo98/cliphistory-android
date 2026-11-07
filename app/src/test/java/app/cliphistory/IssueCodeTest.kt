package app.cliphistory

import app.cliphistory.core.*
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class IssueCodeTest {
    @Test fun fixedCodesFromChecksReachTheUser() {
        val profile = runCatching { check(false) { "PRIMARY_PHONE_PROFILE_REQUIRED" } }.exceptionOrNull()!!
        assertEquals("PRIMARY_PHONE_PROFILE_REQUIRED", issueCode(profile))
        assertEquals("SYSTEM_CLIPBOARD_UNAVAILABLE", issueCode(IllegalStateException("SYSTEM_CLIPBOARD_UNAVAILABLE")))
        assertEquals("INVALID_PAGE_REQUEST", issueCode(IllegalArgumentException("INVALID_PAGE_REQUEST")))
        assertEquals("ENCRYPTION_IDENTITY_CHANGED", issueCode(StoreException("ENCRYPTION_IDENTITY_CHANGED")))
    }
    @Test fun freeTextIsNeverPassedThrough() {
        // Exception text could contain clipboard or file content.
        assertEquals("OPERATION_FAILED_IllegalStateException", issueCode(IllegalStateException("secret pasted text")))
        assertEquals("OPERATION_FAILED_IllegalStateException", issueCode(IllegalStateException("Check failed.")))
        assertEquals("OPERATION_FAILED_IllegalArgumentException", issueCode(IllegalArgumentException("Failed requirement.")))
        assertEquals("OPERATION_FAILED_IllegalStateException", issueCode(IllegalStateException()))
        assertEquals("OPERATION_FAILED_NullPointerException", issueCode(NullPointerException("PRIMARY_PHONE_PROFILE_REQUIRED")))
    }
    @Test fun platformAndPermissionFailuresKeepTheirMeaning() {
        assertEquals("ANDROID_PERMISSION_DENIED", issueCode(SecurityException("uid 0 does not own package")))
        assertEquals("ANDROID_PERMISSION_DENIED", issueCode(InvocationTargetException(SecurityException("x"))))
        assertEquals("PLATFORM_INVOCATION_FAILED", issueCode(InvocationTargetException(null)))
    }
    @Test fun knownCodesGetTheirOwnExplanation() {
        assertEquals(IssueKind.NONE, issueKind(""))
        assertEquals(IssueKind.NONE, issueKind(null))
        assertEquals(IssueKind.SHELL_MODE_REQUIRED, issueKind("START_SHIZUKU_USING_WIRELESS_DEBUGGING"))
        assertEquals(IssueKind.SHELL_MODE_REQUIRED, issueKind("WIRELESS_SHIZUKU_SHELL_REQUIRED"))
        assertEquals(IssueKind.MAIN_PROFILE_REQUIRED, issueKind("PRIMARY_PHONE_PROFILE_REQUIRED"))
        assertEquals(IssueKind.CLIPBOARD_NOT_SUPPORTED, issueKind("UNSUPPORTED_CLIPBOARD_SIGNATURE_getPrimaryClip"))
        assertEquals(IssueKind.CLIPBOARD_NOT_SUPPORTED, issueKind("SYSTEM_CLIPBOARD_UNAVAILABLE"))
        assertEquals(IssueKind.OTHER, issueKind("HELPER_START_TIMEOUT"))
    }
}
