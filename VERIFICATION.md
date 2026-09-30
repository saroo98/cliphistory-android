# ClipHistory 1.1.0 verification

Date: 2026-09-13. Windows build, API 37 target, API 34 minimum. This report supersedes historical original-build reports for the redesign.

## Passed

- `:app:testDebugUnitTest`: 50 core cases through one JUnit wrapper, plus six presentation-state tests. No failures or errors.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintRelease`, `:app:assembleRelease`: successful.
- Release lint: 0 errors, 14 warnings. These include four inherited hidden-API reflection warnings, localization/plural suggestions and unused design resources. The build is not warning-free.
- Separate debug instrumentation: 26 assertions on a disposable API 36 emulator at normal text size and Android font scale 1.5. Synthetic history only.
- UI checks: offline history read, filtered search, no matches, clear search, pagination and refresh retention, preview, complete-text clipboard restoration, copy-and-stay and copy-and-return, persisted theme and copy preference, overflow entries, invalid capacity rejection, recorder refresh without duplicate content, and read-error presentation.
- Rendered light/dark screens, detail pages, sheets and larger text were visually inspected. UI screenshot protection remains enabled. Test-only View rendering produces the synthetic screenshots.
- APK signature: valid RSA 3072-bit APK v2 signature, same certificate as the 1.0.0 delivery.
- APK identity: `app.cliphistory`, version name `1.1.0`, version code `2`, non-debuggable.
- Permission audit: only `moe.shizuku.manager.permission.API_V23`. No INTERNET permission.
- Disposable emulator release smoke test: installed the original signed 1.0.0, upgraded in place to 1.1.0 with `install -r`, launched successfully, opened the actual overflow and Appearance sheet through touch input, and selected Dark. No AndroidRuntime errors were logged. This upgrade smoke test used an empty release history, not the user's real data.

The final APK digest is in `ClipHistory-1.1.0.apk.sha256`. Signing certificate SHA-256:

`ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`

## Evidence

- `reports/redesign-final-build.txt`
- `reports/TEST-app.cliphistory.CoreJUnitTest.xml`
- `reports/TEST-app.cliphistory.RecorderStateTest.xml`
- `reports/ui-instrumentation.txt`, `reports/ui-large-font.txt`
- `reports/redesign-lint-release.txt`
- `reports/redesign-signature.txt`, `reports/redesign-permissions.txt`, `reports/redesign-badging.txt`
- `reports/release-upgrade.txt`, `reports/release-runtime.txt`, and `reports/release-*.xml`
- `reports/screenshots/`, `reports/screenshots-large-font/`

The instrumented tests drive native Views and inspect state. They do not certify all physical touch paths or TalkBack operation. The connected/listening/paused/test-pass scenes are explicitly presentation fixtures. They do not prove Shizuku connectivity. Screenshot files omit system-owned UI such as keyboards and status-bar icons.

## Runtime limits

Physical Pixel/Shizuku capture, private descriptor access under phone SELinux, hidden-API behavior on API 37, Quick Settings behavior on the phone, long-term capture with the app process killed, Shizuku restart, reboot and battery behavior still require `DEVICE_TESTS.md`. No personal phone was modified. No universal capture or frame-rate guarantee is made.

## Packaging and signing

The local signing key is retained in the private `.signing` directory and is excluded from the delivery archive. Keep it for compatible updates. The archive also excludes machine paths, caches, downloaded toolchains, emulator logs, debug APKs and test APKs. It includes production and test source and selected evidence.
