# ClipHistory 1.2 release

The owner authorised fixing the audit findings, creating a public source repository, and delivering an APK prepared for F-Droid. The release keeps the existing native Android interface, private storage format and signing identity.

## Completion criteria

- Saved history stays reachable in landscape, small windows, 200% text and keyboard layouts.
- Rotation and appearance changes preserve loaded history and the visible entry.
- Old asynchronous reads cannot replace newer navigation; feedback expires across backgrounding.
- Read and command failures have accurate, current diagnostics and remain distinct from missing entries.
- Offline paging and copying reuse validated snapshots; obsolete search work is skipped.
- Current recorder health remains separate from historical overload counts.
- Setup instructions, accessibility actions, bottom sheets and dependency notices are complete.
- Regression tests, actual rendered UI and real Shizuku capture pass using synthetic data.
- Linux source builds are verified; release metadata, public tagged source and signed artifacts are provided.
- Physical tests use a separate application ID and do not clear existing phone history.
- Scores reflect observed evidence; F-Droid acceptance is reserved for F-Droid's review.

## Live steps

1. Completed: inspect source, preserve the baseline and identify the release environment.
2. Completed: fix layout and lifecycle behaviour; the existing 26 UI assertions pass, including preservation across appearance changes.
3. Completed: improve snapshot processing, reads and diagnostics. Lifecycle, corruption and capacity regression checks pass.
4. Completed: prepare F-Droid build recipe, store text, full notices and official pinned wrapper. Public repository created; isolated Linux build and F-Droid metadata lint pass.
5. Completed: 29 UI, 11 lifecycle/corruption, 9 large-text layout and 5 capacity assertions pass. Sixteen live assertions pass on both API 36 emulation and the isolated Pixel API 37 application. Rendered screens reviewed.
6. In progress: review, sign, tag and publish the release artifacts and source, verify exact-tag reproducibility and package the handoff.
