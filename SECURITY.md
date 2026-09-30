# Security and privacy model

## Scope of trust

The normal APK runs as its own Android UID. Shizuku starts its helper as **shell UID 2000**, with substantially broader privileges. Only grant Shizuku access to a build whose source and signing identity you trust.

Our source has no network client, analytics, ads, cloud SDK, external command execution or INTERNET permission. **An app manifest does not constrain a shell-UID process to the normal app's permission sandbox.** The no-network property of this helper comes from its implementation, not from a claim that shell is technically unable to make network connections. Third-party dependencies and the compiled APK still need inspection in a real build.

The helper uses Android's existing clipboard service and the shell package's background clipboard permission. It does not grant extra global permissions, disable security settings, root the phone or patch Gboard. Its hidden-interface routing accepts only known shapes; an unrecognised platform signature is an error, not a guessed transaction layout.

## Storage and IPC

The normal app creates two files in its private `noBackupFilesDir`, sets owner-only permissions, and passes open descriptors to the helper. The helper validates the owning UID and regular-file type. It does not browse `/data/user/0`, use shared storage or store clips in `/data/local/tmp`.

Positional reads/writes avoid shared seek positions. The shell side does not truncate, chmod or rename app data. Length-prefixed, checksummed snapshots alternate between files. Acknowledgement follows fsync. Explicit clear/delete/limit shrink and pause updates mirror to the recovery slot; failures in cleanup are surfaced. These mechanisms detect interrupted/corrupt writes but cannot guarantee survival of physical storage failure or simultaneous damage to both slots.

A checksum is **not encryption**. The design relies on the phone's ordinary private-app storage and underlying Android storage protection, not an additional app-managed AES database. A compromised app UID, root, powerful system software or someone with an unlocked device and app access is outside this confidentiality boundary. No cloud backup is requested. Inactive recovery data can represent an earlier generation of history; explicit deletion/clear rewrites both slots.

Obsolete file tails are overwritten during shorter writes. This is logical cleanup, **not guaranteed forensic erasure from flash**, snapshots or prior backups. Do not advertise it as secure deletion.

The AIDL interface checks the app owner's UID for every history/status/control operation. Destroy additionally permits Shizuku's shell/root lifecycle caller. Requests and replies have bounded text/page sizes. A caller in another normal Android UID is not allowed access. Observer callbacks contain no clipboard payload.

## Capture and lifecycle

The raw clipboard callback accepts the Android system UID, validates its interface descriptor and clears incoming Binder identity before making a shell-identity clipboard call. The helper reads the current clip immediately, then queues persistence on one bounded writer.

Marked-sensitive clips, non-text/multi-item payloads and texts over 65,536 UTF-8 bytes are not archived. Source apps do not universally mark secrets. Pausing stops archiving; it does not mean the helper never receives a clipboard callback. **Stop recorder** removes the helper and blocks recovery until explicit Start. A failed durable Stop write is reported without acknowledging success. Stopping Shizuku also ends the helper.

A lease watches the Shizuku server Binder, not the UI Binder. Normal UI death should not stop capture; server death should terminate the helper. Optional recovery reconnects after supported interruptions. Reboot still requires unlocking and starting non-root Shizuku. Enabled recovery then attempts reconnection when permitted. Owner-stop checks gate reads and serial commits using the stopped flag and public process-exit information. Only verified AOSP task-removal descriptions are exempted; unknown descriptions fail closed. Those descriptions are not stable across manufacturers/releases. Hidden APIs, process lifetime and clipboard timing cannot support a universal 100% guarantee.

The helper checks incoming file identities on attachment to avoid resurrecting memory belonging to files removed by Android's Clear storage. An unlinked backing file makes subsequent capture terminate the helper. These controls are implemented but must be validated on the device; no physical clear-data test was performed in the chat.

## UI and diagnostics

All app-owned windows block capture through `FLAG_SECURE` by default. **Allow screenshots** deliberately permits screenshots and screen recording. Recents hiding is independently enabled by default. The tile requires unlocking and either displays a temporary system QS dialog or opens history through an immutable PendingIntent. It requests no overlay permission. The private optional recovery notification contains no saved text. Copying deliberately places full text on Android's shared clipboard, where the keyboard and destination app can use it.

No raw clips are logged by our code. Diagnostics expose counters, API layouts, version/device details and bounded error identifiers, not copied text. The connection test is opt-in and warns that it changes the current Android clipboard. Known test nonces are excluded from ordinary history, including delayed callbacks.

## Signing and supply chain

No signing key is shipped. The Windows build generates a private owner-specific key under `.signing/`, restricts that directory and uses environment-based keytool password arguments. Keep that directory private and backed up. Do not send the password, key or SDK caches to support chats.

Build scripts download official tool distributions over HTTPS and check published checksums; Gradle fetches pinned app dependencies from configured repositories. Publisher metadata is part of the trust boundary, and this is not an independent supply-chain audit. The Android merged manifest and APK signature/permissions must be checked after a real build, not inferred solely from source XML.

## Reporting an issue

Preserve the app version, APK hash, Android/Shizuku versions and a redacted diagnostic/build report. Do not provide actual passwords or copied personal text. Stop the recorder before reproducing any privacy issue with harmless canary strings.
