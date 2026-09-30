# ClipHistory: F-Droid submission preparation

Checked on 2026-09-30 against the owner's [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/), the [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/), current [contributor instructions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) and the [App inclusion merge-request template](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/App%20inclusion.md).

The local submission material is complete. An actual public GitLab fdroiddata fork, its CI result and the inclusion merge request are still pending. The local recipe test is not GitLab CI or F-Droid acceptance.

## Evidence for this app

| Item | Status and exact evidence |
|---|---|
| Current public source | [saroo98/cliphistory-android](https://github.com/saroo98/cliphistory-android), real production source and tests |
| Source licence / author permission | Root MIT licence; README explicitly welcomes F-Droid inclusion at the project owner's request |
| Runtime libraries | Shizuku SDK MIT; Kotlin, JetBrains/AndroidX annotations Apache-2.0; full bundled notices |
| Separate Shizuku manager | [Official source](https://github.com/RikkaApps/Shizuku), Apache-2.0; not bundled, root mode not supported by this app |
| Toolchain | Command-line build succeeds on Linux with open-source JDK 17; Android Studio is not required |
| Asset provenance | Original launcher/native UI and synthetic captures under the app's MIT licence; no bundled font or stock image binary |
| Release tag | `v1.2.0`, exact commit `5739fb2bfbe26023aa02782a83eb2eb7443d126d` |
| Application / version | `app.cliphistory`, 1.2.0, code 3, minimum API 34, target API 37 |
| Identifier check | GitLab API reports no `metadata/app.cliphistory.yml` in current fdroiddata; exact-ID MR and ClipHistory RFP searches returned no entries on the check date. This is not a guarantee of uniqueness outside F-Droid. |
| Default store language | Upstream `fastlane/metadata/android/en-US/` exists in the production tag |
| Short description | 61 characters with no final period |
| Full description | Actual features, privacy model, Shizuku/primary-profile prerequisite and platform limits disclosed |
| Changelog | `changelogs/3.txt`, 405 characters |
| Icon / screenshots | PNG icon 512 by 512; four synthetic native View images, each 1080 by 2400 |
| Build metadata | `fdroid/metadata/app.cliphistory.yml`, YAML with LF endings and full source hash |
| Metadata content | Build, signing, author/source/issue fields only; descriptions and graphics are read from upstream Fastlane |
| Update discovery | Tagged releases, `UpdateCheckMode: Tags`, `AutoUpdateMode: Version`; `checkupdates` succeeds |
| Maintainer contact | AuthorName `saroo98`, public GitHub author page and repository issue tracker |
| Reproducible binary | Public APK URL plus allowed certificate; Windows/Linux APK equality and successful signature copying |
| Actual recipe test | `fdroid build --test --scan-binary --no-tarball app.cliphistory:3` succeeds locally, including source/APK scans, public binary download, binary comparison and signer check |
| Build / metadata exemptions | No skip-scan, force-build, custom scanignore/scandelete or lint baseline used |
| ABI splitting | Unnecessary: approximately 2.28 MiB APK with no bundled native libraries |
| External keys / accounts | No API key or account needed by the app or build; GitLab login is needed only for submission |

The installed application ID and release code were retained for upgrade compatibility. There is no new Android feature restriction, removed functionality or rebuilt production APK as part of this guide check. APK SHA-256 remains `7eb1043dc6c33cba080a2567e35c32b223981305fa90c0783b4efc21673b481d`.

## Prepared submission

Use title **New app: ClipHistory**. The copy-ready project description is `docs/F_DROID_MERGE_REQUEST.md`; select GitLab's current **App inclusion** template and check its items only when they are actually satisfied.

In a public fork of `fdroid/fdroiddata`, create an unprotected branch called `app.cliphistory` from the current default branch. Add exactly one file: copy this project's LF-formatted `fdroid/metadata/app.cliphistory.yml` to the fork's `metadata/app.cliphistory.yml`. Upstream already supplies descriptions, screenshots and licences; those files and the APK should not be copied into fdroiddata.

The recipe keeps only the latest version, pins the full commit and enables upstream signature verification. In the fork's environment, run readmeta, rewritemeta, checkupdates and lint, then let its pipeline validate the change. Local CLI checks and a local recipe build have already passed with fdroidserver 2.4.5 and the pinned app toolchain. Its evidence is in `docs/release-1.2/fdroid-recipe-build.txt`. The local configuration supplies installed Gradle 9.3.1, JDK 17 and the official Android SDK; it does not emulate the whole F-Droid build server.

Open the merge request against fdroiddata after reviewing the fork's CI results. Keep fork/branch/CI checklist entries pending until the fork exists and the actual pipeline passes. A packaging request in `fdroid/rfp` remains an alternative for getting an initial review, but the current RFP template directs submitters with prepared metadata toward a merge request.

F-Droid maintains the inclusion decision. Its maintainers may request recipe/environment adjustments or clarification of Shizuku and internal-API compatibility. The four Android lint internal-API warnings are explained in the release report; they are not suppressed. GitLab CI and review must not be reported as successful merely because this local build succeeds.

## Ongoing release details

For a future release, retain the same app ID and private signing key, increase version code/name, update upstream changelog/store material, tag the actual source and publish its aligned signed APK at the versioned binary URL. Re-run reproduction and capture tests. Source and bug-report access must remain available; future internal Android API changes can require maintenance.

The open device/accessibility/battery coverage and the evidence-based scores remain in `docs/release-1.2/RELEASE.md`: overall 85/100 and F-Droid readiness 84/100. This check completes the submission paperwork and local recipe evidence; it does not remove those coverage boundaries.
