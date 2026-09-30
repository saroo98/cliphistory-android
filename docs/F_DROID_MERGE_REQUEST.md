# New app: ClipHistory

ClipHistory keeps a local, searchable plain-text clipboard history through an explicitly authorized Shizuku shell recorder. Exact-text duplicate handling keeps one entry per text; consecutive-only handling is selectable. Saved history remains available offline. Native controls include full-text preview, pause, limits, appearance, screenshot/Recents privacy and a temporary three-entry quick-copy tile panel.

## Source and build

- Source: https://github.com/saroo98/cliphistory-android
- Contact: https://github.com/saroo98/cliphistory-android/issues
- Licence: MIT; upstream owner welcomes F-Droid inclusion.
- App: `app.cliphistory`, version 1.3.0/code 4, Android 14+.
- Recipe: upstream `fdroid/submission/app.cliphistory.yml`, copied into fdroiddata `metadata/app.cliphistory.yml`.
- Toolchain: AGP 9.1.1, Gradle 9.3.1, JDK 17, platform `android-37.0`, Build Tools 36.0.0; no NDK.
- Fastlane includes changelog `4.txt`, original launcher icon and synthetic native screenshots.

The upstream historical metadata retains 1.2. The new-app submission copy includes only the latest 1.3 build, as required by the current App inclusion template. Its block pins `a72134900c574a354a366b24c979f7903b6ace35`, tagged `v1.3.0`. The public [release](https://github.com/saroo98/cliphistory-android/releases/tag/v1.3.0) provides the original-key reference APK, SHA-256 `13fa9570bb2c7e4df7be34b0e4dcb2a92c278113d91ec6f7f43a1061dba1d070`. Tagged updates and original signer `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` are preserved. No scan exemptions or lint baseline. Full free-software dependency notices are bundled; no proprietary runtime SDK or native library.

## Evidence and remaining checklist

Fresh runtime evidence and exact-source reproduction are in upstream `docs/release-1.3/RELEASE.md`. F-Droid server 2.4.5 metadata checks, tagged-update discovery and actual local recipe with source/APK scans pass, including comparison against the downloaded public original-key APK. A local recipe does not establish the fork's GitLab CI result. GitLab account access is currently unavailable.

- [x] Matching public release and reference binary verified.
- [x] Exact 1.3 recipe, scans and signature reproduction verified.
- [x] Current app-ID and existing-request checks performed on 2026-09-30: exact metadata HTTP 404; app-ID MR search returned zero. Repeat immediately before submission if delayed.
- [ ] Public fdroiddata fork and unprotected branch exist.
- [ ] Actual GitLab pipeline passes.

Select GitLab's current App inclusion template and reconcile this description with its checklist before submission.

The current template was read on 2026-09-30. The submission recipe is latest-only, LF YAML with a full source hash, AuthorName, tracker, tagged updates and original-key reproducibility. Fastlane en-US descriptions, icon, changelog and synthetic images remain upstream. There are no external source repositories or native libraries requiring srclibs/submodules or ABI splits. Additional fdroiddata MR search by app name and fdroiddata/RFP issue searches returned zero scoped matches. No pipeline result is implied. Explain the six disclosed Android lint warnings in the Reports checklist. If GitLab requests paid CI or sensitive financial/phone verification, leave the pipeline issue for maintainers rather than paying or changing project access.

## Runtime and privacy

Requires separate Shizuku API 13+ in non-root shell UID 2000 on the primary profile. Shizuku needs restarting after reboot. Optional recovery respects explicit Stop, Android Force stop and Task Manager Stop. Background battery policy remains the user's choice.

No internet permission, ads, analytics, account, cloud service, keyboard replacement or overlay permission. History is private and excluded from backup. Screenshots default to blocked and may be enabled independently of Recents privacy. Sensitive-marked clips, unsupported payloads and text over 64 KiB are skipped; unmarked text can contain secrets.

Internal Android clipboard interfaces remain a compatibility risk. Runtime evidence names API 34/35/36 emulators and a Pixel 8 Pro API 37 beta validation package. Android controls the tile launch animation. Literal zero latency or every-device success is not claimed. Exhaustive TalkBack audio, kernel wakeup counts and multi-day battery testing remain unmeasured. F-Droid inclusion is an external review decision.
