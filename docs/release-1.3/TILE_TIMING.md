# Native tile timing and frame review

Requested schedule: ten openings after 60 seconds idle, then two after 120 seconds idle. This is **12 openings and 840 seconds of intentional waits**. Recording, UI setup and checks add wall time.

The first full Pixel run completed all 12 openings in 1,094.109 seconds. Every panel showed three synthetic entries, stayed visible throughout its bounded recording and closed with one native tap. The earlier 2–4 second service-binding waits and immediate dismissal did not recur.

All **1,158 encoded frames** were decoded in presentation order and reviewed as crops of the owned panel. Compositor timelines were analyzed too. Video is variable frame rate, omitting unchanged frames; this is not every physical refresh. Raw videos, full Quick Settings images and traces stay private and are not distributed.

## Findings

- Android SystemUI expands the clipboard icon and fades into the panel. The app reserves the three-row loading space and adds no window animation.
- No-animation launch flags, disabling the starting preview and a translucent floating theme did not remove the remote SystemUI transition on this Pixel.
- Where startup input survived the buffer, first app frame completion was about 58–108 ms after touch-up. This is not the end of the system transition.
- Trial 10 contains one late app frame: `App Deadline Missed, App Resynced Jitter`, 29.68 ms. Startup packets in trials 5, 8 and 11 were overwritten in the original shared buffer. No zero-jank claim is supported.
- The later 30-opening test separates app pre-draw and accessibility availability; see `COVERAGE.md`.

The native driver now separates frame timeline and ftrace buffers. The second full run before the measured row-reservation fix also passed **12/12**, with 840 seconds idle and 1,098.407 seconds wall time. All **1,183 encoded frames** were decoded and reviewed. Each panel stayed open and closed with one tap. All twelve traces retained startup input and reported no positive error statistics in the trace processor.

The second run contains 297 app-window frame records, with two late presentations: trial 2 (20.05 ms) and trial 12 (23.18 ms), both labelled `App Deadline Missed, App Resynced Jitter`. These counts describe this bounded observation, not every device refresh or future opening. The trial 12 trace includes a 12.47 ms window relayout with resize; a separate instrumentation observation checks whether content loading changes the measured window size. The icon expansion/fade remains visible. Literal zero delay, zero extra system animation and zero late frames have **not** been achieved.

Selected second-run numeric results are in `TILE_NUMBERS.json`; final results are in `TILE_FINAL_NUMBERS.json`. Accessibility XML dump timings include the dump's own several-second overhead and are not app loading measurements. Cumulative `dumpsys gfxinfo` counters are not counted as per-trial jank.

## Measured resize fix

Thirty additional openings confirmed that a two-line native button measured 194px, while its 64dp reservation was 192px. The loading window was 993px tall and became 995px when entries arrived. The app now reserves the height of the same native two-line button at the current font scale. Thirty openings on that fix retain an identical **1080x999px** window throughout loading; app-loaded pre-draw p95 is **123.97ms**, maximum **147.35ms**.

The third full timed schedule on the fix passed **12/12**, with 840 seconds idle and **1,096.203 seconds** wall time. All **1,147 encoded frames** were reviewed in order. No immediate dismissal or 2–4 second binding stall recurred. First app-frame completion was **54.32–113.17ms after touch-up**, which precedes completion of Android's visible launch animation. All twelve traces retained startup and reported no positive parser-error statistics. The 300 app-window frame records have **no App Deadline Missed classification**; one late presentation is labelled **SurfaceFlinger Scheduling** (8.20ms frame duration). That is a bounded observation, not a universal zero-jank guarantee. The system icon expansion/fade remains visible.

| Final app-window classification | Records |
|---|---|
| On-time Present / None | 283 |
| On-time Present / Buffer Stuffing | 11 |
| Early Present / SurfaceFlinger Scheduling, App Resynced Jitter | 2 |
| Late Present / SurfaceFlinger Scheduling | 1 |
| Unspecified Present / Non Animating | 3 |

No app deadline miss does not mean every record has a None classification. These compositor observations remain part of the disclosed result.

The API 34 emulator's first test attempt after repeated replacement/add/remove operations encountered a tile cached as unavailable with stale SystemUI bindings. Rebooting the owned emulator cleared that precondition and the native 20-assertion suite passed. API 36 passed directly. These observations do not establish every manufacturer/update path.

## Reproduction

Build/install only the isolated validation app and test APK. Seed three synthetic entries using its offline tile suite and stop its recorder first. The script requires a debuggable validation target and no live validation helper.

```text
python tools/test-tile-timing.py --adb <absolute-sdk-adb-path> --serial <validation-device> --output <new-private-report-directory>
```

The script performs native tile/Close taps, records bounded video and Perfetto traces, checks loaded entries and stability, and restores the validation screenshot preference. It never copies entries, writes clipboard text, clears production data or changes global animation/battery settings. `--smoke` runs two immediate trials and does not substitute for the full schedule.
