# ClipHistory 1.4.3 local release candidate

31 October–1 November 2026. Version 1.4.3, code 13, package `app.cliphistory`, minimum Android 11 / API 30. This is a local candidate, not an F-Droid publication.

## Changes

The candidate includes the earlier encrypted-history, sensitive-capture, Android 11–13, duplicate-event and recovery-notification changes documented in [1.4.2](../release-1.4.2/RELEASE.md).

On a Xiaomi 14, StrongBox was advertised but returned `ERROR_UNIMPLEMENTED` when asked to generate the history wrapping key. The key now falls back to the normal Android Keystore path for that unsupported case or `StrongBoxUnavailableException`; unrelated failures still fail closed. The Xiaomi's fallback key was reported as hardware-backed. Other devices may differ.

On a Lenovo Tab M11, an automatic retry erased the previous `HELPER_START_TIMEOUT` while another bind was in progress. Diagnostics then incorrectly suggested starting Shizuku. The last helper-start failure now remains available during an automatic retry and is cleared on a successful connection or an explicit fresh attempt. Automatic retries remain enabled. The targeted regression failed [before](retry-before.txt), passed [after](retry-after.txt), and the real-device Diagnostics screen changed from [before](lenovo-diagnostic-before.xml) to [after](lenovo-diagnostic-after.xml).

Two failure paths could stop supervision even though a Stop request was not durably saved. If boot identity was unavailable, the client cleared its explicit session activation after the failed write. Separately, a clipboard update during a pending Stop could cause the supervisor to stop before the result was known. The client now retains activation until Stop is saved, and the supervisor waits for the result. Both scenarios were reproduced and passed after the fix: [unknown boot before](unknown-boot-before.txt), [after](unknown-boot-after.txt); [pending Stop before](pending-stop-before.txt), [after](pending-stop-after.txt).

## Current evidence

| Check | Result and scope |
|---|---|
| Build | `:app:testDebugUnitTest`, `:app:lintRelease`, `:app:assembleRelease`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed. |
| Unit tests and lint | 79 tests passed, with no failures or skips; release lint had zero errors and 18 warnings. |
| Source audit | 74/74 static source checks passed. |
| F-Droid scanner | The F-Droid scanner found zero fatal source problems and zero APK problems, using an isolated copy of the local working tree and its SUSS signatures. [Results](fdroid-scans-final.json). The Windows scanner could not discover `apksigner`; the APK signature was verified separately with Android build tools. This scan does not establish F-Droid build reproducibility. |
| Earlier 1.4.3 candidate | Before the two recovery fixes above, Android 11, 12, 12L, 14, 16 and 17 each passed 20/20 emulator suites. The Android 13 run had an intermittent tile failure. Those runs do not qualify the later candidate. |
| Final candidate, Android 11 / API 30 | 22/22 emulator suites, 680 assertions. [Results](api30-candidate-checks.json). |
| Final candidate, Android 12 / API 31 | Both new recovery failure suites passed, 18 assertions. [Results](api31-candidate-checks.json). |
| Final candidate, Android 12L / API 32 | Both new recovery failure suites passed, 18 assertions. [Results](api32-candidate-checks.json). |
| Final candidate, Android 13 / API 33 | 22/22 emulator suites, 680 assertions. [Results](api33-candidate-checks.json). The earlier intermittent tile failure remains unexplained. |
| Final candidate, Android 14 / API 34 | Both new recovery failure suites passed, 18 assertions. [Results](api34-candidate-checks.json). |
| Final candidate, Android 15 / API 35 | 22/22 emulator suites, 680 assertions. [Results](api35-candidate-checks.json). |
| Final candidate, Android 16 / API 36 | Both new recovery failure suites passed, 18 assertions. [Results](api36-candidate-checks.json). |
| Final candidate, Android 16 minor SDK 36.1 | 22/22 emulator suites, 680 assertions. [Results](api361-candidate-checks.json). |
| Final candidate, Android 17 / API 37 | Both new recovery failure suites passed, 18 assertions. [Results](api37-candidate-checks.json). |
| Signed production APK on Android 11 | The actual non-debuggable signed candidate connected to official Shizuku and captured A–B–A–B as two visible entries in B–A order. [Result](api30-production-candidate.json), [connection](api30-production-connection.xml), [history](api30-production-history.xml). |
| Upgrade from 1.4.1 | An in-place validation-package upgrade to the final candidate on Android 14 preserved the app UID, wrapped key, authenticated history, settings and sensitive entries; the recorder was replaced. [Results](upgrade-final.json), [assertions](upgrade-final.txt). |
| Signed APK | SHA-256 `b9f95c4b4d9dcf6e29af23dea49bab1cc932b39f7e85cd93f2cc7719230040b5`; Android package/version and v3 signature verified, and 16 KB page alignment checked. The signing certificate SHA-256 is `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`. |

The updated validation APK SHA-256 is `daeddead6bb85563ba792784febf00ab7163cd3eca7402d6daa19d59056128a4`; the earlier validation APK SHA-256 is `cb1a7f75718d0d91a626b793375eed11d275a2e296d7b0117d95b8f5c38f7e6b`. The validation package is separate from the signed production package. Synthetic test samples were used throughout.

### Real devices

- **Xiaomi 12 Pro, Android 14, HyperOS 1:** the exact signed 1.4.3 candidate connected to official Shizuku 13.6, captured and deduplicated synthetic copies, and expired a marked sensitive entry after a one-minute setting. [Test details](PCLOUDY_XIAOMI_TESTS.md).
- **Redmi Note 11 Pro, Android 11, MIUI 13:** official Shizuku 13.6 failed before constructing the recorder, with a `LoadedApk.makeApplication` null-pointer exception. A same-device comparison using official Shizuku 13.5.4 connected and captured a synthetic external copy. This is not a recommendation to downgrade. [Test details and failure log](PCLOUDY_XIAOMI_TESTS.md).
- **Xiaomi 14, Android 15, HyperOS 2.0:** the preceding 1.4.2 test build reproduced and fixed the StrongBox failure. Official Shizuku 13.6 recorded ordinary and source-marked sensitive test entries, handled A–B–A–B as two entries, and removed a timed sensitive entry. This was not a test of the final 1.4.3 APK or of HyperOS 3.
- **Lenovo Tab M11 TB330FU, Android 15, MediaTek:** official Shizuku 13.6 failed inside `LoadedApk.makeApplicationInner` before the recorder was constructed. [Failure log](lenovo-shizuku-13.6-failure.txt). On an earlier 1.4.3 test build, official Shizuku 13.5.4 connected and [captured A–B–A–B](lenovo-shizuku-13.5.4-capture.xml). With the earlier signed 1.4.3 APK, the locally available `13.7.0-thedjchi` fork connected, retained history, captured A–B–A–B, and captured and expired a sensitive test entry. [Recovery](lenovo-fork-recovered.xml), [capture](lenovo-ab.xml), [sensitive before](lenovo-sensitive-before.xml), [sensitive after](lenovo-sensitive-after.xml). This is a device-specific compatibility comparison, not a security assessment or general recommendation to downgrade or install a fork.
- **Samsung Galaxy A34 5G SM-A346N, Android 16, MediaTek:** the earlier signed 1.4.3 APK connected with official Shizuku 13.6, [captured A–B–A–B](samsung-final-ab.xml), and [retained the entries after force stop and reopening](samsung-final-restart.xml). The validation Keystore probe reported hardware backing and no advertised StrongBox. [Probe](samsung-keystore.txt).
- **Connected Pixel 8 Pro:** the installed production app is 1.4.1 and its signing certificate matches the 1.4.3 candidate. This was a read-only signature check; the production app was not updated or tested in this run.

These observations show that the Shizuku startup failure depends on more than the Android version or MediaTek hardware alone. They do not prove the exact cause or fix on the reviewer’s HyperOS 3 device. Google offered only one Xiaomi model in this project's device catalog. An additional reservation was rejected because the free device-streaming quota was exhausted; the test project's billing remained disabled. The Lenovo remote reboot command stalled and the device remained running, so real reboot recovery was not verified. Long-duration idle and battery reliability were not qualified in this run.

An earlier Android 13 tile suite timed out while waiting for the re-enabled tile to become ready. A separate first rerun timed out at initial launch while SystemUI reported the tile unavailable; one later run and three repetitions passed. [Initial failure](api33-tile-initial-failure.txt), [later pass](api33-tile-repeat-passed.txt). The cause is not established, and a pass alone does not resolve it. Real-device and further SystemUI investigation would be useful.

Two later Android 12L and 16 emulator launches did not appear in ADB on reused, non-recommended emulator ports. The affected disposable processes were stopped, and both recovery suites passed after launching on different ports. This was a test-infrastructure interruption; its exact cause was not established.

## Publication gates

The [F-Droid request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) is still open and refers to published version 1.3.4. Version 1.4.3 is currently an uncommitted local working tree. Before submitting this candidate, it needs a reviewed source commit and matching immutable tag, public source and matching reference APK, a recipe pinned to the actual commit, independent F-Droid/Linux reproducibility and fresh CI/review. The local Ubuntu WSL instance could not start because its virtual disk path was missing, and the Docker Linux engine was not running; neither was changed. The HyperOS 3 report remains open. No commit, tag, push, public reply or publication was performed for this candidate.
