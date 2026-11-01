# Pixel 8 Pro / Android 17 acceptance checks

**Current evidence: see `VERIFICATION.md`.** This is the broader acceptance checklist, including tests beyond this release's device coverage. Do not infer every item passed from a successful build or connection test.

The 1.4.4 candidate has focused emulator and remote physical-device evidence in [release qualification](docs/release-1.4.4/RELEASE.md). Earlier tests are documented separately for [1.4.3](docs/release-1.4.3/RELEASE.md) and [1.4.2](docs/release-1.4.2/RELEASE.md); their results do not by themselves qualify the newer APK.

## Earlier 1.4.5 fork candidates

The following checks preceded the latest manager chooser and privacy descriptions. They are retained as scoped integration history, not as tests of the current APK hash. Current artifact identities and reruns are in [1.4.5 qualification](docs/release-1.4.5/QUALIFICATION.md).

- Official Shizuku 13.6.0, shell mode on an Android 14 emulator: the 1.4.5 validation build passed the 34-assertion live capture suite.
- Shizako zako3.17, upstream GitHub build, shell mode on an Android 14 emulator: the 1.4.5 validation build passed the 34-assertion live capture suite. Version 1.4.4 initially showed a misleading missing-service state with this fork, although manually retrying could connect.
- Porter 0.8.1-rc0 with Porter Compatibility, shell mode on an Android 17 emulator: the 1.4.5 validation build passed the same live capture suite. The signed release also connected and captured synthetic text.
- Shevery 14.6.1, upstream GitHub build, shell mode on an Android 17 emulator: the 1.4.5 validation build passed the live capture suite with previously granted access. In a fresh-authorization test, the prompt did not remain visible, so access was granted manually in Shevery.
- Shizuku+ 13.6.0.r2551, upstream native-package build, shell mode on an Android 17 emulator: the 1.4.5 validation build passed the live capture suite without a companion installed. The fixture already had an authorization from earlier manager tests, so this does not establish the fresh-approval flow.
- An in-place signed 1.4.4 to 1.4.5 update on a Pixel 8 Pro with thedjchi's Shizuku 13.7.0 fork preserved the observed history count and settings, and the recorder reported Listening. No clipboard text was read or changed for this phone observation.

Those earlier checks did not verify Shizako's F-Droid variant. The subsequent [current-candidate qualification](docs/release-1.4.5/QUALIFICATION.md) adds its actual CI-built `zako3.17-f` artifact, fresh permission approval without the official launcher, live capture, sensitive retention and Settings checks. It also records the latest Pixel and Android matrix results. Other unlisted forks, root and device-owner modes remain unverified or outside the supported contract. The maintainer's reported symptom did not include enough detail to establish whether it was identical to the reproduced missing-service state. The 1.4.5 source and release have not been submitted to F-Droid.

Use harmless test text, not passwords or private messages. Do not clear or alter valuable history to run a test without first deciding that it is safe to do so. The app intentionally has no cloud backup/export in this version.

## First gate: installation and connection

Install a successfully compiled APK and leave Gboard as the default keyboard. Start Shizuku in wireless-debugging mode, open ClipHistory and tap Connect. Allow its Shizuku permission.

Diagnostics should show daemon UID 2000, the actual Android API level, listener registration and verified private storage. A failed connection is not a partial pass. Copy the diagnostic report or keep the build error; do not send raw clipboard data.

Run the app's Connection test after reading its confirmation. It replaces the current Android clipboard with a unique harmless string, exercises the real callback and performs a durable write/read of unchanged history. PASS must appear in Diagnostics. Confirm the history count did not increase and a full history did not lose an item. A late or repeated test callback is meant to be filtered too.

If the Android hidden API layout, shell permission or SELinux descriptor access fails, stop at this gate. Do not enable Accessibility, disable SELinux or root the device to disguise the failure.

## Ordinary background capture

With recording active, leave ClipHistory and copy distinct harmless texts from Chrome, a notes/editor app, and the messaging apps you normally use. Do not reopen the history app between copies. Include a multiline text and Unicode such as `کوردی فارسی 中文 🙂`.

Reopen history. Check the contents and newest-first order. Tap an older item; the app should return to the previous screen. Paste through Gboard and compare the complete text, including spacing/newlines. Search should return matching saved texts without changing them.

Swiping an app out of Recents and killing its ordinary process are different from Force stop. Test Recents removal first. For an explicit process-death check, use an already-authorised ADB connection from your own PC, leave the app in the background, and run:

```sh
adb shell am kill app.cliphistory
```

Do not mistake that command's completion for proof that the process was killed; inspect processes where possible. Copy more test text elsewhere, then reopen history. Recording should have continued in the shell helper. Force stop is not a supported promise of continuous recording; do not use it as the normal way to pause the recorder.

## Rolling limit

On disposable test history with limit 100, copy numbered strings `CH-001` through `CH-101` at a human pace while the history UI is closed. There should be 100 entries: `CH-101` at the top and `CH-002` at the bottom, with `CH-001` no longer in the current list. Lower the limit to 20 and confirm the oldest entries are removed. Raise it again and confirm that previously removed entries do not reappear.

Copy `A`, `A` consecutively: one entry. With default unique-text handling, `A → B → A → B` must leave exactly `B, A`. Select consecutive-only handling to obtain four entries for that sequence. Switching back must combine duplicate text, preserve pause/limit and clean both recovery snapshots. Compare complete text, including case, whitespace and Unicode.

## Recovery and pause

Pause the recorder from the menu, copy a harmless secret-like canary elsewhere, and confirm it is not archived. Restart Shizuku/reconnect and confirm the paused setting persists. Resume recording and confirm new copies are saved.

Stop Shizuku while the app is closed. Copy a distinct text. Restart Shizuku and reopen ClipHistory. The missing interval must not be falsely reported as captured; old committed history should still be available. New copies after reconnection should be saved.

Reboot a disposable test device. Before restarting Shizuku, history must not claim active recording or expose text before unlocking. With resumption enabled, unlock and start Shizuku; recovery should reconnect when permitted. With resumption Off, Binder delivery must not start the recorder until explicit activation. A previously paused history must remain paused. Non-root Shizuku restart remains a manual requirement.

For disposable data only, also test Android's Clear storage and uninstall/reinstall behavior. Previously cleared history must not be resurrected from a surviving helper's memory. The implementation checks replacement file identities and rejects unlinked backing files; this platform behavior still requires device validation.

## Privacy, permission and size boundaries

Use only a synthetic marked-sensitive clip, not an actual password, to test sensitive filtering and opt-in capture. The app checks `android.content.extra.IS_SENSITIVE`; an app that does not mark its own secret cannot be recognised reliably by that flag. Test capture off, capture on, default five-minute expiry, custom duration, confirmed indefinite retention and disable-capture cleanup. Oversized UTF-8 text should increment the skipped counter rather than appearing truncated.

Revoke ClipHistory's Shizuku permission and confirm that recording stops or access is denied. Re-authorise it, reopen the app and check a new copy. Verify that other unprivileged apps cannot bind directly to the private history interface.

History must not be exposed while locked. The tile should require unlocking. Screenshots are blocked by default; enabling capture must take effect in the Activity, each sheet and the tile dialog, including recreation and locking. Recents hiding must remain independent. Screen recording and actual Recents snapshot checks are separate from window-flag assertions. Diagnostics must contain no saved text.

## Longer-run evidence

Before treating the app as dependable, repeat normal copy/paste use over several hours, including device idle periods and switching networks/apps. Check recorded counts/content, reconnect behavior, memory use and battery impact on the actual phone. There is no benchmarked battery or memory claim in this delivery.

Excessively fast clipboard changes may overwrite intermediate system clipboard values before a listener can retrieve them. Record such misses honestly. The helper has a bounded queue and reports overload drops; a historical drop must not be concealed by a generic “active” message.

## Exit criteria

Record the APK hash, app version, Android build/security patch, Shizuku version and each result. Mark PASS only after observing the expected behavior. Leave unexecuted cases NOT RUN. An unresolved Android compile, connection, data-loss, privacy or background-capture failure means device acceptance has not passed.

## Automated isolated regression suites

For fork discovery, install the validation app and test APK on a disposable emulator. With Shizako running as shell and validation access not yet granted, `adb shell am instrument -w -e suite fork-discovery app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation` checks that a live compatible Binder reaches the permission state without an official Shizuku launcher. With Shizako installed but its service stopped, use `-e suite fork-stopped -e expected_manager com.churan.shizako` to check stopped-service detection and the manager shortcut. With both Shizako and Porter installed, `-e suite fork-ambiguous` checks that the shortcut does not choose one silently. These checks do not establish capture until a separate live capture test is run.

Build the validation variant described in `BUILDING.md` for an owned disposable emulator. Grant that separate application Shizuku access first. The `live`, `html-port`, `html-port-live`, history-seeding and corruption suites refuse physical devices. A separate package does not isolate Android's shared clipboard: synthetic writes can still reach a personal phone's production recorder. Never use these suites on personal history.

```sh
adb shell am instrument -w -e suite live app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
```

The live suite uses the test APK's clipboard producer, verifies real capture and restores the original clipboard as an opaque object on the disposable emulator. Enable **Settings → Recovery → Automatic recovery** before running it: the failed-notification-Stop assertion exercises that optional supervisor. A reused emulator can retain this option disabled by a previous settings suite. It clears only the validation app's synthetic history. The normal `app.cliphistory` package is not its target. For a personal phone, use a signed upgrade/launch check and read-only inspection; do not infer that restoring the clipboard reverses a synthetic capture by another recorder.

On a disposable emulator with Shizuku stopped, build the normal debug app and run `-e suite regression`, `-e suite layout` or `-e suite capacity` against `app.cliphistory.test/app.cliphistory.UiSmokeInstrumentation`. Omit `suite` for the UI checks and synthetic screenshots. Layout requires an open software keyboard. Capacity uses 500 entries of 64 KiB, then restores small synthetic history.

Additional validation modes are `settings`, `privacy`, `recovery`, `onboarding`, `customization`, `paused-connection`, `tile` and `tile-performance`. The paused connection check performs no clipboard writes. On a personal phone, tile performance opens/closes only; native copy tests belong on disposable emulators. `tools/test-tile-timing.py` performs ten 60-second idle trials and two 120-second trials, with private bounded video/traces. It requires the isolated validation variant and a stopped validation recorder. Do not publish raw phone traces or full notification-shade images. See `docs/release-1.3/TILE_TIMING.md` for measured stages and limits.

Stop the helper before fixture seeding: the helper is the single writer and must not hold different in-memory history while tests replace its files. The test APK and fixtures are never distributed in the production APK.

## UI audit regression additions (1.3.1)

Freshly build/install the validation app and test APK. On an owned disposable emulator, run the existing instrumentation command with `-e suite audit-navigation` for nested Back/scroll/recreation and real Settings copy actions, or `-e suite audit-diagnostics` for readable presentation and exact raw report codes. Diagnostic presentation tests do not imply a live Shizuku result.

The `accessibility` suite checks actual heading/pane/disclosure controls, the history row's custom full-text action through its accessibility node, unchanged clipboard contents while viewing, and transient feedback lifetime. For its extended-timeout case, preserve the emulator's original `accessibility_non_interactive_ui_timeout_ms`, set it to `5000` through `adb shell settings put secure`, run the suite, then restore the original value (delete the key if originally absent). Do not change a personal phone's accessibility preferences for automated tests. These node/lifetime checks do not certify spoken TalkBack traversal.

The test-only producer also supports action `app.cliphistory.test.READ_HASH`. Launch its Activity in the foreground on a synthetic test emulator; the visible SHA-256 allows exact UTF-8 clipboard verification without displaying or changing clipboard text. This Activity is absent from the production APK.

The `quick-errors` suite requires a disposable emulator. It replaces both isolated validation history slots with invalid synthetic payloads, checks the actual floating error panel, restores valid data before activating Retry, and verifies that Open app closes the error panel and returns focus to Main. A separate fresh Main launch exercises its failed-read Diagnostics, system Back, and Try again controls against the same fault fixture. Its `finally` block restores the valid history and the fixture's screenshot protection. It does not simulate a ClipboardManager write failure or run on a personal phone.

For error-state captures, pass `-e theme light` or `-e theme dark` to that suite. It restores the isolated fixture's Light appearance afterward. Captures under the target's `files/ui-evidence/` are actual Activity decor drawings, not full-system screenshot attestations.

The `tile` suite also changes the existing tile between Open full history and Quick copy. It checks the published SystemUI subtitle and actual destination in each mode, including returning an existing Settings Activity to History. Closing Quick copy is checked separately from the Main Activity underneath it. For signed-production acceptance, repeat the mode change through the real Settings radio controls and exercise the cached tile before reopening the shade; the published direct-launch intent must match the new mode.

The `command-errors` suite requires an owned disposable emulator, the isolated validation package and an authorized Shizuku server running as shell UID 2000. Run it with `-e suite command-errors -e theme light` or `dark`. It connects a paused real recorder, invokes Main's command entry with a real invalid-capacity RPC, and activates the operation-failure sheet's View diagnostics and Done accessibility controls. The harness bypasses the normal capacity field's input validation; it does not fabricate a daemon response or simulate storage exhaustion, IPC death, or a normal user input. It verifies unchanged snapshot/generation/count/capacity and stops only the validation recorder in `finally`. It performs no clipboard writes. Its captured PNGs are Activity decor drawings, not full-system screenshots.

The diagnostic audit additionally checks initial, updated and reported unavailable duplicate policy against the existing Not confirmed text. The `quick-errors` suite verifies the same value in the actual failed-read Diagnostics page. The clipboard-write-exception message now names the existing entry retry and Close paths; these suites do not induce an Android ClipboardManager write exception.

## HTML UI port checks

On the disposable validation emulator run `-e suite html-port` for Settings controls and summaries, native choice dialogs, Help disclosures and restoration, diagnostic disclosure, late-text search, quick-copy loading/empty/error states and selectable copy recovery. Copy-denial recovery is a synthetic presentation check, not an induced Android clipboard-permission failure.

With shell-mode Shizuku authorized on that emulator, run `-e suite html-port-live -e theme light` and `dark`. It uses the real paused helper and verifies deletion, pending Undo across navigation and rotation, full-capacity refusal without eviction, original ID/time/text restoration, stale-token rejection and Dismiss. It writes only disposable synthetic history. Its issue-only status check exercises the UI metadata-loss boundary, then independently verifies the actual helper token is unchanged; it is not a fabricated IPC failure claim.

Activity/window decor captures in `files/ui-evidence/` are actual native drawings. Full-system screenshots, motion measurements, hardware behavior and spoken TalkBack results require separate evidence. See `docs/HTML_UI_PORT.md` for this build's executed checks and limits.

## Encryption and sensitive retention (1.4)

Use `-e suite encryption` on the owned disposable emulator for Keystore wrapping, modified-record rejection, legacy v1/v2 migration, a partial first key write, mixed plaintext/ciphertext interrupted migration, completed-phase plaintext rejection, missing-key failure and expiry on a cached offline read. It creates isolated test directories and does not overwrite personal data.

`-e suite key-check` is phone-safe on the separate validation package. It creates new test files, reports the observed Keystore security level, and performs no clipboard write, history seeding, deletion of existing history or permission change. It does not certify the signed production upgrade.

`-e suite sensitive-ui` tests saved offline settings in both themes, screenshot controls, full-text expiry, quick-panel expiry and ID-based retry expiry. `-e suite sensitive-live` requires authorized shell-mode Shizuku and tests actual marked capture, classification on unmarked recopy, preserved clipboard flags, saved/skipped counters, purge policy, real native controls, custom-duration bounds, indefinite confirmation and an actual one-minute deadline. These modes refuse physical phones.

History fixtures use the production encryption wrapper after proving that their helper has stopped. Deliberate corrupt payloads remain test-only. No test hook or fixture Activity is included in the release. Local evidence, build labels and coverage limits are in `docs/release-1.4/RELEASE.md`.

The timing driver requires `--expected-version` with the exact validation version, for example `1.4.0-validation`. Run identical hardware, fixture, theme and measurement modes for before/after comparisons. Capture actual frame/video evidence; accessibility-dump timings are not rendering timings.

`-e suite missing-snapshots` verifies recovery with one authenticated copy and fail-closed handling when both history files are empty or missing after a completed migration. It leaves the wrapped key unchanged. The full `encryption` suite includes these cases. `-e suite storage-threading` invalidates caches off the main thread, then exercises loading, search, full text and copy under a main-thread StrictMode policy. This is a focused disk-work check, not proof that StrictMode observes every JNI or Binder operation.

`tools/test-encrypted-upgrade.py` requires the owned API 34 emulator with a stopped validation baseline already installed. The default is plaintext 1.3.4; `--baseline 1.4.0` and `--baseline 1.4.1` stage timed sensitive, indefinite sensitive and ordinary entries using the installed app’s own encryption. It starts the real old shell recorder, enables Automatic recovery through the native Settings control, installs the new APK in place, and compares the full committed state in test memory. Enabling that fixture option is necessary to expect automatic reconnection after the update. Its sanitized report records helper replacement, exact state preservation, authenticated copies, wrapped-key preservation for encrypted baselines, and private file modes. Never run it on a personal phone.

`tools/test-lifecycle.py` uses native UI and the actual process/task registry instead of instrumentation for lifecycle observations. Android starts and finishes instrumentation by force-stopping its target, which would invalidate a recorder-recovery check. The driver waits for acknowledged listener readiness and verifies synthetic producer events against Android's clipboard hash. Destructive modes refuse a physical device and require this task's disposable ClipHistory emulator. A protected Recents task can omit its accessibility label; the driver verifies native Overview dismissal against the task registry.

For a fork, supply its observed `--manager-package` and `--server-process` to the lifecycle driver, for example `com.churan.shizako` and `shizako_server`. Startup uses that manager's installed native starter; this driver is not a universal launcher for forks with different setup mechanisms. Verify the process name and shell UID first. Stop the previous service through its manager and account for native watchdogs before switching. An empty server query is a fixture failure, not proof that app recovery failed.

After `--mode start`, `--mode clear-storage` explicitly clears only this owned emulator's `app.cliphistory.validation` data. It verifies a connected zero-entry fresh store, a replacement key record/helper, and exactly one newly captured synthetic entry. This is a destructive fixture test, never a personal-phone command. Authentication can then be checked with `-e suite stored-status`; instrumentation must run after, not during, lifecycle observation.

`-e suite phone-observe` without a `pause` argument is read-only on production ClipHistory while running from the separate validation package. It exports only count, limit, known recorder/settings state and whitelisted encryption diagnostics. It performs no clipboard writes, personal-history export, screenshot, clearing, pause change or connection test. With explicit `-e pause true` or `false`, it operates the native pause switch and waits for acknowledgement instead; the caller must restore the reported original state in `finally`.

`-e suite phone-core -e allowPhone isolated-synthetic` runs the committed-sensitivity, queued-expiry and Undo regressions in private synthetic memory. `-e suite phone-live -e allowPhone isolated-synthetic -e productionPaused acknowledged` requires the production recorder to have been paused through its acknowledged UI first. It binds a separate `:phonecheck` helper with new encrypted test files, tests actual sensitive capture, interleaved recopy and one-minute expiry, then restores the eligible original system clipboard from foreground process memory. It refuses marked-sensitive, multi-item, URI and intent clipboard checkpoints. Never run it concurrently with another clipboard recorder or the owner copying text. Restore the production pause state and any temporary service authorization afterward, including on failure. A process crash can prevent clipboard restoration; these safeguards do not make a shared clipboard isolated.

`-e suite manager-shortcuts` uses only the validation app. With two or more known managers installed, it checks chooser cancellation, every selected launcher and the corresponding trusted Android battery-settings destination. Outgoing intents are intercepted; separately open one actual system Settings destination on hardware and inspect the chooser in both themes and at large font scale.

The matrix driver requires the exact validation version and records the APK hashes, AVD identity and OS build. Set `--manager-package` for a fork. The requested manager and its installed version do not independently prove it supplies the active Binder; verify only its service is running. Older reports' `shizuku_package` field described the installed official manager, not necessarily the active service. Record the service and distribution separately:

```sh
python tools/test-android-matrix.py --adb /path/to/adb --serial emulator-5596 --apk /path/to/validation.apk --test-apk /path/to/validation-test.apk --expected-version 1.4.5-validation --output reports/current --suite live settings encryption
```

Before cold or idle timing, restore the three-entry ordinary synthetic fixture with the existing `quick-errors` suite. Sensitive-expiry tests intentionally remove their fixture entries and are not a timing baseline. Select the serial from fresh device and AVD identity checks; do not reuse another project's emulator port. Raw video and traces remain private under `.tools/`.

For a signed current-release upgrade, use a fresh disposable emulator with the production package absent. Verify the public baseline APK's hash and certificate, install it, give real service approval and capture two synthetic texts in A/B/A/B order. Handle the observed optional background-setup sheet with **Not now** before asserting visible history. Record the UID, exact synthetic contents and inspected settings. Install the candidate with `adb -s <owned-emulator> install -r <candidate.apk>`, verify its version and unchanged UID, then require retained contents/settings, an active recorder and a new external copy. Repeat after Android Force stop: reopening and reinstalling must preserve the stopped state, and only explicit **Start recorder** should restore capture. Cover API 30, 31, 32 and 33 when qualifying the older-Android update policy. Do not use the historical `test-upgrade.py` legacy-format fixture as evidence of this current signed flow.

On a failed matrix suite, the driver preserves logcat, the crash buffer, windows, activities and target exit records in UTF-8 before stopping. Check the focused window and timestamps before attributing a hidden control or failed copy to the product. Keep only one UI-automation process on each device. A boot-time System UI failure must be recorded and the fixture recovered before testing; never disable ANR reporting to obtain a pass.
