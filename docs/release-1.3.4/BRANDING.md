# ClipHistory 1.3.4 branding validation

The approved logo is integrated without new dependencies or permissions. Selection, dimensions and exact copied asset hashes are in [branding](../../branding/README.md). Version 1.3.3 and its release assets remain unchanged.

Fresh API 36 disposable emulator checks on 2026-10-02:

- Settings: 21 assertions passed, including privacy, rotation and preferences. Onboarding: 24 assertions passed, including welcome frequency and setup navigation. These FeatureChecks suites select light internally; passing a dark argument does not change their theme.
- HTML-port native regression suite: 121 assertions passed separately with light and dark arguments. This is the native Android implementation, not browser execution of the HTML reference.
- Native UI smoke suite: 33 assertions passed, including history, search, copy, full text, settings and offline states. It rendered the actual installed adaptive icon to `runtime-adaptive-icon.png`.
- Actual OS screenshots of Welcome and Settings > About were captured and visually inspected in both themes. The mark and app name remain legible, with the existing hierarchy and spacing. Screenshots were enabled only in the disposable validation package for these OS captures.
- The HTML reference keeps embedded assets and adds the favicon, viewer wordmark and matching Welcome/About identity. Node's syntax check and direct source checks passed. `html-logo.patch` records the exact change. Browser rendering was blocked by the browser tool's local-file URL policy and is unverified.

Windows development assembly, 44 JVM tests (zero failures/errors/skips), and release lint (zero errors) passed before release freeze. Clean frozen-source builds, final lint counts, APK signature/reproduction and F-Droid recipe results are recorded separately in `checks.json` after execution. No zero-latency or every-device claim follows from these checks.

GitHub README images and social preview are separate surfaces. A local social-preview file alone does not establish an upload. Current external publication status is recorded in [F_DROID_PUBLICATION.md](../F_DROID_PUBLICATION.md).
