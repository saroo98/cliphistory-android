# ClipHistory 1.3.0 verification

Package `app.cliphistory`, code 4, minimum API 34, compile/target API 37. Results and limits are recorded separately from the 1.2 release.

- 28 JVM test methods pass, including the aggregated core suite, six migration cases and nine recovery-policy cases. Debug/release lint and app/test assembly pass. Source audit: 70/70.
- Actual runtime coverage includes unique/consecutive policies, signed upgrades, recovery and stop semantics, reboot/pause, offline history, 200% layout, Settings, optional prompts, privacy and floating quick copy. See [coverage](docs/release-1.3/COVERAGE.md).
- Thirty Pixel warm app loads meet the 500 ms p95 target: observed 129.17 ms. SystemUI/accessibility availability is a separate measurement, p95 704 ms. See [frame review](docs/release-1.3/TILE_TIMING.md).
- Real Pixel Shizuku-fork attachment passes while paused, without clipboard writes. Disposable emulators use official Shizuku and verify real capture.
- Release signing retains certificate SHA-256 `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`. Final exact-source Windows/Linux reproduction and F-Droid recipe verification remain pending until performed.

The app requests exactly Shizuku access, foreground-service, special-use foreground-service, notifications and boot-completed permissions. It has no internet/overlay/accessibility-service/keyboard permissions. History is private and excluded from backup. Screenshots default to blocked and can be enabled separately from Recents privacy.

No zero latency, universal success, exhaustive TalkBack audio traversal, kernel wakeup count, multi-day battery soak or every-OEM coverage is claimed. Non-root Shizuku must be restarted after reboot. Unknown owner-stop descriptions fail closed. F-Droid inclusion and main-server verification are external decisions.

Historical 1.2 evidence remains in [docs/release-1.2](docs/release-1.2/); its reproduction and recipe success do not qualify 1.3.
