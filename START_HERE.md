# Start here

**Current public release: ClipHistory 1.4.7, code 17, Android 11+.** See the [release note](docs/release-1.4.7/RELEASE.md) for the exact APK and checks.

Use the signed release from https://github.com/saroo98/cliphistory-android/releases. See `VERIFICATION.md` for current build and device evidence. The source archive includes no private signing key.

## On your Windows computer

1. Download and **extract the whole ZIP**. Do not run the script inside Windows' ZIP preview. A short local folder such as `C:\Users\YourName\ClipHistory` is suitable.
2. Double-click **`BUILD_WINDOWS.cmd`**. It asks before downloading missing build tools. Read and respond to the Android SDK licence prompts. It does not need to root or access your phone, and it does not upload the source.
3. A successful run creates **`ClipHistory-<version>.apk`** and its checksum, using the version in `app/build.gradle.kts`. A failed run leaves an error and `build-windows.log`; do not treat an old APK as a newly successful build.

The first build needs an internet connection and may download substantial development tools. Subsequent builds can reuse them. The release uses the pinned toolchain through the official Gradle wrapper; build and release checks are recorded separately.

## On your Pixel — after the build succeeds

Open the generated APK on your phone and use Android's installation prompts. Start Shizuku, open **ClipHistory**, tap **Connect**, and allow the Shizuku request.

Open **⋮ → Diagnostics → Run connection test**. It temporarily replaces the current Android clipboard with harmless test text. It does not insert that test into your saved history. Read the result in Diagnostics.

Use **Settings → Quick access → Add Quick Settings tile** to add **Clipboard**. Keep your existing keyboard selected. Copy normal text in other apps; open ClipHistory from the icon or tile to retrieve it. Tap a saved item to copy it and return, then paste normally.

On Android 11–12, the app explains how to drag Clipboard into Quick Settings using Edit. On these versions, blocking screenshots also hides Recents previews; the separate Recents control requires Android 13+.

Tap **View**, or hold an entry and choose **View full text** to read it. Use **Settings → Appearance** for System, Light, or Dark. Turn off **Settings → Return after copying** to stay in the app after copying.

Open **Settings** for duplicate handling, recovery, reboot resumption, explicit Start/Stop, optional battery guidance, floating or full-history tile mode, screen privacy, motion and welcome options.

After upgrading from 1.1 or 1.2, open ClipHistory and tap **Connect** once to complete the new recovery setup. The saved pause and history limit are preserved by migration; an explicit restart after a recorded Stop deliberately resumes recording.

After reboot, unlock and start Shizuku again. Enabled recovery attempts reconnection once Shizuku is available and Android permits it. Otherwise reopen ClipHistory and reconnect. Copies made before reconnection cannot be recovered. The **Always-on guide** explains the steps.

Screenshots and screen recording are blocked by default. Enable **Allow screenshots** when desired. Recents hiding can be controlled separately on Android 13+; on Android 11–12 it follows screenshot blocking. Version 1.3 migrates saved history to v2, so downgrading to older releases is unsupported. Update with the same signer without uninstalling to preserve history.

Version 1.4 encrypts saved history and migrates it to v3. Allow Shizuku during the first conversion so the previous writer can be stopped safely. Do not downgrade afterward. **Settings → Privacy** controls source-marked sensitive text, its automatic deletion and duration. Capture is off by default; encryption is always enabled.

For future Android apps, follow the [complete F-Droid guide](docs/F_DROID_ANDROID_GUIDE.md).

## Keep these files

Keep the project and especially the hidden **`.signing`** folder created on your PC. Its key and password are needed for future app upgrades without uninstalling. Do not share that folder or password.

For a build failure, the useful file is **`build-windows.log`**. For a phone integration issue, use **⋮ → Diagnostics → Copy report**. Do not send clipboard contents, private signing files or passwords.
