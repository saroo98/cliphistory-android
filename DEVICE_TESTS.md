# Pixel 8 Pro / Android 17 acceptance checks

**Current evidence: see `VERIFICATION.md`.** This is the broader acceptance checklist, including tests beyond this release's device coverage. Do not infer every item passed from a successful build or connection test.

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

Use only a synthetic marked-sensitive clip, not an actual password, to test sensitive filtering. The app checks `android.content.extra.IS_SENSITIVE`; an app that does not mark its own secret cannot be recognised reliably by that flag. Oversized UTF-8 text should increment the skipped counter rather than appearing truncated.

Revoke ClipHistory's Shizuku permission and confirm that recording stops or access is denied. Re-authorise it, reopen the app and check a new copy. Verify that other unprivileged apps cannot bind directly to the private history interface.

History must not be exposed while locked. The tile should require unlocking. Screenshots are blocked by default; enabling capture must take effect in the Activity, each sheet and the tile dialog, including recreation and locking. Recents hiding must remain independent. Screen recording and actual Recents snapshot checks are separate from window-flag assertions. Diagnostics must contain no saved text.

## Longer-run evidence

Before treating the app as dependable, repeat normal copy/paste use over several hours, including device idle periods and switching networks/apps. Check recorded counts/content, reconnect behavior, memory use and battery impact on the actual phone. There is no benchmarked battery or memory claim in this delivery.

Excessively fast clipboard changes may overwrite intermediate system clipboard values before a listener can retrieve them. Record such misses honestly. The helper has a bounded queue and reports overload drops; a historical drop must not be concealed by a generic “active” message.

## Exit criteria

Record the APK hash, app version, Android build/security patch, Shizuku version and each result. Mark PASS only after observing the expected behavior. Leave unexecuted cases NOT RUN. An unresolved Android compile, connection, data-loss, privacy or background-capture failure means device acceptance has not passed.

## Automated isolated regression suites

Build the validation variant described in `BUILDING.md` for a physical phone. Grant that separate application Shizuku access first.

```sh
adb shell am instrument -w -e suite live app.cliphistory.validation.test/app.cliphistory.UiSmokeInstrumentation
```

The live suite uses the test APK's separate clipboard producer, verifies real capture and restores the original clipboard as an opaque object. It clears only the validation app's synthetic history. The normal `app.cliphistory` package is not its target. The instrumented runner refuses synthetic seeding on a physical production package.

On a disposable emulator with Shizuku stopped, build the normal debug app and run `-e suite regression`, `-e suite layout` or `-e suite capacity` against `app.cliphistory.test/app.cliphistory.UiSmokeInstrumentation`. Omit `suite` for the UI checks and synthetic screenshots. Layout requires an open software keyboard. Capacity uses 500 entries of 64 KiB, then restores small synthetic history.

Additional validation modes are `settings`, `privacy`, `recovery`, `onboarding`, `customization`, `paused-connection`, `tile` and `tile-performance`. The paused connection check performs no clipboard writes. On a personal phone, tile performance opens/closes only; native copy tests belong on disposable emulators. `tools/test-tile-timing.py` performs ten 60-second idle trials and two 120-second trials, with private bounded video/traces. It requires the isolated validation variant and a stopped validation recorder. Do not publish raw phone traces or full notification-shade images. See `docs/release-1.3/TILE_TIMING.md` for measured stages and limits.

Stop the helper before fixture seeding: the helper is the single writer and must not hold different in-memory history while tests replace its files. The test APK and fixtures are never distributed in the production APK.
