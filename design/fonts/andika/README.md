# Andika

The Borealis Font setting offers Andika, made for new readers: mirror letters such as b and d look different ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). The app bundles the font, so it never fetches it at run time. Andika has no monospaced companion: the codes use IBM Plex Mono.

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright (c) 2004-2025 SIL Global,
  with the Reserved Font Names "Andika" and "SIL". The files are unmodified, so the names
  stay.
- Source: Andika 7.000, released 2025-06-02, `Andika-7.000.zip` of
  <https://github.com/silnrsi/font-andika/releases/tag/v7.000>.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `andika_regular.ttf` | 7.000 | `Andika-Regular.ttf` | `27484fdc98d0d63f90407f8266e28295f6fb16d2b13c5024df0214f17152919a` |
| `andika_semibold.ttf` | 7.000 | `Andika-SemiBold.ttf` | `73c4df767769af2e545bf66ca4caabd4294ce61eb7206a9908eedfd6a7cc965e` |

The licence file `OFL.txt` is the one of the release, with LF line ends where the release has CRLF (the repository stores text with LF). Its
text is unchanged.

Weights: Regular (400) and SemiBold (600) only. The release has a Medium too, which is not
bundled: it would add about 0.4 MB, and Compose takes Regular for 500. The scale that gives
the font the x-height of IBM Plex Sans is 1.034 (the drawn x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
