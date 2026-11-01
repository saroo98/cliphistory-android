# Security and privacy model

## Scope of trust

The normal APK runs as its own Android UID. Shizuku starts its helper as **shell UID 2000**, with substantially broader privileges. Only grant Shizuku access to a build whose source and signing identity you trust.

Our source has no network client, analytics, ads, cloud SDK, external command execution or INTERNET permission. **An app manifest does not constrain a shell-UID process to the normal app's permission sandbox.** The no-network property of this helper comes from its implementation, not from a claim that shell is technically unable to make network connections. Third-party dependencies and the compiled APK still need inspection in a real build.

The helper uses Android's existing clipboard service and the shell package's background clipboard permission. It does not grant extra global permissions, disable security settings, root the phone or patch Gboard. Its hidden-interface routing accepts only known shapes; an unrecognised platform signature is an error, not a guessed transaction layout.

## Storage and IPC

The normal app creates two files in its private `noBackupFilesDir`, sets owner-only permissions, and passes open descriptors to the helper. The helper validates the owning UID and regular-file type. It does not browse `/data/user/0`, use shared storage or store clips in `/data/local/tmp`.

Positional reads/writes avoid shared seek positions. The shell side does not truncate, chmod or rename app data. Length-prefixed, checksummed snapshots alternate between files. Acknowledgement follows fsync. Explicit clear/delete/limit shrink and pause updates mirror to the recovery slot; failures in cleanup are surfaced. These mechanisms detect interrupted/corrupt writes but cannot guarantee survival of physical storage failure or simultaneous damage to both slots.

History is additionally encrypted with **AES-256-GCM**: a 256-bit data key, a fresh random 96-bit nonce for every slot write, and a 128-bit authentication tag. Associated data binds the envelope version, application, random storage identity and slot. Length and version bounds are validated before decryption. The inner checksum remains as serialization validation; it is not the encryption mechanism.

An application-UID Android Keystore key wraps the random data key and migration phase in an atomic no-backup record. StrongBox is preferred when supported and available; otherwise Android's normal Keystore is used. The observed security level is in advanced diagnostics, rather than assumed from the phone model. The wrapping key is not exported. The data key is unwrapped on the client's worker and delivered through owner-checked Binder to the shell recorder, which holds it in RAM and encrypts on its serial worker. Closing the UI does not make recording dependent on further Keystore calls.

The upgrade establishes that the old helper is absent or dead before writing migration files. First conversion of an existing store requires running, authorized Shizuku to verify that ownership; a missing provider Binder alone is not proof that a shell writer has stopped. Failure to establish ownership leaves the store untouched and retryable. Pending migration may read validated legacy v1/v2 snapshots; completed migration rejects plaintext. Both slots must authenticate and match before migration is marked complete. Authentication or wrapping-key failure is an error, not permission to create a new key and discard history. Recovery may use the other authenticated slot, which can contain an earlier generation. No anti-rollback guarantee is made against a privileged attacker replacing private files with old valid ciphertext.

An older encrypted helper is also confirmed dead before another app version binds a writer to the same files. This check runs on the client I/O worker and leaves a same-version healthy recorder running.

No cloud backup is requested. Encryption protects stored files from disclosure without the key. A compromised application or recorder process, root/system access, or an unlocked device with app access remains outside this confidentiality boundary. Text and the data key must be available in memory during use, and managed runtimes do not guarantee erasure of every RAM copy. Copied text deliberately remains readable in Android's clipboard. This is not an unhackable vault.

Obsolete file tails are overwritten during shorter writes. This is logical cleanup, **not guaranteed forensic erasure from flash**, snapshots or prior backups. Do not advertise it as secure deletion.

The AIDL interface checks the app owner's UID for every history/status/control operation. Destroy additionally permits Shizuku's shell/root lifecycle caller. Requests and replies have bounded text/page sizes. A caller in another normal Android UID is not allowed access. Observer callbacks contain no clipboard payload.

## Capture and lifecycle

The raw clipboard callback accepts the Android system UID, validates its interface descriptor and clears incoming Binder identity before making a shell-identity clipboard call. The helper reads the current clip immediately, then queues persistence on one bounded writer.

Android can notify again when it finishes classifying an existing copy. The helper distinguishes this from a new copy using the system clipboard timestamp, exact text and source-sensitive flag. It keeps only one keyed signature in RAM, with a random per-listener key; no extra plaintext or persisted fingerprint is retained. The current clipboard is remembered before listening, without importing it into history. Clearing history or expiring an entry does not make a later notification for that same copy new data.

Marked-sensitive clips are excluded by default. Optional capture reads the existing plain-text item without coercing URIs, resolving HTML or querying content providers. Non-text/multi-item payloads and texts over 65,536 UTF-8 bytes remain unsupported. Source apps do not universally mark secrets. An exact text already classified sensitive retains that classification on recopy; newly marked text upgrades retained exact matches and pending Undo. Copying sensitive history back to Android preserves its sensitive-preview flag.

Auto-delete is enabled by default at five minutes. Deadlines use boot identity and elapsed realtime, so changing the wall clock or restarting the process cannot extend them. Timed sensitive text is withheld after reboot or when boot identity cannot be established. Reads, cached offline access, previews, copy retries and Undo recheck expiry; automatic expiry has no Undo. Disabling sensitive capture purges retained sensitive entries and their pending Undo. Turning auto-delete off requires an explicit UI confirmation, removes timers only after expired text is purged, and allows deliberate indefinite retention. Re-enabling assigns a deadline to indefinite entries; a longer timer does not extend already active deadlines.

The clipboard observation's monotonic time travels with its queued event. A writer backlog does not restart the timer; an event already expired when processed is not saved. Invalid or mismatched capture clocks fail closed for timed sensitive captures.

The observer also carries the committed sensitivity and original deadline of an exact retained match, including pending Undo. This view is published after the first durable write, before recovery-copy mirroring, so delayed unmarked recopy cannot become ordinary indefinite history merely because cleanup ran first. This is not a permanent secret classifier: once no retained sensitive match exists, an unmarked new copy cannot be identified as sensitive by that rule.

One nearest-deadline task uses the existing bounded serial writer. Cancelled deadlines are removed from its queue immediately. No repeating scan, wake lock or exact-alarm permission is added. Android suspension can delay physical cleanup, but expired text is withheld on access. Cleanup rewrites both recovery slots and reports failure, with bounded retries. Auto-delete affects saved history; it does not clear Android's shared clipboard or copies held by other apps. Flash wear levelling and old snapshots prevent a guaranteed forensic-erasure claim.

Pausing stops archiving; it does not mean the helper never receives a clipboard callback. **Stop recorder** removes the helper and blocks recovery until explicit Start. A failed durable Stop write is reported without acknowledging success. Stopping Shizuku also ends the helper.

A lease watches the Shizuku server Binder, not the UI Binder. Normal UI death should not stop capture; server death should terminate the helper. Optional recovery reconnects after supported interruptions. Reboot still requires unlocking and starting non-root Shizuku. Enabled recovery then attempts reconnection when permitted. Owner-stop checks gate reads and serial commits using the stopped flag and public process-exit information. Only verified AOSP task-removal descriptions are exempted; unknown descriptions fail closed. Those descriptions are not stable across manufacturers/releases. Hidden APIs, process lifetime and clipboard timing cannot support a universal 100% guarantee.

The helper checks incoming file identities on attachment to avoid resurrecting memory belonging to files removed by Android's Clear storage. An unlinked backing file makes subsequent capture terminate the helper. The owned emulator's Clear storage test replaces the old helper/key without restoring prior history; its fresh authenticated state contains only a new synthetic event. No personal phone clear-data test was performed. See `docs/release-1.4/RELEASE.md` for the exact scope.

## UI and diagnostics

All app-owned windows block capture through `FLAG_SECURE` by default. **Allow screenshots** deliberately permits screenshots and screen recording. Independent Recents hiding is enabled by default on Android 13+; on Android 11–12 it follows screenshot blocking. The tile requires unlocking and either displays a temporary system QS dialog or opens history through the platform launch API (an immutable PendingIntent on Android 14+). It requests no overlay permission. The private optional recovery notification contains no saved text. Copying deliberately places full text on Android's shared clipboard, where the keyboard and destination app can use it.

No raw clips are logged by production code. Diagnostics expose counters, API layouts, version/device details and bounded error identifiers, not copied text. The connection test is opt-in and warns that it changes the current Android clipboard. Known test nonces are excluded from ordinary history, including delayed callbacks.

## Signing and supply chain

No signing key is shipped. The Windows build generates a private owner-specific key under `.signing/`, restricts that directory and uses environment-based keytool password arguments. Keep that directory private and backed up. Do not send the password, key or SDK caches to support chats.

Build scripts download official tool distributions over HTTPS and check published checksums; Gradle fetches pinned app dependencies from configured repositories. Publisher metadata is part of the trust boundary, and this is not an independent supply-chain audit. The Android merged manifest and APK signature/permissions must be checked after a real build, not inferred solely from source XML.

## Reporting an issue

Preserve the app version, APK hash, Android/Shizuku versions and a redacted diagnostic/build report. Do not provide actual passwords or copied personal text. Stop the recorder before reproducing any privacy issue with harmless canary strings.
