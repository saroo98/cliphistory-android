# ClipHistory 1.4.5 initial qualification

This is the retained initial-candidate record. The later package-update recovery fix and its exact build are recorded in [FINAL_QUALIFICATION.md](FINAL_QUALIFICATION.md). Results below remain tied to the initial hashes; they are not silently reassigned to the later APK.

Local candidate, version code 15. This record separates product fixes from upstream limitations and evidence still needed. The public F-Droid submission previously targeted 1.4.4; this document does not claim publication or maintainer approval.

## Decisions from comparable projects

Reviewed 1 November 2026:

| Evidence | Decision for ClipHistory |
| --- | --- |
| [Install with Options](https://github.com/zacharee/InstallWithOptions/blob/main/app/src/main/java/dev/zwander/installwithoptions/util/ShizukuUtils.kt) checks the live Binder before manager discovery. [Mihon issue 3575](https://github.com/mihonapp/mihon/issues/3575) reports a fork-detection failure. | A running compatible API takes precedence over manager package names. Known packages serve only as shortcuts. Multiple installed managers require an explicit user choice for both manager and battery settings. |
| [Octoclip's setup](https://docs.octoclip.app/features/source/background-monitoring/android/shizuku) separates setup, actual external copies and background recovery. | Verify external capture, sensitive expiry and lifecycle separately. A successful connection indicator is insufficient. Record forced idle separately from natural overnight use. |
| [Easy Clipboard](https://github.com/Qutaiba-Khader/easy-clipboard) uses several capture mechanisms. | Retain the existing shell service and normal keyboard. Adding an IME, accessibility service, overlay permission or root fallback would broaden permissions and interaction costs without fixing the confirmed package-discovery defect. |
| [Porter's compatibility documentation](https://porter.darken.eu/compatibility) distinguishes its companion from direct API support. | Require the companion for upstream UserService operations. Never infer UserService support from the manager's brand or Binder alone. |
| [Shevery 14.6.1 approval activity](https://github.com/HmnDev-Tech/shevery/blob/14.6.1/manager/src/main/java/moe/shizuku/manager/authorization/RequestPermissionActivity.kt) and its server send/validate caller identity. | Preserve approval and shell-UID checks. Offer manual approval in the service manager if its prompt closes. Do not spoof an approval intent or silently grant permission. |

## Risk and disposition register

| Area | Confirmed finding / boundary | Resolution or required evidence |
| --- | --- | --- |
| Renamed manager | Older code checked the official launcher before the live Binder. | Binder-first connection and real capture tests with alternate manager packages. |
| Several managers installed | Both manager and battery shortcuts lacked a usable destination. | Explicit chooser shared by both shortcuts. Regression must check the selected package, cancellation and the system battery-settings destination. |
| Fresh Shevery approval | Fresh approval on Android 17 reproduced the prompt closing with `No valid UID passed in intent`. | The manager's Application management screen allowed the validation app; return/reconnect and all 34 live assertions passed. Preserve that user-controlled workaround. The upstream prompt itself remains defective on this tested combination. |
| Official Shizuku 13.6 on Android 17 | Restarting its server did not deliver the Binder to the already-running background client within 45 seconds, twice. Opening the client again connected and captured. Its manager also showed an empty authorized-app list despite granted access. | Reproduced upstream-version limitation, not fixed in ClipHistory. Use a manager release compatible with that OS or open ClipHistory again after starting the service. Shizako's F-Droid build passed an isolated server restart. The phone used thedjchi Shizuku 13.7, not this failing 13.6 build. |
| Sensitive flags | Unmarked private text cannot reliably be classified by the clipboard API. | Explain this at the Privacy setting; keep capture opt-in, encryption and expiry. Test marked, unmarked and recopy cases. Do not introduce unreliable secret guessing. |
| Expiry scope | Saved-history expiry does not erase Android's shared clipboard or other apps' copies. | State the boundary beside auto-delete and in help. Verify the two encrypted snapshots and offline readers expire timed entries. |
| Test version gate | Matrix driver hardcoded `1.4.4-validation`. | Require the caller's exact validation version, verify the installed package and record APK hashes. |
| HyperOS 3 | Reviewer reported a helper-start failure; similar MIUI 13 failure was reproduced and fixed previously. | HyperOS 3 remains a hardware/retester gap. Need exact firmware, manager/build variant, setup mode, sanitized exception, connection test and external A/B/A/B capture on the candidate. A Pixel or stock emulator does not close it. |
| Android 13 tile | Earlier candidate had an unexplained intermittent SystemUI timeout. | The current candidate passed the 75-assertion tile suite. Additional final-repeat results are recorded below. Passing reruns establish observed success, not the root cause of that old timeout. |
| Long-term background | Prior short tests do not prove multi-day or every-OEM reliability. | Test bounded idle, process/service death and reboot now; record duration, state and manual restart prerequisites. Keep natural overnight use explicitly unverified until performed. |
| Build variants | Earlier review lacked the distinct F-Droid Shizako artifact. | Downloaded the actual CI-built `zako3.17-f` APK. Fresh discovery/approval with the official launcher disabled passed. Initial operational tests passed, but a surviving alternate watchdog made service attribution uncertain. A clean single-service retest is recorded separately below. The CI test signer is not the eventual catalogue signer. |
| Switching services | A fork watchdog restarted an old shell service during the lifecycle fixture's switch to official Shizuku. Two servers were observed and startup timed out. | Preserve the failed fixture log. Reboot the disposable device, leave alternate managers disabled, start only the intended service and verify one shell server. The app's chooser explains running one service at a time. Killing a server alone does not prove it remains stopped. |
| Stale publication documents | Top-level verification/submission documents still described 1.3.4 or a local 1.4.4 candidate as current. | Update current pointers, version/SDK/permission facts and release gates. Retain dated historical evidence without implying it qualifies 1.4.5. |
| Signed Android 13 upgrade | Public signed 1.4.4 to the initial candidate preserved two exact synthetic texts and known settings, but the recorder did not reconnect within 35 seconds. | Diagnosed as an installer exit being mistaken for a deliberate user stop on older Android. Fixed in the later candidate; see its separate signed-upgrade and genuine Force stop evidence. |
| Unsupported modes | Primary profile, Android 11+, API 13+ shell UID 2000 and upstream UserService are the contract. | Continue to reject root/device-owner-only incompatible modes. Work profiles, multi-user and arbitrary unknown forks are not certified. |
| Publication | Local APK tests do not certify an immutable public release or F-Droid's recipe. | Freeze reviewed source, commit/tag/build with authorization, independent reproduction/scans, update the existing recipe, current CI and maintainer review. Never move a published tag or replace its APK. |

## Evidence rules

Results are recorded with actual APK hashes and device/OS versions. Validation APK tests use a separate synthetic-data package. Physical production observations export only known settings and counts, not personal history. Source checks, unit tests, debug instrumentation, signed production checks and independent builds prove different things.

No universal bug-free, all-forks, all-OEM, zero-latency, forensic-erasure or independent security-audit claim is made. External blockers stay visible until tested.

## Exact artifacts and build evidence

The tested production source is a local working tree based on `cabdab5`, not a release commit. Tests and documents were refined after the runtime APK build; its app code/resources were unchanged during the final device matrix. Private raw logs and screenshots are under ignored `reports/release-1.4.5/`.

| Artifact | SHA-256 |
| --- | --- |
| Signed local 1.4.5 APK, code 15 | `1549a0a0644d2736a7ac76b9a7357e6f4dd8a999075ea659a961a7dfcb0de493` |
| Validation app | `c22214b27932b6e17436286eabd83130b5de8e692a703fcf80e55c14c55e310d` |
| Validation tests after rotation-test timing correction | `06ebe38c8680e9a9ec230c197845278e86530eeb6403e382375425555b9b4cf1` |
| Final validation tests with explicit live-test policy and event wait | `438359e5461150bfdae56960192960e934c6885b7bdd0d500ae9f59e778d29ac` |
| Independent Windows/Linux unsigned APK | `9074116e6651f95e879a6cc3648cb09c2b5c496323827f6b5d3dc22fddd5b99c` |
| Production signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| Shizako F-Droid CI APK | `13656bc25f705c7abf31cfdbff5edd84b963133c694a26fa02c97f5c3baf9e7d` |

Shizako provenance: [F-Droid build job 17627963465](https://gitlab.com/fdroid/fdroiddata/-/jobs/17627963465), upstream source `41979314eda0303fc49c8f9519bc6993a9775455`, package `com.churan.shizako`, code 317, version `zako3.17-f`. Its temporary CI signing certificate SHA-256 is `bbff4c1333a850b373539790af7f1282ac29940c4e48544f2270bf555667bb06`. It was installed only on a disposable emulator. This tests the build variant, not an upgrade from an eventual F-Droid production certificate.

The Windows build passed 79 JVM tests with no failures, errors or skips, release lint with zero errors and 24 warnings, and the 74-check source audit. An independent Linux build matched the unsigned APK; `apksigcopier compare --unsigned` passed against the signed candidate. The first source-only Linux fixture omitted Git provenance and differed only in `META-INF/version-control-info.textproto`; reproducing the same Git HEAD without changing snapshot source resolved the difference. F-Droid source scanning reported zero fatal findings with no custom exclusions; APK scanning passed. Signature and 16 KiB zip-alignment checks passed. This does not replace the exact public-commit/reference recipe check before publication.

The 24 lint warnings remain visible: ten private-API warnings cover the shell clipboard/bootstrap integration; two concern an inlined sensitivity-key constant exercised on API 30; one concerns synchronous durable preference acknowledgement, deliberately performed on the client worker; one is a newer-Gradle advisory; one is an API-specific manifest attribute; one is an English plural candidate; one is an unnecessary resource API qualifier; seven are unused strings. No permission or privacy check was weakened to suppress them. Android/OEM private-API compatibility remains a real external risk.

## Physical-phone and UI evidence

The exact signed APK was upgraded in place on a Pixel 8 Pro, Android 17, using the existing thedjchi Shizuku 13.7 service. The installed APK hash matched. Before/after observations matched the history count, limit, recording state and all ten inspected settings; they are not a byte-for-byte export of personal history. Diagnostics reported AES-256-GCM, StrongBox and a passed expiry clock.

- Signed production connection test passed with the prior pause state and eligible clipboard restored.
- Isolated physical live verification passed 20 assertions: boot identity recovery/rejection, field-only settings update, actual marked capture, interleaved recopy without duplication/downgrade/timer extension, one-minute expiry in both encrypted snapshots, offline read and clipboard restoration. No real secret was used.
- Seven additional synthetic-memory assertions passed on the phone for queued sensitive expiry, committed classification and expired Undo.
- The validation manager-chooser suite passed 17 assertions with three installed managers. On the signed production app, cancellation and the actual selected Shizuku Android battery-settings page were checked. Its light-theme chooser screenshot was visually inspected.
- Temporary validation authorization was revoked afterward. The original service and production recorder remained running; no phone reboot, history wipe, personal-history export or global clipboard-warning change was used.
- New privacy descriptions were visually inspected in light and dark native captures. Offline-sensitive controls were correctly disabled while local screenshot settings remained usable.
- The chooser was visually inspected on the emulator in light and dark themes and dark theme at 2.0 font scale. Both package identifiers, actions and Cancel remained visible without overlap. A separate two-manager shortcut test passed 12 assertions on Android 16.1. Font scale was restored afterward.

The API 37 screenshot test initially caught a dark, redacted rotation frame after a fixed 300 ms delay. It did not show private content. The test now waits for UI idleness before asserting the protected surface; the black-surface assertion is unchanged. Both API 30 and 37 passed afterward. This was a test timing correction, not a claim of a newly fixed privacy leak.

## Platform matrix and retained failures

All rows use the validation app hash above. These are targeted suites, not a claim that every UI control was exercised on every OS. Exact fingerprints, test hashes, elapsed times and per-suite assertions are in each local `checks.json`.

| OS / API | Verified current-candidate coverage |
| --- | --- |
| Android 11 / 30 | Live capture (34), sensitive retention (40), Settings (22), sensitive UI (23), accessibility structure (22), encryption (99), recovery failures (11), boot identity (9), helper failure (10), tile (75). Upstream Shizako 3.17. |
| Android 12 / 31 | Live capture (34), Settings (23), encryption (99). Official Shizuku 13.6.0. |
| Android 12L / 32 | Live capture (34), Settings (23), encryption (99). Official Shizuku 13.6.0. |
| Android 13 / 33 | Live capture (34), Settings (23), encryption (99), tile (75). A second cold fixture passed final live capture (36) and tile (75) again. Official Shizuku 13.6.0. |
| Android 14 / 34 | Live capture (34), Settings (23), encryption (99). Official Shizuku 13.6.0. |
| Android 15 / 35 | Live capture (34), Settings (23), encryption (99). Official Shizuku 13.6.0. |
| Android 16.1 / 36.1 | Final live capture (36), Settings (23), encryption (99), tile (75), sensitive retention (40), two-manager shortcuts (12). Official Shizuku 13.6.0. |
| Android 17 / 37 | Shevery capture (34), sensitive retention (40), three-manager shortcuts (17); 20 resumed suites covering Settings, sensitive UI, accessibility structure, recovery/pending/failures, boot identity, helper failure, privacy, tile, duplicates/regressions, capacity, layout, onboarding, customization, quick-panel and command errors, storage threading, paused connection and encryption. Separate Shizako lifecycle evidence follows. |

The Android 16.1 first cold live run reported `Process crashed`; the old driver had not saved the crash buffer before shutdown, so that original cause remains unknown. A later reproduction reached the marked-sensitive skip assertion and its cleanup obscured the primary error. The policy was verified off, and the same old test passed warm. The test now establishes its own synthetic policy, waits up to eight seconds for the sensitive-event counter instead of sleeping 700 ms, and logs the primary failure before cleanup. The final five-suite run passed without a product-code change. This is stronger test synchronization and diagnostics, not evidence that an unexplained process crash was fixed. The driver now saves crash output on failure.

For the official Shizuku 13.6 restart limitation, startup logs sent the Binder to its manager but not to the already-running client. Its [startup implementation](https://github.com/RikkaApps/Shizuku/blob/v13.6.0/server/src/main/java/rikka/shizuku/server/ShizukuService.java) enumerates installed packages. Shizako's committed [API 37 compatibility shim](https://github.com/cr1437/Shizako/blob/41979314eda0303fc49c8f9519bc6993a9775455/server/src/main/java/rikka/shizuku/server/ApiCompat.java) documents the changed package-list return type and handles both forms. This is consistent with the observed discovery failure; the exact swallowed internal exception was not captured. No speculative polling, permission bypass or weakening of client identity checks was added.

Natural overnight or multi-day OEM reliability, spoken TalkBack navigation, every manager/version/profile and HyperOS 3 remain unverified. Android 16.1 results do not imply a separate API 36.0 test in this run.

## Lifecycle evidence

On the owned Android 17 emulator, the current validation app passed helper death, app death, native Recents dismissal, Android task-manager Stop and force-stop checks. Recovery captured new externally copied synthetic text after recoverable interruptions. Deliberate stops ended capture and did not change the saved files; explicit reopening restored operation.

With only Shizako F-Droid `zako3.17-f` running as shell, server restart recovered capture in the test recorded at 12.033 seconds. Enabled boot recovery reconnected after manually restarting the external service. Pause survived a separate reboot, and a verified external copy left both saved files unchanged while paused. Disabling boot resumption prevented the helper from starting after reboot; disabling automatic recovery prevented a killed helper from restarting during the 20-second observation. The original options were restored for subsequent checks.

The same helper survived 180.001 seconds of forced deep idle, with Android reporting `IDLE` before and after, and captured a new synthetic copy after waking. Forced idle and the simulated battery-unplug state were released in cleanup. A subsequent isolated storage-clear check created a new key record and empty store without resurrecting old entries, then captured a new copy. These destructive checks affected only the disposable emulator's validation package.

A clean single-service Shizako F-Droid retest, after rebooting away the alternate watchdog, passed live capture (36), sensitive retention (40), Settings (23) and encryption (99) using the final test APK. The observed sole shell server was `shizako_server`; the official and Shevery managers were disabled. This supersedes the initially ambiguous operational attribution, not the separate genuine fresh-consent observation.

The original reboot driver intermittently failed to open Settings during the post-boot transition. It now reads the acknowledged visible recorder status and retains the actual copy/no-copy assertions; passing the metadata read alone is insufficient. One disposable-emulator run showed a System UI non-response dialog; the saved `lastanr` dump had no entry, so no internal cause is assigned. Choosing Wait allowed the remaining recovery checks to run. These fixture failures remain in the local evidence. No product-code edit was made to conceal them.

## External review state

The public [ClipHistory MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) was re-read on 1 November. It still targets 1.4.4, and [maintainer pipeline 3014908103](https://gitlab.com/fdroid/fdroiddata/-/pipelines/3014908103) is shown as passed. The latest maintainer [fork report](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4135852044) refers to Shizako MR !50819; it does not give an exact failure trace. The local discovery defect and now-tested CI variant directly address that integration, but the reporter's exact symptom still requires their retest. HyperOS 3 remains separately untested. No comment, tag, push or recipe update was made by this qualification work.
