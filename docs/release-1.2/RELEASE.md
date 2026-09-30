# ClipHistory 1.2.0 release verification

Verified on 2026-09-30. The release is prepared for F-Droid's independent inclusion review. It is not yet listed in F-Droid's main repository.

## Artifacts and source

- [Signed APK](https://github.com/saroo98/cliphistory-android/releases/download/v1.2.0/ClipHistory-1.2.0.apk)
- [APK SHA-256](https://github.com/saroo98/cliphistory-android/releases/download/v1.2.0/ClipHistory-1.2.0.apk.sha256)
- [Complete source and APK archive](https://github.com/saroo98/cliphistory-android/releases/download/v1.2.0/ClipHistory-1.2.0-complete.zip)
- [Archive SHA-256](https://github.com/saroo98/cliphistory-android/releases/download/v1.2.0/ClipHistory-1.2.0-complete.zip.sha256)
- [Public source tag](https://github.com/saroo98/cliphistory-android/tree/v1.2.0)
- [F-Droid metadata](https://github.com/saroo98/cliphistory-android/blob/main/fdroid/metadata/app.cliphistory.yml)

The APK's exact production source is tag `v1.2.0`, commit `5739fb2bfbe26023aa02782a83eb2eb7443d126d`. Subsequent main-branch changes document verification, pin the handoff recipe and improve packaging/signing helpers. They do not change the APK's production code. Clone the tag for exact reproduction; an extracted ZIP lacks the Git revision that AGP embeds in the APK.

| Property | Verified value |
|---|---|
| Application ID | `app.cliphistory` |
| Version | 1.2.0, code 3 |
| Minimum / target Android API | 34 / 37 |
| Debuggable | No |
| Requested permission | `moe.shizuku.manager.permission.API_V23` only |
| Internet permission | Absent |
| Runtime dependency notices | Full text bundled in `assets/licenses/NOTICES.txt` and readable in the app |
| Source licence | MIT |

## Reproduction and signing

Both unsigned APKs built from clean public-tag clones, on Windows and Ubuntu Linux, have SHA-256:

```text
c8e05e4e0e2a0feab3a784a2320e4de8026234b31bf968ef5c4b15fd5b172ccf
```

The final signed APK has SHA-256:

```text
7eb1043dc6c33cba080a2567e35c32b223981305fa90c0783b4efc21673b481d
```

The existing signing certificate has SHA-256:

```text
ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82
```

The release uses Build Tools 36.0.0 `apksigner` with `--alignment-preserved true`, v1 disabled and v4 disabled. This preserves the unsigned ZIP's alignment and allows upstream signature copying. Copying the signature to the independently built Linux unsigned APK with apksigcopier 1.1.1 produces the identical signed digest above. The copied signature verifies using the Linux SDK's apksigner. The final APK also passes 4-byte / 16 KiB alignment inspection and its embedded source revision matches the release commit.

The toolchain is AGP 9.1.1, official checksum-pinned Gradle 9.3.1, JVM target 17, Android platform `android-37.0`, Build Tools 36.0.0 and Shizuku API/provider 13.1.5. Linux uses JDK 17; Windows uses Android Studio's supported JBR. This demonstrates reproduction in these two environments, not a build on F-Droid's own server. [F-Droid describes the separate signature-copy verification requirement.](https://f-droid.org/docs/Reproducible_Builds/)

F-Droid server 2.4.5 metadata lint passes with current fdroiddata category configuration. The source scanner reports zero fatal findings for the public tag. Its signed-APK scanner exits with code 0, including the non-free class and extra signing-block checks. No custom scan exemption or disabled lint baseline was added. Clean-tag Android lint has 0 errors and 5 warnings: four internal clipboard API warnings and one pinned Gradle version suggestion.

The metadata recipe itself also passes `fdroid build --test --scan-binary --no-tarball app.cliphistory:3` locally. F-Droid downloads the public binary, successfully compares the build and validates the allowed signer. Readmeta, rewritemeta, checkupdates and lint succeed. See `fdroid-recipe-build.txt` and `../F_DROID_SUBMISSION.md`. GitLab CI and main-repository review have not run. The user's guide check changes submission metadata/documentation only; production source and signed APK digest remain unchanged.

## Behavior verified

- 50 core cases, six recorder-state tests and six release-data tests pass. The v1 storage format remains byte compatible; malformed UTF-8 and corrupt snapshots are rejected.
- 29 native UI assertions cover search, paging, preview, copy feedback, appearance, full notices and failure presentation.
- 11 release regression assertions cover preservation of 73 loaded entries and the visible anchor across rotation, stale navigation callbacks, background feedback, typed missing entries and cache corruption.
- Nine large-text landscape assertions include an actual pointer tap on the results control above the keyboard, restored history and panel dismissal at 200% text.
- Five capacity assertions pass for 500 entries of 65,536 bytes in a 192 MiB process. Observed encode: 327 ms; first offline read: 381 ms; cached read: under 1 ms; cached initial page: 423 ms; next page: 42 ms. These samples are not device-wide latency guarantees.
- Sixteen real Shizuku capture assertions pass on API 36 emulation and sixteen on a Pixel 8 Pro API 37. They cover shell UID 2000, listener registration, app-private descriptor persistence, actual background capture, exact Unicode/whitespace, duplicate suppression, pause, self-test nonce exclusion and marked-sensitive/oversized filtering.
- The final signed APK successfully upgrades the signed 1.1.0 release on the temporary API 36 emulator and cold-launches as version code 3. Android reports no debuggable flag and the expected permission only.
- Rendered light, dark, preview and help Views and synthetic phone/large-text captures were inspected. Production screenshot protection remains enabled.

Physical tests used a separate validation package. The original clipboard was restored as an opaque object without logging its text. The normal phone app's history was not cleared. Both temporary phone packages and their shell helper were removed after testing; the normal installed package remains present.

Evidence files in this directory contain synthetic test data and selected output with machine paths normalised. No keys, passwords, test APKs, personal clipboard text or device history are included in the distribution.

## Assessment

Overall: **85/100**. F-Droid publication readiness: **84/100**. These are evidence-based engineering judgments, not official F-Droid ratings or a guarantee that every possible defect is absent. Rounding uses the weighted totals below.

| Overall dimension | Score | Weight | Evidence and deductions |
|---|---:|---:|---|
| Functionality | 88 | 25% | Real capture, offline history, search/copy, pause, filtering and error flows pass; real reboot and locked tile coverage remain incomplete. |
| UI and UX | 84 | 25% | Calm native light/dark design, direct copy/preview actions, guided setup and stable navigation; Shizuku setup is still a multi-step prerequisite. |
| Efficiency | 87 | 15% | Bounded history/IPC, cached reads, skipped obsolete work and maximum-size checks; no multi-day power study. |
| Reliability | 86 | 15% | Lifecycle, rotation, corruption and typed failures tested; internal Android clipboard interfaces can change. |
| Privacy and security | 87 | 10% | Private no-backup storage, screenshot protection, caller checks and no network permission; sensitivity relies on source app flags and this does not protect against a compromised device. |
| Accessibility and compatibility | 78 | 10% | 48 dp controls, explicit labels/actions, large-font and landscape recovery; exhaustive TalkBack traversal and Android 14/OEM device coverage remain open. |
| Weighted total | **85.45** | **100%** | Reported overall score: **85**. |

| F-Droid readiness dimension | Score | Weight | Evidence and deductions |
|---|---:|---:|---|
| Public source and licence | 98 | 20% | Public tagged source, full app licence and source archive. |
| Dependencies and notices | 94 | 20% | Free runtime dependencies, full notices and successful scanners; independent review remains necessary. |
| Build and reproduction | 96 | 20% | Clean Windows/Linux APK equality and successful signed reproduction; F-Droid's actual build environment has not run it. |
| Metadata and signing | 96 | 15% | Linted recipe with exact revision, store assets, upstream binary URL and preserved signing identity. |
| Validation and maintenance | 82 | 15% | Automated and real-device evidence, documented setup/failures; limited OS/manufacturer coverage and internal API maintenance. |
| External inclusion approval | 0 | 10% | No accepted Request for Packaging, merged fdroiddata recipe or main-repository listing is claimed. |
| Weighted total | **84.30** | **100%** | Reported readiness score: **84**. |

## Remaining boundaries

Shizuku must be installed separately and run as the non-root shell user in the primary profile. It must be started again after reboot. Saved history stays readable while it is stopped. Root-mode Shizuku and other user profiles are deliberately unsupported by the recorder's security policy.

At 200% text in landscape, a system keyboard can occupy nearly the entire window. Android owns that keyboard. The explicit results control remains reachable above it and closes it to restore the history view; the test verifies this recovery rather than claiming rows fit alongside every keyboard.

No exhaustive TalkBack audit, multi-day battery soak, real reboot/locked tile acceptance, every manufacturer build or future Android version is claimed. These are the main deductions from a higher score. No known release-blocking failure remains in the tested flows.

The supplied metadata and release are ready for an F-Droid packaging submission. Acceptance and publication require F-Droid's review and build verification; the GitHub release does not create an F-Droid listing.
