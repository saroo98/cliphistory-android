# ClipHistory 1.4 local qualification

Version **1.4.0 / code 10**, package `app.cliphistory`, implements the approved 8 October encryption plan. Source baseline: `6009e25f548a5f1a45d6cf129d288056e76d5b8f` (1.3.4/code 8). This is local qualification of an uncommitted working tree. No commit, tag, push, public release or F-Droid recipe update has been performed. Earlier 1.3.4 reproducibility and external CI do not qualify this binary.

## Behavior and boundaries

All persisted ordinary and sensitive history uses platform **AES-256-GCM**, fresh 96-bit nonces, 128-bit tags and authenticated application/storage/slot identity. An application-UID Android Keystore key wraps the random data key. Only the wrapped record is stored; the authorized UID-2000 recorder receives the data key through Binder for encryption on its existing serial worker. Both encrypted snapshots are read back before migration completes. Existing framing, fsync, two-copy recovery and privacy controls remain.

Sensitive capture defaults **Off**. Auto-delete defaults **On, 5 minutes**. Source marking and retained exact-text sensitivity determine classification; there is no guessed password/card detection. Recopy cannot extend a current timer. Users can choose 1, 5, 15, 30 or 60 minutes, or a custom 1–1,440 minutes. Disabling auto-delete requires confirmation. Disabling sensitive capture removes saved sensitive history and its Undo.

Timed entries use verified boot identity and elapsed realtime. They become inaccessible at the deadline, after reboot, or if boot identity is unavailable. One scheduled next deadline performs durable cleanup when execution is available. No polling scan, exact alarm, wake lock, new service, database, network permission or runtime dependency was added. Cancelled tasks are removed immediately rather than accumulating until their former deadlines.

The ordinary native copy/search/preview/Quick Settings workflow has no new authentication or interaction step. Three controls appear in the existing Privacy group. History copy, native selected-text copy and quick copy retain Android's sensitive flag. Retry fetches the entry by ID again. Visible expiry, offline cache filtering and Undo reject expired content.

Encryption protects stored files. Text and the data key necessarily exist in trusted process memory. Text copied for another app is plaintext in Android's shared clipboard. Auto-delete removes saved history, **not Android's clipboard**. Suspension can delay physical file cleanup; old storage blocks, screenshots, a compromised OS/recorder and complete valid-file rollback remain outside this protection. Keystore loss does not trigger an automatic reset. An old APK cannot read the new format; do not downgrade an encrypted personal store.

## Necessary implementation adjustments

- AIDL transaction IDs 0–15 remain unchanged; policy is 16 and encrypted attachment is 17. Attachment additionally accepts `bootCount` from the authorized app UID. Real non-root shell attachment could not establish app-attributed Settings boot identity itself. The app reads it off the UI thread and passes it with the storage identity; reboot terminates the server lease. An unavailable value fails closed for timed content.
- First conversion of existing legacy history requires running, authorized Shizuku to verify the old writer's absence/death. A missing provider Binder is not accepted as proof of exclusive ownership. Unverified conversion leaves data intact and retryable; later encrypted history remains readable offline.
- A completed key record with both history slots missing/empty now reports `BOTH_SNAPSHOTS_UNREADABLE`, rather than presenting or writing a false empty history. The defect was reproduced before the fix and the four targeted checks pass afterward. A genuinely new pending store can still initialize normally.
- Native hardware and accessibility copy share `CopyText.onTextContextMenuItem`; an ActionMode callback alone would not cover hardware Ctrl+C. No normal-text interaction change was added.
- Final review found that a queued sensitive event started its timer at processing time. Clipboard packets now carry verified boot/elapsed observation time to the existing writer. Queue delay cannot extend retention or save an already expired event; invalid capture clocks fail closed. Three deterministic regressions failed before this fix and pass afterward (`queued-capture-before.txt`, `queued-capture-after.txt`). No persistence format or UI rendering changed.
- Code 9 was installed locally during qualification. The corrected artifact uses code 10 to select updated recorder code. The existing writer-death confirmation also runs before binding an older encrypted helper, avoiding concurrent writers during an update. A real live code-9 to code-10 emulator upgrade preserved its wrapped key, exact synthetic payload and authenticated metadata (`encrypted-helper-upgrade-code10.json`). Same-version healthy helpers remain running.

## Build and privacy evidence

The final release build used the documented JBR 21, Gradle 9.3.1, AGP 9.1.1, SDK 37 and Build Tools 36.0.0. Minimum API remains 34. No production dependency or permission was added.

| Check | Fresh result | Evidence |
|---|---|---|
| JVM tests | 65; zero failures/errors | `build-checks-final.json`, `final-build.txt` |
| Release lint | Zero errors; 15 disclosed warnings | `build-checks-final.json` |
| Distribution source guards | 74/74 | `source-audit-final.txt` |
| Signed release | APK signature verified; original RSA-3072 certificate; 16 KiB alignment verified | `apk-signature-final.txt`, `apk-alignment-final.txt` |
| Manifest/package | 1.4.0/code 10; non-debuggable; backup and cleartext disabled; no test component or instrumentation | `apk-badging-final.txt`, `apk-manifest-final.txt` |
| Permissions/components | Original five permissions, one Activity, one Provider, two Services, one Receiver | `apk-manifest-final.txt` |
| Main-thread storage | Five launch/search/preview/copy assertions; no history-file or Keystore disk violation observed on code 10 | `storage-threading-code10-api34.txt` |
| Physical Keystore | Nine isolated, non-destructive Pixel assertions; actual **StrongBox** | `pixel-keystore.txt` |

Lint warnings comprise intentional durable SharedPreferences commit (1), required shell clipboard reflection (4), newer Gradle availability (1), the existing count-label plural concern (1), obsolete API-26 resource folder (1) and unused resources (7). They are disclosed rather than suppressed or presented as errors. StrictMode is best-effort: it does not instrument all JNI or Binder-internal work. Source review additionally verifies history and Keystore operations run on the client I/O or recorder worker. See [Android's StrictMode documentation](https://developer.android.com/reference/android/os/StrictMode).

## Android behavior evidence

The disposable owned API-34 AVD uses actual non-root Shizuku, shell UID 2000. The phone is a Pixel 8 Pro on API 37. Destructive fixtures, plaintext legacy seeds, deliberate stops and reboots are emulator-only. Phone observation exports only whitelisted metadata, never clipboard/history text, screenshots or keys.

| Suite | Result | Evidence |
|---|---|---|
| Encryption/key/migration | 95 assertions on code 10: v1/v2 and interrupted phases, both authenticated copies, canaries absent from raw ciphertext, tamper/missing-key preservation, no plaintext fallback, cache expiry and missing-snapshot guards | `encryption-code10-api34.txt` |
| Missing-snapshot regression | Four assertions; failing pre-fix run preserved | `missing-snapshots-before.txt`, `missing-snapshots-final-api34.txt` |
| Actual legacy old-helper APK upgrade | 160 assertions on code 9; old writer survived install, then terminated before migration; 75 entries and complete committed state preserved; both slots verified, mode 0600 | `live-upgrade-api34.txt`, `live-upgrade-api34.json` |
| Already-encrypted helper upgrade | Actual code 9 → 10: surviving old helper replaced; same wrapped key, exact synthetic payload, count/limit/pause/duplicate metadata and app UID retained | `encrypted-helper-upgrade-code10.json`, `encrypted-helper-upgrade-code10.txt` |
| Live sensitive capture | 38 assertions on code 10: opt-in, normal capture, sticky recopy/deadline, every copy path, native settings, bounds, indefinite confirmation, real one-minute cleanup of both durable snapshots | `sensitive-live-code10-api34.txt` |
| Sensitive UI/expiry/retry | 23 assertions on code 10: both themes, offline controls, full-text and held quick-panel expiry, history/quick retries crossing expiry | `sensitive-ui-code10-api34.txt` |
| Final normal regression | 12 assertions on code 10 | `regression-code10-api34.txt` |
| Final quick errors | 21 assertions on code 10 | `quick-errors-code10-api34.txt` |
| Final maximum capacity | Five assertions on code 10; 500 maximum-size entries, no OOM | `capacity-code10-api34.txt` |
| Final native tile | 353 assertions on code 10 across 30 warm openings; stable bounds and no immediate closing | `tile-performance-code10-api34.txt` |
| Retained idle openings | 12/12 on code 9: ten 60-second waits and two 120-second waits; each panel loads, stays open and closes with one tap | `idle-final-api34.json` |
| Clear-storage isolation | Surviving old helper replaced, new key record, fresh empty store, then exactly one new synthetic entry; one authenticated offline readback assertion | `lifecycle-clear-storage-api34.json`, `clear-storage-status-api34.txt` |
| Pixel signed upgrade | Final 1.4.0/code 10; count 16, limit 100, eight original and ten interim switches preserved; AES-256-GCM/StrongBox/expiry clock Passed; replacement UID-2000 recorder Listening | `pixel-code10-acceptance.json` |

Additional 1.4 checks passed earlier in code-9 qualification: Settings 21, Privacy 11, Recovery 11, Accessibility 22, Onboarding 24, Customization 9, native tile 75, live capture 33, command and quick errors 21 per theme, HTML-port 123 per theme and live 50. Large text (1.6×), landscape and keyboard layout passed 11 assertions, then the default font size/orientation was restored. The new Privacy screens were rendered and visually inspected in both themes. These are fresh 1.4 implementation checks, but are not mislabeled as reruns of every suite on code 10. The last correction does not change rendering, user settings controls or cryptographic format. Evidence files are named by suite here.

JVM regression coverage includes 17 deterministic sensitive-policy cases for elapsed-time/reboot/unknown-boot expiry, sticky classification including pending Undo, policy transitions, mirror failures, capacity eviction and queued-event timing. Expiry assertions inspect durable decrypted snapshots where applicable, rather than equating a filtered UI with disk deletion.

Actual code-9 lifecycle qualification covers ordinary app death, helper death, server-lease death/restart, native Recents Clear all, force stop, Android task stop, disabled automatic recovery, disabled boot resumption, normal reboot after manual Shizuku restart, and paused reboot. App death preserves the same helper PID and capture continues. Deliberate stops prevent capture. Reboots preserve pause/options; non-root Shizuku still requires restart. The added code-10 outdated-helper barrier is additionally exercised by the live encrypted upgrade and final sensitive capture checks. See `lifecycle-*-qualified-api34.json`, `lifecycle-boot-off-api34.json`, `lifecycle-boot-paused-api34.json` and `lifecycle-helper-death-off-api34.json`.

An explicit Clear storage on the owned validation emulator left the former shell helper alive initially. Fresh startup replaced it and its key/storage identity without restoring prior entries. The new authenticated store contained only its new synthetic capture. No personal phone storage was cleared. Actual uninstall/reinstall was not used as a substitute for this tested Clear storage case.

The lifecycle driver observes the target through native UI, not instrumentation attached to that target. Android force-stops instrumentation's target on entry/exit, which invalidated an earlier boot test setup. Synthetic clipboard events now wait for actual listener readiness and producer focus/completion, with an exact clipboard hash check. Recents task removal is verified against Android's task registry; a predicted launcher shortcut is not counted as dismissal.

## Performance qualification

Measurements are emulator observations, not universal phone/OEM guarantees. Warm before/after comparison uses the same native Quick Settings activation path, 30 trials and the owned API-34 AVD. Loaded pre-draw and accessibility availability measure different stages. Cold `am start -W` measures first draw; expected history visibility is checked separately.

| Measurement | 1.3.4 baseline | Final 1.4 | Interpretation |
|---|---:|---:|---|
| Native warm loaded pre-draw p95 | 451.20 ms | 243.71 ms | No final regression above the planned 10% gate |
| Native warm accessibility p95 | 1,157 ms | 930 ms | Includes Android UI dump overhead |
| Native warm accessibility p50 | 1,065 ms | 877 ms | 30 final openings, no timeout/dismissal |
| Cold first-draw p50 / p95 | 1,687 / 2,022 ms | 657 / 752 ms | Ten Android COLD launches per run; nearest-rank percentiles, substantial host/VM variance |
| Maximum snapshot uncached read | 654 ms | 687 ms | One matched capacity run per build |
| Cached read | 6 ms | 2 ms | Current filtered cache checks expiry each access |
| Capacity fixture first launch | 1,531 ms | 616 ms | 500 maximum-size entries |
| Next page / full-text retrieval | 76 / 117 ms | 67 / 57 ms | Existing paging/full-text path |
| Capacity serialization | 408 ms | 382 ms | Serialization alone does not measure total encryption/fsync |

Evidence: `baseline-tile-matched-api34.txt`, `tile-performance-code10-api34.txt`, `baseline-cold-api34.json`, `cold-code10-api34.json`, `baseline-capacity-matched-api34.txt`, `capacity-code10-api34.txt`. Final encoded snapshot size is 32,778,572 bytes; app heap limit is 192 MiB and no OOM occurred. These observations support acceptable performance in the tested environment, **not a claim that encryption caused the speedup**. Earlier code-9 timings (178.94 ms warm loaded p95, 415 ms cold p95, 386 ms uncached read) were better than the final run in several metrics; this variance is disclosed rather than selecting only the fastest result.

The initial command-triggered tile baseline (172.10 ms loaded p95 / 910 ms accessibility p95) was not a native tap. An early command-triggered current run was slower; matching the native path and repeating the qualification did not reproduce an added regression. Host load, warm caches and emulator health are material sources of uncertainty. Command/native numbers must not be combined as one controlled comparison.

One measured 60-second idle interval left helper CPU user/system ticks and RSS pages unchanged. App PSS changed from 32,409 to 37,329 KiB across UI/lifecycle state. No kernel wakeup count, measured battery drain or multi-day soak was performed. The one-deadline design and absence of a wake lock are source facts, not a battery-life certification.

The retained code-9 idle schedule passes all twelve native tile openings, with 840 seconds of requested waits and 1,019.828 seconds elapsed including automation/recording overhead. All 1,167 encoded video frames were decoded and visually inspected; each trial's decoded PNG count matches independent ffprobe frame counting. The quick panel remained populated with stable bounds, with no new empty Main flash, layout jump or immediate dismissal. The SystemUI shade collapse, brief blur and dim transitions also appeared in the baseline. The capture-clock/handoff correction does not modify these rendering paths; code-10 warm/cold checks are repeated, while the fourteen-minute idle schedule is retained with this accurate build label.

The captured app-process frame timeline contains 128 records, including 20 classified App Deadline Missed, with a maximum recorded duration of 74.57 ms. The baseline has 108 records, including 60 App Deadline Missed, maximum 226.93 ms. These windows include opening and closing; they are not exclusively opening-frame timings. VFR screenrecord omits unchanged screen frames, so encoded frames are not every physical display refresh. The results meet the measured regression gate and do **not** establish zero missed frames, zero latency or encryption as the cause of improved values. See `frame-review-api34.json` and `idle-final-api34.json`. Raw synthetic videos/traces/contact sheets remain private under `.tools/encryption-current/idle-api34-final/`.

## Test-environment failures retained

An old SystemUI tile token and later an Android PointerEventDispatcher input ANR invalidated setup in the long-running AVD. These are recorded in `emulator-stale-tile-tokens.txt` and `emulator-system-anr.txt`. Another project then used the same emulator port. Only the verified owned ClipHistory API-34 VM was stopped; its existing data was retained and it restarted on port 5586. The other VM was untouched. Subsequent final behavior/performance checks passed.

The first final idle driver attempt stopped during setup because a full-page Settings fling skipped the screenshot control. Overlapping bounded swipes fixed the test driver. A setup failure is not counted as a passed timing trial or a demonstrated app defect.

The first clear-storage attempt stopped before any wipe because no independent recorder was active. Its preceding startup driver had remained on the Settings page after timing restored the screenshot preference. Starting explicitly with the existing OPEN_HISTORY intent fixed this harness precondition. The subsequent recorder start, Clear storage and authenticated fresh-state checks passed.

## Local artifact and publication boundary

Signed APK source: `app/build/outputs/apk/release/app-release.apk` (2,634,671 bytes).

- APK SHA-256: `654fa4060b6166eebadc7e9725dcfcebf56a458a5adf06c3b30af782e0b4239d`
- Signer certificate SHA-256: `ef13472a271f187fbcfb3fecc186a5afa041bf1a2567caa166c82f8eb5459c82`

Delivered local APK: `F:/Astra/Clipboardphoner/ClipHistory-1.4.0.apk`, with `ClipHistory-1.4.0.apk.sha256`. Its bytes match the qualified build and digest above.

The verified Pixel 8 Pro received this APK with `adb install -r`; Android reports 1.4.0/code 10 and the same signing certificate. The code-9 recorder PID 29689 was replaced by PID 1527 under UID 2000. Before/after whitelisted UI metadata retains 16 entries, the 100-entry limit, all eight original switches and ten interim switches. Sensitive capture is Off and auto-delete On. Production advanced diagnostics report AES-256-GCM, StrongBox and expiry clock Passed; the recorder reports Listening. See `pixel-before-code10.txt`, `pixel-after-code10.txt`, `pixel-package-code10.txt`, `pixel-processes-code10.txt` and `pixel-code10-acceptance.json`. The original 1.3.4 → code-9 migration acceptance is retained separately in `pixel-upgrade-acceptance.json`.

Pixel acceptance used metadata only. No personal history contents, screenshots, clipboard writes, stop, uninstall, data clear, downgrade or reboot were used. Exact payload migration, copying destinations, offline expiry and disruptive failure/lifecycle cases were verified with owned emulator fixtures. The Pixel test does not claim inspection of every personal payload or every copy destination on physical hardware.

For a later F-Droid publication: commit the reviewed source, publish an immutable authorized tag/reference, pin the metadata recipe to that exact commit, rerun clean cross-platform/reproducible builds, source/APK scans, signature copying and reference comparison, and obtain maintainer CI/review. No nonexistent release tag or new recipe was invented. Existing publication state remains associated with 1.3.4; this signed local APK is not itself an F-Droid inclusion decision.

## Definition of Done state

Verified: encrypted durable writes/readback, wrapped key and owner checks, migration/interruption and old-writer exclusion, accurate failure states, opt-in capture/default timer/policy transitions, expiry across cache/view/copy/retry/Undo, normal workflow and sensitivity propagation, actual recorder lifecycle semantics, fresh build/tests/source/manifest/signature checks, light/dark and accessibility/layout checks.

Verified: all twelve timed openings and encoded-frame review, the planned measured performance regression gate, Pixel same-signer in-place acceptance and matching delivery checksum. The completed gate list and evidence references are recorded in `checks.json`.

Disclosure checks cover real clipboard data and key material. Controlled synthetic fixture strings intentionally appear in ignored local recordings needed for visual verification; no personal clipboard payload or plaintext data key was exported. This states the evidence boundary explicitly rather than calling synthetic screenshots payload-free.

No exhaustive TalkBack audio traversal, every-OEM coverage, measured battery drain/multi-day soak, secure RAM/flash erasure or zero-delay guarantee is claimed. Final source/documentation review checks crypto bounds/nonce generation, writer exclusion and migration, threading, timer/copy/Undo expiry, errors, privacy, accessibility and scope. No new production dependency or unrelated redesign was introduced; publication actions remain separate.
