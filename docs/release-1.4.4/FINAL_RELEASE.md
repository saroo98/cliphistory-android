# ClipHistory 1.4.4 release

Published 1 November 2026. The signed [GitHub release](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.4) uses source commit `f240f9b39ba8425747d4180e7b6932f09717b817`, package `app.cliphistory`, version code 14 and Android 11/API 30 minimum.

| Item | SHA-256 |
|---|---|
| Signed release APK | `0f2e159208d3757e1180d6baf935721908acd9e708826e132f1f2d6135c826ae` |
| Windows and Linux unsigned builds | `3e4214fe043181e88b5f60df98788e070cf5d0182a1e81633797edfc34d9b4f6` |
| Signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |

The release source passed 79 unit tests, release lint with zero errors and 24 warnings, and 74 source-audit checks. A fresh Windows release build and an independent Linux build had identical unsigned bytes. Apksigcopier reproduced the signed APK from the Linux build. The signed APK passed signature and 16 KiB alignment checks.

A local F-Droid 2.4.5 recipe build checked out the published commit, ran source and APK scans, downloaded the release APK, and successfully compared it with the built binary and allowed signing key. The local setup used Gradle 9.3.1, the Android SDK and ruamel.yaml 0.18.10. Its metadata lint completed with a trailing-space warning on the canonical wrapped `Binaries` line. These local checks do not replace F-Droid CI.

The exact signed release was installed in place on a Pixel 8 Pro running Android 17. The installed APK hash matched, the observed history count and settings matched before and after, and the connection test passed with the eligible original clipboard and prior pause state restored. The clipboard-access notice explanation and system-settings shortcut were checked; the phone-wide setting was left unchanged. Matching counts do not prove a byte-for-byte comparison of personal history.

Earlier [candidate checks](RELEASE.md) cover the Redmi Note 11 Pro on MIUI 13 and the Android emulator matrix. The Redmi was tested before the clipboard-notice shortcut was added. The reported HyperOS 3 device remains untested, so its issue is not confirmed fixed.

The [F-Droid merge request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) has been updated for 1.4.4. The fork's pipeline reported that GitLab identity verification was required and ran no jobs. F-Droid maintainer CI, review, and inclusion remain pending; the successful 1.3.4 pipeline does not qualify this release.
