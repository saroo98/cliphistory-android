# ClipHistory 1.3 F-Droid submission preparation

The upstream owner requests F-Droid inclusion. Follow the current [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/) and [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/). Local builds do not establish GitLab CI or inclusion.

## App and source

- Source/contact: https://github.com/saroo98/cliphistory-android and its issue tracker.
- Application ID: `app.cliphistory`; version 1.3.0/code 4; Android 14+.
- MIT source and original native artwork. Full runtime dependency notices are bundled. Shizuku SDK is MIT; the separate official manager is Apache-2.0.
- Requires Shizuku API 13+ running as non-root shell UID 2000, primary profile. Non-root Shizuku needs starting after reboot.
- No internet, overlay, accessibility-service or keyboard permission. Five reviewed permissions cover Shizuku, foreground recovery, its special-use type, notifications and boot.
- Upstream Fastlane includes descriptions, changelog `4.txt`, 512px icon and synthetic native screenshots. Private device media is excluded.

## Build and verification

AGP 9.1.1, Gradle 9.3.1, JDK 17, platform `android-37.0`, Build Tools 36.0.0. No NDK, proprietary runtime SDK, API key or build account. The recipe retains 1.2 and adds 1.3 using its actual source revision. `Binaries` and the allowed original signer preserve update identity. No scanner exemptions or lint baseline.

Exact revision, digests, passing Windows/Linux reproduction and actual local recipe results are in [release-1.3/RELEASE.md](release-1.3/RELEASE.md). Fresh runtime evidence and limits are in `COVERAGE.md` and `TILE_TIMING.md`. Historical 1.2 results do not qualify 1.3.

## Submission procedure

1. Verify public release source/reference APK against the metadata revision and versioned binary URL.
2. Check current fdroiddata for the exact ID and any existing inclusion request. Earlier empty searches are not current proof.
3. In an authenticated public fork of `fdroid/fdroiddata`, create an unprotected `app.cliphistory` branch from its current default branch. Copy only upstream `fdroid/submission/app.cliphistory.yml` to `metadata/app.cliphistory.yml`. The current App inclusion template requires only the latest build. This submission copy contains the same verified 1.3 recipe; the upstream `fdroid/metadata/` copy retains 1.2 as required by the approved implementation plan.
4. Run `fdroid readmeta`, `fdroid rewritemeta app.cliphistory`, `fdroid checkupdates app.cliphistory`, `fdroid lint app.cliphistory`, and `fdroid build --test --scan-binary --no-tarball app.cliphistory:4` in the prepared environment.
5. Run the actual fork pipeline. Use GitLab's current App inclusion template and `F_DROID_MERGE_REQUEST.md`. Check external items only after observing their results.
6. Open the inclusion merge request and record its real URL/pipeline result. F-Droid maintainers decide inclusion and may request recipe changes.

On 2026-09-30 the exact metadata lookup returned HTTP 404 and the app-ID MR search returned zero. The GitLab browser is signed out, so an authenticated public fork, its CI and the inclusion merge request remain unfinished. Local preparation is complete; sign-in is required to continue the external workflow. Do not claim the app is already listed. Internal Android API compatibility and measured device/performance/accessibility limits remain disclosed after packaging.
