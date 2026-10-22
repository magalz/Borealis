# Atkinson Hyperlegible Mono

The monospaced companion of Atkinson Hyperlegible Next, for the codes (the PIN, addresses, keyboard keys, chips and the performance overlay) when that font is chosen ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). The app bundles the font, so it never fetches it at run time.

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright 2020-2024 The Atkinson
  Hyperlegible Mono Project Authors. It has no Reserved Font Name.
- Source: Atkinson Hyperlegible Mono 2.001, from the Google Fonts project at
  <https://github.com/googlefonts/atkinson-hyperlegible-next-mono/tree/154d50362016cc3e873eb21d242cd0772384c8f9>.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `atkinson_mono_regular.ttf` | 2.001 | `AtkinsonHyperlegibleMono-Regular.ttf` | `2c9a7b9fabebd5c746a808d8a1ef2f52e618850caad6ec4fd9f5c48f56e1c1af` |
| `atkinson_mono_medium.ttf` | 2.001 | `AtkinsonHyperlegibleMono-Medium.ttf` | `43ee765e4c582789a77a032e0e8fb7ca119684ce99402df5f044f20afe84fb06` |
| `atkinson_mono_semibold.ttf` | 2.001 | `AtkinsonHyperlegibleMono-SemiBold.ttf` | `6dafae0d4cd636b5ed1b91bde38e46308ff3a2299665f7c32a67f35925c2177d` |

The licence file `OFL.txt` is the one of the release.

Weights: 400, 500 and 600. The scale is 1.040 (the drawn x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
