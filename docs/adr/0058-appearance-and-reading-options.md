---
status: accepted
date: 2026-10-21
decision-makers: João Pedro Magalhães
---

# 0058 Add the appearance and reading options

## Context and Problem Statement

Sprint 11 adds options for how the app looks and reads: the font, the accent colour, how strong
the background is, and the spacing of the text. Each option must keep WCAG 2.2 level AA on every
screen, and it must run on the Google TV Streamer. How should the options be chosen, which fonts
and accents are offered, and what does each one change?

## Decision Drivers

* The target is WCAG 2.2 level AA on every screen, measured with the tool in `design/accessibility/`
  and its method in [docs/ux/accessibility.md](../ux/accessibility.md).
* Reading research does not agree on one font for everyone, and it does not show that dyslexia
  fonts help in controlled studies. Readers differ, so the app offers a choice.
* A TV is read from about 3 m, so its text must be large enough.
* Fonts, colours and text spacing must not change the layouts that users rely on. A layout that does
  not fit at a larger spacing must be fixed, not hidden.
* Few dependencies. Fonts are bundled from their official releases, for privacy and offline use.
* The licences of all bundled fonts must be recorded and shown in the app.

## Considered Options

* Keep one font, one accent and one text spacing, with no choice.
* Offer a choice of fonts, each for an accessibility purpose.
* Offer OpenDyslexic as one of the fonts.
* Widen the letter spacing only, to make the text wider.
* Widen the letter spacing and the word spacing, through one text component.
* Offer a choice of accent colours with fixed backgrounds.
* Offer a density setting for the cards.

## Decision Outcome

Chosen option: "Offer a choice of fonts, each for an accessibility purpose, with the text widened
through one text component", because it lets each reader choose a font and spacing that suits
them, the measurements show that the contrast holds, and the cost is a set of bundled fonts and one
design-system text component.

Decision details:

1. **TV text size.**
   * On a TV, body text goes from 15 to 16 sp, and the smallest text from 12 to 13 sp.
   * The secondary text on the rail and the secondary size stay at 14.5 sp, and the title at 24 sp.
     The handheld keeps 15 sp and 12 sp.
   * The entries of the TV Settings groups get 3 dp of padding instead of 4 dp, so the 11 groups fit
     in 540 dp without scrolling.
   * The shared text halo (stroke 3 dp, blur 2 dp, 80 %) keeps every font at 4.5:1 or more at the new
     sizes. The lowest case over the fonts is 5.07:1 (Atkinson Hyperlegible Mono, `ink-2`, body text
     on a TV over Northern lights). See [Halo per font](../ux/accessibility.md#halo-per-font-sprint-11).
   * This change is merged in the phase (issue #47).

2. **Font choice.** Every font in the choice has an accessibility purpose. The choices are:
   * IBM Plex Sans (the default);
   * Atkinson Hyperlegible Next;
   * Luciole;
   * Lexend;
   * Andika;
   * B612;
   * the system font.

   * Each choice shows its name in its own font, and a purpose line under it. The purpose lines are:
     * IBM Plex Sans: "Default. Clear at small sizes, covers many languages."
     * Atkinson Hyperlegible Next: "Made for low vision: letters that look alike are drawn to differ."
     * Luciole: "Made with readers with low vision and tested in a published study."
     * Lexend: "Wider spacing built into the letters, for text that feels crowded."
     * Andika: "Made for new readers: mirror letters such as b and d look different."
     * B612: "Made for cockpit screens: quick to read at a glance and from a distance."
     * System font: "Uses your device's font."
   * The codes (the PIN, addresses, keyboard keys, chips and the performance overlay) use the monospaced
     companion of the font where one exists: IBM Plex Mono, Atkinson Hyperlegible Mono or B612 Mono.
     Otherwise they use IBM Plex Mono.
   * Each font is scaled so that its x-height matches that of IBM Plex Sans. The scales are Atkinson
     Hyperlegible Next 1.04, Luciole 0.95, Lexend 0.98, Andika 1.03 and B612 0.92. A scaled size never
     goes below the minimum sizes of the tokens, so the scale cannot make text smaller than today's
     minimum.
   * Andika ships Regular and SemiBold only. Luciole and B612 ship Regular and Bold.
   * All fonts are bundled from their official releases, with no subsetting or other change. No font
     is downloaded at run time.
   * The licences screen shows the licence of every font.

3. **OpenDyslexic was considered and rejected.**
   * Controlled studies show no benefit (see More Information).
   * The owner judged that Andika draws mirror and look-alike letters apart at least as well, and that
     it reads better.
   * OpenDyslexic was also the widest font. It failed 27 of 87 fit tests at the default spacing.

4. **Text spacing.** The levels are:
   * **Normal:** the spacing of the design.
   * **Wide:** letter spacing 0.12 em, word spacing 0.16 em and line height 1.5. These are the minimums
     of WCAG 1.4.12.
   * **Wider:** letter spacing 0.18 em and word spacing 0.5 em. The spike found that 0.35 em for the
     word spacing fits about as well. See Open points.

   * **How:** the word spacing goes through one design-system `Text`. It adds the letter spacing of
     each space as a span of the annotated string.
     * The semantics text, which TalkBack reads, stays the original string.
     * A detekt `ForbiddenImport` keeps Material's `Text` out of the code.
   * **Rejected:**
     * letter spacing alone, which widens the spaces only as much as the letters;
     * hair spaces or other space characters in the string, which change what TalkBack reads and failed
       715 tests;
     * `Paint.setWordSpacing`, which Compose cannot reach.
   * **Layout work:** the layouts that do not fit at Wide or Wider are fixed as part of this work. The
     spike found 22 fit failures at Wide and 39 at Wider with 0.5 em. A test keeps the word gap clearly
     larger than the letter gap in every font.

5. **Accent colour.**
   * **The set:** violet (today's colour, the default), teal `#4BECD0` and white `#EFF3FF`. The role
     values are in [design/accessibility/accents.json](../../design/accessibility/accents.json).
   * **Backgrounds:** the drawn backgrounds keep the fixed Borealis violet, whatever the accent. So the
     glass contrast table stays valid. The glass floor uses the colours of the chosen accent.
   * **Contrast fixes:**
     * selected labels on glass get a role that keeps 4.5:1. Today's violet is short of 4.5:1 over four
       of the six glass styles at the default, down to 3.21:1 over Flowing light;
     * the text of the snackbar action gets its own role. Today it uses `accent-strong`, which the new
       accents cannot use as text.
   * **White** marks a selection with an indicator bar on the segment, the rail item and the Settings
     group list. Its colour alone is too close to the text colour to show a selection.
   * **Icon and status:** the green of the icon becomes the teal. The Online status gets a glow or a
     drop shadow, so that it stands apart from the teal accent.
   * **Evidence:** the measurements are in
     [Accent candidates](../ux/accessibility.md#accent-candidates-sprint-11), from the `accents` command
     of the contrast tool.

6. **Licence policy.**
   * Luciole is CC BY 4.0. The allowlist of [the dependency policy](../policies/dependencies.md) does not
     include it.
   * The owner allows CC-BY-4.0 for fonts only, with the credit shown in the app and in NOTICE. The
     allowlist has a row for it that cites this ADR.
   * The reserved font names are recorded. Lexend's reserved name is "RevReading Lexend". Andika's are
     "Andika" and "SIL".
   * B612 is a trademark of the Eclipse Foundation. The font is OFL 1.1 and also EPL 2.0 and EDL 1.0.
   * The five items of the dependency policy for each font are in
     [More Information](#more-information).

7. **Card density was considered and rejected.** Every layout stays Comfortable. In the spike, the
   Compact variants cut the host detail line at the largest text size. With 4 columns on a phone, they
   also cut the Running badge.

8. **Background intensity.**
   * The options are 100, 75, 50 and 25 %.
   * A veil of the page colour lowers the brightness of the background. High contrast draws a dim of its
     own, which is 0.85.
   * The veil only darkens, so the glass floors and the glass table stay valid.
   * With High contrast on, its 0.85 dim wins over the veil.

9. **About links on a TV.** On a TV, an About link opens a dialog with the URL and a QR code. This reuses
   ZXing, which is already used by Send to phone (ADR 0051). Phones and handhelds open the browser.
   Issue #53 tracks this.

10. **Bold text.** Compose applies the system's Bold text setting (`fontWeightAdjustment`) itself. With it
    on, the three weights of a font collapse into the heaviest one, as the system does. The app does not
    change this behaviour.

### Consequences

* Good, because each reader can choose a font, a spacing and an accent that suits them.
* Good, because the text keeps 4.5:1 with the halo, and 7:1 under High contrast, in every font (see
  [Halo per font](../ux/accessibility.md#halo-per-font-sprint-11)).
* Good, because the word spacing is applied in one component, and the semantics text stays the original.
* Good, because the fonts are bundled, so the app works offline and makes no font request.
* Good, because the licences of the fonts are recorded, and the credit is shown in the app.
* Bad, because the bundled fonts add about 2.2 MB to the APK in the spike build (see More Information).
* Bad, because the layouts that do not fit at Wide or Wider need work, and each layout change needs a fit
  test.
* Bad, because the halo and the new fonts have been measured by the model of the tool, not on a device.
  The halo has not been seen with these fonts.
* Bad, because the dependency policy has a new licence in the allowlist (CC-BY-4.0), for fonts only.

## Pros and Cons of the Options

### Font choice: the seven choices (chosen)

* Good, because each choice has a purpose that a reader can read in the picker.
* Good, because the measurements keep the halo at 4.5:1 for each font.
* Bad, because seven fonts add files, licences and fit work.

### Font choice: one font only

* Good, because it adds no files and no licences.
* Bad, because no one font suits every reader. The Wallace et al. study found that the preferred font
  was not the fastest one for each reader (see More Information).

### OpenDyslexic

* Good, because some readers know and prefer it.
* Bad, because controlled studies show no benefit (see More Information).
* Bad, because it failed 27 of 87 fit tests, the most of any font.
* Bad, because it is the widest font, so the layouts need the most change.

### Text spacing: letter spacing only

* Good, because it is the simplest change.
* Bad, because it widens the spaces between words only as much as the letters. The Galliussi et al. study
  found that letter spacing without word spacing can slow reading.

### Text spacing: space characters in the string

* Good, because it needs no new component.
* Bad, because TalkBack reads a different text, and 715 tests failed.

### Text spacing: `Paint.setWordSpacing`

* Good, because it sets the word spacing directly.
* Bad, because Compose `Text` cannot reach it.

### Text spacing: letter and word spacing through one `Text` (chosen)

* Good, because the word spacing is wider than the letter spacing, as the research asks.
* Good, because the semantics text stays the original string.
* Bad, because the design-system `Text` replaces the Material `Text` in 39 files, and a detekt rule keeps it
  that way.

### Accent colour: fixed backgrounds (chosen)

* Good, because the glass table stays valid for every accent.
* Good, because the accent changes the controls and not the page.
* Bad, because the backgrounds keep violet whatever the accent.

### Accent colour: the backgrounds take the accent

* Good, because the whole screen follows the accent.
* Bad, because the glass table would need to be measured again for each accent.

### Card density

* Good, because more cards fit on a screen.
* Bad, because at the largest text size the host detail line is cut, and the Running badge is cut on
  a phone with 4 columns.
* Rejected by the owner. Every layout stays Comfortable.

### Background intensity: a veil (chosen)

* Good, because it darkens the background in four steps.
* Good, because it only darkens, so the glass floors and the glass table stay valid.
* Bad, because it does not change the background shape or motion.

### About links on a TV: a QR code (chosen)

* Good, because the TV has no browser to open, and a phone can read the QR code.
* Good, because ZXing is already in the app.
* Bad, because the reader needs a second device to open the link.

## More Information

### Research

* Dyslexia fonts. Controlled studies found no benefit for these fonts:
  * OpenDyslexic: Wery and Diliberto, 2017, [PMC5629233](https://pmc.ncbi.nlm.nih.gov/articles/PMC5629233).
  * Dyslexie: Kuster et al., 2018, [PMC5934461](https://pmc.ncbi.nlm.nih.gov/articles/PMC5934461/).
  * Marinus et al., 2016: the spacing, not the letter shapes, explained the small gain.
    TODO(verify): the URL of Marinus et al. 2016 is not in the brief.
* Spacing:
  * Zorzi et al., 2012: wider letter spacing together with word spacing helped children with dyslexia,
    [PMC3396504](https://pmc.ncbi.nlm.nih.gov/articles/PMC3396504).
  * Galliussi et al., 2020: letter spacing without word spacing can slow reading,
    [PMC7188700](https://pmc.ncbi.nlm.nih.gov/articles/PMC7188700).
* Choice of font: Wallace et al., 2022. Individual readers read up to about 35 % faster in their best
  font, and their preference did not predict it: [PDF](https://jeffhuang.com/papers/Readability_TOCHI22.pdf).
  This supports offering a choice.
* Luciole: the only candidate with a peer-reviewed study with low-vision readers. Galiano et al.,
  [hal-04086507](https://hal.univ-lyon2.fr/hal-04086507). TODO(verify): the year is not in the brief.
* Atkinson Hyperlegible Next, B612 and Andika are designed for legibility. No published outcome data was
  found for them.
* Lexend: the claims rest on one vendor study with 20 children, [lexend.com](https://www.lexend.com/).
* TV text: about 16 sp is the suggested minimum for text read at 3 m.
  * Legge and Bigelow, 2011: [PMC3428264](https://pmc.ncbi.nlm.nih.gov/articles/PMC3428264).
  * [Android TV typography guide](https://developer.android.com/design/ui/tv/guides/styles/typography).
* WCAG 2.2 success criterion 1.4.12, Text Spacing:
  [understanding document](https://www.w3.org/WAI/WCAG22/Understanding/text-spacing.html).

### Spike results (scratch, not in the repository)

* Fit tests (87 per run, at the system font scale of 200 % and at Largest text times 1.3):
  * IBM Plex at 15 and 12 sp: 0 failures; at 16 and 13 sp on a TV: 1 failure, fixed in the TV text size
    work.
  * Atkinson Hyperlegible Next: 8 failures. Luciole: 0. Lexend: 0. Andika: 11. B612: 0. System font: 0.
  * OpenDyslexic: 27 failures.
* Word spacing in Compose (BOM 2026.09.00, Compose 1.12.1), the span approach in IBM Plex: Wide has 22
  failures, Wider with 0.35 em has 37 and Wider with 0.5 em has 39. The worst case was
  OpenDyslexic at Wider 0.5 em, with 49 of 87 failures.
* Glyph coverage:
  * `⌫` is missing in every font, Plex included. It falls back to the system font.
  * The arrows are missing in Atkinson Hyperlegible Next, Lexend and OpenDyslexic Mono.
  * B612 has 3 % of Latin Extended-A, and no Cyrillic or Vietnamese.
  * Luciole, Lexend and Atkinson have no Cyrillic or Greek.
  * Andika is the only font with full Vietnamese.

### Fonts: the five items of the dependency policy

The sources are the manifest of the candidate fonts (SHA-256 and licence of each file) and the APK
measurements of the spike. The sizes are compressed bytes in the spike build, which ships the fonts in
full. In that build, Andika had three weights; the shipped Andika has two (see Open points).

* **IBM Plex Sans and IBM Plex Mono** (in the repository, already shipped): not new.
* **Atkinson Hyperlegible Next 2.001 and Atkinson Hyperlegible Mono 2.001**
  * Reason: the purpose of low vision, with letters that look alike drawn to differ.
  * Licence: OFL 1.1.
  * Maintenance: the release of Google Fonts (Atkinson Hyperlegible Next, version 2.001). TODO(verify):
    the date of the last release.
  * Footprint: 96 KB (Next) and 86 KB (Mono). No permissions, no native code.
  * Alternatives: the other fonts of the choice, and the system font.
* **Luciole** (Regular and Bold)
  * Reason: a peer-reviewed study with low-vision readers.
  * Licence: CC BY 4.0. The credit is "Luciole, Laurent Bourcellier, Jonathan Perez", from its LICENSE file.
    Allowed for fonts by decision 6.
  * Maintenance: the release on [luciole-vision.com](https://www.luciole-vision.com/fonts/Luciole.zip).
    TODO(verify): the version and the date of the last release.
  * Footprint: 112 KB. No permissions, no native code.
  * Alternatives: the other fonts of the choice.
* **Lexend 1.008** (Regular, Medium and SemiBold)
  * Reason: wider spacing built into the letters, for text that feels crowded.
  * Licence: OFL 1.1. Reserved font name "RevReading Lexend".
  * Maintenance: the Google Fonts project, version 1.008. TODO(verify): the date of the last release.
  * Footprint: 150 KB. No permissions, no native code.
  * Alternatives: the other fonts of the choice.
* **Andika 7.000** (Regular and SemiBold, shipped)
  * Reason: for new readers. Mirror letters such as b and d look different.
  * Licence: OFL 1.1. Reserved font names "Andika" and "SIL".
  * Maintenance: the SIL release v7.000, [github.com/silnrsi/font-andika](https://github.com/silnrsi/font-andika/releases/tag/v7.000). TODO(verify): the date of the release.
  * Footprint: 1,249 KB in the spike build with three weights. The shipped build drops Medium, which
    saves about 0.42 MB. TODO(verify): the size of the shipped build.
  * Alternatives: the other fonts of the choice. OpenDyslexic was rejected (decision 3).
* **B612 1.008 and B612 Mono 1.008** (Regular and Bold)
  * Reason: designed for reading at a glance and from a distance.
  * Licence: OFL 1.1, also EPL 2.0 and EDL 1.0. The name "B612" is a trademark of the Eclipse Foundation.
  * Maintenance: the PolarSys project of the Eclipse Foundation, [github.com/polarsys/b612](https://github.com/polarsys/b612/tree/1.008),
    version 1.008. TODO(verify): the date of the last release.
  * Footprint: 121 KB (B612) and 124 KB (B612 Mono).
  * Alternatives: the other fonts of the choice.
* **System font.** Uses the font of the device. No file is bundled and no licence applies.
* **OpenDyslexic 0.92** (rejected, not bundled): the spike measured 246 KB for OpenDyslexic and 36 KB for
  OpenDyslexic Mono.

The spike measured that the fonts add 2.22 MB compressed to the APK, which included OpenDyslexic. The
release APK does not shrink its resources, so the fonts count in full there too. TODO(verify): the
APK size with the shipped set.

### Related

* Amends [0056](0056-add-an-animated-background-and-the-appearance-group.md) and
  [0057](0057-glass-surfaces-and-the-accessibility-settings.md) by adding the choices, which
  do not change their decisions.
* Accessibility method and baseline: [docs/ux/accessibility.md](../ux/accessibility.md).
* [Dependency policy](../policies/dependencies.md): the licence allowlist, amended by this ADR.
* [0051](0051-send-the-diagnostic-bundle-to-a-phone-with-a-qr-code.md): the QR code and ZXing.
* Roadmap: Sprint 11.

### Open points

* The spike found that the sizes below the token minimums need a floor. The owner decided that a scaled
  size never goes below the minimum sizes. The spike asked whether the floor is on the size or on the
  x-height. This ADR follows the owner.
* The spike sheets showed Wider at 0.35 em with the words running together. The spike report says that
  0.35 em fits about as well as 0.5 em, and the decision uses 0.5 em. TODO(verify): the owner's
  reason for 0.5 em over 0.35 em.
* The per-font APK sizes are from the spike build (see the dependency items above). TODO(verify): the
  APK size with the shipped fonts.
* The brief does not give the URL of Marinus et al. 2016 and the year of Galiano et al. TODO(verify).
