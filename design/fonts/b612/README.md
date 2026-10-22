# B612 and B612 Mono

The Borealis Font setting offers B612, made for cockpit screens: quick to read at a glance and from a distance ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). B612 Mono is its monospaced companion, used for the codes when B612 is chosen. The app bundles both, so it never fetches them at run time.

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright 2012 The B612 Project
  Authors. The project is also licensed under EPL 2.0 and EDL 1.0; only the OFL is bundled.
  "B612" is a trademark of the Eclipse Foundation.
- Source: B612 and B612 Mono 1.008, released 2019-03-14, from the PolarSys project of the
  Eclipse Foundation at <https://github.com/polarsys/b612/tree/1.008>.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `b612_regular.ttf` | 1.008 | `B612-Regular.ttf` | `139dce659100a83bf95b48474696e448bee95631ef84fd3d0437ced2bf33cf73` |
| `b612_bold.ttf` | 1.008 | `B612-Bold.ttf` | `91749541ac7b2c328b58832b7e2c4df809d7e2ba38d62a3a5aa3f8e38b271814` |
| `b612_mono_regular.ttf` | 1.008 | `B612Mono-Regular.ttf` | `b98cb96cc8a6206dae08c063d60902df7e6d40f86139ebdb97256704253c9c69` |
| `b612_mono_bold.ttf` | 1.008 | `B612Mono-Bold.ttf` | `b467b1d19fdabed42be51d87e38c86645ceeff2f828f294775188d00d1fd68ca` |

The licence file `OFL.txt` is the one of the release, with LF line ends where the release has CRLF (the repository stores text with LF). Its
text is unchanged.

Weights: Regular (400) and Bold (700) only, for both families. Compose takes the nearest one:
Regular for 500 and Bold for 600. The scale that gives the font the x-height of IBM Plex Sans
is 0.923 for B612 and 0.921 for B612 Mono (the drawn x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
