# ClipHistory 1.4.4 candidate qualification

1 November 2026. Version 1.4.4, code 14, package `app.cliphistory`; Android 11 / API 30 or later. This document records the local candidate checks at that time, not an F-Droid release.

The APK and build hashes below predate the later local clipboard-access notice settings change. That change and its publication limitation are documented in [CLIPBOARD_ACCESS_NOTICE.md](CLIPBOARD_ACCESS_NOTICE.md); a new release build and verification are required.

## Shizuku startup compatibility

The previous candidate failed on a Redmi Note 11 Pro with MIUI 13 and official Shizuku 13.6. The recorded exception occurred in Android's `LoadedApk.makeApplication`, before the clipboard helper was constructed. The trace matches the missing process-name failure described in [Shizuku-API pull request 547](https://github.com/RikkaApps/Shizuku-API/pull/547); the Redmi framework was not independently inspected.

`ClipAppComponentFactory` supplies the missing process-name information during the shell-only application bootstrap. It does nothing in ClipHistory's normal application process. The workaround uses Android internals and falls back to Shizuku's existing error handling if the expected fields are unavailable. It cannot guarantee compatibility with every Android or manufacturer release. A debug-only probe exercises the same package-loading sequence and verifies that the helper can be constructed.

On the affected Redmi, the signed 1.4.4 APK below was installed and its device-side SHA-256 matched. With official Shizuku 13.6, the connection test passed, including clipboard events and storage. External synthetic copies A, B, A, B produced two entries in B, A order. A marked sensitive copy was skipped by default; after opt-in and a one-minute timer, another appeared and was deleted. Force-stop preserved the ordinary entries and stopped recording; manually starting the recorder allowed a new copy. After a real reboot, restarting Shizuku and opening ClipHistory restored the saved history and allowed a new external copy. The recovery service was not observed before Shizuku was restarted and the app opened, so automatic recovery on this MIUI device remains unverified. Only synthetic data was used; the cloud session was released.

An earlier isolated validation APK also passed the startup probe and the same connection, duplicate, sensitive-expiry and force-stop checks on that Redmi. The Shizuku APK installed there matched SHA-256 `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`.

The F-Droid reviewer reported a helper startup exception on Android 16 / HyperOS 3; a full framework trace was not visible in the comments checked. HyperOS 3 was not available in the free device catalog used here. Pcloudy rejected a later attempt to reserve the Redmi again with a generic 403, preventing further investigation of its automatic reboot recovery. The Redmi fix is relevant to the reviewer report, but the HyperOS 3 result and the exact cause on the reviewer's device are not established.

## Build and verification

| Check | Result and scope |
|---|---|
| Signed candidate | `ClipHistory-1.4.4.apk`, SHA-256 `72e88e3d7753cc3706e07a3a446d9f0301208eff681b0988c4e915f1f141ef4a`; signer SHA-256 `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`. APK signature and 16 KB page alignment verified; package code 14, min SDK 30, target 37, not debuggable. |
| Build and source checks | Gradle unit tests, release lint and debug/release APK builds passed. 79 unit tests passed; lint reported zero errors and 24 warnings. The six new warnings describe the private Android APIs used by the compatibility workaround. Source audit: 74/74 checks. |
| Android 11 emulator | 22/22 validation suites passed, 680 assertions, including live capture, encryption, privacy, sensitive expiry, native controls, recovery and the actual Quick Settings tile. [Results](api30-checks.json). |
| Android 13 tile | Ten consecutive runs passed, 75 repeated assertions per run. The earlier 1.4.3 timeout while waiting for SystemUI to report the re-enabled tile remains unexplained; these passes do not establish its cause. [Results](api33-tile-repeats.json). |
| Android 16 emulator | On minor SDK 36.1, six validation suites passed, 179 assertions: paused connection, live capture, sensitive expiry, recovery failures, the actual Quick Settings tile and helper-start failure diagnostics. [Results](api361-checks.json). This is a stock emulator, not HyperOS 3. |
| Other emulator startup checks | The isolated shell bootstrap and helper-construction probe passed on Android 12 / API 31, 12L / API 32, 15 / API 35 and Android 16 minor SDK 36.1. These probes are startup checks, not full feature suites. |
| Android 17 emulator | Live capture and sensitive-expiry suites passed. A real emulator reboot reconnected automatically after Shizuku was restarted and captured an external synthetic copy, without opening ClipHistory first. With reboot resumption switched off, no helper restarted during the observation. [Results](api37-checks.json), [boot on](api37-boot.json), [boot off](api37-boot-off.json). |
| Upgrade from 1.3.4 | An in-place validation-package upgrade on Android 14 preserved the app UID and exact synthetic history, migrated both slots to authenticated encrypted storage and replaced the live shell helper. [Results](upgrade-from-1.3.4.json), [assertions](upgrade-from-1.3.4.txt). |
| Cross-platform comparison | Independent Windows and Linux unsigned builds from the same local source snapshot matched SHA-256 `fa3157fb43ca9ac6c6874751f4f7902166a9fd0a0ed47b8dd57082d93e5d02f6`. `apksigcopier` reproduced the signed candidate byte for byte from the Linux build. This is not a complete F-Droid recipe build from a published commit. |
| F-Droid scanner | The isolated source snapshot produced zero fatal findings, with the Gradle wrapper JAR excluded for the scan; the Linux-built APK scan passed. The signed APK was verified separately. |
| Store images | The Welcome screenshot was refreshed to remove an outdated privacy message, and a Privacy settings screenshot was added. Both are actual screenshots of the signed 1.4.4 candidate on a disposable Android 17 emulator; the Privacy image shows sensitive capture and screenshots enabled for demonstration. |

The validation APK and test APK used for the current emulator runs have SHA-256 `0a8b3fb2bbfce36da764a61ef95ab48dfe794544d9c9437ca2346f2b27dc0da6` and `5eaa4309c6c30b7c07dad38e987865e99f007eff2f8ff3409d7f3d4b52044e1c`, respectively. The earlier [1.4.3 qualification](../release-1.4.3/RELEASE.md) records broader Android-version and device tests, but its results do not automatically qualify this binary.

The first 1.3.4 upgrade test stopped before installation because its synthetic copy producer still had focus when the driver looked for ClipHistory's menu. The driver now reopens ClipHistory before changing the recovery setting; the upgrade passed on rerun. This was a test-driver correction, not an application fix.

## Publication status at the time of these checks

The existing [F-Droid merge request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) still targets public 1.3.4/code 8. The 1.4.4 source is an uncommitted local working tree. Its candidate APK embeds the old Git HEAD, `6009e25f548a5f1a45d6cf129d288056e76d5b8f`, which does not identify the uncommitted changes. It must therefore be rebuilt and re-signed from the actual reviewed release commit; this candidate must not be treated as the final reference APK. Submitting the new release also requires an immutable version tag, publication of that source and matching signed reference APK, an updated metadata recipe pinned to the actual commit, the full F-Droid build and reproducibility checks, and fresh CI and maintainer review. No commit, tag, push, public reply or publication was performed for this candidate.
