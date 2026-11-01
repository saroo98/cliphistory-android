# Xiaomi device tests for the 1.4.3 candidate

Tested on 1 November 2026 with Pcloudy's free real-device trial. Only synthetic clipboard text was used. All device sessions were released. These checks do not cover HyperOS 3, real reboot recovery or long-duration background reliability.

The ClipHistory APK used in both device tests was the signed local 1.4.3 candidate, SHA-256 `b9f95c4b4d9dcf6e29af23dea49bab1cc932b39f7e85cd93f2cc7719230040b5`. On the Xiaomi 12 Pro, the installed APK was pulled and hashed. On the Redmi's first session, the installed APK was hashed on the device. Both matched the local candidate. The comparison session installed the same uploaded APK, but its installed bytes were not separately hashed.

## Xiaomi 12 Pro, Android 14, HyperOS 1

Model `2201122G`; build `Xiaomi/zeus_global/zeus:14/UKQ1.230917.001/V816.0.10.0.ULBMIXM:user/release-keys`. The installed official Shizuku 13.6 APK was pulled and matched SHA-256 `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`.

- The app connected to Shizuku. Its connection test reported a received clipboard event, a passed storage check and active recording.
- Four external synthetic copies in A, B, A, B order produced exactly two entries, B then A.
- A source-marked sensitive copy was not saved with sensitive capture off. After enabling capture and choosing a one-minute expiry, a marked entry appeared and was absent when checked approximately 68 seconds later. Automatic deletion was enabled. The app was force-stopped, reopened and manually reconnected during that interval, so this is not a separate test of expiration while the app remains stopped.
- The ordinary entries survived force-stop and reopening. The app reported that the recorder had been stopped by the user; after tapping Start recorder, another external synthetic copy was saved.

## Redmi Note 11 Pro, Android 11, MIUI 13

Build `Redmi/vida_in/vida:11/RP1A.200720.011/V13.0.6.0.RGDINXM:user/release-keys`. In two Pcloudy sessions, the uploaded official Shizuku 13.6 started and the user granted ClipHistory access, but the recorder did not connect. The second session reproduced the failure without Pcloudy's local ADB tunnel.

The [captured Shizuku log excerpt](redmi-miui13-shizuku-13.6-failure.txt) shows `LoadedApk.makeApplication` throwing a null-pointer exception before `ClipboardUserService` is constructed. The exception and framework line match the Android 11 example in [Shizuku-API pull request 547](https://github.com/RikkaApps/Shizuku-API/pull/547). The device's framework code was not independently inspected, so the precise OEM cause is inferred from the matching trace, not independently proven. The pull request was open when checked and its author says the patched build itself had not been device-verified.

For a comparison on the same phone and app installation, the test replaced Shizuku 13.6 with official Shizuku 13.5.4, started its official ADB script and granted access again. ClipHistory then connected, its connection test passed, and a synthetic text copied from Google search appeared in history. [Screenshot](redmi-miui13-shizuku-13.5.4-capture.png). The local 13.5.4 and 13.6 APKs have the same `CN=Rikka` signing-certificate SHA-256 `268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30`; the installed Shizuku APK was not separately hashed on this Redmi. The older version was used only for this disposable-device comparison. This is not a security assessment or recommendation to downgrade.

## Release implications

The Redmi result identifies an upstream compatibility failure but does not fix it in ClipHistory or prove the reported HyperOS 3 device has the same cause. The candidate is not ready to present as universally working. The public F-Droid request still targets 1.3.4; 1.4.3 still needs reviewed, published source and a matching immutable tag, an updated recipe, independent reproducibility and fresh review. No commit, push, public message or publication was performed during these tests.
