# F-Droid publication status

Current app: **ClipHistory 1.3.3/code 7**, `app.cliphistory`, MIT, Android 14+. Source/contact: https://github.com/saroo98/cliphistory-android.

The release is being qualified from a clean source revision. The previously installed local APK predates the source freeze and is not the F-Droid reference. Exact hashes, reproduction, public release and submission results are recorded after verification. This preparation document does not predeclare success.

GitLab account access was verified on 2026-10-02; the earlier sign-in blocker is resolved. Exact metadata lookup returned HTTP 404, and scoped app-name fdroiddata MR/RFP searches returned no matches. Fork, pipeline and inclusion MR results are separate checks.

Fastlane includes current descriptions, changelog 7, original artwork and synthetic native images. Source/runtime licences are included. The latest-only recipe is `fdroid/submission/app.cliphistory.yml`; historical upstream metadata retains earlier builds. An immutable public source tag, original-key reference, full source hash and passing current recipe are required before submission.

Native runtime/visual results are in [HTML_UI_PORT.md](HTML_UI_PORT.md). Historical qualification remains in [release-1.3/RELEASE.md](release-1.3/RELEASE.md) and does not qualify 1.3.3. F-Droid acceptance remains external.

See [submission procedure](F_DROID_SUBMISSION.md) and [review description](F_DROID_MERGE_REQUEST.md). Never publish keys, cached tooling, private device images or clipboard history.
