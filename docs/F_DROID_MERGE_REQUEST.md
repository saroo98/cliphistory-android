# Submitted F-Droid inclusion request

[MR !50917: New app: ClipHistory](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917), created 2026-10-02. The current submitted description is reproduced below. The metadata-formatting correction is committed as `8465d0cc`; its local checks passed with the current CI tool revision. The overall CI checkbox remains unchecked pending a maintainer rerun. Acceptance and listing are not claimed.

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. You can update git submodules without opening an MR in this repo and the submodule is covered by our scanner.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). We'll use your signature for improved security/reliability, also allowing users to switch between different channels. Do note that if you don't enable reproducible build then the apk will be signed with our key so you can't enable it later. If you can't enable this, please add the reasons here.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller.
* [x] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [ ] All pipelines should pass. **The maintainer pipeline passed 8/9 jobs; its metadata-formatting failure is corrected in 8465d0cc. Awaiting a maintainer rerun on that commit.**
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

## App and inclusion request

ClipHistory is a local, searchable plain-text clipboard history app. The upstream owner requests F-Droid inclusion. Source is MIT; there are no ads, trackers, account, network service, or Internet permission. Fastlane en-US summary, description, changelog 7, 512px icon and six current synthetic native screenshots are in the upstream repository.

- Application ID: `app.cliphistory`
- Latest release: **1.3.3 / versionCode 7**, Android 14+
- Source: https://github.com/saroo98/cliphistory-android
- Immutable source: `ff704441df26ad2dd764a023fcb85b5cfeb8fa4b`, tag `v1.3.3`
- Public release/reference APK: https://github.com/saroo98/cliphistory-android/releases/tag/v1.3.3
- Author/contact: https://github.com/saroo98/cliphistory-android/issues

No related ClipHistory fdroiddata or RFP issues were found in scoped searches on 2026-10-02; no existing exact app-ID metadata was found. The fork is public, `app.cliphistory` is unprotected, and the MR adds only `metadata/app.cliphistory.yml`. The recipe is LF, latest-only, uses the full source hash and tag updates, and has no disabled builds or scanner exemptions. Conditional checklist items: no external source repositories/submodules; no native libraries or useful ABI split (APK is 2,558,015 bytes). No rebase was needed.

## Executed build and reproducibility checks

On 2026-10-02, clean Windows and Linux builds produced identical unsigned APKs. F-Droid server **2.4.5** passed `readmeta`, `rewritemeta`, `lint`, `checkupdates`, and the complete `build --test --scan-binary --no-tarball app.cliphistory:7` run from the public upstream source. Source and APK scans passed. The recipe downloaded the public original-key reference, accepted its declared signing certificate, and compared it successfully with the built APK. Apksigcopier **1.1.1** also reproduced the signed reference byte for byte.

- Unsigned Windows/Linux/F-Droid SHA-256: `b6f5678d02615182dc97b49977bb4d9412d3048e0364700fdc992ebe2dfc8154`
- Signed reference SHA-256: `6bd7239fbf12b9366cc70714275b977025eeffc4a7e49275833c2e7d8c61430d`
- Original certificate SHA-256: `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`
- AGP 9.1.1, Gradle 9.3.1, Linux JDK 17, SDK android-37.0, Build Tools 36.0.0; no NDK.
- Clean Windows unit tests: **44 passed**, zero failures/errors/skips.
- Source-distribution audit: **72/72 checks passed**, including no signing material.
- Android lint: **0 errors, 14 disclosed warnings**, without suppression: one AGP advisory, one durable preference commit, one plural candidate, four private-API calls and seven unused strings.

Public qualification and selected actual outputs: https://github.com/saroo98/cliphistory-android/blob/main/docs/F_DROID_PUBLICATION.md and https://github.com/saroo98/cliphistory-android/tree/main/docs/release-1.3.3 . One local Python 3.14.4 process aborted during the first APK scan; an unchanged complete retry passed. The cause of that interpreter crash is not established; no scanner bypass was used.

## Current CI status

Maintainer linsui triggered [F-Droid pipeline #2906329865](https://gitlab.com/fdroid/fdroiddata/-/pipelines/2906329865) on 2026-10-02. Eight jobs passed: build, checkupdates, git redirect, fdroid lint, tools check scripts, schema validation, source inspection, and APK inspection. The report confirmed a reproducible APK. The only failed job was `fdroid rewritemeta`, requiring the `Binaries` URL on an indented continuation line.

Correction commit `8465d0ccae6b6bff96c00b1b35c7052b9fab0af1` uses the corrected artifact from [job #16893233572](https://gitlab.com/fdroid/fdroiddata/-/jobs/16893233572), byte for byte. All parsed metadata values, the release tag, source revision, reference APK and signer are unchanged. Local `readmeta`, `rewritemeta` (no further changes), and `lint` passed with CI's fdroidserver revision `c21c177ff6d813697aaf9c988ca9fbb2b571b468` and ruamel.yaml 0.18.10. Corrected YAML SHA-256: `a3dd3f50de324c75d9deaca5e83774a75c31123c09a334da675a810acbc3bf31`.

The prior report has nine informational entries and one minor R8 warning. The existing release configuration deliberately disables minification because Shizuku loads the UserService entry point reflectively (`app/build.gradle.kts`). The published APK is 2.5 MB with no native libraries. No new APK or release is needed for this formatting correction.

Automatic [branch pipeline #2906846719](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2906846719) and [MR pipeline #2906847320](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2906847320) for the corrected commit again have zero jobs under the account identity-verification gate; `yaml_errors` is null. [A maintainer rerun was requested](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_3945365323). No full CI pass for the corrected commit, inclusion, or listing is claimed. No phone/card information was submitted.

## Runtime and privacy limits

Recording requires the separate Shizuku app, API 13+, explicitly authorized as non-root shell UID 2000 on the primary profile. Saved history is readable offline. Non-root Shizuku must be started again after reboot; internal Android clipboard APIs and OEM behavior remain compatibility limits. Recovery respects explicit Stop, Force stop and Task Manager Stop; optional battery settings do not promise unlimited background operation.

Private history is excluded from Android backup. Screenshots default to blocked with an optional setting; Recents privacy is independent. Sensitive-marked clips, unsupported payloads and text over 64 KiB are skipped. Unmarked copied text may contain secrets. There is no overlay permission, Accessibility service, keyboard replacement, ads or analytics. UI checks include an API 36 emulator and signed Pixel API 37 upgrade/launch/Settings inspection. Zero latency, every-OEM behavior, exhaustive spoken TalkBack and multi-day battery measurements are not claimed.
