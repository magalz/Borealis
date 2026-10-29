"""Tests of the contrast tool: the WCAG maths, the blur and glass model, the halo and the
measurement of a folder of frames. Run from the repository root:

    python -m unittest discover -s design/accessibility
"""

import contextlib
import hashlib
import io
import json
import math
import re
import struct
import tempfile
import unittest
import unittest.mock
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import contrast

TOKENS = Path(__file__).resolve().parents[1] / "tokens.json"
WORDS = ("ink", "ink-2", "ink-3", "ink-4", "accent-soft", "page", "surface", "surface-raised")


def tokens():
    return contrast.load_colors(TOKENS, WORDS)


def uniform(width, height, rgb):
    return np.broadcast_to(np.asarray(rgb, np.float32), (height, width, 3)).copy()


class WcagMathTest(unittest.TestCase):
    def test_black_on_white_is_21_to_1(self):
        self.assertAlmostEqual(contrast.hex_contrast("#000000", "#FFFFFF"), 21.0, places=6)

    def test_the_ratio_is_the_same_either_way_round(self):
        self.assertAlmostEqual(
            contrast.hex_contrast("#FFFFFF", "#777777"),
            contrast.hex_contrast("#777777", "#FFFFFF"),
            places=9,
        )

    def test_777777_on_white_is_about_4_48_to_1(self):
        self.assertAlmostEqual(contrast.hex_contrast("#777777", "#FFFFFF"), 4.478, places=3)

    def test_ink_on_page_is_15_81(self):
        colors = tokens()
        self.assertAlmostEqual(
            contrast.hex_contrast(colors["ink"], colors["page"]), 15.81, places=2
        )

    def test_ink_4_on_surface_is_4_51(self):
        colors = tokens()
        self.assertAlmostEqual(
            contrast.hex_contrast(colors["ink-4"], colors["surface"]), 4.51, places=2
        )

    def test_the_luminance_uses_the_srgb_threshold_of_0_04045(self):
        # Below the threshold the curve is linear, above it a power of 2.4.
        self.assertAlmostEqual(float(contrast.srgb_to_linear(0.04)), 0.04 / 12.92, places=9)
        self.assertAlmostEqual(
            float(contrast.srgb_to_linear(0.5)), ((0.5 + 0.055) / 1.055) ** 2.4, places=9
        )

    def test_the_luminance_of_an_array_of_pixels(self):
        pixels = np.array([[[0, 0, 0], [1, 1, 1]], [[1, 0, 0], [0, 1, 0]]], np.float64)
        np.testing.assert_allclose(
            contrast.relative_luminance(pixels), [[0.0, 1.0], [0.2126, 0.7152]], atol=1e-9
        )

    def test_a_colour_is_read_from_hex(self):
        np.testing.assert_allclose(
            contrast.parse_hex("#FF8000"), [1.0, 128 / 255, 0.0], atol=1e-9
        )

    def test_the_colours_come_from_the_token_file(self):
        colors = tokens()
        self.assertEqual(colors["ink"].upper(), "#E8EAED")
        self.assertEqual(colors["surface"].upper(), "#14171B")


class FrameStatisticsTest(unittest.TestCase):
    def test_the_worst_case_is_the_lowest_ratio_over_the_frame(self):
        # Text of luminance 1 on a pixel of 0 (21:1) and on one of 0.5 (1.05 / 0.55).
        stats = contrast.contrast_stats(np.array([0.0, 0.5]), 1.0)
        self.assertAlmostEqual(stats["min"], 1.05 / 0.55, places=6)

    def test_a_pixel_brighter_than_the_text_counts_too(self):
        # A star behind a letter: text of 0.5 over a pixel of 1.0 is the same 1.05 / 0.55.
        stats = contrast.contrast_stats(np.array([0.0, 1.0]), 0.5)
        self.assertAlmostEqual(stats["min"], 1.05 / 0.55, places=6)

    def test_p1_and_p5_are_the_percentiles_of_the_ratios(self):
        luminance = np.linspace(0.0, 0.4, 1001)
        ratios = 1.05 / (luminance + 0.05)
        stats = contrast.contrast_stats(luminance, 1.0)
        self.assertAlmostEqual(stats["p1"], float(np.percentile(ratios, 1)), places=6)
        self.assertAlmostEqual(stats["p5"], float(np.percentile(ratios, 5)), places=6)
        self.assertLess(stats["min"], stats["p1"])
        self.assertLess(stats["p1"], stats["p5"])

    def test_a_uniform_frame_has_one_ratio(self):
        stats = contrast.contrast_stats(np.full((4, 5), 0.2), 0.8)
        for key in ("min", "p1", "p5"):
            self.assertAlmostEqual(stats[key], 0.85 / 0.25, places=6)


class FormatRatioTest(unittest.TestCase):
    def test_a_ratio_is_rounded_to_two_places(self):
        self.assertEqual(contrast.format_ratio(15.8077), "15.81")
        self.assertEqual(contrast.format_ratio(4.5072), "4.51")

    def test_a_ratio_short_of_a_wcag_limit_is_never_rounded_up_to_it(self):
        self.assertEqual(contrast.format_ratio(4.4962), "4.496")
        self.assertEqual(contrast.format_ratio(2.9996), "2.999")
        self.assertEqual(contrast.format_ratio(6.9999), "6.999")

    def test_a_ratio_at_a_limit_is_printed_as_it_is(self):
        self.assertEqual(contrast.format_ratio(4.5), "4.50")
        self.assertEqual(contrast.format_ratio(7.0), "7.00")


class BlurAndGlassTest(unittest.TestCase):
    def test_a_uniform_image_keeps_its_colour_after_the_blur(self):
        image = uniform(80, 60, (0.2, 0.4, 0.6))
        blurred = contrast.gaussian_blur(image, 7.0)
        self.assertEqual(blurred.shape, image.shape)
        np.testing.assert_allclose(blurred, image, atol=1e-5)

    def test_the_blur_of_a_point_is_a_gaussian_of_the_sigma(self):
        image = np.zeros((101, 101, 3), np.float32)
        image[50, 50] = 1.0
        blurred = contrast.gaussian_blur(image, 3.0)
        self.assertAlmostEqual(float(blurred[50, 50, 0]), 1 / (2 * np.pi * 9), delta=2e-4)
        # One sigma away along a row the height is exp(-1/2) of the peak.
        self.assertAlmostEqual(
            float(blurred[50, 53, 1] / blurred[50, 50, 1]), float(np.exp(-0.5)), delta=2e-3
        )
        self.assertAlmostEqual(float(blurred[..., 2].sum()), 1.0, delta=1e-3)

    def test_the_blur_does_not_darken_the_edges(self):
        # The edge of the frame repeats (clamp), so a bright frame stays bright at its edge.
        image = uniform(60, 40, (0.9, 0.9, 0.9))
        blurred = contrast.gaussian_blur(image, 6.0)
        self.assertGreater(float(blurred[0, 0, 0]), 0.899)

    def test_the_sigma_is_the_one_skia_derives_from_the_radius(self):
        self.assertAlmostEqual(contrast.blur_sigma(32), 0.57735 * 32 + 0.5, places=6)

    def test_the_density_comes_from_the_width_of_the_frame(self):
        self.assertAlmostEqual(contrast.frame_density(1920, "tv"), 2.0, places=6)
        self.assertAlmostEqual(contrast.frame_density(1233, "phone"), 3.0, places=6)

    def test_the_composite_at_alpha_1_is_the_tint(self):
        image = uniform(20, 10, (0.1, 0.5, 0.9))
        out = contrast.composite(image, np.array([0.3, 0.2, 0.1]), 1.0)
        np.testing.assert_allclose(out, uniform(20, 10, (0.3, 0.2, 0.1)), atol=1e-6)

    def test_the_composite_at_alpha_0_is_the_image(self):
        image = uniform(20, 10, (0.1, 0.5, 0.9))
        out = contrast.composite(image, np.array([0.3, 0.2, 0.1]), 0.0)
        np.testing.assert_allclose(out, image, atol=1e-6)

    def test_the_composite_mixes_in_the_encoded_values(self):
        image = uniform(2, 2, (0.0, 0.0, 0.0))
        out = contrast.composite(image, np.array([1.0, 0.5, 0.2]), 0.7)
        np.testing.assert_allclose(out[0, 0], [0.7, 0.35, 0.14], atol=1e-6)

    def test_a_glass_panel_is_the_blur_under_the_tint(self):
        image = np.zeros((101, 101, 3), np.float32)
        image[50, 50] = 1.0
        tint = np.array([0.1, 0.1, 0.1])
        sigma = contrast.blur_sigma(5)
        expected = contrast.composite(contrast.gaussian_blur(image, sigma), tint, 0.5)
        np.testing.assert_allclose(contrast.glass(image, tint, 0.5, 5), expected, atol=1e-6)


class ClipFramesTest(unittest.TestCase):
    def test_the_frames_are_spread_evenly_over_the_loop(self):
        self.assertEqual(contrast.frame_indices(240, 24), list(range(0, 240, 10)))
        self.assertEqual(contrast.frame_indices(145, 24)[:3], [0, 6, 12])
        self.assertEqual(len(contrast.frame_indices(145, 24)), 24)

    def test_the_last_frame_is_before_the_end_of_the_loop(self):
        indices = contrast.frame_indices(600, 24)
        self.assertEqual(len(indices), 24)
        self.assertLess(indices[-1], 600)

    def test_the_filter_selects_the_frames_by_number(self):
        expression = contrast.select_filter([0, 10, 20])
        self.assertTrue(expression.startswith("select="))
        for index in (0, 10, 20):
            self.assertIn(f"eq(n\\,{index})", expression)


class CoverGeometryTest(unittest.TestCase):
    def test_a_phone_scales_the_clip_to_its_height_and_cuts_the_sides(self):
        # 16:9 at 2673 px high is 4752 px wide; the centre 1233 px of it are shown.
        self.assertEqual(
            contrast.cover_geometry((1233, 2673), (1920, 1080)), (4752, 2673, 1759, 0)
        )

    def test_a_screen_wider_than_the_clip_scales_to_its_width_and_cuts_top_and_bottom(self):
        self.assertEqual(contrast.cover_geometry((2000, 1000), (1920, 1080)), (2000, 1125, 0, 62))

    def test_a_screen_of_the_aspect_of_the_clip_cuts_nothing(self):
        self.assertEqual(contrast.cover_geometry((1920, 1080), (1920, 1080)), (1920, 1080, 0, 0))


class FoldTest(unittest.TestCase):
    def test_the_worst_case_keeps_its_precision_until_it_is_printed(self):
        # 4.49996 rounded to four places is 4.5000, which would print as a pass.
        row = contrast._fold(
            [
                {"min": 4.5, "p1": 4.6, "p5": 4.7},
                {"min": 4.49996, "p1": 4.5, "p5": 4.6},
            ]
        )
        self.assertEqual(row["min"], 4.49996)
        self.assertEqual(row["per_frame_min"], [4.5, 4.49996])
        self.assertEqual(row["worst_frame"], 1)
        self.assertEqual(contrast.format_ratio(row["min"]), "4.499")

    def test_the_ratio_of_a_surface_is_not_rounded(self):
        colors = tokens()
        rows = contrast.measure_surfaces(colors)
        row = next(r for r in rows if r["surface"] == "surface" and r["token"] == "ink-4")
        self.assertEqual(row["ratio"], contrast.hex_contrast(colors["ink-4"], colors["surface"]))


class MeasureTest(unittest.TestCase):
    def setUp(self):
        self.colors = tokens()

    def measure(self, frames):
        return contrast.measure_source(frames, "AuroraCurtains", "tv", self.colors)

    def test_text_straight_on_a_flat_page_is_the_ratio_on_the_page(self):
        page = contrast.parse_hex(self.colors["page"])
        result = self.measure([uniform(96, 54, page)] * 2)
        self.assertIn("background", result)
        by_token = {row["token"]: row for row in result["background"]}
        for token in contrast.TEXT_TOKENS:
            expected = contrast.hex_contrast(self.colors[token], self.colors["page"])
            self.assertAlmostEqual(by_token[token]["min"], expected, places=3)
        self.assertEqual(len(by_token["ink"]["per_frame_min"]), 2)

    def test_the_worst_frame_is_the_one_with_the_brightest_pixel(self):
        page = contrast.parse_hex(self.colors["page"])
        dark = uniform(96, 54, page)
        bright = dark.copy()
        bright[10, 10] = (0.7, 0.6, 0.8)
        result = self.measure([dark, bright, dark])
        self.assertIn("background", result)
        row = {r["token"]: r for r in result["background"]}["ink-3"]
        self.assertEqual(row["worst_frame"], 1)
        self.assertLess(row["min"], row["per_frame_min"][0])
        self.assertAlmostEqual(row["min"], row["per_frame_min"][1], places=9)

    def test_a_panel_over_a_flat_page_lies_between_the_page_and_the_surface(self):
        page = contrast.parse_hex(self.colors["page"])
        result = self.measure([uniform(96, 54, page)])
        self.assertIn("glass", result)
        panels = [r for r in result["glass"] if r["token"] == "ink"]
        self.assertEqual(len(panels), 36)
        low = contrast.hex_contrast(self.colors["ink"], self.colors["surface"])
        high = contrast.hex_contrast(self.colors["ink"], self.colors["page"])
        for row in panels:
            self.assertGreaterEqual(row["min"], low - 1e-3)
            self.assertLessEqual(row["min"], high + 1e-3)

    def test_the_panels_are_the_grid_of_alpha_and_blur(self):
        page = contrast.parse_hex(self.colors["page"])
        result = self.measure([uniform(96, 54, page)])
        self.assertIn("glass", result)
        grid = {(r["alpha"], r["blur_dp"]) for r in result["glass"]}
        self.assertEqual(
            grid,
            {(a, b) for a in (0.30, 0.40, 0.50, 0.60, 0.70, 0.85) for b in (0, 8, 16, 24, 32, 48)},
        )

    def test_the_opaque_surfaces_do_not_depend_on_the_frames(self):
        rows = contrast.measure_surfaces(self.colors)
        self.assertEqual(len(rows), 3 * len(contrast.TEXT_TOKENS))
        row = next(r for r in rows if r["surface"] == "surface" and r["token"] == "ink-4")
        self.assertAlmostEqual(row["ratio"], 4.507, places=3)

    def test_a_folder_of_frames_is_read_by_style_and_device(self):
        page = tuple(round(c * 255) for c in contrast.parse_hex(self.colors["page"]))
        with tempfile.TemporaryDirectory() as folder:
            for name in ("AuroraCurtains-tv", "Starfield-phone"):
                (Path(folder) / name).mkdir()
                for index in range(2):
                    Image.new("RGB", (96, 54), page).save(Path(folder) / name / f"{index:02d}.png")
            (Path(folder) / "notes").mkdir()
            result = contrast.measure_folder(Path(folder), self.colors)
        self.assertIn("sources", result)
        self.assertEqual(
            sorted((s["style"], s["device"], s["frames"]) for s in result["sources"]),
            [("AuroraCurtains", "tv", 2), ("Starfield", "phone", 2)],
        )
        self.assertEqual(
            {(r["style"], r["device"]) for r in result["background"]},
            {("AuroraCurtains", "tv"), ("Starfield", "phone")},
        )
        # The result is plain data: it goes to JSON as it is.
        json.dumps(result)

    def test_a_panel_blurs_by_dp_at_the_density_of_the_frame(self):
        # One white pixel on a page-coloured frame. The pixel is where the glass is brightest, and
        # a Gaussian of sigma s lets a point keep 1 / (2 pi s^2) of its lift, so the worst case
        # tells the sigma that was used: radius dp * density px, as Skia turns it into a sigma.
        page = contrast.parse_hex(self.colors["page"])
        tint = contrast.parse_hex(self.colors["surface"])
        ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))
        for width, density in ((96, 0.1), (192, 0.2)):
            with self.subTest(width=width):
                frame = uniform(width, width * 9 // 16, page)
                frame[frame.shape[0] // 2, width // 2] = 1.0
                result = self.measure([frame])
                for alpha, blur_dp in ((0.30, 16), (0.85, 32)):
                    sigma = 0.57735 * blur_dp * density + 0.5
                    lift = 1 / (2 * np.pi * sigma**2)
                    seen = alpha * tint + (1 - alpha) * (page + (1 - page) * lift)
                    expected = float(
                        contrast.contrast_ratio(contrast.relative_luminance(seen), ink)
                    )
                    row = next(
                        r
                        for r in result["glass"]
                        if (r["alpha"], r["blur_dp"], r["token"]) == (alpha, blur_dp, "ink")
                    )
                    self.assertAlmostEqual(row["min"] / expected, 1.0, delta=2e-3)

    def test_the_tables_name_the_worst_style_of_each_candidate(self):
        page = contrast.parse_hex(self.colors["page"])
        bright = uniform(96, 54, page)
        bright[5, 5] = (0.8, 0.8, 0.9)
        result = contrast.measure_sources(
            {
                ("AuroraCurtains", "tv"): [uniform(96, 54, page)],
                ("SoftGlow", "tv"): [bright],
            },
            self.colors,
        )
        self.assertIn("surfaces", result)
        text = contrast.markdown_tables(result)
        self.assertIn("Table A", text)
        self.assertIn("Table B", text)
        self.assertIn("Table C", text)
        # The bright pixel is in the glow, so the glow is the worst style of every candidate.
        table_c = text[text.index("Table C") :]
        self.assertIn("Soft glow", table_c)
        self.assertNotIn("Aurora curtains", table_c)


    def test_a_table_of_panels_is_given_for_each_device(self):
        page = contrast.parse_hex(self.colors["page"])
        result = contrast.measure_sources(
            {
                ("AuroraCurtains", "tv"): [uniform(96, 54, page)],
                ("AuroraCurtains", "phone"): [uniform(96, 54, page)],
            },
            self.colors,
        )
        text = contrast.markdown_tables(result)
        self.assertIn("Table C, TV", text)
        self.assertIn("Table C, phone", text)

    def test_the_p5_table_says_it_is_the_lowest_of_the_frames(self):
        page = contrast.parse_hex(self.colors["page"])
        frames = {("SoftGlow", "tv"): [uniform(96, 54, page)]}
        result = contrast.measure_sources(frames, self.colors)
        self.assertIn("lowest over the frames", contrast.markdown_tables(result))


class NoBlurTest(unittest.TestCase):
    """A blur of 0 dp is no blur at all: the glass of an older phone, which cannot blur."""

    def test_the_panel_without_blur_is_the_frame_under_the_tint(self):
        colors = tokens()
        page = contrast.parse_hex(colors["page"])
        tint = contrast.parse_hex(colors["surface"])
        frame = uniform(96, 54, page)
        frame[20, 40] = 1.0
        result = contrast.measure_source([frame], "AuroraCurtains", "tv", colors)
        row = next(
            r
            for r in result["glass"]
            if (r["alpha"], r["blur_dp"], r["token"]) == (0.85, 0, "ink")
        )
        ink = float(contrast.relative_luminance(contrast.parse_hex(colors["ink"])))
        brightest = float(contrast.relative_luminance(contrast.composite(frame, tint, 0.85)).max())
        self.assertAlmostEqual(row["min"], float(contrast.contrast_ratio(brightest, ink)), places=4)

    def test_a_blur_makes_the_worst_pixel_darker_than_none(self):
        colors = tokens()
        page = contrast.parse_hex(colors["page"])
        frame = uniform(96, 54, page)
        frame[20, 40] = 1.0
        result = contrast.measure_source([frame], "AuroraCurtains", "tv", colors)
        ratios = {
            r["blur_dp"]: r["min"]
            for r in result["glass"]
            if (r["alpha"], r["token"]) == (0.85, "ink")
        }
        self.assertLess(ratios[0], ratios[8])


def glass_result():
    """A measurement of two styles: a flat page, and a page with a bright pixel on the phone."""
    colors = tokens()
    page = contrast.parse_hex(colors["page"])
    bright = uniform(96, 54, page)
    bright[20, 40] = (0.9, 0.9, 1.0)
    result = contrast.measure_sources(
        {
            ("AuroraCurtains", "tv"): [uniform(96, 54, page)],
            ("AuroraCurtains", "phone"): [bright],
            ("Starfield", "tv"): [uniform(96, 54, page)],
        },
        colors,
    )
    return colors, bright, result


class GlassTableTest(unittest.TestCase):
    """The table of the worst backdrop that Kotlin turns into floors and text colours."""

    def test_the_worst_backdrop_is_the_brightest_pixel_over_both_devices(self):
        colors, bright, result = glass_result()
        table = contrast.worst_backdrop_luminance(result)
        self.assertEqual(sorted(table), ["AuroraCurtains", "Starfield"])
        tint = contrast.parse_hex(colors["surface"])
        density = contrast.frame_density(96, "phone")
        for blur_dp, alpha in ((8, 0.40), (48, 0.85), (0, 0.30)):
            panel = (
                contrast.composite(bright, tint, alpha)
                if blur_dp == 0
                else contrast.glass(bright, tint, alpha, blur_dp * density)
            )
            expected = float(contrast.relative_luminance(panel).max())
            self.assertAlmostEqual(table["AuroraCurtains"][(blur_dp, alpha)], expected, delta=2e-5)

    def test_a_style_with_one_device_is_measured_on_that_device(self):
        colors, _, result = glass_result()
        page = contrast.parse_hex(colors["page"])
        tint = contrast.parse_hex(colors["surface"])
        expected = float(contrast.relative_luminance(contrast.composite(page, tint, 0.5)))
        table = contrast.worst_backdrop_luminance(result)
        self.assertAlmostEqual(table["Starfield"][(0, 0.50)], expected, delta=2e-5)

    def test_the_table_is_rounded_up_never_down(self):
        colors, bright, result = glass_result()
        tint = contrast.parse_hex(colors["surface"])
        exact = float(contrast.relative_luminance(contrast.composite(bright, tint, 0.85)).max())
        value = contrast.worst_backdrop_luminance(result)["AuroraCurtains"][(0, 0.85)]
        self.assertGreaterEqual(value, exact - 1e-9)
        self.assertLess(value - exact, 1.1e-5)

    def test_the_kotlin_file_says_it_is_generated(self):
        _, _, result = glass_result()
        text = contrast.glass_table_kotlin(result)
        header = text.split("package", 1)[0]
        self.assertIn("Generated by", header)
        self.assertIn("contrast.py glass-table", header)
        self.assertIn("Do not edit", header)
        self.assertIn("package io.github.magalz.borealis.designsystem.glass", text)

    def test_the_kotlin_file_has_a_row_for_each_style_in_the_grid(self):
        _, _, result = glass_result()
        text = contrast.glass_table_kotlin(result)
        self.assertIn("intArrayOf(30, 40, 50, 60, 70, 85)", text)
        self.assertIn("intArrayOf(0, 8, 16, 24, 32, 48)", text)
        self.assertIn("BackgroundStyle.AuroraCurtains to", text)
        self.assertIn("BackgroundStyle.Starfield to", text)
        self.assertNotIn("BackgroundStyle.SoftGlow", text)

    def test_the_numbers_in_the_kotlin_file_are_the_table_row_by_row(self):
        _, _, result = glass_result()
        table = contrast.worst_backdrop_luminance(result)
        text = contrast.glass_table_kotlin(result)
        block = text.split("BackgroundStyle.AuroraCurtains to", 1)[1].split("BackgroundStyle.", 1)[0]
        rows = [
            [float(n) for n in row.split(",")]
            for row in re.findall(r"doubleArrayOf\(([^)]*)\)", block)
        ]
        self.assertEqual(len(rows), 6)
        for blur_index, blur_dp in enumerate((0, 8, 16, 24, 32, 48)):
            expected = [
                table["AuroraCurtains"][(blur_dp, alpha)]
                for alpha in (0.30, 0.40, 0.50, 0.60, 0.70, 0.85)
            ]
            self.assertEqual(rows[blur_index], expected)

    def test_the_kotlin_file_has_unix_line_ends_and_is_the_same_every_time(self):
        _, _, result = glass_result()
        text = contrast.glass_table_kotlin(result)
        self.assertNotIn("\r", text)
        self.assertTrue(text.endswith("}\n"))
        self.assertEqual(text, contrast.glass_table_kotlin(result))

    def test_the_command_writes_the_file_from_a_measurement(self):
        _, _, result = glass_result()
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "result.json"
            source.write_text(json.dumps(result), encoding="utf-8")
            out = Path(folder) / "GlassContrastTable.kt"
            args = ["glass-table", str(source), "--out", str(out)]
            self.assertEqual(contrast.main(args, log=io.StringIO()), 0)
            self.assertEqual(out.read_bytes(), contrast.glass_table_kotlin(result).encode("utf-8"))

    def test_the_command_says_what_it_wrote_to_the_log_it_is_given(self):
        _, _, result = glass_result()
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "result.json"
            source.write_text(json.dumps(result), encoding="utf-8")
            out = Path(folder) / "GlassContrastTable.kt"
            log, screen = io.StringIO(), io.StringIO()
            with contextlib.redirect_stderr(screen):
                contrast.main(["glass-table", str(source), "--out", str(out)], log=log)
            self.assertEqual(log.getvalue(), f"wrote {out}\n")
            self.assertEqual(screen.getvalue(), "")

    def test_the_hash_of_a_clip_is_its_sha256_by_the_name_of_the_file(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "background_one.mp4").write_bytes(b"one")
            (Path(folder) / "background_two.mp4").write_bytes(b"")
            (Path(folder) / "other.mp4").write_bytes(b"not a clip of the backgrounds")
            (Path(folder) / "background_one_still.webp").write_bytes(b"a still")
            self.assertEqual(
                contrast.clip_hashes(folder),
                {
                    "background_one": hashlib.sha256(b"one").hexdigest(),
                    "background_two": hashlib.sha256(b"").hexdigest(),
                },
            )

    def test_the_kotlin_file_holds_the_hash_of_each_clip(self):
        _, _, result = glass_result()
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "background_one.mp4").write_bytes(b"one")
            (Path(folder) / "background_two.mp4").write_bytes(b"two")
            text = contrast.glass_table_kotlin(result, folder)
        for name, content in (("background_one", b"one"), ("background_two", b"two")):
            digest = hashlib.sha256(content).hexdigest()
            self.assertRegex(text, rf'"{name}"\s+to\s+"{digest}"')

    def test_a_clip_that_changes_changes_the_file(self):
        _, _, result = glass_result()
        with tempfile.TemporaryDirectory() as folder:
            clip = Path(folder) / "background_one.mp4"
            clip.write_bytes(b"first take")
            before = contrast.glass_table_kotlin(result, folder)
            clip.write_bytes(b"second take")
            self.assertNotEqual(contrast.glass_table_kotlin(result, folder), before)

    def test_the_file_holds_the_hash_of_the_clips_of_the_app(self):
        _, _, result = glass_result()
        text = contrast.glass_table_kotlin(result)
        clips = sorted(contrast.CLIPS_DIR.glob("background_*.mp4"))
        self.assertGreater(len(clips), 0)
        for clip in clips:
            digest = hashlib.sha256(clip.read_bytes()).hexdigest()
            self.assertRegex(text, rf'"{clip.stem}"\s+to\s+"{digest}"')
        # And no hash of a clip that is not there.
        hashes = re.findall(r'"background_\w+"\s+to\s+"[0-9a-f]{64}"', text)
        self.assertEqual(len(hashes), len(clips))

    def test_the_file_is_written_where_the_app_reads_it(self):
        self.assertEqual(
            contrast.GLASS_TABLE_KT.relative_to(contrast.REPO).as_posix(),
            "apps/android/borealis/designsystem/src/main/kotlin/io/github/magalz/borealis/"
            "designsystem/glass/GlassContrastTable.kt",
        )


class GlassColoursTest(unittest.TestCase):
    """The floors and the text colours of the glass, worked out again from the table of the app.

    Kotlin works them out in `GlassRules`; these are the values that `docs/ux/accessibility.md`
    prints, and the tests pin the ones that `GlassRulesTest` pins too.
    """

    def setUp(self):
        self.colors = tokens()
        text = contrast.GLASS_TABLE_KT.read_text(encoding="utf-8")
        self.table = contrast.read_glass_table_kotlin(text)

    def ink(self, style, blur, alpha):
        target = contrast.glass_text_target(self.table[style][(blur, alpha)])
        return tuple(
            contrast.walk_colour(self.colors[token], self.colors["ink"], target)
            for token in ("ink-3", "ink-4")
        )

    def test_a_half_rounds_up_as_it_does_in_kotlin(self):
        self.assertEqual([contrast.round_half_up(x) for x in (0.5, 1.5, 2.5, 3.4)], [1, 2, 3, 3])

    def test_a_walk_that_lands_on_half_a_level_rounds_up(self):
        def luminance_at(level):
            return float(contrast.relative_luminance(np.full(3, level / 255)))

        # Black towards #0A0A0A: step 5 of 100 is half a level, and step 4 is 0.4 of one.
        target = (luminance_at(0.4) + luminance_at(0.5)) / 2

        self.assertEqual(contrast.walk_colour("#000000", "#0A0A0A", target), "#010101")

    def test_a_walk_that_starts_at_the_target_stays_where_it_is(self):
        self.assertEqual(contrast.walk_colour("#8b9096", "#E8EAED", 0.1), "#8B9096")

    def test_the_table_of_the_app_reads_back_as_it_was_written(self):
        _, _, result = glass_result()
        text = contrast.glass_table_kotlin(result)
        self.assertEqual(
            contrast.read_glass_table_kotlin(text), contrast.worst_backdrop_luminance(result)
        )

    def test_the_colours_at_the_default_are_the_ones_the_app_pins(self):
        self.assertEqual(self.ink("AuroraCurtains", 8, 0.40), ("#8D9298", "#8D9298"))
        self.assertEqual(self.ink("FlowingLight", 8, 0.40)[0], "#A8ACB1")
        self.assertEqual(self.ink("NorthernLights", 8, 0.70)[0], "#9DA1A7")
        self.assertEqual(self.ink("SoftGlow", 8, 0.40)[0], "#8B9096")

    def test_light_mist_is_where_half_a_level_decides(self):
        self.assertEqual(self.ink("LightMist", 8, 0.40), ("#969BA0", "#969BA1"))
        self.assertEqual(self.ink("LightMist", 0, 0.85)[0], "#90959A")

    def test_the_floors_are_the_lowest_tint_that_keeps_the_text(self):
        floor = lambda style, blur: contrast.glass_floor(self.table[style], blur, self.colors)
        northern = [floor("NorthernLights", blur) for blur in (8, 16, 24, 32, 48)]
        self.assertEqual(northern, [70, 60, 50, 50, 40])
        self.assertEqual(floor("FlowingLight", 8), 40)
        for style in ("AuroraCurtains", "SoftGlow", "LightMist", "Starfield"):
            self.assertEqual(floor(style, 8), 30, style)

    def test_the_notes_print_what_the_app_does(self):
        text = contrast.glass_colour_tables(self.table)
        self.assertIn("| Northern lights | 70 % | `#9DA1A7` |", text)
        self.assertIn("| Light mist | 40 % | `#969BA0` | `#969BA1` |", text)
        self.assertIn("`#90959A`", text)

    def test_the_command_prints_the_notes(self):
        import contextlib
        import io

        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(contrast.main(["glass-colours"]), 0)
        self.assertIn("Northern lights", out.getvalue())
        self.assertEqual(out.getvalue().strip(), contrast.glass_colour_tables(self.table).strip())


class VideoGlassHaloTest(unittest.TestCase):
    """The text on the glass over the stream video (ADR 0061, decision 4), measured by the tool.

    The glass is the viewer's 40 percent of `surface` over the video, and the video is any frame.
    The text on it has a halo, the halo of the text on a background but at the opacity of 100 %.
    This takes the lightest frame there is, a white one, and a busy bright one of squares of white
    and black, lays the glass of the app on them (a blur of 8 dp on the copy, which is a fourth of
    the video, and the tint), and asks the tool's own model of the halo what the text keeps over
    its ring: the least ratio over every colour of the frame with every alpha of the ring, as
    `halo` takes it. The segmented controls are on the `surface` colour, which no frame reaches.
    `MenuGlassHaloTest` and `TextHaloTest` of the app read the same in what is drawn.
    """

    TINT = 0.40
    BLUR_PX = 4
    # The halo that the app draws on the glass: 3 dp wide, a blur of 2 dp, and 100 % opaque.
    HALO = (3, 2, 1.0)
    TEXTS = ("ink", "ink-2", "accent-soft", "danger-ink")
    DEVICES = ("phone", "tv")

    def setUp(self):
        self.colors = contrast.load_colors(TOKENS, (*WORDS, "danger-ink", "accent-tint"))
        self.surface = contrast.parse_hex(self.colors["surface"])
        self.page = contrast.parse_hex(self.colors["page"])
        self.lights = {
            token: float(contrast.relative_luminance(contrast.parse_hex(self.colors[token][:7])))
            for token in self.TEXTS
        }
        white = uniform(400, 800, (1.0, 1.0, 1.0))
        busy = white.copy()
        busy[(np.indices(busy.shape[:2]).sum(axis=0) // 8) % 2 == 1] = 0.0
        self.frames = {
            name: contrast.frame_colours(contrast.glass(frame, self.surface, self.TINT, self.BLUR_PX))
            for name, frame in (("white", white), ("busy", busy))
        }

    def worst(self, config):
        """The least ratio of each text colour with `config` over every frame, device and style."""
        found = {}
        for device in self.DEVICES:
            for text in contrast.HALO_TEXTS:
                geometry = contrast.halo_geometry(text, device)
                alphas = geometry.alphas[geometry.configs.index(config)]
                for colours in self.frames.values():
                    for token in self.TEXTS:
                        ratio = contrast.halo_worst_ratio(
                            colours, (alphas.min(), alphas.max()), self.page, self.lights[token]
                        )
                        found[token] = min(found.get(token, ratio), ratio)
        return found

    def test_every_text_keeps_4_5_over_the_lightest_glass_with_the_halo(self):
        for token, ratio in self.worst(self.HALO).items():
            with self.subTest(token=token):
                self.assertGreaterEqual(ratio, 4.5 * contrast.HALO_MARGIN)

    def test_without_a_halo_the_text_falls_short_over_it(self):
        # The check can fail: ink is 2.1:1 over the glass of 40 % over white, ink-2 1.5:1.
        for token, ratio in self.worst(contrast.HALO_NONE).items():
            with self.subTest(token=token):
                self.assertLess(ratio, 4.5)

    def test_the_halo_of_the_text_on_a_background_falls_short_for_the_danger_ink_on_a_tv(self):
        # The halo of the Library, 3 dp, 2 dp, 80 %, keeps ink and ink-2 but the red of the Quit row
        # is 4.40:1 on a TV, which is why the glass takes 100 %.
        worst = self.worst((3, 2, 0.8))
        self.assertLess(worst["danger-ink"], 4.5)
        self.assertGreaterEqual(worst["ink"], 4.5)
        self.assertGreaterEqual(worst["ink-2"], 4.5)

    def test_it_is_the_smallest_candidate_of_the_grid_that_keeps_all_four(self):
        passing = [
            config
            for config in contrast.halo_configs()
            if min(self.worst(config).values()) >= 4.5 * contrast.HALO_MARGIN
        ]
        self.assertEqual(passing[0], self.HALO)

    def test_the_labels_of_a_segmented_control_keep_4_5_over_their_solid_container(self):
        # An unselected label is ink-3 on the surface; the selected one is accent-soft over the
        # tint of the accent on the surface.
        self.assertGreaterEqual(contrast.hex_contrast(self.colors["ink-3"], self.colors["surface"]), 4.5)
        laid = contrast.over(self.colors["accent-tint"], self.surface)
        soft = contrast.parse_hex(self.colors["accent-soft"])
        self.assertGreaterEqual(contrast.rgb_contrast(soft, laid), 4.5)


class HaloGridTest(unittest.TestCase):
    """The halo of text straight on the background: the grid of what is tried, and the text."""

    def test_the_grid_is_the_stroke_the_blur_and_the_opacity_of_the_issue(self):
        self.assertEqual(contrast.HALO_STROKES_DP, (0, 1, 2, 3))
        self.assertEqual(contrast.HALO_BLURS_DP, (2, 4, 8))
        self.assertEqual(contrast.HALO_OPACITIES, (0.6, 0.8, 1.0))

    def test_the_candidates_run_from_the_smallest_halo_to_the_largest(self):
        configs = contrast.halo_configs()
        self.assertEqual(len(configs), 4 * 3 * 3)
        self.assertEqual(configs[0], (0, 2, 0.6))
        self.assertEqual(configs[1], (0, 2, 0.8))
        self.assertEqual(configs[3], (0, 4, 0.6))
        self.assertEqual(configs[-1], (3, 8, 1.0))
        self.assertEqual(configs, sorted(configs))

    def test_no_halo_is_a_candidate_of_its_own(self):
        self.assertEqual(contrast.HALO_NONE, (0, 0, 0.0))
        self.assertNotIn(contrast.HALO_NONE, contrast.halo_configs())

    def test_the_text_is_sized_in_sp_by_the_token_file_at_the_density_of_the_device(self):
        sizes = {
            ("title", "tv"): 24 * 2.0,
            ("title", "phone"): 18 * 3.0,
            ("label", "tv"): 13 * 2.0,
            ("label", "phone"): 11 * 3.0,
            ("body", "tv"): 16 * 2.0,
            ("body", "phone"): 14 * 3.0,
            ("small", "tv"): 14.5 * 2.0,
            ("small", "phone"): 12.5 * 3.0,
        }
        for (text, device), px in sizes.items():
            with self.subTest(text=text, device=device):
                self.assertEqual(contrast.text_size_px(text, device), px)

    def test_a_title_is_large_text_on_a_tv_only(self):
        # 24 sp regular or 18.66 sp bold is large; a title of 18 sp semibold on a phone is not.
        self.assertEqual(contrast.halo_limit("title", "tv"), 3.0)
        self.assertEqual(contrast.halo_limit("title", "phone"), 4.5)
        for text in ("label", "body", "small"):
            for device in ("tv", "phone"):
                self.assertEqual(contrast.halo_limit(text, device), 4.5)


class HaloModelTest(unittest.TestCase):
    """The halo as the app draws it: the glyphs widened by a stroke, blurred, in `page`."""

    def setUp(self):
        self.colors = tokens()
        self.page = contrast.parse_hex(self.colors["page"])

    def test_a_glyph_mask_is_coverage_where_the_text_is_and_nothing_elsewhere(self):
        mask = contrast.glyph_mask("body", "tv", pad=8)
        self.assertEqual(mask.dtype, np.float32)
        self.assertEqual(mask.ndim, 2)
        self.assertGreater(float(mask.max()), 0.95)
        self.assertLessEqual(float(mask.max()), 1.0)
        self.assertGreater(float((mask > 0).sum()), 500)
        self.assertLess(float((mask > 0).mean()), 0.5)
        self.assertEqual(float(mask[:8].max()), 0.0)
        self.assertEqual(float(mask[:, :8].max()), 0.0)

    def test_a_stroke_widens_every_glyph_by_its_width_in_dp(self):
        plain = contrast.glyph_mask("body", "tv", pad=20)
        wide = contrast.glyph_mask("body", "tv", stroke_dp=2, pad=20)
        self.assertEqual(plain.shape, wide.shape)
        self.assertTrue(np.all(wide >= plain - 1e-6))
        # 2 dp are 4 px on a TV: a pixel 3 px outside a glyph is covered, 6 px outside is not.
        rows, columns = np.nonzero(plain > 0.9)
        y, x = rows[np.argmin(columns)], columns.min()
        self.assertGreater(float(wide[y, x - 3]), 0.5)
        self.assertEqual(float(wide[y, x - 6]), 0.0)

    def test_a_label_is_set_in_capitals_with_the_spacing_of_the_kicker(self):
        spaced = contrast.glyph_mask("label", "tv")
        # 13 sp are 26 px on a TV, and capitals are wider than the lower case of the same size.
        self.assertGreater(spaced.shape[1], contrast.glyph_mask("small", "tv").shape[1] * 0.8)
        self.assertGreater(spaced.shape[1], 26 * 0.55 * len(contrast.HALO_SAMPLE))

    def test_the_ring_is_the_pixels_within_two_pixels_of_a_glyph_and_not_the_glyph(self):
        mask = np.zeros((9, 9), np.float32)
        mask[4, 4] = 1.0
        ring = contrast.ring_mask(mask)
        self.assertEqual(ring.shape, mask.shape)
        self.assertFalse(ring[4, 4])
        # The disc of radius 2 has 13 pixels, 12 of them round the glyph.
        self.assertEqual(int(ring.sum()), 12)
        self.assertTrue(ring[4, 6])
        self.assertTrue(ring[3, 5])
        self.assertFalse(ring[4, 7])
        self.assertFalse(ring[2, 2])

    def test_a_partly_covered_pixel_is_a_glyph_pixel_and_not_the_ring(self):
        mask = np.zeros((9, 9), np.float32)
        mask[4, 4] = 1.0
        mask[4, 5] = 0.2
        ring = contrast.ring_mask(mask)
        self.assertFalse(ring[4, 5])
        self.assertTrue(ring[4, 7])

    def test_the_alpha_of_the_halo_is_the_blurred_mask_at_the_opacity(self):
        mask = np.zeros((101, 101), np.float32)
        mask[50, 50] = 1.0
        alpha = contrast.halo_alpha(mask, blur_dp=2, opacity=0.5, density=2.0)
        blurred = contrast.gaussian_blur(mask[..., None], contrast.blur_sigma(2 * 2.0))[..., 0]
        self.assertEqual(alpha.shape, mask.shape)
        np.testing.assert_allclose(alpha, 0.5 * blurred, atol=1e-6)

    def test_a_larger_blur_spreads_the_halo_and_thins_it_at_the_glyph(self):
        mask = np.zeros((101, 101), np.float32)
        mask[40:60, 40:60] = 1.0
        small = contrast.halo_alpha(mask, 2, 1.0, 2.0)
        large = contrast.halo_alpha(mask, 8, 1.0, 2.0)
        self.assertGreater(float(small[50, 50]), float(large[50, 50]))
        self.assertGreater(float(large[50, 25]), float(small[50, 25]))

    def test_no_blur_is_the_mask_itself_at_the_opacity(self):
        mask = np.zeros((20, 20), np.float32)
        mask[5:9, 5:9] = 1.0
        mask[9, 9] = 0.5
        np.testing.assert_allclose(contrast.halo_alpha(mask, 0, 0.8, 3.0), 0.8 * mask, atol=1e-7)

    def test_no_opacity_is_no_halo(self):
        mask = np.ones((20, 20), np.float32)
        self.assertEqual(float(contrast.halo_alpha(mask, 4, 0.0, 2.0).max()), 0.0)

    def test_the_halo_is_composited_in_the_tint_at_its_alpha_in_the_encoded_values(self):
        image = uniform(4, 2, (1.0, 1.0, 1.0))
        alpha = np.array([[0.0, 0.25, 0.5, 1.0], [1.0, 0.5, 0.25, 0.0]], np.float32)
        out = contrast.halo_over(image, alpha, np.array([0.2, 0.4, 0.0]))
        np.testing.assert_allclose(out[0, 0], [1.0, 1.0, 1.0], atol=1e-6)
        np.testing.assert_allclose(out[0, 1], [0.8, 0.85, 0.75], atol=1e-6)
        np.testing.assert_allclose(out[0, 3], [0.2, 0.4, 0.0], atol=1e-6)

    def test_a_full_opacity_halo_with_a_wide_stroke_makes_the_page_around_the_glyph(self):
        plain = contrast.glyph_mask("body", "tv", pad=60)
        wide = contrast.glyph_mask("body", "tv", stroke_dp=8, pad=60)
        ring = contrast.ring_mask(plain)
        alpha = contrast.halo_alpha(wide, 2, 1.0, 2.0)
        white = uniform(plain.shape[1], plain.shape[0], (1.0, 1.0, 1.0))
        around = contrast.halo_over(white, alpha, self.page)[ring]
        np.testing.assert_allclose(
            around, np.broadcast_to(self.page, (int(ring.sum()), 3)), atol=2e-3
        )
        ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))
        ratios = contrast.halo_contrast(white[ring], alpha[ring], self.page, ink)
        self.assertAlmostEqual(
            float(ratios.min()),
            contrast.hex_contrast(self.colors["ink"], self.colors["page"]),
            delta=0.05,
        )

    def test_no_halo_leaves_the_contrast_of_the_background_as_it_is(self):
        pixels = np.array([[0.2, 0.3, 0.4], [0.9, 0.9, 0.9]], np.float32)
        ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))
        ratios = contrast.halo_contrast(pixels, np.zeros(2, np.float32), self.page, ink)
        np.testing.assert_allclose(
            ratios, contrast.contrast_ratio(contrast.relative_luminance(pixels), ink), rtol=1e-6
        )

    def test_a_text_is_placed_on_a_grid_of_six_by_four_over_the_frame(self):
        places = contrast.halo_placements((1080, 1920), (120, 386))
        self.assertEqual(len(places), 6 * 4)
        self.assertEqual(len(set(places)), 24)
        self.assertEqual(places[0], (0, 0))
        self.assertEqual(places[-1], (1920 - 386, 1080 - 120))
        self.assertEqual(len({x for x, _ in places}), 6)
        self.assertEqual(len({y for _, y in places}), 4)
        for x, y in places:
            self.assertTrue(0 <= x <= 1920 - 386 and 0 <= y <= 1080 - 120)


class HaloGeometryTest(unittest.TestCase):
    """What does not depend on the frame: the ring around the glyphs and the alpha of each halo."""

    def setUp(self):
        self.geometry = contrast.halo_geometry("body", "tv")
        self.index = {config: i for i, config in enumerate(self.geometry.configs)}

    def test_the_candidates_are_no_halo_and_then_the_grid(self):
        self.assertEqual(self.geometry.configs, [contrast.HALO_NONE] + contrast.halo_configs())

    def test_there_is_an_alpha_for_each_candidate_at_each_pixel_of_the_ring(self):
        ring = self.geometry.ring
        self.assertEqual(ring.dtype, bool)
        self.assertEqual(self.geometry.alphas.shape, (37, int(ring.sum())))
        self.assertEqual(ring.shape, self.geometry.size)
        self.assertEqual(float(self.geometry.alphas[0].max()), 0.0)

    def test_the_opacity_scales_the_alpha_of_a_stroke_and_a_blur(self):
        full = self.geometry.alphas[self.index[(2, 4, 1.0)]]
        part = self.geometry.alphas[self.index[(2, 4, 0.6)]]
        np.testing.assert_allclose(part, 0.6 * full, atol=1e-6)
        self.assertLessEqual(float(full.max()), 1.0)

    def test_a_wider_stroke_puts_more_halo_on_the_ring(self):
        means = [
            float(self.geometry.alphas[self.index[(stroke, 2, 1.0)]].mean())
            for stroke in (0, 1, 2, 3)
        ]
        self.assertEqual(means, sorted(means))
        self.assertGreater(means[3], 0.8)
        self.assertLess(means[0], 0.5)

    def test_the_ring_of_a_phone_is_at_its_own_density(self):
        phone = contrast.halo_geometry("body", "phone")
        self.assertGreater(phone.size[1], self.geometry.size[1])
        self.assertGreater(float(phone.alphas[phone.configs.index((1, 2, 1.0))].mean()), 0.5)


class HaloWorstTest(unittest.TestCase):
    """The worst ratio over every pixel of a frame, with every alpha of the ring laid on it."""

    def setUp(self):
        self.colors = tokens()
        self.page = contrast.parse_hex(self.colors["page"])
        self.ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))
        self.ink2 = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink-2"])))

    def test_the_colours_of_a_frame_are_listed_once_each(self):
        frame = uniform(8, 6, (0.2, 0.4, 0.6))
        frame[2, 3] = (1.0, 1.0, 1.0)
        frame[4, 4] = (1.0, 1.0, 1.0)
        found = contrast.frame_colours(frame)
        self.assertEqual(found.shape, (2, 3))
        rows = sorted(tuple(np.round(row * 255).astype(int)) for row in found)
        self.assertEqual(rows, [(51, 102, 153), (255, 255, 255)])

    def test_the_colours_are_the_ones_of_the_8_bit_picture(self):
        frame = uniform(4, 4, (0.2, 0.4, 0.6))
        frame[0, 0] = (0.2 + 0.0004, 0.4, 0.6)
        self.assertEqual(contrast.frame_colours(frame).shape, (1, 3))

    def test_the_worst_ratio_of_a_bright_colour_is_at_the_thinnest_part_of_the_halo(self):
        white = np.ones((1, 3), np.float32)
        worst = contrast.halo_worst_ratio(white, (0.5, 0.9), self.page, self.ink)
        at = lambda alpha: float(
            contrast.halo_contrast(white, np.array([alpha], np.float32), self.page, self.ink)[0]
        )
        self.assertAlmostEqual(worst, at(0.5), places=5)
        self.assertLess(worst, at(0.9))

    def test_the_worst_colour_counts_and_not_the_average(self):
        colours = np.array([[0.05, 0.05, 0.05], [0.9, 0.9, 0.9], [0.1, 0.2, 0.1]], np.float32)
        worst = contrast.halo_worst_ratio(colours, (0.6, 0.6), self.page, self.ink)
        bright = contrast.halo_contrast(
            colours[1:2], np.array([0.6], np.float32), self.page, self.ink
        )
        self.assertAlmostEqual(worst, float(bright[0]), places=5)

    def test_a_range_of_alphas_that_darkens_a_pixel_to_the_text_is_one_to_one(self):
        # White at an alpha of 0 is brighter than ink-2, and at 0.9 it is darker: in between there
        # is an alpha at which it is as bright as the text, and the ratio there is 1:1.
        white = np.ones((1, 3), np.float32)
        self.assertEqual(contrast.halo_worst_ratio(white, (0.0, 0.9), self.page, self.ink2), 1.0)

    def test_no_halo_is_the_ratio_of_the_frame_itself(self):
        colours = np.array([[0.3, 0.5, 0.7], [0.1, 0.1, 0.1]], np.float32)
        worst = contrast.halo_worst_ratio(colours, (0.0, 0.0), self.page, self.ink)
        expected = contrast.contrast_ratio(contrast.relative_luminance(colours), self.ink).min()
        self.assertAlmostEqual(worst, float(expected), places=5)


class HaloMeasureTest(unittest.TestCase):
    def setUp(self):
        self.colors = tokens()
        self.page = contrast.parse_hex(self.colors["page"])
        self.geometry = contrast.halo_geometry("body", "tv")
        self.ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))

    def measure(self, frame, texts=("body",)):
        return contrast.measure_halo_frame(frame, "tv", self.colors, texts=texts)

    def test_with_no_halo_a_flat_frame_gives_the_ratio_of_the_text_on_that_colour(self):
        # The case of Table A: no halo and no opacity leave the background as it is.
        colour = np.array([90, 128, 166], np.float32) / 255
        stats = self.measure(uniform(1920, 1080, colour))["body"][contrast.HALO_NONE]["ink"]
        expected = contrast.contrast_stats(contrast.relative_luminance(colour)[None], self.ink)
        for key in ("min", "p1", "p5"):
            self.assertAlmostEqual(stats[key], expected[key], places=4)

    def test_both_text_tokens_are_measured(self):
        stats = self.measure(uniform(1920, 1080, self.page))["body"][contrast.HALO_NONE]
        self.assertEqual(sorted(stats), ["ink", "ink-2"])
        self.assertLess(stats["ink-2"]["min"], stats["ink"]["min"])

    def test_a_bigger_halo_gives_more_contrast_over_a_bright_frame(self):
        white = uniform(1920, 1080, (1.0, 1.0, 1.0))
        by_config = self.measure(white)["body"]
        none = by_config[contrast.HALO_NONE]["ink"]["min"]
        wider = ((1, 2, 1.0), (2, 2, 1.0), (3, 2, 1.0))
        ratios = [by_config[config]["ink"]["min"] for config in wider]
        # Where a halo darkens white only a little, it is as bright as the text and the ratio is
        # lower than it was, so only the wider strokes are compared with no halo at all.
        self.assertEqual(ratios, sorted(ratios))
        self.assertLess(none, 1.5)
        self.assertGreater(ratios[1], none)
        self.assertGreater(ratios[2], 5.0)

    def test_a_bright_pixel_counts_wherever_it_is_on_the_frame(self):
        # The place of the pixel is none of the six by four places of the grid, and no halo is
        # laid on it, so it is the ratio of ink over it as it is.
        frame = uniform(1920, 1080, self.page)
        frame[523, 877] = 1.0
        stats = self.measure(frame)["body"][contrast.HALO_NONE]["ink"]
        self.assertAlmostEqual(
            stats["min"], contrast.hex_contrast(self.colors["ink"], "#FFFFFF"), places=3
        )

    def test_the_worst_case_with_a_halo_is_a_bright_pixel_under_the_thinnest_part_of_it(self):
        config = (3, 2, 1.0)
        thinnest = float(self.geometry.alphas[self.geometry.configs.index(config)].min())
        frame = uniform(1920, 1080, self.page)
        frame[731, 1203] = 1.0
        stats = self.measure(frame)["body"][config]["ink"]
        expected = contrast.halo_contrast(
            np.ones((1, 3), np.float32), np.array([thinnest], np.float32), self.page, self.ink
        )
        self.assertAlmostEqual(stats["min"], float(expected[0]), places=3)

    def test_the_minimum_does_not_move_with_the_place_of_a_pixel_by_a_pixel(self):
        found = []
        r, c = np.argwhere(self.geometry.ring)[0]
        for y, x in ((r, c), (r + 1, c), (r, c + 1), (r + 3, c + 5)):
            frame = uniform(1920, 1080, self.page)
            frame[y, x] = (0.8, 0.8, 0.9)
            found.append(self.measure(frame)["body"][(2, 2, 0.8)]["ink-2"]["min"])
        self.assertAlmostEqual(max(found), min(found), places=6)

    def test_the_halo_hides_a_bright_pixel_on_the_ring_in_part(self):
        ring = np.argwhere(self.geometry.ring)[0]
        frame = uniform(1920, 1080, self.page)
        frame[ring[0], ring[1]] = 1.0
        by_config = self.measure(frame)["body"]
        self.assertGreater(
            by_config[(3, 2, 1.0)]["ink"]["min"], by_config[contrast.HALO_NONE]["ink"]["min"] * 5
        )

    def test_p1_and_p5_are_not_moved_by_one_bright_pixel(self):
        # One pixel in a thousand is not the 1% that p1 and p5 report.
        ring = np.argwhere(self.geometry.ring)[0]
        x, y = contrast.halo_placements((1080, 1920), self.geometry.size)[-1]
        frame = uniform(1920, 1080, self.page)
        frame[y + ring[0], x + ring[1]] = 1.0
        stats = self.measure(frame)["body"][contrast.HALO_NONE]["ink"]
        self.assertLess(stats["min"], 1.3)
        self.assertGreater(stats["p1"], 10.0)
        self.assertGreater(stats["p5"], 10.0)

    def test_a_source_folds_the_frames_to_the_worst_one(self):
        ring = np.argwhere(self.geometry.ring)[0]
        dark = uniform(1920, 1080, self.page)
        bright = dark.copy()
        bright[ring[0], ring[1]] = 1.0
        rows = contrast.measure_halo_source(
            [dark, bright, dark], "AuroraCurtains", "tv", self.colors, texts=("body",)
        )
        self.assertEqual(len(rows), 1 * 2 * 37)
        row = next(
            r
            for r in rows
            if (r["text"], r["token"], r["stroke_dp"], r["blur_dp"], r["opacity"])
            == ("body", "ink", 0, 0, 0.0)
        )
        self.assertEqual(row["style"], "AuroraCurtains")
        self.assertEqual(row["device"], "tv")
        self.assertEqual(row["worst_frame"], 1)
        self.assertLess(row["min"], 1.3)
        self.assertLessEqual(row["min"], row["p1"])
        self.assertLessEqual(row["p1"], row["p5"])
        json.dumps(rows)

    def test_a_folder_of_frames_is_read_by_style_and_device(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "Starfield-tv").mkdir()
            (Path(folder) / "notes").mkdir()
            page = tuple(round(c * 255) for c in self.page)
            Image.new("RGB", (1920, 1080), page).save(Path(folder) / "Starfield-tv" / "00.png")
            result = contrast.measure_halo_folder(Path(folder), self.colors, texts=("body",))
        self.assertEqual({(r["style"], r["device"]) for r in result["halo"]}, {("Starfield", "tv")})
        self.assertEqual(result["meta"]["strokes_dp"], [0, 1, 2, 3])
        self.assertEqual(result["meta"]["placements"], [6, 4])
        json.dumps(result)


def halo_rows(passing, ratio=10.0, failing=2.0, texts=("title", "body"), devices=("tv",)):
    """Rows for every candidate over `texts` and `devices`: those in `passing` keep `ratio`."""
    rows = []
    for config in [contrast.HALO_NONE] + contrast.halo_configs():
        stroke, blur, opacity = config
        for text in texts:
            for device in devices:
                for token in contrast.HALO_TOKENS:
                    value = ratio if config in passing else failing
                    rows.append(
                        {
                            "style": "AuroraCurtains",
                            "device": device,
                            "text": text,
                            "token": token,
                            "stroke_dp": stroke,
                            "blur_dp": blur,
                            "opacity": opacity,
                            "min": value,
                            "p1": value + 1,
                            "p5": value + 2,
                            "worst_frame": 0,
                        }
                    )
    return rows


class HaloChoiceTest(unittest.TestCase):
    def test_the_smallest_halo_that_keeps_the_ratios_is_chosen(self):
        rows = halo_rows({(2, 2, 0.8), (2, 2, 1.0), (3, 2, 0.6), (2, 8, 1.0)})
        self.assertEqual(contrast.choose_halo(rows), (2, 2, 0.8))

    def test_the_least_stroke_comes_first_then_the_least_blur_then_the_least_opacity(self):
        self.assertEqual(contrast.choose_halo(halo_rows({(3, 2, 0.6), (2, 8, 1.0)})), (2, 8, 1.0))
        self.assertEqual(contrast.choose_halo(halo_rows({(2, 8, 0.6), (2, 4, 1.0)})), (2, 4, 1.0))
        self.assertEqual(contrast.choose_halo(halo_rows({(2, 4, 1.0), (2, 4, 0.8)})), (2, 4, 0.8))

    def test_no_halo_is_never_the_choice(self):
        rows = halo_rows({contrast.HALO_NONE, (3, 8, 1.0)})
        self.assertEqual(contrast.choose_halo(rows), (3, 8, 1.0))

    def test_nothing_is_chosen_when_no_candidate_of_the_grid_keeps_the_ratios(self):
        self.assertIsNone(contrast.choose_halo(halo_rows(set())))

    def test_one_row_below_its_limit_rejects_the_candidate(self):
        rows = halo_rows({(2, 2, 0.8), (3, 2, 0.8)})
        low = next(
            r
            for r in rows
            if (r["stroke_dp"], r["blur_dp"], r["opacity"], r["text"], r["token"])
            == (2, 2, 0.8, "body", "ink-2")
        )
        low["min"] = 4.4
        self.assertEqual(contrast.choose_halo(rows), (3, 2, 0.8))

    def test_a_ratio_must_clear_its_limit_by_the_margin_of_the_model(self):
        # 4.5 : 1 with 2 percent to spare is 4.59.
        self.assertIsNone(contrast.choose_halo(halo_rows({(2, 2, 0.8)}, ratio=4.55)))
        self.assertEqual(contrast.choose_halo(halo_rows({(2, 2, 0.8)}, ratio=4.6)), (2, 2, 0.8))

    def test_a_tv_title_is_large_text_and_is_measured_with_ink_only(self):
        rows = halo_rows({(2, 2, 0.8)}, texts=("title",), ratio=3.2)
        for row in rows:
            if row["token"] != "ink":
                row["min"] = 1.0
        self.assertEqual(contrast.choose_halo(rows), (2, 2, 0.8))

    def test_a_phone_title_is_small_text_and_needs_4_5_to_1(self):
        rows = halo_rows({(2, 2, 0.8)}, texts=("title",), devices=("phone",), ratio=3.2)
        self.assertIsNone(contrast.choose_halo(rows))

    def test_the_choice_is_printed_in_words(self):
        self.assertEqual(
            contrast.halo_choice_text((2, 2, 0.8)), "stroke 2 dp, blur 2 dp, opacity 80 %"
        )

    def test_the_tables_name_the_candidates_and_the_choice(self):
        rows = halo_rows({(2, 2, 0.8)}, devices=("tv", "phone"))
        text = contrast.halo_tables({"meta": {}, "halo": rows})
        self.assertIn("Table D", text)
        self.assertIn("Table E", text)
        self.assertIn("| 2 | 2 | 80 % |", text)
        self.assertIn("The choice: stroke 2 dp, blur 2 dp, opacity 80 %.", text)

    def test_the_tables_say_when_no_candidate_does_it(self):
        text = contrast.halo_tables({"meta": {}, "halo": halo_rows(set())})
        self.assertIn("No candidate of the grid", text)

    def test_the_command_measures_a_folder_and_writes_the_result(self):
        import contextlib
        import io

        with tempfile.TemporaryDirectory() as folder:
            frames = Path(folder) / "frames"
            (frames / "Starfield-tv").mkdir(parents=True)
            Image.new("RGB", (1920, 1080), (14, 16, 19)).save(frames / "Starfield-tv" / "00.png")
            target = Path(folder) / "halo.json"
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                args = ["halo", str(frames), "--json", str(target), "--texts", "body"]
                code = contrast.main(args)
            self.assertEqual(code, 0)
            written = json.loads(target.read_text(encoding="utf-8"))
            self.assertEqual({r["text"] for r in written["halo"]}, {"body"})
        self.assertIn("Table D", out.getvalue())


class HaloHighContrastTest(unittest.TestCase):
    """High contrast: the page lies over the background at 0.85 and the text is whiter."""

    def setUp(self):
        self.colors = tokens()
        self.page = contrast.parse_hex(self.colors["page"])

    def test_the_high_contrast_colours_come_from_the_token_file(self):
        found = contrast.load_high_contrast(TOKENS)
        self.assertEqual(found["ink"].upper(), "#FFFFFF")
        self.assertEqual(found["ink-2"].upper(), "#E8EAED")
        self.assertEqual(found["dim"], 0.85)

    def test_the_veil_is_the_page_over_the_frame_at_the_dim_in_the_8_bit_values(self):
        frame = uniform(3, 2, (1.0, 0.5, 0.0))
        veiled = contrast.halo_veil(frame, self.page, 0.85)
        expected = np.rint((0.85 * self.page + 0.15 * np.array([1.0, 0.5, 0.0])) * 255) / 255
        np.testing.assert_allclose(veiled[0, 0], expected, atol=1e-6)
        self.assertEqual(veiled.shape, frame.shape)

    def test_no_dim_leaves_the_frame_as_it_is(self):
        frame = uniform(3, 2, (0.9, 0.5, 0.1))
        np.testing.assert_allclose(contrast.halo_veil(frame, self.page, 0.0), frame, atol=1e-6)

    def test_a_dimmed_frame_is_measured_under_the_veil(self):
        colors = dict(self.colors, ink="#FFFFFF")
        white = uniform(1920, 1080, (1.0, 1.0, 1.0))
        stats = contrast.measure_halo_frame(white, "tv", colors, texts=("body",), dim=0.85)
        veiled = np.rint((0.85 * self.page + 0.15 * np.ones(3)) * 255) / 255
        expected = contrast.contrast_ratio(
            contrast.relative_luminance(veiled), contrast.relative_luminance(np.ones(3))
        )
        self.assertAlmostEqual(
            stats["body"][contrast.HALO_NONE]["ink"]["min"], float(expected), places=4
        )

    def test_the_text_needs_7_to_1_under_high_contrast_whatever_its_size(self):
        for text in contrast.HALO_TEXTS:
            for device in ("tv", "phone"):
                with self.subTest(text=text, device=device):
                    self.assertEqual(contrast.halo_limit(text, device, high_contrast=True), 7.0)

    def test_the_table_says_every_loose_text_keeps_7_to_1_when_it_does(self):
        rows = halo_rows({(3, 2, 0.8)}, ratio=9.0, devices=("tv", "phone"))
        text = contrast.halo_high_contrast_tables({"meta": {}, "halo": rows}, (3, 2, 0.8))
        self.assertIn("Table G", text)
        self.assertIn("Table H", text)
        self.assertIn("every text keeps 7:1", text)
        self.assertIn("stroke 3 dp, blur 2 dp, opacity 80 %", text)

    def test_the_table_names_the_text_that_falls_short(self):
        rows = halo_rows({(3, 2, 0.8)}, ratio=9.0, devices=("tv",))
        low = next(
            r
            for r in rows
            if (r["stroke_dp"], r["blur_dp"], r["opacity"], r["text"], r["token"])
            == (3, 2, 0.8, "body", "ink-2")
        )
        low["min"] = 6.5
        low["style"] = "Starfield"
        text = contrast.halo_high_contrast_tables({"meta": {}, "halo": rows}, (3, 2, 0.8))
        self.assertIn("falls short of 7:1", text)
        self.assertIn("6.50", text)
        self.assertIn("Starfield", text)

    def test_the_command_measures_under_high_contrast_and_prints_the_tables(self):
        import contextlib
        import io

        with tempfile.TemporaryDirectory() as folder:
            frames = Path(folder) / "frames"
            (frames / "Starfield-tv").mkdir(parents=True)
            Image.new("RGB", (1920, 1080), (255, 255, 255)).save(frames / "Starfield-tv" / "00.png")
            target = Path(folder) / "halo-hc.json"
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                args = ["halo", str(frames), "--json", str(target)]
                args += ["--texts", "body", "--high-contrast", "--halo", "3", "2", "80"]
                code = contrast.main(args)
            self.assertEqual(code, 0)
            written = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual(written["meta"]["high_contrast"]["dim"], 0.85)
        self.assertEqual(written["meta"]["colors"]["ink"].upper(), "#FFFFFF")
        self.assertIn("Table G", out.getvalue())


class HaloTokensTest(unittest.TestCase):
    """`--tokens` names the token file of the halo, as it does for `measure`."""

    def custom_tokens(self, folder):
        data = json.loads(TOKENS.read_text(encoding="utf-8"))
        data["color"]["ink"]["$value"] = "#ABCDEF"
        data["size"]["tv"]["body"]["$value"] = "30sp"
        data["high-contrast"]["ink"]["$value"] = "#FEDCBA"
        data["high-contrast"]["dim"]["$value"] = 0.5
        path = Path(folder) / "tokens.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def run_command(self, folder, *extra):
        frames = Path(folder) / "frames"
        (frames / "Starfield-tv").mkdir(parents=True)
        Image.new("RGB", (1920, 1080), (14, 16, 19)).save(frames / "Starfield-tv" / "00.png")
        target = Path(folder) / "halo.json"
        args = ["halo", str(frames), "--json", str(target), "--texts", "body", *extra]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(contrast.main(args), 0)
        return json.loads(target.read_text(encoding="utf-8"))

    def test_the_colours_come_from_the_token_file(self):
        with tempfile.TemporaryDirectory() as folder:
            written = self.run_command(folder, "--tokens", str(self.custom_tokens(folder)))
        self.assertEqual(written["meta"]["colors"]["ink"], "#ABCDEF")
        bare = [
            row
            for row in written["halo"]
            if row["token"] == "ink" and (row["stroke_dp"], row["blur_dp"]) == (0, 0)
        ]
        self.assertEqual(len(bare), 1)
        expected = contrast.hex_contrast("#ABCDEF", "#0E1013")
        self.assertAlmostEqual(bare[0]["min"], expected, delta=1e-4)

    def test_the_text_sizes_come_from_the_token_file(self):
        with tempfile.TemporaryDirectory() as folder:
            tokens_json = self.custom_tokens(folder)
            written = self.run_command(folder, "--tokens", str(tokens_json))
            tall = contrast.halo_geometry("body", "tv", tokens_json).size
        self.assertEqual(written["meta"]["texts"]["body"]["sizes_sp"]["tv"], 30.0)
        self.assertGreater(tall[0], contrast.halo_geometry("body", "tv").size[0])

    def test_the_high_contrast_colours_and_dim_come_from_the_token_file(self):
        with tempfile.TemporaryDirectory() as folder:
            written = self.run_command(
                folder,
                *("--tokens", str(self.custom_tokens(folder))),
                *("--high-contrast", "--halo", "3", "2", "80"),
            )
        self.assertEqual(written["meta"]["colors"]["ink"], "#FEDCBA")
        self.assertEqual(written["meta"]["high_contrast"]["dim"], 0.5)

    def test_without_it_the_tokens_of_the_repository_are_used(self):
        with tempfile.TemporaryDirectory() as folder:
            written = self.run_command(folder)
        self.assertEqual(written["meta"]["colors"]["ink"], tokens()["ink"])
        self.assertEqual(written["meta"]["texts"]["body"]["sizes_sp"]["tv"], 16.0)


class HaloIconPathTest(unittest.TestCase):
    """The icons of the app, read from the source of `BorealisIcons`, and the paths as polylines."""

    def test_the_icons_are_read_from_the_icon_set_of_the_app(self):
        icons = contrast.read_icons()
        self.assertEqual(icons.stroke, 2.0)
        self.assertEqual(icons.paths["Back"], ("M19 12H5", "M12 19l-7-7 7-7"))
        self.assertEqual(icons.paths["Add"], ("M12 5v14M5 12h14",))
        self.assertEqual(icons.paths["Check"], ("M5 12.5l4.5 4.5L19 7.5",))
        self.assertEqual(
            icons.paths["Visibility"],
            (
                "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z",
                "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            ),
        )
        self.assertEqual(icons.paths["VisibilityOff"][2], "M4 4l16 16")

    def test_a_path_that_is_a_constant_in_the_source_is_read_whole(self):
        paths = contrast.read_icons().paths
        self.assertIn("Gear", paths)
        gear, hub = paths["Gear"]
        # The gear is written in several strings, added together.
        self.assertTrue(gear.startswith("M10.38 4.98L10.75 2.48"))
        self.assertTrue(gear.endswith("A7.2 7.2 0 0 1 10.38 4.98Z"))
        self.assertGreater(len(gear), 300)
        self.assertEqual(hub, "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0")

    def test_a_declaration_that_the_formatter_wrapped_is_read_all_the_same(self):
        source = """
object BorealisIcons {
    val Short: ImageVector by lazy { icon("Short", "M0 0h5") }

    val Wrapped: ImageVector
        by lazy {
        icon(
            "Wrapped",
            FIRST,
            "M1 1l2 2",
        )
    }

    val Called: ImageVector by lazy {
        icon("Called", "M3 3h4")
    }

    private const val FIRST =
        "M2 2h4" +
            "v4"
    private const val STROKE = 2.5f
}
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "BorealisIcons.kt"
            path.write_text(source, encoding="utf-8")
            icons = contrast.read_icons(path)
        self.assertEqual(icons.stroke, 2.5)
        self.assertEqual(
            icons.paths,
            {
                "Short": ("M0 0h5",),
                "Wrapped": ("M2 2h4v4", "M1 1l2 2"),
                "Called": ("M3 3h4",),
            },
        )

    def test_the_icons_that_are_measured_are_in_the_set(self):
        icons = contrast.read_icons()
        for name in contrast.HALO_ICONS:
            self.assertIn(name, icons.paths)

    def test_a_path_is_a_polyline_in_the_units_of_the_grid(self):
        lines = contrast.icon_polylines("M19 12H5")
        self.assertEqual(len(lines), 1)
        np.testing.assert_allclose(lines[0], [[19, 12], [5, 12]])

    def test_relative_commands_move_from_the_current_point(self):
        lines = contrast.icon_polylines("M12 19l-7-7 7-7")
        self.assertEqual(len(lines), 1)
        np.testing.assert_allclose(lines[0], [[12, 19], [5, 12], [12, 5]])

    def test_a_move_starts_a_new_polyline(self):
        lines = contrast.icon_polylines("M12 5v14M5 12h14")
        self.assertEqual(len(lines), 2)
        np.testing.assert_allclose(lines[0], [[12, 5], [12, 19]])
        np.testing.assert_allclose(lines[1], [[5, 12], [19, 12]])

    def test_a_closed_path_ends_where_it_started(self):
        lines = contrast.icon_polylines("M3.5 4.5h17v11h-17z")
        self.assertEqual(len(lines), 1)
        np.testing.assert_allclose(
            lines[0], [[3.5, 4.5], [20.5, 4.5], [20.5, 15.5], [3.5, 15.5], [3.5, 4.5]]
        )

    def test_an_arc_is_a_circle_of_its_radius_round_its_centre(self):
        lines = contrast.icon_polylines("M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
        self.assertGreater(len(lines), 0)
        points = np.concatenate(lines)
        self.assertGreater(len(points), 40)
        distances = np.hypot(points[:, 0] - 12, points[:, 1] - 12)
        self.assertLess(float(abs(distances - 3).max()), 0.02)
        # It goes all the way round: the top and the bottom of the circle are on it.
        self.assertLess(float(points[:, 1].min()), 9.05)
        self.assertGreater(float(points[:, 1].max()), 14.95)

    def test_a_smooth_curve_goes_on_from_the_last_curve(self):
        lines = contrast.icon_polylines("M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z")
        self.assertGreater(len(lines), 0)
        points = np.concatenate(lines)
        for corner in ((2, 12), (12, 5), (22, 12), (12, 19)):
            nearest = float(np.hypot(*(points - corner).T).min())
            self.assertLess(nearest, 0.05, corner)
        self.assertAlmostEqual(float(points[:, 1].min()), 5.0, delta=0.05)
        self.assertAlmostEqual(float(points[:, 1].max()), 19.0, delta=0.05)
        np.testing.assert_allclose(lines[0][0], lines[0][-1], atol=1e-6)

    def test_every_icon_of_the_app_is_drawn_by_the_parser(self):
        icons = contrast.read_icons()
        self.assertGreater(len(icons.paths), 0)
        for name, paths in icons.paths.items():
            for data in paths:
                with self.subTest(icon=name, path=data[:20]):
                    lines = contrast.icon_polylines(data)
                    self.assertGreater(len(lines), 0)
                    points = np.concatenate(lines)
                    self.assertTrue(np.all(np.isfinite(points)))
                    self.assertGreaterEqual(float(points.min()), 0.0)
                    self.assertLessEqual(float(points.max()), contrast.HALO_ICON_GRID)


class HaloIconMaskTest(unittest.TestCase):
    """An icon as a glyph mask: its centre line stroked 2 units wide, 24 dp square."""

    def arm(self, mask, column):
        """How much of a column of the mask is covered, which is the width of a stroke across it."""
        return float(mask[:, column].sum())

    def test_an_icon_is_24_dp_at_the_density_of_the_device(self):
        for device, density in (("tv", 2.0), ("phone", 3.0)):
            with self.subTest(device=device):
                mask = contrast.glyph_mask("Add", device, pad=8)
                columns = np.nonzero(mask.max(axis=0) > 0.5)[0]
                self.assertGreater(len(columns), 0)
                # The arms run from 5 to 19 units, and the round caps add half a stroke: 16 units.
                self.assertAlmostEqual(
                    int(columns.max() - columns.min() + 1), 16 * density, delta=1
                )

    def test_the_stroke_of_an_icon_is_two_units_wide(self):
        mask = contrast.glyph_mask("Add", "tv", pad=8)
        # 7 units from the left edge of the grid, across the horizontal arm: 2 units, 4 px.
        self.assertAlmostEqual(self.arm(mask, 8 + 14), 4.0, delta=0.3)
        phone = contrast.glyph_mask("Add", "phone", pad=8)
        self.assertAlmostEqual(self.arm(phone, 8 + 21), 6.0, delta=0.3)

    def test_the_mask_is_coverage_and_nothing_outside_the_icon(self):
        mask = contrast.glyph_mask("Add", "tv", pad=8)
        self.assertEqual(mask.dtype, np.float32)
        self.assertEqual(mask.shape, (48 + 16, 48 + 16))
        self.assertEqual(float(mask[:8].max()), 0.0)
        self.assertEqual(float(mask[:, :8].max()), 0.0)
        self.assertGreater(float(mask.max()), 0.95)
        self.assertLessEqual(float(mask.max()), 1.0)
        # The corners of the grid are empty: a plus has no part there.
        self.assertEqual(float(mask[8 : 8 + 6, 8 : 8 + 6].max()), 0.0)

    def test_a_halo_stroke_widens_the_icon_by_its_width_on_every_side(self):
        mask = contrast.glyph_mask("Add", "tv", stroke_dp=3, pad=10)
        # 2 units of the stroke and 3 dp (6 px) on each side of the arm: 2 + 6 + 6 = 8 dp, 16 px.
        self.assertAlmostEqual(self.arm(mask, 10 + 14), 16.0, delta=0.6)
        plain = contrast.glyph_mask("Add", "tv", pad=10)
        self.assertEqual(mask.shape, plain.shape)
        self.assertTrue(np.all(mask >= plain - 1e-6))

    def test_the_back_arrow_is_a_shaft_and_the_head_of_an_arrow(self):
        mask = contrast.glyph_mask("Back", "tv", pad=4)

        def at(x, y):
            return float(mask[4 + round(y * 2), 4 + round(x * 2)])

        self.assertGreater(at(12, 12), 0.9)  # the shaft
        self.assertGreater(at(18, 12), 0.9)  # the far end of the shaft
        self.assertGreater(at(5, 12), 0.9)  # the tip
        self.assertGreater(at(8.5, 8.5), 0.9)  # the upper stroke of the head
        self.assertGreater(at(8.5, 15.5), 0.9)  # the lower stroke of the head
        self.assertEqual(at(19.5, 4), 0.0)  # a corner of the grid
        self.assertEqual(at(12, 8.5), 0.0)  # between the shaft and the head

    def test_the_gear_has_a_hole_in_the_middle_and_the_hub_round_it(self):
        mask = contrast.glyph_mask("Gear", "phone", pad=4)

        def at(x, y):
            return float(mask[4 + round(y * 3), 4 + round(x * 3)])

        self.assertEqual(at(12, 12), 0.0)  # the hole of the hub, of radius 2 units
        self.assertGreater(at(12, 9), 0.9)  # the hub: a circle of radius 3
        self.assertGreater(at(12, 2.5), 0.9)  # a tooth

    def test_the_eye_is_a_lid_a_pupil_and_nothing_between(self):
        for name in ("Visibility", "VisibilityOff"):
            with self.subTest(icon=name):
                mask = contrast.glyph_mask(name, "tv", pad=4)

                def at(x, y):
                    return float(mask[4 + round(y * 2), 4 + round(x * 2)])

                self.assertGreater(at(12, 5), 0.9)  # the top of the lid
                self.assertGreater(at(12, 9), 0.9)  # the top of the pupil
                self.assertEqual(at(12, 7), 0.0)  # between the lid and the pupil
                # The hole in the pupil, which the slash of the other icon runs through.
                self.assertEqual(at(12, 12) > 0.9, name == "VisibilityOff")
        slashed = contrast.glyph_mask("VisibilityOff", "tv", pad=4)
        plain = contrast.glyph_mask("Visibility", "tv", pad=4)
        self.assertGreater(float(slashed.sum()), float(plain.sum()))

    def test_the_icons_differ(self):
        sums = {n: float(contrast.glyph_mask(n, "tv", pad=4).sum()) for n in contrast.HALO_ICONS}
        self.assertEqual(len({round(v) for v in sums.values()}), len(sums))


class HaloIconMeasureTest(unittest.TestCase):
    """The halo of an icon: the ring round it and the ratio over a frame."""

    CHOICE = (3, 2, 0.8)

    def setUp(self):
        self.colors = tokens()
        self.page = contrast.parse_hex(self.colors["page"])
        self.ink = float(contrast.relative_luminance(contrast.parse_hex(self.colors["ink"])))

    def test_an_icon_needs_3_to_1_on_every_device_and_under_high_contrast(self):
        for name in contrast.HALO_ICONS:
            for device in ("tv", "phone"):
                for high in (False, True):
                    with self.subTest(icon=name, device=device, high_contrast=high):
                        self.assertEqual(contrast.halo_limit(name, device, high), 3.0)

    def test_the_icons_are_not_text_styles(self):
        for name in contrast.HALO_ICONS:
            self.assertNotIn(name, contrast.HALO_TEXTS)

    def test_the_ring_of_an_icon_has_an_alpha_for_each_candidate(self):
        geometry = contrast.halo_geometry("Back", "tv")
        ring = geometry.ring
        self.assertEqual(geometry.configs, [contrast.HALO_NONE] + contrast.halo_configs())
        self.assertEqual(geometry.alphas.shape, (37, int(ring.sum())))
        # 24 dp are 48 px, and the ring is two pixels wide round the arrow.
        self.assertLess(ring.shape[0], 48 + 2 * contrast.HALO_RING_PX + 2)
        self.assertLess(int(ring.sum()), 48 * 48 // 3)

    def test_the_chosen_halo_covers_the_ring_of_an_icon_thickly(self):
        for name in contrast.HALO_ICONS:
            geometry = contrast.halo_geometry(name, "tv")
            alphas = geometry.alphas[geometry.configs.index(self.CHOICE)]
            with self.subTest(icon=name):
                # A 2 unit stroke widened by 3 dp on each side is thick: the ring is well inside.
                self.assertGreater(float(alphas.mean()), 0.7)
                self.assertLessEqual(float(alphas.max()), 0.8 + 1e-6)

    def test_an_icon_is_measured_as_a_text_style_is(self):
        stats = contrast.measure_halo_frame(
            uniform(1920, 1080, (1.0, 1.0, 1.0)), "tv", self.colors, texts=("Add",)
        )
        self.assertEqual(list(stats), ["Add"])
        self.assertEqual(len(stats["Add"]), 37)
        none = stats["Add"][contrast.HALO_NONE]["ink"]["min"]
        halo = stats["Add"][self.CHOICE]["ink"]["min"]
        self.assertLess(none, 1.5)
        self.assertGreater(halo, 3.0)

    def test_a_bright_pixel_counts_for_an_icon_wherever_it_is_on_the_frame(self):
        frame = uniform(1920, 1080, self.page)
        frame[523, 877] = 1.0
        stats = contrast.measure_halo_frame(frame, "tv", self.colors, texts=("Gear",))
        self.assertAlmostEqual(
            stats["Gear"][contrast.HALO_NONE]["ink"]["min"],
            contrast.hex_contrast(self.colors["ink"], "#FFFFFF"),
            places=3,
        )

    def test_the_rows_of_a_source_are_the_icons_with_their_own_limit(self):
        rows = contrast.measure_halo_source(
            [uniform(1920, 1080, self.page)], "Starfield", "tv", self.colors, texts=("Add", "Gear")
        )
        self.assertEqual({row["text"] for row in rows}, {"Add", "Gear"})
        self.assertEqual(len(rows), 2 * 37 * 2)
        json.dumps(rows)
        for row in rows:
            self.assertEqual(contrast.halo_limit(row["text"], row["device"]), 3.0)

    def test_the_meta_of_a_measurement_says_which_icons_and_how_they_are_drawn(self):
        result = contrast.measure_halo_sources(
            {("Starfield", "tv"): [uniform(1920, 1080, self.page)]}, self.colors, texts=("Add",)
        )
        self.assertIn("icons", result["meta"])
        self.assertEqual(result["meta"]["icons"]["names"], list(contrast.HALO_ICONS))
        self.assertEqual(result["meta"]["icons"]["size_dp"], 24)
        self.assertEqual(result["meta"]["icons"]["stroke_units"], 2.0)
        json.dumps(result)


FONTS = Path(__file__).resolve().parents[1] / "fonts" / "ibm-plex" / "res" / "font"
PLEX_REGULAR = FONTS / "ibm_plex_sans_regular.ttf"
PLEX_SEMIBOLD = FONTS / "ibm_plex_sans_semibold.ttf"
PLEX_MONO_REGULAR = FONTS / "ibm_plex_mono_regular.ttf"
PLEX_MONO_SEMIBOLD = FONTS / "ibm_plex_mono_semibold.ttf"


def sfnt(tables):
    """The bytes of a font file with these tables (a tag to its bytes) and nothing else.

    FreeType cannot load it. It is what the readers of the `head`, `OS/2` and `fvar` tables need.
    """
    count = len(tables)
    out = bytearray(struct.pack(">IHHHH", 0x00010000, count, 0, 0, 0))
    offset = 12 + 16 * count
    body = bytearray()
    for tag, data in tables.items():
        out += struct.pack(">4sIII", tag.encode("ascii"), 0, offset + len(body), len(data))
        body += data + bytes(-len(data) % 4)
    return bytes(out + body)


def head_table(units_per_em):
    table = bytearray(54)
    struct.pack_into(">H", table, 18, units_per_em)
    return bytes(table)


def os2_table(version, x_height):
    table = bytearray(96)
    struct.pack_into(">H", table, 0, version)
    struct.pack_into(">h", table, 86, x_height)
    return bytes(table)


def fvar_table(axes):
    """An `fvar` table of axes, each a (tag, minimum, default, maximum)."""
    out = bytearray(struct.pack(">HHHHHHHH", 1, 0, 16, 2, len(axes), 20, 0, 0))
    for tag, low, default, high in axes:
        values = (round(value * 65536) for value in (low, default, high))
        out += struct.pack(">4siiiHH", tag.encode("ascii"), *values, 0, 256)
    return bytes(out)


def patched_plex(folder, name, os2_version=None, x_height=None):
    """A copy of Plex Sans Regular with its `OS/2` version or its `sxHeight` changed."""
    data = bytearray(PLEX_REGULAR.read_bytes())
    for index in range(struct.unpack_from(">H", data, 4)[0]):
        tag, _, offset, _ = struct.unpack_from(">4sIII", data, 12 + 16 * index)
        if tag == b"OS/2":
            if os2_version is not None:
                struct.pack_into(">H", data, offset, os2_version)
            if x_height is not None:
                struct.pack_into(">h", data, offset + 86, x_height)
    path = Path(folder) / name
    path.write_bytes(bytes(data))
    return path


class FontReadingTest(unittest.TestCase):
    """What the tool reads of a font file: its x-height, and the axes of a variable font."""

    def test_the_x_height_is_the_height_of_the_x_as_drawn(self):
        face = contrast.font_face(PLEX_REGULAR, 400)
        self.assertAlmostEqual(contrast.x_height_ratio(face), 0.516, places=9)
        semibold = contrast.font_face(PLEX_SEMIBOLD, 600)
        self.assertAlmostEqual(contrast.x_height_ratio(semibold), 0.522, places=9)

    def test_the_os2_table_is_read_as_a_cross_check(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "made.ttf"
            path.write_bytes(sfnt({"head": head_table(2048), "OS/2": os2_table(4, 1024)}))
            self.assertAlmostEqual(contrast.os2_x_height(contrast.font_face(path, 400)), 0.5)
        self.assertAlmostEqual(contrast.os2_x_height(contrast.font_face(PLEX_REGULAR, 400)), 0.516)

    def test_a_table_with_no_x_height_has_none_to_cross_check(self):
        # Version 1 of the OS/2 table has no sxHeight, and zero is none.
        with tempfile.TemporaryDirectory() as folder:
            old = patched_plex(folder, "old.ttf", os2_version=1, x_height=0)
            zero = patched_plex(folder, "zero.ttf", x_height=0)
            for path in (old, zero):
                with self.subTest(path=path.name):
                    face = contrast.font_face(path, 400)
                    self.assertIsNone(contrast.os2_x_height(face))
                    self.assertAlmostEqual(contrast.x_height_ratio(face), 0.516, places=9)

    def test_the_drawn_x_is_the_height_whatever_the_table_says(self):
        # The sxHeight of a font may be off by a few percent (Andika and B612 are) or be nonsense
        # (OpenDyslexic Mono has the em, 1000 of 1000). The app draws the x, so the x is measured.
        with tempfile.TemporaryDirectory() as folder:
            for units in (600, 1000, 100):
                path = patched_plex(folder, f"font{units}.ttf", x_height=units)
                with self.subTest(units=units):
                    face = contrast.font_face(path, 400)
                    self.assertAlmostEqual(contrast.os2_x_height(face), units / 1000, places=9)
                    self.assertAlmostEqual(contrast.x_height_ratio(face), 0.516, places=9)

    def test_a_file_that_is_not_a_font_is_reported_and_not_a_struct_error(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "notes.txt"
            path.write_text("This is a text file, long enough to have a header but no font.\n" * 4)
            for read in (contrast.font_axes, lambda p: contrast.font_face(p, 400)):
                with self.subTest(read=read), self.assertRaisesRegex(ValueError, "not a font"):
                    read(path)
            with self.assertRaisesRegex(ValueError, "not a font"):
                contrast.font_set(path, PLEX_SEMIBOLD)

    def test_a_font_cut_short_is_reported_too(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "cut.ttf"
            path.write_bytes(PLEX_REGULAR.read_bytes()[:300])
            with self.assertRaisesRegex(ValueError, "cut short"):
                contrast.font_axes(path)
            with self.assertRaisesRegex(ValueError, "cut short"):
                contrast.os2_x_height(contrast.FontFace(str(path), None))

    def test_a_table_that_runs_past_the_end_of_the_file_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "cut.ttf"
            data = bytearray(sfnt({"head": head_table(1000), "OS/2": os2_table(4, 500)}))
            path.write_bytes(bytes(data[:-60]))
            with self.assertRaisesRegex(ValueError, "cut short"):
                contrast.os2_x_height(contrast.FontFace(str(path), None))

    def test_a_static_font_has_no_axes_and_is_used_at_the_weight_of_its_file(self):
        self.assertEqual(contrast.font_axes(PLEX_REGULAR), [])
        self.assertEqual(contrast.font_face(PLEX_SEMIBOLD, 600), (str(PLEX_SEMIBOLD), None))

    def test_the_axes_of_a_variable_font_are_read_from_its_fvar_table(self):
        axes = [("wght", 100.0, 400.0, 900.0), ("wdth", 75.0, 100.0, 125.0)]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table(axes)}))
            self.assertEqual(contrast.font_axes(path), axes)

    def test_a_variable_font_is_set_to_the_weight_asked_and_the_default_of_every_other_axis(self):
        axes = [("wdth", 75.0, 100.0, 125.0), ("wght", 100.0, 400.0, 900.0)]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table(axes)}))
            self.assertEqual(contrast.font_face(path, 600), (str(path), (100.0, 600.0)))
            self.assertEqual(contrast.font_face(path, 400).axes, (100.0, 400.0))

    def test_a_weight_the_font_does_not_have_is_reported_with_the_range_it_has(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table([("wght", 300.0, 400.0, 500.0)])}))
            with self.assertRaisesRegex(ValueError, "300.*500"):
                contrast.font_face(path, 600)

    def test_a_variable_font_without_a_weight_axis_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table([("wdth", 75.0, 100.0, 125.0)])}))
            with self.assertRaisesRegex(ValueError, "weight"):
                contrast.font_face(path, 400)

    def test_a_variable_font_that_this_freetype_cannot_set_is_reported(self):
        # A FreeType older than 2.9.1 refuses to set an axis, and Pillow says so with this error.
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table([("wght", 100.0, 400.0, 900.0)])}))
            face = contrast.font_face(path, 600)
            with unittest.mock.patch("PIL.ImageFont.truetype") as truetype:
                truetype.return_value.set_variation_by_axes.side_effect = NotImplementedError(
                    "FreeType 2.9.1 or greater is required"
                )
                with self.assertRaisesRegex(ValueError, "variable.*FreeType"):
                    contrast.load_face(face, 16)

    @unittest.skipUnless(
        Path("C:/Windows/Fonts/bahnschrift.ttf").is_file(), "a variable font is not installed"
    )
    def test_a_real_variable_font_is_drawn_heavier_at_a_higher_weight(self):
        path = Path("C:/Windows/Fonts/bahnschrift.ttf")
        light = contrast.font_face(path, 400)
        heavy = contrast.font_face(path, 600)
        self.assertIsNotNone(heavy.axes)

        def coverage(face):
            font = contrast.load_face(face, 40)
            image = Image.new("L", (400, 120), 0)
            ImageDraw.Draw(image).text((10, 10), "Living room", font=font, fill=255)
            return float(np.asarray(image, np.float32).sum() / 255)

        self.assertGreater(coverage(heavy), coverage(light) * 1.05)


class XHeightScaleTest(unittest.TestCase):
    """The scale that sets the x-height of a font to that of IBM Plex Sans at the same size."""

    def test_ibm_plex_sans_is_the_scale_of_one(self):
        self.assertEqual(contrast.x_height_scale(contrast.font_face(PLEX_REGULAR, 400)), 1.0)
        self.assertEqual(contrast.PLEX_SANS.scale, 1.0)

    def test_a_font_with_a_taller_x_has_a_scale_below_one_and_a_shorter_one_above(self):
        # The Semibold of Plex Sans has an x of 0.522 of the em against 0.516 of the Regular.
        semibold = contrast.font_face(PLEX_SEMIBOLD, 600)
        regular = contrast.font_face(PLEX_REGULAR, 400)
        self.assertAlmostEqual(contrast.x_height_scale(semibold), 0.516 / 0.522, places=9)
        self.assertAlmostEqual(contrast.x_height_scale(regular, semibold), 0.522 / 0.516, places=9)

    def test_the_scale_is_that_of_the_drawn_x_and_the_table_does_not_move_it(self):
        with tempfile.TemporaryDirectory() as folder:
            wrong = patched_plex(folder, "wrong.ttf", x_height=600)
            self.assertEqual(contrast.x_height_scale(contrast.font_face(wrong, 400)), 1.0)

    def test_a_scaled_font_has_the_x_height_of_plex_at_the_size_of_the_text(self):
        fonts = contrast.font_set(PLEX_SEMIBOLD, PLEX_SEMIBOLD)
        drawn = contrast.x_height_ratio(fonts.regular) * fonts.scale
        self.assertAlmostEqual(
            drawn, contrast.x_height_ratio(contrast.PLEX_SANS.regular), places=9
        )

    def test_the_scale_of_a_set_is_that_of_its_regular_face(self):
        fonts = contrast.font_set(PLEX_SEMIBOLD, PLEX_REGULAR)
        self.assertAlmostEqual(fonts.scale, 0.516 / 0.522, places=9)


class FontSetTest(unittest.TestCase):
    """A font set: the regular and the semibold face of a font, its name and its scale."""

    def test_the_default_is_ibm_plex_sans(self):
        plex = contrast.PLEX_SANS
        self.assertEqual(plex.name, "IBM Plex Sans")
        self.assertEqual(Path(plex.regular.path), PLEX_REGULAR)
        self.assertEqual(Path(plex.semibold.path), PLEX_SEMIBOLD)
        self.assertIsNone(plex.regular.axes)
        self.assertIsNone(plex.semibold.axes)

    def test_a_set_is_named_after_its_regular_file_unless_it_is_given_a_name(self):
        self.assertEqual(
            contrast.font_set(PLEX_MONO_REGULAR, PLEX_MONO_SEMIBOLD).name, "ibm_plex_mono_regular"
        )
        named = contrast.font_set(PLEX_REGULAR, PLEX_SEMIBOLD, "Plex")
        self.assertEqual(named.name, "Plex")

    def test_a_set_that_is_not_there_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(OSError):
                contrast.font_set(Path(folder) / "none.ttf", PLEX_SEMIBOLD)

    def test_a_manifest_names_the_set_and_its_files_next_to_it(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "regular.ttf").write_bytes(PLEX_MONO_REGULAR.read_bytes())
            (Path(folder) / "semibold.ttf").write_bytes(PLEX_MONO_SEMIBOLD.read_bytes())
            manifest = Path(folder) / "font.json"
            manifest.write_text(
                json.dumps({"name": "Mono", "regular": "regular.ttf", "semibold": "semibold.ttf"}),
                encoding="utf-8",
            )
            fonts = contrast.read_font_manifest(manifest)
            self.assertEqual(fonts.name, "Mono")
            self.assertEqual(Path(fonts.regular.path), Path(folder) / "regular.ttf")
            self.assertEqual(Path(fonts.semibold.path), Path(folder) / "semibold.ttf")
            self.assertEqual(fonts.scale, contrast.x_height_scale(fonts.regular))

    def test_a_manifest_may_name_files_by_absolute_path(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder) / "font.json"
            manifest.write_text(
                json.dumps({"regular": str(PLEX_REGULAR), "semibold": str(PLEX_SEMIBOLD)}),
                encoding="utf-8",
            )
            fonts = contrast.read_font_manifest(manifest)
        self.assertEqual(Path(fonts.regular.path), PLEX_REGULAR)
        self.assertEqual(fonts.name, "ibm_plex_sans_regular")

    def test_a_manifest_without_both_faces_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder) / "font.json"
            manifest.write_text(json.dumps({"regular": str(PLEX_REGULAR)}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "semibold"):
                contrast.read_font_manifest(manifest)


class HaloFontsTest(unittest.TestCase):
    """The halo of text set in a font set: the faces, and the scale of the x-height."""

    def test_the_default_reproduces_the_masks_and_the_rings_of_ibm_plex(self):
        # Characterization: these are the figures that the tool gave before it took a font set, and
        # `docs/ux/accessibility.md` has the tables that come from them. The label and the body of a
        # TV are at the sizes of Sprint 11 (13 and 16 sp), which `design/tokens.json` has now; the
        # tool as it was before the font set gives these figures at those sizes too.
        pinned = {
            ("title", "tv"): ((80, 522), 6130.316, (51, 504), 4409),
            ("title", "phone"): ((87, 586), 7749.765, (56, 567), 5015),
            ("label", "tv"): ((51, 369), 2258.541, (27, 353), 2666),
            ("label", "phone"): ((60, 464), 3622.095, (33, 447), 3399),
            ("body", "tv"): ((58, 343), 1815.597, (35, 326), 2876),
            ("body", "phone"): ((72, 445), 3127.875, (45, 427), 3901),
            ("small", "tv"): ((54, 312), 1494.114, (32, 295), 2542),
            ("small", "phone"): ((66, 399), 2502.756, (41, 381), 3424),
        }
        for (text, device), (shape, coverage, size, ring) in pinned.items():
            with self.subTest(text=text, device=device):
                mask = contrast.glyph_mask(text, device, pad=8)
                geometry = contrast.halo_geometry(text, device)
                self.assertEqual(mask.shape, shape)
                self.assertAlmostEqual(float(mask.sum()), coverage, delta=coverage * 1e-3)
                self.assertEqual(geometry.size, size)
                self.assertEqual(int(geometry.ring.sum()), ring)

    def test_asking_for_plex_is_asking_for_nothing(self):
        for text in contrast.HALO_TEXTS:
            with self.subTest(text=text):
                np.testing.assert_array_equal(
                    contrast.glyph_mask(text, "tv", pad=8),
                    contrast.glyph_mask(text, "tv", pad=8, fonts=contrast.PLEX_SANS),
                )

    def test_a_set_sets_the_text_in_its_own_faces(self):
        mono = contrast.font_set(PLEX_MONO_REGULAR, PLEX_MONO_SEMIBOLD)
        sans = contrast.glyph_mask("body", "tv", pad=8)
        other = contrast.glyph_mask("body", "tv", pad=8, fonts=mono)
        self.assertNotEqual(sans.shape, other.shape)
        sans_size = contrast.halo_geometry("body", "tv").size
        self.assertNotEqual(sans_size, contrast.halo_geometry("body", "tv", fonts=mono).size)

    def test_the_semibold_face_sets_the_title_and_the_label_and_the_regular_the_rest(self):
        regular_only = contrast.font_set(PLEX_REGULAR, PLEX_REGULAR)
        for text in ("body", "small"):
            with self.subTest(text=text):
                np.testing.assert_array_equal(
                    contrast.glyph_mask(text, "tv", pad=8, fonts=regular_only),
                    contrast.glyph_mask(text, "tv", pad=8),
                )
        for text in ("title", "label"):
            with self.subTest(text=text):
                lighter = contrast.glyph_mask(text, "tv", pad=8, fonts=regular_only)
                heavier = contrast.glyph_mask(text, "tv", pad=8)
                self.assertLess(float(lighter.sum()), float(heavier.sum()) * 0.97)

    def test_the_scale_sets_the_size_of_the_text_and_the_spacing_of_the_label(self):
        scaled = contrast.PLEX_SANS._replace(scale=1.25)
        for text in contrast.HALO_TEXTS:
            with self.subTest(text=text):
                plain = contrast.halo_geometry(text, "tv").size
                bigger = contrast.halo_geometry(text, "tv", fonts=scaled).size
                self.assertAlmostEqual(bigger[0] / plain[0], 1.25, delta=0.1)
                self.assertAlmostEqual(bigger[1] / plain[1], 1.25, delta=0.05)

    def test_a_frame_is_measured_with_the_font_set_it_is_given(self):
        colors = tokens()
        white = uniform(1920, 1080, (1.0, 1.0, 1.0))
        scaled = contrast.PLEX_SANS._replace(scale=1.25)
        plain = contrast.measure_halo_frame(white, "tv", colors, texts=("body",))
        wider = contrast.measure_halo_frame(white, "tv", colors, texts=("body",), fonts=scaled)
        # The ring is another set of pixels, so a halo that is thin at it gives another ratio.
        config = (1, 2, 0.6)
        self.assertNotEqual(plain["body"][config]["ink"]["p5"], wider["body"][config]["ink"]["p5"])
        # No halo is the ratio of the frame itself, whatever the text is set in.
        none = contrast.HALO_NONE
        self.assertAlmostEqual(
            plain["body"][none]["ink"]["min"], wider["body"][none]["ink"]["min"], places=6
        )


class HaloFontsCommandTest(unittest.TestCase):
    """`halo --font ... --font-semibold ...` and `halo --fonts <manifest>`."""

    def frames(self, folder):
        frames = Path(folder) / "frames"
        (frames / "Starfield-tv").mkdir(parents=True)
        ramp = np.linspace(0, 255, 1920).astype(np.uint8)
        pixels = np.zeros((1080, 1920, 3), np.uint8)
        pixels[..., 0] = ramp
        pixels[..., 1] = ramp // 2
        pixels[..., 2] = 255 - ramp
        Image.fromarray(pixels).save(frames / "Starfield-tv" / "00.png")
        return frames

    def run_command(self, folder, *extra, texts=("body", "title")):
        target = Path(folder) / "halo.json"
        args = ["halo", str(self.frames(folder)), "--json", str(target), "--texts", *texts, *extra]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(contrast.main(args), 0)
        return json.loads(target.read_text(encoding="utf-8"))

    def refuses(self, message, *extra):
        """Runs `halo` with these options, which it refuses with a message that has `message`."""
        with tempfile.TemporaryDirectory() as folder:
            args = ["halo", folder, "--json", str(Path(folder) / "x.json"), *extra]
            err = io.StringIO()
            with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as stop:
                contrast.main(args)
        self.assertEqual(stop.exception.code, 2)
        self.assertIn(message, err.getvalue())

    def test_with_no_option_the_rows_are_those_of_plex_given_by_name(self):
        with tempfile.TemporaryDirectory() as folder:
            default = self.run_command(folder)
        with tempfile.TemporaryDirectory() as folder:
            named = self.run_command(
                folder, "--font", str(PLEX_REGULAR), "--font-semibold", str(PLEX_SEMIBOLD)
            )
        self.assertEqual(default["halo"], named["halo"])
        self.assertEqual(default["meta"]["fonts"]["name"], "IBM Plex Sans")
        self.assertEqual(default["meta"]["fonts"]["scale"], 1.0)

    def test_the_font_files_set_the_rows_and_the_meta_says_which_they_are(self):
        with tempfile.TemporaryDirectory() as folder:
            default = self.run_command(folder)
        with tempfile.TemporaryDirectory() as folder:
            other = self.run_command(
                folder, "--font", str(PLEX_MONO_REGULAR), "--font-semibold", str(PLEX_MONO_SEMIBOLD)
            )
        self.assertNotEqual(default["halo"], other["halo"])
        fonts = other["meta"]["fonts"]
        self.assertEqual(fonts["name"], "ibm_plex_mono_regular")
        self.assertEqual(fonts["regular"], {"file": "ibm_plex_mono_regular.ttf", "wght": None})
        self.assertEqual(fonts["semibold"], {"file": "ibm_plex_mono_semibold.ttf", "wght": None})
        self.assertEqual(fonts["scale"], 1.0)
        self.assertEqual(other["meta"]["texts"]["title"]["font"], "ibm_plex_mono_semibold")
        self.assertEqual(other["meta"]["texts"]["body"]["font"], "ibm_plex_mono_regular")

    def test_the_scale_of_the_font_is_in_the_meta_and_changes_the_rows(self):
        with tempfile.TemporaryDirectory() as folder:
            result = self.run_command(
                folder, "--font", str(PLEX_SEMIBOLD), "--font-semibold", str(PLEX_SEMIBOLD)
            )
        with tempfile.TemporaryDirectory() as folder:
            default = self.run_command(folder)
        self.assertAlmostEqual(result["meta"]["fonts"]["scale"], 0.516 / 0.522, places=9)
        self.assertNotEqual(result["halo"], default["halo"])

    def test_a_manifest_gives_the_same_set_as_the_two_options(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder) / "font.json"
            manifest.write_text(
                json.dumps(
                    {
                        "name": "Plex Mono",
                        "regular": str(PLEX_MONO_REGULAR),
                        "semibold": str(PLEX_MONO_SEMIBOLD),
                    }
                ),
                encoding="utf-8",
            )
            by_manifest = self.run_command(folder, "--fonts", str(manifest))
        with tempfile.TemporaryDirectory() as folder:
            by_options = self.run_command(
                folder, "--font", str(PLEX_MONO_REGULAR), "--font-semibold", str(PLEX_MONO_SEMIBOLD)
            )
        self.assertEqual(by_manifest["halo"], by_options["halo"])
        self.assertEqual(by_manifest["meta"]["fonts"]["name"], "Plex Mono")

    def test_the_tables_say_the_font_when_it_is_not_plex(self):
        def printed(*extra):
            with tempfile.TemporaryDirectory() as folder:
                target = Path(folder) / "halo.json"
                args = ["halo", str(self.frames(folder)), "--json", str(target)]
                out = io.StringIO()
                with contextlib.redirect_stdout(out):
                    contrast.main([*args, "--texts", "body", *extra])
            return out.getvalue()

        other = printed(
            *("--font", str(PLEX_MONO_REGULAR)), *("--font-semibold", str(PLEX_MONO_SEMIBOLD))
        )
        self.assertTrue(other.startswith("Font: ibm_plex_mono_regular"))
        self.assertIn("x-height scale 1.000", other.splitlines()[0])
        self.assertNotIn("Font:", printed())

    def test_one_face_alone_is_not_a_font_set(self):
        self.refuses("--font and --font-semibold go together", "--font", str(PLEX_REGULAR))
        self.refuses(
            "--font and --font-semibold go together", "--font-semibold", str(PLEX_SEMIBOLD)
        )

    def test_a_manifest_and_the_options_are_not_asked_together(self):
        self.refuses(
            "--fonts and --font are not asked together",
            *("--fonts", "font.json", "--font", str(PLEX_REGULAR)),
            *("--font-semibold", str(PLEX_SEMIBOLD)),
        )

    def test_a_font_file_that_is_not_there_is_reported(self):
        self.refuses(
            "No such file or directory: 'none.ttf'",
            *("--font", "none.ttf", "--font-semibold", str(PLEX_SEMIBOLD)),
        )

    def test_a_file_that_is_not_a_font_is_reported_with_its_name(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "notes.txt"
            path.write_text("This is a text file, long enough to have a header but no font.\n" * 4)
            self.refuses(
                "not a font",
                "--font", str(path), "--font-semibold", str(PLEX_SEMIBOLD),
            )

    def test_a_manifest_that_is_not_there_is_reported(self):
        self.refuses("No such file or directory: 'none.json'", "--fonts", "none.json")

    def test_the_icons_have_no_font(self):
        self.refuses(
            "--icons and a font are not asked together",
            *("--icons", "--halo", "3", "2", "80"),
            *("--font", str(PLEX_REGULAR), "--font-semibold", str(PLEX_SEMIBOLD)),
        )


class HaloFontTablesTest(unittest.TestCase):
    """The choice of the halo is that of the font of the app, and the notes have it once."""

    def result(self, fonts):
        rows = halo_rows({(2, 2, 0.8)}, devices=("tv", "phone"))
        return {"meta": {"fonts": fonts}, "halo": rows}

    def test_the_font_of_the_app_has_its_choice_as_the_notes_have_it(self):
        text = contrast.halo_tables(self.result(contrast.fonts_meta(contrast.PLEX_SANS)))
        self.assertIn("The choice: stroke 2 dp, blur 2 dp, opacity 80 %.", text)

    def test_a_result_with_no_fonts_is_the_font_of_the_app(self):
        text = contrast.halo_tables(self.result(None))
        self.assertIn("The choice: stroke 2 dp, blur 2 dp, opacity 80 %.", text)

    def test_another_font_has_the_smallest_candidate_and_not_the_choice(self):
        # NotesChoice reads the line "The choice: ..." of the notes, which must be there once.
        other = dict(contrast.fonts_meta(contrast.PLEX_SANS), name="Lexend")
        text = contrast.halo_tables(self.result(other))
        self.assertNotIn("The choice:", text)
        line = "The smallest candidate for Lexend: stroke 2 dp, blur 2 dp, opacity 80 %."
        self.assertIn(line, text)

    def test_the_same_files_under_another_scale_are_another_font(self):
        scaled = dict(contrast.fonts_meta(contrast.PLEX_SANS), scale=1.1)
        self.assertNotIn("The choice:", contrast.halo_tables(self.result(scaled)))

    def test_the_meta_of_a_font_set_names_its_files_weights_and_scale(self):
        meta = contrast.fonts_meta(contrast.PLEX_SANS)
        self.assertEqual(
            meta,
            {
                "name": "IBM Plex Sans",
                "regular": {"file": "ibm_plex_sans_regular.ttf", "wght": None},
                "semibold": {"file": "ibm_plex_sans_semibold.ttf", "wght": None},
                "x_height": {"drawn": 0.516, "os2": 0.516},
                "scale": 1.0,
            },
        )

    def test_the_meta_has_the_drawn_x_and_the_table_when_they_differ(self):
        with tempfile.TemporaryDirectory() as folder:
            path = patched_plex(folder, "wrong.ttf", x_height=600)
            meta = contrast.fonts_meta(contrast.font_set(path, PLEX_SEMIBOLD))
        self.assertEqual(meta["x_height"], {"drawn": 0.516, "os2": 0.6})
        self.assertEqual(meta["scale"], 1.0)

    def test_a_table_with_no_x_height_is_none_in_the_meta(self):
        with tempfile.TemporaryDirectory() as folder:
            path = patched_plex(folder, "old.ttf", os2_version=1, x_height=0)
            meta = contrast.fonts_meta(contrast.font_set(path, PLEX_SEMIBOLD))
        self.assertEqual(meta["x_height"], {"drawn": 0.516, "os2": None})

    def test_the_weight_of_a_variable_face_is_that_of_its_wght_axis(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "variable.ttf"
            path.write_bytes(sfnt({"fvar": fvar_table([("wght", 100.0, 400.0, 900.0)])}))
            self.assertEqual(contrast._face_weight(contrast.font_face(path, 600)), 600.0)
            self.assertIsNone(contrast._face_weight(contrast.font_face(PLEX_REGULAR, 400)))

    @unittest.skipUnless(
        Path("C:/Windows/Fonts/bahnschrift.ttf").is_file(), "a variable font is not installed"
    )
    def test_the_weights_of_a_real_variable_font_are_in_the_meta(self):
        path = Path("C:/Windows/Fonts/bahnschrift.ttf")
        meta = contrast.fonts_meta(contrast.font_set(path, path))
        self.assertEqual(meta["regular"], {"file": "bahnschrift.ttf", "wght": 400.0})
        self.assertEqual(meta["semibold"], {"file": "bahnschrift.ttf", "wght": 600.0})


class HaloIconTablesTest(unittest.TestCase):
    CHOICE = (3, 2, 0.8)

    def rows(self, passing, ratio=6.0, **kwargs):
        return halo_rows(
            passing, ratio=ratio, texts=contrast.HALO_ICONS, devices=("tv", "phone"), **kwargs
        )

    def test_the_tables_say_every_icon_keeps_3_to_1_when_it_does(self):
        rows = self.rows({self.CHOICE})
        text = contrast.halo_icon_tables({"meta": {}, "halo": rows}, self.CHOICE)
        self.assertIn("Table I", text)
        self.assertIn("Table J", text)
        self.assertIn("every icon keeps 3:1", text)
        self.assertIn("stroke 3 dp, blur 2 dp, opacity 80 %", text)
        for name in contrast.HALO_ICONS:
            self.assertIn(name, text)

    def test_the_tables_name_the_icon_that_falls_short(self):
        rows = self.rows({self.CHOICE})
        low = next(
            r
            for r in rows
            if (r["stroke_dp"], r["blur_dp"], r["opacity"], r["text"], r["token"], r["device"])
            == (3, 2, 0.8, "Gear", "ink-2", "phone")
        )
        low["min"] = 2.5
        low["style"] = "Starfield"
        text = contrast.halo_icon_tables({"meta": {}, "halo": rows}, self.CHOICE)
        self.assertIn("falls short of 3:1", text)
        self.assertIn("2.50", text)
        self.assertIn("Gear", text)
        self.assertIn("Starfield", text)

    def test_an_icon_must_clear_3_to_1_by_the_margin_of_the_model(self):
        # 3.04 reaches 3:1 but not with 2 percent to spare, which is 3.06.
        tight = {"meta": {}, "halo": self.rows({self.CHOICE}, ratio=3.04)}
        self.assertIn("falls short", contrast.halo_icon_tables(tight, self.CHOICE))
        clear = {"meta": {}, "halo": self.rows({self.CHOICE}, ratio=3.07)}
        self.assertIn("every icon keeps 3:1", contrast.halo_icon_tables(clear, self.CHOICE))

    def test_under_high_contrast_the_icons_have_no_margin_to_clear(self):
        meta = {"high_contrast": {"dim": 0.85}}
        result = {"meta": meta, "halo": self.rows({self.CHOICE}, ratio=3.01)}
        text = contrast.halo_icon_tables(result, self.CHOICE)
        self.assertIn("every icon keeps 3:1", text)
        self.assertIn("0.85", text)

    def test_the_smallest_halo_that_an_icon_needs_alone_is_named(self):
        rows = self.rows({(2, 2, 0.8), self.CHOICE})
        text = contrast.halo_icon_tables({"meta": {}, "halo": rows}, self.CHOICE)
        self.assertIn("smallest candidate", text)
        self.assertIn("stroke 2 dp, blur 2 dp, opacity 80 %", text)

    def test_the_tables_of_the_text_leave_the_icon_rows_alone(self):
        rows = halo_rows({(2, 2, 0.8)}, devices=("tv", "phone"))
        rows += self.rows(set(), failing=1.0)
        text = contrast.halo_tables({"meta": {}, "halo": rows})
        self.assertIn("The choice: stroke 2 dp, blur 2 dp, opacity 80 %.", text)
        self.assertNotIn("Gear", text)

    def run_command(self, *extra):
        import contextlib
        import io

        with tempfile.TemporaryDirectory() as folder:
            frames = Path(folder) / "frames"
            (frames / "Starfield-tv").mkdir(parents=True)
            Image.new("RGB", (1920, 1080), (255, 255, 255)).save(frames / "Starfield-tv" / "00.png")
            target = Path(folder) / "halo-icons.json"
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = contrast.main(["halo", str(frames), "--json", str(target), *extra])
            written = json.loads(target.read_text(encoding="utf-8"))
        return code, written, out.getvalue()

    def test_the_command_measures_the_icons_and_prints_their_tables(self):
        code, written, out = self.run_command("--icons", "--halo", "3", "2", "80")
        self.assertEqual(code, 0)
        self.assertEqual({r["text"] for r in written["halo"]}, set(contrast.HALO_ICONS))
        self.assertIn("Table I", out)

    def test_the_command_measures_the_icons_under_high_contrast(self):
        code, written, out = self.run_command(
            "--icons", "--high-contrast", "--halo", "3", "2", "80"
        )
        self.assertEqual(code, 0)
        self.assertEqual(written["meta"]["high_contrast"]["dim"], 0.85)
        self.assertEqual(written["meta"]["colors"]["ink"].upper(), "#FFFFFF")
        self.assertIn("Table I", out)

    def test_icons_and_text_styles_are_not_asked_for_together(self):
        import contextlib
        import io

        with tempfile.TemporaryDirectory() as folder:
            args = ["halo", folder, "--json", str(Path(folder) / "x.json"), "--icons"]
            args += ["--texts", "body", "--halo", "3", "2", "80"]
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                contrast.main(args)

    def test_the_halo_to_check_goes_with_the_icons(self):
        import contextlib
        import io

        with tempfile.TemporaryDirectory() as folder:
            args = ["halo", folder, "--json", str(Path(folder) / "x.json"), "--icons"]
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                contrast.main(args)


# The accent candidates (Sprint 11, issue #50)

# Today's accent tokens, and a candidate of `accents.json` made of them.
VIOLET_COLOR = {
    "accent": "#A78BFA",
    "accent-soft": "#C4B5FD",
    "accent-strong": "#7C3AED",
    "on-accent": "#160B2E",
    "accent-tint": "#A78BFA21",
    "accent-line": "#A78BFA66",
    "accent-inverse": "#7C3AED",
}
VIOLET_HIGH_CONTRAST = {
    "accent": "#C8B7FC",
    "accent-soft": "#D6CCFE",
    "accent-line": "#C8B7FC",
    "accent-inverse": "#5744A9",
}
SURFACE_HEX = {"page": "#0E1013", "surface": "#14171B", "surface-raised": "#1A1E24"}
STYLES = ("AuroraCurtains", "SoftGlow", "NorthernLights", "LightMist", "FlowingLight", "Starfield")


def candidate(color=None, high_contrast=None, **extra):
    """A valid candidate of `accents.json`: today's violet with some values changed."""
    colors = {**VIOLET_COLOR, **(color or {})}
    high = {**VIOLET_HIGH_CONTRAST, **(high_contrast or {})}
    notes = {role: f"how {role} was derived" for role in colors}
    notes.update({f"high-contrast.{role}": f"how {role} is derived" for role in high})
    return {
        "label": "Test",
        "source": "a test",
        "color": colors,
        "high-contrast": high,
        "notes": notes,
        "known_fails": {},
        **extra,
    }


def write_accents(folder, **candidates):
    path = Path(folder) / "accents.json"
    path.write_text(json.dumps({"candidates": candidates}), encoding="utf-8")
    return path


class ColourMathTest(unittest.TestCase):
    """RGBA colours, CIEDE2000 and the dichromacies: the maths of the accent checks."""

    def test_an_rgba_colour_is_rgb_and_an_alpha_in_the_order_of_the_tokens(self):
        rgb, alpha = contrast.parse_rgba("#A78BFA21")
        np.testing.assert_allclose(rgb, [167 / 255, 139 / 255, 250 / 255])
        self.assertAlmostEqual(alpha, 0x21 / 255)

    def test_a_colour_with_no_alpha_is_opaque(self):
        rgb, alpha = contrast.parse_rgba("#0E1013")
        self.assertEqual(alpha, 1.0)
        np.testing.assert_allclose(rgb, contrast.parse_hex("#0E1013"))

    def test_a_colour_that_is_not_hex_is_reported(self):
        for value in ("A78BFA", "#A78B", "#A78BFAF", "#GGGGGG"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                contrast.parse_rgba(value)

    def test_a_colour_laid_over_another_is_mixed_in_the_encoded_values_to_8_bits(self):
        # Compose holds an sRGB colour in 8 bits a channel and rounds half up: the tint on the
        # raised surface is (44.25, 44.11, 63.69) of 255, which is (44, 44, 64).
        out = contrast.over("#A78BFA21", contrast.parse_hex("#1A1E24"))
        np.testing.assert_allclose(out * 255, [44, 44, 64], atol=1e-9)
        alpha = 0x21 / 255
        below, above = contrast.parse_hex("#14171B"), contrast.parse_hex("#A78BFA")
        mixed = alpha * above + (1 - alpha) * below
        np.testing.assert_allclose(
            contrast.over("#A78BFA21", below), np.floor(mixed * 255 + 0.5) / 255, atol=1e-9
        )

    def test_an_opaque_colour_over_another_is_itself(self):
        np.testing.assert_allclose(
            contrast.over("#336699", contrast.parse_hex("#14171B")), contrast.parse_hex("#336699")
        )

    def test_the_ratio_of_two_colours_is_the_one_of_their_hex_values(self):
        a, b = contrast.parse_hex("#A78BFA"), contrast.parse_hex("#14171B")
        expected = contrast.hex_contrast("#A78BFA", "#14171B")
        self.assertAlmostEqual(contrast.rgb_contrast(a, b), expected)
        self.assertAlmostEqual(contrast.rgb_contrast(b, a), expected)

    def test_ciede2000_gives_the_test_data_of_sharma_wu_and_dalal(self):
        pairs = [
            ((50.0, 2.6772, -79.7751), (50.0, 0.0, -82.7485), 2.0425),
            ((50.0, 3.1571, -77.2803), (50.0, 0.0, -82.7485), 2.8615),
            ((50.0, 2.8361, -74.0200), (50.0, 0.0, -82.7485), 3.4412),
            ((50.0, 0.0, 0.0), (50.0, -1.0, 2.0), 2.3669),
            ((50.0, 2.49, -0.001), (50.0, -2.49, 0.0009), 7.1792),
            ((50.0, -0.001, 2.49), (50.0, 0.0009, -2.49), 4.8045),
            ((50.0, 2.5, 0.0), (50.0, 0.0, -2.5), 4.3065),
            ((50.0, 2.5, 0.0), (73.0, 25.0, -18.0), 27.1492),
            ((50.0, 2.5, 0.0), (61.0, -5.0, 29.0), 22.8977),
            ((50.0, 2.5, 0.0), (56.0, -27.0, -3.0), 31.9030),
            ((50.0, 2.5, 0.0), (58.0, 24.0, 15.0), 19.4535),
        ]
        for first, second, expected in pairs:
            with self.subTest(first=first, second=second):
                self.assertAlmostEqual(contrast.delta_e2000(first, second), expected, places=4)
                self.assertAlmostEqual(contrast.delta_e2000(second, first), expected, places=4)

    def test_the_distance_of_two_colours_is_the_one_the_kotlin_check_pins(self):
        # ColourVisionPaletteTest pins these two pairs, from a second implementation.
        self.assertAlmostEqual(contrast.colour_distance("#009E73", "#808080"), 24.975, delta=0.01)
        self.assertAlmostEqual(contrast.colour_distance("#56B4E9", "#F0E442"), 57.529, delta=0.01)
        self.assertAlmostEqual(contrast.colour_distance("#000000", "#FFFFFF"), 100.0, delta=0.01)
        self.assertEqual(contrast.colour_distance("#FF0000", "#FF0000"), 0.0)

    def test_the_lab_of_white_and_black_are_the_ends_of_the_lightness(self):
        np.testing.assert_allclose(contrast.srgb_to_lab(np.ones(3)), [100, 0, 0], atol=0.01)
        np.testing.assert_allclose(contrast.srgb_to_lab(np.zeros(3)), [0, 0, 0], atol=1e-6)

    def test_each_row_of_a_dichromacy_adds_up_to_one(self):
        kinds = sorted(contrast.DICHROMACIES)
        self.assertEqual(kinds, ["deuteranopia", "protanopia", "tritanopia"])
        for kind, matrix in contrast.DICHROMACIES.items():
            for row in matrix:
                with self.subTest(kind=kind, row=row):
                    self.assertAlmostEqual(sum(row), 1.0, delta=2e-6)

    def test_white_and_a_grey_are_seen_as_they_are_by_every_dichromat(self):
        for kind in contrast.DICHROMACIES:
            for grey in (np.ones(3), np.full(3, 128 / 255)):
                with self.subTest(kind=kind):
                    np.testing.assert_allclose(contrast.seen_by(grey, kind), grey, atol=1e-9)

    def test_red_is_seen_by_a_protanope_as_the_first_column_of_its_matrix_encoded(self):
        seen = contrast.seen_by(np.array([1.0, 0.0, 0.0]), "protanopia")
        np.testing.assert_allclose(seen, [0.42661, 0.37265, 0.0], atol=0.002)

    def test_what_a_dichromat_sees_is_whole_8_bit_values(self):
        seen = contrast.seen_by(contrast.parse_hex("#A78BFA"), "deuteranopia")
        np.testing.assert_allclose(seen * 255, np.round(seen * 255), atol=1e-9)

    def test_a_distance_as_a_dichromat_sees_it_is_that_of_the_seen_colours(self):
        a, b = "#56B4E9", "#009E73"
        seen = [contrast.seen_by(contrast.parse_hex(c), "tritanopia") for c in (a, b)]
        expected = contrast.delta_e2000(*(contrast.srgb_to_lab(c) for c in seen))
        self.assertAlmostEqual(contrast.colour_distance(a, b, "tritanopia"), expected, places=9)
        self.assertNotAlmostEqual(
            contrast.colour_distance(a, b, "tritanopia"), contrast.colour_distance(a, b), places=2
        )


COLOUR_MEASURES_KT = (
    contrast.REPO
    / "apps/android/borealis/designsystem/src/test/kotlin/io/github/magalz/borealis"
    / "designsystem/theme/ColourMeasures.kt"
)


def read_dichromacies_kotlin(text):
    """The matrices of `enum class Dichromacy` of ColourMeasures.kt, by the name of each in lower
    case: three rows of three numbers."""
    enum = text[text.index("enum class Dichromacy") :]
    found = {}
    for entry in re.finditer(r"^    (\w+)\(\n(.*?)^    \),", enum, re.S | re.M):
        rows = re.findall(r"listOf\(\s*(-?[\d.]+(?:\s*,\s*-?[\d.]+)+)\s*\)", entry[2])
        found[entry[1].lower()] = tuple(tuple(float(n) for n in row.split(",")) for row in rows)
    return found


def read_constant_kotlin(text, name):
    """The number of `const val NAME = 0.95047` or of `NAME = 216.0 / 24389.0` in Kotlin."""
    found = re.search(name + r" = ([\d.]+)(?: / ([\d.]+))?", text)
    return float(found[1]) / float(found[2]) if found[2] else float(found[1])


def read_xyz_kotlin(text):
    """The rows of linear sRGB to XYZ in `Color.toLab`: the x, y and z lines, three numbers each."""
    rows = re.findall(r"val [xyz] = \(?(\d\.\d+) \* r \+ (\d\.\d+) \* g \+ (\d\.\d+) \* b", text)
    return tuple(tuple(float(n) for n in row) for row in rows)


class KotlinColourMeasuresTest(unittest.TestCase):
    """The tool and `ColourMeasures.kt` measure with the same numbers.

    The Kotlin file is the check of the colour-blind palettes (`ColourVisionPaletteTest`) and the
    tool is the check of the accents: a number that moves in one has to move in the other.
    """

    @classmethod
    def setUpClass(cls):
        cls.kotlin = COLOUR_MEASURES_KT.read_text(encoding="utf-8")

    def test_the_matrices_of_the_dichromacies_are_those_of_the_kotlin_check(self):
        found = read_dichromacies_kotlin(self.kotlin)
        self.assertEqual(sorted(found), ["deuteranopia", "protanopia", "tritanopia"])
        self.assertEqual(found, contrast.DICHROMACIES)

    def test_a_matrix_that_differs_is_found(self):
        moved = self.kotlin.replace("0.152286, 1.052583", "0.152287, 1.052583")
        self.assertNotEqual(moved, self.kotlin)
        self.assertNotEqual(read_dichromacies_kotlin(moved), contrast.DICHROMACIES)

    def test_the_white_point_and_the_lab_constants_are_those_of_the_kotlin_check(self):
        self.assertEqual(read_constant_kotlin(self.kotlin, "WHITE_X"), contrast.LAB_WHITE_X)
        self.assertEqual(read_constant_kotlin(self.kotlin, "WHITE_Z"), contrast.LAB_WHITE_Z)
        self.assertEqual(read_constant_kotlin(self.kotlin, "LAB_EPSILON"), contrast.LAB_EPSILON)
        self.assertEqual(read_constant_kotlin(self.kotlin, "LAB_KAPPA"), contrast.LAB_KAPPA)

    def test_the_transfer_function_thresholds_are_those_of_the_kotlin_check(self):
        decode = read_constant_kotlin(self.kotlin, "SRGB_THRESHOLD")
        encode = read_constant_kotlin(self.kotlin, "SRGB_LINEAR_THRESHOLD")
        self.assertEqual(decode, contrast.SRGB_THRESHOLD)
        self.assertEqual(encode, contrast.SRGB_ENCODE_THRESHOLD)

    def test_the_matrix_from_linear_srgb_to_xyz_is_that_of_the_kotlin_check(self):
        self.assertEqual(len(read_xyz_kotlin(self.kotlin)), 3)
        self.assertEqual(read_xyz_kotlin(self.kotlin), contrast.SRGB_TO_XYZ)


class AccentFileTest(unittest.TestCase):
    """The candidates of `accents.json` are checked as they are read."""

    def load(self, **candidates):
        with tempfile.TemporaryDirectory() as folder:
            return contrast.load_accents(write_accents(folder, **candidates))

    def test_a_valid_file_gives_its_candidates_in_order(self):
        loaded = self.load(one=candidate(), two=candidate())
        self.assertEqual(list(loaded), ["one", "two"])
        self.assertEqual(loaded["one"]["color"]["accent"], "#A78BFA")
        self.assertEqual(loaded["one"]["high-contrast"]["accent-line"], "#C8B7FC")

    def test_a_role_that_is_missing_is_reported_with_the_candidate(self):
        color = dict(VIOLET_COLOR)
        del color["on-accent"]
        with self.assertRaisesRegex(ValueError, "teal.*on-accent"):
            self.load(teal=dict(candidate(), color=color))

    def test_the_role_of_the_snackbar_action_is_required(self):
        color = dict(VIOLET_COLOR)
        del color["accent-inverse"]
        with self.assertRaisesRegex(ValueError, "teal.*accent-inverse"):
            self.load(teal=dict(candidate(), color=color))

    def test_the_high_contrast_role_of_the_snackbar_action_is_required(self):
        high = dict(VIOLET_HIGH_CONTRAST)
        del high["accent-inverse"]
        with self.assertRaisesRegex(ValueError, "teal.*high-contrast.*accent-inverse"):
            self.load(teal=dict(candidate(), **{"high-contrast": high}))

    def test_a_high_contrast_role_that_is_missing_is_reported(self):
        broken = dict(candidate(), **{"high-contrast": {"accent": "#C8B7FC"}})
        with self.assertRaisesRegex(ValueError, "teal.*accent-soft"):
            self.load(teal=broken)

    def test_a_colour_that_is_not_hex_is_reported_with_its_role(self):
        with self.assertRaisesRegex(ValueError, "teal.*accent-strong.*purple"):
            self.load(teal=candidate(color={"accent-strong": "purple"}))

    def test_only_the_tint_and_the_line_may_have_an_alpha(self):
        with self.assertRaisesRegex(ValueError, "teal.*accent.*opaque"):
            self.load(teal=candidate(color={"accent": "#A78BFA80"}))
        with self.assertRaisesRegex(ValueError, "teal.*on-accent.*opaque"):
            self.load(teal=candidate(color={"on-accent": "#160B2E80"}))
        faint = {"accent-line": "#A78BFA", "accent-tint": "#A78BFA10"}
        loaded = self.load(teal=candidate(color=faint))
        self.assertEqual(loaded["teal"]["color"]["accent-tint"], "#A78BFA10")

    def test_the_colours_of_high_contrast_are_opaque(self):
        with self.assertRaisesRegex(ValueError, "teal.*accent-line.*opaque"):
            self.load(teal=candidate(high_contrast={"accent-line": "#C8B7FC66"}))

    def test_a_value_with_no_note_on_how_it_was_derived_is_reported(self):
        broken = candidate()
        del broken["notes"]["accent-soft"]
        with self.assertRaisesRegex(ValueError, "teal.*accent-soft.*how"):
            self.load(teal=broken)
        broken = candidate()
        del broken["notes"]["high-contrast.accent"]
        with self.assertRaisesRegex(ValueError, "teal.*high-contrast.accent"):
            self.load(teal=broken)

    def test_a_candidate_with_no_label_or_known_fails_has_the_defaults(self):
        bare = candidate()
        del bare["label"], bare["known_fails"]
        loaded = self.load(teal=bare)
        self.assertEqual(loaded["teal"]["label"], "teal")
        self.assertEqual(loaded["teal"]["known_fails"], {})

    def test_a_known_failure_has_a_reason(self):
        known = candidate(known_fails={"distance:ink": "a reason"})
        self.assertEqual(self.load(teal=known)["teal"]["known_fails"], {"distance:ink": "a reason"})
        with self.assertRaisesRegex(ValueError, "teal.*distance:ink.*reason"):
            self.load(teal=candidate(known_fails={"distance:ink": " "}))

    def test_a_file_with_no_candidates_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, "no candidate"):
                contrast.load_accents(write_accents(folder))


class AccentContextTest(unittest.TestCase):
    """The colours of the tokens that the accents are checked against."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)

    def test_the_surfaces_and_the_ink_are_the_tokens(self):
        colors = self.context["colors"]
        self.assertEqual(colors["page"], "#0E1013")
        self.assertEqual(colors["chrome"], "#0B0D10")
        self.assertEqual(colors["surface-raised"], "#1A1E24")
        self.assertEqual(colors["ink"], "#E8EAED")
        self.assertEqual(colors["ink-2"], "#C3C7CC")
        self.assertEqual(colors["ink-3"], "#8B9096")
        self.assertEqual(colors["hover"], "#FFFFFF0A")

    def test_the_ink_of_high_contrast_is_the_inverse_surface_of_the_snackbar(self):
        # The snackbar is drawn on the ink: the ink of high contrast is white.
        self.assertEqual(self.context["high_contrast_ink"], "#FFFFFF")

    def test_the_status_colours_are_the_default_and_the_two_palettes(self):
        status = self.context["status"]
        self.assertEqual(list(status), ["default", "red-green", "blue-yellow"])
        self.assertEqual(status["default"]["success"], "#4CAF50")
        self.assertEqual(status["default"]["offline"], "#686E75")
        self.assertEqual(status["red-green"]["success"], "#56B4E9")
        self.assertEqual(status["blue-yellow"]["success"], "#009E73")
        for palette in status.values():
            self.assertEqual(sorted(palette), ["danger", "offline", "success", "warning"])


class AccentBackdropTest(unittest.TestCase):
    """The brightest pixel behind each glass, with the accent tint on it, over a set of frames."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)
        self.candidates = {"one": candidate(), "two": candidate(color={"accent-tint": "#33CC9940"})}

    def expected(self, colour, alpha, tint=None, tint_alpha=0.0):
        """The luminance of a flat frame under the glass of `alpha` and then a tint."""
        surface = contrast.parse_hex(self.context["colors"]["surface"])
        panel = alpha * surface + (1 - alpha) * np.asarray(colour)
        if tint is not None:
            panel = tint_alpha * contrast.parse_hex(tint) + (1 - tint_alpha) * panel
        return float(contrast.relative_luminance(panel))

    def rows(self, frames):
        sources = {("Starfield", "tv"): frames}
        return contrast.measure_backdrops(sources, self.candidates, self.context)

    def row(self, rows, blur, alpha, overlay=None, device="tv"):
        found = [
            r
            for r in rows
            if (r["blur_dp"], r["alpha"], r["overlay"], r["device"])
            == (blur, alpha, overlay, device)
        ]
        self.assertEqual(len(found), 1, (blur, alpha, overlay))
        return found[0]["luminance"]

    def test_the_glass_of_a_flat_frame_is_the_surface_over_it_at_the_tint(self):
        colour = (0.5, 0.4, 0.8)
        rows = self.rows([uniform(192, 108, colour)])
        for alpha in contrast.GLASS_ALPHAS:
            with self.subTest(alpha=alpha):
                self.assertAlmostEqual(
                    self.row(rows, 8, alpha), self.expected(colour, alpha), places=5
                )
        self.assertAlmostEqual(self.row(rows, 48, 0.85), self.expected(colour, 0.85), places=5)

    def test_the_panels_are_the_grid_of_8_dp_and_the_reduced_glass(self):
        rows = self.rows([uniform(192, 108, (0.2, 0.2, 0.2))])
        panels = {(r["blur_dp"], r["alpha"]) for r in rows}
        self.assertEqual(panels, {(8, a) for a in contrast.GLASS_ALPHAS} | {(48, 0.85)})
        self.assertEqual({r["style"] for r in rows}, {"Starfield"})
        self.assertEqual({r["device"] for r in rows}, {"tv"})
        self.assertEqual({r["overlay"] for r in rows}, {None, "one", "two"})

    def test_each_candidate_has_its_tint_laid_on_the_glass(self):
        colour = (0.5, 0.4, 0.8)
        rows = self.rows([uniform(192, 108, colour)])
        for name, tint in (("one", "#A78BFA"), ("two", "#33CC99")):
            alpha = contrast.parse_rgba(self.candidates[name]["color"]["accent-tint"])[1]
            with self.subTest(name=name):
                self.assertAlmostEqual(
                    self.row(rows, 8, 0.4, name), self.expected(colour, 0.4, tint, alpha), places=5
                )
        self.assertNotAlmostEqual(self.row(rows, 8, 0.4, "one"), self.row(rows, 8, 0.4, "two"), 3)

    def test_the_brightest_pixel_counts_and_not_the_average(self):
        # Half the frame is bright: with a blur of 8 dp, 1.6 px here, the middle of the bright half
        # is as bright as a flat frame of that colour.
        frame = uniform(192, 108, (0.1, 0.1, 0.1))
        frame[:, 96:] = (0.9, 0.9, 0.9)
        rows = self.rows([frame])
        self.assertAlmostEqual(self.row(rows, 8, 0.4), self.expected((0.9, 0.9, 0.9), 0.4), 3)

    def test_the_worst_of_the_frames_is_kept(self):
        dark, bright = uniform(192, 108, (0.1, 0.1, 0.1)), uniform(192, 108, (0.7, 0.7, 0.7))
        rows = self.rows([dark, bright, dark])
        self.assertAlmostEqual(self.row(rows, 8, 0.6), self.expected((0.7, 0.7, 0.7), 0.6), 5)

    def test_each_device_has_its_own_rows(self):
        sources = {
            ("Starfield", "tv"): [uniform(192, 108, (0.1, 0.1, 0.1))],
            ("Starfield", "phone"): [uniform(123, 267, (0.8, 0.8, 0.8))],
        }
        rows = contrast.measure_backdrops(sources, self.candidates, self.context)
        self.assertLess(self.row(rows, 8, 0.4, device="tv"), self.row(rows, 8, 0.4, device="phone"))

    def test_a_folder_of_frames_is_measured_with_its_sources_and_the_candidates(self):
        colour = (0.5, 0.4, 0.8)
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "Starfield-tv").mkdir()
            pixels = np.uint8(np.round(uniform(192, 108, colour) * 255))
            Image.fromarray(pixels).save(Path(folder) / "Starfield-tv" / "00.png")
            result = contrast.measure_accents(folder, self.candidates, self.context)
        self.assertEqual(result["sources"], [{"style": "Starfield", "device": "tv", "frames": 1}])
        self.assertEqual(sorted(result["candidates"]), ["one", "two"])
        self.assertIn("backdrops", result)
        self.assertEqual(result["meta"]["glass_tint"], "surface")
        json.dumps(result)


def backdrop_rows_of_the_table():
    """The rows of the glass table of the app, as a measurement gives them, with no tint on them."""
    text = contrast.GLASS_TABLE_KT.read_text(encoding="utf-8")
    table = contrast.read_glass_table_kotlin(text)
    return [
        {
            "style": style,
            "device": "tv",
            "blur_dp": blur,
            "alpha": alpha,
            "overlay": None,
            "luminance": luminance,
        }
        for style, cell in table.items()
        for (blur, alpha), luminance in cell.items()
    ]


class AccentReportTest(unittest.TestCase):
    """What the report says of a candidate, for the checks that need no frame."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)

    def report(self, one=None):
        found = contrast.accent_report({"one": one or candidate()}, self.context, [])
        return found["one"]

    def fill(self, report, name):
        return next(f for f in report["fills"] if f["id"] == name)

    def test_the_text_of_an_accent_fill_has_4_5_to_1_on_it(self):
        found = self.fill(self.report(), "on-accent/accent")
        expected = contrast.hex_contrast("#160B2E", "#A78BFA")
        self.assertAlmostEqual(found["ratio"], expected, places=9)
        self.assertEqual(found["limit"], 4.5)

    def test_accent_and_accent_soft_are_checked_as_text_on_the_three_surfaces(self):
        report = self.report()
        for role in ("accent", "accent-soft"):
            for surface, hex_value in SURFACE_HEX.items():
                with self.subTest(role=role, surface=surface):
                    found = self.fill(report, f"{role}/{surface}")
                    expected = contrast.hex_contrast(VIOLET_COLOR[role], hex_value)
                    self.assertAlmostEqual(found["ratio"], expected, places=9)
                    self.assertEqual(found["limit"], 4.5)

    def test_the_figures_of_violet_are_those_of_the_notes(self):
        # Table B has accent-soft at 10.32, 9.74 and 9.06, and the text field says accent is 6.61.
        report = self.report()
        for name, expected in (
            ("accent-soft/page", 10.32),
            ("accent-soft/surface", 9.74),
            ("accent-soft/surface-raised", 9.06),
            ("accent/surface", 6.61),
        ):
            with self.subTest(name=name):
                self.assertAlmostEqual(self.fill(report, name)["ratio"], expected, delta=0.006)

    def test_the_text_on_the_tint_of_a_selected_row_is_over_the_surface_and_the_raised_one(self):
        report = self.report()
        alpha = 0x21 / 255
        for surface in ("surface", "surface-raised"):
            below = contrast.parse_hex(SURFACE_HEX[surface])
            tint = alpha * contrast.parse_hex("#A78BFA") + (1 - alpha) * below
            tint = np.floor(tint * 255 + 0.5) / 255
            for role in ("accent", "accent-soft"):
                with self.subTest(role=role, surface=surface):
                    found = self.fill(report, f"{role}/tint-on-{surface}")
                    expected = contrast.rgb_contrast(contrast.parse_hex(VIOLET_COLOR[role]), tint)
                    self.assertAlmostEqual(found["ratio"], expected, places=9)
                    self.assertLess(found["ratio"], self.fill(report, f"{role}/{surface}")["ratio"])

    def test_the_snackbar_action_is_checked_as_text_on_the_light_ink_surface(self):
        # The action of a snackbar is drawn in accent-inverse on `ink`, the inverse surface.
        found = self.fill(self.report(), "accent-inverse/ink")
        self.assertAlmostEqual(found["ratio"], contrast.hex_contrast("#7C3AED", "#E8EAED"), 9)
        self.assertAlmostEqual(found["ratio"], 4.73, delta=0.006)
        self.assertEqual(found["limit"], 4.5)
        self.assertNotIn("accent-inverse/ink", self.report()["fails"])

    def test_an_action_colour_that_is_too_light_for_the_ink_is_a_failure(self):
        # accent-strong of teal is 2.30:1 on ink: it is why the role exists.
        light = candidate(color={"accent-inverse": "#06AF97"})
        found = self.fill(self.report(light), "accent-inverse/ink")
        self.assertAlmostEqual(found["ratio"], 2.30, delta=0.006)
        self.assertIn("accent-inverse/ink", self.report(light)["fails"])

    def test_the_strong_accent_is_reported_for_information_with_no_limit(self):
        found = self.fill(self.report(), "accent-strong/page")
        self.assertIsNone(found["limit"])
        self.assertAlmostEqual(found["ratio"], contrast.hex_contrast("#7C3AED", "#0E1013"), 9)
        self.assertNotIn("accent-strong/page", self.report()["fails"])

    def test_a_pair_that_falls_short_is_a_failure_by_the_id_of_the_check(self):
        weak = candidate(color={"on-accent": "#8B6FE0"})
        self.assertIn("on-accent/accent", self.report(weak)["fails"])
        self.assertNotIn("on-accent/accent", self.report()["fails"])

    def test_a_ratio_that_reaches_its_limit_is_not_a_failure(self):
        strong = candidate(color={"on-accent": "#000000", "accent": "#FFFFFF"})
        self.assertNotIn("on-accent/accent", self.report(strong)["fails"])

    def test_the_roles_and_the_label_are_in_the_report(self):
        report = self.report(candidate(label="Violet"))
        self.assertEqual(report["label"], "Violet")
        self.assertEqual(report["roles"]["color"], VIOLET_COLOR)
        self.assertEqual(report["roles"]["high-contrast"], VIOLET_HIGH_CONTRAST)

    def high(self, report):
        return {h["id"]: h for h in report["high_contrast"]}

    def test_high_contrast_text_has_7_to_1_on_every_fill_and_the_lowest_is_reported(self):
        found = self.high(self.report())
        self.assertEqual(found["hc:accent"]["limit"], 7.0)
        self.assertEqual(found["hc:accent-soft"]["limit"], 7.0)
        self.assertEqual(found["hc:accent-line"]["limit"], 3.0)
        self.assertEqual(found["hc:on-accent/accent"]["limit"], 7.0)
        # The notes: 7.55 and 9.04 are the lowest, and the label keeps 10:1 on the accent fill.
        self.assertAlmostEqual(found["hc:accent"]["ratio"], 7.55, delta=0.006)
        self.assertAlmostEqual(found["hc:accent-soft"]["ratio"], 9.04, delta=0.006)
        self.assertGreater(found["hc:on-accent/accent"]["ratio"], 10.0)

    def test_the_fills_of_high_contrast_include_the_selected_rows_and_the_hover(self):
        # The lowest ratio is where the fill is lightest, and the tint and the hover lighten it.
        found = self.high(self.report(candidate(high_contrast={"accent": "#7C6BC0"})))
        plain = ("page", "chrome", "surface", "surface-raised")
        self.assertNotIn(found["hc:accent"]["on"], plain)
        worst = min(
            contrast.hex_contrast("#7C6BC0", hex_value)
            for hex_value in ("#0E1013", "#0B0D10", "#14171B", "#1A1E24")
        )
        self.assertLess(found["hc:accent"]["ratio"], worst)

    def test_the_snackbar_action_keeps_7_to_1_on_the_white_ink_of_high_contrast(self):
        found = self.high(self.report())["hc:accent-inverse/ink"]
        self.assertAlmostEqual(found["ratio"], contrast.hex_contrast("#5744A9", "#FFFFFF"), 9)
        self.assertAlmostEqual(found["ratio"], 7.49, delta=0.006)
        self.assertEqual(found["limit"], 7.0)
        self.assertEqual(found["on"], "ink")
        self.assertNotIn("hc:accent-inverse/ink", self.report()["fails"])

    def test_the_snackbar_action_of_normal_contrast_is_short_of_7_to_1_on_that_ink(self):
        # accent-inverse of the colour group is 5.70:1 on white: it is why high contrast has its own.
        short = candidate(high_contrast={"accent-inverse": "#7C3AED"})
        found = self.high(self.report(short))["hc:accent-inverse/ink"]
        self.assertAlmostEqual(found["ratio"], 5.70, delta=0.006)
        self.assertIn("hc:accent-inverse/ink", self.report(short)["fails"])

    def test_a_high_contrast_accent_under_7_to_1_is_a_failure(self):
        report = self.report(candidate(high_contrast={"accent": "#7C6BC0"}))
        self.assertIn("hc:accent", report["fails"])
        self.assertIn("hc:on-accent/accent", report["fails"])
        self.assertNotIn("hc:accent", self.report()["fails"])

    def test_the_distance_from_the_status_colours_is_taken_where_the_palettes_are_for(self):
        found = {d["id"]: d for d in self.report()["distance"]}
        self.assertEqual(len(found), 14)
        relevant = {key for key, entry in found.items() if entry["relevant"]}
        self.assertEqual(
            relevant,
            {
                "distance:default:normal",
                "distance:red-green:normal",
                "distance:red-green:protanopia",
                "distance:red-green:deuteranopia",
                "distance:blue-yellow:normal",
                "distance:blue-yellow:tritanopia",
                "distance:ink",
                "distance:ink-2",
            },
        )
        for entry in found.values():
            self.assertEqual(entry["limit"], 20.0)

    def test_the_nearest_status_colour_and_its_distance_are_the_least_of_the_four(self):
        # An accent that is a status colour is at no distance from it.
        report = self.report(candidate(color={"accent": "#4CAF50"}))
        entry = {d["id"]: d for d in report["distance"]}["distance:default:normal"]
        self.assertEqual(entry["nearest"], "success")
        self.assertAlmostEqual(entry["least"], 0.0, places=9)
        self.assertIn("distance:default:normal", report["fails"])

    def test_the_distance_in_a_palette_is_that_of_the_colours_as_the_dichromat_sees_them(self):
        found = {d["id"]: d for d in self.report()["distance"]}
        expected = min(
            contrast.colour_distance("#A78BFA", status, "deuteranopia")
            for status in ("#56B4E9", "#F0E442", "#D55E00", "#808080")
        )
        self.assertAlmostEqual(found["distance:red-green:deuteranopia"]["least"], expected, 9)

    def test_a_vision_that_a_palette_is_not_for_has_a_row_that_does_not_fail(self):
        report = self.report(candidate(color={"accent": "#4CAF50"}))
        entry = {d["id"]: d for d in report["distance"]}["distance:default:deuteranopia"]
        self.assertFalse(entry["relevant"])
        self.assertLess(entry["least"], 20.0)
        self.assertNotIn("distance:default:deuteranopia", report["fails"])

    def test_every_accent_is_kept_apart_from_ink_and_ink_2(self):
        white = self.report(candidate(color={"accent": "#F5F3FF"}))
        found = {d["id"]: d for d in white["distance"]}
        self.assertTrue(found["distance:ink"]["relevant"])
        self.assertLess(found["distance:ink"]["least"], 20.0)
        self.assertLess(found["distance:ink-2"]["least"], 20.0)
        self.assertAlmostEqual(
            found["distance:ink"]["least"], contrast.colour_distance("#F5F3FF", "#E8EAED"), 9
        )
        self.assertIn("distance:ink", white["fails"])
        self.assertIn("distance:ink-2", white["fails"])

    def test_an_accent_with_a_hue_is_held_to_it_too_and_violet_keeps_it(self):
        # Violet is 29 from ink and 22 from ink-2 as a trichromat sees them. No chroma lets an
        # accent off: a pale lilac of chroma 21 is 16 from both, and fails.
        violet = self.report()
        found = {d["id"]: d for d in violet["distance"]}
        self.assertTrue(found["distance:ink"]["relevant"])
        self.assertTrue(found["distance:ink-2"]["relevant"])
        self.assertGreater(found["distance:ink"]["least"], 20.0)
        self.assertGreater(found["distance:ink-2"]["least"], 20.0)
        self.assertNotIn("distance:ink", violet["fails"])
        self.assertNotIn("distance:ink-2", violet["fails"])
        pale = self.report(candidate(color={"accent": "#E5D0F5"}))
        self.assertIn("distance:ink", pale["fails"])
        self.assertIn("distance:ink-2", pale["fails"])

    def test_the_glass_has_no_rows_without_a_backdrop(self):
        report = self.report()
        self.assertEqual(report["glass"], [])
        self.assertEqual(report["floors"], {})


class AccentGlassTest(unittest.TestCase):
    """Over the glass, with the figures of the notes: Tables L and M, the text field."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)
        self.rows = backdrop_rows_of_the_table()
        self.report = self.make(candidate(), self.rows)

    def make(self, one, rows):
        return contrast.accent_report({"one": one}, self.context, rows)["one"]

    def glass(self, report, style, spec):
        return next(g for g in report["glass"] if (g["style"], g["spec"]) == (style, spec))

    def test_the_floor_of_each_style_is_the_one_of_the_notes(self):
        # The floors at 8 dp in the notes; the default tint of 40 % is raised to them.
        self.assertEqual(
            self.report["floors"],
            {
                "AuroraCurtains": 30,
                "SoftGlow": 30,
                "NorthernLights": 70,
                "LightMist": 30,
                "FlowingLight": 40,
                "Starfield": 30,
            },
        )

    def test_a_row_is_a_style_at_the_default_and_at_reduced(self):
        self.assertEqual(len(self.report["glass"]), 12)
        for style, tint in zip(STYLES, (40, 40, 70, 40, 40, 40)):
            with self.subTest(style=style):
                self.assertEqual(self.glass(self.report, style, "standard")["tint"], tint)
                self.assertEqual(self.glass(self.report, style, "reduced")["tint"], 85)

    def test_the_ratios_of_violet_are_those_of_tables_l_and_m(self):
        standard = {
            # style: accent-soft (the focused label) and accent (the focused outline)
            "AuroraCurtains": (7.87, 5.34),
            "SoftGlow": (8.24, 5.59),
            "NorthernLights": (6.47, 4.39),
            "LightMist": (7.05, 4.78),
            "FlowingLight": (5.71, 3.88),
            "Starfield": (8.69, 5.89),
        }
        reduced = {
            "AuroraCurtains": (9.42, 6.39),
            "SoftGlow": (9.41, 6.39),
            "NorthernLights": (8.88, 6.02),
            "LightMist": (9.46, 6.42),
            "FlowingLight": (8.84, 6.00),
            "Starfield": (9.66, 6.56),
        }
        for spec, expected in (("standard", standard), ("reduced", reduced)):
            for style, (soft, accent) in expected.items():
                with self.subTest(spec=spec, style=style):
                    row = self.glass(self.report, style, spec)
                    self.assertAlmostEqual(row["accent-soft"], soft, delta=0.006)
                    self.assertAlmostEqual(row["accent"], accent, delta=0.006)
                    self.assertIsNone(row["selected"])

    def test_accent_as_text_falls_short_where_the_outline_does_not(self):
        fails = set(self.report["fails"])
        # 4.39 and 3.88 are outlines that keep 3:1 and text that does not keep 4.5:1.
        self.assertIn("glass:standard:NorthernLights:accent-text", fails)
        self.assertIn("glass:standard:FlowingLight:accent-text", fails)
        self.assertNotIn("glass:standard:LightMist:accent-text", fails)
        self.assertNotIn("glass:standard:AuroraCurtains:accent-text", fails)
        self.assertNotIn("glass:standard:FlowingLight:accent-outline", fails)
        self.assertNotIn("glass:standard:FlowingLight:accent-soft-text", fails)
        self.assertNotIn("glass:reduced:FlowingLight:accent-text", fails)

    def test_a_dark_enough_accent_falls_short_as_an_outline_too(self):
        report = self.make(candidate(color={"accent": "#4B3F8A"}), self.rows)
        self.assertIn("glass:standard:NorthernLights:accent-outline", report["fails"])

    def test_the_floor_follows_the_accent_soft_of_the_candidate(self):
        # accent-soft is one of the colours that the floor keeps at 4.5:1: a dim one raises it.
        report = self.make(candidate(color={"accent-soft": "#7A6AC0"}), self.rows)
        self.assertGreater(report["floors"]["Starfield"], 30)
        self.assertGreater(self.glass(report, "Starfield", "standard")["tint"], 40)

    def test_a_tint_off_the_grid_is_read_as_the_next_one_below_it(self):
        # No tint of the grid keeps this accent-soft, so the floor is 100 %, which is off the grid:
        # the backdrop is that of 85 %, the brightest of the next one below, as the app reads it.
        report = self.make(candidate(color={"accent-soft": "#4A3F8A"}), self.rows)
        row = self.glass(report, "Starfield", "standard")
        self.assertEqual(report["floors"]["Starfield"], 100)
        self.assertEqual(row["tint"], 100)
        text = contrast.GLASS_TABLE_KT.read_text(encoding="utf-8")
        table = contrast.read_glass_table_kotlin(text)
        self.assertEqual(row["backdrop"], table["Starfield"][(8, 0.85)])

    def test_the_grid_reads_a_tint_between_two_as_the_lower(self):
        cell = {(8, alpha): 0.0 for alpha in contrast.GLASS_ALPHAS}
        self.assertEqual(contrast._grid_alpha(cell, 0.55), 0.5)
        self.assertEqual(contrast._grid_alpha(cell, 0.5), 0.5)
        self.assertEqual(contrast._grid_alpha(cell, 1.0), 0.85)
        self.assertEqual(contrast._grid_alpha(cell, 0.2), 0.3)

    def test_the_selected_label_is_accent_over_the_tint_on_the_glass(self):
        # The rows of the tint on the glass are brighter than the glass alone by a little.
        rows = list(self.rows) + [
            {**row, "overlay": "one", "luminance": row["luminance"] + 0.01}
            for row in self.rows
            if row["blur_dp"] in (8, 48)
        ]
        report = self.make(candidate(), rows)
        found = self.glass(report, "NorthernLights", "standard")
        accent = float(contrast.relative_luminance(contrast.parse_hex("#A78BFA")))
        expected = (accent + 0.05) / (found["backdrop"] + 0.01 + 0.05)
        self.assertAlmostEqual(found["selected"], expected, places=6)
        self.assertLess(found["selected"], found["accent"])
        self.assertIn("glass:standard:NorthernLights:selected", report["fails"])
        self.assertNotIn("glass:standard:AuroraCurtains:selected", report["fails"])

    def test_the_selected_label_of_the_app_is_accent_soft_over_the_tint_on_the_glass(self):
        # The app draws the selected label in accent-soft: it keeps 4.5:1 where accent does not.
        rows = list(self.rows) + [
            {**row, "overlay": "one", "luminance": row["luminance"] + 0.01}
            for row in self.rows
            if row["blur_dp"] in (8, 48)
        ]
        report = self.make(candidate(), rows)
        found = self.glass(report, "NorthernLights", "standard")
        soft = float(contrast.relative_luminance(contrast.parse_hex("#C4B5FD")))
        expected = (soft + 0.05) / (found["backdrop"] + 0.01 + 0.05)
        self.assertAlmostEqual(found["selected-soft"], expected, places=6)
        self.assertGreater(found["selected-soft"], found["selected"])
        self.assertNotIn("glass:standard:NorthernLights:selected-soft", report["fails"])
        dark = self.make(candidate(color={"accent-soft": "#7A6AC0"}), rows)
        self.assertIn("glass:standard:NorthernLights:selected-soft", dark["fails"])

    def test_the_selected_label_has_no_row_where_no_tint_was_laid_on_the_glass(self):
        for row in self.report["glass"]:
            self.assertIsNone(row["selected-soft"])

    def test_the_worst_of_two_devices_is_the_brightest_backdrop(self):
        brighter = [
            {**row, "device": "phone", "luminance": row["luminance"] + 0.02}
            for row in self.rows
            if row["style"] == "Starfield" and (row["blur_dp"], row["alpha"]) == (8, 0.4)
        ]
        report = self.make(candidate(), self.rows + brighter)
        found = self.glass(report, "Starfield", "standard")
        self.assertAlmostEqual(found["backdrop"], brighter[0]["luminance"], places=9)


class AccentTablesTest(unittest.TestCase):
    """The Markdown tables of the accents, and the failures that they name."""

    def setUp(self):
        context = contrast.accent_context(TOKENS)
        self.candidates = {
            "violet": candidate(label="Violet"),
            "pale": candidate(
                label="Pale",
                color={"accent": "#F5F3FF", "accent-soft": "#FFFFFF"},
                high_contrast={"accent": "#FFFFFF"},
            ),
        }
        report = contrast.accent_report(self.candidates, context, backdrop_rows_of_the_table())
        self.result = {"meta": {"date": "2026-10-21", "revision": "abc"}, "candidates": report}
        self.text = contrast.accent_tables(self.result)

    def test_there_is_a_table_for_each_part_of_the_checks(self):
        for title in ("N", "O", "P", "Q", "R", "S", "T", "U", "W"):
            with self.subTest(title=title):
                self.assertIn(f"Table {title}.", self.text)

    def test_the_selected_label_of_the_app_has_its_own_table_over_the_glass(self):
        rows = [
            {**row, "overlay": name, "luminance": row["luminance"] + 0.01}
            for row in backdrop_rows_of_the_table()
            for name in self.candidates
            if row["blur_dp"] in (8, 48)
        ]
        context = contrast.accent_context(TOKENS)
        report = contrast.accent_report(
            self.candidates, context, backdrop_rows_of_the_table() + rows
        )
        text = contrast.accent_tables({"meta": {}, "candidates": report})
        self.assertRegex(text, r"Table W\. The selected label of the app, `accent-soft`")
        table = text[text.index("Table W.") :]
        self.assertIn("| Aurora curtains | Standard |", table)

    def test_the_role_sets_are_listed_by_candidate(self):
        self.assertIn("`#A78BFA`", self.text)
        self.assertIn("`#F5F3FF`", self.text)
        self.assertIn("| Role | Violet | Pale |", self.text)

    def test_a_ratio_just_short_of_a_limit_is_not_read_as_the_limit(self):
        self.assertEqual(contrast.format_ratio(4.496), "4.496")
        self.assertIn("5.34", self.text)

    def test_a_cell_short_of_its_limit_says_so(self):
        self.assertRegex(self.text, r"4\.39 \(outline only\)")
        self.assertRegex(self.text, r"3\.88 \(outline only\)")
        self.assertNotRegex(self.text, r"5\.34 \(")

    def test_the_failures_of_each_candidate_are_named(self):
        self.assertIn("Violet fails", self.text)
        self.assertIn("Pale fails", self.text)
        self.assertIn("glass:standard:NorthernLights:accent-text", self.text)
        self.assertIn("distance:ink", self.text)

    def test_a_candidate_that_passes_everything_says_so(self):
        context = contrast.accent_context(TOKENS)
        report = contrast.accent_report({"ok": candidate(label="Ok")}, context, [])
        report["ok"]["fails"] = []
        text = contrast.accent_tables({"meta": {}, "candidates": report})
        self.assertIn("Ok fails no check.", text)

    def test_the_part_that_needs_no_frame_is_printed_with_no_rows_of_glass(self):
        context = contrast.accent_context(TOKENS)
        report = contrast.accent_report({"ok": candidate(label="Ok")}, context, [])
        text = contrast.accent_tables({"meta": {}, "candidates": report})
        self.assertIn("Table N.", text)
        self.assertIn("Table O.", text)


class AccentCommandTest(unittest.TestCase):
    """`accents <frames>` reads accents.json and the frames and prints the tables."""

    def run_command(self, folder, *extra):
        frames = Path(folder) / "frames"
        (frames / "Starfield-tv").mkdir(parents=True)
        (frames / "Starfield-phone").mkdir(parents=True)
        Image.new("RGB", (192, 108), (40, 60, 90)).save(frames / "Starfield-tv" / "00.png")
        Image.new("RGB", (123, 267), (40, 60, 90)).save(frames / "Starfield-phone" / "00.png")
        accents = write_accents(folder, one=candidate(label="One"), two=candidate(label="Two"))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = contrast.main(["accents", str(frames), "--accents", str(accents), *extra])
        return code, out.getvalue()

    def test_the_command_prints_the_tables(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out = self.run_command(folder, "--jobs", "1")
        self.assertEqual(code, 0)
        self.assertIn("Table N.", out)
        self.assertIn("| Role | One | Two |", out)
        self.assertIn("Starfield", out)

    def test_the_json_is_written_when_it_is_asked_for(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "accents-result.json"
            code, _ = self.run_command(folder, "--jobs", "1", "--json", str(target))
            written = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual(code, 0)
        self.assertEqual(sorted(written["candidates"]), ["one", "two"])
        self.assertEqual(written["sources"][0]["style"], "Starfield")
        self.assertEqual(written["meta"]["revision"], contrast._revision())
        self.assertEqual(len(written["meta"]["date"]), 10)
        self.assertIn("backdrops", written)
        self.assertEqual(written["candidates"]["one"]["floors"]["Starfield"], 30)

    def test_no_json_is_written_when_it_is_not_asked_for(self):
        with tempfile.TemporaryDirectory() as folder:
            self.run_command(folder, "--jobs", "1")
            self.assertEqual(list(Path(folder).glob("*.json")), [Path(folder) / "accents.json"])

    def test_the_command_reads_the_tokens_it_is_given(self):
        with tempfile.TemporaryDirectory() as folder:
            data = json.loads(TOKENS.read_text(encoding="utf-8"))
            data["color"]["surface"]["$value"] = "#FFFFFF"
            tokens_file = Path(folder) / "tokens.json"
            tokens_file.write_text(json.dumps(data), encoding="utf-8")
            target = Path(folder) / "accents-result.json"
            args = ("--tokens", str(tokens_file), "--json", str(target), "--jobs", "1")
            self.run_command(folder, *args)
            written = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual(written["meta"]["colors"]["surface"], "#FFFFFF")

    def test_an_accents_file_that_is_wrong_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            frames = Path(folder) / "frames"
            (frames / "Starfield-tv").mkdir(parents=True)
            Image.new("RGB", (192, 108), (40, 60, 90)).save(frames / "Starfield-tv" / "00.png")
            accents = write_accents(folder, one=candidate(color={"accent": "purple"}))
            err = io.StringIO()
            with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as stop:
                contrast.main(["accents", str(frames), "--accents", str(accents)])
        self.assertEqual(stop.exception.code, 2)
        self.assertIn("one", err.getvalue())
        self.assertIn("purple", err.getvalue())


class ShippedAccentsTest(unittest.TestCase):
    """The candidates that `design/accessibility/accents.json` proposes for the gate."""

    @classmethod
    def setUpClass(cls):
        cls.accents = contrast.load_accents(contrast.ACCENTS_JSON)
        cls.context = contrast.accent_context(TOKENS)
        cls.report = contrast.accent_report(cls.accents, cls.context, [])

    def test_the_candidates_are_violet_green_teal_and_white(self):
        self.assertEqual(list(self.accents), ["violet", "green", "teal", "white"])

    def test_violet_is_the_accent_tokens_of_sprint_10(self):
        violet = self.accents["violet"]
        self.assertEqual(violet["color"], VIOLET_COLOR)
        self.assertEqual(violet["high-contrast"], VIOLET_HIGH_CONTRAST)

    def test_the_others_start_from_the_colours_of_the_icon(self):
        sources = {name: self.accents[name]["source"] for name in ("green", "teal", "white")}
        self.assertIn("#6EE7B7", sources["green"])
        self.assertIn("#14B8A6", sources["teal"])
        self.assertIn("#F5F3FF", sources["white"])

    def test_every_role_has_a_note_on_how_its_value_was_derived(self):
        for name, found in self.accents.items():
            roles = (*found["color"], *(f"high-contrast.{r}" for r in found["high-contrast"]))
            for role in roles:
                with self.subTest(candidate=name, role=role):
                    self.assertGreater(len(found["notes"][role]), 20)

    def test_each_set_passes_every_check_that_needs_no_frame_but_the_ones_it_names(self):
        for name, found in self.accents.items():
            with self.subTest(candidate=name):
                self.assertEqual(sorted(self.report[name]["fails"]), sorted(found["known_fails"]))

    def test_green_and_teal_pass_all_of_them(self):
        self.assertEqual(self.accents["green"]["known_fails"], {})
        self.assertEqual(self.accents["teal"]["known_fails"], {})

    def test_violet_is_not_told_from_the_sky_blue_of_the_red_green_palette_by_a_dichromat(self):
        # Today's accent is a violet that a protanope and a deuteranope see as blue, and a tritanope
        # sees as grey: it is 9, 5 and 15 from the success and offline colours that they have.
        self.assertEqual(
            sorted(self.accents["violet"]["known_fails"]),
            [
                "distance:blue-yellow:tritanopia",
                "distance:red-green:deuteranopia",
                "distance:red-green:protanopia",
            ],
        )

    def test_white_cannot_be_told_from_the_ink_whatever_its_value(self):
        known = self.accents["white"]["known_fails"]
        self.assertEqual(sorted(known), ["distance:ink", "distance:ink-2"])
        for reason in known.values():
            self.assertIn("Whatever its value", reason)

    def test_every_set_has_a_snackbar_role_that_keeps_4_5_to_1_on_ink(self):
        for name, found in self.accents.items():
            with self.subTest(candidate=name):
                fills = self.report[name]["fills"]
                check = next(c for c in fills if c["id"] == "accent-inverse/ink")
                self.assertGreaterEqual(check["ratio"], 4.5)
                self.assertNotIn("accent-inverse/ink", found["known_fails"])

    def test_every_set_has_a_high_contrast_snackbar_role_that_keeps_7_to_1_on_the_white_ink(self):
        for name, found in self.accents.items():
            with self.subTest(candidate=name):
                checks = {c["id"]: c for c in self.report[name]["high_contrast"]}
                self.assertGreaterEqual(checks["hc:accent-inverse/ink"]["ratio"], 7.0)
                self.assertNotIn("hc:accent-inverse/ink", found["known_fails"])

    def test_violet_keeps_its_strong_accent_as_the_snackbar_role(self):
        colors = self.accents["violet"]["color"]
        self.assertEqual(colors["accent-inverse"], colors["accent-strong"])

    def test_the_known_failures_are_checks_of_the_set_with_a_reason_that_says_something(self):
        for name, found in self.accents.items():
            report = self.report[name]
            ids = {c["id"] for key in ("fills", "high_contrast", "distance") for c in report[key]}
            for check, reason in found["known_fails"].items():
                with self.subTest(candidate=name, check=check):
                    self.assertIn(check, ids)
                    self.assertGreater(len(reason), 40)

    def test_the_sets_that_pass_keep_a_margin_over_the_distance_that_they_need(self):
        # The model of colour vision is an approximation, so a set is proposed with more than 20.
        for name in ("green", "teal", "white"):
            for entry in self.report[name]["distance"]:
                if entry["relevant"] and entry["id"] not in self.accents[name]["known_fails"]:
                    with self.subTest(candidate=name, check=entry["id"]):
                        self.assertGreaterEqual(entry["least"], 20.4)


def role_set(group):
    """The roles of a group of `tokens.json`, `{role: value}`, with no `$` keys."""
    return {
        role: found["$value"]
        for role, found in group.items()
        if not role.startswith("$") and role != "high-contrast"
    }


class ShippedTokensTest(unittest.TestCase):
    """`design/tokens.json` carries the sets of `accents.json` that the owner chose (ADR 0058,
    decision 5): violet in the colour groups, teal and white in a group of their own."""

    @classmethod
    def setUpClass(cls):
        cls.tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
        cls.accents = contrast.load_accents(contrast.ACCENTS_JSON)

    def test_violet_is_the_colour_and_high_contrast_groups(self):
        violet = self.accents["violet"]
        colors = {role: self.tokens["color"][role]["$value"] for role in violet["color"]}
        high = self.tokens["high-contrast"]
        high = {role: high[role]["$value"] for role in violet["high-contrast"]}
        self.assertEqual(colors, violet["color"])
        self.assertEqual(high, violet["high-contrast"])

    def test_teal_and_white_are_a_group_each_with_a_high_contrast_group(self):
        for name in ("teal", "white"):
            with self.subTest(set=name):
                group = self.tokens[f"accent-{name}"]
                self.assertEqual(role_set(group), self.accents[name]["color"])
                high = {r: f["$value"] for r, f in group["high-contrast"].items() if r[0] != "$"}
                self.assertEqual(high, self.accents[name]["high-contrast"])

    def test_the_groups_are_colours_and_each_role_says_what_it_is_for(self):
        for name in ("teal", "white"):
            group = self.tokens[f"accent-{name}"]
            self.assertEqual(group["$type"], "color")
            for role, found in group.items():
                if role.startswith("$") or role == "high-contrast":
                    continue
                with self.subTest(set=name, role=role):
                    self.assertGreater(len(found["$description"]), 10)

    def test_the_green_candidate_is_not_a_set_of_the_tokens(self):
        self.assertNotIn("accent-green", self.tokens)

    def test_each_variant_under_a_palette_is_a_group_with_a_high_contrast_group(self):
        variants = contrast.load_palette_variants(contrast.ACCENTS_JSON)
        for palette, found in variants.items():
            for name, variant in found.items():
                with self.subTest(accent=name, palette=palette):
                    group = self.tokens[f"accent-{name}-{palette}"]
                    self.assertEqual(role_set(group), variant["color"])
                    found = group["high-contrast"].items()
                    high = {role: token["$value"] for role, token in found if role[0] != "$"}
                    self.assertEqual(high, variant["high-contrast"])

    def test_there_is_a_group_for_each_variant_and_for_no_other_pair(self):
        variants = contrast.load_palette_variants(contrast.ACCENTS_JSON)
        expected = {f"accent-{n}-{p}" for p, found in variants.items() for n in found}
        groups = {
            key
            for key in self.tokens
            for palette in contrast.PALETTES
            if key.startswith("accent-") and key.endswith(f"-{palette}")
        }
        self.assertEqual(groups, expected)
        self.assertEqual(
            expected,
            {"accent-violet-red-green", "accent-violet-blue-yellow", "accent-white-blue-yellow"},
        )

    def test_the_groups_of_the_variants_say_which_palette_they_are_for(self):
        keys = ("accent-violet-red-green", "accent-violet-blue-yellow", "accent-white-blue-yellow")
        for key in keys:
            group = self.tokens[key]
            palette = "Red-green" if key.endswith("red-green") else "Blue-yellow"
            with self.subTest(group=key):
                self.assertEqual(group["$type"], "color")
                self.assertIn(palette, group["$description"])
                for role, found in group.items():
                    if role.startswith("$") or role == "high-contrast":
                        continue
                    self.assertGreater(len(found["$description"]), 10, role)


# The accents under a colour-blind palette (issue #50, after the UAT of 2026-10-22)


TEAL_COLOR = {
    "accent": "#4BECD0",
    "accent-soft": "#A2FCE8",
    "accent-strong": "#06AF97",
    "on-accent": "#001713",
    "accent-tint": "#4BECD021",
    "accent-line": "#4BECD066",
    "accent-inverse": "#027464",
}
TEAL_HIGH_CONTRAST = {
    "accent": "#4BECD0",
    "accent-soft": "#A2FCE8",
    "accent-line": "#4BECD0",
    "accent-inverse": "#026053",
}
# The violet of the blue-yellow palette, with the colours that these tests use.
PINKER_VIOLET_COLOR = {
    "accent": "#C688FE",
    "accent-soft": "#C8BBFE",
    "accent-tint": "#C688FE21",
    "accent-line": "#C688FE66",
}
PINKER_VIOLET_HIGH_CONTRAST = {
    "accent": "#C7B9FC",
    "accent-soft": "#CAC4FE",
    "accent-line": "#C7B9FC",
}


def teal():
    return candidate(label="Teal", color=TEAL_COLOR, high_contrast=TEAL_HIGH_CONTRAST)


def pinker_violet():
    return candidate(
        label="Violet, Blue-yellow",
        color=PINKER_VIOLET_COLOR,
        high_contrast=PINKER_VIOLET_HIGH_CONTRAST,
    )


def write_accents_with_variants(folder, variants, **candidates):
    """An `accents.json` with candidates and the variants that go with a palette each:
    `variants` is `{palette: {name: candidate}}`."""
    path = Path(folder) / "accents.json"
    path.write_text(
        json.dumps({"candidates": candidates, "palette_variants": variants}), encoding="utf-8"
    )
    return path


class PaletteChecksTest(unittest.TestCase):
    """How far the roles of an accent are from the status colours of a palette."""

    @classmethod
    def setUpClass(cls):
        cls.context = contrast.accent_context(TOKENS)
        cls.accents = contrast.load_accents(contrast.ACCENTS_JSON)

    def checks(self, name, palette):
        found = self.accents[name]
        checks = contrast.palette_checks(
            found["color"], found["high-contrast"], self.context, palette
        )
        return {check["id"]: check for check in checks}

    def test_a_palette_is_measured_for_normal_vision_and_for_the_vision_it_is_for(self):
        def visions(palette):
            return {c["vision"] for c in self.checks("teal", palette).values()}

        self.assertEqual(visions("red-green"), {"normal", "protanopia", "deuteranopia"})
        self.assertEqual(visions("blue-yellow"), {"normal", "tritanopia"})
        self.assertEqual(visions("default"), {"normal"})

    def test_the_roles_that_mark_an_accent_are_measured_with_those_of_high_contrast(self):
        roles = {c["role"] for c in self.checks("teal", "red-green").values()}
        self.assertEqual(
            roles,
            {
                "accent",
                "accent-soft",
                "accent-strong",
                "accent-line",
                "hc:accent",
                "hc:accent-soft",
                "hc:accent-line",
            },
        )

    def test_a_check_is_named_by_its_palette_its_vision_and_its_role(self):
        found = self.checks("teal", "red-green")
        self.assertIn("palette:red-green:protanopia:accent-soft", found)
        self.assertIn("palette:red-green:normal:hc:accent-line", found)
        self.assertEqual(found["palette:red-green:protanopia:accent-soft"]["limit"], 20.0)

    def test_the_line_is_measured_as_it_lies_over_the_surface(self):
        # The line is the accent at 40 %: over `surface` it is a dark tone, as the border is seen.
        surface = contrast.parse_hex("#14171B")
        colours = contrast.palette_role_colours(VIOLET_COLOR, VIOLET_HIGH_CONTRAST, surface)
        np.testing.assert_allclose(colours["accent-line"] * 255, [0x4F, 0x45, 0x74], atol=1e-9)
        np.testing.assert_allclose(colours["accent"], contrast.parse_hex("#A78BFA"))
        np.testing.assert_allclose(colours["hc:accent-line"], contrast.parse_hex("#C8B7FC"))

    def test_violet_is_5_from_the_sky_blue_of_the_red_green_palette_as_a_deuteranope_sees_it(self):
        found = self.checks("violet", "red-green")["palette:red-green:deuteranopia:accent"]
        self.assertAlmostEqual(found["least"], 5.0, delta=0.05)
        self.assertEqual(found["nearest"], "success")

    def test_the_soft_accent_of_violet_is_3_5_from_the_sky_blue_as_a_protanope_sees_it(self):
        found = self.checks("violet", "red-green")["palette:red-green:protanopia:accent-soft"]
        self.assertAlmostEqual(found["least"], 3.5, delta=0.05)
        self.assertEqual(found["nearest"], "success")

    def test_the_distance_is_the_least_of_the_four_status_colours_of_the_palette(self):
        found = self.checks("teal", "red-green")["palette:red-green:deuteranopia:accent"]
        expected = {
            status: contrast.colour_distance("#4BECD0", colour, "deuteranopia")
            for status, colour in self.context["status"]["red-green"].items()
        }
        self.assertEqual(sorted(expected), ["danger", "offline", "success", "warning"])
        self.assertAlmostEqual(found["least"], min(expected.values()), places=9)
        self.assertEqual(found["nearest"], min(expected, key=expected.get))

    def test_the_inks_are_given_for_information_and_the_figures_of_sprint_11_are_there(self):
        # Teal is 23.5 from the success green of today's palette and 15.3 from its text colour.
        found = self.checks("teal", "default")["palette:default:normal:accent"]
        self.assertAlmostEqual(found["least"], 23.5, delta=0.05)
        self.assertEqual(found["nearest"], "success")
        self.assertAlmostEqual(found["ink_least"], 15.3, delta=0.05)
        self.assertEqual(found["ink_nearest"], "success-ink")

    def test_the_inks_do_not_count_toward_the_distance_that_is_held(self):
        # Every colour light enough to be read as text is within 20 of a light ink in the red-green
        # palette, so the rule is held to the four status colours, as the palettes are.
        found = self.checks("teal", "red-green")
        check = found["palette:red-green:protanopia:accent"]
        self.assertLess(check["ink_least"], 20.0)
        self.assertGreaterEqual(check["least"], 20.0)
        self.assertEqual(contrast.palette_fails(found.values()), [])

    def test_the_accent_the_soft_accent_and_their_high_contrast_colours_are_held(self):
        held = {c["role"] for c in self.checks("teal", "red-green").values() if c["held"]}
        self.assertEqual(
            held, {"accent", "accent-soft", "hc:accent", "hc:accent-soft", "hc:accent-line"}
        )

    def test_the_strong_accent_and_the_line_are_measured_and_not_held(self):
        # Teal's line over the surface is 10.5 from the grey of the palette as a protanope sees it.
        found = self.checks("teal", "red-green")
        line = found["palette:red-green:protanopia:accent-line"]
        self.assertAlmostEqual(line["least"], 10.5, delta=0.05)
        self.assertEqual(line["nearest"], "offline")
        self.assertFalse(line["held"])
        self.assertFalse(found["palette:red-green:normal:accent-strong"]["held"])

    def test_a_palette_that_is_not_for_a_vision_holds_nothing(self):
        for name in ("violet", "teal", "white"):
            with self.subTest(accent=name):
                self.assertFalse(any(c["held"] for c in self.checks(name, "default").values()))

    def test_the_failures_are_the_held_checks_that_fall_short_of_20(self):
        fails = contrast.palette_fails(self.checks("violet", "red-green").values())
        self.assertIn("palette:red-green:protanopia:accent", fails)
        self.assertIn("palette:red-green:deuteranopia:accent-soft", fails)
        self.assertNotIn("palette:red-green:normal:accent", fails)
        self.assertNotIn("palette:red-green:protanopia:accent-line", fails)
        self.assertEqual(contrast.palette_fails(self.checks("teal", "blue-yellow").values()), [])


class PaletteVariantFileTest(unittest.TestCase):
    """The variants of `accents.json` are read and checked as the candidates are."""

    def load(self, variants, **candidates):
        with tempfile.TemporaryDirectory() as folder:
            path = write_accents_with_variants(folder, variants, **candidates)
            return contrast.load_palette_variants(path), contrast.load_accents(path)

    def test_a_file_with_no_variants_has_none(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_accents(folder, violet=candidate())
            self.assertEqual(contrast.load_palette_variants(path), {})

    def test_a_variant_is_a_candidate_of_the_palette_it_is_for(self):
        variants, _ = self.load(
            {"blue-yellow": {"violet": candidate(color={"accent": "#C688FE"})}}, violet=candidate()
        )
        self.assertEqual(list(variants), ["blue-yellow"])
        self.assertEqual(variants["blue-yellow"]["violet"]["color"]["accent"], "#C688FE")
        self.assertEqual(variants["blue-yellow"]["violet"]["label"], "Test")

    def test_only_the_colour_blind_palettes_have_variants(self):
        with self.assertRaisesRegex(ValueError, "default"):
            self.load({"default": {"violet": candidate()}}, violet=candidate())

    def test_a_variant_of_an_accent_that_is_not_a_candidate_is_refused(self):
        with self.assertRaisesRegex(ValueError, "teal.*red-green.*no candidate"):
            self.load({"red-green": {"teal": candidate()}}, violet=candidate())

    def test_a_variant_is_checked_like_a_candidate_and_reported_with_its_palette(self):
        broken = candidate()
        del broken["notes"]["accent-soft"]
        with self.assertRaisesRegex(ValueError, "violet.*blue-yellow.*accent-soft.*how"):
            self.load({"blue-yellow": {"violet": broken}}, violet=candidate())
        with self.assertRaisesRegex(ValueError, "violet.*red-green.*accent.*purple"):
            self.load(
                {"red-green": {"violet": candidate(color={"accent": "purple"})}},
                violet=candidate(),
            )

    def test_the_accents_as_a_palette_draws_them_are_the_variants_or_themselves(self):
        variants, accents = self.load(
            {"blue-yellow": {"white": candidate(color={"accent": "#EFF3FF"})}},
            violet=candidate(),
            white=candidate(color={"accent": "#EFF3FF", "accent-soft": "#FFFFFF"}),
        )
        under = contrast.palette_candidates(accents, variants, "blue-yellow")
        self.assertEqual(list(under), ["violet", "white"])
        self.assertIs(under["violet"], accents["violet"])
        self.assertIs(under["white"], variants["blue-yellow"]["white"])
        again = contrast.palette_candidates(accents, variants, "red-green")
        self.assertIs(again["white"], accents["white"])


class PaletteReportTest(unittest.TestCase):
    """The checks of the accents as they are and of their variants, with no frames."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)
        self.candidates = {"violet": candidate(label="Violet"), "teal": teal()}
        self.variants = {"blue-yellow": {"violet": pinker_violet()}}
        self.report = contrast.palette_report(self.candidates, self.variants, self.context)

    def test_there_is_a_column_for_each_accent_and_each_variant(self):
        columns = [(c["key"], c["label"], c["palette"]) for c in self.report["columns"]]
        self.assertEqual(
            columns,
            [
                ("violet", "Violet", None),
                ("teal", "Teal", None),
                ("violet@blue-yellow", "Violet, Blue-yellow", "blue-yellow"),
            ],
        )

    def test_an_accent_has_its_checks_under_every_palette_and_a_variant_under_its_own(self):
        checks = self.report["checks"]
        self.assertEqual(list(checks["violet"]), ["default", "red-green", "blue-yellow"])
        self.assertEqual(list(checks["violet@blue-yellow"]), ["blue-yellow"])

    def test_the_failures_are_those_of_the_held_checks_of_each_column(self):
        fails = self.report["fails"]
        self.assertIn("palette:red-green:protanopia:accent", fails["violet"])
        self.assertIn("palette:blue-yellow:tritanopia:accent", fails["violet"])
        self.assertEqual(fails["teal"], [])
        # A variant is held to its own palette alone.
        self.assertEqual(fails["violet@blue-yellow"], [])

    def test_a_variant_that_is_no_better_fails_under_its_palette(self):
        same = {"blue-yellow": {"violet": candidate(label="Same")}}
        report = contrast.palette_report({"violet": candidate()}, same, self.context)
        fails = report["fails"]["violet@blue-yellow"]
        self.assertIn("palette:blue-yellow:tritanopia:accent", fails)

    def test_the_report_is_plain_data(self):
        json.dumps(self.report)


class PaletteTablesTest(unittest.TestCase):
    """Table X: the distances of the roles, as the palettes and the visions see them."""

    def setUp(self):
        context = contrast.accent_context(TOKENS)
        self.candidates = {"violet": candidate(label="Violet"), "teal": teal()}
        variants = {"blue-yellow": {"violet": pinker_violet()}}
        report = contrast.palette_report(self.candidates, variants, context)
        reports = contrast.accent_report(self.candidates, context, [])
        self.text = contrast.accent_tables({"meta": {}, "candidates": reports, "palettes": report})

    def row(self, label):
        table = self.text[self.text.index("Table X.") :]
        found = [line for line in table.splitlines() if line.startswith(f"| {label} |")]
        self.assertEqual(len(found), 1, label)
        return [cell.strip() for cell in found[0].strip("|").split("|")]

    def test_table_x_follows_table_u_and_names_the_columns(self):
        self.assertIn("Table X.", self.text)
        self.assertLess(self.text.index("Table U."), self.text.index("Table X."))
        header = self.row("Check")
        self.assertEqual(header[-3:], ["Violet", "Teal", "Violet, Blue-yellow"])

    def test_a_held_distance_that_falls_short_says_so(self):
        cells = self.row("`accent` from the red-green status colours, deuteranopia vision")
        self.assertTrue(cells[1].startswith("5.0, success (short)"), cells[1])
        self.assertTrue(cells[2].startswith("21.6, success"))
        self.assertNotIn("short", cells[2])

    def test_a_variant_has_cells_only_under_its_own_palette(self):
        under_red_green = self.row("`accent` from the red-green status colours, normal vision")
        under_tritanopia = self.row(
            "`accent` from the blue-yellow status colours, tritanopia vision"
        )
        self.assertEqual(under_red_green[-1], "-")
        self.assertNotEqual(under_tritanopia[-1], "-")
        self.assertIn("short", under_tritanopia[1])
        self.assertNotIn("short", under_tritanopia[-1])

    def test_the_strong_accent_and_the_line_that_fall_short_are_not_held(self):
        cells = self.row("`accent-line` from the red-green status colours, protanopia vision")
        self.assertIn("(not held)", cells[2])
        self.assertNotIn("short", cells[2])

    def test_the_high_contrast_colours_have_rows_of_their_own(self):
        cells = self.row(
            "high contrast `accent` from the red-green status colours, protanopia vision"
        )
        self.assertIn("short", cells[1])

    def test_the_inks_that_are_near_are_given_for_information(self):
        # Teal is 15.3 from the success text colour of today's palette: the 20 is held to the fills.
        cells = self.row("`accent` from the default status colours, normal vision")
        self.assertIn("23.5, success", cells[2])
        self.assertIn("ink 15.3, success-ink", cells[2])

    def test_a_result_with_no_palette_report_has_no_table_x(self):
        context = contrast.accent_context(TOKENS)
        reports = contrast.accent_report(self.candidates, context, [])
        text = contrast.accent_tables({"meta": {}, "candidates": reports})
        self.assertNotIn("Table X.", text)


class PaletteAccentReportTest(unittest.TestCase):
    """`accent_report` for the accents as a palette draws them."""

    def setUp(self):
        self.context = contrast.accent_context(TOKENS)

    def report(self, one, palette):
        return contrast.accent_report({"one": one}, self.context, [], palette=palette)["one"]

    def test_the_failures_under_a_palette_are_the_held_checks_of_that_palette(self):
        report = self.report(candidate(), "red-green")
        self.assertIn("palette:red-green:protanopia:accent", report["fails"])
        self.assertNotIn("palette:blue-yellow:tritanopia:accent", report["fails"])
        self.assertEqual(report["palette"], "red-green")
        self.assertTrue(report["palette_checks"])

    def test_the_status_colours_of_the_other_palettes_are_not_held(self):
        report = self.report(candidate(), "blue-yellow")
        self.assertNotIn("distance:red-green:deuteranopia", report["fails"])
        self.assertNotIn("distance:default:normal", report["fails"])
        self.assertIn("distance:ink", [d["id"] for d in report["distance"]])

    def test_the_ink_is_held_under_a_palette_as_it_is_without_one(self):
        white = candidate(color={"accent": "#F5F3FF"})
        self.assertIn("distance:ink", self.report(white, "red-green")["fails"])

    def test_table_u_sends_only_the_rows_of_the_palette_that_is_asked_to_table_x(self):
        # The accent of the red-green palette is not drawn under the blue-yellow one, so its rows
        # of the red-green palette are not claimed there.
        reports = contrast.accent_report({"one": candidate()}, self.context, [], "blue-yellow")
        text = contrast.accent_tables({"meta": {}, "candidates": reports})

        def row(palette, vision):
            start = f"| `accent` from the {palette} status colours, {vision} vision |"
            return next(line for line in text.splitlines() if line.startswith(start))

        self.assertIn("(Table X)", row("blue-yellow", "tritanopia"))
        self.assertIn("(not claimed)", row("red-green", "protanopia"))
        self.assertNotIn("(Table X)", row("default", "normal"))

    def test_a_report_with_no_palette_is_as_it_was(self):
        report = contrast.accent_report({"one": candidate()}, self.context, [])["one"]
        self.assertNotIn("palette", report)
        self.assertFalse([f for f in report["fails"] if f.startswith("palette:")])


class AccentPaletteCommandTest(unittest.TestCase):
    """`accents <frames> --palette` measures the accents as that palette draws them."""

    def run_command(self, folder, *extra):
        frames = Path(folder) / "frames"
        (frames / "Starfield-tv").mkdir(parents=True)
        Image.new("RGB", (192, 108), (40, 60, 90)).save(frames / "Starfield-tv" / "00.png")
        variants = {"blue-yellow": {"one": dict(pinker_violet(), label="One, Blue-yellow")}}
        accents = write_accents_with_variants(
            folder, variants, one=candidate(label="One"), two=candidate(label="Two")
        )
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = contrast.main(
                ["accents", str(frames), "--accents", str(accents), "--jobs", "1", *extra]
            )
        return code, out.getvalue()

    def test_without_a_palette_the_accents_are_as_they_are_and_table_x_is_there(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out = self.run_command(folder)
        self.assertEqual(code, 0)
        self.assertIn("| Role | One | Two |", out)
        self.assertIn("Table X.", out)
        self.assertIn("One, Blue-yellow", out)

    def test_a_palette_measures_the_variant_in_place_of_the_accent_it_is_for(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out = self.run_command(folder, "--palette", "blue-yellow")
        self.assertEqual(code, 0)
        self.assertIn("| Role | One, Blue-yellow | Two |", out)
        self.assertIn("`#C688FE`", out)
        self.assertIn("Table X.", out)

    def test_a_palette_with_no_variant_measures_the_accents_as_they_are(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out = self.run_command(folder, "--palette", "red-green")
        self.assertEqual(code, 0)
        self.assertIn("| Role | One | Two |", out)

    def test_the_palette_is_in_the_json(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "result.json"
            self.run_command(folder, "--palette", "blue-yellow", "--json", str(target))
            written = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual(written["meta"]["palette"], "blue-yellow")
        self.assertIn("palettes", written)
        self.assertEqual(written["candidates"]["one"]["palette"], "blue-yellow")

    def test_a_palette_that_is_not_colour_blind_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            err = io.StringIO()
            with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as stop:
                self.run_command(folder, "--palette", "default")
        self.assertEqual(stop.exception.code, 2)
        self.assertIn("default", err.getvalue())

    def test_a_variant_file_that_is_wrong_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            frames = Path(folder) / "frames"
            (frames / "Starfield-tv").mkdir(parents=True)
            Image.new("RGB", (192, 108), (40, 60, 90)).save(frames / "Starfield-tv" / "00.png")
            variants = {"blue-yellow": {"nobody": candidate()}}
            accents = write_accents_with_variants(folder, variants, one=candidate())
            err = io.StringIO()
            with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as stop:
                contrast.main(["accents", str(frames), "--accents", str(accents)])
        self.assertEqual(stop.exception.code, 2)
        self.assertIn("nobody", err.getvalue())


# The variants that design/accessibility/accents.json proposes, by (accent, palette): the accent and
# the soft accent, and the same two of high contrast.
PROPOSED_VARIANTS = {
    ("violet", "red-green"): {
        "accent": "#F4BCBC",
        "accent-soft": "#FCC3C8",
        "high-contrast": {"accent": "#F4BCBC", "accent-soft": "#FCC3C8"},
    },
    ("violet", "blue-yellow"): {
        "accent": "#C688FE",
        "accent-soft": "#C8BBFE",
        "high-contrast": {"accent": "#C7B9FC", "accent-soft": "#CAC4FE"},
    },
    ("white", "blue-yellow"): {
        "accent": "#EFF3FF",
        "accent-soft": "#F3FCFF",
        "high-contrast": {"accent": "#EFF3FF", "accent-soft": "#F3FCFF"},
    },
}


class ShippedPaletteVariantsTest(unittest.TestCase):
    """The variants of the accents under the colour-blind palettes (ADR 0057 and 0058)."""

    @classmethod
    def setUpClass(cls):
        cls.accents = contrast.load_accents(contrast.ACCENTS_JSON)
        cls.variants = contrast.load_palette_variants(contrast.ACCENTS_JSON)
        cls.context = contrast.accent_context(TOKENS)
        cls.report = contrast.palette_report(cls.accents, cls.variants, cls.context)

    def found(self, name, palette):
        return self.variants[palette][name]

    def test_there_is_a_variant_for_each_pair_that_falls_short_and_for_no_other(self):
        short = {
            (name, palette)
            for name in self.accents
            for palette in contrast.PALETTES
            if any(f.startswith(f"palette:{palette}:") for f in self.report["fails"][name])
        }
        proposed = {(name, palette) for palette, found in self.variants.items() for name in found}
        self.assertEqual(proposed, set(PROPOSED_VARIANTS))
        self.assertEqual(short, set(PROPOSED_VARIANTS))

    def test_the_pairs_that_fall_short_are_those_of_the_measurement(self):
        fails = self.report["fails"]
        violet = set(fails["violet"])
        # Violet is the blue of the success colour to a protanope and a deuteranope, whatever it is
        # drawn as, and its high contrast colours are no better.
        for vision in ("protanopia", "deuteranopia"):
            for role in ("accent", "accent-soft", "hc:accent", "hc:accent-soft", "hc:accent-line"):
                self.assertIn(f"palette:red-green:{vision}:{role}", violet)
        for role in ("accent", "hc:accent", "hc:accent-soft", "hc:accent-line"):
            self.assertIn(f"palette:blue-yellow:tritanopia:{role}", violet)
        self.assertNotIn("palette:blue-yellow:tritanopia:accent-soft", violet)
        # Teal and green keep the 20 under both palettes. White falls short with its soft accent
        # alone, which a tritanope sees as the pale pink of the warning yellow.
        self.assertEqual(fails["teal"], [])
        self.assertEqual(fails["green"], [])
        self.assertEqual(
            sorted(fails["white"]),
            [
                "palette:blue-yellow:tritanopia:accent-soft",
                "palette:blue-yellow:tritanopia:hc:accent-soft",
            ],
        )

    def test_a_variant_is_what_it_is_proposed_to_be(self):
        for (name, palette), expected in PROPOSED_VARIANTS.items():
            with self.subTest(accent=name, palette=palette):
                variant = self.found(name, palette)
                self.assertEqual(variant["color"]["accent"], expected["accent"])
                self.assertEqual(variant["color"]["accent-soft"], expected["accent-soft"])
                for role, value in expected["high-contrast"].items():
                    self.assertEqual(variant["high-contrast"][role], value)

    def test_every_variant_keeps_the_20_under_its_palette_with_something_to_spare(self):
        for name, palette in PROPOSED_VARIANTS:
            key = f"{name}@{palette}"
            with self.subTest(variant=key):
                self.assertEqual(self.report["fails"][key], [])
                held = [c for c in self.report["checks"][key][palette] if c["held"]]
                self.assertTrue(held)
                for check in held:
                    self.assertGreaterEqual(check["least"], 20.4, check["id"])

    def test_every_variant_is_measured_for_the_visions_of_its_palette_and_no_other(self):
        for name, palette in PROPOSED_VARIANTS:
            with self.subTest(accent=name, palette=palette):
                checks = self.report["checks"][f"{name}@{palette}"]
                self.assertEqual(list(checks), [palette])
                visions = {c["vision"] for c in checks[palette]}
                self.assertEqual(visions, set(contrast.PALETTE_VISIONS[palette]))

    def test_a_variant_fails_no_check_that_its_accent_passes(self):
        # The opaque fills and high contrast, which need no frame, and the glass that the app
        # measured (`GlassContrastTable.kt`). The selected label over its tint needs frames: the
        # `accents --palette` command measures it, and the notes have the figures.
        rows = backdrop_rows_of_the_table()
        for name, palette in PROPOSED_VARIANTS:
            with self.subTest(accent=name, palette=palette):
                base = contrast.accent_report({name: self.accents[name]}, self.context, rows)
                found = self.found(name, palette)
                under = contrast.accent_report({name: found}, self.context, rows, palette=palette)
                new = [
                    f
                    for f in under[name]["fails"]
                    if not f.startswith("palette:") and f not in base[name]["fails"]
                ]
                self.assertEqual(new, [])

    def test_a_variant_fails_only_the_checks_that_it_names(self):
        for name, palette in PROPOSED_VARIANTS:
            found = self.found(name, palette)
            under = contrast.accent_report({name: found}, self.context, [], palette=palette)[name]
            with self.subTest(accent=name, palette=palette):
                self.assertEqual(sorted(under["fails"]), sorted(found["known_fails"]))

    def test_the_violet_of_the_blue_yellow_palette_is_the_violet_a_little_pinker(self):
        accent = self.found("violet", "blue-yellow")["color"]["accent"]
        today = self.accents["violet"]["color"]["accent"]
        self.assertLess(contrast.colour_distance(accent, today), 8.0)

        def hue(value):
            _, a, b = contrast.srgb_to_lab(contrast.parse_hex(value))
            return math.degrees(math.atan2(b, a)) % 360

        self.assertLess(abs(hue(accent) - hue(today)), 20.0)

    def test_the_variant_of_white_changes_the_soft_accent_alone(self):
        variant, today = self.found("white", "blue-yellow"), self.accents["white"]
        for role in contrast.ACCENT_ROLES:
            if role != "accent-soft":
                self.assertEqual(variant["color"][role], today["color"][role], role)
        self.assertNotEqual(variant["color"]["accent-soft"], today["color"]["accent-soft"])
        self.assertEqual(variant["known_fails"].keys(), today["known_fails"].keys())

    def test_the_variant_of_violet_for_the_red_green_palette_is_not_a_violet_and_says_so(self):
        found = self.found("violet", "red-green")
        self.assertIn("no violet", found["source"].lower())

    def test_every_role_of_a_variant_has_a_note_on_how_its_value_was_derived(self):
        for palette, found in self.variants.items():
            for name, variant in found.items():
                high = (f"high-contrast.{role}" for role in variant["high-contrast"])
                for role in (*variant["color"], *high):
                    with self.subTest(accent=name, palette=palette, role=role):
                        self.assertGreater(len(variant["notes"][role]), 20)


if __name__ == "__main__":
    unittest.main()
