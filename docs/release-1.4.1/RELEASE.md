# ClipHistory 1.4.1: review follow-up and local qualification

**31 October 2026. Local candidate, version code 11. Not a published release or F-Droid acceptance.**

The unpublished 1.4 encryption and sensitive-text implementation is retained. This follow-up fixes confirmed privacy and recovery defects, addresses the actionable review requests, and tests the resulting app without using the physical phone. No commit, push, tag, release upload or reviewer message was made during this follow-up.

## 1. Candidate identity

| Item | Measured value |
|---|---|
| Application ID | `app.cliphistory` |
| Version | `1.4.1`, code `11` |
| Minimum / target / compile SDK | 34 / 37 / 37 |
| Signed APK | `ClipHistory-1.4.1.apk`, 2,648,382 bytes |
| Signed APK SHA-256 | `be2cf0e62d86f85593b78093ecc5ee58b0bb2e61425cb52d4404bfcf357a67be` |
| Signing certificate SHA-256 | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| Unsigned APK SHA-256 | `7dfa169e354933d242f8acb5a364705bd0f408f44b647c7ecf9fcd9b00d8afb9` |
| Isolated validation APK SHA-256 | `0fe754a8450d90db014e2278fafce336121abe2f4efe653be3d7dbfb0116c09b` |
| Source state | Working tree based on `6009e25f548a5f1a45d6cf129d288056e76d5b8f`, including preserved uncommitted 1.4 work and this follow-up |
| Public/submitted version | Still 1.3.4/code 8, source `97c1e996e2619a5cf6f86f7f70614576d795b8c3` |

The validation package is `app.cliphistory.validation`. It uses the same app source with a separate ID and test-only instrumentation. The signed production-ID APK was additionally exercised through a real 1.4.0 to 1.4.1 update. These are different binaries; their hashes and exact test-APK hashes are recorded in [build checks](build-checks.json) and each matrix report.

The previously delivered 1.4.0 APK remains unchanged. Do not uninstall a real installation to update. Use the same signing identity. The v3 encrypted history format is unchanged from 1.4.0; older plaintext versions cannot read it.

## 2. F-Droid review: findings and disposition

All 14 visible comments in [merge request 50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) were inspected. The latest inspected comments were dated 27 October. The existing successful 3 October pipeline qualifies the submitted 1.3.4 recipe, not this candidate.

| Review item | Action and evidence | Status |
|---|---|---|
| Actual clipboard capture must be tested; launch-only testing is insufficient | Real official Shizuku 13.6.0 shell-UID-2000 helpers captured synthetic text written by a separate app on APIs 34, 35, 36 and 37. The live suites check exact text, ordering, deduplication and saved storage. | Verified on the measured stock emulator builds |
| Explain that Shizuku is installed separately | Corrected missing/stopped onboarding wording. Onboarding checks pass on all four APIs. | Fixed locally |
| Explain `FOREGROUND_SERVICE_SPECIAL_USE` | Added a narrow maintainer note to both recipe copies: local user-enabled recording/reconnection, no network role. | Fixed locally; not yet on the remote MR |
| Optional app title metadata | Added `fastlane/metadata/android/en-US/title.txt` with `ClipHistory`. | Added locally |
| Android 16 / HyperOS 3 helper launch NPE | Investigated the upstream startup order; added useful timeout classification, connection/device/Shizuku diagnostics and a Help topic. A real failed-helper launch test checks the resulting recovery UI/report. | App-side reporting fixed; exact OEM startup failure remains unverified |

The [capture request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4086289128) and [HyperOS report](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4096882297) require different evidence. Successful stock capture cannot close the OEM report by itself.

### HyperOS boundary

The reported failure occurs at `LoadedApk.makeApplication`, before `ClipboardUserService(Context)` is entered. Official [Shizuku UserService source](https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserService.java) calls `makeApplication(true, null)` before loading the app helper; the `true` path requests the framework Application. Editing ClipHistory's custom Application would not repair that earlier failure.

There is a [matching upstream report](https://github.com/RikkaApps/Shizuku/issues/1198) and a [proposed upstream fallback](https://github.com/RikkaApps/Shizuku-API/pull/547). The available review lacks a complete device stack trace and exact device model. A matching report is evidence for an upstream compatibility hypothesis, not proof of identical causation on the reviewer's device.

At the final recheck, the [official latest-release URL](https://github.com/RikkaApps/Shizuku/releases/latest) still resolves to 13.6.0. The fallback pull request remains open and its author explicitly reports that the patched build has not yet been device-verified. An available proposed patch is therefore not a verified fix to distribute as part of this release.

This work does not silently switch Shizuku distributions, add another privileged launcher or relax security checks. A `HELPER_START_TIMEOUT` means a helper Binder never arrived after binding was requested. It does not claim the timeout alone identifies a particular firmware bug. Testing an intentionally missing helper exercises that failure path; it does not reproduce HyperOS.

To close the OEM item, obtain the full redacted Shizuku startup exception and exact model/build, then verify real capture on that environment using a confirmed working upstream fix. A stock emulator cannot supply that result.

## 3. Confirmed app defects fixed

| Defect | Smallest coherent correction | Regression evidence |
|---|---|---|
| Delayed unmarked recopy could become ordinary text after its retained sensitive match expired | Preserve committed exact-text sensitivity and its original deadline at observation time. Carry it in the existing queued event. Reject it after expiry rather than recreating indefinite ordinary history. Include pending Undo in the match. | Two deterministic history/Undo tests failed before the fix; final JVM tests pass. |
| Sensitivity could remain unpublished during slow recovery-slot mirroring | Publish the immutable sensitive view after the first durable write, before mirror I/O, through a small repository callback. | Two controlled mirror-write tests failed before the correction and pass afterward. |
| A new sensitive preference action could overwrite another acknowledged setting using stale UI state | Within the existing serial client command, read current policy and change only the requested field. Both switches, duration selection and indefinite confirmation use it. | Real RPC regression holds the parent UI snapshot stale. It failed before the change and passes on all four APIs. |
| A helper initially given an unknown boot count rejected a later verified count forever | Allow unknown-to-known recovery for the same attached files/key/storage identity. Continue rejecting known-to-conflicting and known-to-unknown changes. | Actual helper red/green test; 9 assertions pass on all four APIs. |
| Helper launch failure lacked actionable diagnostic context | Distinguish pre-Binder startup timeout; show connection issue/message, manufacturer/model and installed Shizuku version; link to relevant Help. | 35 diagnostic assertions on each API; 6 actual failed-helper assertions on APIs 34, 35 and 37. |

No new production dependency, permission, database, storage format, permanent sensitive-text tombstone or periodic polling service was introduced for these fixes. The existing bounded writer and native screens are retained.

The new matching rule applies to source-marked text and sensitivity already committed at observation time. An unmarked new copy after its retained sensitive entry has disappeared cannot be identified as a secret by this rule. The app does not infer all passwords or card numbers. Encryption protects stored history; displayed text, in-process memory and Android's shared clipboard remain readable during use. See [the security model](../../SECURITY.md).

## 4. Build and static checks

The final production-code build ran on Windows with Android Studio JBR **21.0.10**, Gradle **9.3.1**, AGP **9.1.1**, Build Tools **36.0.0** and SDK **37**. Java bytecode target is 17. The existing Shizuku API/provider dependencies remain **13.1.5**.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon -PvalidationBuild -PunsignedRelease `
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease `
  :app:assembleDebug :app:assembleDebugAndroidTest
```

- **72 JVM tests**, zero failures, errors or skips.
- Release lint: **zero errors, 15 warnings**. Categories: durable preference commit (1), necessary hidden API reflection (4), available AGP update (1), plural candidate (1), redundant SDK guard (1), unused resources (7). No suppression was added to make the checks pass. See [build checks](build-checks.json).
- Source audit: **74/74**. See [audit output](source-audit.txt). This is a source checklist, not runtime proof.
- Signature verification passes with the original certificate, v3 signing. **16 KiB alignment passes.** See [signature](apk-signature.txt) and [alignment](apk-alignment.txt).
- The release is non-debuggable and not test-only. Its manifest contains no test instrumentation or synthetic producer. Backup is disabled. The only five requested permissions remain Shizuku access, foreground service, special-use foreground service, notifications and boot completed. No INTERNET or overlay permission. See [manifest](apk-manifest.txt) and [permissions](apk-permissions.txt).

The test APK was rebuilt after test-only fixture/assertion corrections. Per-run hashes identify which test harness ran. The frozen validation app and signed production app remained unchanged throughout the final matrix and upgrade checks.

## 5. Android coverage

All device operations selected an explicit owned `emulator-*` serial. **The physical phone was not used.** Official Google x86_64 images and official Shizuku **13.6.0.r1086.2650830c** were used. Helper processes were checked as non-root shell UID **2000**.

| Android | API | Acceptance result | Exact device/build and logs |
|---|---:|---|---|
| 14 | 34 | 18/18 suites passed | [Matrix](matrix-api34/checks.json) |
| 15 | 35 | 18/18 suites passed | [Matrix](matrix-api35/checks.json) |
| 16 | 36 | 18/18 suites passed | [Matrix](matrix-api36/checks.json) |
| 17 | 37 | 18/18 suites passed | [Matrix](matrix-api37/checks.json) |

The API-37 image reports Android 17, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`. The complete other fingerprints are in their matrix reports. These are specific Google emulator builds, not every firmware or security-patch release.

Each API passed the same 18 suites: boot identity (9 assertions), live capture (33), sensitive live capture (40), encryption/migration (95), sensitive UI/expiry (23), regression (12), settings (21), onboarding (24), diagnostics (35), native HTML-port behavior (123), connected native behavior (50), privacy (11), quick-copy errors (21), command errors (21), accessibility structure/actions (22), customization (9), recovery (11), and paused connection (9).

Sensitive live checks include default skipping, opt-in, authenticated durable capture, sticky sensitivity, original timer retention, sensitive-preview marking on normal/quick/native selected-text copy, policy changes, custom input validation and a **real one-minute elapsed-time expiry**. Encryption checks cover Keystore unwrap/reload, legacy/interrupted migration, authenticated recovery and tamper/missing-key/missing-snapshot failures.

Additional results:

| Check | Observed result | Evidence |
|---|---|---|
| Tile interaction, capacity and layout | Native tile 75, tile performance 353, capacity 5 and layout 11 assertions pass on APIs 34, 35 and 37 | [API 34](extended-api34/checks.json), [API 35](extended-api35/checks.json), [API 37](extended-api37/checks.json) |
| Nested navigation and failed helper | Navigation 53 + failed-helper 6 pass on APIs 34, 35 and 37 | [API 34 rerun](navigation-api34/checks.json); API 35/37 extended reports |
| Main-thread disk work | 5 StrictMode assertions pass on APIs 35 and 37 for search, full text and copy | Extended suite logs |
| Dark theme | Four native UI/error suites pass on API 36 | [Dark report](dark-api36/checks.json) |
| Dark theme with 200% font size | The same four suites pass on API 35; font scale restored afterward | [Large-text report](dark-large-api35/checks.json) |
| App process death | Recorder remains alive; a subsequent synthetic copy is captured on API 37 | [Result](lifecycle-api37-app-death.json) |
| Helper process death | Optional recovery starts a replacement and captures a subsequent copy on API 37 | [Result](lifecycle-api37-helper-death.json) |
| Explicit Force stop | No new capture; helper terminates on API 37 | [Result](lifecycle-api37-force-stop.json) |
| Reboot | After unlocking and restarting non-root Shizuku, recovery reconnects and captures a new copy on API 37 | [Result](lifecycle-api37-boot.json) |

### Timing and visual inspection

Thirty warm tile openings per API were measured on APIs 35 and 37. Tap-to-loaded app pre-draw p95 was **307.86 ms** on API 35 and **285.90 ms** on API 37; maxima were 330.43 and 360.16 ms. The test also checks stable panel geometry and completion. These are app pre-draw measurements on a shared emulator host, not guaranteed full-display latency or a zero-delay claim. Raw observations: [API 35](tile-warm-frames-api35.json), [API 37](tile-warm-frames-api37.json).

Selected actual native light/dark settings, diagnostic/help pages and 200% text recovery sheets were visually inspected. Text wraps, controls remain reachable through scrolling, and no new overlap or horizontal truncation was found in those inspected views. [Selected captures](screens/README.md) are app-window drawings from synthetic fixtures, not physical-phone screenshots or video-frame analysis.

Android 13 and older are outside the existing minimum SDK. No root/Sui, secondary-profile, OEM-specific battery management, physical hardware-backed Keystore, exhaustive TalkBack audio traversal or multi-day battery result is claimed.

## 6. Upgrade results

| Path | Preserved and verified | Evidence |
|---|---|---|
| 1.3.4/code 8 validation → 1.4.1/code 11 | 75 committed entries including new real cross-app copies; every entry/ID/timestamp/setting through a canonical-state digest; app UID; new shell helper; both authenticated encrypted copies and 0600 modes | [Result](upgrade-1.3.4-to-1.4.1.json), [160 assertions](upgrade-1.3.4-to-1.4.1.txt) |
| 1.4.0/code 10 validation → 1.4.1/code 11 | Ordinary, timed-sensitive and indefinite-sensitive fixture entries staged using the installed old app's codec/encryption; full state/deadlines; original wrapped key; app UID; replacement shell helper; both authenticated copies and 0600 modes | [Result](upgrade-1.4.0-to-1.4.1.json), [baseline](upgrade-1.4.0-to-1.4.1.before.txt), [16 assertions](upgrade-1.4.0-to-1.4.1.txt) |
| Original-signed production 1.4.0 → original-signed production 1.4.1 on API 35 | Actual `app.cliphistory` update; non-debuggable code 11; same UID; existing saved text; new helper; new background capture; full copy-back checked by SHA-256 | [Signed upgrade result](signed-upgrade-api35.json) |

The encrypted upgrade stages synthetic sensitive history through the old implementation; it does not claim the old sensitive UI was exhaustively revalidated. A fingerprint-reading instrumentation process can be recorded by Android as a user-requested stop. The corrected driver explicitly restarts the old app before the installation boundary, so it tests an enabled live recorder. This observation did not justify weakening the app's Force stop behavior.

## 7. F-Droid checks actually applied

Followed [the reusable guide](../F_DROID_ANDROID_GUIDE.md). Used official `fdroidserver` source commit **`cdfdce835a8231996b02392bc79c1879e6bef501`** and current `fdroiddata` commit **`7471262366c2bc813ee58714c3e9bd630e10b420`** in isolated local tool checkouts. App dependencies were not changed to install these tools.

| Gate | Actual result |
|---|---|
| `fdroid readmeta` | Passed on the prepared submitted recipe |
| `fdroid rewritemeta --list app.cliphistory` | Passed; canonical formatting checked |
| `fdroid lint app.cliphistory` | Passed |
| `fdroid checkupdates --allow-dirty app.cliphistory` | Passed; public tags still resolve to 1.3.4, correctly excluding unpublished 1.4.1 |
| Upstream source scanner | Zero fatal findings and zero source warnings on 542 nonignored working-tree files copied to isolated staging. Scanner's normal Gradle-wrapper removal affected that staging only. This is not a clean-commit recipe build. |
| `fdroid scanner --refresh --exit-code ClipHistory-1.4.1.apk` | Passed: no flagged non-free classes or extra signing blocks |
| Independent Linux recipe build and signature-copy reproduction of 1.4.1 | **Not run**: no frozen public 1.4.1 reference; this host's Ubuntu WSL filesystem is unavailable and Docker's Linux engine is not running. |
| New-version maintainer CI / merge / catalogue publication | **Not performed**; external gates remain open |

See [metadata lint](fdroid-lint.txt), [update discovery](fdroid-checkupdates.txt), [source scan](fdroid-source-scan.json) and [APK scan](fdroid-apk-scan.txt). The Windows F-Droid tool run warns that it cannot discover `apksigner` and that config-file POSIX permissions are unavailable. These warnings were retained; separate official Windows Build Tools signature verification passed. A scanner success is not full reproducibility proof.

Both tracked recipe copies deliberately still name the real public 1.3.4 source SHA. They must be updated together after a real 1.4.1 source commit exists. No fabricated future SHA or nonexistent reference binary was added.

## 8. Final publication sequence

1. Review and commit the complete preserved 1.4 implementation plus these fixes, docs and metadata. Keep keys, SDK/emulator caches and private test outputs excluded. Record the full resulting SHA.
2. Build and sign from that clean exact revision using the original private key. Recheck the resulting digest, manifest and update behavior. The current APK is a tested local candidate, not proof of a future clean-revision binary.
3. Run a fresh Linux/F-Droid recipe build and upstream-signature comparison for that exact source/reference pair in a working Linux environment. Investigate differences rather than copying the old green result.
4. Publish the immutable `v1.4.1` source tag and matching `ClipHistory-1.4.1.apk` reference/checksum. Do not move an old tag or replace a differently built APK silently.
5. Replace the intended build in both local recipes and the existing MR branch with the full real source SHA, version 1.4.1 and code 11. Keep the existing signer and reference URL pattern; run format/lint/update checks again.
6. Run new maintainer CI. Reply briefly with the exact source/APK and this release's capture/upgrade evidence, while explicitly leaving HyperOS unverified. Resolve threads only on the evidence relevant to them.
7. Confirm maintainer acceptance, main-server build/signature verification and an actual catalogue entry separately. There is no reliable fixed merge/publication ETA.

No production upload or public reply is implied by this document. The owner has a concrete tested candidate and reviewable remaining gates.

## 9. Failures and evidence limits

Failures were investigated rather than counted as passes. Preserved red tests include delayed sensitive recopy, slow mirror publication, stale policy and boot identity. The baseline live sensitive run interrupted by installing another test APK is not treated as an app failure or a pass.

The supplementary API-34 navigation report originally contains a failure: it counted two unknown settings labels after a third field was added, then failed to await asynchronous decrypted entry actions. The tests now assert each relevant row and await the action sheet; [the rerun passes](navigation-api34/checks.json). The matrix driver's completion parser also needed to recognize the existing audit suite's summary format. These harness corrections did not change production behavior or remove coverage.

API-37 setup initially lacked Shizuku's server-side authorization despite the Android permission being granted. The official Allow confirmation was completed on the emulator and the entire matrix rerun. The initial permission error is [retained](api37-initial-permission-setup.txt).

None of these tests certify every possible source app, clipboard race, firmware, hardware fault or privileged attacker. Clipboard notifications do not preserve every intermediate value during arbitrarily rapid changes. OEM compatibility and unpublished-release reproduction remain explicit limits.

## 10. Local Definition of Done

- [x] Current review items are individually recorded with fixed, verified or unresolved status.
- [x] Confirmed privacy, settings and recovery defects have targeted red/green regressions.
- [x] The final app builds; JVM, lint and source gates pass with warnings disclosed.
- [x] The same validation app passes real shell capture, sensitive expiry, encryption and native UI suites on APIs 34–37.
- [x] Plaintext and encrypted upgrades preserve full synthetic state; the encrypted update preserves its key.
- [x] The original-signed production APK upgrades without uninstalling and captures/copies afterward.
- [x] Focused process-death, explicit-stop and reboot behavior passes on API 37.
- [x] Selected native visuals and large text were inspected after testing.
- [x] Local F-Droid preparation checks were applied and a reusable guide delivered.
- [x] The final changes and artifact identity were reviewed; previous unpublished work and the 1.4.0 APK are preserved.
- [ ] Exact HyperOS failure reproduced and a fix verified on the affected firmware.
- [ ] New source/reference published, independently reproduced, accepted by maintainers and listed in F-Droid.

The last two boxes are deliberately open. Local qualification is complete within the measured environment; universal compatibility and publication are not claimed.
