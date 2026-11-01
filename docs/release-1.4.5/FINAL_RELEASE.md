# ClipHistory 1.4.5 release verification

Verified 1 November 2026. Version 1.4.5/code 15 is published on [GitHub](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.5). F-Droid inclusion is a separate maintainer decision; see the [existing submission](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917).

## Exact release

| Item | Verified identity |
| --- | --- |
| Source commit and immutable tag | `329ab008db02a66aae151891b56e2db9d533d9a0`, `v1.4.5` |
| Signed APK, public download and installed Pixel APK | `4f60af8bf952db2d344a7fe939ea8b4f0b8296ee40e733e8859b895e5194bc63` |
| Windows, Linux, local F-Droid and downloaded GitLab CI unsigned APK | `a91688bb661c2b4ba78d81eb711db5f5081cd96eaafdff7d4a51f0f966a78b06` |
| Original signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| APK size | 2,672,759 bytes |

The exact source commit rebuilt successfully on Windows and Linux. Unsigned bytes match. The release passed 80 JVM tests, Android lint with zero errors and 24 disclosed warnings, v3 signature verification, 16 KiB ZIP alignment and `apksigcopier compare --unsigned`. The downloaded public APK has the expected hash and certificate.

The previously tested final candidate and this release contain identical ZIP entries except `META-INF/version-control-info.textproto`, which now records the immutable release commit. Runtime classes, resources and assets are identical. The [candidate qualification](FINAL_QUALIFICATION.md) and [machine-readable matrix](checks.json) retain their original hashes and scopes; they are not relabelled as new tests of the release APK.

## Real-device release check

The exact signed release was installed in place on the Pixel 8 Pro, Android 17, with thedjchi Shizuku 13.7 in shell mode. Its installed APK hash matches the public release. Native Stop recorder and Start recorder replaced the older same-version helper, and the new helper's shell UID 2000 was verified. The production connection test passed.

Observed history count, limit, ten inspected settings and encryption metadata matched before and after. Normal capture was paused during the synthetic probe; the eligible original clipboard and original pause state were restored. No personal history export, history deletion, phone reboot or phone-wide security setting change was used. This metadata comparison does not claim a byte-for-byte export of personal history.

## Confirmed fixes and coverage

- Service discovery uses the running compatible Binder rather than requiring the original Shizuku manager package. Manager and battery shortcuts offer an explicit choice when several known managers are installed.
- Android 11–13 package updates no longer masquerade as deliberate user stops. The fix recognizes only the reproduced installer exit description for the exact app package. Genuine stops and unknown descriptions remain protected.
- Approval guidance covers compatible service managers and their manual authorization fallback. Shevery's access permission still requires user approval.
- Privacy wording distinguishes source-marked sensitive text from unmarked text, and history expiry from Android's separate clipboard.
- Final candidate capture and encryption checks passed on eight emulator targets: API 30, 31, 32, 33, 34, 35, 36.1 and 37. Actual signed 1.4.4 upgrades, preserved synthetic data/settings, Force stop and explicit restart passed on API 30–33. API 37 used Shizako's actual F-Droid CI build, including Settings and the native tile.
- The broader initial-candidate UI, fork, fresh-authorization, lifecycle, boot, Doze and storage tests remain separately identified in [initial qualification](QUALIFICATION.md). The reusable [F-Droid guide](../F_DROID_ANDROID_GUIDE.md) now requires dependency/fork research, exact variants and hashes, real consent, lifecycle and upgrade boundaries, privacy checks, reproducibility and current CI evidence.

## F-Droid

The public-source recipe passed `readmeta`, canonical `rewritemeta`, `lint`, `checkupdates --allow-dirty` in an isolated scratch workspace, and `build --test --scan-binary --no-tarball app.cliphistory:15`. Source and APK scans passed without custom exclusions. F-Droid downloaded the public reference, accepted the original certificate and reproduced its signed APK. Its unsigned hash matches both independent builds above.

The local formatter uses fdroidserver 2.4.5 and ruamel.yaml 0.18.10. The canonical wrapped `Binaries` field retains its serializer-generated space; the resulting trailing-space advisory is disclosed. Initial scratch setup attempts stopped on a different serializer, missing Git initialization and untracked workspace files. Correcting the test environment allowed the complete gate to pass; no app or scanner rule was changed to hide a failure.

The existing MR now pins this release at branch commit `c86f77422e02a5de07ca5446c62d7c58b11541ae`, preserving the maintainer's earlier rebase. The public submitted recipe matches the local file byte for byte, SHA-256 `fab33f004e59f74df4f285127de0292425682828536eaa662ee42fe95cf08d98`. The [Shizako report received a tested-fix reply](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917#note_4136522958).

[Pipeline 3015458811](https://gitlab.com/xizirsaro/fdroiddata/-/pipelines/3015458811) passed all nine jobs for this exact metadata commit at 22:08:36 UTC. Its build log explicitly confirms successful reference comparison and the allowed signer. The downloaded CI unsigned APK also matches the independent builds. Exact job IDs and results are in [release-checks.json](release-checks.json).

The APK job log contains eight informational findings (six declared permissions, signing identity and APK size/no native library) and one minor R8 notice. Minification remains intentionally disabled for the reflectively loaded UserService. An independent run of the submission branch's `check-fastlane.py`, on the exact release source, returned one informational entry for the complete English metadata and eight screenshots, with no warning or error.

The upstream MR's Reports tab says **No scanning jobs configured** for this fork pipeline. Chrome blocked the source-report artifact's CDN download, so its raw JSON was not independently retrieved. The Reports checklist item remains open rather than claiming a complete imported-report review. All actual jobs passed; this report-view limitation is separate from the verified builds and device tests. No browser security control was disabled. Maintainers control acceptance and catalogue publication, neither of which is claimed.

## Remaining limits

HyperOS 3 still needs the reporter's exact firmware and service variant retest. Official Shizuku 13.6 on Android 17 has a reproduced background Binder-delivery limitation after server restart; opening ClipHistory reconnects. The tested Shizako F-Droid build recovers automatically. Fresh Shevery approval on Android 17 may require manual authorization in its manager. These external limitations are not claimed fixed upstream.

Support remains Android 11+, primary profile, Shizuku API 13+ with upstream UserService support and shell UID 2000. Arbitrary forks, root/device-owner modes, work profiles, natural multi-day OEM operation, spoken TalkBack navigation and independent security review are not certified. There is no claim of universal compatibility or absence of all bugs. No security, privacy, identity or deliberate-stop protection was weakened.
