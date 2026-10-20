# Borealis icon, shortcut icon, mark and TV banner

`generate.py` draws the Borealis art from one geometry source: an aurora over a
purple night sky and a dark ridge in the LuminalShine page colour (`#0E1013`).
The owner approved it on 2026-10-14.

It writes these files under `apps/android/app/src/main/res/`, and the mark under the
resources of the design system:

| Output | Use |
| --- | --- |
| `drawable/borealis_launcher_background.xml`, `drawable/borealis_launcher_foreground.xml` | Adaptive launcher icon layers, also used by the Android TV channel logo (`drawable/ic_channel.xml`) |
| `drawable/borealis_launcher_monochrome.xml` | Themed (monochrome) icon on Android 13 and later |
| `drawable/borealis_shortcut_foreground.xml`, `drawable/borealis_shortcut_monochrome.xml` | Monitor glyph layers of the host and pinned-app shortcut icon (`mipmap-anydpi/ic_pc_scut.xml`, hand-written: the launcher sky behind these two layers) |
| `mipmap-*/ic_launcher.png` | Legacy launcher PNGs, for tools that read a raster icon from the APK |
| `drawable-xhdpi/atv_banner.png` | Google TV and Android TV banner (320 x 180 dp) |
| `apps/android/borealis/designsystem/src/main/res/drawable/borealis_mark.xml` | The mark in the footer of the navigation rail: the paths of the monochrome layer, cropped to the 72 x 72 that a launcher shows and drawn at 24 dp. It has no colour of its own: a screen tints it |

Regenerate from the repository root after changing the geometry or colours:

```sh
python -I design/icon/generate.py --preview icon-preview.png
```

The script needs Pillow. The banner wordmark uses Noto Sans SC Bold (SIL Open
Font License 1.1), which ships with Windows; pass `--font` with another OFL font
on other systems. Only the rendered pixels are committed, never the font file.
Check the preview sheet before committing.
