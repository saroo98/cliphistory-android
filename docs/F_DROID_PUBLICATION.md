# F-Droid publication status

ClipHistory **1.3.4/code 8**, `app.cliphistory`, MIT, Android 14+. The upstream owner requests inclusion. Qualification ran on **2026-10-02**. Local preparation passed; the latest release still needs maintainer CI and inclusion review.

## Immutable release

| Item | Verified value |
|---|---|
| Public source/tag | [v1.3.4](https://github.com/saroo98/cliphistory-android/tree/v1.3.4) |
| Source commit | `97c1e996e2619a5cf6f86f7f70614576d795b8c3` |
| Public reference | [ClipHistory-1.3.4.apk](https://github.com/saroo98/cliphistory-android/releases/download/v1.3.4/ClipHistory-1.3.4.apk) |
| Windows/Linux/F-Droid unsigned SHA-256 | `296106acf29d31eb10dc0989e1e457973946e9d3fb4f0501c7e023c2dbee2978` |
| Signed/downloaded reference SHA-256 | `b58e21f01105a74e03ae76c5cac9dd9d55dab7175e310258516873af25e3f8c5` |
| Original certificate SHA-256 | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| APK size | 2,558,071 bytes |

AGP 9.1.1, Gradle 9.3.1, Linux JDK 17 / supported Windows JBR 21, platform android-37.0, Build Tools 36.0.0. Clean public clones were pinned to the source above. Apksigcopier 1.1.1 reproduced the signed reference byte for byte from the Linux unsigned build. Signature v3 and 16 KiB ZIP alignment verify. Old tags and assets were preserved. Later documentation and metadata commits do not move v1.3.4; reproduce the published APK from its pinned source, not a later documentation revision.

This release integrates the supplied logo in Android's adaptive/themed launcher, tile, recovery notification, Welcome and About. The appropriate GitHub and store exports are also supplied. Clipboard behavior, permissions and dependencies are unchanged. Exact artwork hashes, sizes and provenance are in [branding](../branding/README.md).

## Executed checks

- Clean Windows build: **44 unit tests**, zero failures/errors/skips; release lint **zero errors, 14 warnings**.
- Independent clean Linux build: unsigned bytes match Windows.
- Source-distribution audit: **72/72** checks, including no signing material.
- Non-debuggable package/code/SDK and the same five reviewed permissions verified in the signed APK. No Internet, overlay, Accessibility-service, keyboard permission or native library.
- CI-matched F-Droid server 2.4.5, source `c21c177ff6d813697aaf9c988ca9fbb2b571b468`, ruamel.yaml 0.18.10: `readmeta`, `rewritemeta`, `lint`, `checkupdates` and `build --test --scan-binary --no-tarball app.cliphistory:8` passed. Canonical rewrite made no byte changes after LF normalization.
- The full recipe passed source/APK scans, downloaded the public reference, accepted the original signer and compared its APK successfully. Its unsigned digest matches both independent builds above. No scan exemption or check was disabled.
- Native API 36 emulator: Settings **21**, Onboarding **24**, Native UI **33** assertions passed. Native HTML-port regression: **121 light + 121 dark** assertions passed. Actual Welcome/About OS screenshots and the installed launcher icon were visually inspected. FeatureChecks select light internally; their dark-labelled raw reruns are not dark-theme evidence.
- Signed in-place upgrade on the connected Pixel 8 Pro: installation succeeded and the package reports **1.3.4/code 8**. No uninstall, personal-history inspection or physical screenshot was needed. This is upgrade verification, not a fresh full physical-device UI audit.
- Upstream Fastlane text, changelog 8, approved 512 x 512 catalog icon, 1024 x 500 feature graphic and seven native screenshots verified. Store screenshots contain no physical-device or personal clipboard data.

Actual logs and the machine-readable result are in [release-1.3.4](release-1.3.4/). Local filesystem roots in public logs are normalized. The recipe's initial scratch setup needed the existing categories/config directory and canonical LF writes. A digest command first looked in `unsigned/`; `--test` writes to `tmp/`, where the actual APK and downloaded reference digests were then confirmed. These were setup corrections, not app fixes.

The 14 Android-lint warnings remain disclosed and unsuppressed: one AGP advisory, one durable preference-commit advisory, one plural candidate, four private-API calls and seven unused strings. Build logs also retain the reflective clipboard interoperability diagnostic, deprecated window soft-input flag and SDK-tools XML-format advisory. Private clipboard APIs remain a compatibility limit. Broader native UI evidence is in [HTML_UI_PORT.md](HTML_UI_PORT.md); historical checks do not qualify a new binary automatically.

## Current external submission

[MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917) is open. It adds only `metadata/app.cliphistory.yml` from public, unprotected `xizirsaro/fdroiddata:app.cliphistory`; no conflict is reported. The latest-only recipe is committed as `550696372b43e4dedb641c2a8cc618125ae1d235`, pins the immutable 1.3.4 source, and has SHA-256 `5cfd6bbbe65555721ee2013fa15d4880c3d79e84841983fb49ed13b3b74decf6`. The downloaded submitted file matches the qualified local file byte for byte, LF without BOM. Preserve the canonical space after `Binaries:` and its indented continuation URL.

The previous 1.3.3 formatting correction at `8465d0ccae6b6bff96c00b1b35c7052b9fab0af1` passed **all nine jobs** in [maintainer pipeline #2907476394](https://gitlab.com/fdroid/fdroiddata/-/pipelines/2907476394). Its success qualifies that earlier release, not 1.3.4. The initial formatter failure and serializer correction are retained in [the earlier evidence](release-1.3.3/fdroid-metadata-format-fix.txt).

The new automatic [branch run #2907541778](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2907541778) and [MR run #2907542150](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2907542150) have zero jobs. The UI says **Verify your identity to run this pipeline**; API `yaml_errors` is null. The description now covers 1.3.4, with the current CI/report checkboxes unchecked. [A short maintainer rerun request was posted](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_3946650775). No latest-release remote CI pass, acceptance or listing is claimed.

The prior CI report's minor R8 notice remains applicable: minification is deliberately disabled because Shizuku loads the UserService entry point reflectively. The APK is about 2.5 MB and contains no native libraries. This notice is explained in the request. No rebase, signing-key upload, payment, credential change or sensitive identity submission was performed.

On 2026-10-02, initial exact app-ID lookup returned 404 and scoped ClipHistory searches of fdroiddata MRs/issues and RFP issues found no related request. The existing MR is reused. Store artwork stays upstream and is included in v1.3.4; F-Droid retrieves it from release source during its publication workflow.

## Branding delivery limits

The new GitHub README wordmark is published and visually verified. The 1280 x 640 social-preview image is committed, but **its Settings upload is pending** because Chrome's ChatGPT extension requires file-URL access for file uploads. Permission to change that access was requested; it has not been granted. No account avatar or unrelated fork logo was changed. No live website exists; favicon exports are prepared for future use.

The local HTML reference has embedded logo assets, version 1.3.4 and a passing JavaScript syntax/source check. Its browser visual check was blocked by the browser's local-file URL policy. The exact local edit is preserved in [html-logo.patch](release-1.3.4/html-logo.patch); browser rendering is unverified.

F-Droid maintainers decide inclusion and perform independent builds. Non-root Shizuku requires restarting after reboot; private Android API and OEM behavior remain compatibility limits. Read the [submission procedure](F_DROID_SUBMISSION.md) and [current MR description](F_DROID_MERGE_REQUEST.md).
