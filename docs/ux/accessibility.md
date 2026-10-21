# Accessibility: contrast over the background

Sprint 10 aims at WCAG 2.2 level AA on every screen, and at 7:1 (AAA) for text
when high contrast is on. This page records how the contrast of text over the
animated background is measured, the baseline measured before any fix, how the
table that the glass of the app reads comes out of that measurement, and the halo
of the text that stands on the background. The tool is in
[`design/accessibility/`](../../design/accessibility/README.md); its README says
how to run it again. The decisions that follow from the numbers are in
[ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md).

- Date: 2026-10-20.
- Revision measured: `fedc4ce0`. It widens the grid of the glass candidates of
  `fec048bb`, the first measurement. The frames are those of that first
  measurement, taken with the app code of `109e7dff`, the Sprint 9 merge: the
  drawn backgrounds and the clips have not changed since. So Tables A and B are
  as they were, and Table C is new.

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

With no halo the lowest ratios are those of Table A: `ink` is 1.56 and `ink-2` is 1.11 over
Northern lights. The title of a TV needs 3:1 and keeps 7.26 over Northern lights, its worst
background. The worst small text is `ink-2` at 5.32, for the body of a TV over Northern lights, 16%
above 4.59, which is 4.5 with 2% to spare. No candidate with a stroke of 2 dp keeps the ratios: the
best of them, a blur of 2 dp at 100 %, has `ink-2` at 4.24. A stroke of 3 dp keeps them at a blur
of 2 dp from 80 %, and a stroke of 3 dp with a blur of 2 dp at 80 % is the smallest, and the one
that the app draws. Its lowest p1 and p5 are 8.32 and 9.33 for `ink-2` and 11.73 and 13.15 for
`ink`: the pixels that come near the lowest ratios are very few.

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
  host, as in the Stream, there is no background and no halo.
- **The ink.** `BackdropInk` says what the text was: `Ink3` and `Ink4` are `ink-2` where there is
  a halo and the tokens, as they were, where there is not. `Ink` and `Ink2` are the tokens.
