# ClipHistory review follow-up, 31 October 2026

## Scope and completion criteria

Preserve the unpublished 1.4.0/code 10 encryption and sensitive-text implementation. Investigate every new F-Droid review item, fix confirmed app defects, test on disposable emulators, and provide a reusable F-Droid publication guide. Do not use the physical phone. Do not publish, push, or post a review reply as part of local qualification.

Completion requires fresh build, unit/lint/source checks, real shell-mode Shizuku capture on the available supported Android matrix, sensitive retention/encryption checks, preservation of existing encrypted history during an APK upgrade, release signature/permission checks, and an evidence table with explicit failures or coverage limits. Emulator success does not certify HyperOS, Samsung firmware, hardware Keystore security, or the absence of all bugs.

## Live implementation steps

1. **Completed:** verified all 14 current MR comments, source baseline, and emulator/tool inventory. Baseline unit tests and validation APKs build successfully. API-36 emulator boots.
2. **Completed:** reproduced missing startup diagnostic context, delayed unmarked sensitive recopy, the slow-mirror publication window, the stale sensitive-settings race, and unknown-to-known boot identity recovery failure. Focused fixes and targeted red/green regressions are complete.
3. **Completed:** the same 1.4.1 validation APK passes 18 suites on Android 14, 15, 16 and 17. Additional tile, navigation, large-text and lifecycle checks pass. Legacy and encrypted upgrades preserve exact history; the signed production-ID APK also upgrades successfully. Android 17 reconnects after reboot once non-root Shizuku is restarted.
4. **Completed locally:** wrote the reusable F-Droid guide and ran upstream metadata parsing, canonical-format checking, lint, tagged-update discovery, source scanning and APK scanning. The new public-source, Linux recipe/reproduction and maintainer CI gates remain explicitly pending.
5. **Completed locally:** signed and verified the code-11 update with the original certificate, reviewed the final changes, and delivered the APK, [qualification report](RELEASE.md), [machine-readable results](checks.json), selected native visuals and [reusable guide](../F_DROID_ANDROID_GUIDE.md). The physical phone and remote publication state remain unchanged. HyperOS verification and the new public-source/Linux/maintainer gates remain open as recorded in the report.

## Verified starting state

- Git HEAD: `6009e25f548a5f1a45d6cf129d288056e76d5b8f`; existing uncommitted 1.4 implementation is preserved.
- App: `app.cliphistory`, 1.4.0/code 10, min SDK 34, target/compile SDK 37, Shizuku client API/provider 13.1.5.
- Submitted F-Droid version: 1.3.4/code 8. Earlier submission CI does not qualify the unpublished update.
- Reviewers requested actual capture evidence, clearer separate Shizuku installation guidance, and a foreground-service explanation.
- Ariel reported Shizuku 13.6.0 helper startup failure on Android 16 / HyperOS 3, before `ClipboardUserService(Context)` executes. No full device log or exact model has yet been supplied in the inspected review.
- Upstream Shizuku `UserService.create()` calls `LoadedApk.makeApplication(true, null)` before loading the app's helper class. The `true` argument requests the default framework Application. Changing our custom Application cannot repair a failure at that earlier boundary.
- Related upstream issue: <https://github.com/RikkaApps/Shizuku/issues/1198>. A matching failure is not proof that the reviewer's device has the identical root cause.
- No ADB devices were attached during the initial inventory. Every subsequent device command must specify an observed `emulator-*` serial.

## Decisions and limits

- Fix app behavior within the existing native architecture. Do not add another privileged launcher, silently change Shizuku distributions, relax UID/file checks, or introduce a replacement clipboard backend to conceal an external startup failure.
- Use harmless synthetic test contents only. Preserve previous evidence under `docs/release-1.4/` and the previously delivered 1.4.0 APK.
- Qualify Android 14/API 34, 15/API 35, 16/API 36, and 17/API 37 where official emulator images are available. Android 13 and older remain unsupported by the existing min SDK.
- The exact HyperOS environment is unavailable on a stock emulator. Record it as unresolved if it cannot be independently reproduced and verified.
- Use 1.4.1/code 11 for the corrected APK so it upgrades the previously installed 1.4.0/code 10 and replaces old helper code. Keep the earlier APK intact.
- Signing/reproducibility, source publication, the upstream release tag, updated submitted metadata, maintainer review, and catalogue publication are distinct gates. Never represent a local dirty-tree build as a reproducible published release.
