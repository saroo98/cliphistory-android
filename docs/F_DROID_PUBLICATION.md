# F-Droid publication handoff

Source: https://github.com/saroo98/cliphistory-android

The working release is **1.3.0 / code 4**, application ID `app.cliphistory`, MIT, Android 14+. It requires the primary profile and separately installed Shizuku running as non-root shell UID 2000. Non-root Shizuku needs starting after reboot.

The public [v1.3.0 release](https://github.com/saroo98/cliphistory-android/releases/tag/v1.3.0) pins source `a72134900c574a354a366b24c979f7903b6ace35`. Its reference APK SHA-256 is `13fa9570bb2c7e4df7be34b0e4dcb2a92c278113d91ec6f7f43a1061dba1d070`. Clean Windows/Linux unsigned bytes match, and signature reproduction matches the signed public binary. F-Droid server 2.4.5 metadata, update discovery, source/APK scans and actual local recipe build pass. See [the exact release evidence](release-1.3/RELEASE.md).

On 2026-09-30 the exact metadata lookup in fdroiddata returned HTTP 404, and the app-ID merge-request search returned zero matches. The browser is signed out of GitLab. No authenticated fork, pipeline or inclusion merge request has been created, and the app is not claimed to be listed. Account sign-in is the remaining submission blocker; F-Droid acceptance remains an external decision.

Provided material: source/build instructions, Fastlane descriptions and changelog 4, synthetic native screenshots, full bundled dependency notices, exact five-permission audit and fresh runtime coverage in `docs/release-1.3/`. The existing 1.2 metadata build block, `Binaries` and allowed signing key are retained. The 1.3 block pins the verified source revision above. Later evidence/metadata commits do not move the immutable release tag or replace its APK.

The current App inclusion template requires latest-version-only metadata for a new app. Use `fdroid/submission/app.cliphistory.yml` for the inclusion MR; it contains the identical verified 1.3 recipe. Its metadata/update/scans/actual build/reference comparison checks pass independently. The historical upstream recipe remains intact. Search by app name and the additional fdroiddata/RFP issue searches return zero scoped matches on the date above.

Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No proprietary runtime SDK, network client, ads or analytics. No custom scan exemption or disabled lint baseline.

Android lint warnings for internal clipboard APIs and durable worker-thread preference commits are disclosed. Unknown platform interfaces fail with a visible error. The app adds no clipboard polling or background watchdog; optional recovery uses a quiet foreground notification with Open and Stop.

F-Droid requires a source recipe and independent build/review, not only an APK. A public fdroiddata fork, pipeline and inclusion merge request are separate steps. Only actually observed results may be checked off. Read `docs/F_DROID_SUBMISSION.md` and the current guide before submission.

Primary references:

- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
