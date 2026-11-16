# ClipHistory 1.4.9 release note

Version **1.4.9 / code 19**. This release changes only Setup help and the setup wording. The recorder, storage, permissions and signing key are unchanged from 1.4.8. Final hashes are recorded after tagging.

## Final release

Published on [GitHub](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.9) on 16 November 2026.

| Item | Verified identity |
| --- | --- |
| Tag and source commit | `v1.4.9` → `314a4820cfae32507ed16856b68d92379b986ab3` |
| Signed APK: release asset, public download and installed Pixel APK | `97a221004f35bb3032215b24bfca80f0fef9f17bf20eec14dd1ec340f04f89b1`, 2,697,335 bytes |
| Unsigned APK, Windows (JBR 21) and Linux (CI, Temurin 17) clean builds of the tag | `4402dc5d2847226e7410023229c561dcb6119ff8e95c2df1164fb7e536a1dd43` |
| Signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |

The tagged commit rebuilt byte-identically on Windows and Linux, and `apksigcopier compare --unsigned` matches the signed APK. The APK records the tagged commit. Target 37, minimum 30, not debuggable, the same six permissions. Unit tests 101/101, lint 0 errors.

| Check | Result |
| --- | --- |
| GitHub CI on the tag | Unit tests, lint and APKs green (run 43880586166). Full emulator matrix, Android 11–17 × Shizuku, Shizako and root refusal: 21/21 jobs green (run 43880590915). The Android 11 Shizuku job passed on re-run: its emulator went offline between the privacy and onboarding suites, before the onboarding suite started, as an Android 11 job also did for 1.4.8. |
| Signed upgrade from the public 1.4.8, Android 16 emulator | Shizuku-Next with Start on boot: first install date and the 3 saved entries kept; the recorder restarted as UID 2000 in the same second as the update, before the app was opened; Listening; Setup help in the release build shows "All set" with steps 1–4 done. |
| Pixel 8 Pro, Android 17 QPR3 beta | The public release APK was installed in place over 1.4.8 with its data kept (first install date unchanged); the installed hash matches; the recorder restarted as UID 2000 by itself, with Shizuku-Next 14.0.36 already running. |

## What changed

- **Setup help shows progress** (`24a7fa8`). The guide reads the connection state, marks finished steps **Done**, labels the next one **Do this next**, and opens at that step when started from the welcome sheet or the home screen. It rebuilds whenever the setup state changes, for example when the helper app starts or stops while the guide is open. When the recorder is connected it says "All set". The step logic is `nextSetupStep()` in `core/RecorderState.kt`, covered by a unit test.
- **Limit on Android 17.** The QPR3 beta hides `adb_wifi_enabled` from apps, so with the helper app installed but stopped, the guide points at step 2 (wireless debugging) unless the setting reads on.
- **Shizuku-Next is recommended, not required.** Step 1, its download button, the welcome sheet, the home screen's "no helper app" card and the README call Shizuku-Next the recommended option for the best compatibility. Shizuku and other compatible apps still work, and step 1 says they can skip it.

## Checks before tagging

| Check | Result |
| --- | --- |
| JVM unit tests | 101/101 (1 new) |
| Release lint | 0 errors |
| Guide suites, Android 16 emulator | `html-port` 122/122, `onboarding` 24/24 |
| Guide states, Android 16 emulator with Shizuku-Next | No helper app installed: step 1 next. Installed and stopped: step 1 done, step 2 next. Wireless debugging on and stopped: steps 1–2 done, step 3 next. Running, ClipHistory not connected: steps 1–3 done, step 4 next. Connected: "All set" and steps 1–4 done. Stopping the helper app with the guide open updated it to step 2. The guide opened scrolled to the next step. |

## Not covered

- Spoken TalkBack navigation of the new labels (each heading's spoken text includes its status; touch sizes are checked automatically).
- The guide states on a physical phone before release.

Support stays Android 11+, the primary profile, and a Shizuku API 13+ service in shell mode. There is no claim of universal device compatibility.
