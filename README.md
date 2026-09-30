# ClipHistory

A local clipboard-history companion for Android, with **Shizuku started in non-root shell mode**. It is not a keyboard and does not modify Gboard.

## Release 1.2.0

Public source: https://github.com/saroo98/cliphistory-android. Signed APKs are in the repository's Releases page. Android API 34 or later is required; compile and target are API 37.

This release fixes layout, lifecycle and diagnostics defects, improves bounded snapshot processing and cached offline paging, and includes complete setup and licence information. Build and device evidence is in `VERIFICATION.md`. The F-Droid recipe is `fdroid/metadata/app.cliphistory.yml`, with store text and images in `fastlane/metadata/android/`.

F-Droid publication requires its independent review and build verification. Providing an APK and source does not mean it is already listed in the main repository.

## What is implemented

- Text history with a default limit of **100**, configurable from **20 to 500**. Oldest entries drop from the current history as new entries arrive.
- Event-driven background capture in a shell-UID Shizuku UserService. No Accessibility service, replacement keyboard, overlay, polling loop or ordinary foreground service.
- App icon and a **Clipboard Quick Settings tile**. The tile opens history and requests unlocking when needed; it does not display a guessed recording status.
- Redesigned light/dark history: search, tap-to-copy-and-return, explicit full-text button, long-press action sheet, full-text preview, clear history, limit setting and pause/resume.
- System / Light / Dark appearance and an optional copy-and-stay mode. Preferences persist across launches.
- Exact plain-text content, including whitespace, newlines and Unicode. Consecutive identical saved texts do not consume extra slots.
- Private, no-backup storage with checksummed, alternating snapshots and explicit corruption/error handling.
- Sensitive-flag filtering, bounded text/IPC/queue sizes, privacy-safe diagnostics and an explicit device connection/storage test.
- A lease on the Shizuku **server** Binder, independent of the normal app's process. Stopping that server makes the helper stop, rather than leaving an orphan recorder intentionally running.

## Start

On Windows, extract the ZIP fully, then double-click **`BUILD_WINDOWS.cmd`**. Read `START_HERE.md` for the short walkthrough and `BUILDING.md` for details.

Once a build has succeeded, install the generated `ClipHistory-1.2.0.apk`, start Shizuku, open ClipHistory, tap **Connect** and approve Shizuku access. From the three-dot menu, run **Connection test** and add the Quick Settings tile. Keep Gboard selected as your keyboard.

After a phone reboot, restart non-root Shizuku and reopen ClipHistory. Copies made before the recorder resumes cannot be reconstructed. Existing saved history is independent of Gboard's own expiry timer.

## Boundaries

Only the primary Android phone profile and shell UID **2000** are supported by this build. Root/Sui, work profiles, secondary users, images, URIs and multi-item clip payloads are not claimed supported.

An individual text must be at most **65,536 UTF-8 bytes**. Oversized items are skipped and counted, not silently truncated. Entries marked sensitive by their source app are skipped. Not every source app marks passwords or other secrets; do not use this as a password vault.

Clipboard notifications announce changes; they are not a durable queue of every intermediate clipboard value. Very rapid replacements, process death, permission failures, overload or platform changes can cause missed copies. Registered-listener status or a passed one-shot test does not prove long-term, all-app capture.

No runtime network code, account, ads, analytics, cloud service or INTERNET permission is included in our app source. Build-time downloads are separate. The shell helper has broader system privileges than a normal app; the absence of INTERNET in the app manifest is not a security sandbox for shell code. See `SECURITY.md`.

## Architecture

```text
Source app → Android clipboard → shell UserService listener
                                      ↓
                              bounded serial writer
                                      ↓
                        two app-private file descriptors
                                      ↓
                        history Activity / Quick Settings
```

The ordinary app creates its own private files and passes open descriptors to the helper via an owner-UID-guarded AIDL interface. The helper uses positional read/write and fsync, not chmod, truncate, shared storage or arbitrary path access. A length-prefixed framing format permits shorter snapshots and zeroed obsolete tails without changing file attributes as shell.

The helper remains the single writer. Opening the app reconnects to it. Without Shizuku, the UI can read and copy saved history but deliberately does not mutate its files while a potentially existing helper could still be writing them.

Explicit deletion, clearing, shrinking the limit and changing pause state update both recovery slots. A checksum detects corruption, not malicious alteration or disclosure. Recovery may fall back to the previous committed generation. It cannot recover from both copies being damaged.

## Source map

- `core/`: history policy, codec, framed storage, clipboard signature routing and probe tracking; executable host tests.
- `daemon/`: shell clipboard bridge, descriptor access, AIDL implementation, serial persistence and lifecycle handling.
- `client/`: Shizuku permission/binding, private file ownership, async IPC and offline reading.
- `ui/`: native Android Activity, list adapter and Quick Settings tile.
- `app/src/main/aidl/`: bounded, owner-checked IPC interface and change observer.
- `tools/`: host tests, static source audit and local Gradle/Windows build bootstraps.
- `docs/release-1.2/`: selected synthetic test evidence.
- `fdroid/` and `fastlane/`: build recipe and store metadata.

## Tests

With Kotlin CLI and Java 17+ installed:

```sh
bash tools/test-core.sh
python3 tools/audit_source.py
```

With a real Android SDK and the dependencies available:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Release signing needs the owner-local `.signing` material created by the Windows build script; otherwise the Gradle release output is unsigned. No signing key is distributed in this ZIP.

Read `VERIFICATION.md`, `DEVICE_TESTS.md`, `SECURITY.md` and `docs/SOURCES.md` before treating this as a verified daily-use app.

## Licence

Original project code is MIT licensed. Shizuku, Kotlin and Android build dependencies retain their respective licences. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
