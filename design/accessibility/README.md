# Contrast tool

Measures the contrast of text over the animated backgrounds, and over the glass panels drawn on
them, as WCAG 2.x defines it. `docs/ux/accessibility.md` has the method and the baseline numbers.
Sprint 10 uses it to choose the glass look of a panel and the text colours on it, and runs it again
after the fixes. Sprint 11 uses it to measure the halo of the text in each candidate font, and the
candidates of the accent colour (`accents.json`).

## What it needs

- Python 3.11 or later with `numpy` and `Pillow`. CI runs Python 3.14 with numpy 2.4.6 and Pillow
  12.2.0, which are the versions it was tried with (`.github/workflows/ci.yml`). Nothing else: the
  tests are `unittest`.
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

`measure` and `halo` read the colours (and `halo` the text sizes) from `design/tokens.json`;
`--tokens <file>` names another token file for either.

Run the tests from the repository root. The "Design tools" job of CI runs them too, with
`python -I -m unittest discover -s design/accessibility -v`, when anything under `design/` changes:

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

The file also holds `clipSha256`, the SHA-256 of each clip in `designsystem/src/main/res/raw` as it
was when the file was written. `GlassContrastTableClipsTest` in `:borealis:designsystem` hashes the
clips again and compares, so a clip that is encoded again fails the test until the frames are taken
again and the table is written again. The two styles that are drawn in code have no clip, and nothing
detects a change to them: see [the backgrounds](../backgrounds/README.md#what-the-glass-table-depends-on).

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
is taken at the pixels that are not in a glyph and are within 2 px of one. The grid is a stroke of
0 to 3 dp, a blur of 2, 4 or 8 dp and an opacity of 60, 80 or 100 %, and no halo.

The worst case is over every place of the sample on the frame, a pixel apart. It is the lowest ratio
over every colour of the frame with every alpha of the ring, which takes a few seconds for a set of
frames, and with no halo it is Table A. p1 and p5 are over 6 x 4 places, for information. The whole
run, 12 sets of 24 frames, takes about two minutes with `--jobs 4`, as in the command above. The
command prints Tables D, E and F of `docs/ux/accessibility.md` and the smallest candidate that keeps
4.5:1 (3:1 for the title of a TV), with 2% to spare. `--texts body title` measures only some of the
styles. The JSON has the worst case, p1, p5 and the worst frame of every text style, token and
candidate. `BackdropHalo` in `:borealis:designsystem` has the choice, and a test keeps it equal to
the line "The choice: ..." of the notes.

With `--high-contrast --halo 3 2 80` the frames are put under the veil of high contrast (the `page`
colour at the dim of the tokens, 0.85) and `ink` and `ink-2` are those of high contrast. It checks
the halo that is named, and not the grid, against 7:1 for every size of text, and prints Tables G
and H and whether every text keeps it.

With `--icons --halo STROKE_DP BLUR_DP OPACITY_PERCENT` it measures the icons that stand on the
background instead of the text: the back arrow, the plus, the gear and the eye of `BorealisIcons.kt`.
The tool reads the paths from that file, strokes them as wide as the app does (2 units on the grid of
24, with round caps and joins) and draws them at 24 dp, 48 px on a TV and 72 px on a phone. The mask
is the icon, and the rest is the method above: the same ring, grid and worst case over every place.
An icon is a non-text indicator that needs 3:1 (WCAG 1.4.11), also under high contrast. The halo
that is named is the one to check, as it is for high contrast. The command prints Tables I and J
and the smallest candidate that keeps 3:1 for the icons alone; `--high-contrast` as well puts the
frames under the veil first. It takes under a minute for 12 sets of 24 frames with 4 jobs.
`--icons` and `--texts` are not asked together.

### The halo in another font

The text is set in IBM Plex Sans unless a font is named, and then the tables are those of
`docs/ux/accessibility.md`, to the row. A font set is the regular face (the body and the small text)
and the semibold face (the title and the label), as two options or as a small JSON manifest:

```sh
python -I design/accessibility/contrast.py halo <frames> --json <halo.json> --jobs 4 \
  --font <regular.ttf> --font-semibold <semibold.ttf>
python -I design/accessibility/contrast.py halo <frames> --json <halo.json> --jobs 4 --fonts <font.json>
```

```json
{ "name": "Atkinson Hyperlegible Next", "regular": "AtkinsonHyperlegibleNext-Regular.ttf",
  "semibold": "AtkinsonHyperlegibleNext-SemiBold.ttf" }
```

The files of a manifest are by the manifest, or by absolute path, and `name` is optional (the stem of
the regular file). Pass the files by path, and do not copy a font into the repository for this.

- **A font with no SemiBold** (B612, Luciole) takes its Bold for the semibold face, which is the
  face that Compose picks for a weight of 600. A font with a Regular only takes it for both:
  Compose would make a bold of it, a little heavier than the model.
- **A variable font** is given as the same file twice. The tool reads its `fvar` table and sets the
  `wght` axis to 400 and 600 with Pillow's variation API, and every other axis to its default. A
  font with no weight axis, one that does not have the weight, and a FreeType older than 2.9.1 are
  reported, and nothing is measured at the wrong weight.
- **The x-height scale.** Fonts differ in how tall their lowercase is, so the app draws each at a
  size that sets its x-height to that of IBM Plex Sans at the same size. The scale is Plex's x-height
  over the font's: `sxHeight` of the OS/2 table over the units per em, or the height of an x as
  drawn when the table has none, or has a value that no x has (under 0.3 or over 0.8 of the em: the
  `sxHeight` of OpenDyslexic Mono is the em). The text is measured at the size of the tokens times
  that scale, the ring and the halo follow it, and `meta.fonts.scale` has it. The limits of the
  styles, 3:1 for the title of a TV and 4.5:1 for the rest, are those of the token sizes: a scale
  does not turn a small text into a large one. The scale is the one of the regular face, for both.
- **A monospaced companion** is a font set like any other: pass its Regular and its SemiBold (or
  Bold). The sample text is the same, "Living room PC, 4 apps".
- **The tables of another font** begin with a line "Font: ..." with the font, its files and its
  scale, and they say "The smallest candidate for" and the name of the font where the tables of
  Plex say "The choice:". `docs/ux/accessibility.md` has the line "The choice: ..." once, for the font of
  the app, and `NotesChoice` in the tests of `:borealis:designsystem` reads it: never paste that
  sentence for another font.

### Measuring the candidate fonts (Sprint 11, issue #48)

The candidate fonts are not in the repository: they are downloaded to a folder of their own, with
the licence and the checksum of each file. To measure them again:

1. Make a manifest for each font (the sans fonts, and the monospaced companion of each that has one),
   with the Regular and the SemiBold, or the Bold where there is no SemiBold.
2. Take the 288 frames as above (`extract` for the clips and the `BackgroundFrameExport` test for the
   two drawn styles, on the revision of the backgrounds that you mean).
3. Run `halo <frames> --json <font.json> --fonts <manifest.json> --jobs 4` for each manifest: about
   three minutes each.
4. The tool prints Tables D, E and F of the font, the scale and the smallest candidate that keeps
   the ratios (or says that none of the grid does). The JSON has `meta.fonts` and the rows of every
   candidate of the grid, so the verdict of the halo of the app is at
   `(stroke 3, blur 2, opacity 80 %)` in it.
5. Put the verdicts in "Halo per font (Sprint 11)" of `docs/ux/accessibility.md`.

## Accent candidates

Sprint 11 lets the viewer choose the accent colour (issue #50). `accents.json` is a set of
candidates, each a full set of the accent roles of `design/tokens.json`, and

```sh
python -I design/accessibility/contrast.py accents <frames> [--json <accents.json>] [--jobs 4]
```

measures each over the frames and prints the tables N to U of `docs/ux/accessibility.md`.
`--accents <file>` names another set of candidates and `--tokens <file>` another token file (the
surfaces, the ink and the status colours come from it). Without `--json` nothing is written. It
takes about nine minutes for the 288 frames with 4 jobs: each frame is blurred twice and each of
seven glass panels is laid under each candidate's tint.

`accents.json` holds `candidates`, each with:

- `label` and `source`, the colour of the icon that the set starts from;
- `color`: `accent`, `accent-soft`, `accent-strong`, `on-accent` (all `#RRGGBB`), `accent-tint` and
  `accent-line` (`#RRGGBB` or `#RRGGBBAA`, drawn with an alpha);
- `high-contrast`: the opaque `accent`, `accent-soft` and `accent-line` that high contrast puts in
  their place;
- `notes`: for each value (`accent`, ..., `high-contrast.accent-line`) how it was derived. The file
  is refused when a value has none;
- `known_fails`: the checks that need no frame that the set fails, each with why. A test keeps it
  equal to what the tool says, so a value that is changed has to be reflected here.

What is checked, for each candidate (a limit is met at or over it, with no margin):

- **On the surfaces.** `on-accent` on `accent` (4.5:1), and `accent` and `accent-soft` as text on
  `page`, `surface` and `surface-raised`, and on the tint of a selected row over `surface` and over
  `surface-raised` (4.5:1). The tint is laid over the surface in whole 8 bit values, as Compose
  draws it. `accent-strong` on the three surfaces is given for information: it carries no text.
- **Over the glass.** The brightest pixel behind the text of the glass of each style, over every
  frame of both devices, at the default of Settings (a tint of 40 % raised to the floor of the style
  at 8 dp) and at Reduced (85 %, 48 dp). The floor is worked out with the `accent-soft` of the
  candidate, as it is one of the colours that the floor keeps at 4.5:1. `accent` and `accent-soft`
  are read there as text (4.5:1) and as an outline (3:1), and the selected label, `accent` over its
  own tint laid on that glass, as text. The backgrounds are those that are drawn today, with their
  fixed violet: Sprint 11 stops the drawn backgrounds from taking the accent, so the glass table of
  the app (`GlassContrastTable.kt`) stays valid.
- **High contrast.** The opaque `accent` and `accent-soft` keep 7:1 and `accent-line` 3:1 over
  every fill that text lies on: the four surfaces, the tint of a selected row on `surface` and on
  `surface-raised`, and the hover on `surface-raised` (the fills of `HighContrastColorsTest`), and
  `on-accent` keeps 7:1 on the high contrast `accent`.
- **Distance from the status colours.** The least CIEDE2000 distance of `accent` from the four
  status colours (success, warning, danger, offline) of each of the three palettes, as normal
  vision and each dichromacy sees them (the Machado matrices of `ColourMeasures.kt`). 20 or more is
  distinct, as in `ColourVisionPaletteTest`. It is held where a palette is for the vision: today's
  colours for normal vision, the red-green palette for normal vision, protanopia and deuteranopia,
  the blue-yellow palette for normal vision and tritanopia. The other rows are for information. A
  neutral accent (CIELAB chroma under 10: white) is also held to 20 from `ink` and `ink-2`.

The result has `meta`, `sources`, `backdrops` (the brightest pixel of each glass and tint, by style
and device) and `candidates`, with every check as a row: `id`, `ratio` (or `least`), `limit` and,
where it applies, where it is lowest. `fails` lists the ids of the checks that are short of their
limit. The tables are made from that and not from the frames: the function `accent_tables` prints
them from a saved result.

## The JSON

`meta` has the date, the revision, the colours and the grid. `sources` lists the folders read.
The halo's JSON has the same `meta` and `sources`, and rows in `halo`; the icons are rows of
`halo` too, with the name of the icon in `text`, and `meta.icons` says which they are. `meta.fonts`
has the font set that the text was set in (its name, the file and the weight of each face, and the
scale), and `meta.texts` the weight and the file of each text style.
`background` (a) and `glass` (c) have a row for each style, device, token (and alpha and `blur_dp`):
`min`, `p1`, `p5`, `worst_frame` and `per_frame_min`. `surfaces` (b) has a `ratio` for each
surface and token.
