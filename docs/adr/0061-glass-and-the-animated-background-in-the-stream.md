---
status: accepted
date: 2026-10-28
decision-makers: João Pedro Magalhães
---

# 0061 Show the glass and the animated background in the stream

## Context and Problem Statement

The stream video is a hardware `SurfaceView`, and the chrome of the stream is a `ComposeView` drawn over it. The design system's glass (`GlassHost`) blurs a recording of the Compose background with `RenderEffect`, and it cannot record a `SurfaceView`. So [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 10 left the stream with no glass, and [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 kept the animated background out of the stream. The Google TV Streamer has no cross-window blur, so a blur behind a separate window is not an option. How should the in-game menu, the Quit popup and the screens outside the game look in a stream, without a decoder that competes with the stream?

## Decision Drivers

* A stream owns the decoder, the CPU and the screen, so the stream must not pay more than it must for the look.
* The Google TV Streamer has no cross-window blur (`mBlurEnabled=false`), so a blur behind a separate window is not an option there.
* The stream should look like the Library.
* Text over the blurred video must keep its contrast.
* The accessibility settings of [0057](0057-glass-surfaces-and-the-accessibility-settings.md) still win.
* The clip that plays behind the Starting screen must not compete with the stream's hardware decoder.

## Considered Options

For the glass in the stream:

* Solid surface (today).
* Tint only, with no blur.
* A live `PixelCopy` of the video at a quarter of its size, blurred with `RenderEffect`, refreshed 4, 15 or 30 times a second, or at every display frame.
* A copy frozen when the menu opens.

For the background outside the game:

* Off (today).
* The user's style, still.
* The user's style, moving, with the clip released before the stream decoder is configured.
* Always Aurora curtains.

For the screens that show the background:

* Starting and Quitting only.
* Every screen outside the game.

## Decision Outcome

Chosen option: "A live blurred copy of the video at 30 times a second, with Solid as the other choice, and the user's moving background on every screen outside the game", because it gives the stream the Library's look at a cost that the owner accepted after a live comparison on the TV and the S22 (the owner's [gate G2 of Sprint 15](https://github.com/magalz/Borealis/issues/99), 2026-10-28), and because one rule for every screen is simpler than a rule per screen.

Decision details:

1. **Two Appearance options in Settings**, next to Background.
   * **Menu glass in the stream:** Blur (the default) or Solid. Blur is a live copy of the video at a quarter of its size, blurred and refreshed 30 times a second. It is drawn behind the in-game menu, its side panel and the Quit popup. The copies stop when the menu closes and in picture-in-picture. Solid is today's surface.
   * There is no tint-only option, because the owner judged it a nightmare for accessibility. There is no frozen copy, because a frozen copy looks very strange when the video moves.
   * **Background outside the game:** Moving (the default), Still or Off. It is the user's background style (Settings, Background). Its intensity, from [0058](0058-appearance-and-reading-options.md), applies outside the game as it does in the Library.
2. **Every screen outside the game** (Starting, the conflict, the error, Quitting and Disconnecting) shows that background, with no difference between the screens. No option chooses which screens get the background. The background goes when the phase becomes Streaming. The first frame can come before the connection reports that the stream started, so the cut follows the phase and not the stage.
3. **The accessibility settings still win** ([0057](0057-glass-surfaces-and-the-accessibility-settings.md)).
   * Transparency Off and High contrast give a solid surface and no copy.
   * Transparency Standard or Reduced gives the stream glass the tint and blur of that level. The final strength is set by the contrast check of decision 4.
   * Reduce motion and the battery saver turn the copy off, so the menu glass is solid, and keep the background still, whatever the options. The battery saver is followed live, as [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 6 does in the Library; it is not part of the look read at the start.
   * Below API 31 the stream glass is solid, with no `RenderEffect`. The owner allows only Blur or Solid, with no tint-only option. This differs from the Reduced tint that [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 5 gives the Library panels on API 28 to 30.
   * The stream reads the look once, when the stream starts, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 11 says. The transparency level and its tint and blur, and the two new options, are read the same way, so a change shows in the next stream.
4. **Text over the background and the blurred video.**
   * Text on the background outside the game gets the halo of the Library, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 13 gives it.
   * Text over the blurred video keeps 4.5:1 through a text halo or shadow, or through a stronger blur and a tint floor. The result is checked with the contrast tool in `design/accessibility/` before the owner's gate G1 of Sprint 15.
5. **Dialogs.**
   * In the Streaming phase, the Quit popup is a panel inside the chrome layer. It uses the same live copy as the menu. The conflict dialog does not show in that phase, because the conflict belongs to the launch, before any video.
   * [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 8 (the window blur and a frozen background) does not apply to the stream's Quit popup.
   * Outside the game, the screens and their dialogs sit on the background in a glass host, and follow [0057](0057-glass-surfaces-and-the-accessibility-settings.md) as the Library does, decision 8 included.
6. **Clip lifecycle.** A clip style plays only while no stream decoder is configured.
   * It plays from the start until the stream's decoder is configured. The clip is released before the decoder is configured.
   * It plays again after the stream's decoder has been released, when the connection has stopped.
   * While a decoder is configured (Quitting or Disconnecting before the stream has stopped, or an error screen while the decoder is still up), a clip style shows its still. A Compose style keeps moving.
7. **Frame pace.** The background outside the game follows [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 9 unchanged. On a TV it runs at 24 frames a second while a blur is drawn. The copy rate of 30 a second is separate, and it applies only to the menu glass in the Streaming phase.
8. **This amends** [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 and [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decisions 10 and 13. Two more rules of 0057 differ in the stream and stay in force for the Library: decision 5 (the Reduced tint below API 31, where the stream glass is solid) and decision 8 (the window blur of dialogs, where the stream's Quit popup uses the copy).

### Measurements behind the decision

Measured in the owner's comparison on 2026-10-28. The TV ran Steam Big Picture, and the S22 ran an HDR stream with the copy at 30 a second. The TV rows are 20 seconds each.

| Rate of the copy on the TV | Chrome frame, p50 | Decode, average | Note |
| --- | --- | --- | --- |
| 4 a second | 14.7 ms | 4.7 ms | |
| 15 a second | 13.8 ms | 5.2 ms | |
| 30 a second (chosen) | 13.8 ms | 6.0 ms | |
| Every display frame (about 46 copies a second complete) | 27.0 ms | 7.4 ms | 14 display ticks a second skipped while a copy is pending |

Other measures:

* **TV copy:** over all TV runs and rates, 40,751 copies, all successful: 11.9 ms at p50 and 18.6 ms at p95, 1.7 % over 25 ms, and the longest 188 ms. At the chosen 30 a second alone: 11.8 ms at p50, 17.7 ms at p95, 1.5 % over 25 ms. The copy is 480 x 270 pixels.
* **S22 copy (HDR):** 30 copies a second, with no failed copy. The copy takes 2.8 ms at p50 and 15.3 ms at most. The first copy after the menu opens took 129.7 ms, and 3 requests were skipped in that first second. The 15.3 ms maximum is after that first copy. The chrome frame takes 7.3 ms at p50. The decode averages 5.6 ms with the menu open and 5.9 ms with it closed.
* **TV start, from the creation of the activity to the first frame, in ms.** Within a series the start time varies by 1 to 2 s, and over all runs it spans 10.5 to 18 s, so only a large cost would show.

| Background | Start times (ms) |
| --- | --- |
| Off | 12956, 14001, 14422, 12188, 11276 |
| Moving, user style Aurora curtains (spike background 2) | 11443, 11578, 11409 |
| Moving, user style Northern lights clip (spike background 2) | 12025, 11433 |
| Aurora curtains, always (spike background 3) | 12865, 12109 |

* **TV clip:** the player is created in 16 to 18 ms. The first background frame comes 0.7 to 0.9 s after the start. The clip's release takes 33 to 48 ms and ends 93 to 106 ms before the stream's decoder starts to configure. There were 0 decoder errors.

### Consequences

* Good, because the stream gets the Library's look.
* Good, because the moving background shows no measurable start cost on the TV, within the run-to-run spread of about 2 s.
* Good, because at 30 a second the copy looks live, "more than the eye catches", in the owner's words.
* Good, because the Solid option keeps today's surface for anyone who wants it, and the accessibility settings still win.
* Bad, because one copy typically costs 11 to 16 ms on the TV GPU (11.9 ms at p50), and about 3 ms at p50 on the S22.
* Bad, because the first copy after the menu opens is slow (129.7 ms on the S22), and long copies occur (1.7 % of TV copies take over 25 ms, with a maximum of 188 ms).
* Bad, because on the TV the decode time rose from about 4.7 ms to 6.0 ms at 30 a second.
* Bad, because the colour of the HDR copy on the S22 was not yet judged.
* Bad, because a large purple patch was seen once, on the panel only and not on the video, for a moment, around a change of the spike's switch. The log shows no copy error. It is a lead for the implementation: a copy taken during an update, or the background drawn behind the panel during the change.
* Bad, because the text contrast over the blurred video is not yet measured (decision 4).

## Pros and Cons of the Options

### Glass in the stream: Solid surface (today)

* Good, because it costs no copy and no blur.
* Bad, because the stream does not look like the Library.

### Glass in the stream: tint only, with no blur

* Good, because it costs no copy and no blur on any device.
* Bad, because the video shows through sharp.
* Bad, because the owner judged it "a nightmare for accessibility".

### Glass in the stream: live copy at 4 a second

* Good, because it takes the fewest copies. On the TV the decode is 4.7 ms, and the chrome frame is 14.7 ms at p50.
* Bad, because it was not chosen. The owner chose 30 a second.

### Glass in the stream: live copy at 15 a second

* Good, because the decode is 5.2 ms on the TV, and the chrome frame is 13.8 ms at p50.
* Bad, because it was not chosen. The owner chose 30 a second.

### Glass in the stream: live copy at 30 a second (chosen)

* Good, because it looks live, "more than the eye catches", in the owner's words.
* Good, because the chrome frame stays at 13.8 ms at p50 on the TV.
* Bad, because the decode rises to 6.0 ms on the TV, and each copy typically costs 11 to 16 ms there.

### Glass in the stream: live copy at every display frame

* Good, because it is the most live option.
* Bad, because on the TV about 46 copies a second complete, and 14 display ticks a second are skipped while a copy is pending. The chrome frame takes 27.0 ms at p50.

### Glass in the stream: frozen copy when the menu opens

* Good, because it takes one copy, when the menu opens.
* Bad, because it looks "very strange and ugly" when the video moves, in the owner's words.

### Background outside the game: off (today)

* Good, because no player and no decoder run.
* Bad, because the stream does not show the Library's background.

### Background outside the game: the user's style, still

* Good, because no player is made, as [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 6 says for a still.
* Bad, because the background does not move. It is an option in Settings, not the default.

### Background outside the game: the user's style, moving (chosen)

* Good, because the TV measures show no measurable start cost.
* Good, because the clip is released before the stream's decoder is configured, with 0 decoder errors.
* Bad, because the clip's decoder runs during the start on the TV, which the [roadmap](../roadmap.md#sprint-15-the-stream-screens-and-glass) lists as a risk.

### Background outside the game: always Aurora curtains

* Good, because it is a drawn style, so it needs no player.
* Bad, because it ignores the user's choice of style. The owner chose the user's style.

### Screens: Starting and Quitting only

* Bad, because the screens outside the game would differ from each other. The owner chose one rule for all of them.

### Screens: every screen outside the game (chosen)

* Good, because all the screens follow one rule, with no difference between them.
* Bad, because the Disconnecting, conflict and error screens also show the background, and so can start the clip.

## More Information

* Amends [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 (never in a stream) and [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decisions 10 (the stream has no glass) and 13 (the halo is never drawn in the stream).
* Stream-only differences from [0057](0057-glass-surfaces-and-the-accessibility-settings.md): decisions 5 and 8. They stay in force for the Library.
* Background intensity: [0058](0058-appearance-and-reading-options.md).
* The owner's comparison on 2026-10-28 used variants behind a hidden switch, built on the spike branch `spike/s15-stream-glass`. That branch was never merged.
* Roadmap: [Sprint 15](../roadmap.md#sprint-15-the-stream-screens-and-glass).
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
