# ClipHistory F-Droid submission

Local state checked 1 November 2026. Follow the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/), [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) and [contribution instructions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md). The reusable engineering and publication workflow is in [F_DROID_ANDROID_GUIDE.md](F_DROID_ANDROID_GUIDE.md).

Reuse [MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917). The local submission recipe currently pins published **1.4.4/code 14**, source `f240f9b39ba8425747d4180e7b6932f09717b817`. The unpublished **1.4.5/code 15** candidate has [separate qualification](release-1.4.5/FINAL_QUALIFICATION.md). Do not relabel the older public APK, recipe or pipeline as qualification of 1.4.5. Read the live MR before making any external status claim.

## Current candidate

- `app.cliphistory`, Android 11/API 30 minimum, native Android Views.
- Public MIT source and issue tracker: https://github.com/saroo98/cliphistory-android. Artwork provenance and runtime dependency notices are included.
- Separately installed Shizuku API 13+ service with upstream UserService support, non-root shell UID 2000, primary profile. Known manager shortcuts include Shizuku, Shizako, Porter, Shevery and Shizuku+. A known package does not certify every version or mode. Porter needs its compatibility companion. See qualification for tested versions and fresh-approval limits.
- No Internet, overlay, Accessibility-service or keyboard permission, advertising, analytics, account, cloud service, proprietary runtime SDK or bundled native library.
- Six declared permissions: the original Shizuku access permission, Shevery's user-controlled access permission, foreground service, foreground special use, notifications and boot. The additional fork permission never substitutes for actual user authorization. Recovery respects explicit Stop and Android user-stop controls.
- Fastlane English descriptions, changelog 15, approved icon, feature graphic and native screenshots are in source. Personal-device evidence remains local.

## Build and submission gates

AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No NDK, build account or API key. Original-key reproduction uses `Binaries` and `AllowedAPKSigningKeys`. The current candidate source and APK scans use no custom exclusions.

1. Close confirmed app defects and maintain the qualification register. Retain reproducible upstream limitations and exact hardware gaps. Never replace a reported HyperOS 3 retest with a claim based on a stock emulator or different firmware.
2. With release authorization, freeze reviewed source and store materials in an immutable release commit. Rebuild from that exact commit on Windows and independently on Linux. Compare unsigned bytes, alignment, original certificate and exact signature reproduction. A working-tree build is preparation, not this release gate.
3. Publish the immutable tag and versioned reference APK. Do not move an old tag or overwrite a public release asset. Download the reference again and verify its hash and signer.
4. Update `fdroid/submission/app.cliphistory.yml` to the real release commit/version/code. Keep only the latest build in the new-app submission and retain historical builds in `fdroid/metadata/app.cliphistory.yml`. Never invent a commit or point the public recipe at an uncommitted candidate.
5. Run current fdroidserver `readmeta`, `rewritemeta app.cliphistory`, `lint app.cliphistory`, `checkupdates app.cliphistory`, and `build --test --scan-binary --no-tarball app.cliphistory:15` after the code-15 public reference exists. Inspect source/APK scans and reference/signing comparison. Preserve canonical YAML produced by the CI-compatible serializer, including a wrapped `Binaries` value.
6. Update the existing MR with only the app metadata. Keep its description concise: behavior fixed, exact versions/devices tested, remaining external retest. Check checklist items only when their evidence exists for the submitted commit.
7. Inspect the actual latest CI jobs and reports. A jobless pipeline is not a pass. If account access prevents the run, ask a maintainer to run it; never change billing or provide identity documents automatically. Historic verification notices do not establish the current account state.
8. After merge, check the production build, signing and index. Confirm the app/version appears in the catalogue. Maintainers decide inclusion; a prepared APK, open MR or passing local build is not publication.

The current local APK and tests are recorded in [1.4.5 qualification](release-1.4.5/FINAL_QUALIFICATION.md). [Publication history](F_DROID_PUBLICATION.md) and [the 1.4.4 release](release-1.4.4/FINAL_RELEASE.md) retain earlier evidence without implying a new pass.
