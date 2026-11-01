# Physical-phone and F-Droid recheck

Checked **31 October 2026**. **The focused Pixel checks passed. The HyperOS report and publication gates remain open.** This is verification of the existing 1.4.1 candidate, not a new sensitive-capture implementation or a new release.

## Findings that remain

1. **Compatibility, unresolved:** the reported Android 16 / HyperOS 3 failure occurs before the helper constructor. This Pixel passes, but runs a different OS and Shizuku distribution. It cannot close that report. Obtain the affected device/build and redacted startup stack trace, then test a confirmed fix on that firmware. The [matching upstream issue](https://github.com/RikkaApps/Shizuku/issues/1198) and [proposed fallback](https://github.com/RikkaApps/Shizuku-API/pull/547) remain open; the proposal still states that its patched build has not been device-verified.
2. **Release, pending:** 1.4.1 remains local, with no public `v1.4.1` tag found. Public `main` is still `6009e25f548a5f1a45d6cf129d288056e76d5b8f`. The submitted recipe still builds 1.3.4/code 8. This candidate has not completed an independent exact-source reproduction or new maintainer CI. Old green CI does not qualify it.
3. **Policy evidence, needs reviewer assessment:** the accessible Git history starts on 30 September 2026. It does not establish three months of development. The current policy permits exceptions, including proven users. Supply accurate development/use evidence rather than marking this criterion automatically satisfied. This is a review consideration, not a newly reported rejection. See [Quality Control, point 6](https://f-droid.org/en/docs/Inclusion_Policy/).

No new production-code defect was confirmed by this phone pass. This does not establish that every device or possible interaction is bug-free.

## Exact app and environment

| Item | Verified value |
|---|---|
| Device | Google Pixel 8 Pro, primary unlocked profile |
| OS | Android 17 / API 37, security patch 2026-10-05 |
| Build | `google/husky_beta/husky:17/CP41.260831.019/16596456:user/release-keys` |
| Installed Shizuku | `13.7.0-thedjchi`, running as non-root shell UID 2000 |
| Upgrade | Signed production `app.cliphistory`, 1.4.0/code 10 → 1.4.1/code 11, in place |
| Installed APK SHA-256 | `be2cf0e62d86f85593b78093ecc5ee58b0bb2e61425cb52d4404bfcf357a67be` |
| Signing certificate SHA-256 | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| Isolated validation app SHA-256 | `0fe754a8450d90db014e2278fafce336121abe2f4efe653be3d7dbfb0116c09b` |
| Phone test APK SHA-256 | `3b0d3cbf47c4c16aa3e3899fdb2eeba95e277936346f36dc931d9eb7c923ed39` |

The installed production APK was pulled back and matched the candidate byte for byte. The phone's Shizuku fork was already installed and was not replaced. These results must not be described as physical-device testing with official Shizuku 13.6.0.

## Phone checks actually performed

| Check | Result and evidence |
|---|---|
| Delayed sensitive recopy and expired Undo | Passed on phone ART with isolated synthetic memory. Expired queued text was rejected and absent from both recovery copies. [7 core assertions](phone-recheck/core.txt) |
| Publication before mirror I/O | A controlled write hook verified the committed sensitive classification/deadline was visible during recovery-copy writes. Included in the seven core assertions. |
| Hardware-backed encryption | StrongBox observed; key unwrap/reload, both authenticated encrypted copies and damaged-key rejection passed. [9 assertions](phone-recheck/encryption.txt) |
| Same-helper boot-identity recovery | Real isolated shell helper accepted unknown → verified identity and rejected conflicting/downgraded identities. This tests the attachment transition, not an actual phone reboot. [Live log](phone-recheck/live.txt) |
| Field-only sensitive policy update | Changing duration through the real Binder service preserved both live switches. This physical check exercises the shared policy helper; the forced stale-native-UI race remains covered by the earlier emulator regression. |
| Real cross-app sensitive capture | Exact synthetic text and Android's sensitive flag were independently confirmed. It was captured alongside ordinary text. |
| Interleaved recopy | Two entries remained two; sensitive text moved to the top without losing classification or extending its original deadline. |
| Real timed deletion | After one actual elapsed-time minute, sensitive text was absent from both decrypted/authenticated recovery copies. Offline access agreed. These live checks passed [18 assertions](phone-recheck/live.txt). |
| Signed production connection test | Actual native confirmation and probe passed while ordinary recording was paused. Its recognized test text was excluded from history. [Production probe](phone-recheck/production-probe.txt) |
| Production Quick Settings panel | Three openings each showed three entry buttons, stayed open, collapsed the shade, and closed using Close. No entry was copied. [Results](phone-recheck/production-tile.json) |
| Native presentation | Inspected actual light-theme Settings, sensitive retention choices, setup Help, expanded startup guidance and Diagnostics. No overlap or unreachable control was found in the inspected views. Diagnostics showed the installed Shizuku version and no current connection issue. |

The observed personal history count and settings matched before and after the update. StrongBox and the expiry clock remained confirmed. Recording was resumed and the final home state showed Recording.

Personal entries were not exported or individually hashed. Matching counts are not a proof that every private entry's bytes were compared. Destructive controls, data clearing, an actual phone reboot and a production sensitive-capture purge were deliberately not exercised on personal data.

For clipboard-changing tests, ordinary production recording was paused through its acknowledged UI. Eligible existing plain clipboard data was held only in the separate foreground test Activity's RAM, restored and compared internally, then released. Marked-sensitive, non-text and multi-item originals would have been refused. Temporary validation Shizuku access was revoked afterward; production access remained enabled. The isolated helper was absent at the final process check.

Early nonpassing harness runs are retained locally. They exposed validation authorization/startup synchronization and multiword argument parsing errors in the driver. The corrected final test required the exact fixture and sensitive flag; no production fix was inferred from those harness failures. Temporary test-source changes were archived locally and removed from the app tree. Production sources and the signed APK were unchanged by this review.

## Fresh host and F-Droid checks

The final host rerun used:

```text
gradlew.bat --no-daemon -PvalidationBuild -PunsignedRelease --rerun-tasks :app:testDebugUnitTest :app:lintRelease
```

| Gate | Actual result |
|---|---|
| JVM tests | 72 tests, zero failures, errors or skips; all requested Gradle tasks executed |
| Release lint | Zero errors, 15 warnings; existing categories disclosed in the [release qualification](RELEASE.md) |
| Source checklist | 74/74; a static checklist, not device proof |
| Signed APK | Original certificate verified; 16 KiB alignment check passed |
| F-Droid metadata | `readmeta`, `rewritemeta --list app.cliphistory` and `lint app.cliphistory` exited 0 against the prepared submission recipe |
| F-Droid binary scan | `scanner --exit-code` exited 0 for the exact installed candidate, using the local Android SDK |
| New-version independent build/reproduction | Not performed; still open |

Metadata tooling retains Windows warnings about `apksigner` discovery and POSIX config permissions. Separate official Android Build Tools signature verification passed. An initial scanner invocation lacked `ANDROID_HOME`; the corrected invocation succeeded. No scanner bypass or suppression was added.

The historical emulator matrix, upgrade and lifecycle results remain in [RELEASE.md](RELEASE.md). This phone pass supplements them; it does not turn them into OEM-wide proof. See [machine-readable current results](phone-recheck/checks.json).

## Attached F-Droid guide: application-specific comparison

Reviewed the supplied **F_DROID_ANDROID_GUIDE.md**, dated 31 October 2026, SHA-256 `91a971e9f942782464be1912d76716b5178d4366c313564578a8b5fb3b268478`. It was treated as reference material. Its publishing commands were not executed merely because they appeared in the document.

No material correction was found in the app-relevant guidance checked against current official sources. It correctly separates policy from recommendations, upstream signing from F-Droid signing, local checks from reproducibility, and CI from acceptance/publication. Its download-ranking examples are not eligibility criteria or quality scores and were not reranked in this app check.

| Guide requirement | ClipHistory status |
|---|---|
| Main-repository route | Existing [MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917); continue there, not a duplicate request |
| Public source and issue tracker | Public repository and issue tracker exist. Exact 1.4.1 source freeze/tag remains pending. |
| Licences and artwork | MIT app licence, runtime notices, supplied-artwork provenance and Inter's OFL text are present. Shizuku is separately installed and not bundled. |
| FOSS build/dependencies | Existing pinned toolchain and dependency declarations retained. Fresh source checklist and APK scanner passed. No new dependency or permission was introduced here. |
| Permissions and privacy description | Source still requests the five reviewed permissions, no INTERNET or overlay permission. Store text explains separate Shizuku, shell/primary-profile limits, optional sensitive capture, encryption and retention limits. |
| Signing strategy | Existing upstream-signed reproducible route retained. Installed update and certificate passed. New-version independent reproduction remains required for that route. |
| Identifiable release | Local 1.4.1/code 11 candidate exists; public immutable tag and matching clean-source reference are pending. |
| Device/upgrade checks | This physical update, probe and focused regressions passed. Earlier Android 14–17 emulator evidence is separate. HyperOS remains unverified. |
| Store text | Title 11, summary 61, full description 2,202 and code-11 changelog 285 characters, within the guide's stated limits. English metadata is present. |
| Graphics | 512×512 icon, 1024×500 feature graphic and seven screenshots exist. Screenshots were last committed for 1.3.4; review them for the new release and refresh changed screens before tagging. Their presence alone does not establish new-version accuracy. |
| Recipe | Submission copy contains only the current public 1.3.4 build, full real SHA, signer and automatic updates. The other local recipe retains historical builds. Neither is a submitted 1.4.1 recipe. |
| Review/CI | Current MR remains open with 14 overview entries and two open threads. Its existing green pipeline is for the old submission. |
| Merge/catalogue/maintenance | No new-version merge, production build or catalogue installation was established. These are later gates. |
| Developer identity verification | Correctly separated from F-Droid inclusion. Current [MR template](https://gitlab.com/fdroid/fdroiddata/-/raw/master/.gitlab/merge_request_templates/Default.md) says maintainers can trigger FOSS CI if GitLab asks for phone/card details. Google's changing [Android distribution rules](https://developer.android.com/developer-verification) are a separate topic. |

The current [metadata documentation](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/) and [build reference](https://f-droid.org/en/docs/Build_Metadata_Reference/) support the guide's release-source and metadata distinctions. An APK alone is not the F-Droid submission.

## GitLab items: exact status

| Review item | Recheck result |
|---|---|
| Demonstrate actual clipboard functionality | New Pixel evidence: isolated actual capture, sensitive recopy/expiry, and the signed production listener/storage probe passed. Earlier official-Shizuku emulator results are recorded separately. This evidence has not been posted to GitLab. |
| Explain special-use foreground service | Both local recipes contain the short MaintainerNotes explanation. Not yet present in the remote MR. |
| Explain separate Shizuku installation | Current strings and store text explicitly say it is a separate app. The phone Help was inspected. New code/text remains local. |
| Android 16 / HyperOS 3 helper startup | Unresolved on that environment. Improved timeout reporting, model/version diagnostics and Help do not repair Shizuku's pre-constructor framework failure. Pixel success must not be used to resolve this thread. |

The latest rendered discussion remains the 27 October narrowing of the helper-startup failure. The reviewer distinguished working shell clipboard access from the earlier startup exception. The phone result does not contradict that distinction. See the [capture request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4086289128) and [startup report](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4096882297).

## Remaining publication sequence

1. Resolve or explicitly agree the affected-firmware compatibility limitation with reviewers, using exact device/stack-trace evidence. Provide accurate development/use evidence for the policy question if needed.
2. Review the complete existing source changes, release metadata and changed screenshots; freeze a real clean source revision. Rebuild the signed reference from that revision and verify its resulting identity. Do not assume this dirty-tree candidate will match a future clean build.
3. Publish the immutable source tag and matching reference APK/checksum, then run an independent Linux/F-Droid build and actual upstream-signature reproduction for that exact pair. Use the established reproducible signing strategy.
4. Update the intended recipe to the real full release SHA, 1.4.1/code 11; retain only the intended latest build in the new-app MR. Rerun metadata/update checks and new maintainer CI. Include the service note and concise evidence in the existing discussion.
5. Verify maintainer acceptance, merge, production build/signature verification, catalogue listing and practical F-Droid install/update separately.

No commit, push, tag, upload, reviewer message or thread resolution was performed during this verification request. Publication is not claimed complete.
