---
status: accepted
date: 2026-10-19
decision-makers: João Pedro Magalhães
---

# 0056 Add an animated background and the Appearance group

## Context and Problem Statement

The owner wanted a simple, discreet animated background for the app (roadmap, issue #30). Until Sprint 9 every Library and Settings screen painted its own flat page colour. How should the background be drawn and played, where is it chosen, and where must it never appear?

## Decision Drivers

* It must be discreet and must not hurt the contrast of the text over it.
* It must run on a TV (the Google TV Streamer) and on a phone without stutter.
* A stream owns the decoder, the CPU and the screen, so the background must not run in one.
* The system's reduced motion and the battery saver must be respected.
* Few dependencies, and every bundled clip needs a known origin.
* A stored choice must survive reordering or adding styles.

## Considered Options

* Styles drawn in Compose only.
* Video clips only, played with the platform `MediaPlayer`.
* Two styles drawn in Compose and four video clips played with Media3 ExoPlayer, chosen in an Appearance group of Settings.

The owner chose the styles and the default on 2026-10-18.

## Decision Outcome

Chosen option: "Two styles drawn in Compose and four video clips played with Media3 ExoPlayer, chosen in an Appearance group of Settings", because the drawn styles cost no decoder and no file, the clips give richer looks that code cannot, and ExoPlayer loops a clip without a visible break.

Decision details:

1. **Seven styles** (the names are working names, in English only). The owner's choice of default is Aurora curtains.
   * Aurora curtains (1a, the default) and Soft glow (1b) are drawn in Compose.
   * Northern lights (2a), Light mist (2b), Flowing light (3a) and Starfield (3b) are video clips.
   * Off is the plain page colour.
2. **Compose versus video.**
   * The two Compose styles are drawn in the draw phase at about 30 frames a second, in a loop of 20 seconds that has no seam, and they recompose nothing. They use the design tokens `page`, `accent-strong` and `accent`, and one teal that has no token.
   * A clip is 1920 x 1080 H.264 (High profile, level 4.1, 4 reference frames, no B-frames), silent, 16:9 and a seamless loop. The screen cuts it from the centre to fill any window. A still of its first frame covers the surface until the player draws its first frame.
   * Compose is the default, so the common case runs no player.
3. **Media3 ExoPlayer on a `SurfaceView`** (a `TextureView` since Sprint 10, see the note at the end).
   * There is one player at a time. It is made when the Library starts, plays while the Library is resumed, and is released when the Library stops, when the style changes and when the background leaves the composition.
   * The player is muted and its audio track is switched off, so no audio output opens.
   * A player that fails is released and the still comes back. The log says so once, with the name of the clip and of the error, and the clip is not tried again until the style changes.
4. **Where it shows.** The Library shell draws one background behind the rail and every screen: Hosts, Add host, Pairing, Apps, Settings, Privacy and diagnostics and the Audit trail, on a phone, a TV and a handheld. The screens have no page of their own. A navigation removes the old screen at once and fades the new one in over 150 ms, so two screens are never drawn one over the other.
5. **Never in a stream.** The stream is another activity, with its own screens, and the background belongs to the Library activity only. Starting a stream stops the Library, which releases the player, so no decoder of the background runs during a stream.
6. **Reduced motion and the battery saver.** When the system removes animations, or while the battery saver is on, a Compose style is one still frame and a video style is its still. Nothing plays and no player is made. The saver is followed live: turning it on releases the player, and turning it off plays again.
7. **The Appearance group and its storage.**
   * Settings gets a group, Appearance, after Advanced and before Privacy and diagnostics. It held one row, Background, which opens a list of the seven styles in the order of decision 1. [0057](0057-glass-surfaces-and-the-accessibility-settings.md) adds Glass tint and Glass blur.
   * The choice is the setting `borealis_background` in the Borealis DataStore file, `borealis_settings`. It is stored as a stable string id (`aurora_curtains`, `soft_glow`, `northern_lights`, `light_mist`, `flowing_light`, `starfield` or `off`), never as the ordinal of a style. A stored value that no style has reads as the default.
   * The Library follows the setting live, so a change shows behind Settings at once, with no restart.
   * The setting has no legacy SharedPreferences value to move, unlike the performance overlay, so it starts in DataStore, as [0011](0011-datastore-room-and-keystore-for-new-persistence.md) sets for new settings. The DataStore directory is excluded from backups, so the choice is not backed up.
8. **Provenance.** The clips and the scripts that made them are in [design/backgrounds](../../design/backgrounds/README.md).
   * 2a and 2b were generated on the owner's ElevenLabs account on 2026-10-18 (start frames from GPT Image 2.5, videos from Kling 3.0 Pro), and their loop seams were closed with `loopfade.py`. The owner confirmed on 2026-10-19 that their ElevenLabs plan allows bundling the clips in the app.
   * 3a and 3b are generated in code by `gen_loops.py`.
   * The masters of 2a and 2b are not committed.
   * [NOTICE](../../NOTICE) lists Media3 and Guava.

### The dependency: Media3 ExoPlayer 1.11.1

This is the record that the [dependency policy](../policies/dependencies.md#adding-a-dependency) asks for.

* **Reason:** gapless looping and hardware decoding of the background clips. The platform `MediaPlayer` loops with a hiccup. Code alone cannot make the video styles.
* **Licence:** Apache-2.0, on the allowlist. `licensee` passes without a change. Guava 33.3.1-android and failureaccess come with it, also Apache-2.0.
* **Maintenance:** Google maintains it, as part of AndroidX. The release in use is 1.11.1. The version is pinned in the version catalog (`androidx-media3`), and Dependabot updates it.
* **Footprint:**
  * The seven Media3 modules that come in (common, container, database, datasource, decoder, extractor and exoplayer) hold no native code. Their minSdk is 23, against the app's 28.
  * The modules declare the permissions `ACCESS_NETWORK_STATE` and `WAKE_LOCK`. The app declares both already, so the merged permission list does not change.
  * The four clips add about 3.9 MB to the APK.
  * The code adds an estimated 1 to 2 MB of dex to a release build, which is shrunk. The debug build, which is not shrunk, grows by about 6 to 7 MB of dex. These are estimates, not measurements of a release APK.
* **Alternatives considered:** the platform `MediaPlayer`, which loops with a hiccup, and Compose-only styles, which cannot give the clips' looks.

### Consequences

* Good, because the default style needs no player, no decoder and no file.
* Good, because one input, the `BackgroundStyle`, drives the whole background, and the Appearance setting stores it by a stable id.
* Good, because a stream never pays for the background.
* Good, because reduced motion and the battery saver are respected.
* Bad, because the APK grows by the clips and by Media3 with Guava, and one more dependency must be kept up to date.
* Bad, because text contrast over the background must be reviewed. Over the brightest bands of the aurora, the small ink-3 and ink-4 texts fall to 3.0 to 3.7:1. The review was done in Sprint 10 ([0057](0057-glass-surfaces-and-the-accessibility-settings.md), [roadmap](../roadmap.md#sprint-10-ux-and-accessibility)).
* Bad, because the right to bundle the generated clips depends on the terms of the owner's ElevenLabs plan. The owner confirmed it on 2026-10-19.

## Pros and Cons of the Options

### Styles drawn in Compose only

* Good, because it needs no dependency, no clip and no decoder.
* Good, because the styles follow the design tokens.
* Bad, because the richer looks of the clips are out of reach of code that is simple and discreet.

### Video clips only, played with the platform `MediaPlayer`

* Good, because it adds no dependency.
* Bad, because a loop restarts with a hiccup, which a background must not show.
* Bad, because every style would hold a decoder, even the default one.

### Two styles drawn in Compose and four video clips played with Media3 ExoPlayer, chosen in an Appearance group of Settings (chosen)

* Good, because the default costs no decoder and the clips loop cleanly.
* Good, because the user chooses, and Off is one of the choices.
* Bad, because it adds Media3 and Guava and about 3.9 MB of clips.

## More Information

* Amends [0055](0055-settings-storage-and-the-phase-2-settings-screen.md): the DataStore file holds the background setting too, not only the two logging switches.
* Amended on 2026-10-20 by [0057](0057-glass-surfaces-and-the-accessibility-settings.md), and again in Sprint 10:
  * the clips now play in a `TextureView` (ADR 0057), not a `SurfaceView`, so the glass can see them;
  * on a TV, the drawn background runs at 24 frames a second while the blur is drawn (API 31 or later, with the transparency Standard or Reduced). Without the blur it keeps its 30 frames a second, and a clip plays at its own rate;
  * Appearance has three rows now: Background, Glass tint and Glass blur;
  * Reduce motion is the app's own setting as well (Accessibility, [0057](0057-glass-surfaces-and-the-accessibility-settings.md)): Follow system, On or Off. Where this ADR says "the system removes animations", read "Reduce motion removes them", which follows the system by default;
  * Sprint 11 will show the background on the stream's Starting screen. (moved to Sprint 15 on 2026-10-21; see the roadmap)
* Amended on 2026-10-28 by [0061](0061-glass-and-the-animated-background-in-the-stream.md): decision 5 (never in a stream) no longer holds for the screens outside the game. The background shows there, and not in the game.
* Related: [0011](0011-datastore-room-and-keystore-for-new-persistence.md) (new settings start in DataStore), [0052](0052-structure-the-phase-2-ui.md) (Phase 2 UI, dark only) and [0044](0044-design-tokens-pipeline.md) (the tokens that the drawn styles use).
* Use case: [UC-009, change settings](../use-cases/0009-change-settings.md).
* Provenance of the clips: [design/backgrounds](../../design/backgrounds/README.md). Roadmap: [Sprint 9](../roadmap.md#sprint-9-ux-polish-and-the-handheld).
* Related: [0058](0058-appearance-and-reading-options.md) adds the font, accent and background intensity choices to the Appearance group.
