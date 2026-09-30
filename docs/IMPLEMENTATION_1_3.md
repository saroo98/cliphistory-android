# ClipHistory 1.3 implementation status

Approved scope: the supplied 1.3 plan. Existing native Views, one module, Shizuku serial writer, private snapshots, and release signing identity are retained. No new production dependencies.

| Step | Work | Status |
|---|---|---|
| 1 | Baseline and safe test targets | Complete: existing core and release-data tests pass; Pixel connected; validation package required |
| 2 | Preferences and recovery policy | Complete: nine recovery-precedence tests pass; real read-only-directory failure confirms preference rollback and recovery |
| 3 | Unique history and v2 migration | Complete: duplicate, legacy migration, first-write and mirror-failure tests pass, including immediate same-policy cleanup retry |
| 4 | IPC and independent offline reads | Online stale-ID/policy/mirrored-read assertions pass on API 35; concurrent offline reads pass on Pixel API 37 and emulator API 34 |
| 5 | Recovery, boot, explicit-stop guard | API 35 helper/app recovery and Force stop/Task Manager stop pass after the Recents fix; capture after native Recents dismissal passes with recovery On and Off; reboot reconnect and pause/no-capture assertions pass; unknown user-stop descriptions fail closed |
| 6 | Window and Recents privacy | Actual Main and floating-panel OS captures pass on Pixel. Both common modal window constructors pass blocked/allowed/blocked OS capture on API 35 through the real Settings callback. Basic focus/labels pass; exhaustive TalkBack audio traversal is not measured |
| 7 | Settings and battery navigation | Settings/rotation assertions pass on Pixel API 37 and emulator API 34; direct Pixel battery page rendered and correct |
| 8 | Floating quick-copy tile | The measured row-reservation fix passes 30 warm loads with identical 1080x999 window sizes, p95 123.97ms; API 34/36 native suites pass. API 34 needed an owned-emulator reboot after stale SystemUI bindings prevented the first test precondition. The third full timed schedule is in progress on this fix. Android's icon expansion/fade remains; zero-delay/zero-jank is not established |
| 9 | Welcome and setup guide | Complete: 24 actual onboarding, acknowledgement, recreation, optional support and seven-step guide assertions pass API 35 |
| 10 | Regression and lifecycle harness | New suites compile; external lifecycle driver implemented with exact emulator/UID/PID guards |
| 11 | Runtime acceptance and measurement | Recorded in release-1.3/COVERAGE.md: live, regression, layout, Settings, customization, privacy, paused fork connection, native tiles, maximum history and 30-minute idle evidence. Actual kernel wakeups, exhaustive TalkBack audio, multi-day/OEM coverage remain unmeasured. Signed upgrades qualify the earlier core candidate; final APK upgrade check pending |
| 12 | Source audit, checks, signed APK | Current row fix: audit 70/70, JVM tests, debug/release lint and unsigned build pass. Clean exact-revision Windows/Linux release qualification is next; old signed candidate is not the final APK |
| 13 | Release documentation and F-Droid evidence | In progress: README, startup, build, security, device checklist, Fastlane description and changelog updated; fresh evidence gathered separately from 1.2. Exact clean-source reproduction/recipe and public handoff pending |

Completion requires durable history migration, selectable behavior, meaningful unit and runtime validation, accurate platform limits, a verified signed APK, and a reviewed final diff. Publication acceptance is an external result and must not be invented. Non-root Shizuku still requires starting after reboot.
