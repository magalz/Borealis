# Contrast tool

Measures the contrast of text over the animated backgrounds, and over the glass panels drawn on
them, as WCAG 2.x defines it. `docs/ux/accessibility.md` has the method and the baseline numbers.
Sprint 10 uses it to choose the glass look of a panel and the text colours on it, and runs it again
after the fixes.

## What it needs

- Python 3.9 or later with `numpy` and `Pillow` (tried with Python 3.14, numpy 2.4 and Pillow 12.2).
  Nothing else: the tests are `unittest`.
- `ffmpeg` and `ffprobe` on `PATH`, to take the frames of the four clips.
- The Gradle build of `apps/android`, to take the frames of the two styles that are drawn in
  Compose.

## Measure

Use one folder for the frames, outside the repository. Its sub-folders are named
`<Style>-<device>`, where `<Style>` is the name of the `BackgroundStyle` and `<device>` is `tv` or
`phone`. Each holds the frames `00.png`, `01.png` and so on.

1. **The drawn styles.** `AuroraCurtains` and `SoftGlow`, 24 evenly spaced points of the 20 s
   loop, on a TV (1920 x 1080 px, xhdpi) and on a phone upright (1233 x 2673 px, xxhdpi). The
   `BackgroundFrameExport` test of `:borealis:designsystem` renders them with the same
   `DrawnBackground` the app uses, and it writes only when `BOREALIS_FRAMES_OUT` is set. From
   `apps/android`:

   ```sh
   BOREALIS_FRAMES_OUT=<frames> ./gradlew :borealis:designsystem:testDebugUnitTest --tests '*BackgroundFrameExport*' --rerun
   ```

   In PowerShell, set `$env:BOREALIS_FRAMES_OUT = "<frames>"` first. `--rerun` makes Gradle run the
   test even when it is up to date. Without the variable the test is skipped and writes nothing.

2. **The clips.** 24 frames of each clip in `designsystem/src/main/res/raw`, evenly spaced over its
   loop, as each device shows them. On a TV that is the whole 1920 x 1080 px clip. On a phone
   upright it is what `VideoBackground` shows: the clip scaled to the height of the screen, about
   2.5 times larger, and the middle 1233 x 2673 px of it. So a blur of a given size in dp reaches
   less far into the clip on a phone than on a TV:

   ```sh
   python -I design/accessibility/contrast.py extract <frames>
   ```

3. **The measurement.** It reads the colours from `design/tokens.json`, writes the JSON and prints
   the tables of `docs/ux/accessibility.md`. It takes minutes, one frame to a process:

   ```sh
   python -I design/accessibility/contrast.py measure <frames> --json <result.json> --jobs 4
   ```

Run the tests from the repository root:

```sh
python -m unittest discover -s design/accessibility
```

## What it measures

For each style, each frame and each text token (`ink`, `ink-2`, `ink-3`, `ink-4`, `accent-soft`):

- (a) The text straight on the background.
- (b) The text on an opaque surface: `page`, `surface` or `surface-raised`. It does not depend on
  the frames.
- (c) The text on a glass panel: the frame blurred, then the `surface` colour on it at an alpha.
  The grid is what Settings offers: an alpha of 0.30, 0.40, 0.50, 0.60, 0.70 or 0.85 and a blur
  radius of 8, 16, 24, 32 or 48 dp, and also a radius of 0, which is no blur (the glass of API 28 to
  30), 36 candidates. The dp are turned into pixels with the density of the frame.

The statistic is taken over the whole frame, which is deliberately conservative: the worst case is
the lowest ratio of any pixel (a single star counts), and the JSON also holds p1 and p5, the
ratios below which 1% and 5% of the pixels are. A style's figure is the worst of its frames; for
p1 and p5 it is the lowest of the frames' p1 and p5.

The blur is a Gaussian of sigma `0.57735 * radius_px + 0.5`, which is how Skia (and so
`RenderEffect.createBlurEffect`) turns a radius into a sigma. Pixels are blurred and mixed as sRGB
values, as the platform does, and the edge of the frame repeats. The clips have no colour tags, and
Android decodes an untagged HD stream as BT.709 in limited range, so `extract` does too.

The clips are drawn on a `TextureView`, which an in-app blur can sample. The tool models the panel,
not that path.

## The table for the app

The app does not read the JSON. Once the measurement is done,

```sh
python -I design/accessibility/contrast.py glass-table <result.json>
```

writes `GlassContrastTable.kt` in `borealis/designsystem/.../glass/`: for each style, blur and tint,
the luminance of the brightest pixel behind the text, over every frame of the TV and of the phone.
It is the contrast of `ink` turned back into a luminance, `Lb = (L(ink) + 0.05) / ratio - 0.05`,
rounded up. The tint floors and the text colours of the glass are worked out from it in Kotlin
(`GlassRules`), so that rule lives in one tested place. Commit the file with the measurement it
came from, and do not edit it by hand.

```sh
python -I design/accessibility/contrast.py glass-colours
```

prints the floors and the text colours that the app works out from that file, as the tables of
`docs/ux/accessibility.md` have them. It is the same rule written again in Python, and its tests pin
the colours that the Kotlin tests pin. A channel that lands on half a level rounds up, as Kotlin's
`roundToInt` does.

## The halo of text on the background

Text that stands on the background, outside any glass, gets a soft dark halo (issue #33). The same
frames give the contrast that the halo keeps, and the candidate that the app draws:

```sh
python -I design/accessibility/contrast.py halo <frames> --json <halo.json> --jobs 4
```

The sample text of four styles of the app (title, label, body and small text, in IBM Plex at the
sizes of `design/tokens.json`, on a TV and on a phone) is laid over each frame. Each glyph is
widened by a stroke, blurred, and laid in `page` at an opacity, and the ratio of `ink` and `ink-2`
is taken at the pixels that are not in a glyph and are within 2 px of one. The worst case is over
every place of the sample on the frame, a pixel apart: it is the lowest ratio over every colour of
the frame with every alpha of the ring, found in seconds, and with no halo it is Table A. p1 and p5
are over 6 x 4 places, for information. The grid is a
stroke of 0 to 3 dp, a blur of 2, 4 or 8 dp and an opacity of 60, 80 or 100 %, and no halo. The
command prints Tables D, E and F of `docs/ux/accessibility.md` and the smallest candidate that
keeps 4.5:1 (3:1 for the title of a TV), with 2% to spare. `--texts body title` measures only some of
the styles. It takes a minute or two with 8 jobs; the JSON has the worst case, p1, p5 and the worst
frame of every text style, token and candidate. `BackdropHalo` in `:borealis:designsystem` has the
choice, and a test keeps it equal to the line "The choice: ..." of the notes.

## The JSON

`meta` has the date, the revision, the colours and the grid. `sources` lists the folders read.
The halo's JSON has the same `meta` and `sources`, and rows in `halo`.
`background` (a) and `glass` (c) have a row for each style, device, token (and alpha and `blur_dp`):
`min`, `p1`, `p5`, `worst_frame` and `per_frame_min`. `surfaces` (b) has a `ratio` for each
surface and token.

## Colour vision

`contrast.simulate_cvd(rgb, kind)` and `simulate_hex(value, kind)` give a colour as someone with
`protanopia`, `deuteranopia` or `tritanopia` sees it (Machado, Oliveira and Fernandes, 2009,
severity 1.0). A later task uses it to check that the status colours differ without hue.
