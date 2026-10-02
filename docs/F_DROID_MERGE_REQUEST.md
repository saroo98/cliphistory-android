# New app: ClipHistory

Local searchable plain-text clipboard history through an explicitly authorized non-root Shizuku shell recorder. Unique exact-text history is default; consecutive-only handling is selectable. Saved history remains readable offline. Native controls include View, single-deletion Undo, pause, limits, grouped Settings, appearance, screen privacy and a temporary three-entry quick-copy tile panel.

## Source/build

- MIT source: https://github.com/saroo98/cliphistory-android
- Contact: https://github.com/saroo98/cliphistory-android/issues
- Upstream owner requests inclusion.
- app.cliphistory, 1.3.3/code 7, Android 14+.
- AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0; no NDK.
- Fastlane descriptions, changelog 7, icon and synthetic native screenshots are upstream.
- Only metadata/app.cliphistory.yml belongs in this MR: latest-only LF YAML, full source hash, tagged updates and original-key reproducibility. No external source repositories, native ABI splits, scanner exemptions or disabled builds.

Exact qualification and pipeline status are in [F_DROID_PUBLICATION.md](F_DROID_PUBLICATION.md). Select the current App inclusion template and check only observed items. Local Windows/Linux reproduction is separate from GitLab CI.

## Runtime/privacy

Requires Shizuku API 13+ as non-root shell UID 2000 on the primary profile; it needs starting again after reboot. Recovery respects explicit Stop, Force stop and Task Manager Stop. Battery settings stay optional.

No Internet permission, ads, analytics, account, cloud, keyboard replacement or overlay permission. Private history is excluded from backup. Screenshots default to blocked, independently of Recents privacy. Sensitive-marked clips, unsupported payloads and text over 64 KiB are skipped. Unmarked copied text may contain secrets.

Internal clipboard APIs remain a compatibility risk. Current UI checks cover an API 36 emulator and signed Pixel API 37 upgrade/launch/Settings inspection. Historical API 34/35 checks are identified separately. Android controls shade collapse. Zero latency, every-OEM compatibility, exhaustive spoken TalkBack and multi-day battery measurements are not claimed.

Android lint: zero errors, 15 disclosed warnings (one AGP advisory, one durable preference commit, one obsolete SDK condition, one plural candidate, four private-API calls, seven unused strings). No suppressions. F-Droid source/APK scanner results are separate.
