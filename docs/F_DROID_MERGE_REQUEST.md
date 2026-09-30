# New app: ClipHistory

ClipHistory keeps a private, searchable rolling history of plain-text clipboard entries using an explicitly authorised Shizuku shell recorder. Saved history remains readable offline. The native interface supports direct copying, full-text preview, pause, configurable limits, light/dark/system appearance and a Quick Settings entry point.

## Source and build

- Source: https://github.com/saroo98/cliphistory-android
- Issue/contact tracker: https://github.com/saroo98/cliphistory-android/issues
- Upstream licence: MIT; README welcomes F-Droid inclusion.
- Release: https://github.com/saroo98/cliphistory-android/releases/tag/v1.2.0
- App ID: `app.cliphistory`; version 1.2.0, code 3.
- Commit: `5739fb2bfbe26023aa02782a83eb2eb7443d126d`.
- Proposed fdroiddata file: `metadata/app.cliphistory.yml`, copied from the upstream `fdroid/metadata/app.cliphistory.yml`.
- Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform `android-37.0`, Build Tools 36.0.0. No NDK or proprietary IDE required.
- Store material: tagged upstream `fastlane/metadata/android/en-US/`, including descriptions, changelog `3.txt`, 512-pixel icon and four native View screenshots using synthetic data.

The metadata contains one current build and enables tag-based updates. There are no external source repositories/submodules, bundled native libraries or large ABI-specific resources requiring split APKs. Runtime dependencies are free software from Google Maven/Maven Central; full notices are bundled. The separate official Shizuku manager is Apache-2.0 licensed.

## Reproducible build evidence

`fdroid readmeta`, `rewritemeta`, `checkupdates` and `lint` succeed locally. The recipe test `fdroid build --test --scan-binary --no-tarball app.cliphistory:3` succeeds using fdroidserver 2.4.5. It scans the source and APK, downloads the public reference APK, verifies its comparison to the rebuilt binary and checks the allowed signer. No scan was skipped or forced.

Windows and Linux clean-tag unsigned APK SHA-256: `c8e05e4e0e2a0feab3a784a2320e4de8026234b31bf968ef5c4b15fd5b172ccf`.

Public signed APK SHA-256: `7eb1043dc6c33cba080a2567e35c32b223981305fa90c0783b4efc21673b481d`.

Allowed signing certificate SHA-256: `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`.

Upstream signs with apksigner's alignment preservation option. Signature copying to the Linux rebuild reproduces the signed APK byte for byte. Logs and the detailed verification report are in the upstream `docs/release-1.2/` directory. This is local recipe evidence; the public fork's GitLab CI result must be added when it actually runs.

## Runtime and privacy boundaries

Requires Android 14+, the primary device profile, and separately installed Shizuku API 13+ running as non-root shell UID 2000. Users explicitly grant Shizuku access; guided setup is included. Shizuku must be started again after reboot. Root-mode recording and work profiles are unsupported.

No internet permission, ads, analytics, cloud service, keyboard replacement or executable self-updater is included. Storage is private and excluded from backup; screenshots are blocked. Sensitive-marked clips, unsupported payloads and text over 64 KiB are skipped. Sensitivity relies on the source app marking clips correctly.

The recorder uses Android internal clipboard interfaces. Four related lint warnings are disclosed. Capture tests pass on API 36 emulation and an isolated Pixel 8 Pro API 37 validation package. Exhaustive OEM/Android 14 device coverage, TalkBack and multi-day battery testing are not claimed.

Current fdroiddata has no file for this app ID, and exact-ID merge-request and ClipHistory packaging-request searches found no existing entries on 2026-09-30. No related request is claimed or linked without an actual URL. The GitLab fork, its CI and this merge request's external checklist items remain pending until created and verified.
