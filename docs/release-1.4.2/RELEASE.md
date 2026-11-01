# ClipHistory 1.4.2 local qualification

31 October 2026. Version 1.4.2 / code 12. Package `app.cliphistory`. Minimum Android 11 / API 30; compile and target remain API 37. Local candidate, not a published release.

## Confirmed fixes

**Recovery notification flooding.** Every clipboard change previously reposted the same foreground notification. Android's notification manager dropped updates after the app exceeded its rate limit, including the message for a failed durable Stop. The unchanged regression failed twice on API 36.1 before this change. `RecordingRecoveryService` now posts an update only when its displayed status changes. The isolated fix passed all 33 live assertions; the final candidate passes them on every image below. See [before](notification-before.txt), [isolated after](notification-isolated-after.txt) and the final per-image `live.txt` files.

**Cleared text returning after a delayed system event.** The Android 15 run observed three entries after clearing history and copying A/B/A/B: B, A and an earlier cleared Unicode entry. This is recorded in [the failure](cleared-copy-before.txt). Android can notify listeners again when it classifies an existing clip. The bridge now identifies the same logical copy using its platform timestamp and a keyed signature of the exact text and source sensitivity flag. It remembers the current copy before subscribing, so pending classification does not import old text at connection time. The final regression observes exactly B,A on all nine images; the optional consecutive-only mode still records four distinct nonconsecutive copies.

`ClipboardCopyTracker` retains one signature and a random key in RAM, with no extra plaintext field or persisted fingerprint. Unknown timestamps are not treated as reliable identities. Seven new unit tests cover repeated notifications, genuine recopies, different text in the same millisecond, sensitivity changes, exact Unicode/spacing, absent timestamps and clock rollback. [Unit red](copy-tracker-unit-before.txt), [unit green](copy-tracker-unit-after.txt), [isolated Android 15 green](cleared-copy-isolated-after.txt).

The platform behavior is documented in [Android's ClipboardManager callback source](https://android.googlesource.com/platform/frameworks/base/%2B/57946e2bb738/core/java/android/content/ClipboardManager.java) and [ClipboardService's classification broadcast](https://android.googlesource.com/platform/frameworks/base/%2B/refs/heads/android16-release/services/core/java/com/android/server/clipboard/ClipboardService.java). Missing logical-copy filtering was verified in the app source. The red run did not log individual framework callback timestamps, so classification is a documented mechanism consistent with that failure, not a traced identification of its exact system event. [ClipDescription.getTimestamp](https://developer.android.com/reference/android/content/ClipDescription#getTimestamp()) provides the system copy timestamp. These APIs do not provide a universal, unique copy ID across all manufacturers.

## Android 11–13 support

The minimum SDK changed from 34 to 30. This required guarded framework calls, not just changing the manifest minimum:

| Files / symbols | Compatibility behavior |
|---|---|
| `MainActivity.back`, `showPage`, `closePage`, `onBackPressed` | Legacy Back below API 33; existing native callback on newer Android. |
| `MainActivity.addTile`, `requestTile` | Manual Quick Settings Edit/drag instructions on Android 11–12; system Add tile request on 13+. |
| `ClipboardTileService.openHistory`, `publishTile` | Required Intent launch below API 34; immutable PendingIntent and activity-launch tile metadata on 34+. |
| `QuickCopyDialog.onAttachedToWindow` | Receiver registration overload appropriate to the OS; screen-off dismissal retained. |
| `RecordingRecoveryService.onCreate` | Standard foreground-service overload below API 34; special-use type on 34+. |
| `MainActivity.requestRecoveryNotifications` | Runtime notification permission only on 33+; notification settings below that. |
| `ScreenPrivacy.apply`, `SettingsPage` | Independent Recents control only on 33+; older Android follows screenshot blocking and explains this. |
| `OwnerStopGuard.check` | Compatible package-information overload; owner-stop checks retained. |
| `HistoryKeys.security`, `load` | API 30 hardware diagnostic fallback; exact bounded key-record read available on older Android. Truncated and extended records fail closed without replacement. |

No history format, encryption format, production dependency or requested permission was added. Sensitive capture remains optional and initially off; its five-minute default deletion, monotonic expiry and recopy policy are retained. Source-marking is still required to identify sensitive text; the app cannot identify every private copy automatically. Update guidance, store text and changelog 12 reflect the supported versions.

## Final emulator matrix

All rows use the same frozen validation APK `f51febdee89de0cf7167a4ba4916348282f42635952bba5d70d06c99f66e304f`. API 30–36.1 use test APK `2bcc1edb16977e9da9d55d7a8a014dfa225f406b93fb71f9278c88052f2a8929`. Android 17 uses `d0fec6f276390e10228df1fb7d5bbb9c21b831cf8dfd2e8aa5edb8d5a837c311`, after correcting the measured exact-black screenshot assumption described below. This is a test-only revision; production bytes did not change. Each row is one complete run of the 19 suites, rather than a combination of successful fragments. [Machine-readable summary](matrix-summary.json) includes fingerprints, minor SDK, both test-harness versions, durations and per-suite results.

| Android | API | Passed suites | Assertions |
|---|---:|---:|---:|
| 11 | 30 | 19/19 | 651 |
| 12 | 31 | 19/19 | 651 |
| 12L | 32 | 19/19 | 651 |
| 13 | 33 | 19/19 | 651 |
| 14 | 34 | 19/19 | 651 |
| 15 | 35 | 19/19 | 651 |
| 16 | 36 | 19/19 | 651 |
| 16 (minor SDK 36.1) | 36.1 | 19/19 | 651 |
| 17 | 37 | 19/19 | 651 |

Total: **171 suite runs, 5859 assertions**. These are repeated assertions across nine stock Google x86_64 images, not that many unique tests or manufacturer ROMs. Official Shizuku 13.6.0.r1086.2650830c was started as shell UID 2000, without root.

Suites: boot-identity, live capture, sensitive-live, encryption, sensitive-ui, regression, settings, onboarding, audit-diagnostics, html-port, html-port-live, privacy, quick-errors, command-errors, accessibility, customization, recovery, paused-connection and tile. Coverage includes actual background capture, A/B/A/B policies, fifty-copy bursts, failed durable Stop, encrypted corruption handling, a real one-minute sensitive deadline, full-text/quick-panel expiry, native controls, Back, screenshot protection and real SystemUI tile taps/copy/dismissal. Boot-identity is an attachment/recovery fixture; it is not a real reboot test.

Three synthetic images per OS are retained under `validation/final-api*/screenshots`: light/dark native Settings renders and an actual allowed OS screenshot. Light and dark Android 11 Settings were visually inspected: the legacy privacy explanation is readable and fits the scrollable layout. This is focused compatibility inspection, not a fresh full design or animation audit.

## Signed release and upgrade

| Check | Observed result |
|---|---|
| Release artifact | `ClipHistory-1.4.2.apk`, 2,664,567 bytes |
| Release SHA-256 | `ca2df31c8f6e09af9f46bff78c6c8eb0307c93acab6a180d465703e177776cec` |
| Original signing certificate SHA-256 | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| APK identity | Non-debuggable, code 12, min 30, target 37; no test components |
| Signature / alignment | APK v3 verifies; `zipalign -c -P 16 -v 4` succeeds |
| Unit tests | 79 passed; zero failures, errors or skips |
| Release lint | 0 errors, 18 warnings |
| Static source checks | 74/74 |
| Frozen production source | All recorded source/config hashes match the built candidate |

See [build checks](final-build-checks.json), [Gradle result](final-qualified-build.txt), [JUnit XML](unit/), [source audit](source-audit.txt), [source hashes](final-source-sha256.json), [signature](final-apk-signature.txt), [alignment](final-apk-alignment.txt), [manifest](final-apk-manifest.txt) and [permissions](final-apk-permissions.txt).

The JUnit XML hostnames are normalized for privacy; test results are unchanged.

The signed, non-debuggable final APK was separately installed and tested on Android 11. Its native connection test passed; all 51 synthetic copies were present with the final entry verified, its helper ran as UID 2000 and no application notification-rate-limit warnings occurred. [Production check](final-api30-production.json), [connection UI](final-api30-production-connection.xml), [history UI](final-api30-production-after-burst.xml). The recorded burst duration includes ADB and fixture overhead and is not a device latency benchmark.

An in-place validation upgrade from 1.4.1 preserved the app UID, wrapped data key, all committed entries/IDs/timestamps/settings, timed and indefinite sensitive entries, both authenticated snapshots and owner-only file modes. The old live helper was replaced by the new version. [Upgrade result](final-upgrade-1.4.1-to-1.4.2.json), [assertions](final-upgrade-1.4.1-to-1.4.2.txt), [before fingerprint](final-upgrade-1.4.1-to-1.4.2.before.txt). The test explicitly enables Automatic recovery before expecting reconnection; an opted-out app is not expected to resume automatically.

The 18 lint warnings remain visible: AGP advisory (1), durable preference commit (1), inlined sensitivity-string constants (2), predictive-Back manifest attribute ignored on older Android (1), redundant v26 resource qualifier (1), plural candidate (1), reflective private APIs (4) and unused strings (7). The inlined string constants do not call a newer framework method and were exercised on API 30–32. The two compatibility-specific lint suppressions are narrowly documented for the required legacy tile launch and Back fallback; NewApi errors were fixed with actual guards.

## Earlier failures and validation setup

- The first Android 16 emulator attempt encountered a **SystemUI startup ANR**, before the clipboard-producer test. The ANR dialog held window focus; the synthetic producer deliberately refuses to write without focus. Its [failed run](earlier-attempts/final-api36-first/live.txt), [system ANR](earlier-attempts/android16-systemui-anr.txt) and [focused window](earlier-attempts/android16-systemui-focus.txt) are retained. Remaining heavy images ran sequentially with four virtual CPUs; no app workaround was added for a host/emulator startup failure.
- The first Android 13 run had **Automatic recovery disabled** from an earlier settings fixture, so a test expecting the optional supervisor's notification could not pass. [Original failure](earlier-attempts/final-api33-first/live.txt). Native setup now enables the option under test. The same issue was corrected in the upgrade fixture through the real Settings control. The production opt-out remains unchanged.
- Android 17's Settings screenshot assertion failed after rotation because it required exactly RGB (0,0,0). A diagnostic rerun preserved the failing frame and measured RGB **(1,1,1)** while the app content remained hidden and the Activity had focus. The allowed comparison showed the real Settings screen at RGB (247,248,244). [Diagnostic log and original images](earlier-attempts/android17-privacy-diagnostic/). The harness now permits only the observed one-level difference in all RGB channels and checks nine interior points instead of one. Existing FLAG_SECURE and allowed-capture checks remain. The full 19 suites and [three additional Settings/rotation repetitions](privacy-repeats/) pass with this test-only revision, with all 21 assertions each time. The compositor mechanism producing that one-level difference was not traced; no production privacy patch or timing workaround was needed.
- Older Android's secure screenshot API can reject capture entirely instead of returning black pixels. The harness accepts either only for protected windows, still checks FLAG_SECURE and requires a real non-black capture when protection is disabled.
- Android 12L needed the test automation's interactive-window retrieval flag for native dialog actions; recovery controls were already visible. SystemUI dump parsing and tile-page navigation were adapted to the older/newer system layouts. The final native tap/copy assertions were retained. [Earlier accessibility failure](android12L-accessibility-before.txt).

## Remaining limits and publication

**HyperOS is unverified.** No HyperOS-specific root cause has been reproduced, and no speculative Xiaomi-specific patch was made. The earlier Xiaomi TestIt attempt was rejected for account eligibility at `/console/403-oversea`; this task did not change that account or obtain a Xiaomi device. Stock emulator results cannot establish Xiaomi compatibility. A permitted HyperOS device session with launch logs, connection test and actual copied-text capture is still needed to resolve that report.

This task did not use the physical phone. It did not perform a new long-duration idle/battery, real reboot, animation-frame, hardware-backed Keystore, ARM-device or manufacturer-ROM qualification. Instrumentation starts/stops its own target, so its recovery fixtures do not replace native lifecycle or overnight tests. No universal zero-delay or exception-free behavior is claimed.

F-Droid acceptance is separate. The existing local recipes and submission records still reference public 1.3.4/code 8; they were not relabelled as a 1.4.2 release. This dirty working-tree candidate still needs an authorized clean source commit/tag, published source/reference binary, independent Linux reproducibility and the normal F-Droid scan/build/review for those exact bytes. Historical Linux or remote CI results do not qualify this binary. No commit, push, submission, account change or remote status check was performed by this compatibility task. Follow [the publication guide](../F_DROID_ANDROID_GUIDE.md) for those gates.
