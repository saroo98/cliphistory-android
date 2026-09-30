# ClipHistory 1.3.0 release evidence

Verified on 2026-09-30. App `app.cliphistory`, version 1.3.0/code 4, Android 14+ (minimum API 34), compile/target API 37. Public [source and immutable v1.3.0 release](https://github.com/saroo98/cliphistory-android/releases/tag/v1.3.0).

## Artifact identity

| Artifact | SHA-256 or revision |
|---|---|
| Release source and embedded version-control revision | `a72134900c574a354a366b24c979f7903b6ace35` |
| Clean Windows/Linux unsigned APK | `cbf40f781676ef91d0e97b6480e1adb0e0d5811b4da30070141c8237a6d73852` |
| Public original-key signed APK | `13fa9570bb2c7e4df7be34b0e4dcb2a92c278113d91ec6f7f43a1061dba1d070` |
| Original signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |

The [reference APK](https://github.com/saroo98/cliphistory-android/releases/download/v1.3.0/ClipHistory-1.3.0.apk) and checksum are public. Signature verification passes with one RSA-3072 signer and APK Signature Scheme v3, appropriate for the minimum API. No signing key or password ships in the source, APK or complete archive.

Later documentation/metadata commits record release evidence without moving the source tag or replacing its binary. The complete archive contains that binary and reviewed source with final evidence. Reproduce the binary using the exact Git revision above, not an arbitrary later documentation revision or an extracted archive without Git provenance.

## Build and checks

AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No NDK or new production dependency. Clean clones contain no private signing directory.

```sh
git clone https://github.com/saroo98/cliphistory-android.git
cd cliphistory-android
git checkout a72134900c574a354a366b24c979f7903b6ace35
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PunsignedRelease --no-daemon
```

The full command passes in the clean Windows clone using `gradlew.bat`; clean Linux passes JVM tests, release lint and unsigned release assembly. Both produce identical unsigned APKs. The runtime licence asset is canonical LF; a pre-existing working copy with CRLF produced different bytes only for that asset until restored to Git's canonical bytes. No dependency or licence text was removed.

Twenty-eight JVM methods pass, including 55 aggregated core assertions, six migration tests and nine recovery-policy tests. Source audit passes 70/70. Clean release lint has **0 errors, 6 warnings**: four intentional internal clipboard-interface uses, one durable worker-thread preference commit and one pinned Gradle-update notice. No baseline or scan exemption suppresses errors.

Using apksigcopier 1.1.1, copying the reference signing material onto the Linux unsigned build produces the exact signed digest above; apksigner verification passes. This public signing material is not a private key. Final original-key upgrades from signed 1.1.0 and 1.2.0 pass on the owned API 35 emulator, preserving application UID, synthetic whitespace/Unicode text, pause and limit, with valid mirrored V2 snapshots. See `UPGRADES.json`.

## Runtime and performance

Detailed flows and limits are in [COVERAGE.md](COVERAGE.md). Tests use disposable synthetic emulators and the separate physical validation app; production phone history is never seeded, cleared or exported. Test APKs and hooks do not ship.

The tile uses the public Android 14+ cached activity PendingIntent. Repeated service binding no longer controls opening or dismisses the panel. Measured native two-line row reservation prevents the observed two-pixel loading resize. The app adds no panel animation or artificial delay, overlay permission, watchdog, wake lock, polling loop or global animation change.

Thirty final warm Pixel openings pass 227 assertions, including stable 1080x999px window bounds: loaded pre-draw p95 **123.97ms**, maximum **147.35ms**. SystemUI/accessibility p95 is **711ms**, a separate stage. The final requested schedule passes **12/12** openings after ten 60-second and two 120-second waits. Intentional idle is 840 seconds; total run is 1,096.203 seconds including recording and checks. All **1,147 encoded frames** were reviewed. In 300 captured app-window frame records there is no App Deadline Missed classification and one late presentation classified SurfaceFlinger Scheduling. First app-frame completion is 54.32–113.17ms after touch-up. Android's icon expansion/fade remains visible. Variable-frame-rate video omits unchanged frames, so this is not every physical screen refresh. See [TILE_TIMING.md](TILE_TIMING.md) and `TILE_FINAL_NUMBERS.json`.

API 34 and 36 native tile suites pass 20 assertions each. The first API 34 attempt after repeated package/tile replacement found stale SystemUI bindings; reboot cleared that precondition. That failed attempt is disclosed and does not establish every update path. Real-device timing applies to Pixel 8 Pro Android 17 beta CP41.260828.004.A8 and the isolated debug variant, not every production device.

## F-Droid preparation and remaining external work

The recipe preserves the 1.2 build, public `Binaries` URL and original allowed signer, and pins 1.3 to the exact verified source revision. F-Droid server **2.4.5** passes:

```sh
fdroid readmeta
fdroid rewritemeta app.cliphistory
fdroid lint app.cliphistory
fdroid checkupdates --allow-dirty app.cliphistory
fdroid build --test --scan-binary --no-tarball app.cliphistory:4
```

The local scratch metadata workspace is not itself a Git checkout, so update discovery uses the documented `--allow-dirty` workspace option; this does not skip tag/version discovery. The actual build fetches the public source, scans source and APK, downloads the public reference APK, verifies its allowed signer and compares the rebuilt binary successfully. No force build, scanignore or scandelete exemption is added. Selected output is in `FDROID_CHECKS.txt`.

The current GitLab App inclusion template was read directly on 2026-09-30. It requires only the latest version before a new app is merged. To preserve the approved plan's historical 1.2 block, `fdroid/metadata/app.cliphistory.yml` retains it, while the ready-to-submit `fdroid/submission/app.cliphistory.yml` contains only the identical 1.3 block. All commands above were run again on the actual latest-only submission copy and pass, including rebuilt/reference binary comparison; see `FDROID_SUBMISSION_CHECKS.txt`. There are no external source repositories needing srclibs/submodules or native ABI splits. The APK is small and has no native libraries.

On 2026-09-30, public fdroiddata exact metadata lookup for `app.cliphistory.yml` returns HTTP 404; app-ID MR search returns zero. The browser is signed out of GitLab. No authenticated public fork, actual fork pipeline or inclusion MR exists from this work. Account sign-in is required to continue [the prepared submission](../F_DROID_SUBMISSION.md). F-Droid maintainers decide acceptance; the local recipe result is not an inclusion decision or a main-server pipeline result.

Additional fdroiddata MR search by ClipHistory, fdroiddata issue search by application ID and RFP issue search by ClipHistory each returned zero matches. These are scoped searches, not proof that no differently named request exists; repeat them immediately before a delayed submission.

The signed update is installed on the physical Pixel over production 1.1 with the same UID, and the installed base APK has the exact signed digest above. Production clipboard/history text was not read, cleared, seeded or exported. Disposable validation packages and their tile were removed. Open ClipHistory and tap Connect once to complete the new setup; no production recording Start/Stop/copy action was automated. The isolated variant supplies the detailed physical timing evidence, while signed upgrade/migration behavior is checked with synthetic emulator fixtures.

## Bounded engineering assessment

Scores are judgment based on the evidence above, not certification. The rubric penalizes unmeasured coverage and unfinished external publication.

| Overall dimension | Weight | Score / 100 | Evidence and limitation |
|---|---|---|---|
| Functionality | 25% | 90 | Policies, migration, Settings, recovery, offline and quick-copy checks pass; platform stop/reboot limits remain |
| UI and UX | 25% | 84 | Calm native controls, reachable actions, 200% layout and stable tile; Android's launch transition remains |
| Efficiency | 15% | 88 | Bounded history, fast cached reads, measured warm loads and idle CPU; maximum-size cold reads are slower |
| Reliability | 15% | 86 | Failure paths, stop semantics and signed upgrades; internal APIs, beta OS and limited OEM coverage |
| Privacy | 10% | 89 | Local private/no-backup data, secure windows, no network/overlay; saved text is not app-encrypted |
| Accessibility | 10% | 82 | Labels, focus, targets and large fonts; exhaustive spoken TalkBack traversal unmeasured |
| **Weighted overall** | **100%** | **86.70, rounded 87** | |

| Publication preparation | Weight | Score / 100 | Evidence and limitation |
|---|---|---|---|
| Source/licensing | 20% | 98 | Public tagged source, MIT and full free-software notices |
| Dependencies/privacy | 20% | 94 | No proprietary runtime SDK or network/ads; documented privileged shell helper |
| Reproduction/signing | 20% | 96 | Clean cross-OS equality, original-key reproduction and actual recipe comparison |
| Metadata/store materials | 15% | 96 | Pinned recipe, tags, descriptions, screenshots, icon, checks and changelog |
| Runtime validation | 15% | 86 | Broad named device/flow coverage; multi-day/OEM, kernel wakeup and full audio audit unmeasured |
| External submission/approval | 10% | 0 | Account sign-in, fork CI, inclusion MR and acceptance unfinished |
| **Weighted preparation** | **100%** | **84.90, rounded 85** | |

No zero latency, zero system animation, universal success, complete TalkBack audio traversal, actual kernel wakeup count, multi-day battery soak or every-OEM/future-Android compatibility is claimed. Non-root Shizuku needs restarting after reboot. Unknown owner-stop descriptions fail closed. Migration to V2 makes downgrading to 1.1/1.2 unsupported. These limits remain visible in documentation and applicable user guidance.
