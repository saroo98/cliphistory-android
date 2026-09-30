# Primary technical references checked during implementation

Checked on 2026-09-12. These sources guide the implementation; they do not constitute a test of the delivered app on Android 17. AOSP `main`/`master` can differ from the exact Pixel build, which is why the runtime adapter rejects unknown interfaces and device acceptance remains open.

## Android 17 and the build toolchain

- Android 17 SDK setup, compile/target API 37: https://developer.android.com/about/versions/17/setup-sdk
- AGP 9.1.1 compatibility: API 37, Gradle 9.3.1, Build Tools 36.0.0, JDK 17: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- Built-in Kotlin in AGP 9: https://developer.android.com/build/migrate-to-built-in-kotlin
- sdkmanager package syntax and interactive licence acceptance: https://developer.android.com/tools/sdkmanager
- Quick Settings tile lifecycle, permissions, add-tile prompt and PendingIntent launch: https://developer.android.com/develop/ui/views/quicksettings-tiles
- Clipboard APIs and sensitive-clip flag: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste

## Shizuku

- UserService identity, lifecycle, daemon mode, context limitations and destruction transaction: https://github.com/RikkaApps/Shizuku-API/blob/master/README.md
- Actual client API, getBinder, permission listeners and UserServiceArgs: https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java
- How Shizuku constructs the service with an application context: https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserService.java
- Provider manifest: https://github.com/RikkaApps/Shizuku-API/blob/master/provider/src/main/AndroidManifest.xml
- Server permission/lifecycle handling: https://github.com/RikkaApps/Shizuku/blob/master/server/src/main/java/rikka/shizuku/server/ShizukuService.java
- Non-root setup and reboot limitation: https://shizuku.rikka.app/guide/setup/

## Privilege and storage reasoning

- Shell package manifest including background clipboard permission: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/Shell/AndroidManifest.xml
- Android clipboard service access policy: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/clipboard/ClipboardService.java
- IClipboard platform interface: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/IClipboard.aidl
- Clipboard callback interface: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/IOnPrimaryClipChangedListener.aidl
- Shell SELinux domain: https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/shell.te
- App-domain existing-FD permissions (read/write/getattr/map, not arbitrary file-attribute changes): https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app.te

The two-descriptor, non-truncating framing design is an implementation choice based on these rules. It is not a published guarantee that every OEM/Android version accepts the same descriptor operations.
