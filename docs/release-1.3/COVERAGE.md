# ClipHistory 1.3.0 coverage

Verified during implementation on 2026-09-30. Results apply to the named configurations and flows. Older 1.2 results are historical.

| Area | Verified evidence | Limit |
|---|---|---|
| History | Exact-text unique default; A/B/A/B produces B/A; consecutive-only remains selectable; strict UTF-8, bounds and corrupt-frame rejection pass | Equality preserves spaces and Unicode; no normalization |
| Migration | Six migration tests cover V1/V2 and interrupted/mirror writes; final signed 1.1 and 1.2 upgrades on API 35 preserve text, pause, limits and UID | Synthetic fixtures only; V2 downgrade unsupported |
| Recorder | 33 live API 35 assertions cover real shell capture, policies, private persistence, stale IDs, filtering, sustained copies, Stop during attach and real failed preference writes | Disposable synthetic fixtures only |
| Recovery | API 35 helper/app death, recovery Off, native Recents Clear all with recovery On/Off, Task Manager Stop and Force stop verified | Unknown stop descriptions fail closed; OEM differences require testing |
| Reboot | Actual API 35 emulator reboot with resumption On/Off and pause preservation | Non-root Shizuku must be started after reboot |
| Shizuku | Official manager on emulator, UID 2000; nine paused attachment assertions on Pixel API 37 with 13.7.0-thedjchi | Physical check wrote no clipboard text and stopped its isolated helper |
| Interface | 29 UI and 12 regression assertions on API 35; Settings on API 34 and Pixel; light/dark Views inspected | No exhaustive manufacturer coverage |
| Layout | 200% landscape with real keyboard, reachable results control and retained history; Settings rotation passes | Close a keyboard that occupies almost the entire window to recover list space |
| Customization | All optional features Off survive recreation; malformed legacy presentation values use compatible defaults | Mandatory safeguards remain enabled |
| Screenshots | Real Main and floating-panel OS capture on Pixel; sheet/confirmation constructors protect/expose/protect on API 35 through the Settings callback | Does not protect against cameras or a compromised device |
| Accessibility | Labels, headings, toggle states, enabled-control focus, 48dp targets and dismissal; 200% layout | Exhaustive TalkBack spoken-output traversal not measured |
| Quick copy | API 34/36 native tile tests; Pixel 30 warm openings and separate timed schedule | Android controls the tile launch transition; no universal zero-latency guarantee |
| Battery | Actual Pixel app battery page; trusted-system fallbacks and separate optimization-list navigation | Choices and wording vary; no automatic policy changes |

## Performance

Pixel maximum history: 500 entries of 65,536 UTF-8 bytes, snapshot 32,778,066 bytes. Observed encode 583 ms, first offline read 691 ms, cached read 1 ms, initial page 663 ms, next page 42 ms, full-text lookup 1,040 ms; process heap limit 256 MiB. Samples are not device-wide guarantees.

Thirty warm Pixel openings in the isolated validation variant:

- Loaded app pre-draw: p50 **92.33 ms**, p95 **129.17 ms**, maximum **133.54 ms**.
- SystemUI/accessibility entry availability: p50 **662 ms**, p95 **704 ms**, maximum **716 ms**.
- These are different stages. Pre-draw does not prove full presentation or touch availability.

After the measured row-reservation fix, thirty further openings preserve identical window sizes. App-loaded pre-draw p50 98.62ms, p95 123.97ms, maximum 147.35ms; SystemUI/accessibility p95 711ms. The final 12-trial timed schedule passes, with all 1,147 encoded frames reviewed and no app deadline miss in 300 app-window frame records. One late presentation is classified SurfaceFlinger Scheduling. Android's own launch animation remains. These runs use the isolated debug validation variant, not the signed production APK.

A 50-copy session with a 20-entry cap completed in 1,816 ms with correct retained entries and zero reported queue drops. During a separate 1,800.579-second API 36 idle observation the same helper survived, CPU user/system ticks remained 21/36 and PSS changed from 39,114 to 38,924 KiB. Kernel wakeups were **not measured**. No clipboard polling, idle watchdog, wake lock or scheduled alarm was added.

## Boundaries

Pixel 8 Pro: Android 17 beta CP41.260828.004.A8. Owned emulators: API 34, 35 and 36. Test APKs/fixtures do not ship. Physical tests target `app.cliphistory.validation`; production history is never seeded or cleared by the harness.

The final original-key 1.3 APK was subsequently installed over the phone's production 1.1 app with `adb install -r`. Android accepted the update and retained the application UID. The actual installed base APK matches SHA-256 `13fa9570bb2c7e4df7be34b0e4dcb2a92c278113d91ec6f7f43a1061dba1d070`. Production clipboard/history text was not read or exported. The disposable validation app, test APK and validation tile were removed afterwards. A first upgrade requires opening ClipHistory and explicitly connecting to complete the new recovery setup; no production Start, Stop or copying action was automated.

No multi-day battery soak, exhaustive TalkBack audio audit, every OEM/future platform, or F-Droid main-repository acceptance is claimed. Internal clipboard interfaces remain the main compatibility risk.
