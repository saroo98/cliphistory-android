# ClipHistory 1.4.7 release note

Version **1.4.7 / code 17**. Candidate checked on 8 November 2026: `release/1.4.7` at `0362040` plus the version bump, changelog and docs in this commit. The final release APK is rebuilt from the tagged commit; compared with the candidate, only `META-INF/version-control-info.textproto` changes, because it records the commit. Final hashes are recorded after tagging.

## Final release

Published on [GitHub](https://github.com/saroo98/cliphistory-android/releases/tag/v1.4.7) on 8 November 2026.

| Item | Verified identity |
| --- | --- |
| Tag and source commit | `v1.4.7` → `fd2c3c8b2a9d6cd10ac77096bf6258fba54ed8b6` |
| Signed APK: release asset, public download and installed Pixel APK | `df21327e460d8e6ee083f310109833ba8bdd93af17c0b612f65e122ac8caaa7d`, 2,693,239 bytes |
| Unsigned APK, Windows (JBR 21) and Linux (OpenJDK 17) clean builds of the tag | `755ef7b4afbb33ae2252655362292db7a0ed64bf44954585855171db6313815c` |
| Signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |

The tagged commit rebuilt byte-identically on Windows and Linux, `apksigcopier compare --unsigned` matches the signed APK, and it differs from the checked candidate below only in `META-INF/version-control-info.textproto`. Unit tests 100/100, lint 0 errors. The Pixel 8 Pro has the exact release installed in place over the candidate, with its data kept; it launches without a crash.

## What changed

- **Rooted phones without manual steps** (`3353fcc`). Android lets ClipHistory read the clipboard only as the shell user, never as root. The [Sui fork with Shell mode](https://github.com/XiaoTong6666/Sui) can give one app Shell access while other apps keep root, and it starts with the phone. ClipHistory now tells Sui users to choose **Always (Shell)**, explains how to switch if root or Deny was chosen, opens Setup help at a new "Rooted phone" answer, and continues the Connect when Sui restarts the app to apply the choice (only while that Connect waits on Sui; any other Force stop still stops recording). The recorder itself is unchanged, and a service running as root is still refused.
- **More reliable device tests** (`41e33fb`, `0362040`): service apps are downloaded once per run with retries, and a frozen launcher on the test emulators is detected and restarted.

## Checks on the candidate

| Check | Result |
| --- | --- |
| JVM unit tests | 100/100 (7 new) |
| Release lint | 0 errors; the same 24 warnings as 1.4.6, none new |
| Source audit | 75/75 |
| Reproducible build | Windows (JBR 21) and Linux (OpenJDK 17) unsigned APKs are byte-identical: SHA-256 `011634adc5be15ed6d138a8ceb2b88b87f99bd8bfd316943dcdb7dfb1bbe4c90`. `apksigcopier compare --unsigned` matches the signed candidate. |
| Signed candidate | SHA-256 `cf08723d5c1c5dc457e27c69802fd2c535ae41fccaaf030ea88c9a221e41fc7e`, 2,693,239 bytes; original certificate `ef13472a…5459c82`; v3 signature; 16 KiB aligned; target 37, minimum 30; not debuggable; the same six permissions |
| GitHub CI | Unit tests, lint and APKs green (run 42470588064). Full emulator matrix, Android 11–16 × Shizuku, Shizako and root refusal: 18/18 blocking jobs green (run 42496350345). Android 17 jobs stay non-blocking: the emulator image aborts at boot on GitHub's runners. |
| Signed upgrade from the public 1.4.6 | Android 13 emulator, official Shizuku in shell mode, real prompt, synthetic copies: same app UID and signer; history and every setting kept; the recorder restarted by itself as UID 2000 four seconds after the update, before the app was opened; a copy made before opening it was saved; no "Recorder stopped by you"; Force stop still stops recording and Start recorder resumes it. [Report](upgrade-1.4.6-to-1.4.7-api33.json) |
| Rooted phone with Sui | Android 16 emulator, Magisk 31.1 with Zygisk, Sui 13.5.4.3: the recorder runs as UID 2000 in `u:r:shell:s0` on every thread, with no capabilities; all device-test suites pass; the clipboard is never cleared; choosing Shell, or switching from root or Deny, reaches Listening with no extra tap; recording resumes by itself after reboots with the app closed. |
| Launch time (Android 13 emulator, 10 runs each) | Cold p50 / p95: 1.4.6 473 / 501 ms, **1.4.7 478 / 500 ms**, 1.4.6 again 482 / 522 ms. Warm p50: about 50 ms for both. No regression. [Report](launch-compare-api33.json) |
| Visual and accessibility pass | The new cards and the "Rooted phone" answer in light, dark, 200% text and right-to-left layout: every control is labelled and at least 48 dp; buttons stack at large text; the layout mirrors right to left. [Report](visual-accessibility-api36-sui.json) |
| Pixel 8 Pro, Android 17 | The signed candidate was installed in place over 1.4.6: version 17, installed hash matches, data kept (original install date unchanged), launches without a crash. Recording was not observed because the phone's Shizuku server was not running. |

## Not covered

- Sui on KernelSU or APatch, on real rooted phones, and on Android versions other than 16.
- Spoken TalkBack navigation (labels and touch sizes were checked automatically).
- The Firebase Test Lab rerun on the realme RMX3231 for the Android 11 long-press fix from 1.4.6.
- Real Samsung and Xiaomi phones through Android Device Streaming.

Support stays Android 11+, the primary profile, and a Shizuku API 13+ service in shell mode. There is no claim of universal device compatibility.
