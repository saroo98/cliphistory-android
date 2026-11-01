# The reusable guide to publishing an Android app on F-Droid

Research checked: **1 November 2026**. This guide is for the **official F-Droid main repository**, also called `f-droid.org` or the main repo. It can be copied into any Android project. Rules, tools and review practices change, so check the linked official documents again before each submission.

The main idea is simple: **F-Droid reviews the app and normally builds its APK from public source.** Uploading an APK, making an F-Droid-compatible repository, opening a request, passing CI, having a merge request merged and appearing in the public catalogue are different things.

A separate F-Droid-compatible repository can distribute apps under its own rules. It is **not** the same as inclusion in the official main repo, and users must add it to their client.

## The short route

1. Make a useful, maintained app with public source, an appropriate free-software licence and a public way to report problems.
2. Ensure the app, its build tools and distributed dependencies satisfy F-Droid's [inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/). Disclose applicable [Anti-Features](https://f-droid.org/en/docs/Anti-Features/).
3. Make a clean release build from a public tagged commit. Choose your signing strategy before the first F-Droid release.
4. Add the English store description, icon, screenshots and release notes to the app's source repository.
5. Either open a [Request for Packaging](https://gitlab.com/fdroid/rfp/-/issues) and ask for help, or prepare a build recipe in [fdroiddata](https://gitlab.com/fdroid/fdroiddata) and open a merge request. Search first for an existing request.
6. Respond to review, fix build and policy problems, and monitor the production build after the recipe is merged.
7. Confirm the app and the correct version actually appear in the catalogue. For every later release, repeat the relevant checks.

**A useful distinction:** policy requirements and the current new-app merge-request checklist are not identical. Some practices below are strongly recommended rather than universal rules. No guide can guarantee acceptance: the reviewers apply the current policy to the actual app.

## 1. Understand the parts

### Start with comparable apps and an evidence register

This is an engineering workflow, not an extra F-Droid policy. Apply it before implementation and repeat the relevant parts before each release, especially after a platform, permission, dependency or distribution change.

1. Identify the product's actual dependencies and boundaries: OS APIs, external apps, services, file formats, permissions, signing identity, supported devices and profiles. For each boundary, list the assumption that could fail. A package name, one device or one happy-path test is not a compatibility contract.
2. Inspect two or three maintained comparable apps. Read their setup and troubleshooting guides, relevant integration source, recent issues and release notes. Prefer a comparable F-Droid recipe when one exists. Follow primary sources; verify claims from forums against code or a reproduction. Record the URL, date and relevant revision. If no useful analogue exists, record that instead of inventing one.
3. Convert useful findings into explicit decisions: adopt a small established pattern, test a failure mode, document a limitation, or reject an unrelated feature with a reason. Do not copy code without satisfying its licence. Other apps' features and permissions are not a mandatory feature list.
4. Review forks, renamed packages, companion apps and alternate distribution variants for each external integration. Separate protocol/API compatibility, manager discovery, fresh authorization, lifecycle and actual operations. Test the F-Droid variant when it differs; an upstream APK is not interchangeable evidence.
5. Keep one short register in the source repository. Each row needs: user impact, source or reproduction, affected versions, decision or fix, exact validation, result and remaining limitation. Distinguish confirmed defect, upstream defect, intentional boundary, untested risk and passed check. Give external blockers a concrete retest procedure.
6. Turn every confirmed product regression into a focused automated or repeatable manual check. Reproduce the failure first, fix its cause, then repeat the same scenario on the changed build. Never mark an intermittently passing failure fixed without diagnosing it.

Example register format:

| Risk / reported failure | Evidence and affected build | Decision / change | Test and exact artifact | Result / remaining gap |
| --- | --- | --- | --- | --- |
| External manager uses a different package | Link to source or issue; versions | Detect the live API; offer an explicit manager choice | Fresh authorization and actual operation; APK SHA-256 and manager variant | Passed / failed / blocked, with scope |

For clipboard apps, [Octoclip's Shizuku setup](https://docs.octoclip.app/features/source/background-monitoring/android/shizuku) is useful for external-copy and recovery scenarios. [Easy Clipboard](https://github.com/Qutaiba-Khader/easy-clipboard) illustrates alternative capture mechanisms, whose extra permissions and interaction costs need a product decision. [Install with Options](https://github.com/zacharee/InstallWithOptions/blob/main/app/src/main/java/dev/zwander/installwithoptions/util/ShizukuUtils.kt) illustrates checking the running service before manager discovery. These examples motivate tests; they do not prove your app works or require adopting their architecture.

| Term | Meaning |
| --- | --- |
| **App source / upstream** | Your public repository containing the app, its licence, build instructions and store text and images. It may be hosted on GitHub, GitLab, Codeberg or another accessible supported host. |
| **Application ID** | The unique Android package identity, for example `org.example.myapp`. Android treats a changed ID as a different app. |
| **F-Droid client** | An Android app that displays F-Droid-compatible repositories and installs or updates their apps. |
| **Main repository** | The official catalogue maintained by F-Droid, with its own eligibility and review process. |
| **fdroiddata** | The [GitLab repository](https://gitlab.com/fdroid/fdroiddata) containing build recipes and catalogue information for the main repository. |
| **fdroidserver** | The [F-Droid tools](https://gitlab.com/fdroid/fdroidserver) that read recipes, scan, build, verify and publish packages. |
| **Recipe / build metadata** | A YAML file in `fdroiddata/metadata/<applicationId>.yml` describing where the source is, which revision to build and how to build it. It is separate from the store text in your app repository. |
| **RFP** | A request for packaging. A volunteer may prepare the recipe; opening the request is not itself publication. |
| **MR** | A GitLab merge request proposing the recipe to `fdroiddata`. Passing its checks still requires human review. |
| **Anti-Feature** | A warning about behaviour or dependencies some users may not want. A flag can be compatible with inclusion; it does not excuse a prohibited proprietary library inside the APK. |
| **Reproducible build** | An independent build matches the relevant bytes of a reference release, allowing verification of its source and, when configured, use of the upstream signature. |

The official [developer FAQ](https://f-droid.org/en/docs/FAQ_-_App_Developers/) explains the process and its limits. Reviews and automated scans are useful safeguards, but do not imply that every line of every version has received a complete security audit.

## 2. Check eligibility before building the release

Read the live [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) before starting and again before submission. Its details take precedence over this summary.

### Public source, licences and rights

- Publish the **real, current source**, build configuration and scripts in an accessible version-control repository. A placeholder repository or a release APK alone is insufficient.
- Give the application a recognized free and open-source software licence. Include the complete applicable licence text, copyright notices and any required attribution. Use accurate SPDX identifiers, such as `GPL-3.0-only` or `Apache-2.0`, in the recipe.
- Check licences and source availability for libraries, native code, build plugins and generated or bundled parts. An open-source app containing proprietary runtime libraries is not automatically eligible.
- Check fonts, icons, images, audio, maps and other assets separately. F-Droid can accept some non-free **non-code** assets if their licences permit redistribution; these may require a `NonFreeAssets` warning. Do not assume every asset restriction is acceptable.
- Respect third-party copyright and trademarks. Record where important assets came from and their licences. Do not copy another app's brand or identity without the necessary rights.
- Make the public source buildable without developer-only passwords, private assets or undisclosed build steps. Remove secrets from both source and released artifacts. F-Droid says it does not sign up for API keys and that keys supplied to it are included in public source and binary releases. A secret that must remain private cannot be protected by placing it in an APK.
- Before making an existing repository public, review the files and history for credentials, signing keys, private data and licensed material you cannot distribute. Removing a secret from the latest commit does not remove it from Git history; rotate an exposed credential and deal with the history before publication.

### Is the app useful and maintained?

The policy says new apps should be actively maintained, work as described, provide value to users and not merely be demos. A website wrapper should provide native features or enhancements. A minor rebranding of an already maintained app may lack sufficient unique value.

The current quality guideline says an app **should have at least three months of active development**, unless it has proven users, its author already maintains other F-Droid apps, or the app is made only for an event. Give reviewers evidence if an exception applies. A working build alone does not resolve this review question.

Explain in ordinary language what the app does, who it helps, what makes it useful compared with similar apps, and any meaningful limitation. Avoid claiming features that the F-Droid build lacks.

### Package identity and forks

Use a unique, stable application ID. F-Droid advises basing it on a domain you control. Do not use an ID that impersonates another developer or conflicts with another app or domain.

For a fork, follow the current policy on a **different application ID**, name, icon and relevant strings and translations. Explain the meaningful changes, observe the original licence and branding rights, and give credit where required. Different packaging alone is not proof of unique value.

If you are proposing somebody else's app, notify the original author and link their response showing they do not oppose inclusion. The current MR checklist asks for this.

### Proprietary libraries and build inputs

F-Droid's policy excludes proprietary tracking, advertising and analytics SDKs, including examples such as Google Play services, Firebase and Crashlytics, from the distributed app. Use a free alternative, or create an F-Droid/FOSS build flavour that actually removes incompatible code **and dependencies**. Hiding a button is not enough.

Do not assume every Google-hosted artifact is proprietary, or that every artifact in a public repository is free. Check the particular dependency and licence. The policy describes limited allowances for free prebuilt artifacts from approved sources, such as certain Maven repositories, Debian packages and specific SDK/toolchain exceptions. Prefer source builds or accepted free dependencies and check the live policy for current exceptions.

Inspect direct and transitive dependencies, Gradle plugins, native libraries, JavaScript/Dart packages, submodules, assets and generated files. Prebuilt JAR, AAR, `.so`, executable and archive files need a clear origin and justification. An unsupported private artifact repository, proprietary IDE or local-only binary can block a clean build.

For Gradle, dependency reports can help. Replace the module and configuration with the actual release flavour:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

This report is a starting point, not a full licence or build-plugin audit. Also inspect the build configuration and the actual F-Droid APK.

### Privacy, network services and executable downloads

Explain clearly when the app uses the network, what data is sent, why, and which external service receives it. Collect only what is needed. Request sensitive permissions when the user needs the feature, explain why, and handle denial sensibly.

An app can depend on or promote a proprietary **network service** and still be considered for inclusion with the appropriate warning. That is different from bundling a prohibited proprietary SDK. Similarly, advertising delivered entirely by free software may be eligible with an `Ads` warning; a proprietary ad SDK is not made acceptable by adding a warning. Ask reviewers when a case is unclear.

The policy requires additional executable downloads, including add-ons or self-updates, to be **explicitly opt-in**. The choice to decline must be as accessible as accepting, and the user must be told that the download bypasses F-Droid's checks. Do not silently install executable code.

## 3. Audit Anti-Features honestly

Review the actual behaviour of the release flavour against the current [Anti-Features list](https://f-droid.org/en/docs/Anti-Features/). Common cases include:

| Warning | What to investigate |
| --- | --- |
| `Ads` | Advertising presented by the app. |
| `Tracking` | Activity or personal data sent to a service, including relevant optional or indirect tracking as assessed by F-Droid. |
| `NonFreeNet` | Reliance on or promotion of a proprietary network service. |
| `TetheredNet` | Dependence on a particular service that users cannot readily replace or self-host. |
| `NonFreeDep` | A non-free **external app** is needed for some function. |
| `NonFreeAdd` | Promotion of non-free add-ons or apps. |
| `NonFreeAssets` | Non-free assets used in the app, subject to redistribution rights. |
| `KnownVuln`, `DisabledAlgorithm` or `NoSourceSince` | Known security, signing-algorithm or source-availability problems. |

The precise list and definitions can change. Explain the specific behaviour and affected features to reviewers rather than deciding that no warning applies because a permission or tracking library is absent. Some flags apply to particular versions. Existing popular apps can have Anti-Features, so copying their behaviour does not make a particular new app automatically compliant.

## 4. Prepare a clean, buildable project

A new maintainer should be able to clone the public repository, check out the release and understand how to build it without your computer.

Keep a short `README` or `BUILDING.md` covering:

- Prerequisites: supported JDK, Android SDK and Build Tools, Gradle wrapper, and NDK or other toolchains if used.
- The exact release flavour and build command, plus where the APK appears.
- Required configuration and whether the app needs a server, an account, another app, root or special hardware.
- How a contributor can run the relevant tests and where to report problems.

Keep versioned build files and the Gradle wrapper in the repo; use a verified wrapper distribution and checksum where supported. Pin meaningful dependencies and toolchain versions. Avoid dynamic versions, private Maven repos, local SDK paths, uncommitted files and steps that require personal credentials. Keep private signing keys, passwords and real user data out of the repository and source archive.

For a normal Gradle project, a release build might look like this:

```sh
./gradlew --no-daemon :app:assembleRelease
```

This is an example, not a universal command. Your module might not be named `app`, and a flavour might require a different task, such as `assembleFossRelease`. Use your project's actual tests, lint and build tasks. The output can be unsigned if release signing is deliberately left to the distribution process.

**Flutter, React Native, Godot and native projects:** keep the relevant build inputs and lockfiles, document native and framework tool versions, and make every required source and dependency available. Start from a matching [F-Droid build template](https://gitlab.com/fdroid/fdroiddata/-/tree/master/templates) and an actively maintained similar app. A plain Android Gradle recipe is not sufficient for every toolchain.

F-Droid distributes installable APKs for the main repo. Verify that the chosen recipe actually produces the intended APK, not only an Android App Bundle.

## 5. Choose signing and reproducibility early

Android checks the application ID, version and signing identity when applying an in-place update. A different signer normally prevents the update. Do not assume a Play Store build, your own APK and an F-Droid build use the same certificate.

If you also use Google Play App Signing, check the certificate on the APK actually delivered to users. Your Play upload-key certificate can differ from the Play app-signing certificate.

| Choice | How it works | Main consequence |
| --- | --- | --- |
| **F-Droid signs** | F-Droid builds from source and signs with its key for the app. No upstream reference APK is required for this route. | Other channels signed with a different key normally cannot update it in place. |
| **Upstream-signed verified release** | You publish a signed reference APK. F-Droid independently builds the source and checks it against that APK. The verified upstream-signed APK can then be published. | Compatible channels can update each other if their package, signer and versioning are compatible. A mismatch blocks the verified release. |

Reproducible builds are **strongly encouraged but not a condition for every app**. If you cannot enable them, explain why in the MR. Switching signing identity later can require users to back up data, uninstall and reinstall. Android signing-key rotation and migration have specific platform constraints; agree any proposed change with F-Droid and test the actual upgrade path.

For the upstream-signed route:

1. Protect and back up the private signing key and its credentials. Never upload the private key to F-Droid.
2. Build the reference APK from the exact clean, tagged source revision and publish it at a stable, publicly downloadable, versioned URL.
3. Add `Binaries`, or the appropriate per-build `binary` field, and `AllowedAPKSigningKeys` to the recipe. Use the real lowercase SHA-256 **certificate fingerprint**, not the APK file's SHA-256 checksum.
4. Run the F-Droid comparison or have the relevant CI run it. Compilation alone is not a reproducibility check. Record the source revision, APK and verification result.
5. Use the same release flavour, application ID, versions and signing-compatible toolchain. Changes in JDK, Android Build Tools, NDK, ABI, archive ordering, embedded Git information, timestamps or generated files can cause differences.

These are the additional top-level recipe fields for a simple, single-APK example. Replace the URL and fingerprint with your own verified values; this is not a complete recipe:

```yaml
Binaries: https://example.org/releases/v%v/my-app-%v.apk
AllowedAPKSigningKeys: REPLACE_WITH_LOWERCASE_CERTIFICATE_SHA256
```

F-Droid substitutes `%v` with the build's version name and `%c` with its version code. Check that the resulting URL downloads the exact matching signed APK without login. The certificate value is 64 lowercase hexadecimal characters, without colons; do not use the APK's file hash. Use the per-build `binary` field if different builds cannot share one URL pattern.

To inspect a **signed** APK with the Android SDK tools:

```sh
apksigner verify --verbose --print-certs path/to/release.apk
sha256sum path/to/release.apk
```

The first command reports signing certificates; the second computes the file hash. Replace the path. `sha256sum` is a common Linux command; on Windows, PowerShell's `Get-FileHash -Algorithm SHA256` can provide the file hash. An unsigned build cannot pass a signature check until it is signed.

Use `zipalign` when appropriate **before** signing, and verify alignment using the matching Android Build Tools. Do not prescribe a signing-tool version blindly: follow the current [reproducible-build documentation](https://f-droid.org/en/docs/Reproducible_Builds/) and check which tool version and settings work for your release.

F-Droid also documents a more advanced arrangement that can provide both its own and upstream signatures for existing users. It is not the simple default; discuss the migration and exact recipe with maintainers.

## 6. Create an identifiable release

1. Set a meaningful `versionName` and increase `versionCode`. Every new version code needs to be greater than the previous codes that the same installation path must update.
2. Finish code, metadata, licence notices, release notes and tests. Verify that the F-Droid flavour has the correct ID and version.
3. Commit and tag the release using a consistent tag scheme. Push the commit **and** the tag. The new-app MR checklist currently asks for the full commit hash in the recipe, even though release tags are also needed for updates.
4. Build from the clean tagged commit. If you use an upstream-signed reference, publish the matching APK and retain it at a stable URL. Do not silently move a release tag or replace its APK.
5. Record the commit, tag, app ID, version code, build command, toolchain, APK SHA-256 and signing-certificate fingerprint. Record actual tests and unresolved limitations.

For example, once the intended release is committed and the working tree is clean, these Git commands create and publish a tag and print its commit. Replace `v1.0.0` with your real release tag, and push the relevant branch first:

```sh
git status --short
git push origin HEAD
git tag v1.0.0
git push origin v1.0.0
git rev-list -n 1 v1.0.0
```

Check that the printed full hash and the APK's version are the ones entered in the recipe. Do not run the tagging commands until you are ready to publish that release.

Simple version values make automated updates easier. F-Droid's update checker generally reads version information from files; it does not execute arbitrary Gradle logic to calculate a version. If versions are in a custom file, a recipe may need `UpdateCheckData`. Test the actual update detection.

Tag-based checks can pick the highest matching version code, including an unintended prerelease. Filter tags or choose another documented update strategy if your repository also tags unstable versions.

If you ship ABI-specific APKs, each needs an appropriate build entry and a distinct version code. All codes in a new release must be higher than the prior release's relevant codes; set up `VercodeOperation` where needed. Split APKs are encouraged when they materially reduce a large download, not universally required. Follow the current [quick-start guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/) for the exact version scheme.

## 7. Test the release people will receive

At minimum, verify the actual release flavour, not only a debug build:

- Fresh install and the complete main user workflow.
- First launch, clear errors, empty states, offline or unavailable service behaviour where relevant.
- Permission denial and recovery; confirm optional features do not request access unnecessarily.
- Rotation, readable text and practical accessibility on the screens that matter.
- An update from the last distributed version, preserving existing data and settings where applicable.
- Device/Android versions and ABIs relevant to the app. Use a real device when hardware, background restrictions or manufacturer behaviour matters.

Test data migration, backup/restore, background services, account sign-in, native libraries, large-screen layouts and network failure **when those features exist**. Do not invent a large test matrix for an app that does not need it. Note what you did not test.

### Build a bounded, risk-based qualification matrix

Use the register above to select tests. Record the app ID, version code, source revision or uncommitted candidate status, APK and test-APK hashes, installation channel, device model, full OS build, ABI, dependency version/mode, initial state, command or steps, duration and result. A test count without its scope is not sufficient evidence.

| Area | Applicable checks |
| --- | --- |
| Installation and updates | Exact signed release fresh install; supported minimum SDK; same-signer update from the last public version preserving synthetic data/settings; intended behavior below minimum SDK. For persistent apps, update once with recording active and once after an intentional stop. Cover older supported OS boundaries, not only the newest phone. Never uninstall a personal copy just to bypass a signer mismatch. |
| Integrations | Missing/stopped/old service, different package, multiple installed managers, required companion absent/present, fresh grant, denial, revocation, service/app update and restart. Exercise the actual capability, not only a green connection indicator. |
| Android boundaries | Minimum and latest supported stable OS plus versions where permission, lifecycle, clipboard, tile or storage APIs changed. Treat previews separately. Use stock emulators for platform coverage and physical OEM hardware for OEM claims. |
| Background work | Screen off, battery saver, Doze, app/process/helper death, task dismissal versus explicit force-stop, reboot and first unlock, recovery enabled/disabled. Record whether a service had to be restarted manually. Never promise recovery from an explicit force-stop against Android's rules. |
| Sensitive data | Defaults, consent, marked/unmarked input, timeout while visible/background/locked, reboot, duplicate copies, undo, setting changes, screenshots/Recents, backup rules, corruption and key loss. Explain what expiry deletes and what it does not. Use only synthetic secrets. |
| Functional boundaries | Empty/large/Unicode/RTL/multiline input, unsupported content, duplicate policy, history capacity, search, deletion/Undo, source-app auto-clear, external-copy/paste, rapid input and meaningful overload reporting. |
| UX and accessibility | Light/dark/system theme, large fonts, small and large screens, portrait/landscape, TalkBack spoken navigation and actions, keyboard focus, contrast, touch targets, reduced motion, close/back/cancel and permission-denied states. An accessibility-tree assertion does not replace a TalkBack listening check. |
| Performance | Cold and warm launch, first use after idle, repeated tile/panel open-close, long history and rapid copies. Record observed latency and dropped frames on a named device; do not promise zero delay on every phone. |
| Distribution | Exact release manifest and permissions, no debug hooks, dependency/licence audit, source and APK scans, local reproducibility where used, recipe build from immutable public source, current CI, clean-device F-Droid installation once published. |

For persistent background apps, include a bounded screen-off/Doze run before release and a longer natural-use observation when feasible. Record exact duration and power state. A forced-idle emulator run does not establish overnight OEM battery reliability. Never fabricate a multi-day result during a short session.

When lowering the minimum Android version, repeat upgrade and stop/recovery tests on the newly supported versions. A fresh-install pass does not cover their older process-exit semantics. For example, Android's [ApplicationExitInfo documentation](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_PACKAGE_UPDATED) explains that before Android 14 an update could be reported as `REASON_USER_REQUESTED`. Preserve real user stops and unknown cases; do not broadly disable stop protection to make update recovery pass.

For an independent helper that can survive an app replacement, verify that the helper itself uses the candidate code. An installed APK hash alone cannot prove that an older running process was replaced. A same-version development reinstall may retain a helper whose lifecycle is keyed to the version code; restart it through the app's supported controls before the final check. Preserve the user's history, pause state and recovery choices.

Run narrow checks while fixing a defect. Run the affected regression matrix and release gates once the candidate is stable. Serialize operations that share a device, clipboard, service, build output or settings; parallelize independent devices only. Stop a failed suite, keep its logs and earlier passing evidence, diagnose, then resume. After two similar failed attempts reconsider the fixture or hypothesis; do not keep blindly retrying until it passes.

Verify the test environment before attributing a failure to the app. A boot-time System UI non-response dialog can take focus before the app starts; a legitimate onboarding sheet can hide correctly saved content. Preserve the first failure's logcat, crash buffer, focused window, activities and process-exit records in UTF-8. Compare timestamps and inspect the actual UI. Handle only observed setup prompts, and record any environmental recovery before resuming. Never suppress system errors globally or weaken the functional assertion. Use one UI-automation owner per device, and run emulators sequentially when host resources are constrained.

### Release decision

Release-blocking defects include data loss, unauthorized access, unreadable existing data, crashes or failure of the documented core workflow on a supported configuration, incompatible signing, proprietary dependencies contrary to policy, and failed required build/verification checks. Fix and retest them before publication. For inaccessible hardware or an upstream bug, narrow claims, document the limitation and workaround, and make an explicit release decision; do not tick the test as passed. A reviewer-requested retest remains open until accepted by that reviewer. A clean Pixel test cannot close a HyperOS-specific report.

Freeze the candidate before final checks. Any later code/resource change invalidates the affected APK results. Rebuild, update hashes and rerun the relevant checks. Keep the tested local candidate, tagged public source, published APK, F-Droid recipe, CI revision and catalogue version distinct. Never replace an already published APK or move its tag silently.

### If the app uses Shizuku or a compatible fork

Treat the live service and its capabilities as the source of truth. A manager may have a different package name, or no launcher at the original Shizuku package. Use the documented Shizuku API and provider, wait for the Binder, then check the API version, user approval and the privilege identity required by the feature. Do not reject a running service solely because the original manager package is absent. A list of known packages may help open a manager, but it cannot identify every fork or prove which one supplied a Binder. When multiple known managers are installed, offer an explicit choice for manager and battery shortcuts rather than guessing the active service. For unknown managers, explain how to open them manually. Do not enumerate every installed app or request broad package visibility just for this shortcut.

For a real-world example, [Install with Options checks the running Binder](https://github.com/zacharee/InstallWithOptions/blob/main/app/src/main/java/dev/zwander/installwithoptions/util/ShizukuUtils.kt). [Mihon had a reported fork-detection failure](https://github.com/mihonapp/mihon/issues/3575), illustrating why installation checks alone need testing. Neither example establishes compatibility with every fork.

Test at least the original manager and relevant forks on disposable devices or emulators. Record the exact app and manager versions, Android version, activation mode and build variant. Exercise a fresh install, a running service with permission not yet granted, denial and recovery, a stopped service, an actual privileged operation, service restart and an update that preserves data. Where the app uses a Shizuku UserService, test that service too. Use synthetic data and avoid changing a personal phone's existing manager or clipboard without understanding the effects.

When switching managers, stop the previous service through its own controls and confirm which service is actually running. A fork's native watchdog may restart a killed process even after its manager Activity is closed. Do not attribute a result to the newly opened manager while another service still supplies the Binder. Use a clean disposable reboot when necessary, start only the intended service, and record its provenance. Preserve failed setup logs and collect the crash buffer before shutting down a failed test device.

Check service restart while the client is already running in the background as well as after opening it again. These paths can use different manager-side discovery mechanisms. A foreground connection does not prove automatic background reconnection. If an OS update breaks the manager's discovery or approval UI, preserve the reproduction, identify the affected manager version, test a user-controlled workaround or compatible release, and record the upstream limitation. Do not bypass authorization or add continuous polling to conceal it.

Compatibility is not automatic for every fork or operating mode. For example, [Shizako](https://github.com/cr1437/Shizako) uses a different application ID, while [Porter](https://porter.darken.eu/compatibility) can provide Shizuku compatibility through a companion app. Porter's [direct Shizuku bridge](https://porter.darken.eu/developers) does not implement upstream UserServices, so an app that depends on them needs a supported alternative or the compatibility companion. [Shevery](https://github.com/HmnDev-Tech/shevery/tree/14.6.1/shevery-api) has a separate native permission; test that the user can approve it and that the required operations work. If a manager's approval prompt does not appear, test its manual app-authorization screen instead of bypassing the user’s consent. If it fails to recognize an installed or updated client, also test restarting its service. Check each fork's current documentation and test the actual setup. Do not claim support for untested forks, root, device-owner modes or Android versions.

For F-Droid, test the **F-Droid build variant** of both the app and any relevant separate manager when it differs from the upstream release. Record any unavailable artifacts or hardware as untested; a successful source review or GitHub-build test does not establish that the F-Droid variant works. Document the external service requirement and its setup in the listing and app help.

If the app or any shipped dependency includes native libraries, check ABI coverage and Android's [16 KB page-size support](https://developer.android.com/guide/practices/page-sizes) for applicable devices.

You can inspect the APK with the Android SDK's [APK Analyzer](https://developer.android.com/tools/apkanalyzer):

```sh
apkanalyzer apk summary path/to/release.apk
apkanalyzer manifest print path/to/release.apk
```

Check the application ID, version, permissions, components, SDK requirements and that the release is not debuggable. Run the signer check from the previous section on a signed APK. Use synthetic test data and avoid exposing personal content in logs or screenshots.

## 8. Prepare the catalogue listing in the app repository

The current new-app MR checklist requires an `en-US` summary and full description in an upstream Fastlane or Triple-T layout. It recommends images, icon and changelog. You do **not** need to install Fastlane just to create the folders.

A common layout at the **root of your app source repository** is:

```text
fastlane/metadata/android/en-US/
  title.txt
  short_description.txt
  full_description.txt
  changelogs/
    1.txt
  images/
    icon.png
    featureGraphic.png
    phoneScreenshots/
      1.png
      2.png
```

Here `1.txt` is an example: the filename must be the actual numeric `versionCode` without padding. Other supported locations exist; follow [F-Droid's metadata documentation](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/).

- `short_description.txt`: under 80 characters is the quick-start recommendation; no trailing full stop. Say what the app does.
- `full_description.txt`: up to 4,000 characters. Describe key features, prerequisites, limits, network use and relevant external dependencies accurately.
- `title.txt`: optional in the documented structure, up to 50 characters.
- `changelogs/<versionCode>.txt`: up to 500 characters; describe what changed for that release.
- `icon.png` and screenshots: use real, current visuals without personal data. PNG and JPEG are supported; use the documented file extensions. A 512 × 512 icon and 1024 × 500 feature graphic are common, not a universal inclusion rule.
- Add translations when available, but do not claim they are mandatory for acceptance. Explain special setup, such as accounts, hardware, root or a server.

Screenshots, icon and changelog improve the listing and discovery. The documentation describes additional criteria for the client's Latest view, including a translation; client behaviour can change. These criteria are not the same as inclusion eligibility.

Keep the metadata in the **release commit F-Droid builds**. A change only on your development branch will not appear for an older release. For the main repo, do not put screenshots or a duplicate description in the `fdroiddata` MR. Existing recipe-level `Name`, `Summary` and `Description` can override upstream text, so check precedence if an update is not showing.

## 9. Choose a submission route

First search for your application ID, source URL and app name in the [catalogue](https://f-droid.org/en/packages/), [existing RFPs](https://gitlab.com/fdroid/rfp/-/issues) and [fdroiddata merge requests](https://gitlab.com/fdroid/fdroiddata/-/merge_requests). Update an existing relevant request instead of creating duplicates.

### Route A: Request for Packaging

Use the [RFP issue tracker](https://gitlab.com/fdroid/rfp/-/issues) if you need a volunteer to write the recipe. Fill in the current issue template, including:

- App name, application ID and a concise description.
- Public source URL, licence, latest release/tag and build instructions.
- What the app does, prerequisites, any Anti-Features and why it is useful.
- A link to the author's non-opposition if you are not the author.
- Links to any previous related request or relevant discussion.

This is usually easier for a first-time submitter, but it depends on volunteer time and does not guarantee packaging or acceptance. Keep the request current and answer questions there. If GitLab access itself is blocked, consult F-Droid's [contact options](https://f-droid.org/en/about/) or forum for the current fallback; do not create multiple unrelated requests.

### Route B: Submit a complete recipe

This route gives reviewers a concrete build and can reduce their packaging work. Read the current [fdroiddata contributing guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md), [new-app MR checklist](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/Default.md), [templates](https://gitlab.com/fdroid/fdroiddata/-/tree/master/templates) and [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).

1. Sign in to GitLab, fork `fdroid/fdroiddata` and use a separate, unprotected branch for this app. The fork should be public for review.
2. Create `metadata/<applicationId>.yml`, either in your clone or through GitLab's editor. Only one new app belongs in one MR.
3. Start from the official template for your build system or a comparable current recipe. Add real source and issue links, licence, author, source revision and build settings.
4. Enable automated update checks when your release scheme supports them; otherwise explain the manual process. Test the configuration.
5. Keep only the latest intended build or required current ABI builds before initial merge; do not add disabled historical versions. Use the full release commit hash. Ensure YAML is valid and uses LF line endings.
6. Run the available metadata/build checks and inspect the diff. Push the branch and open a new-app MR targeting the official `fdroiddata` repository. Use the current template and link any RFP.
7. Check the exact MR commit's pipeline and reports, answer reviewers, and update the same branch when changes are needed.

The upstream source repo contains app code, release tags and store assets. The `fdroiddata` MR normally contains the recipe. A source repository hosted on GitHub is fine; the official recipe and MR are on F-Droid's GitLab.

If using GitLab in a browser, create the public fork, create an app-specific branch, add the recipe under its `metadata` folder, and commit it to that branch. Open the fork's **CI/CD** view to inspect the pipeline, then create a merge request from that branch into the official repository's `master` branch. Fill in the new-app template and use the Changes view to confirm the MR contains only the intended files. The current contributing guide has the detailed screen-by-screen steps.

### A small Gradle recipe example

This is a **teaching example**, not a ready-to-submit recipe. Replace every example value with verified values for your own release. The example assumes a module named `app`, no product flavour and simple tag-based updates.

```yaml
Categories:
  - System
License: Apache-2.0
AuthorName: Example Developer
SourceCode: https://github.com/example/example-app
IssueTracker: https://github.com/example/example-app/issues

RepoType: git
Repo: https://github.com/example/example-app.git

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: REPLACE_WITH_FULL_RELEASE_COMMIT_HASH
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.0'
CurrentVersionCode: 1
```

Choose actual categories from F-Droid's accepted list; use the correct SPDX licence and public URLs. The `gradle` value is the flavour for a flavoured app. A Flutter, React Native or custom build needs its own recipe. Add signing fields only when using a verified upstream binary. Do not submit the placeholder hash or example URLs.

If you use the fdroidserver importer, the contributor guide documents `fdroid import --url … --subdir …`. Treat generated metadata as a starting point, then verify every value. External source repositories are generally preferred as Git submodules when feasible, per the current checklist.

## 10. Validate the recipe

You can edit metadata without installing fdroidserver, then use the fork's CI. Local fdroidserver testing is useful when you have a suitable isolated Linux environment. Windows users can use an appropriately configured Linux environment or the documented CI route.

Follow the live [quick-start environment instructions](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/) or [build-server setup](https://f-droid.org/en/docs/Build_Server_Setup/). Record the tool version. Do not run untrusted build recipes on a machine containing signing keys or sensitive data: a recipe can execute build commands.

For an optional Linux installation of the tools themselves, F-Droid's contributor guide documents installation from its Git repository. For example, in a separate tools directory with Python, Git and virtual-environment support already installed:

```sh
python3 -m venv fdroid-tools-venv
. fdroid-tools-venv/bin/activate
python -m pip install git+https://gitlab.com/fdroid/fdroidserver.git
fdroid --version
```

This installs the command, not the Android SDK or a complete isolated build server. Follow the [installation guide](https://f-droid.org/en/docs/Installing_the_Server_and_Repo_Tools/) for system prerequisites and configuration. The production infrastructure may use a newer fdroidserver revision than an OS package. You can skip this installation when submitting through GitLab and using the available CI.

From a prepared `fdroiddata` checkout, these are useful checks for a specific app:

```sh
APP_ID=org.example.myapp
fdroid readmeta
fdroid rewritemeta "$APP_ID"
fdroid lint "$APP_ID"
fdroid checkupdates --allow-dirty "$APP_ID"
fdroid build --test --stop --scan-binary --no-tarball "$APP_ID"
```

Replace `APP_ID` with the actual ID. Check `fdroid build --help` and your installed version's documentation. The app build requires the SDK, tools and dependencies expected by its recipe; these commands alone do not set up the build environment. `checkupdates` may change local metadata, so inspect the diff and confirm it selected the intended release. `--test` keeps test output under the temporary directory; it is not a production publication.

Without `--server`, a local build is not the same as F-Droid's isolated build-server VM, and recipe steps intended to run with `sudo` on that server can be skipped. Use the documented build-server setup or the appropriate CI when those details matter.

Investigate warnings, source scan findings, build failures and binary scan results. A successful Gradle build, `readmeta`, lint, a source scan, a test build and upstream-binary verification prove different things. If `Binaries` is configured, confirm the reference was fetched and that the actual signature-copy comparison and signer check succeeded. Do not use `--force`, `--skip-scan` or broad `scanignore` rules to conceal problems. An exception needs a narrow reason and reviewer agreement.

In the fork's GitLab CI, inspect all jobs and the Reports tab for the **current commit**. Fix errors and either fix or substantiate warnings. If GitLab asks for a phone number or payment card to run the F-Droid FOSS CI, the current MR checklist says to leave a note so maintainers can trigger the pipeline rather than supplying those details. Check the live checklist in case that guidance changes.

If you cannot run a local build, say exactly what you did run and rely on the maintainer-approved CI route for the missing checks. Do not report an unrun build or a stale pipeline as passed.

## 11. Make review easy

The current MR template asks what the app does and why it adds value compared with similar apps. Provide screenshots and a concise, factual summary. You can adapt this:

```text
App:
Application ID:
What it does and who it helps:
Useful difference from similar apps:
Source repository and issue tracker:
Licence:
Release tag, full commit, versionName and versionCode:
Build system, release flavour and prerequisites:
Applicable Anti-Features and unusual permissions:
Signing route and reproducibility result, or why it is not enabled:
Tests performed, device/Android version and remaining limitations:
Related RFP or previous request:
```

Do not tick a checklist item until it is true. Respond in the existing MR or RFP, link evidence and keep the submitted branch current. A reasonable response to a failure identifies the failing job, exact revision, relevant log lines and the proposed fix. Retest after the change. A previously passing pipeline applies to its old commit.

Reviewers may inspect source licences, dependencies, app behaviour, metadata, update detection and installability. Their initial review and automated checks do not promise that every later update receives an exhaustive manual audit.

## 12. Common blockers and what to do

These are recurring problems documented in F-Droid policy, review checklists and community discussions. They are not a statistical ranking of rejection reasons.

| Symptom | Likely cause | Next action |
| --- | --- | --- |
| Submission stalls at policy review | Missing full licence, unclear asset rights, proprietary dependency or unavailable source. | Publish the source and full licence, account for assets and dependencies, and remove or replace the incompatible part in the FOSS build. |
| App is too new or looks like a duplicate | Insufficient development history, no proven users, little unique value or a renamed fork. | Provide the policy's relevant exception evidence; explain substantive value, identity and fork changes. |
| Review requests store metadata | `en-US` text missing, wrong folder or metadata absent from release commit. | Add the required summary and full description upstream, include real graphics and a version-code changelog, then tag a new release if needed. |
| Build works locally but fails in F-Droid | Wrong JDK/SDK/NDK, private dependency, undeclared step, unavailable binary or different environment. | Read the first meaningful error and build log; align the documented and recipe toolchains, source and dependencies. |
| Scanner reports proprietary code or binaries | A real incompatible library, an unnecessary bundled binary, a stub or a false positive. | Identify its origin and whether it is built or shipped. Remove or replace it where needed; explain a genuine exception narrowly. |
| Reproducible verification fails after compilation | Wrong release/flavour/ABI, reference URL, signer, toolchain, ZIP alignment, generated files or embedded revision. | Compare the exact reference and rebuild using the official reproducibility guide and diff tools. Fix the cause and rerun the actual comparison. |
| Updates do not appear | Tag not pushed or filtered, version code not increased, version values cannot be parsed, recipe update pending or build failed. | Check the tag, `UpdateCheckMode`, `UpdateCheckData`, current version, build log and F-Droid Monitor in that order. |
| Cannot update an installed app | Same application ID but incompatible signing certificate, or a lower/equal version code. | Verify signer and code for both APKs. Plan a supported migration and data backup; do not casually advise uninstalling. |
| MR has passed CI but is not merged | Volunteer review, unresolved discussion, a policy question or a reviewer-requested change remains. | Check open threads and provide missing evidence. Passing CI is necessary evidence, not approval. |
| MR is merged but the app is absent | Production build, signing, index publication or client refresh is still pending or has failed. | Check `fdroiddata`, build logs and [F-Droid Monitor](https://monitor.f-droid.org/), then refresh the client and check compatibility and filters. |
| App listing is missing changes | Text/images are not in the built release, recipe-level text overrides upstream, no relevant changelog, or the client filters the app. | Check the exact source revision, metadata precedence, locale, client filters and current index. |
| GitLab CI access is blocked | Host account or runner restrictions. | Follow the current MR template's instruction for maintainer-triggered CI and describe the blocker in the same MR. |

Examples from the community illustrate the distinction between these problems: a [new submission needed a complete licence and Fastlane files](https://forum.f-droid.org/t/app-submission-sheen-notes/34716); a [build used the wrong Java version](https://forum.f-droid.org/t/new-app-pipeline-failed-with-bad-java-version/16595); and [one reproducible-build discussion](https://forum.f-droid.org/t/wolf-gallery-offline-encrypted-gallery-looking-for-a-packaging-volunteer/34459) identified version-control information and toolchain differences. These are examples, not evidence that every failure has the same cause.

## 13. From merge to publication

After an MR is merged:

1. Confirm the merged `fdroiddata` recipe references the intended version and revision.
2. Watch the official build and investigate a failure. The [developer FAQ](https://f-droid.org/en/docs/FAQ_-_App_Developers/) links to update and build monitoring.
3. Wait for successful signing, repository index publication and website/client refresh. Volunteer review and production processing have no guaranteed deadline.
4. Open the app's actual catalogue page. Check the suggested version, source link, description, screenshots, permissions and any Anti-Features.
5. Install on a suitable test device through the F-Droid client. Check the package, version, signing identity, main workflow and update from the prior release where relevant.
6. Only then announce availability or add an [F-Droid badge](https://f-droid.org/en/docs/Badges/) linking to the real catalogue page.

A catalogue page can show more than one version or ABI. A device may not see a build because of SDK, ABI, device-feature, repository, client-filter or Anti-Feature settings. Distinguish that from the app never having been published.

## 14. Maintain releases and users

For each release:

1. Review new dependencies, assets, network services, permissions and policy implications.
2. Increase the version code, update the source metadata and changelog, tag and push the exact commit, and publish a matching reference APK if using one.
3. Repeat relevant functionality, upgrade and reproducibility checks.
4. Confirm the update checker found the intended tag and version. If automation is disabled or fails, coordinate a focused `fdroiddata` update with maintainers.
5. Watch the production build and catalogue entry. Respond to user issues and accurately document known limitations.

When source hosting, build requirements or signing changes, notify maintainers early. A source update that adds a proprietary library, loses necessary source or otherwise breaches policy may stop further builds or result in removal. If a reviewer rejects an app, fix the issue and request re-review. The policy also describes an appeal route and the option of a separate repository.

Do not ask users to clear data or uninstall as a routine troubleshooting step: those actions can remove private app data. Explain data-preserving options and let users decide.

## 15. Android developer verification is separate from F-Droid review

This area is changing. As of the research date, Google's published timeline says its developer-verification rollout began on **30 September 2026 for participating stores in Brazil, Indonesia, Singapore and Thailand**, with wider rollout planned from 2027 on certified devices. Google's FAQ says the September deadline does not yet apply to other stores or direct sideloading. Check [Google's current timeline and FAQ](https://developer.android.com/developer-verification) for the latest scope.

This is an Android installation/distribution issue, **not an extra F-Droid inclusion criterion**. Google's [open-source repository registration guide](https://developer.android.com/developer-verification/guides/open-source-app-registration) describes registering package names and signing certificates, including an F-Droid-signed APK where applicable. It also describes an advanced installation flow for unregistered developers. Requirements, availability, costs, identity implications and signer handling may change; review the current official instructions and choose deliberately. Do not register or disclose identity merely because this guide mentions the program.

## 16. What ten heavily downloaded apps show

**Selection and limitation:** the [F-Droid Top Downloads](https://grote.gitlab.io/fdroid-metrics-distilled/) dashboard listed the following first ten on 31 October 2026, for the one-year period ending with its 26 October 2026 data. It is a public, independently presented view of F-Droid download data. Downloads include updates and repeat downloads; they are not unique users, ratings, quality scores or proof that these are objectively the “best” apps. The ordering will change.

Each link below goes to the actual F-Droid catalogue, its public upstream and its `fdroiddata` recipe on GitLab. Upstream does not have to be hosted on GitLab.

| Rank | App and upstream | GitLab build recipe | Useful observation |
| --- | --- | --- | --- |
| 1 | [F-Droid](https://f-droid.org/packages/org.fdroid.fdroid/) · [source](https://gitlab.com/fdroid/fdroidclient) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.fdroid.fdroid.yml) | The client has release history, public issue tracking and screenshots; its recipe uses manual/static update handling. |
| 2 | [Termux](https://f-droid.org/packages/com.termux/) · [source](https://github.com/termux/termux-app) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.termux.yml) | Its description explains the initial package download; the recipe contains custom native-build handling. |
| 3 | [Aurora Store](https://f-droid.org/packages/com.aurora.store/) · [source](https://gitlab.com/AuroraOSS/AuroraStore) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.aurora.store.yml) | Its listing discloses non-free network dependence and tracking; the recipe selects a `vanilla` flavour and manual updates. |
| 4 | [Fennec F-Droid](https://f-droid.org/packages/org.mozilla.fennec_fdroid/) · [source](https://hg.mozilla.org/mozilla-central/file/tip/mobile/android) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.mozilla.fennec_fdroid.yml) | The catalogue explains removed proprietary components and a remaining tracking warning; its separate build repository and recipe are more complex. |
| 5 | [DAVx⁵](https://f-droid.org/packages/at.bitfire.davdroid/) · [source](https://github.com/bitfireAT/davx5-ose) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/at.bitfire.davdroid.yml) | Its recipe uses an open-source flavour, submodules and custom version extraction. |
| 6 | [Thunderbird](https://f-droid.org/packages/net.thunderbird.android/) · [source](https://github.com/thunderbird/thunderbird-android) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/net.thunderbird.android.yml) | The recipe notes explain its `foss` flavour and why its update process is manual. |
| 7 | [Proton VPN, F-Droid-signed build](https://f-droid.org/packages/ch.protonvpn.android/) · [source](https://github.com/ProtonVPN/android-app) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/ch.protonvpn.android.yml) | Its listing distinguishes the F-Droid-signed version from a separate reproducible, upstream-signed version. |
| 8 | [PipePipe](https://f-droid.org/packages/InfinityLoop1309.NewPipeEnhanced/) · [source](https://github.com/InfinityLoop1308/PipePipe) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/InfinityLoop1309.NewPipeEnhanced.yml) | The listing discloses a non-free network-service warning; the recipe shows signer verification and ABI-specific version handling. |
| 9 | [Organic Maps](https://f-droid.org/packages/app.organicmaps/) · [source](https://github.com/organicmaps/organicmaps) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/app.organicmaps.yml) | Its recipe shows native build inputs and a per-build warning for non-free map assets. |
| 10 | [Librera Reader](https://f-droid.org/packages/com.foobnix.pro.pdf.reader/) · [source](https://github.com/foobnix/LibreraReader) | [recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.foobnix.pro.pdf.reader.yml) | Its description explains differences in the F-Droid variant; its recipe documents native components and scanner context. |

**What was actually common in the ten recipes inspected:** each identifies a licence, source and issue tracker; uses a public Git source repository for the build; and records versioned builds. Their catalogue pages give users descriptions and link to their build metadata. This is a dated observation, not a promise that all apps use identical metadata, signing or release processes.

**What was not common:** automatic versus manual updates, upstream versus F-Droid signing, absence of Anti-Features, a single build flavour, and simple build recipes. The practical lesson is to make your own app's source, behaviour, build and maintenance easy to inspect, rather than copying another app's exceptions or complexity.

## Final checklist

### Before submission

- [ ] Comparable apps, relevant integration source and known issues were reviewed; decisions and rejected alternatives are recorded.
- [ ] A risk/evidence register covers dependency variants, actual user workflows, failure/recovery states and platform boundaries. Every result names its exact tested artifact and environment.
- [ ] Confirmed release-blocking defects are fixed and retested; unexplained flakes, external defects and inaccessible-device tests remain explicitly identified.
- [ ] The real source is public, current and buildable; the full licence, attribution and asset rights are clear.
- [ ] The app is useful, maintained, functional and meets the current quality and development-history guidance, or an applicable exception is explained.
- [ ] Application ID and branding are legitimate; fork changes and author non-opposition are documented where applicable.
- [ ] Runtime and build dependencies have been reviewed. The F-Droid flavour excludes incompatible proprietary code and can build without secrets.
- [ ] Network behaviour, permissions, external requirements, executable downloads and Anti-Features are accurately described.
- [ ] Signing is chosen deliberately; the exact release is tagged and the full source commit is known.
- [ ] The release flavour has been tested, including a data-preserving upgrade where applicable.
- [ ] If the app uses Shizuku, the required service capabilities, compatible forks, companion requirements and tested build variants are documented; untested combinations are identified.
- [ ] `en-US` summary and full description are upstream; icon, screenshots and version-code changelog are prepared.
- [ ] The recipe uses the correct ID, source, licence, version, flavour, full commit and update settings. Any upstream signing fingerprint and binary URL are real.
- [ ] Available lint, scan, build, CI and reproducibility checks were performed or their exact blockers are recorded.

### After submission

- [ ] Existing RFPs are linked, the current MR checklist is filled honestly and reviewer questions are answered.
- [ ] The current MR commit has appropriate CI results; remaining warnings and limitations are explained.
- [ ] Merge, production build, signing and catalogue publication have each been checked separately.
- [ ] The published APK, listing and practical install/update path have been verified.
- [ ] Future tags, updates, policy changes and user reports have an owner.

## References

Start with the live sources. If this guide and an official current checklist disagree, follow the current checklist and ask maintainers about ambiguity.

- [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) and [Anti-Features](https://f-droid.org/en/docs/Anti-Features/)
- [Submitting to F-Droid: Quick Start](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [fdroiddata contributor guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md), [new-app MR template](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/Default.md) and [build templates](https://gitlab.com/fdroid/fdroiddata/-/tree/master/templates)
- [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/) and [descriptions, graphics and screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- [Reproducible Builds](https://f-droid.org/en/docs/Reproducible_Builds/), [developer FAQ](https://f-droid.org/en/docs/FAQ_-_App_Developers/) and [F-Droid Monitor](https://monitor.f-droid.org/)
- [F-Droid Forum](https://forum.f-droid.org/), [Requests for Packaging](https://gitlab.com/fdroid/rfp/-/issues) and [community contact options](https://f-droid.org/en/about/)
- [F-Droid Top Downloads](https://grote.gitlab.io/fdroid-metrics-distilled/) and the [forum discussion of download statistics](https://forum.f-droid.org/t/does-f-droid-provide-any-sort-of-stats/4346?page=6)
- [Android developer verification](https://developer.android.com/developer-verification), [open-source repository registration](https://developer.android.com/developer-verification/guides/open-source-app-registration), [APK Analyzer](https://developer.android.com/tools/apkanalyzer) and [apksigner](https://developer.android.com/tools/apksigner)
