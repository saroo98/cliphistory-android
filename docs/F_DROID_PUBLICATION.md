# F-Droid publication status

ClipHistory **1.3.3/code 7**, `app.cliphistory`, MIT, Android 14+. The upstream owner requests inclusion. Current qualification was executed on **2026-10-02**.

## Immutable release

| Item | Verified value |
|---|---|
| Public source/tag | [v1.3.3](https://github.com/saroo98/cliphistory-android/tree/v1.3.3) |
| Source commit | `ff704441df26ad2dd764a023fcb85b5cfeb8fa4b` |
| Public reference | [ClipHistory-1.3.3.apk](https://github.com/saroo98/cliphistory-android/releases/download/v1.3.3/ClipHistory-1.3.3.apk) |
| Windows/Linux/F-Droid unsigned SHA-256 | `b6f5678d02615182dc97b49977bb4d9412d3048e0364700fdc992ebe2dfc8154` |
| Signed/downloaded reference SHA-256 | `6bd7239fbf12b9366cc70714275b977025eeffc4a7e49275833c2e7d8c61430d` |
| Original certificate SHA-256 | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| APK size | 2,558,015 bytes |

AGP 9.1.1, Gradle 9.3.1, JDK 17 (Linux) / supported JBR 21 (Windows), platform android-37.0, Build Tools 36.0.0. Clean clones used canonical Git line endings. Both source checkouts were clean after building. APK signing was separate, with the existing private identity and alignment preservation. Apksigcopier 1.1.1 reproduced the signed reference byte for byte from the Linux unsigned output. Signature v3 and 16 KiB zip alignment verify.

The locally installed pre-freeze 1.3.3 binary has the same application code but an earlier embedded Git revision; its digest is different. It is not the public reference. No old tag or release asset was overwritten. Later metadata/evidence commits do not move v1.3.3.

## Executed gates

- Clean Windows release build, 44 unit tests with zero failures/errors/skips, and lint: **zero errors, 14 warnings**.
- Independent clean Linux release build with identical unsigned bytes.
- Source-distribution audit: **72/72** checks, including absence of signing material.
- Correct non-debuggable package/version/SDK and the same five permissions; no Internet/overlay/Accessibility/keyboard permission or native library.
- F-Droid server 2.4.5: `readmeta`, `rewritemeta`, `lint`, `checkupdates` and `build --test --scan-binary --no-tarball app.cliphistory:7` passed.
- Actual F-Droid source/APK scans, downloaded original-key reference, allowed signer and reference comparison passed. The F-Droid build has the same unsigned digest above.
- Fastlane text limits, changelog 7, 512px icon and six current synthetic native UI images verified. Store media contains no physical-device or personal clipboard data.

Selected actual outputs and a machine-readable manifest are in [release-1.3.3/](release-1.3.3/). Local filesystem roots and trailing whitespace in public logs are normalized; result text is preserved. The initial scratch environment needed Git initialization and generated-output exclusions for checkupdates. One Python 3.14.4 process aborted during the first APK scan; a fresh full recipe run, without changing the recipe or disabling checks, passed. The cause of that interpreter crash is not established and it is not represented as an app fix.

The clean-build warnings are one AGP advisory, one durable preference-commit advisory, one plural candidate, four private-API calls and seven unused resources. They are not suppressed. The prior local port run had 15 warnings; this fresh clean report has 14. Runtime UI qualification and its limits remain separate in [HTML_UI_PORT.md](HTML_UI_PORT.md).
The build logs also retain two Kotlin compiler diagnostics (reflective clipboard `Any?` interoperability and deprecated `SOFT_INPUT_ADJUST_RESIZE`) and an SDK-tools XML-format advisory. These are distinct from the 14 Android-lint warnings. The pinned toolchain built successfully; private Android clipboard API compatibility remains disclosed rather than treated as guaranteed on every future Android version.

## External workflow

**Submitted:** [F-Droid inclusion MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917), open on 2026-10-02, `xizirsaro/fdroiddata:app.cliphistory` into `fdroid/fdroiddata:master`. GitLab reports one changed file and no merge conflict. The public fork and unprotected branch are verified.

Submission commit: `2d2d3df0429cd85ca37064a2e96f0de32d92a7cf`. Its only added file, `metadata/app.cliphistory.yml`, was downloaded and byte-compared with the tested canonical recipe: identical, LF, SHA-256 `b251e032da670f0a0c255bc3a56ea081154359dce1e73c867c60707f69208bd2`.

**External CI remains blocked.** The [merge-request pipeline #2906013918](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2906013918) and [branch pipeline #2906007622](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2906007622) failed before any jobs ran. GitLab explicitly requires separate identity verification. Its `yaml invalid` badge describes this account restriction; the API reports `yaml_errors: null`. This is not an observed YAML/recipe job failure and no remote CI pass is claimed. The current App inclusion template permits requesting a maintainer-triggered pipeline rather than submitting phone/card information. The MR leaves the pipeline checkbox unchecked and makes that request. F-Droid review, independent CI and acceptance/listing are still pending.

On 2026-10-02, the exact app-ID metadata lookup returned HTTP 404 and scoped app-name searches of fdroiddata MRs, fdroiddata issues and RFP issues returned empty arrays. The latest-only submission file is `fdroid/submission/app.cliphistory.yml`, byte-identical to F-Droid's canonical output; upstream historical metadata retains earlier builds. Store materials remain upstream.

F-Droid maintainers decide inclusion and run independent infrastructure builds. Account/CI access restrictions cannot be bypassed or converted into a passing status. No payment, new credential, signing-key upload or sensitive verification is part of this preparation.

Non-root Shizuku needs starting again after reboot; internal clipboard APIs and OEM behavior remain compatibility limits. Read [submission procedure](F_DROID_SUBMISSION.md) and [current MR description](F_DROID_MERGE_REQUEST.md).
