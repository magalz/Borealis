# Luciole

The Borealis Font setting offers Luciole, made with readers with low vision and tested in a published study ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 2). The app bundles the font, so it never fetches it at run time. Luciole has no monospaced companion: the codes use IBM Plex Mono.

- Licence: Creative Commons Attribution 4.0 International (CC BY 4.0). The release ships
  [LICENSE.txt](LICENSE.txt), which names the licence and the authors. The credit is
  "Luciole © Laurent Bourcellier & Jonathan Perez, CC BY 4.0, <https://www.luciole-vision.com>".
  The licences of About show it and `NOTICE` has it. CC BY 4.0 is allowed for fonts only
  ([ADR 0058](../../../docs/adr/0058-appearance-and-reading-options.md), decision 6).
- Source: <https://www.luciole-vision.com/fonts/Luciole.zip>, downloaded on 2026-10-21. The
  archive states no version; it was last modified on 2026-09-21.
- Use: `res/font/` is added as an Android resource directory of `:borealis:designsystem`
  (`apps/android/borealis/designsystem/build.gradle.kts`). Android resource names must be
  lowercase, so the files are renamed. Their content is not changed: the SHA-256 below is that of
  the file of the release and of the file in this folder.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `luciole_regular.ttf` | archive of 2026-09-21 | `Luciole-Regular.ttf` | `4c75513464f29c4c49d0ae5867d4348bf531736e6363c223b028a446f2d5307f` |
| `luciole_bold.ttf` | archive of 2026-09-21 | `Luciole-Bold.ttf` | `254e958919ddc70d8699825cb49643c7acdb407a04532523277642f9577d5cf7` |

The licence file `LICENSE.txt` is the one of the release.

Weights: Regular (400) and Bold (700) only. Compose takes the nearest one: Regular for 500 and
Bold for 600. The scale that gives the font the x-height of IBM Plex Sans is 0.947 (the drawn
x, see [docs/ux/accessibility.md](../../../docs/ux/accessibility.md#the-fonts-and-their-scale)).
