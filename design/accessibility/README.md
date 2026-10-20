# Contrast tool

Measures the contrast of text over the animated backgrounds, and over the glass panels planned for
them, as WCAG 2.x defines it. `docs/ux/accessibility.md` has the method and the baseline numbers.
Sprint 10 uses it to choose the glass look of a panel and to fix the text tokens, and runs it again
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

   ```
   BOREALIS_FRAMES_OUT=<frames> ./gradlew :borealis:designsystem:testDebugUnitTest --tests '*BackgroundFrameExport*' --rerun
   ```

   In PowerShell, set `$env:BOREALIS_FRAMES_OUT = "<frames>"` first. `--rerun` makes Gradle run the
   test even when it is up to date. Without the variable the test is skipped and writes nothing.

2. **The clips.** 24 frames of each clip in `designsystem/src/main/res/raw`, evenly spaced over its
   loop, at the clip's own 1920 x 1080 px, which is a TV at xhdpi:

   ```
   python -I design/accessibility/contrast.py extract <frames>
   ```

3. **The measurement.** It reads the colours from `design/tokens.json`, writes the JSON and prints
   the tables of `docs/ux/accessibility.md`. It takes minutes, one frame to a process:

   ```
   python -I design/accessibility/contrast.py measure <frames> --json <result.json> --jobs 4
   ```

Run the tests from the repository root:

```
python -m unittest discover -s design/accessibility
```

## What it measures

For each style, each frame and each text token (`ink`, `ink-2`, `ink-3`, `ink-4`, `accent-soft`):

- (a) The text straight on the background.
- (b) The text on an opaque surface: `page`, `surface` or `surface-raised`. It does not depend on
  the frames.
- (c) The text on a glass panel: the frame blurred, then the `surface` colour on it at an alpha.
  The grid is an alpha of 0.55, 0.70 or 0.85 and a blur radius of 16, 32 or 48 dp, nine
  candidates. The dp are turned into pixels with the density of the frame.

The statistic is taken over the whole frame, which is deliberately conservative: the worst case is
the lowest ratio of any pixel (a single star counts), and the JSON also holds p1 and p5, the
ratios below which 1% and 5% of the pixels are. A style's figure is the worst of its frames.

The blur is a Gaussian of sigma `0.57735 * radius_px + 0.5`, which is how Skia (and so
`RenderEffect.createBlurEffect`) turns a radius into a sigma. Pixels are blurred and mixed as sRGB
values, as the platform does, and the edge of the frame repeats. The clips have no colour tags, and
Android decodes an untagged HD stream as BT.709 in limited range, so `extract` does too.

The clips are measured at TV size only. On a phone the player shows a centre crop of the same
frame, so the whole-frame figure covers it.

## The JSON

`meta` has the date, the revision, the colours and the grid. `sources` lists the folders read.
`background` (a) and `glass` (c) have a row for each style, device, token (and alpha and `blur_dp`):
`min`, `p1`, `p5`, `worst_frame` and `per_frame_min`. `surfaces` (b) has a `ratio` for each
surface and token.

## Colour vision

`contrast.simulate_cvd(rgb, kind)` and `simulate_hex(value, kind)` give a colour as someone with
`protanopia`, `deuteranopia` or `tritanopia` sees it (Machado, Oliveira and Fernandes, 2009,
severity 1.0). A later task uses it to check that the status colours differ without hue.
