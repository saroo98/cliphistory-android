# HTML UI refinements ported to Android 1.3.3

The reviewed HTML refinements are implemented in the existing native Android Views application. The signed **ClipHistory 1.3.3 / version code 7** APK was installed successfully as an in-place update on the connected Pixel 8 Pro on 2026-10-01. This is a local delivery, not a new public release or F-Droid acceptance claim.

## Qualification boundary

The first local signed 1.3.3 build was installed as an in-place Pixel update on 2026-10-01. Certificate, version, non-debuggable status, launch and Settings were checked. No uninstall, data clear or personal-history mutation occurred. The explicit stopped-recorder preference was respected.

Full private test media and the local delivery report remain outside the public source tree. A clean-source F-Droid reference is qualified separately in [F_DROID_PUBLICATION.md](F_DROID_PUBLICATION.md). The local pre-freeze APK must not be substituted for it. The HTML prototype is a design artifact, not executable Android source or a shipped WebView.

## Native implementation of the 18 findings

| Finding | Native change | Main files | Verification |
|---|---|---|---|
| F01 First setup and readiness | Welcome has Set up recording and Explore; empty guidance reflects readiness; six setup steps place relevant actions next to instructions. | `MainActivity.kt`, `HistoryHome.kt`, `DetailPages.kt` | Onboarding and HTML-port suites; native Welcome/Help captures. |
| F02 Single-deletion recovery | One pending deleted entry lives only in daemon RAM. Persistent Undo/Dismiss restores original ID, timestamp, text and order without evicting newer entries. | `HistoryRepository.kt`, `ClipboardUserService.kt`, AIDL, `MainActivity.kt` | 16 Undo unit tests; real-helper Light/Dark live suites; actual UI deletion/Undo screenshot and count 3 → 2 → 3 on emulator. |
| F03 Modal keyboard leakage | Android Dialog windows provide native modal focus and Back behavior. No HTML/WebView or browser focus implementation ships. | `Ui.kt`, `MainActivity.kt`, `QuickCopyDialog.kt` | Native dialog controls and Back checks; UI/navigation suites. Spoken TalkBack traversal not certified. |
| F04 Lost focus after Save | Tracked dialogs restore the surviving opener; Settings keeps row focus and scroll through appearance recreation and nested pages. | `MainActivity.kt` | HTML-port and navigation checks. |
| F05 Indistinguishable entry actions | View is visible and separately focusable. Copy and View names identify action, entry excerpt and time. Existing accessibility full-text action remains available. | `HistoryAdapter.kt` | Accessibility action tests; measured enabled View width and height are at least 48dp. |
| F06 Weak off switches | Native Switch controls use semantic palette colors with distinct enabled off, on and disabled states. | `Ui.kt`, `SettingsPage.kt` | Native controls/state checks; Light/Dark screenshots. |
| F07 Settings comprehension | Seven groups, visible choice summaries, concise native radio dialogs, recovery subgroups and accurate About/version/source/license rows. | `SettingsPage.kt`, `MainActivity.kt` | Settings/customization/HTML-port suites; entire Settings traversal on Pixel; all 17 expected group/control labels observed. |
| F08 Hidden late search match | Full-text preview highlights all matches and scrolls its own content to the first match. Exact saved text remains selectable/copyable. | `MainActivity.kt` | Late-match test and native full-text captures. |
| F09 Input validation | Limit remains 20–500, with field-associated native error and restored input focus; invalid values do not dismiss or mutate history. | `MainActivity.kt` | Invalid input and successful save tests, including 200% text. |
| F10 Small targets | Visible View control corrected from measured 42dp width to a minimum of 48dp; status, Back, sheet handle and action controls retain native target sizes. | `HistoryAdapter.kt`, `Ui.kt`, `HistoryHome.kt` | Layout/HTML-port measurements; no assertion weakened. |
| F11 System theme | Native System/Light/Dark preferences remain backed by themed contexts; changing appearance recreates the Activity and returns to the selected radio dialog. | `AppSettings.kt` (existing), `MainActivity.kt` | Theme/persistence and Settings restoration checks; both palettes inspected. |
| F12 Tile dependency | Turning tile availability off disables Add tile and explains the dependency. Tile mode is a choice row with the actual current destination. | `SettingsPage.kt`, `MainActivity.kt` | Settings checks and real SystemUI tile-mode tests. |
| F13 Empty quick panel | Empty state provides Open app; offline saved entries remain labeled and usable. | `QuickCopyDialog.kt` | Empty/Open app and offline/error tests. |
| F14 Diagnostic hierarchy | Current health and relevant next action precede three essential fields; technical details are disclosed separately. Raw report remains privacy-safe. | `DetailPages.kt`, `MainActivity.kt` | Diagnostic and command-error suites; Advanced details captures. |
| F15 Battery guidance | Positive current status wording, one primary Open app battery action and optional secondary destinations under Other battery options. Manufacturer and Shizuku limitations remain explicit. | `BackgroundSettings.kt`, `MainActivity.kt` | Status/action checks; native battery sheet captures. External settings depend on the device. |
| F16 Generic errors | Separate unconfirmed delete/clear/limit messages; copy Retry, selectable exact-text fallback and Cancel. Failed reads retain a Retry/Open app route. | `MainActivity.kt`, `QuickCopyDialog.kt` | Real invalid-capacity RPC; corrupt disposable storage; synthetic copy-failure presentation followed by real clipboard Retry. |
| F17 Blank quick loading | Three static, decorative loading placeholders reserve the populated panel bounds; no shimmer, timer, arbitrary launch delay or new window is added. | `QuickCopyDialog.kt` | Loading accessibility/layout tests; 10 warm SystemUI openings with stable loading-to-loaded bounds. |
| F18 Touching error actions | Quick Retry and Open app have separate surfaces with 8dp vertical spacing. | `QuickCopyDialog.kt` | Light/Dark quick-error captures and controls. |

Further native details preserve Help disclosures and reading position, return license Back to its actual origin, reserve space for persistent Undo, retain a pending Undo across issue-only status reads, and apply Reduced motion to the open choice sheet immediately. The app keeps its existing palette, system Roboto, sp text, insets, recycled/paged history list, architecture and dependencies.

The quick dialog's `setWindowAnimations(0)` now runs **after decor/content initialization**. The prior placement was overwritten by the framework theme. A strict post-initialization animation assertion exposed this; moving the override fixed it. Android's own notification-shade collapse remains under SystemUI control.

Undo transactions were appended without renumbering existing AIDL calls. Owner UID checks and the single serialized writer remain. A pending entry/token changes only after the first durable snapshot commit, even if mirror cleanup subsequently fails. First-write failure preserves existing state and pending Undo. Expected full-capacity or stale-token Undo rejection does not mark the recorder unhealthy. No storage schema or migration was added. Clear-all has no Undo; another deletion, explicit Dismiss or helper termination ends the pending Undo.

## Executed validation

Fresh native tests used the isolated validation APK on the owned disposable **API 36 emulator**, 1344×2992 at 480dpi. Synthetic seeding, copying, clearing and corruption tests refuse physical devices. The personal phone received only an upgrade, launch and read-only Settings inspection.

| Gate | Result |
|---|---|
| JVM unit tests | 44 JUnit methods, zero failures/errors/skips; includes 16 deletion-Undo methods and the existing core runner's 55 checks. |
| Static source audit | 71/71 checks passed, including all owner-guarded IPC entries. |
| Final regression matrix | All 18 suite/theme executions passed: HTML port Light/Dark, UI, Settings, privacy, recovery, onboarding, customization, navigation, diagnostics, accessibility, regression, layout, live capture and Light/Dark quick/command errors. |
| Extended matrix | All 7 executions passed: real-helper Undo Light/Dark, HTML port Light/Dark at font scale 2.0, layout 2.0, tile and tile performance. Font scale restored to 1.0. |
| Native visual review | All 15 collected contact sheets inspected, plus supplementary full-system Settings/Welcome/Undo images. Readable Light/Dark, 100%/200% text, portrait/landscape and relevant keyboard layouts were checked. Scroll-boundary cropping is ordinary viewport clipping, not hidden unscrollable content. |
| Pixel UI check | Signed 1.3.3 on Android 17/API 37, security patch 2026-08-05; native Settings route and new summaries observed. Three actual Settings screenshots inspected. No recording preferences changed. |
| Release gate | `testDebugUnitTest`, `lintRelease`, `assembleRelease` completed successfully. Lint: zero errors, **15 warnings**, not a zero-warning result. |
| APK checks | Correct package/version/SDK, non-debuggable, valid signature/alignment and the same five permissions. No INTERNET, overlay or Accessibility-service permission added. |

The warnings comprise one AGP version advisory, one shared-preference commit advisory, one obsolete SDK condition, one plural candidate, four private-API warnings for the existing clipboard bridge, and seven unused string resources. They were not suppressed or represented as all pre-existing. No extra runtime dependency was introduced.

### Tile measurements and boundaries

Ten actual SystemUI tile invocations via its `statusbar` command passed with the same measured panel bounds during loading and after data arrived. App-side first observed loaded pre-draw: **p50 50.9ms, p95/max 114.4ms**. Command-to-accessibility availability: **p50 629ms, p95/max 738ms**. The latter includes SystemUI and observation overhead. The pre-draw timestamps do not measure physical display presentation or every compositor frame. These are warm API 36 emulator measurements, not a zero-delay hardware guarantee or a new fourteen-minute idle test.

Real-helper Undo tests cover pending notice through navigation, two rotations and an issue-only UI status; full-history refusal without eviction; exact durable restoration; newer deletion replacing the old token; stale rejection; and Dismiss. The metadata-loss boundary is an injected UI Bundle, with the actual daemon token checked separately; it is not presented as an induced IPC failure. Copy-denial presentation is also injected. Android clipboard permission denial itself was not induced, although Retry used the real ClipboardManager and preserved exact Unicode/whitespace.

Historical failed runs are retained. Final tests fixed harness scheduling and route assumptions, rather than weakening assertions: Settings scroll is checked after layout; Dialog dismissal after the UI callback; appearance is opened through Settings; and only the one visible tracked dialog is selected. The measured View target and overwritten window animation were actual production defects corrected before the final successful matrices.

### Reproduce on a disposable emulator

Use the existing JDK/SDK and commands documented in `BUILDING.md`. The validation app and test APK are never distributed in the release.

```powershell
.\gradlew.bat --no-daemon -PvalidationBuild :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <disposable-emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <disposable-emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <disposable-emulator> shell am instrument -w -e suite html-port -e theme light app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
adb -s <disposable-emulator> shell am instrument -w -e suite html-port -e theme dark app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
# html-port-live requires authorized shell-mode Shizuku on that disposable emulator.
adb -s <disposable-emulator> shell am instrument -w -e suite html-port-live -e theme light app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
.\gradlew.bat --no-daemon -PunsignedRelease :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
python tools/audit_source.py
```

Signing reused the existing local identity through environment-backed password arguments; no signing secrets are in this report. Certificate verification precedes any in-place phone install. A mismatched identity must not be bypassed by uninstalling.

## Evidence and completion limits

The controlled 2026-10-01 runtime checks above used native 1.3.3 code before its publication source freeze. Selected synthetic native render captures are upstream in `fastlane/metadata/android/en-US/images/phoneScreenshots/`. They contain no personal clipboard content or physical-device media. Full logs and private media are retained locally. Exact-source build and scanner evidence is separate in `F_DROID_PUBLICATION.md`.

The UI port, local release build and phone update passed the stated checks. This does not certify spoken TalkBack/Switch Access, every OEM, tablets/foldables, long-term battery use, long idle/cold-launch performance, every source application's capture or F-Droid acceptance. API 34 was not rerun for this port. No perfect score or universal zero latency is inferred.
