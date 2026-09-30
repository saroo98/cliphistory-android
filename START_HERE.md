# Start here

**Installable APK: `ClipHistory-1.1.0.apk`. Corrected source is included.**

The redesigned Android release has been compiled, signed, linted, and permission-checked on Windows. All 50 core checks and six new presentation-state tests pass. Emulator UI tests cover the redesigned interface. Physical Pixel/Shizuku testing is still pending.

## On your Windows computer

1. Download and **extract the whole ZIP**. Do not run the script inside Windows' ZIP preview. A short local folder such as `C:\Users\YourName\ClipHistory` is suitable.
2. Double-click **`BUILD_WINDOWS.cmd`**. It asks before downloading missing build tools. Read and respond to the Android SDK licence prompts. It does not need to root or access your phone, and it does not upload the source.
3. A successful run creates **`ClipHistory-1.1.0.apk`** in the project folder. A failed run leaves an error and `build-windows.log`; do not treat an old APK from an earlier run as a newly successful build.

The first build needs an internet connection and may download substantial development tools. Subsequent builds can reuse them. The Windows bootstrap was verified for the initial build; the 1.1.0 update was built with that installed toolchain using Gradle and audited separately.

## On your Pixel — after the build succeeds

Open the generated APK on your phone and use Android's installation prompts. Start Shizuku, open **ClipHistory**, tap **Connect**, and allow the Shizuku request.

Open **⋮ → Connection test**. It temporarily replaces the current Android clipboard with harmless test text. It does not insert that test into your saved history. Check **⋮ → Diagnostics** for the result.

Use **⋮ → Add Quick Settings tile** to add **Clipboard**. Keep **Gboard** selected. Copy normal text in other apps; open ClipHistory from the icon or tile to retrieve it. Tap a saved item to copy it and return, then paste normally.

Hold an entry and choose **View full text** to read it. Use **⋮ → Appearance** for System, Light, or Dark. Turn off **⋮ → Copy behavior → Return after copying** to stay in the app after copying.

After rebooting, start Shizuku again and reopen ClipHistory. Existing saved items remain; automatic capture cannot run while Shizuku is stopped.

## Keep these files

Keep the project and especially the hidden **`.signing`** folder created on your PC. Its key and password are needed for future app upgrades without uninstalling. Do not share that folder or password.

For a build failure, the useful file is **`build-windows.log`**. For a phone integration issue, use **⋮ → Diagnostics → Copy report**. Do not send clipboard contents, private signing files or passwords.
