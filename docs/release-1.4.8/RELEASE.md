# ClipHistory 1.4.8 release note

Version **1.4.8 / code 18**. This release changes only the setup guide, the README and the store text. The recorder, storage, permissions and signing key are unchanged from 1.4.7. Final hashes are recorded after tagging.

## Final release

Published on [GitHub](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.8) on 16 November 2026.

| Item | Verified identity |
| --- | --- |
| Tag and source commit | `v1.4.8` → `e8c800d1132c52897e1db18725a82d0cee9e5762` |
| Signed APK: release asset, public download and installed Pixel APK | `f5893210f0eb4fa9ab637b67a30983f9375002b1c6d6f17f0f1e51bc05e7068d`, 2,697,335 bytes |
| Unsigned APK, Windows (JBR 21) and Linux (CI, Temurin 17) clean builds of the tag | `affee6011451d93b5645eb20c955de53cd7637e7dd98b4fceabf86933544966a` |
| Signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |

The tagged commit rebuilt byte-identically on Windows and Linux, and `apksigcopier compare --unsigned` matches the signed APK. The APK records the tagged commit. v3 signature, 16 KiB aligned, target 37, minimum 30, not debuggable, the same six permissions. Unit tests 100/100, lint 0 errors.

| Check | Result |
| --- | --- |
| GitHub CI on the tag | Unit tests, lint and APKs green (run 43867314981). Full emulator matrix, Android 11–17 × Shizuku, Shizako and root refusal: 21/21 jobs green (run 43867319419). The Android 11 Shizako job passed on re-run after its emulator went offline before the onboarding suite started; this time the Android 17 jobs passed too. |
| Signed upgrade from the public 1.4.7, Android 16 emulator | Shizuku-Next 14.0.35 with Start on boot: same signer, first install date kept, the 3 saved entries kept, the recorder restarted as UID 2000 in the same second as the update, before the app was opened, and the app shows Listening. |
| Pixel 8 Pro, Android 17 QPR3 beta | The public release APK was installed in place over 1.4.7 with its data kept (first install date unchanged); the installed hash matches; the recorder restarted as UID 2000 by itself, with Shizuku-Next 14.0.36 already running. |

## What changed

- **Setup with Shizuku-Next** (`45197ab`, `f774980`, `9513f61`). The built-in guide and the README now set users up with [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), a maintained Shizuku fork with the same package name, so ClipHistory works with it unchanged. Its **Start on boot** brings the service back after every restart without a computer. The guide has five steps, with buttons that download Shizuku-Next in one tap (`releases/latest/download/shizuku-next.apk`), open **About phone** and open **Developer options** at Wireless debugging. Users who already run Shizuku keep working; the guide explains how to switch.
- **Why.** On the Android 17 QPR3 beta, writing the adb settings needs `MANAGE_DEBUGGING`, and official Shizuku 13.6.0 can no longer start there ([RikkaApps/Shizuku#2673](https://github.com/RikkaApps/Shizuku/issues/2673)). The same beta also hides `adb_wifi_enabled` from apps, which stopped Shizuku-Next from starting on a fresh install. That was reported as [rushiranpise/Shizuku-Next#224](https://github.com/rushiranpise/Shizuku-Next/issues/224) and fixed in Shizuku-Next 14.0.36, which also added the fixed-name download.

## Checks before tagging

| Check | Result |
| --- | --- |
| JVM unit tests | 100/100 |
| Release lint | 0 errors |
| Guide suites, Android 16 emulator | `html-port` 122/122 (five numbered setup sections with their buttons, in order), `onboarding` 24/24 (every setup paragraph shown) |
| Guide buttons, Android 16 emulator | Download opens the fixed-name APK link in the browser; About phone and Developer options open the matching Settings screens |
| Shizuku-Next 14.0.36 on a Pixel 8 Pro, Android 17 QPR3 beta (DP11.261030.003) | Downloaded through the one-tap link: signer `06360bb8…8c654293`, SHA-256 `661d3800…01d3cec8` matches the release. With `WRITE_SECURE_SETTINGS` revoked: Start works; after a reboot, Start on boot brings the server up 2 seconds after unlock; ClipHistory 1.4.7's recorder reconnects as UID 2000 by itself each time. 14.0.35 failed the same start. |
| Full guide flow, Android 16 emulator (13 November) | No computer needed after install: pairing through Shizuku-Next's notification, Start, Start on boot across a reboot, Connect, and a test copy saved |

## Not covered

- Spoken TalkBack navigation of the new guide (labels and touch sizes are checked automatically).
- Shizuku-Next on phones other than the Pixel 8 Pro, and on Android versions other than 16 and the 17 QPR3 beta.

Support stays Android 11+, the primary profile, and a Shizuku API 13+ service in shell mode. There is no claim of universal device compatibility.
