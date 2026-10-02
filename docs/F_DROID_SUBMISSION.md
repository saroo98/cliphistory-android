# ClipHistory F-Droid submission

The upstream owner requests inclusion. Follow the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/), [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) and [contribution instructions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).

Current submission: [MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917). The maintainer pipeline passed eight of nine jobs, including the build and reference APK checks. Its sole metadata-formatting failure is corrected in `8465d0cc`; local validation with the CI tool revision passed. A maintainer rerun on the corrected commit is requested because automatic fork pipelines remain identity-gated. See [current publication status](F_DROID_PUBLICATION.md).

## Current app

- `app.cliphistory`, **1.3.3/code 7**, Android 14+, native Android Views.
- Public MIT source/contact: https://github.com/saroo98/cliphistory-android and its issue tracker. Original artwork and complete runtime dependency notices included.
- Separate Shizuku API 13+ must run as non-root shell UID 2000 on the primary profile. It needs starting again after reboot.
- No Internet, overlay, Accessibility-service or keyboard permission, advertising, analytics, account, cloud service, proprietary runtime SDK or native library.
- Five permissions cover Shizuku, foreground recovery, its special-use type, notifications and boot. Recovery respects explicit Stop and Android's user-stop controls.
- Fastlane en-US descriptions, changelog 7, 512px icon and synthetic native images are upstream. Personal-device media is excluded.

## Build and submission

AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No NDK, build account or API key. Original-key reproduction uses Binaries and AllowedAPKSigningKeys. No scanner exemption, disabled build or lint baseline.

1. Freeze a clean source revision containing current code and store materials. Build independently on Windows/Linux. Verify matching unsigned bytes, original certificate, alignment and exact signature reproduction.
2. Publish the immutable tag and versioned reference APK. Pin its full source hash. Never move old tags or overwrite old release assets.
3. Check current fdroiddata metadata and existing requests. On 2026-10-02, the exact app-ID metadata lookup returned HTTP 404, and app-name searches of fdroiddata MRs and RFP issues returned empty arrays. These scoped checks do not rule out differently named requests.
4. Use a public fork and unprotected app.cliphistory branch from current master. Copy only `fdroid/submission/app.cliphistory.yml` to `metadata/app.cliphistory.yml`. This new-app copy contains the latest build only. Historical upstream metadata retains old builds. Store materials stay upstream.
5. Run `fdroid readmeta`, `fdroid rewritemeta app.cliphistory`, `fdroid lint app.cliphistory`, `fdroid checkupdates app.cliphistory` and `fdroid build --test --scan-binary --no-tarball app.cliphistory:7`. Check downloaded reference, source/APK scans and signature match. Use the actual CI formatter revision and serializer when checking canonical YAML; the earlier 2.4.5 environment used a different serializer. Preserve the CI formatter's exact continuation-line output, including the space after `Binaries:`.
6. Inspect actual GitLab CI and the current App inclusion template. Check items only after observing results. If GitLab requires payment or sensitive phone/financial verification, request a maintainer pipeline in the MR instead.

Exact source/release and external workflow results are in [F_DROID_PUBLICATION.md](F_DROID_PUBLICATION.md). Historical 1.3.0 checks do not qualify 1.3.3. Readiness and a submitted MR do not mean accepted/listed. F-Droid maintainers independently review and build. Shizuku reboot requirements, internal API compatibility and bounded runtime coverage remain disclosed.
