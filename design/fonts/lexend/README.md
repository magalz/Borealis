# Lexend

The Borealis Font setting offers Lexend, with wider spacing built into the letters, for text that feels crowded ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). The app bundles the font, so it never fetches it at run time. Lexend has no monospaced companion: the codes use IBM Plex Mono.

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright 2018 The Lexend Project
  Authors, with the Reserved Font Name "RevReading Lexend". The files are unmodified, so the
  name stays.
- Source: Lexend 1.008 (built 2022-09-22), from the Google Fonts project at
  <https://github.com/googlefonts/lexend/tree/7894f02b2e7eabc48595f1d4eff3b17b48c6e651>.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `lexend_regular.ttf` | 1.008 | `Lexend-Regular.ttf` | `e2082c28389e9871d5d77ae9163d5c0c1c259fe39bdac91f73d56a600b61f60f` |
| `lexend_medium.ttf` | 1.008 | `Lexend-Medium.ttf` | `ac109d3848ff23f1dda6cdb2f5e5bdb335811967fc4fb60b5f7b9df5a301c5ba` |
| `lexend_semibold.ttf` | 1.008 | `Lexend-SemiBold.ttf` | `204875848cc7981290a75f8be0e64b335b6d8f6ec6cc3ea05d907a37adab8b7b` |

The licence file `OFL.txt` is the one of the release.

Weights: 400, 500 and 600. The scale that gives the font the x-height of IBM Plex Sans is
0.983 (the drawn x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
