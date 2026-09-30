# ClipHistory: Complete UI/UX Image-Generation Prompt

Prepared from the current ClipHistory source. This is a design brief, not an assertion that the proposed redesign or additions are implemented.

## How to use this

Paste the entire section between **BEGIN IMAGE PROMPT** and **END IMAGE PROMPT** into ChatGPT with image generation available. It tells ChatGPT to create the first screen, establish a reference, then generate the rest consistently when you say `Next` or request a screen ID.

Generating separate screens makes small text and layout easier to inspect than requesting dozens of screens in one collage. Reattach the first generated screen if you start a new conversation. Images cannot guarantee exact typography, pixel measurements, interaction behavior, or animation performance; the specifications below remain the reference for later implementation.

The only proposed functional additions in this brief are full-text preview, an appearance preference, and an optional copy-and-stay preference. No future roadmap has yet been agreed. These are proposals for your review, not claims about existing functionality. Other changes are visual or navigational treatments of existing features.

---

# BEGIN IMAGE PROMPT

Act as an expert Android product designer and mobile interface art director. Generate high-fidelity UI images for **ClipHistory**, a private text clipboard-history app for a Pixel 8 Pro running Android 17.

I want a coherent, buildable Android interface that feels calm, minimal, fluid, snappy, and carefully finished. Design an everyday utility I can open, scan, tap, and leave in seconds. Treat text readability and retrieval speed as the product's main visual priorities.

Generate images, not code. Do not answer with only a description. Follow the specification below closely instead of inventing your own features, layout, colors, or copy.

## 1. Product and scope

ClipHistory complements Gboard. It is not a keyboard and does not replace Gboard.

### Existing capabilities to preserve

- Text-only clipboard history, newest first.
- Default capacity: 100 entries. Adjustable between 20 and 500.
- Older entries roll out when the configured capacity is exceeded.
- Search saved text locally.
- Display a short preview and relative timestamp for each entry.
- Tap a history row to copy its full text to Android's clipboard and close this Activity, returning through Android's normal navigation.
- Long-press an entry to access Copy and Delete.
- Load more entries when the current page does not show the entire history.
- Clear saved history with confirmation.
- Pause and resume recording.
- Connect to a Shizuku helper; show connection, permission, recording, and failure states.
- Open the separate Shizuku app when installed.
- Run a connection/storage test, with confirmation before replacing the current clipboard.
- Show diagnostics and copy a diagnostic report.
- Read and search existing history while the recorder is unavailable. Existing mutations such as delete, clear, limit, and pause require a connected helper.
- Add a Quick Settings tile called **Clipboard**. The tile opens the app; it is not an on/off recording toggle.
- Show help/privacy information and restart guidance.
- Existing styling follows the device's light/dark theme; there is no in-app appearance preference yet.

### Proposed additions to design, clearly identified outside the screen

1. **Full-text preview:** a read-only detail view opened from an entry's long-press action sheet. Normal row tap must still copy-and-return by default.
2. **Appearance preference:** System, Light, and Dark, default System.
3. **Copy behavior preference:** “Return after copying,” default on. Turning it off keeps ClipHistory open after a successful copy.

Mark these screens “Proposed addition” in their image captions, not as decorative badges inside the app. They have not been implemented or approved for development yet.

### Strict exclusions

No accounts, sign-in, cloud sync, AI assistant, subscriptions, advertisements, categories, folders, tags, favorites, pinning, editing clips, images, link previews, analytics charts, batch selection, export/import, biometric lock, or new keyboard. Do not invent source-app attribution: the current data does not identify which app a clip came from.

Do not introduce bottom tabs, a floating action button, a permanent notification, decorative onboarding slides, or persistent system overlays. Do not add a sensitive-clips switch, encryption badge, guaranteed-capture claim, or Undo action. The existing delete operation does not implement undo.

## 2. Art direction: quiet mineral

The app should feel composed and understated: a clean reading surface, dark legible text, soft mineral neutrals, and one restrained eucalyptus accent. Most of the screen is useful clipboard content.

Use flat, clean surfaces. No gradients, blur, glass, paper texture, grain, glossy cards, neon, decorative photography, 3D illustrations, big empty hero areas, or marketing slogans. Calmness comes from alignment, proportion, readable type, and restrained color.

The visual identity is a small rounded clipboard outline containing two short horizontal text lines. Keep it recognizable at launcher and Quick Settings sizes. No mascot, letter monogram, multicolor logo, or replacement app name.

### Fixed light palette

| Role | Color |
|---|---|
| Main background | `#F7F8F4` |
| Sheet/dialog surface | `#FFFFFF` |
| Search/secondary surface | `#ECEFE9` |
| Primary text | `#202922` |
| Secondary text | `#626D64` |
| Divider | `#DCE2DA` |
| Primary accent/button | `#365F4B` |
| On primary button | `#FFFFFF` |
| Selected/pressed tint | `#E0EBDD` |
| Warning text | `#805A22` |
| Warning background | `#F5ECD9` |
| Destructive text/button | `#A33D35` |
| Destructive pale background | `#F8E7E3` |

### Fixed dark palette

| Role | Color |
|---|---|
| Main background | `#151B17` |
| Sheet/dialog surface | `#202923` |
| Search/secondary surface | `#28322B` |
| Primary text | `#EDF2EB` |
| Secondary text | `#AFBBAF` |
| Divider | `#364239` |
| Primary accent/button | `#B2D2B7` |
| On primary button | `#18271D` |
| Selected/pressed tint | `#334C3A` |
| Warning text | `#E2C084` |
| Warning background | `#392F20` |
| Destructive text | `#F0AAA0` |
| Destructive background | `#412824` |

Maintain readability. Do not use low-opacity gray to make important text disappear. Status must use words as well as color.

### Typography and geometry

- One Android-style sans family throughout: **Roboto**, without mixing decorative display fonts.
- App title: 28 sp, medium weight, line height about 34 sp.
- Secondary screen/sheet title: 22 sp, medium weight.
- Clipboard text: 16 sp, regular, approximately 23 sp line height.
- Buttons: 14 sp, medium, sentence case.
- Supporting text and timestamps: 13 sp, readable contrast.
- No essential in-app text smaller than 13 sp.
- Main horizontal inset: 20 dp.
- Spacing rhythm: 4, 8, 12, 16, 20, 24, and 32 dp.
- Touch targets: at least 48 dp in each relevant dimension.
- Search: 52 dp high with 16 dp corners.
- Primary buttons: 48 dp high with 14 dp corners.
- Dialogs: 24 dp corners. Bottom sheets: 24 dp top corners and a small handle.
- Icon glyphs: approximately 22–24 dp inside larger touch targets; consistent rounded stroke, about 2 dp.
- Fine dividers separate rows; do not place every clip inside a separate floating card.
- Shadows are minimal and used only to establish modal elevation.

These values are design specifications, not text to print inside the UI.

## 3. Image framing and consistency

Generate one standalone portrait app screen per image by default.

- Use a 9:20 screen aspect ratio, approximately 1080 × 2400 pixels or the closest supported portrait resolution. Treat the layout as roughly 412 × 915 logical units, not a claim about the phone's exact density.
- Use a straight-on, flat screen view. No perspective, hands, desk, floating devices, dramatic shadows, or giant hardware frames.
- Show a believable Android status area with a centered camera cutout and a bottom gesture area. No iPhone notch, Dynamic Island, iOS navigation, or iOS switch styling.
- Status time: 9:41. Battery: 86%. Keep these fixed across ordinary screens.
- Keep app controls clear of system bars and keyboard. A sheet must not place buttons behind the gesture region.
- Preserve the same type, icon style, palette, margins, app bar, and search geometry across screens.
- Put screen ID and any “Proposed addition” label in the response caption, outside the generated UI.
- Do not put color swatches, dimensions, arrows, or explanatory labels inside normal app screenshots.

The first image, S01, becomes the visual reference for every subsequent screen. If a later image drifts, regenerate it against S01 rather than inventing a new visual style. Use the same existing images as visual references when available.

## 4. Navigation and main-screen layout

The product has one home destination: clipboard history. Secondary tasks use sheets, dialogs, or simple back-navigation pages. No bottom navigation.

### Home layout, top to bottom

1. Android status region.
2. A 64 dp app bar: **ClipHistory** aligned left; vertical three-dot menu on the right in a 48 dp target. No large logo beside the title.
3. A compact recorder-status row. Healthy state uses a small green dot and **Recording**. It can reveal Recorder details when tapped, a proposed navigation refinement using existing diagnostic information.
4. A 52 dp search field: magnifier, **Search saved text**, and a clear icon only when text exists.
5. Small count line, **73 / 100 saved**, aligned left. Use **3 matches · 73 / 100 saved** for a filtered result example.
6. A continuous list of clip previews, separated by fine dividers. Each row has two or three lines of text at most, then a muted relative timestamp. Allow shorter rows for short content instead of forcing identical tall cards.
7. When more history exists beyond loaded entries, place a modest **Load more** control at the end of the loaded list. Do not render it as a permanent floating footer.
8. A small quiet footer, **Tap to copy and return · Hold for options**, above the gesture area when room permits. It may be omitted when the keyboard is open or space is tight.

In the ordinary healthy state, do not show Connect, Open Shizuku, diagnostics, or onboarding buttons. Reveal recovery controls only when they are useful.

For connection problems, replace the compact healthy status with one concise inline notice, about 80–120 dp high as needed. Keep existing history visible below it. Do not convert a temporary recorder problem into a blocking full-screen failure.

### Fixed synthetic content

Use these entries in this exact order unless the requested state says otherwise:

1. **“Let's meet at 10:30 by the library.”**: **Just now**
2. **“https://example.com/notes”**: **4 minutes ago**
3. **“Keep the interface quiet. Make the next action obvious.”**: **12 minutes ago**
4. **“Oats\nCoffee\nGreen apples”**: **27 minutes ago**; display actual line breaks, not the literal backslash characters.
5. **“The final draft is ready for review. I've added the updated measurements and the installation notes.”**: **1 hour ago**
6. **“A little less, but better.”**: **2 hours ago**

Only show as many rows as fit comfortably. Never shrink the text to force every entry above the fold. The total 73 represents the whole history, not only the visible rows.

These are plain text clips. The URL is not a rich card or a tappable browser link. Do not attach favicons, app logos, avatars, categories, or thumbnails.

For search screens, use query **notes** and three synthetic matching entries:

- “https://example.com/notes”
- “The final draft is ready for review. I've added the updated measurements and the installation notes.”
- “Workshop notes: simplify the setup and test the recovery flow.”

Highlight matching words with a subtle selected tint, not fluorescent yellow. Matching-word emphasis is a proposed presentation refinement, not an existing implementation claim.

## 5. Interaction rules

- Ordinary row tap copies the full saved text and returns through normal Android navigation. The copy should not wait for a decorative animation.
- Long-press opens a bottom action sheet. Include **Copy**, **View full text** (proposed), and **Delete**. The preview action must not replace default quick copying.
- Deleting removes the selected saved entry. Do not show Undo or promise recovery.
- Clear history always has a confirmation dialog and an explicit destructive button.
- Changes that remove older history, such as lowering the limit, must communicate that consequence before saving.
- Offline history can still be searched, read, and copied when readable. Mutating recorder/history settings is disabled until the helper connects, with a short explanation.
- A missing entry or failed copy produces a concise error, not a “Copied” success.
- System Back dismisses keyboard, modal, or secondary screen in the expected order.
- Proposed full-text preview is read-only. It does not edit or share data.
- Proposed copy-and-stay changes only what happens after a successful copy; default behavior remains copy-and-return.
- Quick Settings tile opens history and requests unlocking when necessary. Its state does not claim recording is active.

## 6. Motion direction, expressed as design intent

Static images cannot demonstrate actual performance. For a separate motion storyboard, use these intended interactions:

- Immediate local press feedback, about 80–100 ms, a restrained tint or Android-style ripple.
- Sheet appearance around 200–240 ms, smooth deceleration, without exaggerated bounce.
- Sheet dismissal around 160–200 ms.
- Search results update without list-wide entrance animations or layout jumps.
- Delete closes the gap with a small 140–180 ms layout transition after confirmed success.
- Recorder text changes with a subtle short crossfade; no permanent pulsing green dot.
- Preserve scroll position during updates. Do not unexpectedly move the list while the user is reading older clips.
- Avoid animated backgrounds, shimmer when real content is already available, parallax, confetti, and slow staggered row reveals.
- Respect reduced-motion preferences. Motion must never be required to understand a state or delay copying.

Do not put speed claims such as “zero latency” or “120 fps guaranteed” inside the app.

## 7. Screen catalog

Create each requested ID as a separate, readable image. Variants listed under a screen are separate requests, not tiny panels squeezed into its screenshot.

### S01: Main history, light theme

The canonical screen. Use the exact home layout, fixed content, **Recording**, and **73 / 100 saved**. Assume the recorder and its connection/storage test are healthy. The main focal point is the readable clip list. No connection CTA, banners, large cards, or illustration.

### S02: Main history, dark theme

Same content, positions, typography, and state as S01. Apply the fixed dark palette. Keep separation subtle and text readable. Do not introduce glow or change geometry.

### S03: Search in progress

Search field contains **notes**, with clear icon. Show **3 matches · 73 / 100 saved** and the three specified results. Show a restrained Android/Gboard-style keyboard below. Keep results above the keyboard, with no fabricated saved-history strip inside Gboard. This keyboard is system/third-party UI and its depiction is illustrative, not a custom app component.

### S04: Search with no results

Query: **mountain**. Count: **0 matches · 73 / 100 saved**. Center a small search outline and **No matching text** in the list area, with **Try a different word.** beneath it. Keep the populated search field and clear affordance visible. This is not empty history.

### S05: History loading

Same app bar and search geometry. Status: **Checking recorder…**. Within the list area show a small progress indicator and **Loading history…**. Do not show **0 saved**, “No saved text,” fabricated list content, or an infinite full-screen splash while data is unresolved.

### S06: Connected, empty history

Status: **Recording**. Count: **0 / 100 saved**. In the list area use one small clipboard outline, **Your next copy starts here**, and **Copy text in any app, then find it here.** No large tutorial, Add button, or fake clipboard records.

### S07: First setup, Shizuku not running

Preserve the home frame. Inline setup surface beneath the title:

- **Start recording**
- **Start Shizuku, then connect ClipHistory.**
- Primary: **Open Shizuku**
- Secondary: **Connect**

Below, show the empty list area and **Your saved text stays on this phone.** Shizuku is an external prerequisite, not a ClipHistory account. No onboarding carousel.

Variant S07b: Shizuku missing. Title **Shizuku is needed**. Copy **Install and start Shizuku to enable recording.** Provide **Setup help** and **Try again**. This is a proposed recovery presentation; do not invent an automatic installer or claim Open Shizuku works when it is absent.

### S08: Permission needed or denied

Default: inline notice **Allow Shizuku access**, description **Connect to request access for ClipHistory.**, button **Connect**.

Variant S08b, previously denied: **Shizuku access is off**, description **Allow ClipHistory in Shizuku's authorised applications.**, primary **Open Shizuku**, secondary **Try again**.

Show readable existing history beneath the notice if populated. Do not fabricate a custom Android/Shizuku grant dialog inside ClipHistory. Any permission UI is owned by the relevant system or third-party app.

### S09: Connecting

Status area: small progress indicator, **Connecting…**, supporting line **Checking recorder and storage.** Hide or disable repeated Connect activation. Preserve already loaded history. Do not show Recording before success.

### S10: Listening, connection test not run

Home with history visible. Status: **Listening**. A concise secondary line/action: **Run connection test**. Do not present an alarming error or a giant checklist. A listener registration is not the same as a successful event/storage test.

### S11: Recording paused

Status row uses a pause icon and **Recording paused** with **Resume** on the right. Saved history remains readable, searchable, and copyable. Use neutral styling rather than destructive red. Overflow switches Pause recording to Resume recording.

### S12: Recorder stopped, saved history available

Inline notice **Recording unavailable**. Description **Your saved history is still available. Start Shizuku to record new copies.** Actions: **Open Shizuku** and **Reconnect**.

Show the original history beneath, count **73 / 100 saved · Offline**. Here Offline means recorder disconnected, not lack of internet. Do not show a Wi-Fi-off icon or suggest the app requires a network connection.

### S13: Overflow menu

Anchor a clean menu below the app bar's three-dot control. Show these rows in this order:

1. **Pause recording** or **Resume recording**, according to state.
2. **History limit**
3. **Add Quick Settings tile**
4. **Connection test**
5. **Diagnostics**
6. **Appearance**: proposed addition, identified only in caption.
7. **Copy behavior**: proposed addition, identified only in caption.
8. **Help & privacy**
9. **Clear history**: restrained destructive text, separated from ordinary options.

Use at least 48 dp per row. Fit it below the top app bar without squeezing labels. Keep the background history visible, with modest modal dimming only if appropriate to the chosen menu treatment.

### S14: Entry action sheet

Bottom sheet, approximately 40% of screen height or the content's natural height. Handle, heading **Saved text**, a two-line preview of the selected third clip, and timestamp **12 minutes ago**.

Actions in a simple vertical list: **Copy**, **View full text**, **Delete**. Delete is red but not a giant red panel. No favorite, edit, share, pin, or undo action. View full text is proposed. The background remains the same home screen with a dim scrim.

Offline variant: keep Copy and proposed View full text available; disable Delete with **Connect the recorder to delete saved text.**

### S15: History limit

Compact bottom sheet with heading **History limit**. Body: **Keep the latest 20–500 texts.** Numeric outlined field labeled **Saved texts**, value **100**. Supporting text: **Lowering the limit deletes older entries.** Bottom actions **Cancel** and primary **Save**.

Use a numeric field, not an invented slider with imprecise values. For the keyboard-open variant, show Android numeric input and keep actions above the keyboard.

### S16: Invalid history limit

Same sheet with value **501**. Field outline and helper text use restrained error styling: **Enter a number from 20 to 500.** Preserve the input. No toast-only error, data loss, or unrelated warning icon.

### S17: Clear confirmation

Centered dialog over dimmed home. Heading **Clear saved history?** Body: **This deletes all saved texts and their recovery copy. Gboard and your current Android clipboard are unchanged.** Actions **Cancel** and destructive **Clear history**.

No prechecked checkbox or misleading reversible-delete language. A subsequent cleared state can use S06 with a brief **History cleared** message after actual success.

### S18: Connection test confirmation

Centered dialog. Heading **Test the connection?** Body: **This replaces your current clipboard with harmless test text. Your saved history stays unchanged.** Secondary concise line: **Checks a clipboard event and storage access.** Actions **Cancel** and **Run test**.

Do not run the test automatically in the design or omit its clipboard side effect.

### S19: Connection test passed

A calm Recorder details sheet, a proposed presentation of existing diagnostics. Title **Recorder**. Small check icon, **Connection test passed**. Rows: **Clipboard event: Received**, **Storage check: Passed**, **Recording: Active**.

Footer: **This checks the connection now, not long-term background reliability.** Actions **Done** and subtle **View diagnostics**. Keep the explanation readable; no giant celebratory checkmark, confetti, or “100% reliable” claim.

Variant S19b: testing in progress with **Waiting for test event…**. Variant S19c: **Connection test needs attention**, **No test event was confirmed. Check Shizuku and try again.**, actions **Try again** and **View diagnostics**. Do not show passed storage if that was not established.

### S20: Diagnostics, readable technical detail

A full-height secondary page with Back, heading **Diagnostics**, and **Copy report** action in an accessible position. Use grouped key/value rows rather than a wall of tiny text.

Top group:

- **ClipHistory: 1.0.0**
- **Device: Pixel 8 Pro**
- **Android: 17 / API 37**
- **Mode: Shizuku recorder**
- **Daemon UID: 2000**
- **Listener: Registered**
- **Storage check: Passed**
- **Connection test: Passed**

Below: **Saved this session: 73**, **Sensitive clips skipped: 2**, **Oversized clips skipped: 0**, **Unsupported clips skipped: 0**, **Overload drops: 0**, **Recovered damaged snapshot: No**, **Issue: None**.

Advanced details may continue below the fold and include security patch, snapshot generation, and actual clipboard API signature. Do not invent these values to fill the image. Show a collapsed **Advanced details** row when space is insufficient.

Footer: **This report contains no saved clipboard text.** Copying the report replaces the current clipboard, so include a subtle **Copies to your clipboard** label near that action. These example values are design data, not actual device verification.

### S21: Help & privacy

A readable secondary page with Back and title **Help & privacy**. Short sections:

**Keep using Gboard**
“Copy text as usual. Open ClipHistory and tap an entry to copy it back.”

**After restarting your phone**
“Start Shizuku again, then open ClipHistory. Copies made while recording is stopped cannot be recovered.”

**Only on this phone**
“No accounts, ads, analytics, or cloud sync.”

**Sensitive text**
“Text marked sensitive by Android is skipped. Not every app marks private text. Pause recording when needed.”

**Limits**
“Text only. Entries over 64 KiB are skipped. Only the main phone profile is supported.”

**Screen privacy**
“Screenshots of history are blocked.”

Footer: **ClipHistory 1.0.0 · MIT license**. Show a scrollable page, not every paragraph compressed into the viewport. Do not add a screenshot toggle or universal password-detection claim.

### S22: Add Quick Settings tile

App-owned instructional sheet: heading **Open history faster**, a small monochrome Clipboard tile preview, description **Add Clipboard to Quick Settings to open your saved text.**, primary **Add tile**, secondary **Not now**.

Below: **You can also add it from Quick Settings → Edit.** This sheet is a proposed pre-request presentation for the existing add-tile capability. The actual system approval remains Android-owned.

Variant S22b: **Clipboard tile is available**, with **Done**. Do not imply the tile records independently or restarts Shizuku itself.

### S23: Quick Settings context

Illustrative Android Quick Settings view with a tile labeled **Clipboard**, subtitle **Open history**, and the same clipboard glyph. The tile looks like a launcher action, not a green recording toggle.

The other system controls are neutral contextual elements, not product features. Do not restyle the whole Android panel in ClipHistory's brand palette or present this conceptual system surface as an exact verified Android 17 screenshot. Caption it **System-owned context, illustrative**.

### S24: Full-text preview, proposed addition

Read-only secondary screen with Back and title **Saved text**. Timestamp **Today, 09:29**. Display the entire synthetic passage, preserving paragraph breaks:

“Keep the interface quiet. Make the next action obvious.

The main screen should focus on saved text. Search belongs near the top, and copying should take one tap.

Put setup, diagnostics, and uncommon actions behind the menu. Show recovery steps only when they are needed.”

Use 17 sp body type with generous but compact line height. No enclosing giant card. Bottom action **Copy and return** with a quieter **Delete** action. With the proposed Return after copying setting off, the primary label becomes **Copy**. No edit caret, tagging, or share button. Delete is unavailable when disconnected, matching the underlying capability.

### S25: Appearance, proposed addition

Bottom sheet with title **Appearance** and one sentence, **Choose how ClipHistory looks.** Three radio rows: **System**, **Light**, **Dark**. System selected. Small supporting text **Matches your phone's theme.** No accent color picker, wallpaper browser, font picker, or custom themes. Show a small restrained preview only if the sheet still reads clearly.

### S26: Copy behavior, proposed addition

Compact sheet titled **Copy behavior**. One setting row: **Return after copying**, Android-style switch on. Supporting text: **Close ClipHistory after copying a saved entry.** Below, short alternative explanation: **Turn off to stay in your history.** Default on preserves existing behavior. No unrelated preferences.

### S27: Copy-and-stay feedback, proposed addition

Same home as S01, with the third row briefly tinted after a successful copy. Show a short snackbar **Copied** above the gesture area. Do not show Undo. This image applies only when the proposed Return after copying setting is off. In default mode, the Activity closes and there is no persistent copied-state screen inside ClipHistory.

### S28: Recoverable error states

Generate one variant per request, using the same error language and layout:

- **S28a: History read failed:** retain the app frame, heading **History could not be read**, description **Your existing files have not been erased.**, action **Try again**, secondary **Diagnostics**. Do not show zero history as though data is gone.
- **S28b: Copy failed:** small dialog **Couldn't copy text**, description **Android did not allow this copy. Your saved text is unchanged.**, action **OK**. Do not show success or close the app as if copying succeeded.
- **S28c: Entry no longer available:** brief **This entry is no longer available.** message and refreshed list.
- **S28d: Recorder connection timed out:** inline **Couldn't connect**, description **Check Shizuku, then try again.**, actions **Open Shizuku** and **Reconnect**; keep saved history visible.
- **S28e: Mutation unavailable offline:** action is disabled and explained by **Connect the recorder to change saved history.** Copy/search remain available.
- **S28f: Operation failed:** concise **Couldn't complete the action**, useful recovery action, and **View diagnostics**. If an operation may have partially completed, do not claim no data changed; show **Check the current history before trying again.**

### S29: Component and state reference board

An optional separate design-system image, not an app screen. Show typography, light/dark tokens, app bar, search field, two history rows, buttons, icon buttons, input error, selected row, snackbar, and recorder states: **Recording**, **Listening**, **Connecting**, **Paused**, **Unavailable**, **Needs attention**.

Label components outside their examples. Use readable scale. This is the only board where palette swatches and design labels are appropriate.

### S30: Motion storyboard

An optional four-frame interaction board with small external captions: **Rest**, **Press**, **Sheet opening**, **Sheet settled**. Demonstrate a long-press on the third entry opening S14. Keep the same list, sheet, and geometry across frames. Annotate the approximate timings outside the phones. This illustrates motion intent only; it is not proof of a working animation.

### S31: Launcher and tile icon assets

An optional icon study on a neutral background: the same clipboard-and-two-lines glyph shown as a launcher icon and a monochrome tile glyph. The launcher uses a eucalyptus field and warm off-white glyph, with balanced internal safe space for Android masking. The tile uses a single-color silhouette/outline with no text baked into it. No mock app-store ratings, packaging, or alternate brands.

## 8. Coverage and consistency rules

Before generating each image, privately check:

- Correct screen ID and actual state.
- Same visual system and geometry as S01.
- Exact labels, synthetic content, and believable timestamps/counts.
- Existing capability versus proposed addition correctly distinguished in captions.
- No source-app attribution, invented roadmap features, network dependence, or unsupported privacy promises.
- Search has room for the keyboard and important actions stay above system insets.
- Error/paused/disconnected states do not look like success.
- Offline saved history remains usable where supported.
- Default row tap remains copy-and-return.
- Text is readable without enlarging the image.
- No accidental iOS patterns, cluttered cards, decorative texture, or giant empty sections.

If there is a conflict, prioritize truthful behavior, readability, the specified hierarchy, then decoration. Do not silently solve crowded content by shrinking fonts. Use scrolling or the specified secondary screen.

## 9. Generation protocol

Start by generating **S01 only**, at the largest supported portrait resolution. It is the visual anchor. Do not generate a tiny contact sheet or substitute an essay for the image.

After each image, give a short caption with its screen ID/name and whether it contains a proposed addition. Then list the next ID to generate. Keep captions outside the app artwork.

When I say **Next**, generate the next main numbered screen from this catalog, using S01 and the most relevant existing screen as references. Generate lettered variants when I request their full ID. Do not automatically omit the remaining catalog because only a limited number of images can be produced in one response.

When I say **Generate S15**, generate that exact screen with the same visual system. When I say **Generate S15 in dark**, use the fixed dark tokens and preserve the layout. When I say **Revise**, change only the requested attributes and preserve the rest.

If a session or reference image is missing and you cannot maintain visual continuity, ask me to attach S01 rather than pretending you can reproduce an unseen image exactly.

Produce polished, practical Android product-design images. The result should look ready to translate into real components, with a calm reading experience and a one-tap path back to my work.

# END IMAGE PROMPT

---

## Suggested review order

Start with S01, S02, S03, S13, S14, and S24 to judge the visual system and main interaction flow. Then create connection/recovery states, settings, privacy/diagnostics, and the remaining variants. Generate boards last, once the screen system is stable.

Approve the look of S01 before investing in the full set. If changing its palette, typography, or geometry later, regenerate affected screens against the revised anchor.

## Source boundary

Existing behavior was checked against `MainActivity.kt`, `HistoryAdapter.kt`, `ClipboardTileService.kt`, `DaemonClient.kt`, and `core/Model.kt` in the local ClipHistory project. New sheet/page treatments, clearer recovery copy, and highlighted matches are design proposals. Full-text preview, an in-app appearance preference, and copy-and-stay are explicitly proposed features. No application source was modified to implement this brief.
