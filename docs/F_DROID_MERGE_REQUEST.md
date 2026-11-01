# Current F-Droid merge-request description

Snapshot verified on 1 November 2026 for MR !50917, submission commit c86f77422e02a5de07ca5446c62d7c58b11541ae. All nine CI jobs passed. The report-view limitation remains explicit; read the live MR before changing any status. Release evidence: [FINAL_RELEASE.md](release-1.4.5/FINAL_RELEASE.md).

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

* [x] All nine jobs passed in [pipeline 3015458811](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/3015458811) for 1.4.5, commit `c86f77422e`.
* [ ] Review the Reports tab for 1.4.5. GitLab currently shows no scanning jobs for this fork run; job-log findings and the independent Fastlane check are explained below.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

## App and release

ClipHistory is a local plain-text clipboard history app for Android 11+. It uses Shizuku or a compatible service in non-root shell mode. The service must be restarted after reboot. There is no Internet permission, account, advertising or analytics.

Latest release: **1.4.5 / versionCode 15**, tag `v1.4.5`, source `329ab008db02a66aae151891b56e2db9d533d9a0`.

- [Source and issue tracker](https://github.com/saroo98/cliphistory-android)
- [Release and signed APK](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.5)
- [Device and emulator qualification](https://github.com/saroo98/cliphistory-android/blob/v1.4.5/docs/release-1.4.5/FINAL_QUALIFICATION.md)

History is encrypted on device. Capture of source-marked sensitive text is optional and off by default; automatic deletion defaults to five minutes. Android clipboard-access warnings remain a system-wide user choice.

## Verification and compatibility

1.4.5 removes the original-manager package check that blocked compatible Shizuku forks, adds explicit manager selection, and fixes update recovery on Android 11–13 while preserving genuine user stops.

The local F-Droid recipe built the pinned public source, passed source/APK scans, and reproduced the signed GitHub reference with the allowed key. Windows and Linux unsigned builds match. There are 80 passing unit tests; Android lint reports zero errors and 24 disclosed warnings.

The final candidate passed capture/encryption checks on eight emulator targets from Android 11 through 17, including Shizako’s actual F-Droid CI build on Android 17. Signed 1.4.4 upgrades passed on Android 11–13. The exact release also passed an in-place Pixel 8 Pro check, preserving observed history/settings and passing the production connection test. The release differs from that candidate only in Git revision metadata.

HyperOS 3 still needs the reporter’s retest. Official Shizuku 13.6 on Android 17 may require opening ClipHistory after a server restart. Fresh Shevery approval may require manual authorization in its manager. These external limitations remain documented.

Minification remains disabled because Shizuku loads the UserService reflectively. The APK is 2.67 MB with no native libraries. All nine current CI jobs passed, including original-key reproduction. The downloaded CI unsigned APK matches the Windows and Linux builds. The APK job reports the six declared permissions, signing identity and size as information, plus the explained minor R8 notice. The same Fastlane checker run locally reports one informational entry and no warnings. Chrome blocked the source-report artifact download, so I have left the Reports checklist item open.
