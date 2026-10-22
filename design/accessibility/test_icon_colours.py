"""Tests of the colours of the app icon against the accent of the app. Run from the repository root:

    python -m unittest discover -s design/accessibility

The band of the icon that was green is the teal of the accent (issue #50, ADR 0058, decision 5): its
gradient goes from the accent at the top to the strong accent at the bottom, as `accents.json` has
them for teal. The generator draws the launcher layers, so the tests read what it writes.
"""

import importlib.util
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

DESIGN = Path(__file__).resolve().parents[1]
RES = DESIGN.parent / "apps" / "android" / "app" / "src" / "main" / "res"

# The gradients of the band of the aurora that sits above the violet one: the launcher foreground
# draws it from y 14 to y 80, and the monitor of the shortcut icon from y 38 to y 66.
BAND_GRADIENTS = {
    "borealis_launcher_foreground.xml": (14, 80),
    "borealis_shortcut_foreground.xml": (38, 66),
}
# The opacities of the band stay as they are: nearly opaque at the top, a wash at the bottom.
TOP_ALPHA, BOTTOM_ALPHA = 0xF2, 0x1A

GRADIENT = re.compile(
    r'android:startY="(\d+)"\s+android:endX="\d+"\s+android:endY="(\d+)">\s*'
    r'<item android:offset="0" android:color="(#[0-9A-F]{8})" />\s*'
    r'<item android:offset="1" android:color="(#[0-9A-F]{8})" />'
)


def load_generator():
    """The generator as a module. It is read without leaving a bytecode cache in design/icon."""
    spec = importlib.util.spec_from_file_location("icon_generate", DESIGN / "icon" / "generate.py")
    module = importlib.util.module_from_spec(spec)
    dont_write, sys.dont_write_bytecode = sys.dont_write_bytecode, True
    try:
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = dont_write
    return module


def teal_roles():
    accents = json.loads((DESIGN / "accessibility" / "accents.json").read_text(encoding="utf-8"))
    color = accents["candidates"]["teal"]["color"]
    return color["accent"].lstrip("#").upper(), color["accent-strong"].lstrip("#").upper()


def band_gradient(xml, y0, y1):
    """The top and bottom colours (`#AARRGGBB`) of the gradient that runs from y0 to y1."""
    found = [(top, bottom) for start, end, top, bottom in GRADIENT.findall(xml)
             if (int(start), int(end)) == (y0, y1)]
    assert len(found) == 1, f"expected one gradient from {y0} to {y1}, found {found}"
    return found[0]


class IconBandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generated = tempfile.TemporaryDirectory()
        load_generator().vectors(cls.generated.name)

    @classmethod
    def tearDownClass(cls):
        cls.generated.cleanup()

    def written(self, name):
        return (Path(self.generated.name) / name).read_text(encoding="utf-8")

    def test_the_band_goes_from_the_teal_accent_to_the_teal_strong_accent(self):
        accent, accent_strong = teal_roles()
        for name, (y0, y1) in BAND_GRADIENTS.items():
            with self.subTest(layer=name):
                top, bottom = band_gradient(self.written(name), y0, y1)
                self.assertEqual(top[3:], accent)
                self.assertEqual(bottom[3:], accent_strong)

    def test_the_band_keeps_its_opacities(self):
        for name, (y0, y1) in BAND_GRADIENTS.items():
            with self.subTest(layer=name):
                top, bottom = band_gradient(self.written(name), y0, y1)
                self.assertEqual(int(top[1:3], 16), TOP_ALPHA)
                self.assertEqual(int(bottom[1:3], 16), BOTTOM_ALPHA)

    def test_the_layers_in_the_app_are_what_the_generator_writes(self):
        for path in sorted(Path(self.generated.name).glob("*.xml")):
            with self.subTest(layer=path.name):
                committed = (RES / "drawable" / path.name).read_text(encoding="utf-8")
                self.assertEqual(committed, path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
