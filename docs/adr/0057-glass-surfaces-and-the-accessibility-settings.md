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

For the TV, when the glass is on:

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
   * While one is open, the app blurs its own window and freezes the background on its still frame.
   * The dialog draws no scrim, and its panel takes the glass tint.
   * On API 28 to 30 the scrim stays.
9. **TV pace.** While the glass is on, the drawn background runs at 24 frames a second on a TV.
   Phones and clips are unchanged.
10. **The stream** has no glass, because the video is behind the chrome. Sprint 11 adds the
    background and a glass panel to the Starting screen.
11. **The Accessibility group** holds these settings:
    * **Reduce motion:** Follow system, On or Off.
    * **Transparency:** Standard, Reduced or Off, as in decision 1.
    * **Focus outline:** thicker, from 3 to 5 dp, in a lighter colour.
    * **Text size:** 100, 115 or 130 %, times the system scale.
    * **High contrast:** solid panels, text at 7:1 or more, opaque borders at 3:1 or more, a dimmed
      background and the thicker outline. It overrides the other settings while it is on.
    * **Colour-blind palette:** Off, Red-green or Blue-yellow, for the status colours. Every status
      also gets a shape or a text.

    The stream follows the text size, the focus, high contrast, the palette and reduce motion.
12. **The contract** is `BorealisLook`, with `LocalBorealisLook`, in `:borealis:designsystem`
    (`theme/BorealisLook.kt`).

### Measurements behind the decision

Measured on 2026-10-20 with a spike build from the throwaway branch `task/s10-blur-spike`, at
`154ca822`. Times are the per-frame p50 from `gfxinfo framestats`. The budget is 33.3 ms at 30 fps.

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
* Bad, because the drawn background runs at 24 frames a second on a TV while the glass is on.
* Bad, because the glass costs about 9 ms more per frame on the TV Settings screen, and 1.8 ms more
  on the S22 Hosts screen.
* Bad, because the Northern lights background needs a higher floor, so its glass is more opaque
  than the other backgrounds.
* Bad, because API 28 to 30 get no blur, and the dialogs keep a scrim there.

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
* Bad, because the TV drawn background must run at 24 frames a second while the glass is on.

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
* Bad, because the background moves less smoothly while the glass is on.

### TV: blur at 15 frames a second

* Good, because the blur would update less often.
* Bad, because it was not measured: it halves the blur work on average, but the frames that redo
  the blur still cost about 37 ms, so the frame times stay uneven.

### TV: tint only

* Good, because it costs no blur on the TV.
* Bad, because the TV loses the blurred look, and the background shows through sharp.

## More Information

* Amends [0056](0056-add-an-animated-background-and-the-appearance-group.md): the clips play in a
  `TextureView`, and the drawn background runs at 24 frames a second on a TV while the glass is on.
* Related: [0053](0053-material-3-with-borealis-focus-on-tv.md) (the focus indication on TV),
  [0044](0044-design-tokens-pipeline.md) (the design tokens that the text colours come from) and
  [0055](0055-settings-storage-and-the-phase-2-settings-screen.md) (the settings storage).
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
* Roadmap: [Sprint 10](../roadmap.md#sprint-10-ux-and-accessibility).
