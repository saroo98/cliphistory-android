# ClipHistory 1.2.0 verification

Date: 2026-09-30. Package `app.cliphistory`, version code 3, minimum API 34, compile/target API 37.

## Confirmed checks

- 50 core regression cases, six recorder-state tests and six release-data tests pass. The new codec remains byte compatible with the existing v1 storage format, including maximum 500-entry histories and strict malformed UTF-8 rejection.
- Windows JVM tests, release lint and debug/test/release APK assembly pass. Clean release-tag lint has 0 errors and 5 warnings: four intentional internal clipboard API warnings and a pinned Gradle update suggestion. These are not hidden by a baseline or disabled checks. An earlier working-folder run also flagged an empty obsolete resource directory; that directory is absent from Git and the distribution.
- 29 emulator UI assertions pass, including full licence notices, search, pagination, preview, copy behavior, appearance changes and error presentation.
- Nine large-text landscape assertions pass, including a real tap on the keyboard dismissal control, recovered history and panel dismissal at 200% text. In extremely constrained keyboard layouts, the results control stays above the keyboard and restores the history viewport.
- Eleven current-source regression assertions pass for rotation and entry anchor preservation, delayed preview navigation, feedback removal when backgrounded, missing entries and cache invalidation after file corruption. Diagnostics reports the actual corruption cause.
- Capacity checks pass using 500 entries of 65,536 bytes in a 192 MiB emulator process. Recorded encode 327 ms, initial offline read 381 ms, repeated read under 1 ms, cached first page 423 ms and next page 42 ms. These are observed samples, not universal benchmarks. See `docs/release-1.2/capacity.txt`.
- Real Shizuku tests pass with 16 assertions on API 36 emulation and 16 on a Pixel 8 Pro API 37. They verify shell UID 2000, listener registration, durable app-private descriptors, actual background capture, nonce exclusion, duplicate suppression, pause, marked-sensitive and oversized filtering, exact Unicode/whitespace and typed missing-entry results.
- The Pixel test targeted the separate `app.cliphistory.validation` application and a test-APK producer. It did not clear the installed normal app's history. The original clipboard was restored as an opaque object without printing its text. The phone's existing Shizuku version was 13.7.0-thedjchi; the emulator used official Shizuku 13.6.0.
- F-Droid server 2.4.5 metadata lint succeeds with current fdroiddata categories. Its public-tag source scan reports zero fatal findings and its signed-APK scan exits successfully. No custom `scanignore` or `scandelete` exemption was added. Its standard scan removes official wrapper artifacts from its temporary source copy.
- Windows and isolated Linux builds from public tag `v1.2.0` produce byte-identical unsigned APKs. Copying the upstream signature to the Linux build using apksigcopier 1.1.1 reproduces the signed APK byte for byte; the copied signature verifies. Signing preserves the original key and the unsigned APK's alignment. Digests are in `docs/release-1.2/RELEASE.md`.
- The F-Droid metadata recipe itself passes a local `fdroid build --test --scan-binary --no-tarball app.cliphistory:3` run, including source/APK scans and comparison against the downloaded public signed APK with the allowed signer. Metadata reading, formatting, tag-update discovery and lint pass. Current submission-guide/template checks are recorded in `docs/F_DROID_SUBMISSION.md`; GitLab CI and acceptance remain pending.
- The final signed APK installs as an update over the signed 1.1.0 release on the temporary API 36 emulator. Version code 3 launches normally and the installed package is not debuggable. The phone's two temporary validation packages and their shell helper were removed; the normal installed app remains present.

Selected logs and synthetic screenshots are in `docs/release-1.2/`. Store images are rendered actual app Views populated with synthetic sample text. Recording presentation fixtures do not establish connectivity; the separate live tests do. View captures omit system-owned keyboard and status-bar surfaces and retain production FLAG_SECURE.

## Remaining coverage limits

No extended multi-day battery run, exhaustive TalkBack traversal, every OEM Android build, real reboot or locked-phone Quick Settings acceptance is claimed. Very small windows combined with a keyboard that occupies almost the entire window can leave no room for history; tapping the explicit results control, finishing search or closing the keyboard restores it. The app uses internal Android clipboard interfaces, so future platform changes can require maintenance. The full checklist remains in `DEVICE_TESTS.md`.

F-Droid publication needs its independent inclusion review and build verification. Source, metadata and reproducible APK evidence do not constitute main-repository acceptance.

## Signing

The release keeps the original signing identity. Certificate SHA-256:

`ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`

Keys and passwords stay in the ignored local `.signing` directory. They are not included in Git, source packages or release assets. APK and archive digests are provided beside the artifacts.
