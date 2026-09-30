# ClipHistory 1.1 redesign

The owner's request approves the written 31-screen UI specification at UI_UX_SPEC.md, including full-text preview, System/Light/Dark appearance and return-after-copy preference. System-owned UI stays system-owned. Component/storyboard boards are design references rather than extra production screens.

## Live steps

1. Completed: verify baseline, add tested presentation state and shared visual tokens/preferences.
2. Completed: implement history/search, recovery states and full-text preview.
3. Completed: implement sheets, diagnostics/help, tile entry and launcher assets.
4. Completed: emulator UI tests, normal/larger-text layout inspection, release build checks and signed upgrade/launch smoke test. Physical Shizuku acceptance remains explicitly unverified.
5. Completed: signed 1.1.0 APK, updated source and evidence packaged; archive integrity and embedded APK identity verified. Signing material and local caches excluded.

## Completion criteria

- S01–S12: light/dark home, local search, loading/empty and accurate recorder recovery states.
- S13–S19: overflow, contextual actions, validated capacity, destructive confirmation and live test results.
- S20–S28: diagnostics/help, system tile flow, read-only full text, persisted preferences and honest errors.
- S29–S31: shared components, restrained platform motion and adaptive/monochrome icon assets. No mock gallery in release.
- Preserve private storage schema, daemon authorization, Shizuku dependency and screenshot protection. No internet permission or new production dependency.
- Preserve app signing identity, increment version, test new presentation behavior and existing core regression suite.
- Inspect actual rendered emulator UI with synthetic test data. Any untested physical Shizuku behavior remains explicitly unverified.

## Implementation choice

Retain the existing native View implementation and use shared resource tokens and small UI helpers. Platform Dialog windows provide bottom sheets and native dialogs without adding a framework. Separate presentation-state mapping, preferences and secondary-page content from Activity orchestration. Test-only instrumentation may populate synthetic private history and capture its own rendered Views, with no fixture route or security bypass in the release.
