# Accessibility: contrast over the background

Sprint 10 aims at WCAG 2.2 level AA on every screen, and at 7:1 (AAA) for text
when high contrast is on. This page records how the contrast of text over the
animated background is measured, the baseline measured before any fix, how the
table that the glass of the app reads comes out of that measurement, the halo
of the text that stands on the background, and what the icons and the text field
on it keep. It then records the high contrast and colour-blind palettes, what the
screen readers and the touch targets need, and a WCAG 2.2 checklist. Sprint 11
adds two sections before the checklist: the candidates of the accent colour
(Accent candidates) and the halo in each candidate font (Halo per font). Sprint 15
adds a third, The stream: glass, halo and focus, which says what the settings do in a
stream and what a screen reader and a D-pad find in it. The tool is in [`design/accessibility/`](../../design/accessibility/README.md); its
README says how to run it again. The decisions that follow from the numbers are in
[ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md).

- Date: 2026-10-20. The sections on the halo, the icons and the text field, which
  came later, carry their own date and revision. The glass table of the app was
  regenerated on 2026-10-21 at `cdd48edc`, with identical numbers: it now also
  holds the hash of each clip. The two sections of Sprint 11, on the accent
  candidates and on the halo per font, were measured on 2026-10-21 and carry
  their own revision.
- The section on the stream (Sprint 15) was written on 2026-10-29 at `2e76dfcfb`. Its
  numbers come from the commits of the video glass and from
  [ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md); the
  tool's own check of them is `VideoGlassHaloTest` in `design/accessibility/test_contrast.py`.
- Revision measured: `fedc4ce0`. It widens the grid of the glass candidates of
  `fec048bb`, the first measurement. The frames are those of that first
  measurement, taken with the app code of `109e7dff`, the Sprint 9 merge: the
  drawn backgrounds and the clips have not changed since. So Tables A and B are
  as they were, and Table C is new.
- This page is a test input of `:borealis:designsystem`: a test reads the halo
  that it chooses (see Halo). Every edit of it runs the unit tests of that
  module again.

## Method

- **Colours.** Every colour comes from [`design/tokens.json`](../../design/tokens.json).
  The text tokens are `ink`, `ink-2`, `ink-3`, `ink-4` and `accent-soft`. The
  surfaces are `page`, `surface` and `surface-raised`.
- **Frames.** 24 frames of each style on each device, evenly spaced over its
  loop. That is 12 sets of frames.
  - Aurora curtains and Soft glow are drawn by the `BackgroundFrameExport` test
    with the same `DrawnBackground` the app uses, on a TV (1920 x 1080 px, xhdpi)
    and on a phone upright (1233 x 2673 px, xxhdpi).
  - The four clips are read with ffmpeg and cut as each device shows them, as
    `VideoBackground` does. A TV shows the whole 1920 x 1080 px clip. A phone
    upright scales the clip to the height of the screen, about 2.5 times larger,
    and shows the middle 1233 x 2673 px of it. So a blur of a given size in dp
    reaches less far into the clip on a phone. The clips have no colour tags, so
    they are decoded as BT.709, as Android does.
- **Ratio.** The WCAG 2.x relative luminance and contrast ratio, with the sRGB
  threshold of 0.04045. A text colour is taken at full opacity.
- **Statistic.** For one frame, the worst case is the lowest ratio of any pixel.
  For a style, it is the lowest of its 24 frames. The whole frame is deliberately
  conservative: a single bright star behind a letter counts. p5 is the lowest,
  over the frames, of the ratio below which 5% of the pixels are. It is for
  information.
- **Three cases.**
  - (a) The text straight on the background. The Settings rows draw no surface
    today, so this is what they show.
  - (b) The text on one of today's opaque surfaces.
  - (c) The text on a modelled glass panel: the frame blurred, then the `surface`
    colour on it at an alpha. The grid is what Settings offers: an alpha of 0.30,
    0.40, 0.50, 0.60, 0.70 or 0.85 and a blur radius of 8, 16, 24, 32 or 48 dp.
    It also has a radius of 0, which is no blur at all: the glass of API 28 to
    30, which cannot blur, draws the tint over the background as it is. That is
    36 candidates.
- **Blur.** A Gaussian of sigma `0.57735 * radius_px + 0.5`. This is how Skia
  turns a radius into a sigma, and Android's `RenderEffect.createBlurEffect`
  uses it. The radius in dp becomes pixels with the density of the frame (2.0 on
  the TV, 3.0 on the phone). The pixels are blurred and mixed as sRGB values, as
  the platform does, and the edge of the frame repeats. A real device may differ
  a little from this model.
  - The app blurs a copy of the background at a fourth of its size and draws it
    back up. Both steps smooth the picture further, so the model keeps a bright
    pixel brighter than the app does, and its numbers err on the side of
    contrast.
- **The table for the app.** `contrast.py glass-table` turns a measurement into
  `GlassContrastTable.kt` (see below). It is the only thing of the measurement
  that the app reads.

## Baseline

### Table A: text on the background (a)

The worst case over the frames.

| Style | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains, phone | 9.66 | 6.86 | 3.62 | 2.92 | 6.31 |
| Aurora curtains, TV | 9.68 | 6.86 | 3.62 | 2.92 | 6.32 |
| Flowing light, phone | 5.72 | 4.06 | 2.14 | 1.73 | 3.73 |
| Flowing light, TV | 5.34 | 3.79 | 2.00 | 1.61 | 3.49 |
| Light mist, phone | 5.77 | 4.10 | 2.16 | 1.74 | 3.77 |
| Light mist, TV | 2.04 | 1.45 | 1.00 | 1.00 | 1.33 |
| Northern lights, phone | 1.57 | 1.11 | 1.00 | 1.00 | 1.03 |
| Northern lights, TV | 1.56 | 1.11 | 1.00 | 1.00 | 1.02 |
| Soft glow, phone | 10.72 | 7.60 | 4.02 | 3.24 | 6.997 |
| Soft glow, TV | 11.37 | 8.07 | 4.26 | 3.44 | 7.43 |
| Starfield, phone | 2.09 | 1.48 | 1.00 | 1.00 | 1.36 |
| Starfield, TV | 1.99 | 1.41 | 1.00 | 1.00 | 1.30 |

The same for p5, for information: the lowest over the frames of the ratio below
which 5% of the pixels are.

| Style | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains, phone | 12.09 | 8.58 | 4.53 | 3.65 | 7.89 |
| Aurora curtains, TV | 12.09 | 8.58 | 4.53 | 3.65 | 7.90 |
| Flowing light, phone | 6.98 | 4.95 | 2.61 | 2.11 | 4.55 |
| Flowing light, TV | 7.08 | 5.02 | 2.65 | 2.14 | 4.62 |
| Light mist, phone | 15.69 | 11.13 | 5.88 | 4.74 | 10.25 |
| Light mist, TV | 12.71 | 9.01 | 4.76 | 3.84 | 8.30 |
| Northern lights, phone | 9.30 | 6.60 | 3.48 | 2.81 | 6.07 |
| Northern lights, TV | 10.14 | 7.20 | 3.80 | 3.06 | 6.62 |
| Soft glow, phone | 11.54 | 8.19 | 4.32 | 3.49 | 7.53 |
| Soft glow, TV | 12.41 | 8.81 | 4.65 | 3.75 | 8.10 |
| Starfield, phone | 14.19 | 10.07 | 5.32 | 4.29 | 9.26 |
| Starfield, TV | 14.30 | 10.15 | 5.36 | 4.32 | 9.34 |

### Table B: text on the opaque surfaces (b)

The contrast ratio. It does not depend on the frames. The last row is the worst
of the three surfaces.

| Surface | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` |
| --- | --- | --- | --- | --- | --- |
| `page` | 15.81 | 11.22 | 5.92 | 4.78 | 10.32 |
| `surface` | 14.92 | 10.58 | 5.59 | 4.51 | 9.74 |
| `surface-raised` | 13.88 | 9.85 | 5.20 | 4.19 | 9.06 |
| worst | 13.88 | 9.85 | 5.20 | 4.19 | 9.06 |

### Table C: text on a glass panel (c)

For each of the 36 candidates, the worst case of each token over every style and
every frame, and the style and device it is in. The last column is the lowest of
the five tokens. A ratio just short of a limit is printed with three places, so
4.496 is not read as 4.50. The first table is over both devices; the two that
follow are for the TV and for the phone alone.

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.30 | 0 (no blur) | 2.84 (Northern lights, TV) | 2.01 (Northern lights, TV) | 1.06 (Northern lights, TV) | 1.00 (Northern lights, TV) | 1.85 (Northern lights, TV) | 1.00 |
| 0.30 | 8 | 5.05 (Northern lights, phone) | 3.59 (Northern lights, phone) | 1.89 (Northern lights, phone) | 1.53 (Northern lights, phone) | 3.30 (Northern lights, phone) | 1.53 |
| 0.30 | 16 | 6.50 (Northern lights, phone) | 4.61 (Northern lights, phone) | 2.44 (Northern lights, phone) | 1.97 (Northern lights, phone) | 4.25 (Northern lights, phone) | 1.97 |
| 0.30 | 24 | 7.05 (Northern lights, phone) | 5.00 (Northern lights, phone) | 2.64 (Northern lights, phone) | 2.13 (Northern lights, phone) | 4.60 (Northern lights, phone) | 2.13 |
| 0.30 | 32 | 7.60 (Northern lights, phone) | 5.39 (Northern lights, phone) | 2.85 (Northern lights, phone) | 2.30 (Northern lights, phone) | 4.96 (Northern lights, phone) | 2.30 |
| 0.30 | 48 | 8.36 (Flowing light, TV) | 5.93 (Flowing light, TV) | 3.13 (Flowing light, TV) | 2.53 (Flowing light, TV) | 5.46 (Flowing light, TV) | 2.53 |
| 0.40 | 0 (no blur) | 3.56 (Northern lights, TV) | 2.53 (Northern lights, TV) | 1.33 (Northern lights, TV) | 1.08 (Northern lights, TV) | 2.33 (Northern lights, TV) | 1.08 |
| 0.40 | 8 | 5.97 (Northern lights, phone) | 4.24 (Northern lights, phone) | 2.24 (Northern lights, phone) | 1.81 (Northern lights, phone) | 3.90 (Northern lights, phone) | 1.81 |
| 0.40 | 16 | 7.44 (Northern lights, phone) | 5.28 (Northern lights, phone) | 2.79 (Northern lights, phone) | 2.25 (Northern lights, phone) | 4.86 (Northern lights, phone) | 2.25 |
| 0.40 | 24 | 7.97 (Northern lights, phone) | 5.66 (Northern lights, phone) | 2.99 (Northern lights, phone) | 2.41 (Northern lights, phone) | 5.20 (Northern lights, phone) | 2.41 |
| 0.40 | 32 | 8.50 (Northern lights, phone) | 6.03 (Northern lights, phone) | 3.18 (Northern lights, phone) | 2.57 (Northern lights, phone) | 5.55 (Northern lights, phone) | 2.57 |
| 0.40 | 48 | 9.20 (Flowing light, TV) | 6.53 (Flowing light, TV) | 3.45 (Flowing light, TV) | 2.78 (Flowing light, TV) | 6.01 (Flowing light, TV) | 2.78 |
| 0.50 | 0 (no blur) | 4.53 (Northern lights, TV) | 3.22 (Northern lights, TV) | 1.70 (Northern lights, TV) | 1.37 (Northern lights, TV) | 2.96 (Northern lights, TV) | 1.37 |
| 0.50 | 8 | 7.08 (Northern lights, phone) | 5.03 (Northern lights, phone) | 2.65 (Northern lights, phone) | 2.14 (Northern lights, phone) | 4.62 (Northern lights, phone) | 2.14 |
| 0.50 | 16 | 8.51 (Northern lights, phone) | 6.04 (Northern lights, phone) | 3.19 (Northern lights, phone) | 2.57 (Northern lights, phone) | 5.55 (Northern lights, phone) | 2.57 |
| 0.50 | 24 | 9.00 (Northern lights, phone) | 6.39 (Northern lights, phone) | 3.37 (Northern lights, phone) | 2.72 (Northern lights, phone) | 5.88 (Northern lights, phone) | 2.72 |
| 0.50 | 32 | 9.49 (Northern lights, phone) | 6.73 (Northern lights, phone) | 3.56 (Northern lights, phone) | 2.87 (Northern lights, phone) | 6.20 (Northern lights, phone) | 2.87 |
| 0.50 | 48 | 10.11 (Flowing light, TV) | 7.18 (Flowing light, TV) | 3.79 (Flowing light, TV) | 3.06 (Flowing light, TV) | 6.60 (Flowing light, TV) | 3.06 |
| 0.60 | 0 (no blur) | 5.84 (Northern lights, TV) | 4.14 (Northern lights, TV) | 2.19 (Northern lights, TV) | 1.76 (Northern lights, TV) | 3.81 (Northern lights, TV) | 1.76 |
| 0.60 | 8 | 8.40 (Northern lights, phone) | 5.96 (Northern lights, phone) | 3.15 (Northern lights, phone) | 2.54 (Northern lights, phone) | 5.48 (Northern lights, phone) | 2.54 |
| 0.60 | 16 | 9.70 (Northern lights, phone) | 6.88 (Northern lights, phone) | 3.63 (Northern lights, phone) | 2.93 (Northern lights, phone) | 6.33 (Northern lights, phone) | 2.93 |
| 0.60 | 24 | 10.13 (Northern lights, phone) | 7.19 (Northern lights, phone) | 3.80 (Northern lights, phone) | 3.06 (Northern lights, phone) | 6.62 (Northern lights, phone) | 3.06 |
| 0.60 | 32 | 10.55 (Northern lights, phone) | 7.49 (Northern lights, phone) | 3.95 (Northern lights, phone) | 3.19 (Northern lights, phone) | 6.89 (Northern lights, phone) | 3.19 |
| 0.60 | 48 | 11.07 (Flowing light, TV) | 7.86 (Flowing light, TV) | 4.15 (Flowing light, TV) | 3.35 (Flowing light, TV) | 7.23 (Flowing light, TV) | 3.35 |
| 0.70 | 0 (no blur) | 7.58 (Northern lights, TV) | 5.38 (Northern lights, TV) | 2.84 (Northern lights, TV) | 2.29 (Northern lights, TV) | 4.95 (Northern lights, TV) | 2.29 |
| 0.70 | 8 | 9.92 (Northern lights, phone) | 7.04 (Northern lights, phone) | 3.72 (Northern lights, phone) | 2.996 (Northern lights, phone) | 6.47 (Northern lights, phone) | 2.996 |
| 0.70 | 16 | 10.99 (Northern lights, phone) | 7.80 (Northern lights, phone) | 4.12 (Northern lights, phone) | 3.32 (Northern lights, phone) | 7.18 (Northern lights, phone) | 3.32 |
| 0.70 | 24 | 11.34 (Northern lights, phone) | 8.05 (Northern lights, phone) | 4.25 (Northern lights, phone) | 3.43 (Northern lights, phone) | 7.40 (Northern lights, phone) | 3.43 |
| 0.70 | 32 | 11.67 (Northern lights, phone) | 8.28 (Northern lights, phone) | 4.37 (Northern lights, phone) | 3.53 (Northern lights, phone) | 7.62 (Northern lights, phone) | 3.53 |
| 0.70 | 48 | 12.06 (Flowing light, TV) | 8.56 (Flowing light, TV) | 4.52 (Flowing light, TV) | 3.65 (Flowing light, TV) | 7.88 (Flowing light, TV) | 3.65 |
| 0.85 | 0 (no blur) | 11.07 (Northern lights, TV) | 7.86 (Northern lights, TV) | 4.15 (Northern lights, TV) | 3.35 (Northern lights, TV) | 7.23 (Northern lights, TV) | 3.35 |
| 0.85 | 8 | 12.46 (Northern lights, phone) | 8.84 (Northern lights, phone) | 4.67 (Northern lights, phone) | 3.77 (Northern lights, phone) | 8.13 (Northern lights, phone) | 3.77 |
| 0.85 | 16 | 13.02 (Northern lights, phone) | 9.24 (Northern lights, phone) | 4.88 (Northern lights, phone) | 3.93 (Northern lights, phone) | 8.50 (Northern lights, phone) | 3.93 |
| 0.85 | 24 | 13.20 (Northern lights, phone) | 9.36 (Northern lights, phone) | 4.94 (Northern lights, phone) | 3.99 (Northern lights, phone) | 8.62 (Northern lights, phone) | 3.99 |
| 0.85 | 32 | 13.36 (Northern lights, phone) | 9.48 (Northern lights, phone) | 5.01 (Northern lights, phone) | 4.04 (Northern lights, phone) | 8.72 (Northern lights, phone) | 4.04 |
| 0.85 | 48 | 13.55 (Flowing light, TV) | 9.61 (Flowing light, TV) | 5.07 (Flowing light, TV) | 4.09 (Flowing light, TV) | 8.84 (Flowing light, TV) | 4.09 |

The TV frames only:

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.30 | 0 (no blur) | 2.84 (Northern lights, TV) | 2.01 (Northern lights, TV) | 1.06 (Northern lights, TV) | 1.00 (Northern lights, TV) | 1.85 (Northern lights, TV) | 1.00 |
| 0.30 | 8 | 5.69 (Northern lights, TV) | 4.04 (Northern lights, TV) | 2.13 (Northern lights, TV) | 1.72 (Northern lights, TV) | 3.72 (Northern lights, TV) | 1.72 |
| 0.30 | 16 | 7.06 (Northern lights, TV) | 5.01 (Northern lights, TV) | 2.64 (Northern lights, TV) | 2.13 (Northern lights, TV) | 4.61 (Northern lights, TV) | 2.13 |
| 0.30 | 24 | 8.13 (Flowing light, TV) | 5.77 (Flowing light, TV) | 3.05 (Flowing light, TV) | 2.46 (Flowing light, TV) | 5.31 (Flowing light, TV) | 2.46 |
| 0.30 | 32 | 8.24 (Flowing light, TV) | 5.85 (Flowing light, TV) | 3.09 (Flowing light, TV) | 2.49 (Flowing light, TV) | 5.38 (Flowing light, TV) | 2.49 |
| 0.30 | 48 | 8.36 (Flowing light, TV) | 5.93 (Flowing light, TV) | 3.13 (Flowing light, TV) | 2.53 (Flowing light, TV) | 5.46 (Flowing light, TV) | 2.53 |
| 0.40 | 0 (no blur) | 3.56 (Northern lights, TV) | 2.53 (Northern lights, TV) | 1.33 (Northern lights, TV) | 1.08 (Northern lights, TV) | 2.33 (Northern lights, TV) | 1.08 |
| 0.40 | 8 | 6.63 (Northern lights, TV) | 4.70 (Northern lights, TV) | 2.48 (Northern lights, TV) | 2.00 (Northern lights, TV) | 4.33 (Northern lights, TV) | 2.00 |
| 0.40 | 16 | 7.98 (Northern lights, TV) | 5.66 (Northern lights, TV) | 2.99 (Northern lights, TV) | 2.41 (Northern lights, TV) | 5.21 (Northern lights, TV) | 2.41 |
| 0.40 | 24 | 8.99 (Flowing light, TV) | 6.38 (Flowing light, TV) | 3.37 (Flowing light, TV) | 2.72 (Flowing light, TV) | 5.87 (Flowing light, TV) | 2.72 |
| 0.40 | 32 | 9.10 (Flowing light, TV) | 6.45 (Flowing light, TV) | 3.41 (Flowing light, TV) | 2.75 (Flowing light, TV) | 5.94 (Flowing light, TV) | 2.75 |
| 0.40 | 48 | 9.20 (Flowing light, TV) | 6.53 (Flowing light, TV) | 3.45 (Flowing light, TV) | 2.78 (Flowing light, TV) | 6.01 (Flowing light, TV) | 2.78 |
| 0.50 | 0 (no blur) | 4.53 (Northern lights, TV) | 3.22 (Northern lights, TV) | 1.70 (Northern lights, TV) | 1.37 (Northern lights, TV) | 2.96 (Northern lights, TV) | 1.37 |
| 0.50 | 8 | 7.73 (Northern lights, TV) | 5.48 (Northern lights, TV) | 2.90 (Northern lights, TV) | 2.34 (Northern lights, TV) | 5.05 (Northern lights, TV) | 2.34 |
| 0.50 | 16 | 9.01 (Northern lights, TV) | 6.39 (Northern lights, TV) | 3.38 (Northern lights, TV) | 2.72 (Northern lights, TV) | 5.88 (Northern lights, TV) | 2.72 |
| 0.50 | 24 | 9.92 (Flowing light, TV) | 7.04 (Flowing light, TV) | 3.72 (Flowing light, TV) | 2.998 (Flowing light, TV) | 6.48 (Flowing light, TV) | 2.998 |
| 0.50 | 32 | 10.02 (Flowing light, TV) | 7.11 (Flowing light, TV) | 3.75 (Flowing light, TV) | 3.03 (Flowing light, TV) | 6.54 (Flowing light, TV) | 3.03 |
| 0.50 | 48 | 10.11 (Flowing light, TV) | 7.18 (Flowing light, TV) | 3.79 (Flowing light, TV) | 3.06 (Flowing light, TV) | 6.60 (Flowing light, TV) | 3.06 |
| 0.60 | 0 (no blur) | 5.84 (Northern lights, TV) | 4.14 (Northern lights, TV) | 2.19 (Northern lights, TV) | 1.76 (Northern lights, TV) | 3.81 (Northern lights, TV) | 1.76 |
| 0.60 | 8 | 9.00 (Northern lights, TV) | 6.38 (Northern lights, TV) | 3.37 (Northern lights, TV) | 2.72 (Northern lights, TV) | 5.87 (Northern lights, TV) | 2.72 |
| 0.60 | 16 | 10.14 (Northern lights, TV) | 7.19 (Northern lights, TV) | 3.80 (Northern lights, TV) | 3.06 (Northern lights, TV) | 6.62 (Northern lights, TV) | 3.06 |
| 0.60 | 24 | 10.91 (Flowing light, TV) | 7.74 (Flowing light, TV) | 4.09 (Flowing light, TV) | 3.30 (Flowing light, TV) | 7.12 (Flowing light, TV) | 3.30 |
| 0.60 | 32 | 10.99 (Flowing light, TV) | 7.80 (Flowing light, TV) | 4.12 (Flowing light, TV) | 3.32 (Flowing light, TV) | 7.18 (Flowing light, TV) | 3.32 |
| 0.60 | 48 | 11.07 (Flowing light, TV) | 7.86 (Flowing light, TV) | 4.15 (Flowing light, TV) | 3.35 (Flowing light, TV) | 7.23 (Flowing light, TV) | 3.35 |
| 0.70 | 0 (no blur) | 7.58 (Northern lights, TV) | 5.38 (Northern lights, TV) | 2.84 (Northern lights, TV) | 2.29 (Northern lights, TV) | 4.95 (Northern lights, TV) | 2.29 |
| 0.70 | 8 | 10.42 (Northern lights, TV) | 7.39 (Northern lights, TV) | 3.90 (Northern lights, TV) | 3.15 (Northern lights, TV) | 6.80 (Northern lights, TV) | 3.15 |
| 0.70 | 16 | 11.34 (Northern lights, TV) | 8.05 (Northern lights, TV) | 4.25 (Northern lights, TV) | 3.43 (Northern lights, TV) | 7.41 (Northern lights, TV) | 3.43 |
| 0.70 | 24 | 11.94 (Flowing light, TV) | 8.47 (Flowing light, TV) | 4.47 (Flowing light, TV) | 3.61 (Flowing light, TV) | 7.79 (Flowing light, TV) | 3.61 |
| 0.70 | 32 | 12.00 (Flowing light, TV) | 8.51 (Flowing light, TV) | 4.496 (Flowing light, TV) | 3.63 (Flowing light, TV) | 7.84 (Flowing light, TV) | 3.63 |
| 0.70 | 48 | 12.06 (Flowing light, TV) | 8.56 (Flowing light, TV) | 4.52 (Flowing light, TV) | 3.65 (Flowing light, TV) | 7.88 (Flowing light, TV) | 3.65 |
| 0.85 | 0 (no blur) | 11.07 (Northern lights, TV) | 7.86 (Northern lights, TV) | 4.15 (Northern lights, TV) | 3.35 (Northern lights, TV) | 7.23 (Northern lights, TV) | 3.35 |
| 0.85 | 8 | 12.73 (Northern lights, TV) | 9.03 (Northern lights, TV) | 4.77 (Northern lights, TV) | 3.85 (Northern lights, TV) | 8.31 (Northern lights, TV) | 3.85 |
| 0.85 | 16 | 13.20 (Northern lights, TV) | 9.36 (Northern lights, TV) | 4.94 (Northern lights, TV) | 3.99 (Northern lights, TV) | 8.62 (Northern lights, TV) | 3.99 |
| 0.85 | 24 | 13.49 (Flowing light, TV) | 9.57 (Flowing light, TV) | 5.05 (Flowing light, TV) | 4.07 (Flowing light, TV) | 8.80 (Flowing light, TV) | 4.07 |
| 0.85 | 32 | 13.52 (Flowing light, TV) | 9.59 (Flowing light, TV) | 5.06 (Flowing light, TV) | 4.08 (Flowing light, TV) | 8.82 (Flowing light, TV) | 4.08 |
| 0.85 | 48 | 13.55 (Flowing light, TV) | 9.61 (Flowing light, TV) | 5.07 (Flowing light, TV) | 4.09 (Flowing light, TV) | 8.84 (Flowing light, TV) | 4.09 |

The phone frames only:

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.30 | 0 (no blur) | 2.85 (Northern lights, phone) | 2.02 (Northern lights, phone) | 1.07 (Northern lights, phone) | 1.00 (Northern lights, phone) | 1.86 (Northern lights, phone) | 1.00 |
| 0.30 | 8 | 5.05 (Northern lights, phone) | 3.59 (Northern lights, phone) | 1.89 (Northern lights, phone) | 1.53 (Northern lights, phone) | 3.30 (Northern lights, phone) | 1.53 |
| 0.30 | 16 | 6.50 (Northern lights, phone) | 4.61 (Northern lights, phone) | 2.44 (Northern lights, phone) | 1.97 (Northern lights, phone) | 4.25 (Northern lights, phone) | 1.97 |
| 0.30 | 24 | 7.05 (Northern lights, phone) | 5.00 (Northern lights, phone) | 2.64 (Northern lights, phone) | 2.13 (Northern lights, phone) | 4.60 (Northern lights, phone) | 2.13 |
| 0.30 | 32 | 7.60 (Northern lights, phone) | 5.39 (Northern lights, phone) | 2.85 (Northern lights, phone) | 2.30 (Northern lights, phone) | 4.96 (Northern lights, phone) | 2.30 |
| 0.30 | 48 | 8.44 (Flowing light, phone) | 5.99 (Flowing light, phone) | 3.16 (Flowing light, phone) | 2.55 (Flowing light, phone) | 5.51 (Flowing light, phone) | 2.55 |
| 0.40 | 0 (no blur) | 3.58 (Northern lights, phone) | 2.54 (Northern lights, phone) | 1.34 (Northern lights, phone) | 1.08 (Northern lights, phone) | 2.33 (Northern lights, phone) | 1.08 |
| 0.40 | 8 | 5.97 (Northern lights, phone) | 4.24 (Northern lights, phone) | 2.24 (Northern lights, phone) | 1.81 (Northern lights, phone) | 3.90 (Northern lights, phone) | 1.81 |
| 0.40 | 16 | 7.44 (Northern lights, phone) | 5.28 (Northern lights, phone) | 2.79 (Northern lights, phone) | 2.25 (Northern lights, phone) | 4.86 (Northern lights, phone) | 2.25 |
| 0.40 | 24 | 7.97 (Northern lights, phone) | 5.66 (Northern lights, phone) | 2.99 (Northern lights, phone) | 2.41 (Northern lights, phone) | 5.20 (Northern lights, phone) | 2.41 |
| 0.40 | 32 | 8.50 (Northern lights, phone) | 6.03 (Northern lights, phone) | 3.18 (Northern lights, phone) | 2.57 (Northern lights, phone) | 5.55 (Northern lights, phone) | 2.57 |
| 0.40 | 48 | 9.28 (Flowing light, phone) | 6.59 (Flowing light, phone) | 3.48 (Flowing light, phone) | 2.81 (Flowing light, phone) | 6.06 (Flowing light, phone) | 2.81 |
| 0.50 | 0 (no blur) | 4.55 (Northern lights, phone) | 3.23 (Northern lights, phone) | 1.70 (Northern lights, phone) | 1.37 (Northern lights, phone) | 2.97 (Northern lights, phone) | 1.37 |
| 0.50 | 8 | 7.08 (Northern lights, phone) | 5.03 (Northern lights, phone) | 2.65 (Northern lights, phone) | 2.14 (Northern lights, phone) | 4.62 (Northern lights, phone) | 2.14 |
| 0.50 | 16 | 8.51 (Northern lights, phone) | 6.04 (Northern lights, phone) | 3.19 (Northern lights, phone) | 2.57 (Northern lights, phone) | 5.55 (Northern lights, phone) | 2.57 |
| 0.50 | 24 | 9.00 (Northern lights, phone) | 6.39 (Northern lights, phone) | 3.37 (Northern lights, phone) | 2.72 (Northern lights, phone) | 5.88 (Northern lights, phone) | 2.72 |
| 0.50 | 32 | 9.49 (Northern lights, phone) | 6.73 (Northern lights, phone) | 3.56 (Northern lights, phone) | 2.87 (Northern lights, phone) | 6.20 (Northern lights, phone) | 2.87 |
| 0.50 | 48 | 10.18 (Flowing light, phone) | 7.22 (Flowing light, phone) | 3.82 (Flowing light, phone) | 3.08 (Flowing light, phone) | 6.65 (Flowing light, phone) | 3.08 |
| 0.60 | 0 (no blur) | 5.86 (Northern lights, phone) | 4.16 (Northern lights, phone) | 2.19 (Northern lights, phone) | 1.77 (Northern lights, phone) | 3.82 (Northern lights, phone) | 1.77 |
| 0.60 | 8 | 8.40 (Northern lights, phone) | 5.96 (Northern lights, phone) | 3.15 (Northern lights, phone) | 2.54 (Northern lights, phone) | 5.48 (Northern lights, phone) | 2.54 |
| 0.60 | 16 | 9.70 (Northern lights, phone) | 6.88 (Northern lights, phone) | 3.63 (Northern lights, phone) | 2.93 (Northern lights, phone) | 6.33 (Northern lights, phone) | 2.93 |
| 0.60 | 24 | 10.13 (Northern lights, phone) | 7.19 (Northern lights, phone) | 3.80 (Northern lights, phone) | 3.06 (Northern lights, phone) | 6.62 (Northern lights, phone) | 3.06 |
| 0.60 | 32 | 10.55 (Northern lights, phone) | 7.49 (Northern lights, phone) | 3.95 (Northern lights, phone) | 3.19 (Northern lights, phone) | 6.89 (Northern lights, phone) | 3.19 |
| 0.60 | 48 | 11.13 (Flowing light, phone) | 7.90 (Flowing light, phone) | 4.17 (Flowing light, phone) | 3.36 (Flowing light, phone) | 7.27 (Flowing light, phone) | 3.36 |
| 0.70 | 0 (no blur) | 7.60 (Northern lights, phone) | 5.39 (Northern lights, phone) | 2.85 (Northern lights, phone) | 2.30 (Northern lights, phone) | 4.96 (Northern lights, phone) | 2.30 |
| 0.70 | 8 | 9.92 (Northern lights, phone) | 7.04 (Northern lights, phone) | 3.72 (Northern lights, phone) | 2.996 (Northern lights, phone) | 6.47 (Northern lights, phone) | 2.996 |
| 0.70 | 16 | 10.99 (Northern lights, phone) | 7.80 (Northern lights, phone) | 4.12 (Northern lights, phone) | 3.32 (Northern lights, phone) | 7.18 (Northern lights, phone) | 3.32 |
| 0.70 | 24 | 11.34 (Northern lights, phone) | 8.05 (Northern lights, phone) | 4.25 (Northern lights, phone) | 3.43 (Northern lights, phone) | 7.40 (Northern lights, phone) | 3.43 |
| 0.70 | 32 | 11.67 (Northern lights, phone) | 8.28 (Northern lights, phone) | 4.37 (Northern lights, phone) | 3.53 (Northern lights, phone) | 7.62 (Northern lights, phone) | 3.53 |
| 0.70 | 48 | 12.11 (Flowing light, phone) | 8.59 (Flowing light, phone) | 4.54 (Flowing light, phone) | 3.66 (Flowing light, phone) | 7.90 (Flowing light, phone) | 3.66 |
| 0.85 | 0 (no blur) | 11.09 (Northern lights, phone) | 7.87 (Northern lights, phone) | 4.15 (Northern lights, phone) | 3.35 (Northern lights, phone) | 7.24 (Northern lights, phone) | 3.35 |
| 0.85 | 8 | 12.46 (Northern lights, phone) | 8.84 (Northern lights, phone) | 4.67 (Northern lights, phone) | 3.77 (Northern lights, phone) | 8.13 (Northern lights, phone) | 3.77 |
| 0.85 | 16 | 13.02 (Northern lights, phone) | 9.24 (Northern lights, phone) | 4.88 (Northern lights, phone) | 3.93 (Northern lights, phone) | 8.50 (Northern lights, phone) | 3.93 |
| 0.85 | 24 | 13.20 (Northern lights, phone) | 9.36 (Northern lights, phone) | 4.94 (Northern lights, phone) | 3.99 (Northern lights, phone) | 8.62 (Northern lights, phone) | 3.99 |
| 0.85 | 32 | 13.36 (Northern lights, phone) | 9.48 (Northern lights, phone) | 5.01 (Northern lights, phone) | 4.04 (Northern lights, phone) | 8.72 (Northern lights, phone) | 4.04 |
| 0.85 | 48 | 13.57 (Flowing light, phone) | 9.63 (Flowing light, phone) | 5.08 (Flowing light, phone) | 4.10 (Flowing light, phone) | 8.86 (Flowing light, phone) | 4.10 |

The clips are drawn on a `TextureView` since Sprint 10, so that the glass can
sample them. Before that they were on a `SurfaceView`, which an in-app blur
cannot see. The blur spike measured this path on the TV and on a phone, and it
works there (ADR 0057).

## What the baseline shows

- **On the background (a).**
  - In the two drawn styles `ink` and `accent-soft` keep more than 6:1 in the
    worst frame. The small tokens do not keep 4.5:1: `ink-3` is 3.62 and `ink-4`
    is 2.92 in Aurora curtains, 4.02 and 3.24 in Soft glow on a phone.
  - In the clips a few pixels reach the colour of the text, so the worst case of
    `ink-3` and `ink-4` is 1.00 in Northern lights and in Starfield, on the TV
    and on the phone, and in Light mist on the TV. The middle of Light mist is
    darker: on the phone its `ink-3` is 2.16. Flowing light is bright over the
    whole frame: `ink-3` is 2.00 on the TV and 2.14 on the phone.
  - For 95% of the pixels (p5) the phone `ink-3` is 3.48 in Northern lights,
    5.88 in Light mist, 5.32 in Starfield and 2.61 in Flowing light.
- **On the opaque surfaces (b).** All five tokens keep 4.5:1 on `page` and on
  `surface`. `ink-4` on `surface-raised` is 4.19, which is why the token says to
  use `ink-3` there. `ink-3` and `ink-4` are below 7:1 even on `page`, so the
  high contrast mode needs text colours of its own.
- **On a glass panel (c).**
  - No candidate keeps all five tokens at 4.5:1 over every style. The best is an
    alpha of 0.85 with a blur of 48 dp, and its lowest token is `ink-4` at 4.09,
    over Flowing light on the TV. `ink-4` is 4.51 on an opaque `surface`, so over
    a frame brighter than `surface` it is below 4.5:1 at every alpha under 1.
  - At the default of the app, an alpha of 0.40 with a blur of 8 dp, `ink` keeps
    5.97, but `ink-2` is 4.24 and `accent-soft` is 3.90 over Northern lights, and
    `ink-3` is 2.24. At an alpha of 0.70 with a blur of 8 dp the lowest `ink-2`
    and `accent-soft` are 7.04 and 6.47, and `ink-3` is 3.72.
  - Without a blur, at an alpha of 0.85, `ink-3` is 4.15 over Northern lights on
    the TV. The blur matters here: at the same alpha and 8 dp it is 4.67. This is
    the case of an API below 31, which cannot blur.
  - The alpha matters most, and a larger blur helps too, most over the small
    bright details of Northern lights and Starfield.

## Text on glass: the table, the floors and the colours

ADR 0057 chooses what to do about the numbers above. The rule lives in one place,
`GlassRules.kt` in `:borealis:designsystem`, and reads one generated table.

### The table

`contrast.py glass-table <result.json>` writes
`borealis/designsystem/src/main/kotlin/io/github/magalz/borealis/designsystem/glass/GlassContrastTable.kt`.
For each style, blur and tint it holds `Lb`, the luminance of the brightest pixel
behind the text, over every frame of the TV and of the phone:

```text
Lb = (L(ink) + 0.05) / ratio(ink) - 0.05
```

`ratio(ink)` is the lowest ratio of `ink` in Table C for that style, over both
devices. `ink` is the lightest of the text tokens, so its ratio is lowest where
the backdrop is brightest, and `Lb` is that pixel. The value is rounded up at five
places. The file says that it is generated and is not edited by hand: after a new
measurement, run the command and commit the file with the result.

### The colours

The content of a glass surface sees the `ink-3` and `ink-4` roles replaced, through
`LocalBorealisColors`. For one glass (a background, a blur and a tint):

1. The worst backdrop `Lb` is read from the table. A blur or a tint between two of
   the grid is read as the next one below it, which is the brighter backdrop.
2. The target is a luminance of `4.5 * 1.02 * (Lb + 0.05) - 0.05`: 4.5:1 with a
   margin of 2% for the difference between the model and a real device.
3. The colour is the first one, walking from the token towards `ink` in 1% steps
   of sRGB, whose WCAG luminance reaches the target, rounded to 8 bits. `ink-3`
   starts at `ink-3` and `ink-4` starts at `ink-4`, so both end at about the same
   colour. A token that already reaches the target is left as it is.
4. With the glass Off, and with high contrast, the tokens are as they are.

`ink`, `ink-2` and `accent-soft` are never changed.

### The floor of each background

The glass is never more transparent than the lowest tint of the grid that meets
two conditions, for the background and the blur: `ink`, `ink-2` and `accent-soft`
keep 4.5:1 as they are, and the adjusted `ink-3` is no lighter than `#A8ACB1`.
That colour is what Flowing light needs at 40%. It keeps `ink-3` clearly below
`ink-2` (`#C3C7CC`). The effective tint of a Standard glass is the larger of the
tint chosen in Settings and this floor.

**Background intensity (Sprint 11).** A page-coloured veil is drawn under the glass, at 0, 25, 50 or
75 % of the page colour, for the Background intensity of 100, 75, 50 or 25 %. Under High contrast the
stronger of the veil and the dim (0.85) wins. The veil never raises the brightest pixel, so the glass
table and the floors above stay valid. Clip pixels darker than the page are lifted toward it, so the
veil does not only darken.

| Style | 8 dp | 16 dp | 24 dp | 32 dp | 48 dp |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | 30 % | 30 % | 30 % | 30 % | 30 % |
| Soft glow | 30 % | 30 % | 30 % | 30 % | 30 % |
| Northern lights | 70 % | 60 % | 50 % | 50 % | 40 % |
| Light mist | 30 % | 30 % | 30 % | 30 % | 30 % |
| Flowing light | 40 % | 40 % | 40 % | 40 % | 40 % |
| Starfield | 30 % | 30 % | 30 % | 30 % | 30 % |

Only Northern lights rises above the lowest tint of the grid, 30%, apart from
Flowing light, whose floor is 40%: at 30% its `ink-3` would be `#B1B5BA`. At the
default of the app, 40% and 8 dp, Northern lights is raised to 70%.

### The colours at the default and in Reduced

At the default, a tint of 40% (raised to the floor) and a blur of 8 dp:

| Style | Tint | ink-3 | ink-4 |
| --- | --- | --- | --- |
| Aurora curtains | 40 % | `#8D9298` | `#8D9298` |
| Soft glow | 40 % | `#8B9096` | `#888E94` |
| Northern lights | 70 % | `#9DA1A7` | `#9DA2A8` |
| Light mist | 40 % | `#969BA0` | `#969BA1` |
| Flowing light | 40 % | `#A8ACB1` | `#A7ABB1` |
| Starfield | 40 % | `#8B9096` | `#858B91` |

Reduced is a tint of 85% and a blur of 48 dp, with no floor needed. The glass of
API 28 to 30 is also 85%, with no blur. `ink-3` keeps its token `#8B9096` in Reduced
over every background, and `ink-4` moves from `#7A8087`. With no blur `ink-3` is the
token over Aurora curtains, Soft glow and Flowing light, and moves over the other
three:

| Style | Reduced ink-3 | Reduced ink-4 | No blur ink-3 | No blur ink-4 |
| --- | --- | --- | --- | --- |
| Aurora curtains | `#8B9096` | `#7E848B` | `#8B9096` | `#80858C` |
| Soft glow | `#8B9096` | `#7E848B` | `#8B9096` | `#7E848B` |
| Northern lights | `#8B9096` | `#83888F` | `#93989E` | `#93989E` |
| Light mist | `#8B9096` | `#7E848B` | `#90959A` | `#90959B` |
| Flowing light | `#8B9096` | `#83888F` | `#8B9096` | `#858B91` |
| Starfield | `#8B9096` | `#7C8289` | `#91959B` | `#90959B` |

These three tables are printed by `contrast.py glass-colours` from the committed
table. Its tests and those of `GlassRules` pin the same colours. A channel that
lands on half a level rounds up, as Kotlin's `roundToInt` does and Python's
`round` does not.

## Halo: text straight on the background

Text that sits on the animated background, outside any glass, is the case (a) of the baseline. The
owner decided on 2026-10-20 that it gets a soft dark halo, and that its secondary text, the `ink-3`
and `ink-4` roles, uses `ink-2`. That covers the title of a screen, the labels of a section, a
count such as "2 hosts", the loading, searching and empty texts, the hints of Add host and of
Pairing, and the titles of the phone bars. The text inside a glass surface is not part of it:
the text colours of the glass cover that. Where the background is off, the plain page, there is no
halo and the numbers of Table B apply. The halo has to keep `ink` and `ink-2` at 4.5:1 for small
text and at 3:1 for large text (24 sp regular or 18.66 sp bold and over) on every background and
device. Without it the text reaches 2:1 or less over Northern lights, Starfield and Light mist on a
TV (Table A).

- Revision measured: `042950a4`, on 2026-10-20. The frames are the 288 of the baseline, taken
  again with the same commands: the drawn styles with the `BackgroundFrameExport` test and the clips
  with `contrast.py extract`.
- `python -I design/accessibility/contrast.py halo <frames> --json <result.json> --jobs 4` measures
  it in about two minutes and prints Tables D, E and F and the choice.

### Method of the halo

- **The text.** Four styles of the app that stand on the background, in IBM Plex, at the size that
  `design/tokens.json` gives each device and at its density (2.0 on the TV and 3.0 on the phone).
  The title is `titleLarge` (24 sp on a TV, 18 sp on a phone, semibold), the label is the kicker
  (12 sp on a TV, 11 sp on a phone, semibold, capitals set 0.07 em apart), the body is `bodyMedium`
  (15 and 14 sp, regular) and the small text is `bodySmall` (14.5 and 12.5 sp, regular). The title
  of a TV is large text and the title of a phone (18 sp semibold) is not. The sample is "Living room
  PC, 4 apps". It is drawn at four times its size and brought down by the mean of each block, so it
  is not hinted, as Android does not hint it.
- **The halo.** The glyphs are widened by a stroke of 0 to 3 dp, round all of each glyph. The
  picture is blurred by a Gaussian of sigma `0.57735 * radius_px + 0.5`, which is how Skia turns a
  blur radius into a sigma and so what a shadow of a text gets, with a radius of 2, 4 or 8 dp. It
  is laid in `page` at an opacity of 60, 80 or 100 %, over the frame, in the encoded values as the
  platform composites. The text is drawn on top. That is 36 candidates, and no halo as a 37th.
- **The ratio.** It is the contrast of the text colour with the pixels that are not in a glyph and
  are within 2 px of one: the pixels beside the strokes, the counters of the letters included. A
  pixel that a glyph covers in part is a glyph pixel. A pixel brighter than the text counts, as in
  the baseline.
- **Where: every place.** The worst case is the lowest ratio over every place that the sample can
  take on the frame, a step of one pixel in both directions, on each of the 24 frames of each of
  the 12 sets of frames. It is found without trying the places one by one. A place lays each pixel
  of the frame under some pixel of the ring, so the worst case is that of every colour of the
  frame with every alpha of the ring. A frame has tens of thousands of colours and a halo is as
  thin as its least alpha and as thick as its greatest, so the worst case is at one of the two,
  or is 1:1 where a halo of some alpha in between darkens a pixel to the luminance of the text. It
  is slightly more than the lowest over the places, for it lays the pixels at the edge of the frame
  under all of the ring and not only under the part of it that fits. With no halo it is Table A, for
  it is the lowest ratio over every pixel of the frame. p1 and p5 are for information: the lowest,
  over six columns and four rows of places over the frame and over the frames, of the ratio below
  which 1% and 5% of the pixels of the ring are.
- **Why not a grid of places.** The first measurement took 6 columns and 4 rows of places, and it
  chose a stroke of 2 dp. The lowest ratio over so few places moves by 15% or more when the sample
  shifts by a pixel, because what decides it is the one bright pixel that falls under the thinnest
  part of the halo. With 24,000 random places the stroke of 2 dp fell below its limit in 7 of the 48
  cases that a text must keep. Every place does not depend on where the places are.
- **The choice.** The smallest candidate that keeps `ink` and `ink-2` at 4.5:1 for the label, the
  body, the small text and the title of a phone, and `ink` at 3:1 for the title of a TV, with 2%
  to spare for the difference between this model and a device, as the glass has. A title is `ink`
  only. The smallest is the least stroke, then the least blur, then the least opacity. The grid is
  that of the issue. One of its candidates keeps the ratios, so the grid was not widened.
- **Why more halo is not always better.** A pixel that a halo darkens only a little is as bright
  as the text, and then its ratio is 1:1, lower than with no halo. That is why the candidates with
  a stroke of 1 dp or less, whose halo is thin at the ring, can do worse than no halo at some
  pixels.

### Table D, E and F

Table D. Text on the background with a halo: the lowest ratio over every background, device and text style, for each candidate. Small text is the label, the body, the small text and the title of a phone, which need 4.5:1. The title of a TV is large text and needs 3:1. A candidate keeps the ratios when every one clears its limit by 2%.

| Stroke dp | Blur dp | Opacity | `ink`, small | `ink-2`, small | `ink`, large | Keeps |
| --- | --- | --- | --- | --- | --- | --- |
| none | - | - | 1.56 | 1.11 | 1.56 | - |
| 0 | 2 | 60 % | 1.65 | 1.17 | 1.65 | no |
| 0 | 2 | 80 % | 1.68 | 1.19 | 1.68 | no |
| 0 | 2 | 100 % | 1.71 | 1.21 | 1.71 | no |
| 0 | 4 | 60 % | 1.65 | 1.17 | 1.72 | no |
| 0 | 4 | 80 % | 1.68 | 1.19 | 1.78 | no |
| 0 | 4 | 100 % | 1.71 | 1.21 | 1.84 | no |
| 0 | 8 | 60 % | 1.66 | 1.18 | 1.74 | no |
| 0 | 8 | 80 % | 1.69 | 1.20 | 1.81 | no |
| 0 | 8 | 100 % | 1.73 | 1.22 | 1.88 | no |
| 1 | 2 | 60 % | 2.15 | 1.52 | 2.13 | no |
| 1 | 2 | 80 % | 2.40 | 1.71 | 2.38 | no |
| 1 | 2 | 100 % | 2.71 | 1.92 | 2.67 | no |
| 1 | 4 | 60 % | 1.99 | 1.41 | 2.07 | no |
| 1 | 4 | 80 % | 2.17 | 1.54 | 2.29 | no |
| 1 | 4 | 100 % | 2.37 | 1.68 | 2.55 | no |
| 1 | 8 | 60 % | 1.89 | 1.34 | 1.94 | no |
| 1 | 8 | 80 % | 2.02 | 1.43 | 2.09 | no |
| 1 | 8 | 100 % | 2.16 | 1.53 | 2.26 | no |
| 2 | 2 | 60 % | 3.29 | 2.33 | 3.17 | no |
| 2 | 2 | 80 % | 4.39 | 3.11 | 4.17 | no |
| 2 | 2 | 100 % | 5.97 | 4.24 | 5.59 | no |
| 2 | 4 | 60 % | 2.54 | 1.80 | 2.61 | no |
| 2 | 4 | 80 % | 3.05 | 2.16 | 3.16 | no |
| 2 | 4 | 100 % | 3.69 | 2.62 | 3.87 | no |
| 2 | 8 | 60 % | 2.21 | 1.57 | 2.20 | no |
| 2 | 8 | 80 % | 2.51 | 1.78 | 2.49 | no |
| 2 | 8 | 100 % | 2.86 | 2.03 | 2.83 | no |
| 3 | 2 | 60 % | 4.79 | 3.40 | 4.68 | no |
| 3 | 2 | 80 % | 7.50 | 5.32 | 7.26 | yes |
| 3 | 2 | 100 % | 11.61 | 8.24 | 11.20 | yes |
| 3 | 4 | 60 % | 3.39 | 2.40 | 3.36 | no |
| 3 | 4 | 80 % | 4.58 | 3.25 | 4.53 | no |
| 3 | 4 | 100 % | 6.33 | 4.49 | 6.22 | no |
| 3 | 8 | 60 % | 2.66 | 1.89 | 2.54 | no |
| 3 | 8 | 80 % | 3.25 | 2.31 | 3.04 | no |
| 3 | 8 | 100 % | 4.02 | 2.85 | 3.68 | no |

Table E. The chosen halo (stroke 3 dp, blur 2 dp, opacity 80 %) by text style and device: the lowest ratio over the backgrounds, with the background it is in, and the lowest p1 and p5.

| Text | Device | Needs | `ink` min | `ink` p1 | `ink` p5 | `ink-2` min | `ink-2` p1 | `ink-2` p5 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| title | TV | 3.00 | 7.26 (Northern lights) | 11.73 | 13.96 | 5.15 (Northern lights) | 8.32 | 9.91 |
| title | phone | 4.50 | 8.85 (Northern lights) | 12.65 | 13.80 | 6.28 (Northern lights) | 8.98 | 9.79 |
| label | TV | 4.50 | 7.70 (Northern lights) | 12.81 | 14.08 | 5.46 (Northern lights) | 9.09 | 9.99 |
| label | phone | 4.50 | 9.17 (Northern lights) | 12.38 | 13.63 | 6.51 (Northern lights) | 8.79 | 9.67 |
| body | TV | 4.50 | 7.50 (Northern lights) | 12.51 | 14.02 | 5.32 (Northern lights) | 8.87 | 9.95 |
| body | phone | 4.50 | 8.85 (Northern lights) | 12.58 | 13.44 | 6.28 (Northern lights) | 8.93 | 9.53 |
| small | TV | 4.50 | 7.62 (Northern lights) | 12.36 | 13.92 | 5.41 (Northern lights) | 8.77 | 9.87 |
| small | phone | 4.50 | 8.96 (Northern lights) | 12.45 | 13.15 | 6.36 (Northern lights) | 8.83 | 9.33 |

Table F. The lowest ratio over the text styles, by background, with no halo and with the chosen one.

| Background | `ink`, none | `ink`, halo | `ink-2`, none | `ink-2`, halo |
| --- | --- | --- | --- | --- |
| Aurora curtains, phone | 9.66 | 14.34 | 6.86 | 10.18 |
| Aurora curtains, TV | 9.68 | 13.90 | 6.86 | 9.92 |
| Flowing light, phone | 5.72 | 12.92 | 4.06 | 9.16 |
| Flowing light, TV | 5.34 | 11.81 | 3.79 | 8.48 |
| Light mist, phone | 5.77 | 12.92 | 4.10 | 9.17 |
| Light mist, TV | 2.04 | 8.21 | 1.45 | 5.99 |
| Northern lights, phone | 1.57 | 8.85 | 1.11 | 6.28 |
| Northern lights, TV | 1.56 | 7.26 | 1.11 | 5.32 |
| Soft glow, phone | 10.72 | 14.63 | 7.60 | 10.38 |
| Soft glow, TV | 11.37 | 14.46 | 8.07 | 10.30 |
| Starfield, phone | 2.09 | 9.76 | 1.48 | 6.92 |
| Starfield, TV | 1.99 | 8.11 | 1.41 | 5.91 |

The choice: stroke 3 dp, blur 2 dp, opacity 80 %.

A test reads the line above and keeps `BackdropHalo` equal to it (`NotesChoice` in the tests of
`:borealis:designsystem`). The line has to stay as it is, once and on one line, or the test fails.

With no halo the lowest ratios are those of Table A: `ink` is 1.56 and `ink-2` is 1.11 over
Northern lights. The title of a TV needs 3:1 and keeps 7.26 over Northern lights, its worst
background. The worst small text is `ink-2` at 5.32, for the body of a TV over Northern lights, 16%
above 4.59, which is 4.5 with 2% to spare. No candidate with a stroke of 2 dp keeps the ratios: the
best of them, a blur of 2 dp at 100 %, has `ink-2` at 4.24. A stroke of 3 dp keeps them at a blur
of 2 dp from 80 %, and a stroke of 3 dp with a blur of 2 dp at 80 % is the smallest, and the one
that the app draws. Its lowest p1 and p5 are 8.32 and 9.33 for `ink-2` and 11.73 and 13.15 for
`ink`: the pixels that come near the lowest ratios are very few.

### Loose text under high contrast

With High contrast on, the loose text has to reach 7:1 (AAA), and the dim of
[`design/tokens.json`](../../design/tokens.json) is what the high contrast mode does to the
background: the `page` colour lies over the animated background at 0.85, in the host, under the
text and the halo. The text is `ink`, white, and `ink-2`, `#E8EAED`, the colours of high contrast.
The `ink-3` and `ink-4` roles of a loose text are `ink-2` there too, as they are without it: a
test reads the colour of the text under a look with High contrast on. The halo is the same as
without it, for the background is still behind the text.

- Revision measured: `d7b559e8`, on 2026-10-21, with the same 288 frames.
- `python -I design/accessibility/contrast.py halo <frames> --json <result.json> --jobs 4
  --high-contrast --halo 3 2 80` measures it, and prints Tables G and H and the verdict. The frame
  is laid under the veil in the 8 bit values, as the screen is, and then measured as the rest of the
  section is, over every place of the text. The limit is 7:1 for every size of text and has no
  margin.

Table G. Loose text under high contrast, with the page colour over it at 0.85 and the ink of high contrast: the lowest ratio over the text styles, by background, with the dim alone and with the dim and the halo (stroke 3 dp, blur 2 dp, opacity 80 %).

| Background | `ink`, dim only | `ink`, halo | `ink-2`, dim only | `ink-2`, halo |
| --- | --- | --- | --- | --- |
| Aurora curtains, phone | 18.12 | 18.82 | 15.03 | 15.61 |
| Aurora curtains, TV | 18.12 | 18.75 | 15.03 | 15.57 |
| Flowing light, phone | 17.18 | 18.59 | 14.25 | 15.43 |
| Flowing light, TV | 17.16 | 18.46 | 14.24 | 15.33 |
| Light mist, phone | 17.20 | 18.60 | 14.27 | 15.43 |
| Light mist, TV | 15.04 | 17.82 | 12.48 | 14.83 |
| Northern lights, phone | 14.49 | 17.99 | 12.02 | 14.92 |
| Northern lights, TV | 14.46 | 17.66 | 12.00 | 14.70 |
| Soft glow, phone | 18.28 | 18.86 | 15.17 | 15.64 |
| Soft glow, TV | 18.36 | 18.83 | 15.24 | 15.63 |
| Starfield, phone | 15.09 | 18.11 | 12.52 | 15.03 |
| Starfield, TV | 15.02 | 17.82 | 12.46 | 14.82 |

Table H. The same halo by text style and device: the lowest ratio over the backgrounds, with the background it is in, and the lowest p1 and p5.

| Text | Device | Needs | `ink` min | `ink` p1 | `ink` p5 | `ink-2` min | `ink-2` p1 | `ink-2` p5 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| title | TV | 7.00 | 17.66 (Northern lights) | 18.45 | 18.76 | 14.65 (Northern lights) | 15.31 | 15.57 |
| title | phone | 7.00 | 17.99 (Northern lights) | 18.58 | 18.74 | 14.92 (Northern lights) | 15.42 | 15.55 |
| label | TV | 7.00 | 17.76 (Northern lights) | 18.60 | 18.78 | 14.73 (Northern lights) | 15.43 | 15.58 |
| label | phone | 7.00 | 18.04 (Northern lights) | 18.53 | 18.71 | 14.97 (Northern lights) | 15.37 | 15.52 |
| body | TV | 7.00 | 17.71 (Northern lights) | 18.55 | 18.77 | 14.70 (Northern lights) | 15.39 | 15.58 |
| body | phone | 7.00 | 17.99 (Northern lights) | 18.57 | 18.69 | 14.92 (Northern lights) | 15.41 | 15.51 |
| small | TV | 7.00 | 17.74 (Northern lights) | 18.54 | 18.76 | 14.72 (Northern lights) | 15.38 | 15.56 |
| small | phone | 7.00 | 18.00 (Northern lights) | 18.55 | 18.65 | 14.94 (Northern lights) | 15.39 | 15.47 |

Under high contrast every text keeps 7:1 over every background: the lowest is 14.70 (`ink-2`, body, TV, Northern lights).

The dim alone is enough: the lowest `ink-2` with no halo is 12.00, over Northern lights on a TV, and
the halo raises every case. No case falls short, so neither the dim nor the halo needs to change.

### How the app draws it

`BackdropText` in `:borealis:designsystem` (`components/BackdropText.kt`) is the one place that
draws a text on the background, and its constants are the choice above; a test keeps the two
equal. `BackdropKicker` is a label in it, and `EmptyState` and the name under a `CoverTile` use it.

- **Drawn with a shadow of a stroked copy of the text.** Behind the text the node paints its own
  paragraph again, with no colour of its own, as a stroke of twice the width of the halo, and with
  a shadow of the halo's blur radius in `page` at the opacity. The stroke widens each glyph by 3 dp
  and the shadow blurs it, which is the model. With the masks of the tool, the stroke and a glyph
  widened by the same width give the same alpha at the pixels of the ring, so the drawing is the
  model and not a look-alike.
- **Not clipped.** `drawText` clips what it draws to the size of the layout unless the overflow is
  visible, and the halo is outside the layout, so the paragraph is painted and not drawn with it.
  The first version of the app was cut at the box of every text.
- **Pinned by pixels.** A test reads a capital I over white, along a line across its stem, and
  compares the alpha of the halo at 2, 5, 8 and 12 px from its edge with the model, within 0.07, in
  the halo that this page chooses and not the one that the constants say. It fails for a stroke or
  a blur that is thinner or wider, a halo that is fainter, over the text, or not drawn, or cut at
  the bounds of the text.
- **Every API level.** It needs no blur effect, which is API 31 and over, so API 28 to 30 draw the
  same halo, and there is no second form to measure. A shadow layer is a part of every text paint.
  The tests draw it with Robolectric and see it, but they are not a device: the halo has not been
  seen on API 28 to 30.
- **When.** Over a background of any style, which is every one but the plain page. The Transparency
  Off and High contrast draw it too, because the background is still behind the text. Outside a
  host there is no background and no halo. The stream was that case; since Sprint 15 its screens
  outside the game stand on the background, in a host, and their text has this halo (see The
  stream: glass, halo and focus, below).
- **The ink.** `BackdropInk` says what the text was: `Ink3` and `Ink4` are `ink-2` where there is
  a halo and the tokens, as they were, where there is not. `Ink` and `Ink2` are the tokens. The
  colours are those of the theme, so under High contrast they are the high contrast ones.

### Icons on the background

The back arrow of the bars, the actions of the Hosts bar (Add host and Settings) and the toggle of
the Apps bar stand on the background with no surface behind them, as the text did. An icon is a
non-text indicator, which WCAG 1.4.11 asks 3:1 for. The icon colour is `ink` (the arrow and the
toggle) or `ink-2` (the actions), and the halo of the text keeps both over every background and
device, so the icons have the same halo and not one of their own.

- Revision measured: `d9c8b027`, on 2026-10-21, with the 288 frames of the halo above.
- `python -I design/accessibility/contrast.py halo <frames> --json <result.json> --jobs 4 --icons
  --halo 3 2 80` measures them in under a minute and prints Tables I and J and the verdict. With
  `--high-contrast` as well, the frame is put under the veil of high contrast first, as for the loose
  text.

**Method.** An icon is a glyph mask, as a letter of the sample text is, and the rest is the method
of the halo: the same ring, the same grid of candidates, the same worst case over every place of the
icon on the frame, and a limit of 3:1 with 2% to spare (none under high contrast).

- **The icon.** `contrast.py` reads the paths of `BorealisIcons.kt` itself (`read_icons`), so it
  measures the icons that the app draws and not a copy of them: the back arrow, the plus, the gear
  and the eye with and without its slash. Each is drawn on the grid of 24 units, 2 units wide with
  round caps and joins, at 24 dp, which is 48 px on a TV and 72 px on a phone. The picture is four
  times that size: a pixel is covered when its centre is within one unit of a segment of the path,
  and the mask is the mean of each block. The curves and the arcs of the paths are cut into short
  segments.
- **The halo.** The mask is widened by the stroke of the halo (3 dp), blurred by the Gaussian of
  Skia for its radius (2 dp) and laid in `page` at its opacity (80 %), exactly as for a text. The
  ring is the pixels that are not in the icon and are within 2 px of it. The stroke of an icon is
  2 dp, thicker than the stem of a letter, so its ring lies well inside the halo: the mean alpha of
  the halo on the ring is above 0.7 for every icon.

Table I. The icons with the halo (stroke 3 dp, blur 2 dp, opacity 80 %), by icon and device: the lowest ratio over the backgrounds, with the background it is in, and the lowest p1 and p5. An icon needs 3:1.

| Icon | Device | Needs | `ink` min | `ink` p1 | `ink` p5 | `ink-2` min | `ink-2` p1 | `ink-2` p5 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Back | TV | 3.00 | 8.36 (Northern lights) | 12.60 | 13.34 | 5.93 (Northern lights) | 8.94 | 9.46 |
| Back | phone | 3.00 | 9.37 (Northern lights) | 13.94 | 14.13 | 6.65 (Northern lights) | 9.89 | 10.02 |
| Add | TV | 3.00 | 8.36 (Northern lights) | 12.68 | 13.33 | 5.93 (Northern lights) | 9.00 | 9.46 |
| Add | phone | 3.00 | 9.37 (Northern lights) | 13.95 | 14.12 | 6.65 (Northern lights) | 9.90 | 10.02 |
| Gear | TV | 3.00 | 8.04 (Northern lights) | 12.83 | 13.79 | 5.71 (Northern lights) | 9.11 | 9.78 |
| Gear | phone | 3.00 | 9.29 (Northern lights) | 13.58 | 14.14 | 6.59 (Northern lights) | 9.63 | 10.03 |
| Visibility | TV | 3.00 | 8.52 (Northern lights) | 12.94 | 13.72 | 6.05 (Northern lights) | 9.18 | 9.73 |
| Visibility | phone | 3.00 | 9.42 (Northern lights) | 13.38 | 14.04 | 6.68 (Northern lights) | 9.50 | 9.96 |
| VisibilityOff | TV | 3.00 | 8.36 (Northern lights) | 12.88 | 13.73 | 5.93 (Northern lights) | 9.14 | 9.74 |
| VisibilityOff | phone | 3.00 | 9.38 (Northern lights) | 13.43 | 14.00 | 6.65 (Northern lights) | 9.53 | 9.94 |

Table J. The lowest ratio over the icons, by background, with no halo and with the halo.

| Background | `ink`, none | `ink`, halo | `ink-2`, none | `ink-2`, halo |
| --- | --- | --- | --- | --- |
| Aurora curtains, phone | 9.66 | 14.45 | 6.86 | 10.25 |
| Aurora curtains, TV | 9.68 | 14.13 | 6.86 | 10.03 |
| Flowing light, phone | 5.72 | 13.13 | 4.06 | 9.32 |
| Flowing light, TV | 5.34 | 12.28 | 3.79 | 8.71 |
| Light mist, phone | 5.77 | 13.13 | 4.10 | 9.32 |
| Light mist, TV | 2.04 | 8.95 | 1.45 | 6.35 |
| Northern lights, phone | 1.57 | 9.29 | 1.11 | 6.59 |
| Northern lights, TV | 1.56 | 8.04 | 1.11 | 5.71 |
| Soft glow, phone | 10.72 | 14.71 | 7.60 | 10.44 |
| Soft glow, TV | 11.37 | 14.62 | 8.07 | 10.37 |
| Starfield, phone | 2.09 | 10.16 | 1.48 | 7.21 |
| Starfield, TV | 1.99 | 8.85 | 1.41 | 6.28 |

With this halo every icon keeps 3:1 over every background, with 2% to spare: the lowest is 5.71
(`ink-2`, the gear, on a TV over Northern lights), 87 % above 3.06, which is 3:1 with 2% to spare.
With no halo the icons are in the case of Table A: 1.11 (`ink-2`) and 1.56 (`ink`) over Northern
lights. On their own the icons would need no more than the smallest candidate that keeps 3:1, a
stroke of 2 dp, a blur of 2 dp and an opacity of 80 %; the app draws the halo of the text, so the
screen has one halo.

**Under high contrast** the icons are `ink` white and `ink-2` `#E8EAED` over the background with the
page colour laid over it at 0.85, with the halo. Every icon keeps 3:1 over every background, and
the lowest is 14.79 (`ink-2`, the gear, on a TV over Northern lights). The dim alone gives 12.00
and the halo raises every case. Table K is Table J under high contrast, the lowest ratio over the
icons by background, with the dim alone and with the dim and the halo (the tool prints it as
Table J when it is run with `--high-contrast`):

Table K. The icons under high contrast, with the page colour over the background at 0.85 and the ink of high contrast.

| Background | `ink`, none | `ink`, halo | `ink-2`, none | `ink-2`, halo |
| --- | --- | --- | --- | --- |
| Aurora curtains, phone | 18.12 | 18.83 | 15.03 | 15.63 |
| Aurora curtains, TV | 18.12 | 18.79 | 15.03 | 15.59 |
| Flowing light, phone | 17.18 | 18.63 | 14.25 | 15.45 |
| Flowing light, TV | 17.16 | 18.53 | 14.24 | 15.37 |
| Light mist, phone | 17.20 | 18.63 | 14.27 | 15.46 |
| Light mist, TV | 15.04 | 17.97 | 12.48 | 14.91 |
| Northern lights, phone | 14.49 | 18.06 | 12.02 | 14.99 |
| Northern lights, TV | 14.46 | 17.83 | 12.00 | 14.79 |
| Soft glow, phone | 18.28 | 18.87 | 15.17 | 15.66 |
| Soft glow, TV | 18.36 | 18.85 | 15.24 | 15.64 |
| Starfield, phone | 15.09 | 18.18 | 12.52 | 15.08 |
| Starfield, TV | 15.02 | 17.96 | 12.46 | 14.90 |

### The text field on the glass

The Address field of Add host had a transparent container, so its label, its text and its outline
stood on the background: the text reached the 2:1 of Table A over Northern lights, Light mist and
Starfield, and the outline, `line-strong` at 12 % white, reached about 1.3:1 over the page. It is now
a glass surface of its shape, which is the glass of the panels: it shows the blurred background under
the tint, and it is the `surface` colour where the glass is Off, under High contrast and on the plain
page. The glass is one surface from the top of the field to the end of its message, with the outline
on it.

- **The text.** The label of a field at rest is `ink-3` as the glass has it, the colour that keeps
  4.5:1 with 2% to spare over the brightest pixel behind the glass of the style (the table of the
  tint floors). The typed text is `ink`. The label of a focused field is `accent-soft`, which the
  glass keeps at 4.5:1 and `accent` does not: `accent` reaches 4.39 over Northern lights at the
  default. The message of an error is `danger-ink`.
- **The outline.** A non-text indicator needs 3:1 (WCAG 1.4.11). It is `ink-3` of the glass at rest
  (the colour that keeps 4.5:1, so more than 3:1), `accent` on focus (the colour of the focus of the
  field as it was) and `danger-ink` with an error. At rest it is 1 dp and on focus 2 dp. On the solid
  surface `ink-3` is 5.59:1 and `accent` 6.61:1.

**Over the glass.** The ratio of each colour over the brightest pixel behind a glass of each style,
in the table that the tint floors come from (`(L + 0.05) / (backdrop + 0.05)`), at the default look
and at Reduced:

Table L. The colours of the text field over the brightest pixel behind its glass, Standard (40 %, 8 dp, raised to the floor of the style).

| Style | Tint | Brightest pixel | Typed text `ink` | Label and outline, glass `ink-3` | Focused label `accent-soft` | Error text `danger-ink` | Focused outline `accent` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Aurora curtains | 40 % | 0.0222 | 12.06 | 4.63 | 7.87 | 6.37 | 5.34 |
| Soft glow | 40 % | 0.0190 | 12.63 | 4.73 | 8.24 | 6.67 | 5.59 |
| Northern lights | 70 % | 0.0378 | 9.92 | 4.60 | 6.47 | 5.24 | 4.39 |
| Light mist | 40 % | 0.0307 | 10.80 | 4.64 | 7.05 | 5.70 | 4.78 |
| Flowing light | 40 % | 0.0495 | 8.75 | 4.62 | 5.71 | 4.62 | 3.88 |
| Starfield | 40 % | 0.0155 | 13.31 | 4.99 | 8.69 | 7.03 | 5.89 |

Table M. The same at Reduced (85 %, 48 dp).

| Style | Tint | Brightest pixel | Typed text `ink` | Label and outline, glass `ink-3` | Focused label `accent-soft` | Error text `danger-ink` | Focused outline `accent` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Aurora curtains | 85 % | 0.0103 | 14.44 | 5.41 | 9.42 | 7.62 | 6.39 |
| Soft glow | 85 % | 0.0104 | 14.42 | 5.40 | 9.41 | 7.61 | 6.39 |
| Northern lights | 85 % | 0.0141 | 13.59 | 5.09 | 8.88 | 7.18 | 6.02 |
| Light mist | 85 % | 0.0101 | 14.49 | 5.43 | 9.46 | 7.65 | 6.42 |
| Flowing light | 85 % | 0.0143 | 13.54 | 5.07 | 8.84 | 7.15 | 6.00 |
| Starfield | 85 % | 0.0089 | 14.80 | 5.55 | 9.66 | 7.82 | 6.56 |

The tests of `BorealisTextField` read what the field draws, the label, the text, the message and the
pixel of the outline, and fail under 4.5:1 for the text and 3:1 for the outlines, at the default and
at Reduced, over every style.

- **The label floats inside the glass.** The field is a filled field of Material 3 with no container,
  indicator or outline of its own. An outlined field floats its label over the top of its outline,
  where the outline is cut to make room for it: half of the label would stand on the background and
  not on the glass.
- **Not covered.**
  - The outside of the outline faces the background. The glass is what makes the field stand out, and
    the ratio is that of the outline with the glass inside it.
  - `danger-ink` is not one of the colours that the tint floor keeps at 4.5:1. At the default and at
    Reduced it keeps it over every style (the lowest is 4.62 over Flowing light), but a viewer who
    chooses a tint and a blur of Settings that leave a brighter backdrop than those could see the
    message of an error under 4.5:1.

### How the app draws an icon

`BackdropIcon` in `:borealis:designsystem` (`components/BackdropIcon.kt`) is the one place that
draws an icon on the background, and it uses the constants of `BackdropHalo`: they are the choice
above, for the text and for the icons.

- **One path, one blur.** Behind the icon the node draws the strokes of its vector as one path. The
  paint has a stroke of the width of the icon's own and twice the halo's, which widens it by the
  halo's 3 dp, a mask filter of the halo's 2 dp radius, which blurs it (Skia gives it the sigma that
  it gives the shadow of a text, which the model has), and the `page` colour at the opacity. It is
  one path and one draw, and not one for each stroke of the icon, because two halos that meet would
  be darker than the union that the model has.
- **When.** Over a background of any style, which is every one but the plain page, with the glass
  Off and under High contrast too, and none outside a host. The node says through `BackdropIconHalo`
  whether the halo is drawn, as `BackdropTextHalo` does for the text.
- **Pinned by pixels.** A test reads the alpha of the halo beside a stroke of an icon at 2, 5, 8 and
  12 px from its edge, against the model within 0.07, with the stroke near the edge of the icon so
  that 12 px are outside its bounds: it fails for a halo that is thinner or wider, fainter, over the
  icon, or cut at its bounds.
- **API level.** The blur of a mask is a part of every paint, so it needs no blur effect (API 31),
  but the halo has not been seen on a device below API 31: the tests draw it with Robolectric.

## High contrast, the colour-blind palettes and the offline colour

These are not measurements over the background, so they have no frames. Their figures come from
[`design/tokens.json`](../../design/tokens.json) at `c6e4b92a` and are pinned by tests, which are
named in each part. The decisions are in
[ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md), decisions 15 to 17.

### High contrast

The `high-contrast` group of the tokens replaces the roles of the same name while the setting is on
(`BorealisColors.withHighContrast`, `HighContrastColorsTest`). The ratio is the lowest over `page`,
`chrome`, `surface` and `surface-raised`, and under the accent tint of a selected row and the hover,
each over the three surfaces.

| Role | Colour | Lowest ratio |
| --- | --- | --- |
| `ink` | `#FFFFFF` | 13.62 |
| `ink-2` | `#E8EAED` | 11.30 |
| `ink-3` | `#D0D3D7` | 9.07 |
| `ink-4` | `#BEC1C4` | 7.54 |
| `accent` | `#C8B7FC` | 7.55 (`on-accent` keeps 10:1 on it) |
| `accent-soft` | `#D6CCFE` | 9.04 |
| `success-ink` | `#7ED48B` | 7.58 |
| `danger-ink` | `#FFABA4` | 7.53 |
| `line`, opaque | `#80868D` | 3.71 |
| `line-strong`, opaque | `#9CA2A8` | 5.29 |
| `offline` | `#848B92` | 3.95 |

- **The dim.** The `page` colour lies over the animated background at 0.85. Over the brightest pixel
  of the 12 sets of frames of the six backgrounds every text role keeps 8:1 or more, and the lowest
  is `ink-4` at 8.00:1. For the loose text the dim and the halo give the numbers of Tables G and H.
  `BackgroundDimTest` pins the veil over a drawn style and over the still of a clip.
- **The outline** is 5 dp and drawn in `ink` (in the white of High contrast there): with the usual
  `ink` it is 15.8:1 on `page`, 14.9:1 on `surface` and 6.9:1 on the brightest backdrop that a glass
  panel is allowed. `ThickFocusOutlineTest` keeps it at 3:1 or more over all of them. The thicker
  outline of the Accessibility group is the same outline.
- **What it overrides.** Only Transparency, Thicker focus outline, Glass tint and Glass blur. Their
  rows are disabled and say "High contrast is on in Accessibility". Text size, the palette and
  Reduce motion still apply, and a palette is applied after High contrast: the status inks of both
  palettes keep 7:1 or more (the bar of `ColourVisionPaletteTest`; 9.2:1 measured), so the two
  compose.

### The offline colour and the status shapes

The offline colour was `#4B5158`, 2.1 to 2.4:1 over the fills (2.2:1 on `surface`), which fails the
3:1 that a non-text indicator needs (WCAG 1.4.11). It is `#686E75`: 3.70:1 on `page`, 3.49:1 on
`surface` and 3.25:1 on `surface-raised` (owner, 2026-10-20). Under High contrast it is `#848B92`,
3.95:1 or more.

Colour is not the only signal of a status (WCAG 1.4.1). The dot of a host has a shape for each state
(`StatusDot`, `StatusDotTest`): a filled circle for online, a filled triangle for attention and a
hollow ring for offline and unknown. The card says the status in words too, and a screen reader
reads it in one sentence with the name.

The Online dot has a soft dark shadow in the page colour, drawn outside its 10 dp box (`StatusDot`).
It is strongest at the edge of the dot, at 85 % of the page colour, and is gone 5 dp outside the box.
It is static, and it is not drawn under High contrast. The shadow is dark, not a glow, because a glow
that touches the dot breaks 3:1 over bright glass. The owner chose it on 2026-10-22.

On a TV the glass is darker, only a little above the page colour (luma 22 in the device round,
against 30 on a phone), and the shadow in the page colour read as a faint ring there. On a TV the
shadow is black, at the same 85 % peak, and is gone 5.75 dp outside the box, still short of the 6 dp
to the status text. In CIE lightness it takes 5.8 off the glass next to the dot on a TV (1.8 with the
page colour), where the phone's takes 4.7 off its own, and it still shows 9.25 dp from the centre
(7.75 before) where the phone's shows 8.25 (`StatusDotTest`). A handheld has the phone's shadow. The
owner decided this on the UAT of 2026-10-22. Contrast of the
dot against what is next to it, over the brightest host-card glass (luminance 0.0495), without and
then with the shadow:

| Palette | Without the shadow | With the shadow |
| --- | --- | --- |
| Today's | 3.79 | 5.68 |
| Red-green | 4.56 | 6.84 |
| Blue-yellow | 3.08 | 4.61 |

On a dark card the shadow is faint. On the Apps title, which sits on the page, it does not show.
Known limitation: the Apps title dot sits on the animated background with no glass. Its 3:1 there is
not measured, and nothing in Sprint 11 makes it worse. The shadow is drawn round that dot too
(`AppsScreen.kt`, the dot of a running app), so it helps there, but this is not measured.

### The colour-blind palettes

The palettes take their colours from the Okabe and Ito set, which is safe for colour vision
deficiency. Each replaces the status roles (`BorealisColors.withColourVision`), and the accent roles
of an accent that the palette would not tell from a status colour (see [the accents under the
colour-blind palettes](#the-accents-under-the-colour-blind-palettes)):

| Role | Red-green | Blue-yellow |
| --- | --- | --- |
| `success` | `#56B4E9` | `#009E73` |
| `success-ink` | `#85C9EF` | `#82CFBA` |
| `warning`, `warning-ink` | `#F0E442` | `#F0E442` |
| `danger` | `#D55E00` | `#D55E00` |
| `danger-ink` | `#ECB78C` | `#ECB78C` |
| `offline` | `#808080` | `#808080` |

- **The check is in Kotlin**: `ColourMeasures` and `ColourVisionPaletteTest` in the tests of
  `:borealis:designsystem`. It simulates the dichromacies with the Machado matrices at severity 1.0
  and measures the distance of two colours in CIEDE2000. Every pair of the four status colours is
  20 or more apart for the dichromacies that the palette is for. The least distance is 23.7 for
  red-green (protanopia and deuteranopia) and 23.9 for blue-yellow (tritanopia). The colours of
  today fail that bar for a red-green dichromat.
- Each status colour keeps 3:1 on `page`, `surface` and `surface-raised`, and each status ink keeps
  7:1 or more as text (the bar of `ColourVisionPaletteTest`; 9.2:1 measured), so High contrast can
  keep the palette.
- The Python contrast tool does not model this: it had a copy until `5fe33a63` removed it, and the
  Kotlin check is the only one.

## Screen readers and touch targets

The checks run in the unit tests, on Robolectric, so they find what a person reviewing by hand
misses on every screen; they are not TalkBack on a device (see the runbook, section 3.23).

### Touch targets

A touch target is 48 dp on a phone and on a handheld. WCAG 2.5.8 asks 24 by 24 px; the app keeps
to the 48 dp of the Android guidance, which the checks of the Accessibility Test Framework (ATF)
enforce. A TV has no touch screen, so it keeps its sizes: the D-pad reaches the targets and the
focus outline shows them. `TouchTargetTest` pins these:

- a segment of a segmented control is 48 dp wide and high, and the 48 dp of a chip is a cell around
  it, so the chip keeps its look;
- a group of the settings list is 48 dp high on a handheld;
- the handle of a bottom sheet is 48 dp wide and high.

`CollapsibleSectionTest` pins the header of a phone section, a 48 dp button. A button (40 dp) and a
menu row (44 dp) keep their size: Compose gives them a 48 dp touch area where no neighbour is in the
way, and the checks pass.

Three effects of the rule wait for the owner's review. The Settings group list of a handheld
scrolls, because its entries are 48 dp. At 800 × 480 dp (the `settingsscreen_handheld_stream.png`
reference) 6 of the 11 groups show (Stream to Audio) and the other 5 need scrolling. In a window 360
dp tall, such as a phone in landscape with a pad, about 4 show. The docked keyboard of a phone is 24
dp taller, because its shortcut chips have 48 dp cells. The keys of the phone keyboard stay under 48
dp (an accepted finding, below).

### Headings, pane titles and what is said

- **Headings**, which a screen reader jumps between: the titles of Hosts (phone and TV), Apps (the
  host name), Add host, Pairing and the Audit trail, the title of Settings on a TV, the title and
  every section of Settings on a phone, the section labels (`BackdropKicker`) and the title of an
  empty state. The tests are `HostsTalkBackTest`, `LibraryScreensTalkBackTest`,
  `AuditTalkBackTest`, `SettingsTalkBackTest` and `ComponentTalkBackTest`.
- **Pane titles**, which a screen reader announces when a window opens: a sheet or a dialog says its
  title, the options of a host say the name of the host, the in-game menu says its header, the
  keyboard says "Keyboard" (`StreamTalkBackTest`).
- **Names and states.** A host card is one sentence: name, status, address, capabilities and "Running
  Desktop"; the status dot says nothing of its own. A pairing step is one thing to read, with its
  number, its text and its address. `SegmentedControl` says its group label after the name of a
  segment, so that the Off of one row is not the Off of another. The rows of the menu and the
  groups of the settings are read in the order that they are drawn.
- **Live regions.** A wrong, expired or failed PIN and the other outcomes of pairing, and the error
  under the address of Add host, are announced when they appear, because the focus stays on the
  button. Snackbars are polite live regions.
- **Large text** (WCAG 1.4.4). The `LargeTextFitTest` classes of the design system and of the
  Library, and `SettingsLargeTextFitTest` and `AuditLargeTextFitTest`, run the screens at a font
  scale of 2.0 and fail on a text that is cut or ends in an ellipsis.

### What the ATF does not see

- **The background.** The checks run with the background Off (`BackgroundStyle.Off`), so their
  contrast checks see the colours that the text really has. Over the animated background the
  contrast is owned by the tool in `design/accessibility/`, which this page describes.
- **TalkBack itself.** Robolectric cannot see the traversal links that TalkBack follows. The
  segments and the keys of the keyboard are still lifted over their neighbours while they have the
  focus, so the merged tree lists the focused one last.
- **A visible snackbar**: no check covers it.

### Accepted findings

A check is never switched off without a reason. `AcceptedFinding` in the test fixtures of
`:borealis:designsystem` takes the class of the check, a reason that is not blank and, optionally,
the elements it applies to; `assertAccessible` fails on any other error or warning. The accepted
findings are:

| Check | Where | Reason |
| --- | --- | --- |
| Touch target | Every screen on a TV (`NoTouchOnTv`) | A TV has no touch screen; its targets are reached by the D-pad and a focus outline. |
| Touch target | The keys of the phone keyboard (`StreamAccessibilityChecksTest`) | The keys of a full keyboard are narrower than 48 dp on a phone, ten to a row, and five rows fit the height of a phone held sideways. The System keyboard chip opens the keyboard of the device. |
| Text contrast | The disabled Display row of the in-game menu | The title is dimmed to 38 %, and WCAG 1.4.3 leaves an inactive component out of the contrast. The note under it keeps full strength. |
| Text contrast | The last chip of the scrolling shortcut row | The check reads the sliver of a chip that the edge of the row cuts off. Whole, the chip is `ink-2` on the surface at 10:1. |
| Text contrast | The buttons of Add host while the host is added | They are disabled, and WCAG 1.4.3 exempts an inactive component. |
| Duplicate name | The Add host button, the title and the rail item (`LibraryAccessibilityChecksTest`) | They carry one name, the role tells them apart, and the label of the button has to be the visible one (WCAG 2.5.3). |
| Duplicate name | The Add host action of the top bar and the button of the empty state of Hosts | They do the same thing, so hearing the name twice costs nothing. |

## Accent candidates (Sprint 11)

Sprint 11 lets the viewer choose the accent colour (issue #50), and the owner chooses at the gate G1
from these numbers. There are four candidate sets of the accent roles: `violet`, which is today's
accent, and `green`, `teal` and `white`, which start from the colours of the icon (`#6EE7B7`,
`#14B8A6` and `#F5F3FF`). [`design/accessibility/accents.json`](../../design/accessibility/accents.json)
has the sets, with a note on how each value was derived, and `contrast.py accents` measures them.
The tokens are not changed: the sets are proposals, and the values may still move at the gate.

**Sprint 11 update.** The icon's green band became the teal in Sprint 11: `#4BECD0` at 95 % to
`#06AF97` at 10 % ([`design/icon/README.md`](../../design/icon/README.md)). The app offers violet,
teal and white. The `green` set is not offered. The measurements below are kept as they were taken.

- Date: 2026-10-21. Revision measured: `c5e91653`, the tool; the colours of `accents.json` are
  those of `d67fe661`, and a note of white has changed since. The frames are the 288 of the
  baseline: the drawn backgrounds and the clips have not changed since.
- The backgrounds are measured as they are drawn today, with their fixed violet. Sprint 11 stops
  the drawn backgrounds from taking the accent (`AuroraBackground` draws its curtains with the
  accent and the strong accent of the tokens), so they keep that violet, and the glass table of
  the app (`GlassContrastTable.kt`) stays valid whichever accent is chosen.
- `python -I design/accessibility/contrast.py accents <frames> --json <result.json> --jobs 4`
  measures it in about nine minutes and prints Tables N to U and the checks that each set fails.

### Method of the accents

- **Where the accent is text.** The selected segment of a segmented control, the selected chip and
  the selected item of the navigation rail draw their label in `accent`, on `accent-tint`, over the
  glass. The focused outline of a text field is `accent` and its label is `accent-soft`. The accent
  fill of a button carries `on-accent`.
- **On the surfaces (Table O).** `on-accent` on `accent` needs 4.5:1. `accent` and `accent-soft`
  as text need 4.5:1 on `page`, `surface` and `surface-raised` and on the tint of a selected row
  over `surface` and over `surface-raised`. The tint is laid in whole 8 bit values, as Compose
  draws it: that is how violet gives the 7.55 and 9.04 of the table of high contrast above and
  of Table T.
  `accent-strong` is given for information: it carries no text.
- **Over the glass (Tables P to S).** The glass of each style at the default of Settings and at
  Reduced, over every frame of both devices, and the luminance of the brightest pixel behind the
  text. At the default the tint is the 40 % of Settings raised to the floor of the style, which
  is worked out with the `accent-soft` of the candidate, as it is one of the colours that the
  floor keeps at 4.5:1 (it is the same for the four: Table P). At Reduced it is 85 % at 48 dp.
  `accent` and `accent-soft` are read as text (4.5:1) and as an outline (3:1), and the selected
  label, `accent` over its own tint laid on that glass, as text (4.5:1). A ratio is held to its
  limit with no margin. The glass of API 28 to 30 (85 %, no blur) is not in these tables: its
  brightest backdrop, 0.0287 (Northern lights), is under the 0.0495 of the Standard glass over
  Flowing light, where each proposal has its lowest ratio, and the proposals have 19 % or more
  to spare there, so a measure of it is not likely to change a verdict.
- **High contrast (Table T).** The opaque `accent` and `accent-soft` need 7:1 and `accent-line`
  3:1 over every fill that text lies on: `page`, `chrome`, `surface`, `surface-raised`, the tint of
  a selected row on `surface` and on `surface-raised`, and the hover on `surface-raised`. These
  are the fills of `HighContrastColorsTest`. `on-accent` needs 7:1 on the high contrast accent.
- **Distance (Table U).** The least CIEDE2000 distance of `accent` from the status colours
  (success, warning, danger, offline), as normal vision, protanopia, deuteranopia and tritanopia
  see them (the matrices of `ColourMeasures.kt`), for today's colours and for the red-green and
  blue-yellow palettes. 20 or more is distinct, as in `ColourVisionPaletteTest`. It is held where
  a palette is for the vision: today's colours for normal vision, the red-green palette for normal
  vision, protanopia and deuteranopia, the blue-yellow palette for normal vision and tritanopia.
  The other rows are for information. Every accent is also held to 20 from `ink` and `ink-2` as
  normal vision sees them, whatever its chroma.

### The candidates

Table N. The role sets of the candidates.

| Role | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- |
| `accent` | `#A78BFA` | `#78EFC7` | `#4BECD0` | `#EFF3FF` |
| `accent-soft` | `#C4B5FD` | `#B9FFE4` | `#A2FCE8` | `#FFFFFF` |
| `accent-strong` | `#7C3AED` | `#36B48F` | `#06AF97` | `#B4B8C4` |
| `on-accent` | `#160B2E` | `#001710` | `#001713` | `#0E1013` |
| `accent-tint` | `#A78BFA21` | `#78EFC721` | `#4BECD021` | `#EFF3FF21` |
| `accent-line` | `#A78BFA66` | `#78EFC766` | `#4BECD066` | `#EFF3FF66` |
| high contrast `accent` | `#C8B7FC` | `#78EFC7` | `#4BECD0` | `#EFF3FF` |
| high contrast `accent-soft` | `#D6CCFE` | `#B9FFE4` | `#A2FCE8` | `#FFFFFF` |
| high contrast `accent-line` | `#C8B7FC` | `#78EFC7` | `#4BECD0` | `#EFF3FF` |

Table O. Text on the opaque fills: the contrast ratio, and the label on an accent fill. The limit of each is in its first column.

| Check | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- |
| `on-accent` on `accent`, 4.50:1 | 6.88 | 13.25 | 12.55 | 17.18 |
| `accent` on `page`, 4.50:1 | 7.00 | 13.58 | 12.89 | 17.18 |
| `accent` on `surface`, 4.50:1 | 6.61 | 12.82 | 12.16 | 16.21 |
| `accent` on `surface-raised`, 4.50:1 | 6.15 | 11.93 | 11.32 | 15.09 |
| `accent` on the tint over `surface`, 4.50:1 | 5.43 | 9.46 | 9.07 | 11.42 |
| `accent` on the tint over `surface-raised`, 4.50:1 | 5.01 | 8.67 | 8.32 | 10.31 |
| `accent-soft` on `page`, 4.50:1 | 10.32 | 16.77 | 15.99 | 19.05 |
| `accent-soft` on `surface`, 4.50:1 | 9.74 | 15.83 | 15.09 | 17.98 |
| `accent-soft` on `surface-raised`, 4.50:1 | 9.06 | 14.73 | 14.04 | 16.73 |
| `accent-soft` on the tint over `surface`, 4.50:1 | 8.01 | 11.68 | 11.25 | 12.66 |
| `accent-soft` on the tint over `surface-raised`, 4.50:1 | 7.38 | 10.71 | 10.32 | 11.44 |
| `accent-strong` on `page` (information) | 3.34 | 7.33 | 6.88 | 9.61 |
| `accent-strong` on `surface` (information) | 3.15 | 6.92 | 6.49 | 9.07 |
| `accent-strong` on `surface-raised` (information) | 2.94 | 6.44 | 6.04 | 8.44 |

Table P. The glass: its tint, which is the 40 % of Settings raised to the floor of the style for the accent-soft of the candidate (85 % at Reduced), and the luminance of the brightest pixel behind the text over every frame of both devices.

| Style | Glass | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 40 %, 0.0222 | 40 %, 0.0222 | 40 %, 0.0222 | 40 %, 0.0222 |
| Aurora curtains | Reduced | 85 %, 0.0103 | 85 %, 0.0103 | 85 %, 0.0103 | 85 %, 0.0103 |
| Soft glow | Standard | 40 %, 0.0190 | 40 %, 0.0190 | 40 %, 0.0190 | 40 %, 0.0190 |
| Soft glow | Reduced | 85 %, 0.0104 | 85 %, 0.0104 | 85 %, 0.0104 | 85 %, 0.0104 |
| Northern lights | Standard | 70 %, 0.0378 | 70 %, 0.0378 | 70 %, 0.0378 | 70 %, 0.0378 |
| Northern lights | Reduced | 85 %, 0.0141 | 85 %, 0.0141 | 85 %, 0.0141 | 85 %, 0.0141 |
| Light mist | Standard | 40 %, 0.0307 | 40 %, 0.0307 | 40 %, 0.0307 | 40 %, 0.0307 |
| Light mist | Reduced | 85 %, 0.0101 | 85 %, 0.0101 | 85 %, 0.0101 | 85 %, 0.0101 |
| Flowing light | Standard | 40 %, 0.0495 | 40 %, 0.0495 | 40 %, 0.0495 | 40 %, 0.0495 |
| Flowing light | Reduced | 85 %, 0.0143 | 85 %, 0.0143 | 85 %, 0.0143 | 85 %, 0.0143 |
| Starfield | Standard | 40 %, 0.0155 | 40 %, 0.0155 | 40 %, 0.0155 | 40 %, 0.0155 |
| Starfield | Reduced | 85 %, 0.0088 | 85 %, 0.0088 | 85 %, 0.0088 | 85 %, 0.0088 |

Table Q. `accent` over the glass: text needs 4.5:1, an outline 3:1.

| Style | Glass | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 5.34 | 10.36 | 9.83 | 13.10 |
| Aurora curtains | Reduced | 6.39 | 12.40 | 11.77 | 15.69 |
| Soft glow | Standard | 5.59 | 10.85 | 10.30 | 13.72 |
| Soft glow | Reduced | 6.39 | 12.39 | 11.76 | 15.67 |
| Northern lights | Standard | 4.39 (outline only) | 8.52 | 8.09 | 10.78 |
| Northern lights | Reduced | 6.02 | 11.68 | 11.09 | 14.77 |
| Light mist | Standard | 4.78 | 9.28 | 8.81 | 11.74 |
| Light mist | Reduced | 6.42 | 12.45 | 11.82 | 15.75 |
| Flowing light | Standard | 3.88 (outline only) | 7.52 | 7.14 | 9.51 |
| Flowing light | Reduced | 6.00 | 11.64 | 11.05 | 14.72 |
| Starfield | Standard | 5.89 | 11.44 | 10.85 | 14.46 |
| Starfield | Reduced | 6.56 | 12.72 | 12.07 | 16.09 |

Table R. `accent-soft` over the glass: text 4.5:1, outline 3:1.

| Style | Glass | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 7.87 | 12.79 | 12.20 | 14.53 |
| Aurora curtains | Reduced | 9.43 | 15.32 | 14.61 | 17.40 |
| Soft glow | Standard | 8.24 | 13.40 | 12.77 | 15.22 |
| Soft glow | Reduced | 9.42 | 15.30 | 14.59 | 17.38 |
| Northern lights | Standard | 6.47 | 10.52 | 10.03 | 11.95 |
| Northern lights | Reduced | 8.88 | 14.43 | 13.75 | 16.39 |
| Light mist | Standard | 7.05 | 11.46 | 10.92 | 13.02 |
| Light mist | Reduced | 9.46 | 15.37 | 14.66 | 17.46 |
| Flowing light | Standard | 5.71 | 9.29 | 8.85 | 10.55 |
| Flowing light | Reduced | 8.84 | 14.37 | 13.70 | 16.33 |
| Starfield | Standard | 8.69 | 14.12 | 13.46 | 16.04 |
| Starfield | Reduced | 9.67 | 15.71 | 14.98 | 17.84 |

Table S. The selected label, `accent` over its tint on the glass.

| Style | Glass | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 4.31 (short) | 7.52 | 7.26 | 8.93 |
| Aurora curtains | Reduced | 5.22 | 9.09 | 8.76 | 10.88 |
| Soft glow | Standard | 4.51 | 7.89 | 7.61 | 9.36 |
| Soft glow | Reduced | 5.21 | 9.09 | 8.76 | 10.87 |
| Northern lights | Standard | 3.62 (short) | 6.15 | 5.92 | 7.41 |
| Northern lights | Reduced | 4.88 | 8.49 | 8.18 | 10.13 |
| Light mist | Standard | 3.88 (short) | 6.81 | 6.58 | 8.06 |
| Light mist | Reduced | 5.24 | 9.14 | 8.80 | 10.94 |
| Flowing light | Standard | 3.21 (short) | 5.55 | 5.35 | 6.63 |
| Flowing light | Reduced | 4.86 | 8.43 | 8.12 | 10.09 |
| Starfield | Standard | 4.77 | 8.29 | 7.99 | 9.88 |
| Starfield | Reduced | 5.38 | 9.38 | 9.03 | 11.24 |

Table T. High contrast: the lowest ratio over the fills that text lies on, the surfaces, the tint of a selected row on them and the hover, and the fill it is on. Text needs 7:1 and a border 3:1.

| Check | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- |
| high contrast `accent`, 7.00:1 | 7.55 (selected row on surface-raised) | 8.67 (selected row on surface-raised) | 8.32 (selected row on surface-raised) | 10.31 (selected row on surface-raised) |
| high contrast `accent-soft`, 7.00:1 | 9.04 (selected row on surface-raised) | 10.71 (selected row on surface-raised) | 10.32 (selected row on surface-raised) | 11.44 (selected row on surface-raised) |
| high contrast `accent-line`, 3.00:1 | 7.55 (selected row on surface-raised) | 8.67 (selected row on surface-raised) | 8.32 (selected row on surface-raised) | 10.31 (selected row on surface-raised) |
| `on-accent` on high contrast `accent`, 7.00:1 | 10.38 (accent) | 13.25 (accent) | 12.55 (accent) | 17.18 (accent) |

Table U. The least CIEDE2000 distance of `accent` from the status colours as each vision sees them, and from the ink. 20 or more apart is distinct. A row that its palette is not for is given for information.

| Check | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- |
| `accent` from the default status colours, normal vision | 23.9, offline | 21.3, success | 23.5, success | 36.1, warning |
| `accent` from the default status colours, protanopia vision | 27.8, offline (not claimed) | 21.7, success (not claimed) | 24.0, success (not claimed) | 36.1, success (not claimed) |
| `accent` from the default status colours, deuteranopia vision | 26.4, offline (not claimed) | 22.9, success (not claimed) | 27.7, success (not claimed) | 35.9, success (not claimed) |
| `accent` from the default status colours, tritanopia vision | 21.0, offline (not claimed) | 17.4, success (not claimed) | 17.4, success (not claimed) | 29.8, warning (not claimed) |
| `accent` from the red-green status colours, normal vision | 24.7, success | 30.8, warning | 29.5, success | 26.2, success |
| `accent` from the red-green status colours, protanopia vision | 9.3, success (short) | 21.2, warning | 24.6, warning | 20.6, success |
| `accent` from the red-green status colours, deuteranopia vision | 5.0, success (short) | 24.7, warning | 21.6, success | 24.7, success |
| `accent` from the red-green status colours, tritanopia vision | 14.8, offline (not claimed) | 12.1, success (not claimed) | 12.0, success (not claimed) | 20.5, warning (not claimed) |
| `accent` from the blue-yellow status colours, normal vision | 27.9, offline | 22.1, success | 21.7, success | 31.5, offline |
| `accent` from the blue-yellow status colours, protanopia vision | 26.6, offline (not claimed) | 21.2, warning (not claimed) | 21.7, success (not claimed) | 31.6, offline (not claimed) |
| `accent` from the blue-yellow status colours, deuteranopia vision | 25.9, offline (not claimed) | 22.6, success (not claimed) | 23.0, offline (not claimed) | 31.4, offline (not claimed) |
| `accent` from the blue-yellow status colours, tritanopia vision | 14.8, offline (short) | 21.3, success | 21.1, success | 20.5, warning |
| `accent` from `ink`, normal vision | 28.9, ink | 24.9, ink | 25.4, ink | 4.5, ink (short) |
| `accent` from `ink-2`, normal vision | 21.9, ink-2 | 25.1, ink-2 | 25.0, ink-2 | 10.6, ink-2 (short) |

### What the tables show

- **Violet, today's accent.**
  - It keeps the text and the high contrast checks: the lowest on the surfaces is 5.01:1 (`accent`
    on its tint over `surface-raised`), and high contrast is 7.55 for `accent` and 9.04 for
    `accent-soft`, the figures of the table of high contrast above.
  - Over the glass `accent-soft` keeps 5.71:1 or more everywhere, and `accent` keeps its 3:1 as an
    outline everywhere, but as text at the default it is 4.39:1 over Northern lights and 3.88:1
    over Flowing light, short of 4.5:1. The selected label is short at the default over four of the
    six styles: 4.31 over Aurora curtains, 3.62 over Northern lights, 3.88 over Light mist and
    3.21 over Flowing light. Soft glow (4.51) and Starfield (4.77) keep it, and at Reduced it is
    4.86 or more over all.
  - It is not told from the status colours by a dichromat: a protanope and a deuteranope see
    it 9.3 and 5.0 from the sky blue success of the red-green palette, and a tritanope 14.8 from
    the grey offline of the blue-yellow palette. Those three rows fail the 20.
- **The icon's band is the teal now.** In Sprint 11 the green band of the app icon became the teal
  (`#4BECD0` at 95 % to `#06AF97` at 10 %). The figures of this bullet are the record of the
  candidates as they were measured, and they are not changed.
- **The icon colours as they are do not pass.** Measured with the tool, with the same ladder of
  roles and over the Flowing light frames (the brightest glass), the icon green `#6EE7B7` is 18.5
  from the success green of today's palette, 19.2 from the warning yellow of the red-green palette
  as a protanope sees it, and 19.3 from the success of the blue-yellow palette as a tritanope sees
  it. The icon teal
  `#14B8A6` is 4.24:1 as text over the Flowing light glass at the default and 3.52:1 as a selected
  label, its high contrast `accent` and `accent-soft` are 5.45 and 6.97:1, and it is 19.7, 15.2,
  11.5, 11.7 and 8.9 from the status colours in the default palette, the red-green palette as a
  protanope and a deuteranope sees it, and the blue-yellow palette as normal vision and a tritanope
  sees it. The icon star `#F5F3FF` is 17.3 from the warning yellow of the blue-yellow palette as a
  tritanope sees it, which sees that yellow as a pale pink.
- **Green, teal and white are the nearest colours of each hue that pass.** Green `#78EFC7` is 2.9
  CIEDE2000 from the icon green; teal `#4BECD0` is lightened to L\* 85 (13.0 from the icon teal),
  and the white is `#EFF3FF`, 2.9 from the star. The soft, strong, on-accent and the tint and
  line follow the ladder of violet, and `accents.json` says how for each. The floors of the glass
  do not change.
  - **Green** fails no check. Over the glass the lowest is 7.52:1 for `accent`, 9.29:1 for
    `accent-soft` and 5.55:1 for the selected label, all over Flowing light at the default. High
    contrast keeps 8.67:1. The least distance is 21.2, from the warning yellow of the red-green
    palette as a protanope sees it.
  - **Teal** fails no check. The lowest over the glass is 7.14, 8.85 and 5.35:1, high contrast keeps
    8.32:1, and the least distance is 21.1, from the success of the blue-yellow palette as a
    tritanope sees it.
  - **White** fails two checks, which no value passes (below). The lowest over the glass is 9.51,
    10.55 and 6.63:1, high contrast keeps 10.31:1 and its least distance from a status colour is
    20.5.
- **The distances have the least to spare.** Over the glass every ratio of the three proposals is
  at least 19 % over its limit, and on the surfaces at least 85 %, but the distances are 20.5 to
  21.2 against 20. The model of colour vision is an approximation, so a value that moves at the gate
  should keep the distance in sight (`contrast.py accents` with another `accents.json`).
- **Why the proposals are light.** An accent has to be read as text over the brightest glass of
  the Standard default, 0.0495 (Flowing light), at 4.5:1, which needs a luminance of 0.40
  (L\* 69.5), and as the label of a selected item over its own tint, which makes that backdrop
  about 40 % brighter and needs about 0.5 (L\* 76). The distances from the status colours push it
  higher still: a search of the hues from green to cyan (124 to 216 degrees) found colours that
  pass every check only at L\* 80 and over.

### Checks that no value can pass

- **White and the ink.** An accent with no chroma is told from `ink` (`#E8EAED`) and `ink-2`
  (`#C3C7CC`) by its lightness alone. A neutral that is light enough to be read as text over the
  Standard glass (L\* 69.5, and 4.5:1 over Flowing light) is at most 19.7 from `ink` and 16.6 from
  `ink-2`, and none is more than 15.0 from both, so `distance:ink` and `distance:ink-2` fail for any
  white. The bounds are sampled: every colour of CIELAB chroma under 10 and L\* 69.5 or more, each
  channel of sRGB from 130 to 255 in steps of 1. The accent of such a set is told from the ink by
  its fill, with the label on it at 17.18:1, and by its tint, and not by its colour. They are the
  `known_fails` of the set.
- **Violet and the dichromats.** The three rows that violet fails (`distance:red-green:protanopia`,
  `distance:red-green:deuteranopia` and `distance:blue-yellow:tritanopia`) are the violet of
  today, which a protanope and a deuteranope see as blue and a tritanope as grey. They are the
  `known_fails` of violet, and green and teal pass them. The app does not draw that violet under a
  colour-blind palette: it draws the variant of [the accents under the colour-blind
  palettes](#the-accents-under-the-colour-blind-palettes).
- **The checks that need a frame** are not in `known_fails`: a test keeps the other checks of each
  set equal to the file, and the glass ones of violet are in the tables above.

### What the app draws (Sprint 11)

- **Selected labels use `accent-soft`.** The selected label of a segment, a chip, a rail item, a
  Settings group and a choice sheet, the text button label, the current step number and the latched
  key label use `accent-soft`, not `accent`. Over the glass it keeps 4.5:1 in every accent. The lowest
  is 4.74:1, violet over Flowing light at the default (Table W). The old violet `accent` gave 3.21:1
  there (Table S).
- **White marks the selection.** Its colour alone is too close to the text colour to show a selection,
  so the selected item has a 3 dp indicator bar. The bar is at the start edge: the left in
  left-to-right layouts and the right in right-to-left layouts.
- **`accent-inverse`.** The snackbar action is drawn in `accent-inverse` on the light inverse surface
  (`ink`). Violet `#7C3AED`, teal `#027464` and white `#636671` are each 4.73:1 or more on `ink`. In
  High contrast, `#5744A9`, `#026053` and `#51555F` are each 7.46:1 or more on white.

### Table W: the selected label (`selected-soft`)

Table W. The selected label of the app: `accent-soft` over its tint on the glass, as text. Text needs
4.5:1. Green is a candidate that the app does not offer.

| Style | Glass | Violet (today) | Green | Teal | White |
| --- | --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 6.35 | 9.28 | 9.00 | 9.90 |
| Aurora curtains | Reduced | 7.69 | 11.23 | 10.86 | 12.07 |
| Soft glow | Standard | 6.65 | 9.74 | 9.45 | 10.38 |
| Soft glow | Reduced | 7.68 | 11.22 | 10.86 | 12.06 |
| Northern lights | Standard | 5.33 | 7.60 | 7.34 | 8.22 |
| Northern lights | Reduced | 7.19 | 10.48 | 10.15 | 11.24 |
| Light mist | Standard | 5.71 | 8.41 | 8.16 | 8.93 |
| Light mist | Reduced | 7.72 | 11.28 | 10.92 | 12.13 |
| Flowing light | Standard | 4.74 | 6.86 | 6.64 | 7.35 |
| Flowing light | Reduced | 7.17 | 10.41 | 10.08 | 11.19 |
| Starfield | Standard | 7.03 | 10.23 | 9.92 | 10.96 |
| Starfield | Reduced | 7.93 | 11.59 | 11.21 | 12.47 |

### The accents under the colour-blind palettes

A palette replaces the status colours, and the accent stayed as it was. So an accent could be a
colour that the viewer of the palette does not tell from a status colour: to a protanope and a
deuteranope violet is a blue, as the sky blue of the success colour of the Red-green palette is. The
owner saw it in the user test of 2026-10-22, where the palette did nothing to the colour of the
selection, and decided that the accent adapts to the palette (issue #50; [ADR
0058](../adr/0058-appearance-and-reading-options.md), decision 5).

- Date: 2026-10-22. Revision measured: `22ef9310`, the tool and the colours of `accents.json`; the
  fix of `b327cd39` changes no figure. The frames are the 288 of the baseline, as above. The values
  are those of the tokens `accent-violet-red-green`, `accent-violet-blue-yellow` and
  `accent-white-blue-yellow`, and `AccentPaletteVariantsTest` keeps the app equal to them.
- `python -I design/accessibility/contrast.py accents <frames> --palette red-green` (or
  `blue-yellow`) measures the accents as that palette draws them, and prints Tables N to X for them.
  Table X does not need the frames.
- **The rule.** For each colour-blind palette, `accent`, `accent-soft` and the colours of High
  contrast (`accent`, `accent-soft` and `accent-line`) are 20 or more of CIEDE2000 from each of the
  four status colours (success, warning, danger, offline), as normal vision and the vision that the
  palette is for see them: protanopia and deuteranopia for Red-green, tritanopia for Blue-yellow
  (the matrices of `ColourMeasures.kt`). It is the bar that `ColourVisionPaletteTest` holds for the
  four status colours among themselves. A variant is chosen to be 21 or more where it can be, as the
  sets that pass have 20.5 or more to spare and the model of colour vision is an approximation.
- **Measured and not held.**
  - `accent-strong` carries no text and no part of the app draws it.
  - `accent-line` is the accent at 40 %, measured over `surface`, a dark tone. For an accent that is
    light enough to carry its text (4.5:1) it is 20 from the four status colours for no colour of
    the sRGB gamut in the Red-green palette, and for a magenta alone in the Blue-yellow palette:
    the offline grey is the colour of the middle of that range of tones. Teal's is 10.5 from it as a
    protanope sees it, and white's 7.8.
  - The text colours of the status roles (`success-ink`, `warning-ink`, `danger-ink`) are given
    where one is nearer than 20 (`ink 15.3, success-ink`). They are never held: they are lightened
    to be read as text, so every accent that can be read as text is near one of them in the
    Red-green palette. Teal is 15.3 from the success text colour of today's palette, and 23.5 from
    the success colour.
- **Which pairs fall short (Table X).**
  - Violet under Red-green: `accent` is 9.3 (protanopia) and 5.0 (deuteranopia) from the success
    colour, `accent-soft` 3.5 and 7.3, and the High contrast colours are no better (3.8 and 8.0, 8.6
    and 13.3).
  - Violet under Blue-yellow: `accent` is 14.8 from the grey of the offline colour as a tritanope
    sees it, and the High contrast `accent` and `accent-soft` are 19.7 and 18.6 from the warning
    yellow, which a tritanope sees as a pale pink. Its `accent-soft` keeps 20.9.
  - White under Blue-yellow: `accent-soft`, the pure white, is 16.8 from the warning yellow as a
    tritanope sees it, and so is the High contrast `accent-soft`.
  - Keep the 20: teal under both palettes (21.6 at the least under Red-green, as a deuteranope
    sees it, and 21.1 under Blue-yellow, as a tritanope sees it, both from the success colour), and
    white under Red-green (20.6 as a protanope sees it).
- **The variants.** A variant is the whole set of roles of an accent under a palette, as a
  candidate is, and `accents.json` has a note on how each value was derived.
  The owner chose them on 2026-10-22, from a sheet of the colours as the vision is simulated:
  violet under Red-green and Blue-yellow, and the soft accent of white under Blue-yellow. Teal is
  not changed under either palette.

Table N, for the variants. The role sets of the variants.

| Role | Violet, Red-green | Violet, Blue-yellow | White, Blue-yellow |
| --- | --- | --- | --- |
| `accent` | `#F4BCBC` | `#C688FE` | `#EFF3FF` |
| `accent-soft` | `#FCC3C8` | `#C8BBFE` | `#F3FCFF` |
| `accent-strong` | `#B88484` | `#934BBA` | `#B4B8C4` |
| `on-accent` | `#200E0D` | `#1D092E` | `#0E1013` |
| `accent-tint` | `#F4BCBC21` | `#C688FE21` | `#EFF3FF21` |
| `accent-line` | `#F4BCBC66` | `#C688FE66` | `#EFF3FF66` |
| `accent-inverse` | `#8A5A5A` | `#834BBA` | `#636671` |
| high contrast `accent` | `#F4BCBC` | `#C7B9FC` | `#EFF3FF` |
| high contrast `accent-soft` | `#FCC3C8` | `#CAC4FE` | `#F3FCFF` |
| high contrast `accent-line` | `#F4BCBC` | `#C7B9FC` | `#EFF3FF` |
| high contrast `accent-inverse` | `#774849` | `#6F38A6` | `#51555F` |

- **Violet under Blue-yellow** is `#C688FE`, a lighter and pinker violet: 0.049 from violet in
  OKLab, 6.2 in CIEDE2000, and the hue of OKLCH moves from 293.5 to 306.9 degrees. `accent` is 21.1
  from the status colours as a tritanope sees them (offline) and 29.4 as normal vision sees them,
  and 28.1 and 21.5 from ink and ink-2. It keeps every contrast rule of violet, and over the glass
  it is no worse: `accent` as text 4.20:1 at the least over Flowing light (outline only, as violet's
  3.88:1), `accent-soft` 6.03:1 (violet 5.71) and the selected label 4.95:1 (violet 4.74), with the
  floors of the glass unchanged. High contrast keeps 7.55:1 for `accent`, 8.23:1 for `accent-soft`
  and 7.52:1 for the snackbar action.
- **Violet under Red-green has no violet.** The colours within 45 degrees of the hue of violet that
  are 20 from the four status colours and from ink are all darker than L 0.57 in OKLCH (Y 0.17):
  the nearest, `#755AD8`, keeps 3.35:1 on `surface-raised` and 2.96:1 on the tint of a selected row,
  and none is light enough to carry text over the glass as `accent-soft` does (Y 0.49 or more).
  The nearest colour of any hue that keeps the 20 and every contrast rule is a pale rose,
  `#F4BCBC`: 0.215 from violet in OKLab and 30.5 in CIEDE2000. It is 28.6, 21.0 and 22.3 from the
  status colours as normal vision, a protanope and a deuteranope see them, and it fails no check
  (`accent` as text 6.41:1 or more over the glass, the selected label 5.28:1 or more, High contrast
  7.64:1 and 8.25:1). The owner chose it from four ways: the rose, violet as it is under the
  palette, the teal set drawn under it, and the dark violet that keeps the hue and gives up the
  contrast of its text.
- **White under Blue-yellow** changes its `accent-soft` alone, to `#F3FCFF`, a white with a trace of
  cyan: 22.9 from the warning yellow as a tritanope sees it, and 4.1 CIEDE2000 from pure white. It
  keeps 10.14:1 over the glass as text and 7.07:1 as the selected label, where the pure white keeps
  10.55 and 7.35. The accent `#EFF3FF` (20.5) and every other value stay, and so do its known
  failures, the distance from ink and from ink-2.
- **What the app draws.** `BorealisColors.withColourVision(vision, accent, highContrast)` gives the
  status roles of the palette and, for these three pairs, the variant in place of the accent family
  of `withAccent` and, under High contrast, of `withHighContrast`. `borealisColors` keeps the order:
  accent, then High contrast, then the palette. With the palette off, and for teal, and for white
  under Red-green, nothing changes. The drawn backgrounds keep their fixed violet, so the glass
  table stays valid. The floors of the glass are those of the effective colours, and they are the
  floors of the accents that the variants replace.

Table X. The least CIEDE2000 distance of each role that marks a selection or an accent from the four status colours of each palette, as each vision sees them, for the accents and for their variants under a colour-blind palette (a variant has cells under its own palette alone). 20 or more apart is distinct. The accent and the soft accent and the colours of high contrast are held to 20 under the colour-blind palettes (`short` when they are not); the strong accent and the line (over `surface`) are measured and `not held`, and the colours of today's palette are given for information. `ink` is the least distance from the text colours of the status roles, given when it is under 20 and never held.

| Check | Violet (today) | Green | Teal | White | Violet, Red-green palette | Violet, Blue-yellow palette | White, Blue-yellow palette |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `accent` from the default status colours, normal vision | 23.9, offline | 21.3, success; ink 11.6, success-ink | 23.5, success; ink 15.3, success-ink | 36.1, warning | - | - | - |
| `accent-soft` from the default status colours, normal vision | 30.9, offline | 26.5, success; ink 16.1, success-ink | 27.3, success; ink 17.5, success-ink | 32.2, warning | - | - | - |
| `accent-strong` from the default status colours, normal vision | 20.1, offline | 13.3, success (information); ink 13.1, success-ink | 17.4, success (information); ink 17.4, success-ink | 25.2, offline | - | - | - |
| `accent-line` from the default status colours, normal vision | 19.9, offline (information) | 18.3, offline (information) | 19.4, offline (information) | 1.7, offline (information) | - | - | - |
| high contrast `accent` from the default status colours, normal vision | 31.5, offline | 21.3, success; ink 11.6, success-ink | 23.5, success; ink 15.3, success-ink | 36.1, warning | - | - | - |
| high contrast `accent-soft` from the default status colours, normal vision | 34.7, offline | 26.5, success; ink 16.1, success-ink | 27.3, success; ink 17.5, success-ink | 32.2, warning | - | - | - |
| high contrast `accent-line` from the default status colours, normal vision | 31.5, offline | 21.3, success; ink 11.6, success-ink | 23.5, success; ink 15.3, success-ink | 36.1, warning | - | - | - |
| `accent` from the red-green status colours, normal vision | 24.7, success | 30.8, warning | 29.5, success | 26.2, success | 28.6, offline; ink 16.5, danger-ink | - | - |
| `accent-soft` from the red-green status colours, normal vision | 24.4, success | 29.3, warning | 29.0, success | 28.6, success | 30.1, offline; ink 18.8, danger-ink | - | - |
| `accent-strong` from the red-green status colours, normal vision | 32.6, offline | 26.7, offline | 26.1, offline | 18.6, offline (not held); ink 16.6, success-ink | 19.1, offline (not held) | - | - |
| `accent-line` from the red-green status colours, normal vision | 27.5, offline | 21.0, offline | 22.7, offline | 7.8, offline (not held) | 16.9, offline (not held) | - | - |
| high contrast `accent` from the red-green status colours, normal vision | 25.4, success | 30.8, warning | 29.5, success | 26.2, success | 28.6, offline; ink 16.5, danger-ink | - | - |
| high contrast `accent-soft` from the red-green status colours, normal vision | 26.6, success | 29.3, warning | 29.0, success | 28.6, success | 30.1, offline; ink 18.8, danger-ink | - | - |
| high contrast `accent-line` from the red-green status colours, normal vision | 25.4, success | 30.8, warning | 29.5, success | 26.2, success | 28.6, offline; ink 16.5, danger-ink | - | - |
| `accent` from the red-green status colours, protanopia vision | 9.3, success (short); ink 15.5, success-ink | 21.2, warning; ink 11.2, danger-ink | 24.6, warning; ink 13.8, danger-ink | 20.6, success; ink 14.5, success-ink | 21.0, offline; ink 13.6, danger-ink | - | - |
| `accent-soft` from the red-green status colours, protanopia vision | 3.5, success (short); ink 5.4, success-ink | 24.4, warning; ink 16.6, danger-ink | 26.3, warning; ink 17.8, danger-ink | 25.5, success; ink 19.6, success-ink | 21.5, success; ink 16.1, danger-ink | - | - |
| `accent-strong` from the red-green status colours, protanopia vision | 24.1, success | 17.3, offline (not held); ink 8.1, danger-ink | 13.3, offline (not held); ink 13.0, danger-ink | 13.5, success (not held); ink 10.5, success-ink | 5.6, offline (not held); ink 19.8, danger-ink | - | - |
| `accent-line` from the red-green status colours, protanopia vision | 25.8, offline | 10.8, offline (not held) | 10.5, offline (not held) | 7.8, offline (not held) | 14.1, offline (not held) | - | - |
| high contrast `accent` from the red-green status colours, protanopia vision | 3.8, success (short); ink 4.9, success-ink | 21.2, warning; ink 11.2, danger-ink | 24.6, warning; ink 13.8, danger-ink | 20.6, success; ink 14.5, success-ink | 21.0, offline; ink 13.6, danger-ink | - | - |
| high contrast `accent-soft` from the red-green status colours, protanopia vision | 8.6, success (short); ink 2.9, success-ink | 24.4, warning; ink 16.6, danger-ink | 26.3, warning; ink 17.8, danger-ink | 25.5, success; ink 19.6, success-ink | 21.5, success; ink 16.1, danger-ink | - | - |
| high contrast `accent-line` from the red-green status colours, protanopia vision | 3.8, success (short); ink 4.9, success-ink | 21.2, warning; ink 11.2, danger-ink | 24.6, warning; ink 13.8, danger-ink | 20.6, success; ink 14.5, success-ink | 21.0, offline; ink 13.6, danger-ink | - | - |
| `accent` from the red-green status colours, deuteranopia vision | 5.0, success (short); ink 12.1, success-ink | 24.7, warning; ink 14.4, danger-ink | 21.6, success; ink 15.8, success-ink | 24.7, success; ink 17.6, success-ink | 22.3, warning; ink 10.9, danger-ink | - | - |
| `accent-soft` from the red-green status colours, deuteranopia vision | 7.3, success (short); ink 2.5, success-ink | 25.5, warning; ink 17.7, danger-ink | 25.1, success; ink 18.6, success-ink | 28.2, warning | 23.5, warning; ink 13.0, danger-ink | - | - |
| `accent-strong` from the red-green status colours, deuteranopia vision | 21.9, success | 10.8, offline (not held); ink 18.1, danger-ink | 7.9, offline (not held); ink 19.2, success-ink | 16.5, success (not held); ink 11.9, success-ink | 11.2, offline (not held); ink 17.5, danger-ink | - | - |
| `accent-line` from the red-green status colours, deuteranopia vision | 25.7, offline | 11.9, offline (not held) | 13.3, offline (not held) | 7.8, offline (not held) | 13.0, offline (not held) | - | - |
| high contrast `accent` from the red-green status colours, deuteranopia vision | 8.0, success (short); ink 2.1, success-ink | 24.7, warning; ink 14.4, danger-ink | 21.6, success; ink 15.8, success-ink | 24.7, success; ink 17.6, success-ink | 22.3, warning; ink 10.9, danger-ink | - | - |
| high contrast `accent-soft` from the red-green status colours, deuteranopia vision | 13.3, success (short); ink 6.0, success-ink | 25.5, warning; ink 17.7, danger-ink | 25.1, success; ink 18.6, success-ink | 28.2, warning | 23.5, warning; ink 13.0, danger-ink | - | - |
| high contrast `accent-line` from the red-green status colours, deuteranopia vision | 8.0, success (short); ink 2.1, success-ink | 24.7, warning; ink 14.4, danger-ink | 21.6, success; ink 15.8, success-ink | 24.7, success; ink 17.6, success-ink | 22.3, warning; ink 10.9, danger-ink | - | - |
| `accent` from the blue-yellow status colours, normal vision | 27.9, offline | 22.1, success; ink 8.8, success-ink | 21.7, success; ink 8.8, success-ink | 31.5, offline | - | 29.4, offline | 31.5, offline |
| `accent-soft` from the blue-yellow status colours, normal vision | 29.1, offline | 27.8, success; ink 11.3, success-ink | 26.9, success; ink 10.1, success-ink | 28.9, warning | - | 29.4, offline | 31.5, warning |
| `accent-strong` from the blue-yellow status colours, normal vision | 32.6, offline | 7.4, success (not held); ink 10.9, success-ink | 8.3, success (not held); ink 12.0, success-ink | 18.6, offline (not held) | - | 28.2, offline | 18.6, offline (not held) |
| `accent-line` from the blue-yellow status colours, normal vision | 27.5, offline | 19.1, success (not held) | 20.0, success | 7.8, offline (not held) | - | 27.9, offline | 7.8, offline (not held) |
| high contrast `accent` from the blue-yellow status colours, normal vision | 29.2, offline | 22.1, success; ink 8.8, success-ink | 21.7, success; ink 8.8, success-ink | 31.5, offline | - | 29.1, offline | 31.5, offline |
| high contrast `accent-soft` from the blue-yellow status colours, normal vision | 30.0, offline | 27.8, success; ink 11.3, success-ink | 26.9, success; ink 10.1, success-ink | 28.9, warning | - | 29.3, offline | 31.5, warning |
| high contrast `accent-line` from the blue-yellow status colours, normal vision | 29.2, offline | 22.1, success; ink 8.8, success-ink | 21.7, success; ink 8.8, success-ink | 31.5, offline | - | 29.1, offline | 31.5, offline |
| `accent` from the blue-yellow status colours, tritanopia vision | 14.8, offline (short) | 21.3, success; ink 7.7, success-ink | 21.1, success; ink 8.4, success-ink | 20.5, warning | - | 21.1, offline; ink 16.4, danger-ink | 20.5, warning |
| `accent-soft` from the blue-yellow status colours, tritanopia vision | 20.9, offline | 26.2, success; ink 11.0, success-ink | 25.1, success; ink 10.2, success-ink | 16.8, warning (short); ink 16.8, warning-ink | - | 21.4, warning | 22.9, warning |
| `accent-strong` from the blue-yellow status colours, tritanopia vision | 17.4, offline (not held) | 6.9, success (not held); ink 9.3, success-ink | 6.1, success (not held); ink 9.8, success-ink | 18.2, offline (not held); ink 19.4, success-ink | - | 21.1, offline | 18.2, offline (not held); ink 19.4, success-ink |
| `accent-line` from the blue-yellow status colours, tritanopia vision | 20.2, offline | 17.5, success (not held) | 17.6, success (not held) | 7.5, offline (not held) | - | 20.5, offline | 7.5, offline (not held) |
| high contrast `accent` from the blue-yellow status colours, tritanopia vision | 19.7, warning (short); ink 19.7, warning-ink | 21.3, success; ink 7.7, success-ink | 21.1, success; ink 8.4, success-ink | 20.5, warning | - | 21.1, warning | 20.5, warning |
| high contrast `accent-soft` from the blue-yellow status colours, tritanopia vision | 18.6, warning (short); ink 18.6, warning-ink | 26.2, success; ink 11.0, success-ink | 25.1, success; ink 10.2, success-ink | 16.8, warning (short); ink 16.8, warning-ink | - | 23.4, offline; ink 20.0, success-ink | 22.9, warning |
| high contrast `accent-line` from the blue-yellow status colours, tritanopia vision | 19.7, warning (short); ink 19.7, warning-ink | 21.3, success; ink 7.7, success-ink | 21.1, success; ink 8.4, success-ink | 20.5, warning | - | 21.1, warning | 20.5, warning |

Table W, for the variants. The selected label of the app, `accent-soft` over its tint on the glass,
as text. Text needs 4.5:1.

| Style | Glass | Violet, Red-green | Violet, Blue-yellow | White, Blue-yellow |
| --- | --- | --- | --- | --- |
| Aurora curtains | Standard | 7.08 | 6.62 | 9.51 |
| Aurora curtains | Reduced | 8.60 | 8.04 | 11.60 |
| Soft glow | Standard | 7.41 | 6.93 | 9.98 |
| Soft glow | Reduced | 8.58 | 8.02 | 11.58 |
| Northern lights | Standard | 5.93 | 5.59 | 7.90 |
| Northern lights | Reduced | 8.02 | 7.51 | 10.80 |
| Light mist | Standard | 6.39 | 5.95 | 8.59 |
| Light mist | Reduced | 8.64 | 8.07 | 11.66 |
| Flowing light | Standard | 5.28 | 4.95 | 7.07 |
| Flowing light | Reduced | 7.99 | 7.49 | 10.75 |
| Starfield | Standard | 7.83 | 7.34 | 10.53 |
| Starfield | Reduced | 8.87 | 8.28 | 11.98 |

## Halo per font (Sprint 11)

Sprint 11 adds a choice of font in which every font has an accessibility purpose (issue #48), and
the halo of [Halo](#halo-text-straight-on-the-background) is one for all of them. This section
measures whether it keeps the contrast of the text in each candidate font, and in the monospaced
companion of each that has one, as the app will draw them: the text set at the size of the tokens
times the scale that sets the x-height of the font to that of IBM Plex Sans.

- Date: 2026-10-21. Revision measured: `c5e91653`, the tool and `design/tokens.json` as they are
  after the merge of the TV text size (#47) and the change of the scale to the drawn x. The frames
  are the 288 of the baseline. Andika, B612 and B612 Mono were measured at that revision, and Lexend
  again as a control, which gave the same rows. The other five sets are those of `84c85c96`: the
  scale of each is the same under the drawn x, so a new run would give the same rows. The fonts are
  in a folder of their own and not in the repository: the files below were passed to the tool by
  path, and the SHA-256 of each is given by its first 12 digits.
- **The text sizes are those of Sprint 11.** On a TV the body is 16 sp and the label, the least text
  of a TV, is 13 sp, and the title (24 sp) and the small text (14.5 sp) are as they were; on a phone
  the sizes are as they were (18, 14, 11 and 12.5 sp). The older Tables D to F above are the record
  of Sprint 10, measured at 15 and 12 sp for the body and the label of a TV, and they stay as they
  are: the row of IBM Plex Sans in Table V is the same halo at the new sizes, so it is not Table E.
  At the old sizes the lowest case of Plex was 5.32:1 (the body of a TV), and at the new sizes it is
  5.16:1 (the label of a TV).
- `python -I design/accessibility/contrast.py halo <frames> --json <font.json> --fonts <manifest.json> --jobs 4` measures one font in about two to three minutes. The README of the tool says how to make the manifest and what the options do.
- The halo is the one that the app draws, stroke 3 dp, blur 2 dp and opacity 80 %, and the question
  is whether it keeps `ink` and `ink-2` at 4.5:1 for the label, the body, the small text and the
  title of a phone, and `ink` at 3:1 for the title of a TV, with 2 % to spare, over every
  background, as the section Halo has it.
- The sets are the owner's: IBM Plex Sans as the baseline and five more sans fonts, and three
  monospaced fonts, with IBM Plex Mono as the baseline of the monospaced ones. OpenDyslexic and
  OpenDyslexic Mono were measured at the Sprint 10 sizes and are out of the set (owner, 2026-10-21):
  they are not in this section.

### The fonts and their scale

| Font | Kind | Regular and SHA-256 (first 12) | Semibold and SHA-256 | Licence |
| --- | --- | --- | --- | --- |
| IBM Plex Sans 1.1.0 | sans | `ibm_plex_sans_regular.ttf` `975dcda37d80` | `ibm_plex_sans_semibold.ttf` `a20caf828602` | OFL 1.1 (in the repository) |
| Atkinson Hyperlegible Next 2.001 | sans | `AtkinsonHyperlegibleNext-Regular.ttf` `88ed5c31a715` | `AtkinsonHyperlegibleNext-SemiBold.ttf` `8e2fff779f35` | OFL 1.1 |
| Lexend 1.008 | sans | `Lexend-Regular.ttf` `e2082c28389e` | `Lexend-SemiBold.ttf` `204875848cc7` | OFL 1.1 |
| Andika 7.000 | sans | `Andika-Regular.ttf` `27484fdc98d0` | `Andika-SemiBold.ttf` `73c4df767769` | OFL 1.1 |
| B612 1.008 | sans | `B612-Regular.ttf` `139dce659100` | `B612-Bold.ttf` `91749541ac7b` | OFL 1.1 |
| Luciole | sans | `Luciole-Regular.ttf` `4c75513464f2` | `Luciole-Bold.ttf` `254e958919dd` | CC BY 4.0 |
| IBM Plex Mono 2.5.0 | mono | `ibm_plex_mono_regular.ttf` `7c6fbddca4b7` | `ibm_plex_mono_semibold.ttf` `f04d7c488ddf` | OFL 1.1 (in the repository) |
| Atkinson Hyperlegible Mono 2.001 | mono | `AtkinsonHyperlegibleMono-Regular.ttf` `2c9a7b9fabeb` | `AtkinsonHyperlegibleMono-SemiBold.ttf` `6dafae0d4cd6` | OFL 1.1 |
| B612 Mono 1.008 | mono | `B612Mono-Regular.ttf` `b98cb96cc8a6` | `B612Mono-Bold.ttf` `b467b1d19fda` | OFL 1.1 |

The semibold roles (the title and the label) use the SemiBold where the font has one, and the Bold
where it has not (B612, B612 Mono and Luciole), which is the face that Compose picks for a weight
of 600. None of the fonts is a variable font; the tool sets the weight of one with `fvar`, but no
number below depends on it.

The scale is the x-height of IBM Plex Sans (0.516 of the em) over that of the regular face, both as
drawn: the tool measures the height of the x at 1000 px, because the app draws the x. The
`sxHeight` of the OS/2 table is given as a cross-check. It agrees to the thousandth for six of the
nine fonts. The drawn x of Andika is 1.7 % under its table, and those of B612 and B612 Mono are
1.6 and 1.8 % over theirs, and their scales follow the x (1.034, 0.923 and 0.921, where the table
would give 1.016, 0.938 and 0.938). The scale is the same at any text size, so it does not change
with the sizes of Sprint 11.

| Font | Drawn x (em) | OS/2 sxHeight (em) | Drawn against table | Scale | Semibold face |
| --- | --- | --- | --- | --- | --- |
| IBM Plex Sans 1.1.0 | 0.516 | 0.516 | +0.0 % | 1.000 | SemiBold |
| Atkinson Hyperlegible Next 2.001 | 0.496 | 0.496 | +0.0 % | 1.040 | SemiBold |
| Lexend 1.008 | 0.525 | 0.525 | +0.0 % | 0.983 | SemiBold |
| Andika 7.000 | 0.499 | 0.508 | -1.7 % | 1.034 | SemiBold |
| B612 1.008 | 0.559 | 0.550 | +1.6 % | 0.923 | Bold |
| Luciole | 0.545 | 0.545 | +0.0 % | 0.947 | Bold |
| IBM Plex Mono 2.5.0 | 0.516 | 0.516 | +0.0 % | 1.000 | SemiBold |
| Atkinson Hyperlegible Mono 2.001 | 0.496 | 0.496 | +0.0 % | 1.040 | SemiBold |
| B612 Mono 1.008 | 0.560 | 0.550 | +1.8 % | 0.921 | Bold |

### The halo of the app in each font

Table V. The halo of the app (stroke 3 dp, blur 2 dp, opacity 80 %) in each font at the Sprint 11
sizes: the lowest ratio over every background, device and text style, as Table D has it for Plex:
`ink` and `ink-2` for small text (4.5:1) and `ink` for large text (the title of a TV, 3:1). Then the
lowest case, how far it is over its limit, whether the halo keeps every ratio with 2 % to spare, and
the smallest candidate of the grid that does for that font alone.

| Font | `ink`, small | `ink-2`, small | `ink`, large | Lowest case | Over its limit | Keeps | Smallest for the font |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IBM Plex Sans 1.1.0 | 7.27 | 5.16 | 7.26 | 5.16, `ink-2`, label, TV, Northern lights | 15 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| Atkinson Hyperlegible Next 2.001 | 7.44 | 5.28 | 7.61 | 5.28, `ink-2`, body, TV, Northern lights | 17 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| Lexend 1.008 | 7.27 | 5.16 | 7.18 | 5.16, `ink-2`, body, TV, Northern lights | 15 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| Andika 7.000 | 7.20 | 5.11 | 7.60 | 5.11, `ink-2`, label, TV, Northern lights | 14 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| B612 1.008 | 7.02 | 4.98 | 7.24 | 4.98, `ink-2`, body, TV, Northern lights | 11 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| Luciole | 7.42 | 5.27 | 7.54 | 5.27, `ink-2`, small, TV, Northern lights | 17 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| IBM Plex Mono 2.5.0 | 7.17 | 5.09 | 7.16 | 5.09, `ink-2`, label, TV, Northern lights | 13 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| Atkinson Hyperlegible Mono 2.001 | 7.15 | 5.07 | 7.44 | 5.07, `ink-2`, body, TV, Northern lights | 13 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |
| B612 Mono 1.008 | 7.08 | 5.02 | 7.37 | 5.02, `ink-2`, body, TV, Northern lights | 12 % | yes | stroke 3 dp, blur 2 dp, opacity 80 % |

### What it shows

- **The halo of the app still keeps the contrast in every font.** All 9 sets, 6 sans and 3
  monospaced, keep every ratio at stroke 3 dp, blur 2 dp and opacity 80 % with 2 % to spare, and so
  with none. The lowest case is 4.98:1, `ink-2` in the body of a TV over Northern lights in B612
  1.008, 11 % over its limit, and the highest of the lowest cases is 5.28:1 in Atkinson Hyperlegible
  Next 2.001. Every set has `ink-2` over Northern lights as its lowest case. The lowest `ink-2` of
  small text is 4.98 to 5.28:1 over the sets, `ink` for small text is 7.0:1 or more, and the title
  of a TV in `ink`, which needs 3:1, is 7.1:1 or more.
- **No smaller halo works for any of them.** The smallest candidate of the grid that keeps the
  ratios is the same as Plex's for each font, stroke 3 dp, blur 2 dp, opacity 80 %, so it is also
  the smallest that keeps every font at once. The halo of the app does not have to change for any
  font of the list, at the sizes of Sprint 10 or of Sprint 11.
- **The sizes of Sprint 11 move the lowest case by a few points, either way.** The label of a TV is
  13 sp and the body 16 sp, so the ring that the halo has to cover is not the same. The lowest case
  of Plex is 5.16:1 against 5.32:1 at the old sizes, 15 % against 18 % over the limit. Over the 9
  sets the margin over the limit moved by -6.7 to +1.5 points of that percentage (the lowest `ink-2`
  of a font is not always in the same text style, and the scales of Andika and B612 changed), and it
  is 10.7 % or more everywhere.
- **High contrast does not depend on the font or on the size.** Under it the dim of the page at 0.85
  gives 12.00:1 for `ink-2` and 14.46:1 for `ink` over every pixel of every background with no halo
  at all (Tables G and H), which is a property of the pixels and not of the glyphs, and a halo only
  raises them, so no font or size can bring the loose text under 7:1. The icons do not use a font.

### Not covered

- The Medium (500) of Atkinson, Lexend and Andika: the model sets the small text, which has the size
  of the rail items, in the regular face.
- Hinting and rasterising: as in the section Halo, the sample is drawn at four times its size and
  brought down by the mean of each block, as Android does not hint it. The FreeType 2.14.3 of Pillow
  12.2.0 draws every face; Android draws with Skia, so a small difference at the edge of a glyph is
  possible, which the 2 % to spare is for.
- The numbers are the model of the halo and not a device: the halo has not been seen with these
  fonts.

## Text spacing (Sprint 11)

Sprint 11 adds the Text spacing row, with the levels Normal, Wide and Wider, to the Accessibility
group (issue #49, [ADR 0058](../adr/0058-appearance-and-reading-options.md), decision 4). Wide
meets the minimums of WCAG 1.4.12. Wider goes beyond them for the spaces between words.

### Text spacing: the values

| Level | Letter spacing | Word spacing | Line height |
| --- | --- | --- | --- |
| Normal | the design | the design | the design |
| Wide | 0.12 em | 0.16 em | 1.5 |
| Wider | 0.18 em | 0.5 em | 1.5 |

Each value is a minimum, in em of the font size of the style. A style that has more of its own
keeps it, so the unit does not matter. The gap at a space is the letter spacing of the space plus
the word spacing of the level. The values are in `TextSpacing` (`theme/BorealisLook.kt`), and
`TextStyle.spaced` in `theme/BorealisType.kt` applies them.

### Text spacing: how the spacing is drawn

- The design-system `Text` (`components/Text.kt`) adds a span on each space of the text. The
  semantics text stays the original string, so TalkBack reads the words as before.
- A detekt `ForbiddenImport` (`apps/android/config/detekt/detekt.yml`) keeps Material's `Text`
  out of the code. Only `Text.kt` imports it.
- A text that a component builds itself takes the level too, through `BorealisType.spaced(style)`:
  the chips, the step numbers, the performance overlay, the rail version and the font picker
  preview.
- Typed text in a text field gets no word spacing. The labels of the field do.
- A non-breaking space gets no word spacing, and no app string uses one.

### Text spacing: word-to-letter gap ratios

The gap at a space, over the gap between two letters, by font and level. The values are from the
s11-text-spacing measurement; they were not run again for this document.

| Font | Normal | Wide | Wider |
| --- | --- | --- | --- |
| IBM Plex Sans | 2.42 | 2.81 | 3.65 |
| Atkinson Hyperlegible Next | 3.00 | 3.15 | 4.00 |
| Luciole | 2.97 | 3.12 | 3.88 |
| Lexend | 3.07 | 3.20 | 4.05 |
| Andika | 3.35 | 3.34 | 4.22 |
| B612 | 3.67 | 3.52 | 4.27 |
| System font | 2.85 | 3.08 | 3.95 |

### Text spacing: fit coverage

- Every font at Normal and at Wider, and IBM Plex Sans at Wide, at 130 % text size times the
  system font scale of 1.3 (1.69): 540 runs, no failure. Wider is drawn as Wider there, which a
  test of the theme keeps (`TextSpacingLimitThemeTest`).
- The 200 % system font size (2.0) is tested in every font at Normal and at Wider, and IBM Plex Sans
  at Wide: the Library, the components, Settings with Privacy and the Audit trail
  (`LargeTextFitTest`, `SettingsLargeTextFitTest`, `AuditLargeTextFitTest`). No failure. Wider is
  drawn as Wide at 2.0, so the runs of Wider are drawn at Wide, and the limit section below says
  why.
- Compact and Dense at 2.0, in IBM Plex Sans and Andika, at Normal, Wide and Wider, in the Library
  screens: 144 runs, no failure (a probe, not kept as a test).

### Text spacing: layout changes

A layout that does not fit at Wide or Wider is changed rather than cut:

- a value row stacks its value under the text when the longest word does not fit;
- a slider's value takes at most 40 % of its row;
- the host detail line wraps;
- the key captions are drawn with the spacing of Wide at Wider; their floor is 8 sp at Normal and
  8 sp over 1.12 at Wide and Wider, because the plain 8 sp is cut off at Wide at a system font scale
  of 2.0 (`KeyCaptionSizeTest`, `KeyCaptionScaleTest`, `KeyboardKeyCaptionSpacingTest`). Luciole at
  Normal is cut off at 2.0 whatever the floor, as it was before;
- Compact and Dense keep the columns of Comfortable at Wider (`CardDensityLayoutTest`).
- the Pairing screen of a TV wraps the line under the PIN at 280 dp, the width of the note about a
  restart, so that the steps keep about 290 dp for their words at a font scale of 2.0, where they
  had 149 dp (`PairingTvRoomTest`);
- the name in the rail footer wraps, inside the word if it must, instead of ending in an ellipsis
  (`RailFooterNarrowTest`);
- the time of an Audit entry takes 35 % of the row at most and wraps at its space, so that the title
  keeps its room on a narrow phone (`AuditEntryRoomTest`).

### Text spacing: the limit at a very large font

Wider is drawn as Wide from a font scale of 2.0, the largest that Android offers, and Wide and
Normal are never limited ([ADR 0058](../adr/0058-appearance-and-reading-options.md), decision 4).
The scale is the system font scale times the Text size, which is the scale that the text is drawn
at (`effectiveTextSpacing`, `theme/TextSpacingLimit.kt`). The look keeps what the user chose, so
Wider comes back when the scale falls, and the type, the design-system `Text`, the stream chrome and
the layouts that follow the level (the card columns) use the level that is drawn.

The 200 % fit tests were run in every font at Wide and at Wider, at 18 font scales from 1.15 to
2.0, with the Text size at its default. Each scale is 462 runs, 231 at each level. The table has
every scale and the runs that fail the fit check, which holds a line to 1 px past its box. The
measures are of 2026-10-22. The columns "before" are from before the three fixes of the layout
changes above and before the UAT layout, at the eight scales of the table that were run then (1.0
was run too and had no failure). The columns "after" are from the head with the fixes: all 18
scales, and the ten of 1.15, 1.3, 1.4, 1.5, 1.6, 1.69, 1.7, 1.8, 1.9 and 2.0 again on the head that
merged the UAT layout, with the same counts.

| Font scale | Wider before | Wide before | Wider after | Wide after |
| --- | --- | --- | --- | --- |
| 1.15 | 1 | 0 | 1 | 0 |
| 1.2 | not run | not run | 0 | 0 |
| 1.3 | 4 | 0 | 0 | 0 |
| 1.35 | not run | not run | 0 | 0 |
| 1.4 | 8 | 0 | 1 | 0 |
| 1.45 | not run | not run | 1 | 0 |
| 1.5 | 9 | 0 | 3 | 0 |
| 1.55 | not run | not run | 1 | 0 |
| 1.6 | 11 | 0 | 4 | 0 |
| 1.65 | not run | not run | 1 | 0 |
| 1.69 | not run | not run | 3 | 0 |
| 1.7 | 8 | 1 | 1 | 0 |
| 1.75 | not run | not run | 4 | 0 |
| 1.8 | 19 | 1 | 9 | 0 |
| 1.85 | not run | not run | 4 | 0 |
| 1.9 | not run | not run | 3 | 0 |
| 1.95 | not run | not run | 3 | 0 |
| 2.0 | 16 | 8 | 5 | 0 |

What the table says:

- **Before the fixes** the failures of Wider began at 1.15 and those of Wide at 1.7. Most were the
  Pairing screen of a TV in all seven fonts, from 1.3.
- **After the fixes** Wide fails in none of the runs. Wider fails in none that is more than
  rounding: every failure that is left runs 0.5 to 2.6 px past its box, in a text whose spaces have
  the spans of the word spacing, and none is cut or ends in an ellipsis. The "after" column of
  Wider adds up to 44 runs, and the most is 2.6 px.
- **The limit is at 2.0** because the 200 % tests run with the check of 1 px, and 5 runs of Wider
  fail it at 2.0. So the tests at 2.0 draw Wider as Wide. Below 2.0 Wider is drawn in full.

The dialog and the row of Settings, and what they say, are in
[UC-010](../use-cases/0010-adjust-accessibility.md), A8.

### Text spacing: known limitations

- Below 2.0, Wider is drawn in full, and 0 to 9 runs of the 231 at a scale run 0.5 to 2.6 px past
  their box (see the limit above). The fit tests find none cut or ending in an ellipsis. It has not
  been seen on a device.
- At a combined font scale above 2.0 (a system font of 2.0 with Largest text is 2.6), Normal text
  already fails the fit tests in 8 of the 84 runs of the Library (Apps with a long host name, on a
  TV and on a phone) and in 1 of the 70 runs of the components (the rows of the settings on a TV);
  Settings and the Audit trail pass. Wider is drawn as Wide there, and Wide is not limited: Wide
  above a combined 2.0 has not been measured, only Normal was, at 2.6. It is in
  [Sprint 17](../roadmap.md#sprint-17-general-fixes) of the roadmap.
- At Wide and Wider, the line ends of a wrapped text can draw up to one word spacing past their
  box: about 12 px in IBM Plex Sans at Wider, and up to 23 px at the largest TV text in the system
  font. The review found no text cut. The ink lands in the padding or in the gaps.
- A non-breaking space gets no word spacing, and no app string uses one.

### Card density

Card density (issue #51, [ADR 0058](../adr/0058-appearance-and-reading-options.md), decision 7)
adds one column to the hosts and apps in Compact and in Dense, and cuts the card padding in Dense.
The invariants hold in every density: the touch targets, the gaps between the cards, the gutter of
the screen and the TV focus scale do not change. With Text spacing at Wider the column is not added:
Compact and Dense keep the columns of Comfortable, because the words of a card need the room (host
names broke inside a word on a TV in the device round). Dense still takes its card padding. The
rule follows the level that is drawn, so at a font scale of 2.0, where Wider is drawn as Wide, the
column is added again. The host
cards of a row are as high as the tallest of them on every device, where a row has more than one
card.

Known limitations of the density:

- App names under a cover are one line with an ellipsis, as before Sprint 11. The fit tests cannot
  see them, because the name hides its own semantics (`CoverTile.kt`). At the largest text in
  Compact or Dense on a phone, only a few letters remain: "Des…" or "Stea…" on a 4-column phone.
- The Continue card breaks words by character at the largest text with Wider on a phone.

## The stream: glass, halo and focus (Sprint 15)

Sprint 15 puts the animated background and the glass into the stream
([ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md)). The stream
draws its chrome over a hardware video, so two things differ from the Library: the
glass behind the in-game menu is a live blurred copy of the video, which is any frame,
and the screens outside the game stand on the background of the user. This section
records what the settings of ADR 0057 do there, how the text keeps its contrast, and what
a D-pad and a screen reader find in the popups and the menu. The numbers of the
contrast are in the ADR (decision 10, and the measurements of 2026-10-29).

### What the settings do in the stream

The stream reads the look, the two stream options of Appearance (Menu glass in the
stream, and Background before and after the stream) and the background style once, when
it starts, so a change shows in the next stream. The battery saver is the exception: it
is followed live.

| Setting or device | The menu and its Quit popup | The screens outside the game and their dialogs | The error popup over the last frame |
| --- | --- | --- | --- |
| Menu glass on Solid | A solid panel. No copy of the video is made. | No effect. | The panel is solid. The frame is still blurred. |
| Background on Still or Off | No effect. | Still: one frame of the style, and no player. Off: the plain page, so the panels are solid and a dialog keeps its scrim. | No effect: the frame is behind the popup, not the background. |
| Transparency Off | A solid panel, no copy. | The background is drawn, the panel of the steps is solid, and a dialog keeps its scrim over it: nothing blurs or holds the background. | The frame shows sharp under the scrim. The panel is solid. |
| High contrast | As Transparency Off. | As Transparency Off. | As Transparency Off. |
| Reduce motion | A solid panel, no copy. | Every style is held still. A clip makes no player. | No change: the frame is still and was copied once. |
| Battery saver | A solid panel, no copy, followed live while the menu is open. | As Reduce motion, followed live. | No change. |
| API 28 to 30 | A solid panel, no copy (nothing blurs). | The panel of the steps is the Reduced tint on the sharp background. A dialog keeps its scrim and its solid panel. | The frame shows sharp under the scrim. The panel is solid. |

Where the host blurs (API 31 or later, Transparency Standard or Reduced, no High
contrast, and a background style that is not Off), the conflict dialog and the error
popup over the background blur the background alone, at 24 dp, hold it still, draw no
scrim and take the tint of the glass, with the tint floor of the background as in the
Library (ADR 0057, decision 4). The popup over the frozen frame keeps its scrim, also over
a blurred frame. The glass over the video, which is the menu and the popups over the frame,
takes the tint and the blur of the viewer's Transparency and is never raised: Standard is
Glass tint and Glass blur (40 % and 8 dp by default), and Reduced is 85 % and 48 dp. A clip that the stream's decoder would compete with shows its still while the
decoder is held (from the stage Preparing the display until the connection has stopped).

The listener of the battery saver is registered only where there can be a glass: the menu
glass on Blur, a stream view to copy and API 31 or later. The Solid option and API 28 to
30 register none (`MenuGlassRulesTest`, `MenuGlassApi30Test`).

### The halo and the casing over the video glass

The video is any frame, so the text on the glass does not stand on the colour behind it:
it stands on a halo. The lightest backdrop that a video can leave is a white frame under
the glass of 40 %. Over it ink is 2.1:1 and ink-2 1.5:1 without a halo.

- **The halo.** A halo of 3 dp, blurred by 2 dp, 100 % opaque, drawn in three passes (a
  stroke, a stroke of half the width and the glyphs filled), on every text and every icon
  of the menu and of the popups over the glass. The halo of a text on the background (the
  Halo section above) is 80 % and one pass: it leaves the danger ink of the Quit row at
  4.40:1 on a TV in the tool's model. One stroke also leaves a hole at the dot of an i or
  a bullet, 3.7:1 for the red of Quit; the second stroke and the filled glyphs cover it.
  `TextHaloTest` reads the ring in what is drawn, and `VideoGlassHaloTest` runs the tool's
  model over a white frame and a busy bright one.
- **The colours.** Ink, ink-2, accent-soft and danger-ink keep 4.5:1 on the ring. Ink-3
  and ink-4 are ink-2 on the glass. The label of a primary button, which has the accent fill,
  stands on its fill and has no halo.
- **Segmented controls.** They sit on a container in the `surface` colour, in the colours
  of the look and with no halo, so a label never stands on the video. The unselected label
  (ink-3) and the selected one (accent-soft on the accent tint) keep 4.5:1 on it.
- **The focus outline.** Over the lightest glass the accent-soft outline is 1.39:1 and the
  thicker one, in ink, 2.1:1, where WCAG 1.4.11 asks 3:1. Under the halo the outline has a
  casing: a stroke 1.5 dp wider on each side, in the `page` colour at the opacity of the
  halo. With it the outline keeps 3:1 (`FocusOverGlassTest`, `MenuGlassCasingTest`). With
  Thicker focus outline, 0.5 dp of the casing is cut by the scroll clip of the side panel
  at its edges; the ring and 1 dp of the casing stay whole.
- **Not covered.** The faint tail of the halo is cut at the 2 dp bleed of the layer that
  holds the text of the popup, which is cosmetic. The tests read pixels on Robolectric,
  not a device. The colour of an HDR copy on the S22 was not judged, and nobody has looked
  by eye at the frozen frame behind the error popup.
- **Text on a panel.** The steps of the Starting screen, the conflict dialog and the error
  popup over the background stand on a panel, with the text colours of the glass and no
  halo. The title, the host name and the note of the Starting screen, and the texts of
  Quitting and Disconnecting, stand on the background and have the halo of the Library. The
  checks of the Accessibility Test Framework run these screens on the plain page.

### The popups: focus and screen reader

The conflict dialog, the error popup and the Quit popup are one component of the chrome
layer, `StreamDialog`, so they follow the same rules (`StreamDialogTest`,
`StreamDialogBackTest`).

- **Focus.** The first button has the initial focus: Cancel in the conflict dialog and the
  Quit popup, Try again in the error popup. The focus stays in the popup in every
  direction: Left and Right move between the buttons and stop at the last, and Up and Down
  never leave the popup. A phone opens a popup in touch mode, where a button cannot
  take the focus, so the first key of a D-pad, a pad or a keyboard puts it on the first
  button, and not on what is behind.
- **Back and B.** Back, the Back of the system and the gamepad B button call the dismissal
  (Cancel, or Close for the error popup) and stop there, so they reach neither the menu
  behind nor the host. A tap on the scrim is Cancel, except on the error popup, which has
  no Cancel and ignores a tap outside it.
- **What is said.** The popup is announced by its title as a pane title. The title is the
  only heading. The title and the body, with the warning of the conflict dialog, are one
  polite live region, so a screen reader reads them when the popup opens. The raw code and
  the port hint of the error popup are under it, outside that region, and a screen reader
  reaches them as it moves on. Three buttons or more stack on a phone.
- **What is out of reach.** While the Quit popup is open the menu behind it stays drawn
  but has no focus and no semantics: neither the D-pad nor a screen reader enters it.
- **Quitting and Disconnecting.** Their text is a polite live region. The loader stands
  still with Reduce motion. Back leaves Quitting and does nothing during Disconnecting.

### The menu has no lateral movement

The owner decided on 2026-10-28 that the focus never moves sideways in the in-game menu
(`InGameMenuFocusTest`, `UsbChromeMenuFocusTest`).

- Left and Right do nothing on every row and chip, from a D-pad, a keyboard and the stick of
  a pad on the USB driver. The chrome takes the keys, so none reaches the game or the PC.
- Up and Down walk every focusable of the menu in the order it is drawn, and stop at the
  first and the last. The chips of Send keys come in reading order, over their lines.
- OK, Enter or A on an option row selects the next option and wraps to the first. The
  segments of the row are not focus stops. A tap selects a segment, and so does a screen
  reader, which hears "Change `<row>`" on the row and the group label after the name of a
  segment.

## WCAG 2.2 checklist

The criteria that Sprint 10 touched, and how each is met. Level AA is the target of every screen,
and AAA (1.4.6) for the text of High contrast.

| Criterion | Level | How it is met |
| --- | --- | --- |
| 1.3.1 Info and Relationships | A | Headings, pane titles, one sentence for a host card and a pairing step, a role and an Expanded or Collapsed state for the sections of the phone Settings (`*TalkBackTest`). |
| 1.4.1 Use of Color | A | A shape for each status of the dot, and a text beside it. The palettes keep the colours apart for dichromats (`StatusDotTest`, `ColourVisionPaletteTest`), and the accent from the status colours (`AccentPaletteVariantsTest`). The Online shadow of Sprint 11 is a contrast aid, not a status cue. The Running badge of a cover is the dot and its label when the label fits on one line. On a phone with 4 columns the label never fits, so the badge is the dot alone: its shape and presence, the tile's TalkBack state "Running" and the Apps header "`<app>` running" carry the status. |
| 1.4.3 Contrast (Minimum) | AA | Text on the opaque surfaces (Table B), on glass through the tint floors and the adapted secondary ink (Table C and the floors), on the background through the halo (Tables D to F), in the Add host field (Tables L and M), and over the video glass of the stream through its halo (the stream section). |
| 1.4.4 Resize Text | AA | Text size 100, 115 or 130 % times the system scale, and tests at a font scale of 2.0 (`LargeTextFitTest`). |
| 1.4.6 Contrast (Enhanced) | AAA | High contrast: 7:1 or more for the text roles on every fill, and for the loose text over every background (Tables G and H). |
| 1.4.12 Text Spacing | AA | The Text spacing row: Wide sets 0.12 em of letter spacing, 0.16 em of word spacing and a line height of 1.5, the minimums of the criterion, and Wider sets 0.5 em of word spacing. The spaces are drawn apart in every font (`WordGapTest`, `TextWordSpacingTest`), and the layouts that do not fit at Wide or Wider are changed (`TextFitTest`). The 200 % system font size is tested at Normal, Wide and Wider, and at 2.0 Wider is drawn as Wide, with a dialog and a line in the row that say so (see text spacing: the limit at a very large font). |
| 1.4.11 Non-text Contrast | AA | The offline ring at 3.25:1 or more, the icons on the background through the halo (Tables I to K), the outlines of the text field (Tables L and M), the opaque borders of High contrast, and the Online dot with its shadow: 5.68:1 over the brightest host-card glass (3.79:1 without it, today's palette). The Apps title dot on the animated background is not measured (a known limitation, see the status dot). |
| 2.1.1 Keyboard | A | The D-pad and a gamepad reach every control; OK on a Settings group enters its pane, and the licence text and the Audit detail scroll with the D-pad. |
| 2.2.2 Pause, Stop, Hide | A | The animated background is the only decorative moving content; it stops with Reduce motion On, the system's Remove animations or the battery saver, and the background Off is the plain page. In the stream the same holds outside the game, the glass of the menu makes no copy of the video with Reduce motion or the battery saver, and the loader of Quitting and Disconnecting stands still with Reduce motion. The other progress indicators are not covered yet (roadmap, Sprint 17). |
| 2.4.3 Focus Order | A | The traversal order of every screen is tested (`readingOrder`), and the focus goes back to the card that had it after Back from Apps. |
| 2.4.6 Headings and Labels | AA | Headings on every screen title and section label. |
| 2.4.7 Focus Visible | AA | The Borealis focus outline, 3 dp in accent-soft, and 5 dp in `ink` with Thicker focus outline and High contrast, with a casing of the `page` colour over the video glass of the stream. |
| 2.5.3 Label in Name | A | The accessible name of a control is its visible label (the Add host button, an accepted duplicate name). |
| 2.5.8 Target Size (Minimum) | AA | 48 dp targets on a phone and a handheld (`TouchTargetTest`). A TV has no touch screen. |
| 3.3.1 Error Identification | A | The error under a text field is in text, in `danger-ink`, inside the glass of the field, and is announced when it appears. |
| 4.1.2 Name, Role, Value | A | The ATF checks every main screen for names, roles and duplicate names; the groups of Settings are tabs, and the segments have the label of their group. |
| 4.1.3 Status Messages | AA | Polite live regions for pairing outcomes, Add host errors, the notices of the snackbars, the Quitting and Disconnecting texts and the title and body of a popup of the stream. |

Not assessed here: 1.4.10 Reflow and 2.4.11 Focus Not Obscured. TalkBack on a
device is a step of the runbook, and the halo and the tint-only glass have not been seen on API 28
to 30.
