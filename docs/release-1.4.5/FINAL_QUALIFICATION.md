# ClipHistory 1.4.5 final candidate qualification

Local qualification on 1 November 2026, version code 15. This candidate includes the earlier service-discovery and manager-choice changes plus the package-update recovery fix below. It is not a public release commit or evidence of F-Droid acceptance. The [initial qualification](QUALIFICATION.md) retains the wider UI, fork and lifecycle matrix with its own exact artifact hashes.

## Confirmed defect and narrow fix

A signed upgrade from published 1.4.4 to the initial 1.4.5 candidate preserved history and settings on Android 13, but displayed **Recorder stopped by you**. Android's exit record was `USER_REQUESTED`, with the exact description `stop app.cliphistory due to installPackageLI`. Three reproductions and the saved exit record established the cause.

[Android documents](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_PACKAGE_UPDATED) that before Android 14, package updates used `REASON_USER_REQUESTED`. The shared recovery policy now excludes only the verified installer description for the exact owning package. Both client startup and the shell owner-stop guard use it. Real user stops, another package's description, missing descriptions and unknown OEM descriptions remain blocked. No time-based exemption, permission bypass, extra background polling or new dependency was added.

The targeted regression failed before the change and passed afterward. Signed upgrades also check that a genuine Force stop prevents capture, remains stopped across reinstallation, and resumes only after explicit Start recorder.

## Artifact identities

The local working tree is based on `cabdab5`. The private source snapshot and per-file manifest are retained with the test logs. Releasing requires an immutable commit and rebuilding its exact Git provenance.

| Artifact | SHA-256 |
| --- | --- |
| Signed 1.4.5 APK | `3713d2a2a9c9194cf8c146bb295f8992b51672356927d924d78c50a625d24ba9` |
| Validation app | `b4bcc4b330b053337d38184b44642f9b7b758caaaf64532e0151c2379aa06701` |
| Validation tests | `438359e5461150bfdae56960192960e934c6885b7bdd0d500ae9f59e778d29ac` |
| Independent Windows and Linux unsigned APK | `43ae0838a23c06bfa89511a3207bc012f855b9cefeb66d84469f31ffc3718a44` |
| Original signing certificate | `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82` |
| Actual published 1.4.4 upgrade baseline | `0f2e159208d3757e1180d6baf935721908acd9e708826e132f1f2d6135c826ae` |

The final runtime build passed 80 JVM tests, release lint with zero errors and 24 disclosed warnings, and the 74-check source audit. Windows and independent Linux unsigned bytes match. `apksigcopier compare --unsigned` passed. Signature and 16 KiB alignment checks passed. F-Droid source scanning found zero fatal findings with no custom exclusions; APK scanning passed. These local checks do not substitute for a published-commit recipe build and current CI.

## Pixel 8 Pro

The signed APK above was installed in place on Android 17. Its installed hash matched. The service manager was thedjchi Shizuku 13.7 in shell mode.

- The observed history count, limit and ten inspected settings matched before and after. The recorder remained active. This is not a byte-for-byte export of personal history.
- The separate validation app passed 20 real-device live assertions, including actual marked capture, interleaved recopy without duplication or timer extension, one-minute expiry in both encrypted snapshots, offline expiry, boot identity and isolated policy updates.
- Seven additional synthetic-memory assertions passed for queued expiry, committed sensitivity and expired Undo.
- Because the two local candidates share version code 15, the existing helper was explicitly stopped and restarted through the production app. Its new PID and shell UID were checked. The production connection test then passed using the new helper, with the eligible original clipboard and pause state restored.
- Production diagnostics reported AES-256-GCM, StrongBox and a passed expiry clock. Temporary validation authorization was revoked afterward. No personal history export, history wipe, phone reboot or phone-wide warning change was used.

## Final emulator checks

The older-Android signed tests use the actual published 1.4.4 APK and the signed candidate above. Each checks two exact synthetic texts, A/B/A/B deduplication, the same app UID, preserved inspected settings/encryption metadata, a new external copy after upgrading, genuine Force stop, reinstallation while stopped and explicit restart.

Signed update, preserved-data and deliberate-stop checks passed on API 30, 31, 32 and 33. API 32's resumed phase used native UI observation after the instrumentation metadata reader failed to see the open menu. It compared the nine settings actually available on that OS; the independent Recents switch is intentionally available only from API 33. The initial real consent and A/B/A/B phase was retained rather than presented as newly repeated consent.

The Android 12L fixture initially mistook history hidden by the optional **Background reliability** sheet for missing capture. Dismissing the observed sheet revealed the exact saved entry. A separate disappearing **Connect** button was a UI-snapshot race. Those fixture corrections did not change app behavior or remove the capture assertion.

The final Android 16.1 investigation preserved a System UI boot ANR: `executing service com.android.systemui/.SystemUIService, waited 20594ms` at 21:38:04.594. The first production and validation ClipHistory processes started afterward, at 21:38:12.267 and 21:38:14.079. The ANR dialog held focus over the synthetic producer. This explains that run's blocked copy; it does not establish the cause of the older initial-candidate `Process crashed` report. The harness now preserves full UTF-8 logs, windows, activities and exit records, and the remaining emulator runs are sequential with a recorded boot preflight. System error reporting is not disabled.

The final matrix passed on eight emulator targets. Exact fingerprints, manager versions, hashes, assertion counts and timings are in [checks.json](checks.json). Every row uses the validation APK and test APK identified above.

| Target | Final checks |
| --- | --- |
| Android 11 / API 30 | Live capture 36; encryption 99; signed update and deliberate-stop regression. |
| Android 12 / API 31 | Live capture 36; encryption 99; signed update and deliberate-stop regression. |
| Android 12L / API 32 | Live capture 36; encryption 99; signed update and deliberate-stop regression. |
| Android 13 / API 33 | Live capture 36; encryption 99; signed update and deliberate-stop regression. |
| Android 14 / API 34 | Live capture 36; encryption 99. |
| Android 15 / API 35 | Live capture 36; encryption 99. |
| Android 16.1 / API 36.1 | Live capture 36; encryption 99; sensitive retention 40, including a real one-minute expiry. |
| Android 17 / API 37 | Live capture 36; encryption 99; Settings 23; native tile 75, using Shizako's actual F-Droid CI build. |

The final API 36.1 and 37 boot preflights observed no System UI ANR dialog, and no dialog dismissal was needed. API 37 ran a sole shell-UID `shizako_server` with the other managers disabled. The earlier broader UI, permission and lifecycle coverage remains tied to the initial candidate in its separate record. These are targeted tests, not every test on every OS or an overnight reliability claim.

## Remaining external limits

HyperOS 3 still needs the reporter's exact firmware and service variant retest. Official Shizuku 13.6 on Android 17 has a reproduced background Binder-delivery limitation after server restart; opening ClipHistory reconnects, and the tested Shizako F-Droid build recovers automatically. Fresh Shevery approval on Android 17 needs its manager's manual authorization workaround. These are not represented as fixed upstream.

Natural overnight or multi-day OEM operation, spoken TalkBack navigation, root/device-owner modes, work profiles, arbitrary forks and independent security review are not certified. The supported service contract remains Android 11+, primary profile, Shizuku API 13+ with upstream UserService support and shell UID 2000. No privacy, user-stop or identity check was weakened to expand the compatibility claim.
