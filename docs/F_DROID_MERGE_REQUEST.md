# Submitted F-Droid inclusion request

[MR !50917: New app: ClipHistory](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917), created and updated 2026-10-02. The verified submitted description follows. The current recipe is 1.3.4/code 8 at `55069637`; current CI and report items await a maintainer run. The previous 1.3.3 pipeline passed all nine jobs. Acceptance and listing are not claimed.

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

* [ ] All pipelines should pass. **A new maintainer run is needed for 1.3.4/code 8. The previous 1.3.3 pipeline passed all nine jobs.**
* [ ] All warnings and errors in the Reports tab should be fixed or explained. **The latest fork run has no jobs or reports. The prior R8 notice is explained below.**
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

## App and release

ClipHistory is a local, searchable plain-text clipboard history app for Android 14+. The upstream owner requests inclusion. MIT source, no ads, trackers, account, network service or Internet permission. Recording requires Shizuku in non-root shell mode; it must be started again after reboot.

Latest release: **1.3.4 / versionCode 8**, tag `v1.3.4`, source `97c1e996e2619a5cf6f86f7f70614576d795b8c3`.

- [Source and issue tracker](https://github.com/saroo98/cliphistory-android)
- [Release and reference APK](https://github.com/saroo98/cliphistory-android/releases/tag/v1.3.4)
- [Qualification, hashes and actual logs](https://github.com/saroo98/cliphistory-android/blob/main/docs/F_DROID_PUBLICATION.md)

This release adds the final logo. Clipboard behavior, permissions and dependencies are unchanged. Fastlane en-US text, changelog 8, a 512px icon, a 1024 x 500 feature graphic and seven native screenshots are upstream. The MR adds only the latest `metadata/app.cliphistory.yml`, using a full source hash, canonical LF YAML, tag updates and the original signing key. No disabled builds, scanner exemptions, external source repositories or native libraries; ABI splitting is unnecessary for this 2.5 MB APK. No related request was found in scoped checks on 2026-10-02.

## Verification and CI

Clean Windows and Linux builds have identical unsigned bytes. The complete local F-Droid recipe passed source/APK scans and comparison with the downloaded original-key APK. Apksigcopier 1.1.1 reproduced the signed APK byte for byte. Fresh checks: 44 unit tests passed; Android lint has zero errors and 14 disclosed warnings; source audit passed 72/72. Native light/dark regression checks and logo screenshots also passed. Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. Canonical metadata was checked with CI's fdroidserver `c21c177ff6d813697aaf9c988ca9fbb2b571b468` and ruamel.yaml 0.18.10.

The [previous maintainer pipeline](https://gitlab.com/fdroid/fdroiddata/-/pipelines/2907476394) passed all nine jobs for **1.3.3**, after the metadata-formatting correction. A new run is needed for this 1.3.4 update at `550696372b43e4dedb641c2a8cc618125ae1d235`. The automatic [fork MR pipeline](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/2907542150) has zero jobs under GitLab's identity-verification gate. No phone or financial information was submitted. Could a maintainer rerun CI on the latest commit?

The prior report's minor R8 notice remains applicable: release minification is deliberately disabled because Shizuku loads the UserService entry point reflectively. Internal Android clipboard APIs and OEM behavior remain compatibility limits. History is private and excluded from backup; screenshots are blocked by default. Explicit Stop, Force stop and Android Task Manager Stop are respected. No universal capture or zero-latency claim is made.
