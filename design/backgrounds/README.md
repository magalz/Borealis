# Animated backgrounds

The Library and Settings screens sit on one animated background, chosen from seven styles
(issue #30, owner decision of 2026-10-18). `GlassHost` in `:borealis:designsystem` draws it, through
`BorealisBackground`, which is internal to that module, and `BackgroundStyle` names the styles. The
Compose styles have no files; the four video styles are the clips below.

| Style | Working name | Kind | Source |
| --- | --- | --- | --- |
| Off | Off | Plain `page` colour | None |
| 1a | Aurora curtains | Compose | Drawn in code, `AuroraBackground.kt` (the default) |
| 1b | Soft glow | Compose | Drawn in code, `AuroraBackground.kt` |
| 2a | Northern lights | Video | ElevenLabs, see below |
| 2b | Light mist | Video | ElevenLabs, see below |
| 3a | Flowing light | Video | `gen_loops.py` |
| 3b | Starfield | Video | `gen_loops.py` |

The names are working names: they live in string resources
(`strings_background.xml` of the design system), in English only.

## The clips

They are in `apps/android/borealis/designsystem/src/main/res/raw/`, with a still of the first
frame next to each in `res/drawable-nodpi/`. The still is what a screen shows while the system
removes animations (no player and no decoder), and until the player draws its first frame.

| Style | Clip | Still | Length | Frame rate |
| --- | --- | --- | --- | --- |
| 2a | `background_northern_lights.mp4` | `background_northern_lights_still.webp` | 9.4 s | 24 |
| 2b | `background_light_mist.mp4` | `background_light_mist_still.webp` | 6.0 s | 24 |
| 3a | `background_flowing_light.mp4` | `background_flowing_light_still.webp` | 20 s | 30 |
| 3b | `background_starfield.mp4` | `background_starfield_still.webp` | 20 s | 30 |

Every clip is 1920 x 1080 H.264 (High profile, level 4.1, 4 reference frames), silent and a seamless
loop, encoded with `-crf 23 -bf 0 -refs 4 -level 4.1 -tune grain`:

- No B-frames: they made the quality of smooth gradients pulse every few frames, which looked like
  stutter.
- Level 4.1 and 4 reference frames: the first encodes were level 5.0, which a decoder of level 4.x
  could refuse.

The player crops from the centre, so a clip must be 16:9 (the code assumes it).

## Provenance

- **2a, 2b.** Generated on the owner's ElevenLabs account on 2026-10-18. The start frames come from
  GPT Image 2.5 and the videos from Kling 3.0 Pro; the loop seams were closed with a crossfade by
  `loopfade.py`. The owner confirmed on 2026-10-19 that their ElevenLabs plan allows bundling the
  generated clips in the app.
  - 2a: a Kling 3.0 Pro video of about 12 s from the start and end frame, closed with
    `python -I loopfade.py 2a-master.mp4 2a.mp4 24 0 40` (24 frames of crossfade, no frames
    skipped at the start, the 40 last frames dropped: its tail was slow).
  - 2b: regenerated with Kling 3.0 Pro for 8 s from the start frame only, closed with
    `python -I loopfade.py 2b2-master.mp4 2b.mp4 48`.
- **3a, 3b.** Generated in code by `gen_loops.py`: every time-dependent term makes a whole number
  of cycles over the 20 s loop, so the last frame flows into the first. It writes
  `3a-noise-field.mp4` (style 3a) and `3b-particle-sky.mp4` (style 3b).
  `python -I gen_loops.py <out_dir>` needs numpy, Pillow and `ffmpeg` on `PATH`.
- **Masters.** The ElevenLabs masters (`2a-master.mp4`, `2b2-master.mp4`) are not committed; the
  files in `res/raw` are the result of `loopfade.py` on them.

## Stills

Frame 0 of each clip, scaled to 1280 x 720 and stored as lossy WebP (5 to 16 KB each):

```sh
ffmpeg -i background_northern_lights.mp4 -frames:v 1 -vf scale=1280:720:flags=lanczos \
  -c:v libwebp -quality 88 background_northern_lights_still.webp
```

## Colours

The Compose styles use the design tokens `page`, `accent-strong` (`#7C3AED`) and `accent`
(`#A78BFA`), plus a deep teal (`#1F8A8A`) that has no token, so it is a named constant in
`AuroraBackground.kt`. The code-made clips use the same four colours.

## What the glass table depends on

`GlassContrastTable.kt` in `:borealis:designsystem` is measured over frames of these backgrounds
(`design/accessibility/README.md`), and the glass tint floors and text colours follow from it.

- **The four clips** are hashed into the table. `GlassContrastTableClipsTest` fails when a clip in
  `res/raw` is not the one that was measured, so encode a clip again only together with a new table.
- **The two drawn styles** have no clip, so nothing detects a change to them. After a change to
  `Hues`, `BANDS`, `SPOTS` or the constants of the drawing in `AuroraBackground.kt` (and likewise to
  the tokens `page`, `accent` and `accent-strong` that it takes), take the frames again with the
  `BackgroundFrameExport` test, run `contrast.py measure` on them and `contrast.py glass-table` on
  the result, and commit the table.
