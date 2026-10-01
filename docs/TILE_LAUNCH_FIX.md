# Quick-copy launch fix, local build 1.3.2

Package `app.cliphistory`, version code 6. This fixes the extra transition shown around 01:29–01:31 in the supplied Pixel recording. It does not certify universal zero latency or F-Droid inclusion.

## Confirmed causes

The baseline was clean revision `529cc4ff9861e695c803b2de79a70a2b012759c7`. All 178 encoded frames between 89.000 and 91.000 seconds were decoded and inspected. The tile expands into an oversized clipboard rectangle, briefly disappears, then launches the floating panel with a second background transition.

Two native-touch baseline recordings reproduce this sequence. Both phone logs report `ActivityTransitionAnimator: launchResult=0 willAnimate=true`. The three-entry reads complete in about 12–16 ms. The unwanted expansion originates in SystemUI's activity launch, despite the app's no-animation intent and window flags; slow history loading does not explain that sequence. Upstream Android sources support this diagnosis but are not represented as the exact Pixel firmware source.

During testing, the first native-dialog candidate exposed a separate lifecycle defect. Its service cleanup dismissed a still-attached popup. A live trace records `tile destroyed quick=true`, followed 3 ms later by `dialog detached`, before any Close tap. Earlier missing-window failures could not safely be dismissed as automation errors. This candidate defect is fixed too; it is not claimed to be the cause of an unrecorded failure in the original APK.

## Minimal implementation

- `ClipboardTileService` uses `TileService.showDialog` in quick mode. Only full-history mode publishes an activity launch.
- `QuickCopyDialog` replaces the floating Activity and preserves its layout, asynchronous reads, exact text copying, privacy controls, error actions, Close, outside dismissal and Back.
- A weak process-local reference allows an attached dialog to be reused after a service rebind. Service destruction releases service listeners without dismissing the window. Removing the tile still dismisses it. A detached window is not retained by this reference.
- Dialog observers follow actual window attachment and detachment. Its screen-off receiver uses application context so service-context cleanup cannot unregister it prematurely. Pending callbacks are invalidated when the window closes.
- Normal tile binding starts while Quick Settings is visible. Already-published, identical tile state is not updated again. The obsolete active-tile listening requests and Activity declaration are removed.
- Full history retains an immutable PendingIntent. An error's Open app action sends it while the QS token remains valid, then dismisses the dialog. A rejected dialog token falls back to full history.

No dependency, overlay permission, popup foreground service, polling loop, arbitrary launch delay or system animation setting is added. Storage format and recorder behavior are unchanged.

## Verified checks

| Check | Result |
|---|---|
| JVM suite | 28 methods, zero failures/errors |
| Release build and lint | Successful; zero lint errors, 11 existing warnings |
| Static source audit | 69/69 |
| Native tile on API 34 and API 36 | 75 assertions on each: exact whitespace copy, foreground preservation, Close, outside dismissal, Back, mode changes, disabled/re-enabled tile, native error recovery, and service-cleanup regression |
| Error-state fixtures | 18 assertions in each light/dark run on both API 34 and API 36 |
| Privacy fixtures | 11 assertions on each emulator; actual protected/unprotected QS panel capture also checked on the Pixel |
| Final Pixel native-touch warm run | 30/30 openings and dismissals, 353 assertions; no shared clipboard writes |
| App loaded pre-draw | p95 179.28 ms in that 30-opening run, with stable observed window size during loading |
| Accessibility availability | p50 631 ms, p95 687 ms, maximum 714 ms; zero 2-second observation timeouts in that run |
| Final Pixel idle recordings | Three openings after 0, 60 and 120 seconds idle; all show three entries, remain open and accept Close |
| Installed production APK | Signed 1.3.2/code 6 upgrade on the Pixel; Main, native quick panel, sustained visibility and Close pass without copying or exporting history |

All 206 encoded frames in those three final recordings were inspected (67, 71 and 68 frames). Each decoded-image count matches its ffprobe timestamp count. The oversized clipboard expansion, Activity gap and second task/background transition are absent. Ordinary SystemUI shade collapse and blur remain. No four-second stall or unintended dismissal occurs in these recordings. This review covers every encoded video frame, not every physical display refresh. The separate XML-dump observation times include the dump tool's overhead and are not used as startup latency measurements.

The service-cleanup regression deliberately calls the actual owner's `onDestroy` callback with the real QS window attached. This is a deterministic callback regression, distinct from the naturally occurring SystemUI unbinding captured in the failed candidate trace.

The Pixel runs Android 17 beta/API 37. App pre-draw measures an observed app callback, not compositor presentation or completion of Quick Settings' animation. Correctness checks inspect actual attached native Views and still require visible, measured entries, stable bounds and real touch dismissal. Accessibility is recorded separately. If that provider omits a window, the test records a timeout and does not fabricate whole-run accessibility percentiles.

Repeated replacement/add/remove operations on the API 34 emulator reproduced an unavailable tile with a binder token absent from SystemUI's tile map. Restarting the owned emulator restored valid binding and the final 75-assertion suite passed. This setup failure is preserved as evidence; no speculative application retry or global settings change was added.

The final signed package uses the original certificate SHA-256 `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`, retains exactly the five reviewed permissions, and is non-debuggable. New source/release publication and independent F-Droid server reproduction are separate actions. Historical 1.3.0 reproduction results do not qualify 1.3.2 automatically.

The installed production APK was pulled back and its SHA-256 matches the delivered `ClipHistory-1.3.2.apk`: `2e8998f35550dc61ad7fd1da0b8692510f2c93cf08493894125689b9549f8ff2`. It was installed with `adb install -r`; the original first-install timestamp remains unchanged. Production smoke testing does not write the shared clipboard, change preferences or capture screenshots. Private clipboard contents are not exported in its report.

## Reproduction

Use disposable emulator history for the clipboard-writing suite. Physical-phone checks use only the isolated validation package and never copy an entry.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat -PvalidationBuild :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <owned-emulator> shell am instrument -w -e suite tile app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
adb -s <phone-with-validation-tile> shell am instrument -w -e suite tile-performance -e tilePresent true -e tilePage 2 app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
python tools/audit_source.py
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/build-windows.ps1
```

Raw phone videos, SystemUI images and live diagnostic logs remain private outside the source repository. System animation scales remain at 1.0. Temporary lifecycle probes are removed from the delivered source.

Android still controls its ordinary shade collapse, blur and binding schedule. No zero-delay, zero-jank, every-OEM or every-future-opening guarantee is made. The fix removes the additional Activity/task transition and the candidate's incorrect service-lifetime dismissal.
