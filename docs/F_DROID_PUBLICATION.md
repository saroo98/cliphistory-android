# F-Droid publication handoff

Source: https://github.com/saroo98/cliphistory-android

The working release is **1.3.0 / code 4**, application ID `app.cliphistory`, MIT, Android 14+. It requires the primary profile and separately installed Shizuku running as non-root shell UID 2000. Non-root Shizuku needs starting after reboot.

The existing public release is 1.2.0. Its local recipe/reproduction evidence is historical. New 1.3 exact-source reproduction, public reference APK and recipe checks are pending and must be recorded after execution. GitLab CI and F-Droid inclusion have not been established.

Provided material: source/build instructions, Fastlane descriptions and changelog 4, synthetic native screenshots, full bundled dependency notices, exact five-permission audit and fresh runtime coverage in `docs/release-1.3/`. The existing 1.2 metadata build block, `Binaries` and allowed signing key are retained. The 1.3 block must pin the actual release source commit once determined.

Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No proprietary runtime SDK, network client, ads or analytics. No custom scan exemption or disabled lint baseline.

Android lint warnings for internal clipboard APIs and durable worker-thread preference commits are disclosed. Unknown platform interfaces fail with a visible error. The app adds no clipboard polling or background watchdog; optional recovery uses a quiet foreground notification with Open and Stop.

F-Droid requires a source recipe and independent build/review, not only an APK. A public fdroiddata fork, pipeline and inclusion merge request are separate steps. Only actually observed results may be checked off. Read `docs/F_DROID_SUBMISSION.md` and the current guide before submission.

Primary references:

- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
