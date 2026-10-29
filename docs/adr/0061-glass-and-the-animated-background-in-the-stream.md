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

For the error of a stream that ran (added on 2026-10-29, decision 9):

* The full-screen error on the background (today).
* A popup on the background.
* A popup on the last frame of the stream, frozen and blurred.

## Decision Outcome

Chosen option: "A live blurred copy of the video at 30 times a second, with Solid as the other choice, and the user's moving background on every screen outside the game", because it gives the stream the Library's look at a cost that the owner accepted after a live comparison on the TV and the S22 (the owner's [gate G2 of Sprint 15](https://github.com/magalz/Borealis/issues/99), 2026-10-28), and because one rule for every screen is simpler than a rule per screen. *Amended on 2026-10-29:* the choice holds. Decision 9 adds the error popup over the last frame of a stream that failed, and decision 10 records how the text over the glass keeps its contrast. Decisions 1 to 6 and 8 carry a note where the shipped code differs from their first wording.

Decision details:

1. **Two Appearance options in Settings**, next to Background.
   * **Menu glass in the stream:** Blur (the default) or Solid. Blur is a live copy of the video at a quarter of its size, blurred and refreshed 30 times a second. It is drawn behind the in-game menu, its side panel and the Quit popup. The copies stop when the menu closes and in picture-in-picture. Solid is today's surface.
   * There is no tint-only option, because the owner judged it a nightmare for accessibility. There is no frozen copy, because a frozen copy looks very strange when the video moves.
   * *Amended on 2026-10-29:* "no frozen copy" holds for the menu and its Quit popup, which show the live copy. The popup of the error of a stream that ran stands on one frozen copy, the last frame, which nothing moves behind (decision 9).
   * **Background outside the game:** Moving (the default), Still or Off. It is the user's background style (Settings, Background). Its intensity, from [0058](0058-appearance-and-reading-options.md), applies outside the game as it does in the Library.
   * *Amended on 2026-10-29:* the labels in Settings are Menu glass in the stream (Blur or Solid) and Background before and after the stream (Moving, Still or Off).
2. **Every screen outside the game** (Starting, the conflict, the error, Quitting and Disconnecting) shows that background, with no difference between the screens. No option chooses which screens get the background. The background goes when the phase becomes Streaming. The first frame can come before the connection reports that the stream started, so the cut follows the phase and not the stage.
   * *Amended on 2026-10-29:* the screens do differ in one case. The error of a stream that ran, once the copy of its last frame is asked for, stands on that frame and not on the background (decision 9). With no frame to show, it stands on the background like the others.
3. **The accessibility settings still win** ([0057](0057-glass-surfaces-and-the-accessibility-settings.md)).
   * Transparency Off and High contrast give a solid surface and no copy.
     *Amended on 2026-10-29:* this holds for the menu and its Quit popup. The error popup is the exception (decision 9): its one copy is made whatever the look, and with Transparency Off, High contrast or below API 31 the frame shows sharp under the scrim and the panel is solid.
   * Transparency Standard or Reduced gives the stream glass the tint and blur of that level. The final strength is set by the contrast check of decision 4.
   * Reduce motion and the battery saver turn the copy off, so the menu glass is solid, and keep the background still, whatever the options. The battery saver is followed live, as [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 6 does in the Library; it is not part of the look read at the start.
     *Amended on 2026-10-29:* neither turns off the copy behind the error popup, because that frame is still and copied once. They still keep the background still where the popup stands on it.
   * Below API 31 the stream glass is solid, with no `RenderEffect`. The owner allows only Blur or Solid, with no tint-only option. This differs from the Reduced tint that [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 5 gives the Library panels on API 28 to 30.
   * The stream reads the look once, when the stream starts, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 11 says. The transparency level and its tint and blur, and the two new options, are read the same way, so a change shows in the next stream.
4. **Text over the background and the blurred video.**
   * Text on the background outside the game gets the halo of the Library, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 13 gives it.
   * Text over the blurred video keeps 4.5:1 through a text halo or shadow, or through a stronger blur and a tint floor. The result is checked with the contrast tool in `design/accessibility/` before the owner's gate G1 of Sprint 15.
   * *Amended on 2026-10-29:* the owner chose the halo and dropped the tint floor. Decision 10 records the choice and its measures.
   * *Amended on 2026-10-29:* text that stands on a panel, and not straight on the background, has no halo of its own. That is the text of the error popup over the background, which has the text colours of the glass panel, and the steps of the Starting screen. Over the frozen frame, where the panel is the video glass, the text has the halo of decision 10.
5. **Dialogs.**
   * In the Streaming phase, the Quit popup is a panel inside the chrome layer. It uses the same live copy as the menu. The conflict dialog does not show in that phase, because the conflict belongs to the launch, before any video.
   * [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 8 (the window blur and a frozen background) does not apply to the stream's Quit popup.
   * Outside the game, the screens and their dialogs sit on the background in a glass host, and follow [0057](0057-glass-surfaces-and-the-accessibility-settings.md) as the Library does, decision 8 included.
   * *Amended on 2026-10-29:* the conflict dialog and the error popup are panels of the chrome layer (`StreamDialog`), not dialog windows, so the host does for them what decision 8 does for a window, but to the background alone: it blurs it (24 dp) and holds it still, the dialog draws no scrim, and the panel takes the tint of the glass. The host does this only where it blurs: API 31 or later, Transparency Standard or Reduced, no High contrast, and a background style that is not Off. On API 28 to 30, with Transparency Off, with High contrast and with the background Off, the scrim and a solid panel stay.
   * *Amended on 2026-10-29:* over the frozen last frame of decision 9 the scrim stays, also where the frame is blurred. The owner decided it. It is one more difference from [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 8, next to the Quit popup (the first bullet) and the Reduced tint of API 28 to 30.
6. **Clip lifecycle.** A clip style plays only while no stream decoder is configured.
   * It plays from the start until the stream's decoder is configured. The clip is released before the decoder is configured.
   * It plays again after the stream's decoder has been released, when the connection has stopped.
   * While a decoder is configured (Quitting or Disconnecting before the stream has stopped, or an error screen while the decoder is still up), a clip style shows its still. A Compose style keeps moving.
   * *Amended on 2026-10-29:* the session holds the decoder from the first stage of the connection itself, which is the stage Preparing the display, so the clip is released a few stages before the decoder is configured. It plays again when the connection has stopped (`StreamSession.onConnectionStopped`), or at once where no connection ran.
   * *Amended on 2026-10-29:* under the conflict dialog and the error popup, where the host blurs the background (decision 5), every style is held still, a Compose style included. A Compose style moves under them only where the host does not blur: API 28 to 30, Transparency Off, High contrast. Reduce motion and the battery saver hold every style still anyway.
7. **Frame pace.** The background outside the game follows [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 9 unchanged. On a TV it runs at 24 frames a second while a blur is drawn. The copy rate of 30 a second is separate, and it applies only to the menu glass in the Streaming phase.
8. **This amends** [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 and [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decisions 10 and 13. Two more rules of 0057 differ in the stream and stay in force for the Library: decision 5 (the Reduced tint below API 31, where the stream glass is solid) and decision 8 (the window blur of dialogs, where the stream's Quit popup uses the copy). *Amended on 2026-10-29:* decision 8 also differs for the error popup over the frozen frame, which keeps its scrim.
9. **The error of a stream that ran is a popup over its last frame** (the owner's decision of 2026-10-29, added after the work of Sprint 15).
   * The error is a popup inside the chrome layer, a `StreamDialog` like the conflict dialog, and no longer a full screen. It has the message, Try again (focused), Wake PC or Pair again where the failure offers them, Diagnostics and Close. A tap outside it does nothing. Back and the gamepad B button close it, which is Close, back to the Library. Every failure uses it, also those of a start that never ran, which stand on the background.
   * When a stream that ran fails, the popup stands on the last frame of the stream, frozen and blurred. One copy of the video is made, a quarter of its size like the copy of the menu, and no clock runs for it.
   * With no frame the popup stands on the user's background (decision 2): a failure before the first frame, the first-frame watchdog, and a copy that fails. While the copy is pending, which took about 0.2 to 0.3 s in the device rounds (see the measures below), the popup is on black with a solid panel and the background does not show, so it does not flash.
   * The copy is asked for at the top of `connectionTerminated` and of `stageFailed` in `Game`, from the thread that reports the end, before the connectivity test and the stop of the connection, because the decoder is released a fraction of a second later and a copy after that gets no data. It is asked for only after the first frame was shown, not for the end that the user's own Quit caused and not for a graceful end.
   * The frame is always behind the popup when a copy exists. It is blurred where the device can blur and the settings allow it (API 31 or later, no Transparency Off, no High contrast), with the blur of the menu glass at the viewer's transparency, and sharp otherwise. The panel is the glass of the menu over the blurred frame where the menu glass option is Blur, and the solid surface with Solid, Transparency Off, High contrast or below API 31. With the option on Solid the frame is still blurred, and only the panel is solid. Reduce motion and the battery saver are not asked, because the frame is still and copied once. The scrim stays over the frame, also where it is blurred (decision 5).
   * The copy is in memory only. It is let go when the activity is destroyed, which is also how Try again ends it, and a copy that ends after that is dropped. Nothing is saved, and the log says only that a copy was asked for with its size, how long it took, or why it failed.
   * Known limit: the frame is placed and scaled when the copy ends, so a rotation or a resize after the failure does not place it again.
10. **The text over the video glass keeps its contrast with a halo** (the owner's decision of 2026-10-29). It replaces the tint floor of 83 percent that the first version of the glass had, which the owner dropped.
    * The glass is the viewer's tint and blur and is never raised for the contrast: 40 percent at 8 dp at Standard, 85 percent at 48 dp at Reduced. Ink-3 and ink-4 are ink-2 on it, as on a background with a halo.
    * Every text on the glass has a halo that is 3 dp wide, blurred by 2 dp and 100 percent opaque, drawn in three passes: a stroke, a stroke of half the width, and the glyphs filled. The halo of a text on the background is 80 percent and one pass. The design system's `LocalTextHalo` sets it for a screen that stands over a picture it cannot measure, and every `Text` and `BackdropIcon` under it draws it. The label of a primary button, which has the accent fill, stands on its fill and has no halo.
    * The segmented controls of the menu sit on a container in the `surface` colour, in the colours of the look and with no halo, so that their labels never stand on the video.
    * The focus outline has a casing under the halo hook: a stroke 1.5 dp wider on each side, in the `page` colour at the opacity of the halo.

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

### Measurements of 2026-10-29

For decision 10, the lightest backdrop that any video can leave is a white frame under the glass of 40 percent, because a blur mixes pixels and no frame is lighter than white. A halo that keeps the text over it keeps it over every frame.

* **Without a halo**, over that glass, ink is 2.1:1 and ink-2 is 1.5:1.
* **With the halo**, ink, ink-2, accent-soft and danger-ink keep 4.5:1 on the ring of the halo, as drawn (`TextHaloTest`, the strings of the menu and of the popup) and in the model of the contrast tool, over a white frame and a busy bright one of squares of white and black, on a phone and on a TV, with the tool's margin of 2 percent (`VideoGlassHaloTest` in `design/accessibility/test_contrast.py`).
* **Why 100 percent in three passes.** The halo of the Library (80 percent, one pass) leaves the danger ink of the Quit row at 4.40:1 on a TV in the model, and 100 percent is the smallest candidate of the grid that keeps all four colours. As drawn, one stroke leaves a hole in the halo at a small shape, the dot of an i or a bullet: 3.7:1 for the red of Quit at the dot of its i. The stroke of half the width and the filled glyphs cover it.
* **Focus outline.** Against the lightest glass the accent-soft outline is 1.39:1 and the thick one, in ink, 2.1:1, where WCAG 1.4.11 asks 3:1. With the casing it keeps 3:1 (`FocusOverGlassTest`).
* **Segmented controls.** Ink-3 on the `surface` colour, and accent-soft over the accent tint laid on it, keep 4.5:1.

For decision 9, on the S22 (Disconnect from the Web UI of the host, Steam Big Picture, ENet error -1):

* A copy requested when `connectionTerminated` is first reported, before the stop of the connection, and one requested 100 ms later both got the frame (218 to 278 ms each, 77.6 percent of the pixels not black).
* The decoder stopped 150 ms after the first request and was released 260 ms after it. A copy at 500 ms and one at 2 s, after the Failed phase, returned `ERROR_SOURCE_NO_DATA`. That is why the request is at the top of `connectionTerminated`, and not at the Failed phase.

On the TV (Google TV Streamer, 2026-10-29), the drop was made by blocking the networking of the app. The request came 16 ms before the line "Connection terminated: -1", the stop of the connection took 231 ms, and the copy ended 314 ms after the request, after the stop of the decoder, and worked. The popup was read on the screen by its text. The frame behind it was not captured: no round has recorded how it looks.

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
* Bad, because the text contrast over the blurred video is not yet measured (decision 4). *Amended on 2026-10-29:* it is measured and kept with the halo (decision 10).
* Good, because the error of a stream that ran shows where the stream stopped, with one copy and no clock, and the popup has the look and the focus rules of the Quit popup.
* Bad, because the copy of the last frame has to be taken before the decoder is released, so the request is at the top of `connectionTerminated`, on the connection thread, before the connectivity test.
* Bad, because a rotation or a resize after the failure leaves the frame at its old place.
* Bad, because the pending state of the copy has no time limit. It relies on `PixelCopy` answering every request it accepts. A copy that never ended would leave the popup on black under its scrim, with a solid panel, and every button works.
* Bad, because the panel of the popup is solid, and the popup on black, for the 0.2 to 0.3 s that the copy took in the device rounds, and turns to glass when the frame arrives.
* Bad, because no round has recorded how the frozen frame looks on a device, or its colour.
* Bad, because with Thicker focus outline 0.5 dp of the casing of the focus outline is cut by the scroll clip of the side panel at its edges. The ring and 1 dp of the casing stay whole, and the 3:1 holds.
* Bad, because the faint tail of the 3 dp halo is cut at the 2 dp bleed of the content layer on the title and body of the popup. It is cosmetic, and the contrast tests do not change.

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

* Good, because all the screens follow one rule, with no difference between them. *Amended on 2026-10-29:* the error of a stream that ran stands on its last frame, which is one difference (decision 9).
* Bad, because the Disconnecting, conflict and error screens also show the background, and so can start the clip.

### Error of a stream that ran: the full-screen error on the background (today)

* Good, because it needs no copy.
* Bad, because the stream is gone from the screen at the moment it stopped, and the error is not a popup like the other two dialogs of the chrome.

### Error of a stream that ran: a popup on the background

* Good, because it needs no copy.
* Bad, because the owner preferred the last frame behind it. It stays the rule where there is no frame.

### Error of a stream that ran: a popup on the last frame, frozen and blurred (chosen)

* Good, because the user sees where the stream stopped, with one copy.
* Good, because it is the glass and the focus rules of the Quit popup.
* Bad, because the copy must be taken at the top of `connectionTerminated`, before the decoder is released.

## More Information

* Amends [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 (never in a stream) and [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decisions 10 (the stream has no glass) and 13 (the halo is never drawn in the stream).
* Stream-only differences from [0057](0057-glass-surfaces-and-the-accessibility-settings.md): decisions 5 and 8. They stay in force for the Library.
* Background intensity: [0058](0058-appearance-and-reading-options.md).
* The owner's comparison on 2026-10-28 used variants behind a hidden switch, built on the spike branch `spike/s15-stream-glass`. That branch was never merged.
* Roadmap: [Sprint 15](../roadmap.md#sprint-15-the-stream-screens-and-glass).
* Amended on 2026-10-29, after the work of Sprint 15 (issues #97 to #105):
  * decisions 9 and 10 are new: the error popup over the last frame, and the halo that keeps the contrast over the video glass, with the measures above;
  * decision 1 says that the frozen copy of the error popup is the one frozen copy there is;
  * decision 2 says that the failure of a stream that ran stands on the frame and not on the background;
  * decision 3 says what Transparency Off, High contrast, API 31, Reduce motion and the battery saver do to the error popup;
  * decision 4 says that the halo is the choice and the tint floor is dropped, and that text on a panel has no halo of its own;
  * decision 5 says that the conflict dialog and the error popup are panels of the chrome layer that blur the background alone, and where they do, and that the popup over the frame keeps its scrim;
  * decision 6 says when the decoder counts as held, and that every style is held still behind a dialog where the host blurs;
  * decision 8 and the consequences carry the matching notes.
* The first wording of each amended decision stays above, so that the history reads. A change is recorded in a dated note and the decision keeps its first wording, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) and [0052](0052-structure-the-phase-2-ui.md) do.
* `StreamChromeRoot`, `StreamDialog`, `OverFrozenFrame`, `FrozenFrame`, `videoGlassSpec` and `frozenFrameLook` of `:borealis:stream` are where the decisions live.
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
