# ClipHistory 1.4.9 release note

Version **1.4.9 / code 19**. This release changes only Setup help and the setup wording. The recorder, storage, permissions and signing key are unchanged from 1.4.8. Final hashes are recorded after tagging.

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
