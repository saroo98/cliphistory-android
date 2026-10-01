# ClipHistory 1.3.1 UI and UX review

## Scope and method

The owner requested a premium, minimal Android clipboard utility and authorized implementation, emulator validation, and a local commit. Preserve the existing quiet mineral palette, native Views architecture, exact clipboard contents, privacy protections, and customization. No new production dependency or remote publication belongs to this change.

Two independent Impeccable reviewers, both GPT-6.1 Sol with maximum reasoning effort, assessed the incumbent source and native evidence before implementation. Assessment A scored design 79/100 and Nielsen usability 30/40. Assessment B scored native technical health 16/20. Neither established a P0 or P1 defect. These are reviewer judgments, not universal quality certifications.

Native evidence included 138 emulator screen captures, 28 valid large-font/orientation captures, and seven successful motion recordings. Two producer-screen adaptivity captures and one failed automation recording were explicitly excluded. Captures of Android-owned transitions are not app layout defects. Motion samples cannot establish physical-device frame pacing. Historical 81-assertion validation is only a baseline; fresh results follow separately.

## Findings and implementation scope

| Finding | Small coherent correction |
|---|---|
| Settings → guide → Back loses the user's place | Preserve parent and scroll state through Help, notices, and Activity recreation; support toolbar and system Back. |
| Android 14 ignores the registered Back callback | Explicitly enable the platform Back callback in the application manifest. Verify real Back navigation on supported older Android, rather than inferring compatibility from Android 16. |
| Ten peer overflow actions weaken hierarchy | Five immediate destinations/actions; durable preferences stay in Settings, tests remain in recorder/guide. Preserve all functionality. |
| Consolidated overflow retains the old ten-action height | Measure the existing scrollable content within the available-height cap; fit normal content and retain scrolling for enlarged text. |
| Full-history tile reopens the previous Settings page | Give the tile an explicit one-shot History destination. Return an existing Activity to History, bypass stale nested-page restoration on fresh creation, and preserve history query/data. Later ordinary recreation still restores the user's selected page. |
| An already-listening tile can retain the previous mode's cached launch intent | Observe the existing tile-mode preference while the service is listening, republish its subtitle and direct Activity intent, and unregister on Stop/Destroy. Retain the existing listening-state request for a service that is not listening. |
| Settings hides current limit and theme | Show existing values alongside their controls; separate recording controls from background recovery. |
| Long-press Copy conceals return behavior | Match the full-text action's Copy / Copy and return wording. |
| Raw connection-test codes dominate diagnostics | Friendly known results on screen; retain precise raw data in the copied report. Unknown results remain truthful. |
| Welcome explains support before first use | Explain Shizuku, recording, and the copy-back action; support remains optional. |
| Temporary feedback ignores accessibility timeout | Respect AccessibilityManager's recommended text timeout and retain lifecycle cleanup. |
| Custom panel/help titles lack accessible structure | Identify actual pane titles and headings, without treating ordinary labels as headings. |
| Advanced diagnostics lacks disclosure state | Expose and update expanded/collapsed semantics. |
| Light selected timestamp contrast is 4.394:1 | Slightly strengthen the muted token; recheck all its actual background pairs. |
| Notices header says 1.2.0 | Version-independent header; preserve complete license bodies. |
| Empty quick copy reserves three absent rows | Release reserved row height after a settled empty/error result; retain stable populated loading dimensions. Explain how to save text. |

## Complete Impeccable coverage

| Criterion | Reviewed outcome / verification boundary |
|---|---|
| Product specificity and emotional tone | Text retrieval remains primary; no promotional dashboard, decorative cards, gradients, or forced support flow. |
| Information architecture and cognitive load | Short overflow, explicit settings values, recovery grouping, preserved nested Back context. |
| Hierarchy, spacing, typography | System sans, flat continuous list, restrained sheet elevation; true titles differ from secondary labels. Long text scrolls. |
| Color, contrast, light/dark | Semantic resource tokens in both themes. Normal text target ≥4.5:1. Decorative dividers are not sole control boundaries. |
| Accessibility and platform conformance | Native roles, checked states, 48 dp targets, heading/pane/disclosure semantics, timeout preference. Actual spoken TalkBack remains a separate acceptance boundary. |
| Motion and responsiveness | Existing system/in-app reduced-motion behavior; quick panel adds no window animation over SystemUI. No claim of zero OS scheduling delay. |
| Adaptivity | Phone portrait/landscape and enlarged text; scrollable actions. Tablets, foldables, and OEM battery screens are not exhaustively certified. |
| Content, onboarding, guidance | Honest Shizuku prerequisites and reboot limits, optional support, exact action wording and version-independent notices. |
| States and recovery | Connected/paused/stopped/offline, empty/search/no results, loading, missing/read errors, disabled mutations. Distinguish tested runtime branches from source-only guards. |
| Performance and efficiency | Recycled ListView, bounded reads, IO executor, search debounce, cancellation guards. No framework migration or speculative infrastructure. |
| Security and privacy | Screenshot/Recents options remain separate. Mandatory sensitive-clip filtering, private storage, and integrity checks remain intact. Test probes ship only in test APK. |
| Usability heuristics | Status visibility, familiar language, control, consistency, prevention, recognition, efficiency, minimalism, error recovery, and guidance assessed independently. |

Questions skipped: 0 remaining mandatory design issues in the bounded review; the owner supplied priorities, full scope, and approval.

The native `audit` and `critique` workflows are the governing review, followed by one bounded `polish` confirmation. The relevant work covers `distill` (menu), `clarify` (action/result wording), `onboard` (first use), `harden` (unknown/read/empty states), `adapt` (large text and rotation), `optimize` (quick-panel measurement), and color/type/layout/accessibility checks. Purposeful existing motion is assessed without adding an animation for its own sake. `init` or a new brand world, `bolder`, `overdrive`, variants, web/browser detection, and extra decorative assets would not solve an established defect in this incumbent native utility and are intentionally not executed. Reviewing every applicable criterion does not mean applying every mutually opposed styling command.

## Verification

The bounded A/B visual confirmation found no remaining confirmed P0, P1, or P2 design defect in its reviewed phone flows. Subsequent individual-control testing found the full-history tile navigation defect listed above; it received a focused route correction and regression check. Assessment A increased from 79 to **87/100** overall and from 30 to **33/40** for Nielsen usability. Assessment B increased from 16 to **17/20** for native technical health. These are evidence-based reviewer judgments for their inspected subjects; they are not an arithmetic quality guarantee or an exhaustive-device certification.

| Design criterion | Final /100 |
|---|---:|
| Specificity and coherence | 88 |
| Visual hierarchy | 88 |
| Color, themes, contrast | 96 |
| Typography | 86 |
| Layout and spacing | 88 |
| Information architecture | 86 |
| Cognitive load | 86 |
| Interaction and native fit | 89 |
| Motion/responsiveness evidence | 80 |
| Copy and scanning | 88 |
| Onboarding and empty states | 84 |
| Errors and recovery | 86 |
| Settings and customization | 87 |
| Adaptivity | 89 |
| Accessibility evidence | 86 |
| Emotional journey and trust | 88 |

| Native technical criterion | Final /4 |
|---|---:|
| Accessibility | 3 |
| Performance | 3 |
| Appearance and theming | 4 |
| Platform conformance | 4 |
| Adaptivity | 3 |

The ten usability scores, in standard Nielsen order, are 3, 3, 4, 3, 3, 3, 4, 4, 3, 3. The strongest gains are predictable Back, visible current preferences, shorter menus, and consistent copy wording. External Shizuku setup remains inherently technical. No additional design implementation is recommended merely to raise the score. Further confidence would come from spoken TalkBack traversal and a physical-device/OEM pass, rather than a new visual system.

The final application source passed the following fresh checks:

- 28 JVM tests with zero failures or errors; debug and release builds; debug and release lint with zero errors. Release lint retains 11 warnings: the existing non-SDK clipboard bridge, synchronous preference persistence, pinned Gradle version, icon-folder qualifier, three unused strings from the removed duplicate preference sheet, and a plural-candidate warning for capacity. The valid capacity range is 20–500, so the English capacity label is always plural. These warnings are not a warning-free certification or a reason to silently replace the clipboard architecture.
- 70/70 static source checks, XML parsing, and `git diff --check`.
- APK alignment, existing-key v3 signature, exact five reviewed permissions, non-debuggable release, and target SDK 37.
- Android 16/API 36: final tile-source run of all 15 instrumentation suites, 574 assertions. This includes navigation 49, diagnostic presentation/report 23, UI 31, settings 20, privacy 11, customization 9, regression 12, layout 11, recovery 11, onboarding 24, capacity 5, accessibility 23, tile 37, tile performance 291, and quick errors 17.
- A subsequent coverage review added five test-only assertions for the history row's custom accessibility action. The targeted API 36 accessibility suite passed all 23 assertions: the actual accessibility node exposes the correctly labelled action, accepts `performAction`, opens the exact saved text, and leaves a synthetic clipboard sentinel unchanged. The OS timeout setting was restored exactly. The five assertions are included in the final 574-assertion run; they do not certify spoken TalkBack traversal.
- The separate API 36 `quick-errors` suite passed 17 assertions in Light and another 17 in Dark against deliberate invalid payloads in both private validation history slots. The real quick panel exposes its error state and two recovery buttons without reserving absent rows. Actual accessibility-node activation of Retry reloads three exact entries after valid data is restored; Open app closes the error panel and returns Main focus. A separate fresh Main launch reads the same fault fixture through normal startup, then its actual failed-read Diagnostics, system Back, and Try again controls are verified. The final assertion verifies exact restoration of the valid synthetic snapshot. Six final images, three per theme, draw the actual Activity decor; they are explicitly not full-system screenshots or signed-production captures. The initial Home fixture attempt incorrectly waited for an injected-file change event with the helper stopped; the corrected test uses the stated fresh launch. The failed producer output is excluded from visual evidence. ClipboardManager write failure is not simulated.
- Android 14/API 34: final tile-source navigation 49 assertions, live-mode tile 37 assertions, plus earlier menu-source UI 31 and settings 20 assertions. The navigation run reproduces the real system Back path and confirms the manifest correction; both scroll context and Activity recreation checks pass.
- Light muted text against canvas, surface, secondary surface, and selected surface: 5.213:1, 5.560:1, 4.790:1, and 4.527:1. The corresponding unchanged dark-theme pairs are 8.784:1, 7.520:1, 6.671:1, and 4.719:1.
- Fifty fresh adaptivity screenshots cover normal and enlarged text up to 200%, portrait and landscape, both themes, and the complete scrollable Settings surface. These Home/Settings captures predate the final Back opt-in, missing-value summary fallback, and menu sizing correction. Their captured normal values/layout remain unchanged; they are not screenshots of an identical APK hash. A separate final-source menu/font batch verifies the sizing correction.

The fresh normal production core contains 64 original PNGs, 32 per theme. Its APK includes the Back opt-in and missing-value fallback but precedes only the menu sizing correction. Final signed production menu/Back evidence is separate. Final-source minimum-API menu evidence includes observed light portrait/landscape and dark portrait/landscape at enlarged text sizes. Six earlier Dark menu files incorrectly named portrait are explicitly annotated as landscape; their bytes remain preserved. Three additional Dark portrait captures verify 1080×2400 before and after capture and show all five actions plus the wrapped offline explanation. Identical top/bottom pairs are not counted as proof that a paragraph was scrolled.

Thirty fresh warm SystemUI tile openings in the source-matched isolated validation app on API 36 preserve populated window bounds on every trial. App latest-three loading p95 is 199.0679 ms. Native touch to accessibility-observable entries is p50 824 ms, p95 921 ms, maximum 1142 ms. These are different clocks: the latter includes SystemUI and observation overhead. Signed-production native recordings are separately identified. Retained app frame/layout reports are not a recording of every physical display refresh and do not establish zero delay or universal frame pacing.

The validation driver retained failed attempts and their cause. A mutable dialog collection read in the test harness was moved to the main thread after a test-only concurrency failure. The actual Android 14 Back failure was fixed in production. Expected diagnostic failure-code fixtures, omitted host instrumentation status markers, and native-driver selector errors were not misreported as application crashes.

The independent control inventory distinguishes 157 baseline IDs: 102 finite current control/action contracts, nine conditional controls, 40 state/gesture contracts, five relocated overflow IDs, and one superseded sheet. All 102 finite contracts have actual emulator-control evidence: 99 have scoped native interactions and three are exercised through actual Activity/accessibility controls in instrumentation. This is a contract count, not 102 physical buttons or a claim that every possible state combination passed. Seventeen finite contracts have current signed Android 15 evidence; 82 rely on retained earlier/intermediate builds whose corresponding control implementation is unchanged. Every binding identifies its APK/source boundary.

Signed Android 15 checks exercise all nine switches in both directions, radio choices, valid/invalid/canceled capacity edits, duplicate handling, exact full-value clipboard copies, entry/full-text/quick-panel controls, page navigation, guide destinations, Stop/Start, and notification actions. Actual Android notification Allow/Deny/Back and SystemUI Add tile/Do not add tile/Back choices pass. Failed tile-driver attempts remain retained: the initial check compared full and shortened component spellings and expected an app-style Cancel label where Android says Do not add tile. The corrected checks use observed SystemUI button IDs and canonical component membership. A real stopped-recorder not-connected OK and the Listening Home connection-test button also pass.

The real Shizuku-owned permission prompt is separately exercised. Back leaves its noncancelable prompt visible without selecting Allow; Deny shows the app's access-off state and disables Clear; Allow all the time reconnects the recorder and enables mutations. The initial dismissal expectation and omitted-custom-permission-row parser failures remain retained. A missing `dumpsys` row is explicitly unreported, not a fabricated grant result. The [official manager implementation](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/authorization/RequestPermissionActivity.kt) makes the prompt noncancelable, corroborating the observed Back behavior; that source does not certify the installed manager's exact bytes.

Seven of the nine conditional controls have actual emulator runtime evidence. The two operation-failure-sheet buttons, View diagnostics and Done, remain unexercised: a failed command must arrive after the connected guard. Disconnecting beforehand reaches the separately tested not-connected message. No nondeterministic IPC-death race, fake error sheet, or disk-filling fixture is introduced merely to manufacture that coverage. ClipboardManager write exceptions and every persistence/IPC failure remain distinct limits.

The current signed quick panel additionally passes actual 200% text/landscape scrolling: its third entry becomes reachable, copies the exact complete value, and Close remains reachable. Root inspected the actual 2400×1080 top/scrolled frames. Screen-off with an already visible settled panel dismisses it; wake does not expose the panel, and clipboard/history stay unchanged. Font and orientation settings are restored exactly. This is not an asynchronous locked-callback race or secure-authentication test. The preceding command-driven attempt after force-stop produced no observed panel launch; the successful continuation uses the actual visible QS tile and naturally starts listening. Original Shizuku grant, history values and recorder connection are restored; the validation app's authorization was untouched. The final signed-production capture batch contains 20 original PNGs and nine unedited MP4s covering both themes, nested navigation/sheets, search/IME, populated tile copy, theme switching, full-history routing, Motion Off, and both empty panels. All nine clips decode; 52 encoded-frame samples and nine contact sheets retain their selected timestamps. Root directly inspected all nine contact sheets and representative final Light/Dark images. Recorded controls report no failure in this batch; sampled frames show expected native/system transitions and settled layouts, without a new confirmed app glitch. These samples do not certify every physical refresh or zero latency. Original two synthetic history values, semantic settings, QS ordering and animator scale are restored. Actual reseeding changes IDs/timestamps, and acknowledged guide/welcome visits remain acknowledged. The committed build must be separately checked against this tested payload, with its own APK/provenance identity; historical captures keep their original APK hashes.

The accessibility timeout follows [Android's AccessibilityManager contract](https://developer.android.com/reference/kotlin/android/view/accessibility/AccessibilityManager#getRecommendedTimeoutMillis(kotlin.Int,kotlin.Int)). Heading and pane semantics follow [Android Views accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/principles-views).

The mode-refresh correction follows the [Tile direct-launch contract](https://developer.android.com/reference/android/service/quicksettings/Tile#setActivityLaunchForClick(android.app.PendingIntent)) and [TileService listening lifecycle](https://developer.android.com/reference/android/service/quicksettings/TileService#onStartListening()). The last published Activity intent bypasses `onClick`, so refreshing only that callback would not fix a cached destination. The bounded signed-production discriminator separately found that normal shade reopening refreshed both modes correctly on the intermediate build; the failed command-driven empty check retained the old History destination. These different paths are not conflated with an empty-panel rendering defect.

## Release boundary

1.3.1 is a local patch build for this review. Existing F-Droid metadata identifies the published 1.3.0 source/binary and stays unchanged until a real tagged upstream release exists. No commit reference, binary URL, F-Droid acceptance, or publication is fabricated for this local patch.
