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
| 8 | Floating quick-copy tile | Complete measured checks: row fix passes 30 stable-size loads, p95 123.97ms. Third requested schedule passes 12/12; all 1,147 encoded frames reviewed. No app deadline miss is reported in 300 app-window frame records; one late presentation is classified SurfaceFlinger Scheduling. Android's icon expansion/fade remains; zero-delay/zero-jank is not established. API 34/36 suites pass, with the API 34 stale-binding precondition disclosed |
| 9 | Welcome and setup guide | Complete: 24 actual onboarding, acknowledgement, recreation, optional support and seven-step guide assertions pass API 35 |
| 10 | Regression and lifecycle harness | New suites compile; external lifecycle driver implemented with exact emulator/UID/PID guards |
| 11 | Runtime acceptance and measurement | Recorded in release-1.3/COVERAGE.md: live, regression, layout, Settings, customization, privacy, paused fork connection, native tiles, maximum history and 30-minute idle evidence. Final signed 1.1 and 1.2 upgrades pass. Actual kernel wakeups, exhaustive TalkBack audio, multi-day/OEM coverage remain unmeasured |
| 12 | Source audit, checks, signed APK | Exact revision a72134900c574a354a366b24c979f7903b6ace35 builds in clean Windows/Linux clones with identical bytes; original-key signed reproduction verifies. Clean release lint: 0 errors, 6 warnings. Final APK upgrades from signed 1.1 and 1.2 pass on the owned API 35 emulator |
| 13 | Local release documentation and F-Droid preparation | Complete: public v1.3.0 tag/reference APK, final evidence review and verified complete source/APK archive. Historical metadata and latest-only submission copy both pass metadata/update/source/APK/actual build/reference checks. Signed production phone update succeeds with the same UID and exact installed APK digest; temporary validation packages/tile are removed. Exact ID lookup returns HTTP 404; scoped MR/RFP issue searches return zero |
| 14 | External F-Droid submission | Blocked: GitLab browser is signed out. A sign-in handoff is open; authenticated public fork, actual fork CI and inclusion MR are unfinished. Maintainer acceptance is an external decision. The ready-to-submit recipe and MR draft are included |

Completion requires durable history migration, selectable behavior, meaningful unit and runtime validation, accurate platform limits, a verified signed APK, and a reviewed final diff. Publication acceptance is an external result and must not be invented. Non-root Shizuku still requires starting after reboot.
