<picture>
  <source media="(prefers-color-scheme: dark)" srcset="branding/cliphistory-dark.svg">
  <img alt="ClipHistory" src="branding/cliphistory.svg" width="320">
</picture>

# ClipHistory

A local clipboard-history companion for Android, with **Shizuku started in non-root shell mode**. It is not a keyboard and does not modify Gboard.

## Release 1.3.4

**1.3.4/code 8** adds the approved ClipHistory logo to the adaptive launcher and themed icon, Quick Settings tile, recovery notification, Welcome and About. GitHub and F-Droid artwork use the matching supplied exports. Asset selection and provenance are in [branding](branding/README.md). Clipboard behavior and the existing UI design remain unchanged.

## Native UI improvements

**1.3.3/code 7** ports the reviewed HTML UI refinements into the native Android application. It adds clearer Settings summaries, guided setup, visible full-text actions, persistent single-deletion Undo and focused error recovery. See [port and verification](docs/HTML_UI_PORT.md). The preceding tile launch fix is documented in [tile verification](docs/TILE_LAUNCH_FIX.md). F-Droid submission status and exact release evidence are recorded in [publication preparation](docs/F_DROID_PUBLICATION.md). A prepared or submitted app is not an accepted F-Droid listing.

Public source: https://github.com/saroo98/cliphistory-android. Signed APKs are in the repository's Releases page. Android API 34 or later is required; compile and target are API 37.

Version 1.3.0/code 4 adds exact unique-text history, configurable screen privacy, optional recorder recovery, a temporary floating quick-copy panel, battery guidance and consolidated Settings. Build and actual device evidence, including remaining acceptance checks, is in `VERIFICATION.md`. Historical 1.2 results do not establish 1.3 readiness. The F-Droid recipe is `fdroid/metadata/app.cliphistory.yml`, with store text and images in `fastlane/metadata/android/`.

The upstream project welcomes F-Droid inclusion under its MIT licence. Submission details and the prepared merge-request description are in `docs/F_DROID_SUBMISSION.md` and `docs/F_DROID_MERGE_REQUEST.md`. Bugs and compatibility reports can be filed in this repository's issue tracker.

F-Droid publication requires its independent review and build verification. Providing an APK and source does not mean it is already listed in the main repository.

## What is implemented

- Text history with a default limit of **100**, configurable from **20 to 500**. Oldest entries drop from the current history as new entries arrive.
- Event-driven background capture in a shell-UID Shizuku UserService. Optional recovery uses a private foreground service and quiet notification with no saved text. No Accessibility service, replacement keyboard, overlay permission or polling loop.
- A **Clipboard Quick Settings tile** with a temporary three-entry panel or full-history action. It requires unlocking, copies full text with one tap, closes after success and never auto-pastes. Full-history mode is available if floating behavior is unsuitable on a device.
- Redesigned light/dark history: search, tap-to-copy-and-return, explicit full-text button, long-press action sheet, full-text preview, clear history, limit setting and pause/resume.
- System / Light / Dark appearance and an optional copy-and-stay mode. Preferences persist across launches.
- Exact plain-text content, including whitespace, newlines and Unicode. Default duplicate handling keeps one entry per exact text: `A → B → A → B` produces `B, A`. The previous consecutive-only mode remains selectable.
- Settings grouped into Recording, Recovery, Quick access, Privacy, Appearance, Welcome and About. Choice rows show their current values; the eight Boolean preferences remain native switches. Screenshots and screen recording are blocked by default but can be enabled; Recents hiding is independently enabled by default. Motion follows the system or can be disabled.
- Optional welcome (First time only, Every app opening, Never), real feedback/share/GitHub support destinations, optional battery guidance and a six-step Shizuku/reboot guide. Help preserves open topics and reading position. Support is never required.
- Single-entry deletion offers one-level Undo until dismissed, replaced by another deletion, cleared history or helper termination. Undo restores the original entry without evicting another entry; a full history asks for a larger limit. The pending entry exists only in helper memory, not a recovery file. Clear history has no Undo.
- Private, no-backup storage with checksummed, alternating snapshots and explicit corruption/error handling.
- Sensitive-flag filtering, bounded text/IPC/queue sizes, privacy-safe diagnostics and an explicit device connection/storage test.
- A lease on the Shizuku **server** Binder, independent of the normal app's process. Stopping that server makes the helper stop, rather than leaving an orphan recorder intentionally running.

## Start

On Windows, extract the ZIP fully, then double-click **`BUILD_WINDOWS.cmd`**. Read `START_HERE.md` for the short walkthrough and `BUILDING.md` for details.

Once a build has succeeded, install `ClipHistory-1.3.4.apk` using the existing signing identity, start Shizuku, open ClipHistory and approve Shizuku access. Run the optional **Connection test** and add the tile through Android's confirmation. The connection test replaces the current shared clipboard with harmless text. Your usual keyboard stays unchanged.

After reboot, unlock and start non-root Shizuku again. Enabled recovery prepares after unlock and attempts reconnection when Shizuku becomes available and Android permits it. It does not automatically open the full Activity. Copies made before reconnection cannot be reconstructed. Battery settings are optional and cannot remove Shizuku's restart requirement.

Automatic recovery Off does not stop an already running helper. **Pause** stops saving; **Stop recorder** stops the helper and blocks recovery until explicit Start. Force stop and Android Task Manager Stop are respected. Unknown manufacturer user-stop descriptions fail closed and may require explicit reconnection.

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

Explicit deletion, clearing, shrinking the limit, changing pause state and duplicate-policy cleanup update both recovery slots. A checksum detects corruption, not malicious alteration or disclosure. Saved text is not app-encrypted. Recovery may fall back to the previous committed generation and cannot recover both copies being damaged.

Version 1.3 reads valid v1 history and writes v2, preserving pause and limit while combining duplicate text under the new default. **Downgrading to 1.1/1.2 after migration is unsupported** because those versions cannot read v2. Never uninstall to bypass a signing mismatch when history matters.

## Source map

- `core/`: history policy, codec, framed storage, clipboard signature routing and probe tracking; executable host tests.
- `daemon/`: shell clipboard bridge, descriptor access, AIDL implementation, serial persistence and lifecycle handling.
- `client/`: Shizuku permission/binding, private file ownership, async IPC and offline reading.
- `ui/`: native Android Activity, list adapter and Quick Settings tile.
- `app/src/main/aidl/`: bounded, owner-checked IPC interface and change observer.
- `tools/`: host tests, static source audit and local Gradle/Windows build bootstraps.
- `docs/release-1.3/`: current selected synthetic evidence and qualification limits; `docs/release-1.2/` remains historical.
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
