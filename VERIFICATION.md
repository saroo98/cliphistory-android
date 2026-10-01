# Current local build: ClipHistory 1.3.2

The quick-copy Activity transition and native-dialog lifecycle fix are recorded in [the 1.3.2 verification report](docs/TILE_LAUNCH_FIX.md). Its current build, device results and limits are separate from the historical release evidence below.

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
