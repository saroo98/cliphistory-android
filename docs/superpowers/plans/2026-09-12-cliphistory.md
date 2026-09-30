# ClipHistory Implementation Plan

> **For agentic workers:** Inline execution in this chat; execute task-by-task with test and review gates.

**Goal:** Implement the approved local-only Android clipboard app and an auditable build/test handover.
**Architecture:** Privileged listener plus single-writer two-slot storage, app-UID-guarded AIDL, normal app UI and Quick Settings tile.
**Tech Stack:** Native Kotlin/Android views, Shizuku API 13.1.5, Android API 37; no server.
**Spec:** docs/superpowers/specs/2026-09-12-cliphistory-design.md

## Global constraints
- API 37 target and compile; API 34 minimum.
- Keep Gboard; no INTERNET permission, accessibility, overlays or shared storage.
- Default 100; configured history range 20–500; text-only; 64 KiB per entry.
- Report build and device validation honestly; do not substitute stub tests for Android tests.

## Task 1: Core storage and history
Files: core/Model.kt, SnapshotCodec.kt, HistoryRepository.kt; app/src/test/java/app/cliphistory/CoreSuite.kt.
Tests: rolling limit, exact Unicode roundtrip, duplicates, sensitive/oversize rejection, restart persistence, truncated/corrupt slots, both slots corrupt, failed writes, shrink and clear mirrored, monotonic IDs, future format rejected. Run tools/test-core.sh, first observe failure then implement and re-run. Interface: Slot.read()/writeAndSync(ByteArray), Snapshot(generation,limit,nextId,paused,entries), HistoryRepository constructor/capture()/delete()/clear()/setLimit()/setPaused().

## Task 2: Privileged transport
Files: AIDL interfaces; daemon/FdAccess.kt, core/FramedSlot.kt, PlatformClipboardBridge.kt, ClipboardUserService.kt. Verify signature-routing cases with real pure-core tests. Register actual framework clipboard listener via runtime reflection, read with cleared Binder identity, persist on a bounded single worker, UID-check every public method, detach without shutting down on UI death. Startup verifies fd read/write and listener registration. Include bounded paging and payload limits.

## Task 3: UI, connection and tile
Files: client/DaemonClient.kt, ClipApplication.kt, ui/MainActivity.kt, ui/HistoryAdapter.kt, ui/ClipboardTileService.kt, manifest/resources. Connect asynchronously, show distinct states, handle death/denial/timeouts, show offline history, require confirmation for clear/self-test, keep IPC off UI thread, update via callback instead of clipboard polling. Add an opt-in diagnostic probe using ProbeTracker: recognise the real callback, verify unchanged history through disk I/O, and never append known test clips to history. Tile opens history using immutable PendingIntent and unlockAndRun.

## Task 4: Build, handover and review
Files: Gradle project, build.ps1/bootstrap scripts, README, DEVICE_TESTS.md, SECURITY.md, VERIFICATION.md. Pin tooling; Windows script checks Java/SDK, offers SDK installation/license confirmation, obtains verified Gradle distribution, generates owner-local release key, runs unit tests + lint + release build and copies APK. No keystore included in source ZIP. Execute core tests and XML/security/source audits locally. Attempt real Android build; record unmet environmental gates explicitly. Package source ZIP, test log and exact checksums. No unfinished feature markers or fabricated APK.

## Execution status
Core and app source implemented; 50 host-JVM cases pass. Source review and packaging performed. Android compilation/lint/signing, PowerShell execution and device acceptance remain NOT RUN because the required environment/downloads are unavailable. See VERIFICATION.md; these are not marked complete.
