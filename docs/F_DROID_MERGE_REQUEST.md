# New app: ClipHistory

ClipHistory keeps a local, searchable plain-text clipboard history through an explicitly authorized Shizuku shell recorder. Exact-text duplicate handling keeps one entry per text; consecutive-only handling is selectable. Saved history remains available offline. Native controls include full-text preview, pause, limits, appearance, screenshot/Recents privacy and a temporary three-entry quick-copy tile panel.

## Source and build

- Source: https://github.com/saroo98/cliphistory-android
- Contact: https://github.com/saroo98/cliphistory-android/issues
- Licence: MIT; upstream owner welcomes F-Droid inclusion.
- App: `app.cliphistory`, version 1.3.0/code 4, Android 14+.
- Recipe: upstream `fdroid/metadata/app.cliphistory.yml`, copied into fdroiddata `metadata/app.cliphistory.yml`.
- Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform `android-37.0`, Build Tools 36.0.0; no NDK.
- Fastlane includes changelog `4.txt`, original launcher icon and synthetic native screenshots.

The 1.2 build block is retained. The 1.3 block must pin the actual recorded release source commit. Tagged updates, public reference APK and original signer `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` are preserved. No scan exemptions or lint baseline. Full free-software dependency notices are bundled; no proprietary runtime SDK or native library.

## Evidence and remaining checklist

Fresh runtime evidence is in upstream `docs/release-1.3/`. Exact-source reproduction and the actual local recipe result belong in `RELEASE.md` after execution. Historical 1.2 results cannot substitute. A local recipe does not establish the fork's GitLab CI result.

- [ ] Matching public release and reference binary verified.
- [ ] Exact 1.3 recipe, scans and signature reproduction verified.
- [ ] Current app-ID and existing-request checks performed.
- [ ] Public fdroiddata fork and unprotected branch exist.
- [ ] Actual GitLab pipeline passes.

Select GitLab's current App inclusion template and reconcile this description with its checklist before submission.

## Runtime and privacy

Requires separate Shizuku API 13+ in non-root shell UID 2000 on the primary profile. Shizuku needs restarting after reboot. Optional recovery respects explicit Stop, Android Force stop and Task Manager Stop. Background battery policy remains the user's choice.

No internet permission, ads, analytics, account, cloud service, keyboard replacement or overlay permission. History is private and excluded from backup. Screenshots default to blocked and may be enabled independently of Recents privacy. Sensitive-marked clips, unsupported payloads and text over 64 KiB are skipped; unmarked text can contain secrets.

Internal Android clipboard interfaces remain a compatibility risk. Runtime evidence names API 34/35/36 emulators and a Pixel 8 Pro API 37 beta validation package. Android controls the tile launch animation. Literal zero latency or every-device success is not claimed. Exhaustive TalkBack audio, kernel wakeup counts and multi-day battery testing remain unmeasured. F-Droid inclusion is an external review decision.
