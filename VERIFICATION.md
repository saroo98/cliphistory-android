# Current local candidate: ClipHistory 1.4.4

The 1 November 2026 candidate is **1.4.4/code 14**, minimum API 30. Current checks, signed APK details, device findings and remaining limits are recorded in [1.4.4 qualification](docs/release-1.4.4/RELEASE.md). The candidate must be rebuilt from the release commit before submission. See the [reusable F-Droid guide](docs/F_DROID_ANDROID_GUIDE.md).

## Historical local 1.4.1 qualification

The earlier review follow-up and Android 14–17 evidence are retained in [1.4.1 qualification](docs/release-1.4.1/RELEASE.md). Those results do not automatically qualify the 1.4.4 binary.

## Historical local 1.4.0 qualification

Local 1.4.0/code 10 adds authenticated encryption for all stored history, optional source-marked sensitive capture, and configurable expiry without changing the ordinary native workflow. Fresh results and explicit limits are in [the 1.4 qualification report](docs/release-1.4/RELEASE.md); progress is recorded in [implementation status](docs/release-1.4/IMPLEMENTATION_STATUS.md).

The final JVM gate passes 65 tests, release lint has zero errors, and source checks pass 74/74. Code-10 Android encryption/migration (95), sensitive live (38), UI/expiry/retry (23), regression (12), quick errors (21), storage threading (5), native tile (353) and capacity (5) checks pass. Three queue-delay regressions failed before the correction and pass afterward. The earlier legacy-helper upgrade passes 160 assertions; a live encrypted code-9 → code-10 upgrade preserves its key/payload/metadata. All twelve retained code-9 idle openings pass and 1,167 encoded frames were reviewed, with missed deadlines disclosed. Code-10 warm/cold measurements pass the baseline regression gate. The final signed Pixel upgrade retains the 16-entry count and observed settings; production diagnostics report AES-256-GCM, StrongBox and a passed expiry clock. Detailed results and coverage limits are in the report and [checks.json](docs/release-1.4/checks.json).

No 1.4 commit, tag, public release, remote recipe update or reproducibility qualification has been performed. The following publication and older-version results are historical records, not new checks or qualification of 1.4.

# Historical ClipHistory 1.3.4 qualification

The approved logo is integrated throughout Android and the release/store materials. Fresh native checks and visual evidence are in [the branding report](docs/release-1.3.4/BRANDING.md). Frozen-source qualification and current external status are in [F-Droid publication](docs/F_DROID_PUBLICATION.md); exact results are in [checks.json](docs/release-1.3.4/checks.json).

- Clean Windows and Linux builds of `97c1e996e2619a5cf6f86f7f70614576d795b8c3` have identical unsigned bytes. Signature copying reproduced the published original-key APK exactly. The full local F-Droid recipe, source/APK scans and reference comparison passed.
- 44 JVM tests passed with zero failures/errors/skips; release lint has zero errors and 14 disclosed warnings; distribution source audit passed 72/72.
- Fresh API 36 emulator checks: Settings 21, Onboarding 24, Native UI 33; native HTML-port regression 121 light + 121 dark assertions. Actual Welcome/About and launcher artwork were visually checked. HTML browser rendering remains unverified because local-file URLs were blocked; JavaScript syntax and asset wiring passed.
- Signed in-place Pixel 8 Pro upgrade succeeded; installed package reports 1.3.4/code 8. No personal history was inspected and no uninstall was used.
- The immutable v1.3.4 release is published. MR !50917 now pins it. A new maintainer CI run is requested; the automatic fork run has zero jobs under GitLab's account verification gate. All nine jobs of the previous 1.3.3 maintainer pipeline passed, which does not qualify 1.3.4.

The native UI work remains documented in [HTML_UI_PORT.md](docs/HTML_UI_PORT.md), with the prior tile fix in [TILE_LAUNCH_FIX.md](docs/TILE_LAUNCH_FIX.md). Historical evidence below does not qualify the current binary. No zero-latency, universal capture or every-device claim is made.

# ClipHistory 1.3.0 verification

Package `app.cliphistory`, code 4, minimum API 34, compile/target API 37. Results and limits are recorded separately from the 1.2 release.

- 28 JVM test methods pass, including the aggregated core suite, six migration cases and nine recovery-policy cases. Debug/release lint and app/test assembly pass. Source audit: 70/70.
- Actual runtime coverage includes unique/consecutive policies, signed upgrades, recovery and stop semantics, reboot/pause, offline history, 200% layout, Settings, optional prompts, privacy and floating quick copy. See [coverage](docs/release-1.3/COVERAGE.md).
- Thirty final Pixel warm app loads preserve identical window dimensions and meet the 500 ms p95 target: observed 123.97 ms. SystemUI/accessibility availability is a separate measurement, p95 711 ms. The final requested idle schedule passes 12/12 openings; all 1,147 encoded video frames were reviewed. No App Deadline Missed classification occurs in 300 app-window frame records; one late presentation is classified SurfaceFlinger Scheduling. Android's icon expansion/fade remains. See [frame review](docs/release-1.3/TILE_TIMING.md).
- Real Pixel Shizuku-fork attachment passes while paused, without clipboard writes. Disposable emulators use official Shizuku and verify real capture.
- Clean Windows and Linux builds of exact revision `a72134900c574a354a366b24c979f7903b6ace35` produce identical unsigned APKs. Signature reproduction produces the exact public signed APK. Original certificate SHA-256: `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`. Final signed upgrades from 1.1 and 1.2 pass on the owned API 35 emulator.
- F-Droid server 2.4.5 readmeta, rewritemeta, lint, tagged-update discovery and actual local build pass. Source/APK scans and comparison against the downloaded public reference binary pass with the allowed original signer. GitLab fork, CI and inclusion submission require authenticated account access and remain unfinished.

The [release report](docs/release-1.3/RELEASE.md) records digests, commands, selected evidence and bounded assessments: overall **87/100**, publication preparation **85/100**. These are engineering judgments, not certification or F-Droid approval.

The app requests exactly Shizuku access, foreground-service, special-use foreground-service, notifications and boot-completed permissions. It has no internet/overlay/accessibility-service/keyboard permissions. History is private and excluded from backup. Screenshots default to blocked and can be enabled separately from Recents privacy.

No zero latency, universal success, exhaustive TalkBack audio traversal, kernel wakeup count, multi-day battery soak or every-OEM coverage is claimed. Non-root Shizuku must be restarted after reboot. Unknown owner-stop descriptions fail closed. F-Droid inclusion and main-server verification are external decisions.

Historical 1.2 evidence remains in [docs/release-1.2](docs/release-1.2/); its reproduction and recipe success do not qualify 1.3.
