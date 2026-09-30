# F-Droid publication handoff

Repository: https://github.com/saroo98/cliphistory-android

Release: https://github.com/saroo98/cliphistory-android/releases/tag/v1.2.0

Application ID: `app.cliphistory`. Version name `1.2.0`, code `3`. Licence MIT. Minimum Android 14 / API 34. It requires the primary device profile and separately installed Shizuku running as non-root shell UID 2000.

## Provided files

- `fdroid/metadata/app.cliphistory.yml`: source location, pinned release build, update checks and upstream binary verification/signing identity.
- `fastlane/metadata/android/en-US/`: descriptions, changelog, rendered icon and four synthetic app screenshots.
- `LICENSE`, `LICENSES/`, `THIRD_PARTY_NOTICES.md` and the APK's readable licence screen: app and dependency notices.
- `BUILDING.md`, `DEVICE_TESTS.md`, `VERIFICATION.md` and `docs/release-1.2/`: reproducible build instructions, device coverage and selected evidence.
- Signed APK, SHA-256 and complete source archive in the GitHub release.

The release build uses AGP 9.1.1, Gradle 9.3.1, JDK 17, platform `android-37.0` and Build Tools 36.0.0. The official Gradle wrapper is checksum pinned. F-Droid's standard scanner may remove wrapper files and select the matching installed Gradle distribution. No custom scanning exemption is required.

## Validation performed

F-Droid server 2.4.5 metadata lint passes with current fdroiddata categories. Its source scan reports zero fatal findings. Isolated Linux compilation succeeds. The source tag must be used for reproducibility so Android's embedded source revision matches. Final signing and byte comparison results are in `docs/release-1.2/RELEASE.md`.

No signing key, password, device history, SDK cache, emulator log or test APK is published. The APK requests only Shizuku permission and is not debuggable. No proprietary runtime SDK, network client, ad or analytics library is used. Shizuku manager remains an external prerequisite; its installation and runtime requirements are disclosed in the listing and in the app.

## Inclusion workflow

F-Droid publishes applications after reviewing source and reproducing their builds; a developer APK alone is insufficient. Submit the supplied metadata and public release source through its Request for Packaging / fdroiddata workflow. Maintainers may adjust the recipe for the current build server or request additional dependency and compatibility evidence. The `Binaries` field requests verification against the upstream signed APK so the existing signing identity can be retained when reproduction succeeds.

This handoff is prepared for that review. It does not claim an accepted packaging request, merged fdroiddata change or main-repository listing. Those are external decisions and must be reported only after they occur.

Primary references:

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
- https://gitlab.com/fdroid/fdroiddata
