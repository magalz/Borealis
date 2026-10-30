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

For the popups over the game, the Quit popup and the error popup over the frozen frame (added on 2026-10-30, decision 11):

* The dialog scrim (the page at 72 percent) and a panel of the viewer's glass tint (40 percent at Standard), as the first version had.
* A light scrim (the page at 35 percent, the side panel's) and a firm panel (85 percent of the surface).

## Decision Outcome

Chosen option: "A live blurred copy of the video at 30 times a second, with Solid as the other choice, and the user's moving background on every screen outside the game", because it gives the stream the Library's look at a cost that the owner accepted after a live comparison on the TV and the S22 (the owner's [gate G2 of Sprint 15](https://github.com/magalz/Borealis/issues/99), 2026-10-28), and because one rule for every screen is simpler than a rule per screen. *Amended on 2026-10-29:* the choice holds. Decision 9 adds the error popup over the last frame of a stream that failed, and decision 10 records how the text over the glass keeps its contrast. Decisions 1 to 6 and 8 carry a note where the shipped code differs from their first wording. *Amended on 2026-10-30:* the choice holds, with three changes from the owner's acceptance test of Sprint 15 (decision 11). The background outside the game is the user's background as the Library shows it, with no option of its own, so "moving" is what the Library's rules give and not an option's value. The menu glass option is the Stream menu style, Blurred or Solid. The popups over the game are a firm panel under a light scrim. Decisions 1, 2, 3, 5, 6, 8, 9 and 10 carry a pointer.

Decision details:

1. **Two Appearance options in Settings**, next to Background.
   * **Menu glass in the stream:** Blur (the default) or Solid. Blur is a live copy of the video at a quarter of its size, blurred and refreshed 30 times a second. It is drawn behind the in-game menu, its side panel and the Quit popup. The copies stop when the menu closes and in picture-in-picture. Solid is today's surface.
   * There is no tint-only option, because the owner judged it a nightmare for accessibility. There is no frozen copy, because a frozen copy looks very strange when the video moves.
   * *Amended on 2026-10-29:* "no frozen copy" holds for the menu and its Quit popup, which show the live copy. The popup of the error of a stream that ran stands on one frozen copy, the last frame, which nothing moves behind (decision 9).
   * **Background outside the game:** Moving (the default), Still or Off. It is the user's background style (Settings, Background). Its intensity, from [0058](0058-appearance-and-reading-options.md), applies outside the game as it does in the Library.
   * *Amended on 2026-10-29:* the labels in Settings are Menu glass in the stream (Blur or Solid) and Background before and after the stream (Moving, Still or Off).
   * *Amended on 2026-10-30:* there is one option, not two (decision 11). The owner dropped Background before and after the stream in the acceptance test, and renamed the other one: its label is **Stream menu style** and its choices are **Blurred** (the default) or **Solid**. The stored key and ids are the same (`borealis_stream_menu_glass`: `blur` or `solid`). The screens outside the game have no option of their own: they follow the Background of the Library. A value that an earlier build stored under the dropped key (`borealis_stream_background`) is never read, and the DataStore file keeps it.
2. **Every screen outside the game** (Starting, the conflict, the error, Quitting and Disconnecting) shows that background, with no difference between the screens. No option chooses which screens get the background. The background goes when the phase becomes Streaming. The first frame can come before the connection reports that the stream started, so the cut follows the phase and not the stage.
   * *Amended on 2026-10-29:* the screens do differ in one case. The error of a stream that ran, once the copy of its last frame is asked for, stands on that frame and not on the background (decision 9). With no frame to show, it stands on the background like the others.
   * *Amended on 2026-10-30:* "that background" is the Background of the Library, drawn as the Library draws it: the style the user chose, at the strength of Background intensity, in the same `GlassHost`, so the style Off is the plain page and any other style moves where the Library's moves and stands still where the Library holds it still (Reduce motion, the battery saver, and an open dialog where the host blurs). The stream reads the style once, when it starts. A clip still shows its still while the stream holds the decoder (decision 6). See decision 11.
3. **The accessibility settings still win** ([0057](0057-glass-surfaces-and-the-accessibility-settings.md)).
   * Transparency Off and High contrast give a solid surface and no copy.
     *Amended on 2026-10-29:* this holds for the menu and its Quit popup. The error popup is the exception (decision 9): its one copy is made whatever the look, and with Transparency Off, High contrast or below API 31 the frame shows sharp under the scrim and the panel is solid.
   * Transparency Standard or Reduced gives the stream glass the tint and blur of that level. The final strength is set by the contrast check of decision 4.
   * Reduce motion and the battery saver turn the copy off, so the menu glass is solid, and keep the background still, whatever the options. The battery saver is followed live, as [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 6 does in the Library; it is not part of the look read at the start.
     *Amended on 2026-10-29:* neither turns off the copy behind the error popup, because that frame is still and copied once. They still keep the background still where the popup stands on it.
     *Amended on 2026-10-30:* "whatever the options" has no option to read now. Reduce motion and the battery saver hold the background still because the Library's host does, as in the Library (decision 11).
   * Below API 31 the stream glass is solid, with no `RenderEffect`. The owner allows only Blur or Solid, with no tint-only option. This differs from the Reduced tint that [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 5 gives the Library panels on API 28 to 30.
   * The stream reads the look once, when the stream starts, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 11 says. The transparency level and its tint and blur, and the two new options, are read the same way, so a change shows in the next stream.
     *Amended on 2026-10-30:* there is one new option, the Stream menu style (decision 1), and the background style of the Library. They are read the same way.
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
   * *Amended on 2026-10-30:* the scrim that stays is lighter (decision 11). Over the game, which is the Quit popup always, and the error popup while the last frame is behind it or its copy is pending, the scrim is the page colour at 35 percent, the one of the side panel, and not the dialog scrim (the page at 72 percent). It is the same around a solid panel. A popup with no copy, or with a copy that failed, is a dialog over the background and follows the bullets above: no scrim where the host blurs, the dialog scrim where it does not.
6. **Clip lifecycle.** A clip style plays only while no stream decoder is configured.
   * It plays from the start until the stream's decoder is configured. The clip is released before the decoder is configured.
   * It plays again after the stream's decoder has been released, when the connection has stopped.
   * While a decoder is configured (Quitting or Disconnecting before the stream has stopped, or an error screen while the decoder is still up), a clip style shows its still. A Compose style keeps moving.
   * *Amended on 2026-10-29:* the session holds the decoder from the first stage of the connection itself, which is the stage Preparing the display, so the clip is released a few stages before the decoder is configured. It plays again when the connection has stopped (`StreamSession.onConnectionStopped`), or at once where no connection ran.
   * *Amended on 2026-10-29:* under the conflict dialog and the error popup, where the host blurs the background (decision 5), every style is held still, a Compose style included. A Compose style moves under them only where the host does not blur: API 28 to 30, Transparency Off, High contrast. Reduce motion and the battery saver hold every style still anyway.
   * *Amended on 2026-10-30:* nothing changes here. With the option gone, the stream holds the background only in the two ways above, the clip's and the dialog's, and in the ways of the Library (Reduce motion, the battery saver). The stream calls the same `GlassHost` as the Library and passes only whether a clip may play (`clipsPlay`, off while the decoder is held), and the style Off while the frozen frame, or its black, covers the background (`OutsideGameBackground`).
7. **Frame pace.** The background outside the game follows [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 9 unchanged. On a TV it runs at 24 frames a second while a blur is drawn. The copy rate of 30 a second is separate, and it applies only to the menu glass in the Streaming phase.
8. **This amends** [0056](0056-add-an-animated-background-and-the-appearance-group.md) decision 5 and [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decisions 10 and 13. Two more rules of 0057 differ in the stream and stay in force for the Library: decision 5 (the Reduced tint below API 31, where the stream glass is solid) and decision 8 (the window blur of dialogs, where the stream's Quit popup uses the copy). *Amended on 2026-10-29:* decision 8 also differs for the error popup over the frozen frame, which keeps its scrim. *Amended on 2026-10-30:* the scrim that the Quit popup and that error popup keep is the light one of decision 11, the page at 35 percent. The dialogs over the background are as 0057 decision 8 has them, and the dialog scrim stays only where the host does not blur.
9. **The error of a stream that ran is a popup over its last frame** (the owner's decision of 2026-10-29, added after the work of Sprint 15).
   * The error is a popup inside the chrome layer, a `StreamDialog` like the conflict dialog, and no longer a full screen. It has the message, Try again (focused), Wake PC or Pair again where the failure offers them, Diagnostics and Close. A tap outside it does nothing. Back and the gamepad B button close it, which is Close, back to the Library. Every failure uses it, also those of a start that never ran, which stand on the background.
   * When a stream that ran fails, the popup stands on the last frame of the stream, frozen and blurred. One copy of the video is made, a quarter of its size like the copy of the menu, and no clock runs for it.
   * With no frame the popup stands on the user's background (decision 2): a failure before the first frame, the first-frame watchdog, and a copy that fails. While the copy is pending, which took about 0.2 to 0.3 s in the device rounds (see the measures below), the popup is on black with a solid panel and the background does not show, so it does not flash.
   * The copy is asked for at the top of `connectionTerminated` and of `stageFailed` in `Game`, from the thread that reports the end, before the connectivity test and the stop of the connection, because the decoder is released a fraction of a second later and a copy after that gets no data. It is asked for only after the first frame was shown, not for the end that the user's own Quit caused and not for a graceful end.
   * The frame is always behind the popup when a copy exists. It is blurred where the device can blur and the settings allow it (API 31 or later, no Transparency Off, no High contrast), with the blur of the menu glass at the viewer's transparency, and sharp otherwise. The panel is the glass of the menu over the blurred frame where the menu glass option is Blur, and the solid surface with Solid, Transparency Off, High contrast or below API 31. With the option on Solid the frame is still blurred, and only the panel is solid. Reduce motion and the battery saver are not asked, because the frame is still and copied once. The scrim stays over the frame, also where it is blurred (decision 5).
   * The copy is in memory only. It is let go when the activity is destroyed, which is also how Try again ends it, and a copy that ends after that is dropped. Nothing is saved, and the log says only that a copy was asked for with its size, how long it took, or why it failed.
   * Known limit: the frame is placed and scaled when the copy ends, so a rotation or a resize after the failure does not place it again.
   * *Amended on 2026-10-30:* the "menu glass option" above is the Stream menu style, Blurred or Solid. While the frame, or its black during the pending copy, is behind the popup (`FrozenFrame.coversBackground`), the scrim is the page at 35 percent and a glass panel is 85 percent of the surface at the blur of the look (decision 11); a solid panel is the `surface` colour under the same scrim. With no copy asked for, or with a copy that failed, the popup is a dialog over the background and draws as it does with no frozen frame. The copy takes about 0.1 to 0.3 s (76 ms on the S22, 314 ms on the TV, in the rounds of 2026-10-29; the measurements of 2026-10-30), so "0.2 to 0.3 s" above is a range of the first rounds.
10. **The text over the video glass keeps its contrast with a halo** (the owner's decision of 2026-10-29). It replaces the tint floor of 83 percent that the first version of the glass had, which the owner dropped.
    * The glass is the viewer's tint and blur and is never raised for the contrast: 40 percent at 8 dp at Standard, 85 percent at 48 dp at Reduced. Ink-3 and ink-4 are ink-2 on it, as on a background with a halo.
      *Amended on 2026-10-30:* the panel of a popup over the game is the one exception to "never raised". It is 85 percent of the surface at the blur of the look, whatever tint the viewer chose for the side panel, which keeps its own tint (decision 11). Ink-3 and ink-4 are still ink-2 on it, and the halo stays.
    * Every text on the glass has a halo that is 3 dp wide, blurred by 2 dp and 100 percent opaque, drawn in three passes: a stroke, a stroke of half the width, and the glyphs filled. The halo of a text on the background is 80 percent and one pass. The design system's `LocalTextHalo` sets it for a screen that stands over a picture it cannot measure, and every `Text` and `BackdropIcon` under it draws it. The label of a primary button, which has the accent fill, stands on its fill and has no halo.
    * The segmented controls of the menu sit on a container in the `surface` colour, in the colours of the look and with no halo, so that their labels never stand on the video.
    * The focus outline has a casing under the halo hook: a stroke 1.5 dp wider on each side, in the `page` colour at the opacity of the halo.
11. **The owner's acceptance test of 2026-10-30 changed three things** (added after the work of Sprint 15). The owner ran the test by hand on the TV and the S22, on the build of `1455bcf0a`. Every step passed except these three decisions and two partial results, U6 (a second device) and U7 (the keyboard and the mouse), which [runbook section 20](../development/device-validation.md#20-results-sprint-15) records.
    * **The popups over the game are a firm panel under a light scrim.** In the test the Quit popup (step U1) looked like a hole: its panel of 40 percent glass was almost transparent, and the scrim of 72 percent turned the background black, the opposite of what the owner expected. Asked separately, the owner chose to change the error popup over the frozen frame the same way, because it shares `StreamDialog`. Now the scrim of a popup over the game is the page colour at 35 percent (`STREAM_SCRIM_ALPHA` in the design system, the alpha of the scrim of the side panel), so the game stays visible around it. The panel is 85 percent of the surface (`POPUP_TINT_PERCENT`, the Reduced tint of [0057](0057-glass-surfaces-and-the-accessibility-settings.md)) over the video blurred by the blur of the look (8 dp at Standard, 48 dp at Reduced), whatever tint the viewer chose for the side panel. Where the rules make the panel solid (Stream menu style Solid, Transparency Off, High contrast, below API 31, and Reduce motion and the battery saver for the live copy), it is the `surface` colour, and the scrim is the same 35 percent.
      * A popup is over the game while the game is behind it: the Quit popup always, and the error popup while the frozen frame is behind it or its copy is pending (`FrozenFrame.coversBackground`, which `StreamChromeRoot` reads for `showsBackground`). The signal is `LocalPopupOverGame`: `QuitConfirmation` and `OverFrozenFrame` provide it, and `StreamDialog` reads it.
      * Unchanged: the side panel of the menu, with its glass and its own scrim, the halo and the casing of the focus outline, the tap outside and Back, and the dialogs over the background outside the game, which have the glass of [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 8 (decision 5 above): the conflict dialog, and the error popup with no copy or with a copy that failed.
      * The halo stays on the text of the popup, although the 85 percent panel keeps the contrast without it. The owner may drop it later. The numbers are in the measurements of 2026-10-30.
    * **There is no option for the background outside the game.** The owner dropped Background before and after the stream. The screens outside the game follow the Background of the Library: the style, its intensity and its Off, Reduce motion, the battery saver and an open dialog where the host blurs. A clip still shows its still while the stream holds the decoder (decision 6), and every style is held still under the conflict dialog and the error popup where the host blurs (decision 6, the second note). The stream calls the Library's `GlassHost`. The option's key, `borealis_stream_background`, is no longer in the settings catalog; a value stored under it is never read, and it stays in the DataStore file.
    * **The menu glass option is the Stream menu style: Blurred or Solid.** It keeps its key (`borealis_stream_menu_glass`), its ids (`blur`, `solid`) and its default. Only the texts changed: the label of the row, its summary and the label of its first choice.

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

*Amended on 2026-10-30:* the owner saw the Quit popup (step U1) and the error popup over the frozen frame (step U5) in the acceptance test ([runbook 3.30](../development/device-validation.md#330-owners-20-minute-uat-sprint-15)), on the build of `1455bcf0a`. U5 passed: the popup over the frame and Back were right. Only the Quit popup looked like a hole (U1), and the owner chose to change the error popup the same way (decision 11). No capture of the frame was made, and no device round has seen the look after decision 11.

### Measurements of 2026-10-30

For decision 11, the lightest frame that a video can leave under the glass is white, as above. The panel of a popup is the `surface` colour at 85 percent over it, which is (55, 58, 61) in the values of the picture. The model of the contrast tool is `PopupGlassContrastTest` in `design/accessibility/test_contrast.py`, with the frames of `VideoGlassHaloTest`. What is drawn is read by `PopupOverGameTest` (the scrim, the panel over a white frame, and every text colour on it).

* **Without a halo**, the worst ratios on that panel are ink 9.50:1, ink-2 6.74:1, accent-soft 6.20:1 and danger-ink 5.01:1. Every text keeps 4.5:1 on its own, and the halo is kept by the owner's decision of 2026-10-29, not for the ratio.
* **With the halo of decision 10**, in the model, over a white frame and a busy bright one, the worst ratios are ink 14.83:1, ink-2 10.52:1, accent-soft 9.68:1 and danger-ink 7.83:1.
* **Ink-3** is 3.56:1 on that panel over white, short of 4.5:1, so ink-3 and ink-4 are still ink-2 on the glass.
* **The copy of the last frame** took 76 ms on the S22, requested 8 ms before "Connection terminated: -1" (the run for [NortheBridge/luminalshine#201](https://github.com/NortheBridge/luminalshine/issues/201), 2026-10-29, when a second client's Quit stopped the first client's session), and 314 ms on the TV (the drop of 2026-10-29 above). The copy takes about 0.1 to 0.3 s (76 ms on the S22, 314 ms on the TV), and the 0.2 to 0.3 s of decision 9 is only part of that.

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
  *Amended on 2026-10-30:* the copy takes about 0.1 to 0.3 s (76 ms on the S22, 314 ms on the TV), so the black and the solid panel last that long, not only 0.2 to 0.3 s.
* Bad, because no round has recorded how the frozen frame looks on a device, or its colour.
  *Amended on 2026-10-30:* the owner has seen the popup over the frozen frame (step U5), and found the popup and Back right. The colour of the frame was not recorded on its own. The new look of decision 11 has not been seen on a device yet.
* Bad, because with Thicker focus outline 0.5 dp of the casing of the focus outline is cut by the scroll clip of the side panel at its edges. The ring and 1 dp of the casing stay whole, and the 3:1 holds.
* Bad, because the faint tail of the 3 dp halo is cut at the 2 dp bleed of the content layer on the title and body of the popup. It is cosmetic, and the contrast tests do not change.
* Good, because the game stays visible around a popup, and the panel is the firm element: every text on it keeps 4.5:1 over the lightest frame on its own colours (decision 11).
* Good, because the screens outside the game follow the Library with no rule of their own, so the stream has one option fewer to explain, store and test.
* Bad, because the popups over the game differ from the side panel (40 percent at Standard) and from [0057](0057-glass-surfaces-and-the-accessibility-settings.md) decision 8, one more difference to keep.
* Bad, because the dropped option leaves its key in the DataStore file of a device that had stored it. Nothing reads it, and no release has had the option, so only debug builds of Sprint 15 can hold one.
* Bad, because the Quit popup and the error popup over the frame in their new look, and the screens outside the game that follow the Library, have no device round yet: only the Appearance rows were looked at on the TV.

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
  *Amended on 2026-10-30:* there is no such option now. A still is what the Library gives under Reduce motion, the battery saver and an open dialog, and the stream gives the same.

### Background outside the game: the user's style, moving (chosen)

* Good, because the TV measures show no measurable start cost.
* Good, because the clip is released before the stream's decoder is configured, with 0 decoder errors.
* Bad, because the clip's decoder runs during the start on the TV, which the [roadmap](../roadmap.md#sprint-15-the-stream-screens-and-glass) lists as a risk.
  *Amended on 2026-10-30:* the choice holds as the user's style as the Library shows it (decision 11). It is not an option with a value of its own, so "moving", "still" and "off" are what the style, Reduce motion and the Library's rules give. The risk is answered by decision 6.

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

### Popups over the game: the dialog scrim and the glass tint of the menu panel (the first version)

* Good, because it is the same scrim as the dialogs everywhere, and the panel is the same glass as the side panel.
* Bad, because in the owner's acceptance test the Quit popup looked like a hole: its panel of 40 percent glass was almost transparent, and the scrim of 72 percent turned the background black.

### Popups over the game: a light scrim and a firm panel (chosen)

* Good, because the game stays visible around the popup, and the scrim is the one of the side panel.
* Good, because the panel is the firm element. At 85 percent every text keeps 4.5:1 over the lightest frame on its own colours.
* Bad, because it is a third look for a surface over the video, next to the side panel and the dialogs over the background.

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
* Amended on 2026-10-30, after the owner's acceptance test of Sprint 15 ([runbook 3.30](../development/device-validation.md#330-owners-20-minute-uat-sprint-15), results in [section 20](../development/device-validation.md#20-results-sprint-15)):
  * decision 11 is new: the popups over the game are a firm panel (85 percent) under a light scrim (the page at 35 percent), there is no option for the background outside the game, and the menu glass option is the Stream menu style (Blurred or Solid), with the measures above;
  * decisions 1 and 2 say that there is one option, and that the background is the Library's;
  * decision 3 says that the stream reads one option and the Library's background style;
  * decision 5 and decision 8 say that the scrim that stays over the game is the light one, and that a popup with no frame is a dialog over the background;
  * decision 6 says that the clip's hold and the dialog's are all the stream adds to the Library's rules;
  * decision 9 says when the error popup is over the game, and what the copy takes;
  * decision 10 says that the panel of a popup is the one exception to "never raised", and that the halo stays;
  * the measurements, the consequences and the pros and cons carry the matching notes.
* The first wording of each amended decision stays above, so that the history reads. A change is recorded in a dated note and the decision keeps its first wording, as [0057](0057-glass-surfaces-and-the-accessibility-settings.md) and [0052](0052-structure-the-phase-2-ui.md) do.
* `StreamChromeRoot`, `StreamDialog`, `OverFrozenFrame`, `FrozenFrame`, `OutsideGameBackground`, `LocalPopupOverGame`, `POPUP_TINT_PERCENT`, `videoGlassSpec` and `frozenFrameLook` of `:borealis:stream`, and `STREAM_SCRIM_ALPHA` of `:borealis:designsystem`, are where the decisions live.
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
