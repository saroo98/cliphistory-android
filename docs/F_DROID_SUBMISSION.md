# ClipHistory F-Droid submission

The upstream owner requests inclusion. Follow the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/), [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) and [contribution instructions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).

Current request: [MR !50917](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50917), updated to **1.3.4/code 8** at `550696372b43e4dedb641c2a8cc618125ae1d235`. The previous 1.3.3 maintainer run passed all nine jobs. The latest automatic fork run has no jobs under the account verification gate; a new maintainer run is requested. See [current publication status](F_DROID_PUBLICATION.md).

## Current app

- `app.cliphistory`, 1.3.4/code 8, Android 14+, native Android Views.
- Public MIT source/contact: https://github.com/saroo98/cliphistory-android and its issue tracker. Artwork provenance and runtime dependency notices included.
- Separate Shizuku API 13+ must run as non-root shell UID 2000 on the primary profile. It needs starting again after reboot.
- No Internet, overlay, Accessibility-service or keyboard permission, ads, analytics, account, cloud service, proprietary runtime SDK or native library.
- The five permissions cover Shizuku, foreground recovery, its special-use type, notifications and boot. Recovery respects explicit Stop and Android's user-stop controls.
- Fastlane en-US descriptions, changelog 8, approved 512px icon, 1024 x 500 feature graphic and seven native images are upstream. Personal-device media is excluded.

## Build and submission

AGP 9.1.1, Gradle 9.3.1, JDK 17, platform android-37.0, Build Tools 36.0.0. No NDK, build account or API key. Original-key reproduction uses Binaries and AllowedAPKSigningKeys. No scanner exemption, disabled build or lint baseline.

1. Freeze a clean source revision containing current code and store materials. Build independently on Windows/Linux. Verify matching unsigned bytes, original certificate, alignment and exact signature reproduction.
2. Publish the immutable tag and versioned reference APK. Pin its full source hash. Never move old tags or overwrite release assets. Current source: `97c1e996e2619a5cf6f86f7f70614576d795b8c3`, tag `v1.3.4`.
3. Check current fdroiddata metadata and related requests. Reuse MR !50917; do not open a duplicate. Initial scoped checks and their limits are recorded in the publication report.
4. Copy only `fdroid/submission/app.cliphistory.yml` into `metadata/app.cliphistory.yml` on the public unprotected branch. New-app metadata keeps only the latest build; upstream historical metadata retains earlier builds. Store materials stay upstream.
5. Run `fdroid readmeta`, `fdroid rewritemeta app.cliphistory`, `fdroid lint app.cliphistory`, `fdroid checkupdates app.cliphistory` and `fdroid build --test --scan-binary --no-tarball app.cliphistory:8`. All passed locally. Inspect source/APK scans, downloaded reference and allowed signer. Use CI's fdroidserver source and serializer for canonical YAML. Preserve its exact continuation-line output, including the space after `Binaries:`.
6. Inspect actual latest GitLab CI and reports. Check template items only after observing results for the submitted revision. If GitLab requests sensitive identity or financial information, leave a succinct maintainer rerun request instead. A local pass or prior-release pass does not qualify latest-release CI.

Exact release hashes, executed checks and external workflow are in [F_DROID_PUBLICATION.md](F_DROID_PUBLICATION.md). Readiness and a submitted request do not mean accepted/listed. Shizuku reboot requirements, private API compatibility and bounded runtime coverage remain disclosed.
