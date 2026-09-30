# Start here

**APK: `ClipHistory-1.3.0.apk`. See `VERIFICATION.md` for current qualification status.**

Use the signed release from https://github.com/saroo98/cliphistory-android/releases. See `VERIFICATION.md` for current build and device evidence. The source archive includes no private signing key.

## On your Windows computer

1. Download and **extract the whole ZIP**. Do not run the script inside Windows' ZIP preview. A short local folder such as `C:\Users\YourName\ClipHistory` is suitable.
2. Double-click **`BUILD_WINDOWS.cmd`**. It asks before downloading missing build tools. Read and respond to the Android SDK licence prompts. It does not need to root or access your phone, and it does not upload the source.
3. A successful run creates **`ClipHistory-1.3.0.apk`** and its checksum. A failed run leaves an error and `build-windows.log`; do not treat an old APK as a newly successful build.

The first build needs an internet connection and may download substantial development tools. Subsequent builds can reuse them. The release uses the pinned toolchain through the official Gradle wrapper; build and release checks are recorded separately.

## On your Pixel — after the build succeeds

Open the generated APK on your phone and use Android's installation prompts. Start Shizuku, open **ClipHistory**, tap **Connect**, and allow the Shizuku request.

Open **⋮ → Connection test**. It temporarily replaces the current Android clipboard with harmless test text. It does not insert that test into your saved history. Check **⋮ → Diagnostics** for the result.

Use **⋮ → Add Quick Settings tile** to add **Clipboard**. Keep **Gboard** selected. Copy normal text in other apps; open ClipHistory from the icon or tile to retrieve it. Tap a saved item to copy it and return, then paste normally.

Tap the eye button, or hold an entry and choose **View full text** to read it. Use **⋮ → Appearance** for System, Light, or Dark. Turn off **⋮ → Copy behavior → Return after copying** to stay in the app after copying.

Open **Settings** for duplicate handling, recovery, reboot resumption, explicit Start/Stop, optional battery guidance, floating or full-history tile mode, screen privacy, motion and welcome options.

After reboot, unlock and start Shizuku again. Enabled recovery attempts reconnection once Shizuku is available and Android permits it. Otherwise reopen ClipHistory and reconnect. Copies made before reconnection cannot be recovered. The **Always-on guide** explains the steps.

Screenshots and screen recording are blocked by default. Enable **Allow screenshots** when desired; Recents hiding is independent. Version 1.3 migrates saved history to v2, so downgrading to older releases is unsupported. Update with the same signer without uninstalling to preserve history.

## Keep these files

Keep the project and especially the hidden **`.signing`** folder created on your PC. Its key and password are needed for future app upgrades without uninstalling. Do not share that folder or password.

For a build failure, the useful file is **`build-windows.log`**. For a phone integration issue, use **⋮ → Diagnostics → Copy report**. Do not send clipboard contents, private signing files or passwords.
