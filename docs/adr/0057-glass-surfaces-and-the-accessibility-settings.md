---
status: accepted
date: 2026-10-20
decision-makers: João Pedro Magalhães
---

# 0057 Add glass surfaces and the Accessibility settings

## Context and Problem Statement

The owner asked on 2026-10-19 for panels and cards with a transparent background and a heavy
blur, so that the animated background of [0056](0056-add-an-animated-background-and-the-appearance-group.md)
stays visible behind them. Text on that background must still meet WCAG. The Settings rows,
which put text straight on the background, already fail: the ink-3 and ink-4 roles reach about
1:1 at the worst pixel of the clips. How can panels be transparent and blurred, keep the text
readable, and run on a TV at a usable frame rate?

## Decision Drivers

* The target is WCAG 2.2 level AA on every screen. With high contrast on, text reaches 7:1 (AAA).
* Text contrast is measured as the worst case of each text token over real frames of the
  background, with the tool in `design/accessibility/` and its method in
  [docs/ux/accessibility.md](../ux/accessibility.md).
* The background must stay visible, so the glass must show it.
* The Google TV Streamer must keep a usable frame rate on Hosts and Settings.
* Few dependencies, and no blur that the device cannot draw.
* Users who need less motion, less transparency or more contrast must be able to get it.
* A stream owns the decoder, the CPU and the screen, so the stream must not pay for the glass.

## Considered Options

* Haze 2.0.1, a third-party blur library.
* One blur per panel.
* One shared blur per frame, at a quarter of the resolution, which each panel samples.
* Tint only, with no blur.

For dialogs and sheets:

* Blur between windows, which the platform would draw behind the dialog.
* Blur the app's own window while a dialog is open.

For the TV, when the blur is drawn:

* Accept about 27 frames a second.
* Run the drawn background at 24 frames a second.
* Blur at 15 frames a second.
* Use tint only on the TV.

## Decision Outcome

Chosen option: "One shared blur per frame, at a quarter of the resolution, which each panel samples",
because it keeps the Settings screen within the frame budget on the TV, adds no dependency, and
lets the panels show the background at the cost of one blur.

Decision details:

1. **Three levels of transparency.** The levels are Standard, Reduced and Off.
   * **Standard:** glass tint 40 %, blur 8 dp.
   * **Reduced:** glass tint 85 %, blur 48 dp.
   * **Off:** the panels are solid, as before.
2. **Where the settings are.**
   * The Accessibility group has a **Transparency** setting with the three levels.
   * Appearance has **Glass tint** (30, 40, 50, 60, 70 or 85 %) and **Glass blur** (8, 16, 24, 32 or 48 dp).
     They set the Standard level.
   * Transparency Reduced or Off, and high contrast, override the Appearance values.
3. **Text on glass.**
   * The secondary text on glass (the ink-3 and ink-4 roles) takes, for each background, the colour
     nearest to ink-3 that keeps 4.5:1 with a 2 % margin. The worst backdrop comes from a table
     that the tool generates and that is committed.
   * At 40 % tint and 8 dp blur, the lightest secondary-text colours that keep 4.5:1 are:
     Aurora curtains `#8D9298` (close to ink-3 `#8B9096`), Soft glow and Starfield (ink-3 unchanged),
     Light mist `#969BA0`, and Flowing light `#A8ACB1`.
   * Northern lights fails at 40 %: ink-2 (4.24:1) and accent-soft (3.90:1) also fail. At 70 %
     tint the secondary text is `#9DA1A7`.
4. **Tint floor.** Each background has a tint floor. The glass is never more transparent than the
   lowest tint at which ink, ink-2 and accent-soft keep 4.5:1 unchanged. The adjusted secondary
   text must also be no lighter than `#A8ACB1`, the Flowing light colour at 40 %, so it stays
   clearly below ink-2. Northern lights at 60 % needs `#ACB0B4`, which is lighter than that bound,
   and at 70 % it needs `#9DA1A7`, which is darker. So in practice only Northern lights rises, to
   70 % at 8 dp.
5. **Technique.**
   * One shared blur per frame, at a quarter of the resolution, which each panel samples.
   * No new dependency. Haze is not used.
   * The blur needs API 31 or later (`RenderEffect`). On API 28 to 30 the glass uses the Reduced
     tint with no blur.
6. **Where the glass is.** On cards, panels and the rail. A phone has no rail.
7. **Clips move to `TextureView`.** The video clips of [0056](0056-add-an-animated-background-and-the-appearance-group.md)
   play in a `TextureView`, not a `SurfaceView`, so the glass can see them.
8. **Dialogs and sheets.**
   * While one is open, the app blurs its own window (24 dp, `WINDOW_BLUR_DP` in `GlassHost.kt`) and
     freezes the background on its still frame.
   * The dialog draws no scrim, and its panel takes the glass tint.
   * On API 28 to 30 the scrim stays.
9. **TV pace.** The drawn background runs at 24 frames a second only while the blur is drawn: on a
   TV, on API 31 or later, with Standard or Reduced transparency (Reduced blurs at 48 dp).
   * A TV on API 28 to 30, which has the tint and no blur, stays at 30 frames a second.
   * Off, High contrast and the plain page draw no blur, so they run at 30 frames a second too.
   * Phones are unchanged, and a clip plays at its own rate.

   The rule is `glassFrameRate` in `GlassRules.kt`.
10. **The stream** has no glass, because the video is behind the chrome. Sprint 15 adds the
    background and a glass panel to the Starting screen. The glass on the Quitting screen, and
    any glass in the stream (the in-game menu), are decided in Sprint 15, because they change
    this rule (see More Information).
11. **The Accessibility group** holds these settings:
    * **Reduce motion:** Follow system, On or Off. Follow system tracks the animation scale of the
      system while the app runs.
    * **Transparency:** Standard, Reduced or Off, as in decision 1.
    * **Thicker focus outline:** a switch. On, the outline is 5 dp and drawn in `ink`; otherwise it
      is 3 dp in accent-soft. High contrast draws the thicker one too.
    * **Text size:** 100, 115 or 130 %, times the system scale.
    * **High contrast:** solid panels, text at 7:1 or more, opaque borders at 3:1 or more, a dimmed
      background and the thicker outline (decision 15). It overrides only Transparency, Thicker
      focus outline, Glass tint and Glass blur: while it is on, those four rows are disabled with
      the note "High contrast is on in Accessibility". Text size, the palette and Reduce motion
      still apply, and the palette is applied after High contrast.
    * **Colour-blind palette:** Off, Red-green or Blue-yellow, for the status colours. Every status
      also gets a shape or a text (decisions 16 and 17).

    The stream follows the text size, the focus, high contrast, the palette and reduce motion. It
    reads the look once, when the stream starts (`StreamSessionFactory.look`), so a change made in
    Settings shows in the next stream.
12. **The contract** is `BorealisLook`, with `LocalBorealisLook`, in `:borealis:designsystem`
    (`theme/BorealisLook.kt`).
13. **A halo for the text on the background.** Text that stands on the animated background with no
    glass behind it (a screen title, a section label, a count, a hint, a loading or an empty text)
    has a soft dark halo. Owner, 2026-10-20. The numbers are in the Halo section of
    [docs/ux/accessibility.md](../ux/accessibility.md) (Tables D to H); the code is `BackdropText`
    (`components/BackdropText.kt`).
    * **What it is:** the shadow of a stroked copy of the text, with a stroke of 3 dp, a blur of 2 dp
      and the `page` colour at 80 %. It needs no blur effect, so it is drawn on every API level.
    * **When it is drawn:** over a background of any style except the plain page, with Transparency
      Off and under High contrast too, because the background is still behind the text. It is never
      drawn in the stream, which has no background.
    * **The ink:** off glass, the ink-3 and ink-4 roles become ink-2, which the halo keeps at 4.5:1
      and which they do not reach on their own.
    * **Why 3 dp:** the worst case is taken over every place that the text can take on every frame,
      a pixel apart, and not over a few places. No stroke of 2 dp keeps ink-2 at 4.5:1 with 2 % to
      spare: at a blur of 2 dp and 80 % its worst is 3.11, and the best of them, at 100 %, is 4.24.
      A stroke of 3 dp keeps it from 80 %, and that is the smallest candidate. Its tightest case is
      5.32:1 (ink-2, body text, TV, Northern lights).
    * **Under High contrast** every loose text is 14.70:1 or more, against the 7:1 that the mode
      promises. The dim of the background alone gives 12.00.
14. **The bare icons and the Add host field.** The icons that stand on the background with no
    surface behind them (the back arrow, the actions of the Hosts bar and the show-hidden toggle of
    the Apps bar) get the halo of the text through `BackdropIcon`, so the screen has one halo. They
    are measured as glyph masks and need 3:1 (WCAG 1.4.11): the worst is 5.71:1 (ink-2, the gear,
    TV, Northern lights), and 14.79:1 or more under High contrast. The Address field of Add host is
    a filled glass field with its own outline, so its label never stands on the raw background. Its
    lowest ratios over every background are 4.60:1 for the label, 3.88:1 for the focused outline
    and 4.62:1 for the error text. **The owner accepted the switch from an outlined field to a
    filled one on 2026-10-21 (Sprint 10 UAT).** An outlined field cuts its outline behind the floating label,
    so half of the label stood on the background and not on the glass. Tables I to M of the
    accessibility page have the numbers.
15. **High contrast.** The colours are the `high-contrast` group of `design/tokens.json`.
    * **Text:** 7:1 or more on page, chrome, surface, surface-raised and under the accent tint and
      the hover. ink-3 is `#D0D3D7` at 9:1, ink-4 is `#BEC1C4` at 7.5:1 and the accent is
      `#C8B7FC`.
    * **Borders:** opaque, 3:1 or more. The line is `#80868D` and the strong line is `#9CA2A8`.
    * **The outline** is 5 dp and drawn in ink.
    * **The background** has a `page` veil at 0.85. Over the brightest pixel of the 12 sets of
      frames of the six backgrounds every text role keeps 8:1 or more, and the lowest is ink-4 at
      8.00:1, about 14 % above the 7:1 limit.
    * **What it overrides** is in decision 11.
16. **The offline colour and the status shapes.** The offline colour goes from `#4B5158` (2.2:1) to
    `#686E75`: 3.7:1 on page, 3.5:1 on surface and 3.25:1 on surface-raised (WCAG 1.4.11). Owner,
    2026-10-20. The status dot has a shape for each state, so that colour is not the only signal
    (WCAG 1.4.1): a filled circle for online, a filled triangle for attention, and a hollow ring
    for offline and unknown.
17. **Colour-blind palettes.** The colours are from the Okabe and Ito set, checked with the Machado
    simulation (severity 1.0) and the CIEDE2000 distance. The least distance between two status
    colours is 23.7 or more for red-green and 23.9 for blue-yellow under tritanopia, against a bar
    of 20. Each colour keeps 3:1 as an indicator, and the status inks keep 7:1 or more (the bar of `ColourVisionPaletteTest`; 9.2:1 measured). The check is in
    Kotlin (`ColourMeasures` and `ColourVisionPaletteTest`), not in the Python tool.
18. **New panels, and Off.** The Settings panes, the Privacy card, the Audit panel and the Privacy
    row had no surface before Sprint 10. Under Off, under High contrast and on the plain page they
    are solid `surface`. Owner, 2026-10-20: accessibility comes first.

### Measurements behind the decision

Measured on 2026-10-20 with a spike build from the throwaway local branch `task/s10-blur-spike`
(not pushed), at `154ca822`. Times are the per-frame p50 from `gfxinfo framestats`. The budget is
33.3 ms at 30 fps.

* **Google TV Streamer** (MT8696, API 34):

  | Screen | Today | Shared blur at 1/4 | Haze 2.0.1 | One blur per panel |
  | --- | --- | --- | --- | --- |
  | Hosts | 29.4 ms | 34.8 ms | 37.7 ms | 39.0 ms |
  | Settings | 27.6 ms | 37.0 ms | 38.6 ms | 40.1 ms |

  * A clip in a `TextureView` with the shared blur costs 25.5 ms, against 18.3 ms without glass.
  * A clip in today's `SurfaceView` cannot be seen by the app, so it cannot be blurred.
  * The blur radius does not change the cost. 40 % with 8 dp and 70 % with 48 dp both cost 36.8 to
    36.9 ms in Settings.
  * With the background still animating behind a sheet, the sheet costs 36.1 ms.
* **Galaxy S22** (Snapdragon 8 Gen 1, API 36): Hosts costs 17.5 ms today and 19.3 ms with the
  shared blur. With the background still animating behind a sheet, the sheet costs 20.5 ms.
* **Blur between windows** is not available on either device (`mBlurEnabled=false`,
  `isCrossWindowBlurEnabled` false).
* **Sheet with the background frozen:** with the app window blurred and the background frozen, no
  frame is drawn while the sheet is open, on either device.

With 24 frames a second on the TV, the budget is 41.7 ms, and Settings with glass (37 ms) fits it.
The owner chose the three levels and the settings on 2026-10-20 from the G1 comparison page. That
page is private, so it is not linked here.

### Consequences

* Good, because the background stays visible behind the panels, and the text meets 4.5:1 on each
  background at the chosen tints.
* Good, because the blur is one shared pass per frame, and no dependency is added.
* Good, because the Accessibility settings let each user choose less motion, less transparency,
  more contrast, a bigger text and a palette that suits them.
* Good, because a stream does not pay for the glass.
* Good, because the text on the background keeps 4.5:1 with the halo, and 7:1 under High contrast,
  over every background (decision 13).
* Bad, because the drawn background runs at 24 frames a second on a TV while the blur is drawn.
* Bad, because the glass costs about 9 ms more per frame on the TV Settings screen, and 1.8 ms more
  on the S22 Hosts screen.
* Bad, because the Northern lights background needs a higher floor, so its glass is more opaque
  than the other backgrounds.
* Bad, because API 28 to 30 get no blur, and the dialogs keep a scrim there.
* Bad, because the halo and the tint-only glass have been drawn only in tests on API 28 to 30, and
  have not been seen on a device there: no such device is available.
* Bad, because in the device round of Sprint 10 a move between Settings groups on the Streamer, with
  the glass at Standard, took 64 ms at the median (72 ms at the most), against 47 to 60 ms in
  Sprint 9. A frame at rest is about 9 ms longer with the blur, as the measurements above say.

## Pros and Cons of the Options

### Haze 2.0.1

* Good, because it is a ready-made blur library.
* Bad, because it costs more per frame than the shared blur on both measured TV screens (37.7 ms
  on Hosts against 34.8 ms, and 38.6 ms on Settings against 37.0 ms).
* Bad, because it adds a dependency. The owner chose no new dependency.

### One blur per panel

* Good, because each panel blurs its own backdrop.
* Bad, because it is the most expensive option on the TV (39.0 ms on Hosts and 40.1 ms on Settings).

### One shared blur per frame, at a quarter of the resolution (chosen)

* Good, because it costs the least of the blur options on the TV (34.8 ms on Hosts and 37.0 ms on
  Settings) and needs no dependency.
* Good, because each panel samples the same blurred frame.
* Bad, because the TV drawn background must run at 24 frames a second while the blur is drawn.

### Tint only, with no blur

* Good, because it costs no blur on any device.
* Bad, because the background shows through sharp. The notes give no measurement for this option.

### Dialogs: blur between windows

* Good, because the platform would blur what is behind the dialog.
* Bad, because neither measured device supports it (`mBlurEnabled=false`).

### Dialogs: blur the app's own window (chosen)

* Good, because it works on the measured devices, and the background is frozen so no frame is drawn
  while the sheet is open.
* Bad, because the background does not move while a dialog is open.

### TV: accept about 27 frames a second

* Good, because nothing changes in the pacing code: the 30 fps loop simply misses its frame time.
* Bad, because with glass the TV Settings frame costs 37 ms, more than the 33.3 ms budget at
  30 fps, so the background drops to about 27 fps with uneven gaps between frames.

### TV: 24 frames a second (chosen)

* Good, because the Settings screen with glass (37 ms) fits the 41.7 ms budget at 24 frames a second.
* Bad, because the background moves less smoothly while the blur is drawn.

### TV: blur at 15 frames a second

* Good, because the blur would update less often.
* Bad, because it was not measured: it halves the blur work on average, but the frames that redo
  the blur still cost about 37 ms, so the frame times stay uneven.

### TV: tint only

* Good, because it costs no blur on the TV.
* Bad, because the TV loses the blurred look, and the background shows through sharp.

## More Information

* Amends [0056](0056-add-an-animated-background-and-the-appearance-group.md): the clips play in a
  `TextureView`, and the drawn background runs at 24 frames a second on a TV while the blur is drawn.
* Amended on 2026-10-21, after the work of Sprint 10:
  * decisions 13 to 18 record the halo, the icons and the Add host field, High contrast, the
    offline colour and the status shapes, the colour-blind palettes and the new panels, which the
    first version of this ADR left to the notes;
  * decision 9 now says that the 24 frames a second apply only while the blur is drawn;
  * decision 11 now says that High contrast overrides four rows and not "the other settings", and
    that the thicker outline is 5 dp in ink;
  * decision 8 records the 24 dp of the window blur;
  * the switch from an outlined to a filled Add host field (decision 14) was decided on
    2026-10-21, see below.
* Amended on 2026-10-21 (Sprint 10 UAT):
  * the owner accepted the Sprint 10 trade-offs as they are: the filled Add host field
    (decision 14); the handheld Settings group list that scrolls, with 6 of the 11 groups
    at 800 × 480 dp; the docked phone keyboard, 24 dp taller, with its keys under 48 dp;
    phone buttons at 40 dp and menu rows at 44 dp; and the glass cost of a move between
    Settings groups on the TV, about 65 ms against 50 to 54 ms with Transparency Off;
  * the decisions that were pending the owner's verdict are now decided, the filled Add
    host field among them;
  * the glass in the stream and the glass on the Starting and Quitting screens are decided
    in Sprint 15, because they change decision 10: the stream has no glass today. The
    in-game menu takes the Library's glass style only after a spike, which compares a
    blurred `PixelCopy` snapshot of the video with tint only.
* Related: [0053](0053-material-3-with-borealis-focus-on-tv.md) (the focus indication on TV),
  [0044](0044-design-tokens-pipeline.md) (the design tokens that the text colours come from) and
  [0055](0055-settings-storage-and-the-phase-2-settings-screen.md) (the settings storage).
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
* Roadmap: [Sprint 10](../roadmap.md#sprint-10-ux-and-accessibility).
* Amended on 2026-10-28 by [0061](0061-glass-and-the-animated-background-in-the-stream.md): decision 10 (the stream has no glass) and decision 13 (the halo is never drawn in the stream) no longer hold. The stream gets the glass and the background, as 0061 decides.
* Test dependency for the screen reader work of Sprint 10 (task `s10-talkback`):
  `io.github.takahirom.roborazzi:roborazzi-accessibility-check`, at the version of Roborazzi in the
  version catalog (1.76.0), in the test fixtures of `:borealis:designsystem`. It runs the
  Accessibility Test Framework of Google over a Compose screen in a Robolectric test. The five
  items of the [dependency policy](../policies/dependencies.md):
  1. **Reason:** the checks find what a person reviewing by hand misses on every screen: a control
     with no name, two controls with one name, a touch target below 48dp, a traversal that loops. A
     few lines of our own would repeat a small part of them and none of the contrast and
     duplicate-name logic. The tests of the screens assert the headings, the order and the states
     besides, as the checks cannot know what a heading is.
  2. **Licence:** Apache-2.0 for the module and for the framework (both POMs), on the allowlist.
  3. **Maintenance:** the module 1.76.0 was released on 2026-09-29 by the Roborazzi project, which
     the build already uses; the framework 4.1.1 (2024-03-29) is the latest release, by Google.
  4. **Footprint:** a 21 KB module and the 773 KB framework, with Material Components 1.2.0-rc01,
     Espresso, the AndroidX test runner, Guava and protobuf-javalite behind it. The framework also
     brings `jsoup` 1.15.1, `checker-qual` 3.22.1, `error_prone_annotations` (2.14.0 asked for,
     2.47.0 resolved) and `hamcrest` 1.3 (core and library), which are test-only artifacts too.
     jsoup 1.15.1 predates the fix of CVE-2022-36033 (jsoup 1.15.3). It is on the unit-test
     classpath only (`:borealis:designsystem:dependencies --configuration
     debugUnitTestRuntimeClasspath`) and is never shipped. It is a test dependency of the
     fixtures: none of these artifacts is on the release classpath of the app (checked with
     `:app:dependencies --configuration releaseRuntimeClasspath` on 2026-10-21), and they have no
     permissions and no native code.
  5. **Alternatives:** our own assertions only (rejected for the reason in 1); Espresso
     `AccessibilityChecks.enable()` (needs a device or an emulator, which the CI does not run, and
     looks at views, not at Compose); the Accessibility Scanner app (by hand, not in the CI).
  The findings that a screen cannot fix are accepted in its test with a reason each; the contrast of
  text over the animated background is measured by the tool in `design/accessibility/` instead.
* Related: [0058](0058-appearance-and-reading-options.md) adds the font, accent, background intensity and text spacing choices, which build on the glass and the contrast of this ADR.
