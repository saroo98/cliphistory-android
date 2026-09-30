# ClipHistory 1.1.0 UI delivery

Implemented from the written 31-scene design specification. The input was a written image-generation prompt, not 31 supplied reference images. This is a native Android implementation of that specification, rather than a claim of pixel matching unseen images.

## Changes

- Quiet mineral light and dark colors, readable text rows, shared spacing, rounded search and sheets, restrained native press feedback.
- Full-text read-only preview from the entry action sheet. A normal tap still copies the complete text and returns immediately.
- Persisted System / Light / Dark appearance and Return after copying preference, enabled by default. Disable it to copy and stay, with brief feedback.
- Reworked search, recorder setup and recovery notices, offline history, validated capacity, confirmations, diagnostics and help.
- Adaptive launcher icon, monochrome themed icon, and Clipboard Quick Settings entry.
- Native bottom-sheet transitions. No decorative delay on copying, no new production dependency, accounts, cloud, ads, or network permission.
- Fixed launch-time system-bar initialization, recorder sheet refresh duplication, and loss of loaded pages during ordinary history refresh.

The history storage format, maximum text size, Shizuku authorization checks, single-writer model, privacy flags and existing capture implementation remain in place. Version code is 2. The APK uses the same signing certificate as the earlier 1.0.0 delivery.

## Design coverage

| Scene | Implementation |
|---|---|
| S01–S02 | HistoryHome and shared light/dark resource palettes |
| S03–S04 | Debounced local search, clear control, match highlighting and distinct no-results state |
| S05 | Loading state without a fabricated zero count |
| S06 | Connected empty-history presentation |
| S07–S08 | Missing/stopped Shizuku, permission-required and denied states |
| S09–S12 | Connecting, listening, recording, paused and unavailable states derived from client/daemon information |
| S13 | Nine-action overflow with unavailable mutations disabled offline |
| S14 | Entry sheet: Copy, View full text, Delete |
| S15–S16 | Capacity input, 20–500 validation, older-entry deletion warning |
| S17 | Clear-history confirmation before the daemon command |
| S18 | Connection-test confirmation before changing the Android clipboard |
| S19 | Live test result sheet, including waiting, passed and failure states |
| S20 | Readable diagnostics, expandable advanced details and clipboard-free report |
| S21 | Setup, privacy, restart and scope help |
| S22–S23 | Tile explanation and Android-owned tile request; system panel remains Android-owned |
| S24 | Full text, timestamp, selectable text, Copy and Delete |
| S25 | Persisted System / Light / Dark appearance |
| S26–S27 | Persisted copy behavior and successful-copy feedback |
| S28 | History read errors, failed copy, missing entry, offline mutation explanation and action errors |
| S29 | Shared Ui helper, resource tokens and accessible native controls, not a gallery page |
| S30 | Native ripple and sheet motion, not an extra storyboard page; no measured frame-rate claim |
| S31 | Adaptive launcher foreground/background/monochrome and tile vector |

System permission prompts, the keyboard and Quick Settings remain real system or Shizuku surfaces. No replicas or fake success screens are bundled. Recovery scenes appear when their conditions occur; there are not 31 navigation destinations.

## Validation boundary

See ../VERIFICATION.md for current build and emulator evidence. The instrumentation uses 73 synthetic records in a disposable API 36 emulator. It checks real offline storage reads, search, pagination, preview, clipboard restoration, copy-and-stay, copy-and-return, persisted appearance, capacity validation, error rendering and recorder-sheet updates. Connected recorder scenes are presentation fixtures, not proof of an active Shizuku recorder.

Screenshots under ../reports/screenshots are rendered app Views captured by the separate debug test APK. They contain synthetic text, omit system-owned overlays and do not disable release screenshot protection. Large-font captures use Android font scale 1.5. These checks are not a full TalkBack, physical-device, all-size or performance certification.

## Installing the update

Install ClipHistory-1.1.0.apk over the earlier signed delivery. Do not uninstall first if you want to keep history. Android will reject an upgrade if an existing installation was signed by a different key. Start Shizuku again as needed, open ClipHistory, and use the connection test. Physical phone acceptance remains in ../DEVICE_TESTS.md.

The private signing key is retained locally and excluded from the source archive. Rebuilding elsewhere with a newly generated key will produce a different signing identity.
