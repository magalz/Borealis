# Accessibility: contrast over the background

Sprint 10 aims at WCAG 2.2 level AA on every screen, and at 7:1 (AAA) for text
when high contrast is on. This page records how the contrast of text over the
animated background is measured, the baseline measured before any fix, and how
the table that the glass of the app reads comes out of that measurement. The
tool is in [`design/accessibility/`](../../design/accessibility/README.md); its
README says how to run it again. The decisions that follow from the numbers are
in [ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md).

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
