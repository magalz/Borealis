# Atkinson Hyperlegible Next

The Borealis Font setting offers Atkinson Hyperlegible Next, made for low vision: letters that look alike are drawn to differ ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). The app bundles the font, so it never fetches it at run time. Its monospaced companion, Atkinson Hyperlegible Mono, is in [../atkinson-mono](../atkinson-mono).

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright 2020-2024 The Atkinson
  Hyperlegible Next Project Authors. It has no Reserved Font Name.
- Source: Atkinson Hyperlegible Next 2.001, from the Google Fonts project at
  <https://github.com/googlefonts/atkinson-hyperlegible-next/tree/7925f50f649b3813257faf2f4c0b381011f434f1>.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `atkinson_next_regular.ttf` | 2.001 | `AtkinsonHyperlegibleNext-Regular.ttf` | `88ed5c31a71584c7772963b02d04bef1eb7e3d2e9c8b9cb204339b1f82cf432c` |
| `atkinson_next_medium.ttf` | 2.001 | `AtkinsonHyperlegibleNext-Medium.ttf` | `dd50b08b3c560846097d23baaaf6a97ffa20dd077115d23c59df68083b9ea05e` |
| `atkinson_next_semibold.ttf` | 2.001 | `AtkinsonHyperlegibleNext-SemiBold.ttf` | `8e2fff779f35ac25239656bb0f3b2bd35959a2009e3dd21a12141c3b9030362b` |

The licence file `OFL.txt` is the one of the release.

Weights: 400, 500 and 600, the weights the tokens use. The scale that gives the font the
x-height of IBM Plex Sans is 1.040 (the drawn x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
