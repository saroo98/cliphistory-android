# Building ClipHistory

## Verification boundary

Current release evidence is in `VERIFICATION.md`. Windows and Linux source builds and isolated physical-device checks are recorded separately. Device coverage does not guarantee compatibility across all Android manufacturers or future OS updates.

## Pinned app toolchain

| Component | Configuration |
|---|---|
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.3.1 |
| Java compiler bytecode target | 17 |
| Gradle runtime JDK | 17 or a supported newer JDK; bootstrap fallback is Temurin 17 |
| Kotlin | AGP 9's built-in Kotlin support; no separate Kotlin Android plugin |
| Android compile / target | API 37 / API 37 |
| Android minimum | API 34 |
| Android Build Tools | 36.0.0, AGP 9.1.1's documented default/minimum |
| Shizuku API and provider | 13.1.5 |
| JUnit | 4.13.2, test-only |

Google documents AGP 9.1.1 support for API 37 and the versions above. Links are in `docs/SOURCES.md`. No NDK, emulator, server or cloud account is needed to compile this app.

## Windows bootstrap

Run `BUILD_WINDOWS.cmd` after fully extracting the archive. It launches Windows PowerShell with a process-local execution-policy option; it does not change the machine's policy or require administrator/root access.

The script:

1. Finds Java through JAVA_HOME, Android Studio's JBR, its own local cache or PATH. With your consent, downloads a Windows x64 Eclipse Temurin JDK 17 when none is available, verifies the SHA-256 checksum from publisher metadata, and extracts it locally.
2. Uses an existing Android SDK from ANDROID_HOME / ANDROID_SDK_ROOT / Android Studio's normal location, or creates `.tools/android-sdk`.
3. Obtains Google's Windows command-line tools when missing and validates the archive size and checksum in Google's HTTPS repository metadata. The bootstrap-tools/JDK patch release can change; app/plugin/dependency versions remain specified above.
4. Presents `sdkmanager --licenses` interactively. It does not pipe automatic acceptance. Then installs API 37, Build Tools 36.0.0 and platform-tools.
5. Creates `local.properties`, an owner-specific RSA signing key and local password files under `.signing/`, only when needed.
6. Uses the official Gradle wrapper to download Gradle 9.3.1 with its pinned SHA-256 checksum.
7. Runs `:app:testDebugUnitTest`, `:app:lintRelease`, `:app:assembleRelease` with `-PunsignedRelease`, then signs through `apksigner --alignment-preserved true` and checks the signature, alignment, permissions and target/debuggable status.
8. Only after those commands succeed, copies the signed output to `ClipHistory-1.3.0.apk` and writes its SHA-256 file.

Build output is recorded in `build-windows.log`. The source, clipboard data and key are not uploaded by our scripts. Ordinary build tools connect to their repositories to download dependencies. If downloads are blocked on your network, the script stops rather than switching to an untrusted APK mirror.

The generated signing key is not escrowed or recoverable through this chat. Keep `.signing/cliphistory.jks` and `.signing/password.txt` privately backed up. They are deliberately excluded from source control and the delivered ZIP. A different key cannot install a normal update over the existing package.

If Windows SmartScreen, organisational policy or SDK licensing prevents the bootstrap, use a current Android Studio installation to open the project, install the specified SDK/toolchain, and build it normally. Do not disable platform-wide security controls to run the script.

## Gradle launchers

The official Gradle 9.3.1 wrapper is checked in, including its generated JAR and scripts. Its distribution is pinned by SHA-256 in `gradle/wrapper/gradle-wrapper.properties`. The optional `tools/gradle.*` text launchers remain available for older workflows; the Windows build uses the official wrapper.

## Linux / macOS with an installed SDK

Install Java 17+, curl, unzip and the Android SDK using the official tools, review the SDK licences, then install `platforms;android-37.0` and `build-tools;36.0.0`. Set ANDROID_HOME or create `local.properties` with `sdk.dir`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. It is debug-signed by the Android tooling and is intended for development. For daily use, create a private release key and use the release build/signing setup described in the Gradle file; never share a debug key as a production signing identity.

## After compilation

Actual installation and device acceptance tests are separate from compilation. Follow `DEVICE_TESTS.md`. A green Gradle result does not establish that Shizuku's shell process can read/write the passed descriptors or access your exact Android 17 clipboard implementation.

## Unsigned release and F-Droid

```sh
./gradlew --no-daemon -PunsignedRelease :app:assembleRelease
```

The output is `app/build/outputs/apk/release/app-release-unsigned.apk`. This option explicitly ignores local personal signing files. Clean public checkouts are unsigned by default. F-Droid builds from `fdroid/metadata/app.cliphistory.yml`; no keystore or password is required. The release uses upstream signature verification through `Binaries`, to preserve the installed signing identity when F-Droid can reproduce the APK.

For an exact reproducible binary, use the release commit recorded beside it in `docs/release-1.3/RELEASE.md`. AGP embeds the Git revision in the APK. An extracted source archive has no Git revision, so its APK is not expected to have the same digest. Do not substitute a later documentation commit or dirty working tree for the recorded release revision.

Sign the unsigned output separately using the private existing key and Build Tools 36.0.0 `apksigner sign --alignment-preserved true`. Supply passwords through environment variable names, never literal command arguments. The alignment option avoids a [known signature-copying incompatibility](https://github.com/obfusk/apksigcopier#what-about-signatures-made-by-apksigner-from-build-tools--3500-rc1). Version 1.2.0 was reproduced from Windows and Linux clones, then checked with apksigcopier 1.1.1 and signature verification. See `docs/release-1.2/RELEASE.md` for digests.

## Isolated physical validation

```sh
./gradlew -PvalidationBuild :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

This creates `app.cliphistory.validation`, visibly labelled ClipHistory validation, alongside the normal app. Start Shizuku and grant this separate app access. Never seed personal production history for tests. Modes `ui`, `regression`, `layout`, `capacity`, `live`, `settings`, `recovery`, `onboarding`, `tile` and `tile-performance` are described in `DEVICE_TESTS.md`. Build without `validationBuild` for the normal application. Test-APK code and fixture entry points are not included in the release.
