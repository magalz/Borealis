# Accessibility: contrast over the background

Sprint 10 aims at WCAG 2.2 level AA on every screen, and at 7:1 (AAA) for text
when high contrast is on. This page records how the contrast of text over the
animated background is measured, and the baseline measured before any fix. The
tool is in [`design/accessibility/`](../../design/accessibility/README.md); its
README says how to run it again.

- Date: 2026-10-20.
- Revision measured: `fec048bb`. The app code is that of `109e7dff`, the
  Sprint 9 merge: the revision adds only the tool and a test that writes frames.

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
    colour on it at an alpha. The panel is not in the app yet. The grid is an
    alpha of 0.55, 0.70 or 0.85 and a blur radius of 16, 32 or 48 dp.
- **Blur.** A Gaussian of sigma `0.57735 * radius_px + 0.5`. This is how Skia
  turns a radius into a sigma, and Android's `RenderEffect.createBlurEffect`
  uses it. The radius in dp becomes pixels with the density of the frame (2.0 on
  the TV, 3.0 on the phone). The pixels are blurred and mixed as sRGB values, as
  the platform does, and the edge of the frame repeats. A real device may differ
  a little from this model.

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

For each of the nine candidates, the worst case of each token over every style
and every frame, and the style and device it is in. The last column is the lowest
of the five tokens. A ratio just short of a limit is printed with three places,
so 4.496 is not read as 4.50. The first table is over both devices; the two that
follow are for the TV and for the phone alone.

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.55 | 16 | 9.09 (Northern lights, phone) | 6.45 (Northern lights, phone) | 3.40 (Northern lights, phone) | 2.75 (Northern lights, phone) | 5.93 (Northern lights, phone) | 2.75 |
| 0.55 | 32 | 10.01 (Northern lights, phone) | 7.10 (Northern lights, phone) | 3.75 (Northern lights, phone) | 3.03 (Northern lights, phone) | 6.54 (Northern lights, phone) | 3.03 |
| 0.55 | 48 | 10.59 (Flowing light, TV) | 7.51 (Flowing light, TV) | 3.97 (Flowing light, TV) | 3.20 (Flowing light, TV) | 6.91 (Flowing light, TV) | 3.20 |
| 0.70 | 16 | 10.99 (Northern lights, phone) | 7.80 (Northern lights, phone) | 4.12 (Northern lights, phone) | 3.32 (Northern lights, phone) | 7.18 (Northern lights, phone) | 3.32 |
| 0.70 | 32 | 11.67 (Northern lights, phone) | 8.28 (Northern lights, phone) | 4.37 (Northern lights, phone) | 3.53 (Northern lights, phone) | 7.62 (Northern lights, phone) | 3.53 |
| 0.70 | 48 | 12.06 (Flowing light, TV) | 8.56 (Flowing light, TV) | 4.52 (Flowing light, TV) | 3.65 (Flowing light, TV) | 7.88 (Flowing light, TV) | 3.65 |
| 0.85 | 16 | 13.02 (Northern lights, phone) | 9.24 (Northern lights, phone) | 4.88 (Northern lights, phone) | 3.93 (Northern lights, phone) | 8.50 (Northern lights, phone) | 3.93 |
| 0.85 | 32 | 13.36 (Northern lights, phone) | 9.48 (Northern lights, phone) | 5.01 (Northern lights, phone) | 4.04 (Northern lights, phone) | 8.72 (Northern lights, phone) | 4.04 |
| 0.85 | 48 | 13.55 (Flowing light, TV) | 9.61 (Flowing light, TV) | 5.07 (Flowing light, TV) | 4.09 (Flowing light, TV) | 8.84 (Flowing light, TV) | 4.09 |

The TV frames only:

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.55 | 16 | 9.56 (Northern lights, TV) | 6.79 (Northern lights, TV) | 3.58 (Northern lights, TV) | 2.89 (Northern lights, TV) | 6.24 (Northern lights, TV) | 2.89 |
| 0.55 | 32 | 10.50 (Flowing light, TV) | 7.45 (Flowing light, TV) | 3.93 (Flowing light, TV) | 3.17 (Flowing light, TV) | 6.85 (Flowing light, TV) | 3.17 |
| 0.55 | 48 | 10.59 (Flowing light, TV) | 7.51 (Flowing light, TV) | 3.97 (Flowing light, TV) | 3.20 (Flowing light, TV) | 6.91 (Flowing light, TV) | 3.20 |
| 0.70 | 16 | 11.34 (Northern lights, TV) | 8.05 (Northern lights, TV) | 4.25 (Northern lights, TV) | 3.43 (Northern lights, TV) | 7.41 (Northern lights, TV) | 3.43 |
| 0.70 | 32 | 12.00 (Flowing light, TV) | 8.51 (Flowing light, TV) | 4.496 (Flowing light, TV) | 3.63 (Flowing light, TV) | 7.84 (Flowing light, TV) | 3.63 |
| 0.70 | 48 | 12.06 (Flowing light, TV) | 8.56 (Flowing light, TV) | 4.52 (Flowing light, TV) | 3.65 (Flowing light, TV) | 7.88 (Flowing light, TV) | 3.65 |
| 0.85 | 16 | 13.20 (Northern lights, TV) | 9.36 (Northern lights, TV) | 4.94 (Northern lights, TV) | 3.99 (Northern lights, TV) | 8.62 (Northern lights, TV) | 3.99 |
| 0.85 | 32 | 13.52 (Flowing light, TV) | 9.59 (Flowing light, TV) | 5.06 (Flowing light, TV) | 4.08 (Flowing light, TV) | 8.82 (Flowing light, TV) | 4.08 |
| 0.85 | 48 | 13.55 (Flowing light, TV) | 9.61 (Flowing light, TV) | 5.07 (Flowing light, TV) | 4.09 (Flowing light, TV) | 8.84 (Flowing light, TV) | 4.09 |

The phone frames only:

| Alpha | Blur dp | `ink` | `ink-2` | `ink-3` | `ink-4` | `accent-soft` | Lowest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.55 | 16 | 9.09 (Northern lights, phone) | 6.45 (Northern lights, phone) | 3.40 (Northern lights, phone) | 2.75 (Northern lights, phone) | 5.93 (Northern lights, phone) | 2.75 |
| 0.55 | 32 | 10.01 (Northern lights, phone) | 7.10 (Northern lights, phone) | 3.75 (Northern lights, phone) | 3.03 (Northern lights, phone) | 6.54 (Northern lights, phone) | 3.03 |
| 0.55 | 48 | 10.65 (Flowing light, phone) | 7.56 (Flowing light, phone) | 3.99 (Flowing light, phone) | 3.22 (Flowing light, phone) | 6.95 (Flowing light, phone) | 3.22 |
| 0.70 | 16 | 10.99 (Northern lights, phone) | 7.80 (Northern lights, phone) | 4.12 (Northern lights, phone) | 3.32 (Northern lights, phone) | 7.18 (Northern lights, phone) | 3.32 |
| 0.70 | 32 | 11.67 (Northern lights, phone) | 8.28 (Northern lights, phone) | 4.37 (Northern lights, phone) | 3.53 (Northern lights, phone) | 7.62 (Northern lights, phone) | 3.53 |
| 0.70 | 48 | 12.11 (Flowing light, phone) | 8.59 (Flowing light, phone) | 4.54 (Flowing light, phone) | 3.66 (Flowing light, phone) | 7.90 (Flowing light, phone) | 3.66 |
| 0.85 | 16 | 13.02 (Northern lights, phone) | 9.24 (Northern lights, phone) | 4.88 (Northern lights, phone) | 3.93 (Northern lights, phone) | 8.50 (Northern lights, phone) | 3.93 |
| 0.85 | 32 | 13.36 (Northern lights, phone) | 9.48 (Northern lights, phone) | 5.01 (Northern lights, phone) | 4.04 (Northern lights, phone) | 8.72 (Northern lights, phone) | 4.04 |
| 0.85 | 48 | 13.57 (Flowing light, phone) | 9.63 (Flowing light, phone) | 5.08 (Flowing light, phone) | 4.10 (Flowing light, phone) | 8.86 (Flowing light, phone) | 4.10 |

The video clips are drawn on a `SurfaceView` today, which an in-app blur cannot
sample, so glass over a clip needs a `TextureView`. The blur spike measured this
path on the TV, and it works there.

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
    over Flowing light on the TV (4.10 on the phone). `ink-4` is 4.51 on an
    opaque `surface`, so over a frame brighter than `surface` it is below 4.5:1
    at every alpha under 1.
  - Without `ink-4`, the candidates that keep the other four at 4.5:1 are an
    alpha of 0.70 with a blur of 48 dp (`ink-3` 4.52) and every candidate at
    0.85. `ink`, `ink-2` and `accent-soft` keep 4.5:1 in all nine.
  - The alpha matters most: at 32 dp the lowest `ink-3` is 3.75, 4.37 and 5.01
    at 0.55, 0.70 and 0.85. A larger blur helps too, most over the small bright
    details of Northern lights, and more on the phone, which shows the clip
    larger: at 0.70 the lowest `ink-3` is 4.12 at 16 dp, 4.37 at 32 dp and 4.52
    at 48 dp.
