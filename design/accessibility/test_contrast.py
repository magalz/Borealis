"""Tests of the contrast tool: the WCAG maths, the blur and glass model, the colour-vision
simulation and the measurement of a folder of frames. Run from the repository root:

    python -m unittest discover -s design/accessibility
"""

import json
import re
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

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


class ColourVisionTest(unittest.TestCase):
    def test_the_three_kinds_are_the_ones_of_machado(self):
        self.assertEqual(
            set(contrast.CVD_MATRICES), {"protanopia", "deuteranopia", "tritanopia"}
        )

    def test_white_stays_white_and_black_stays_black(self):
        for kind in contrast.CVD_MATRICES:
            with self.subTest(kind=kind):
                np.testing.assert_allclose(
                    contrast.simulate_cvd(np.array([1.0, 1.0, 1.0]), kind), 1.0, atol=1e-5
                )
                np.testing.assert_allclose(
                    contrast.simulate_cvd(np.array([0.0, 0.0, 0.0]), kind), 0.0, atol=1e-9
                )

    def test_a_grey_stays_grey(self):
        for kind in contrast.CVD_MATRICES:
            with self.subTest(kind=kind):
                out = contrast.simulate_cvd(np.array([0.5, 0.5, 0.5]), kind)
                np.testing.assert_allclose(out, 0.5, atol=1e-4)

    def test_every_row_of_a_matrix_adds_up_to_one(self):
        for kind, matrix in contrast.CVD_MATRICES.items():
            with self.subTest(kind=kind):
                np.testing.assert_allclose(np.asarray(matrix).sum(axis=1), 1.0, atol=2e-6)

    def test_red_under_protanopia_is_the_first_column_of_the_matrix(self):
        # Linear (1, 0, 0) -> (0.152286, 0.114503, -0.003882), the last clipped to 0, then encoded.
        out = contrast.simulate_cvd(np.array([1.0, 0.0, 0.0]), "protanopia")
        np.testing.assert_allclose(out, [0.42661, 0.37265, 0.0], atol=1e-4)

    def test_an_image_is_simulated_pixel_by_pixel(self):
        image = np.zeros((3, 4, 3))
        image[1, 2] = (1.0, 0.0, 0.0)
        out = contrast.simulate_cvd(image, "deuteranopia")
        self.assertEqual(out.shape, image.shape)
        np.testing.assert_allclose(out[1, 2], contrast.simulate_cvd(image[1, 2], "deuteranopia"))

    def test_the_simulated_colour_is_given_back_as_hex(self):
        self.assertEqual(contrast.simulate_hex("#FFFFFF", "tritanopia"), "#FFFFFF")
        self.assertEqual(contrast.simulate_hex("#000000", "protanopia"), "#000000")


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
            self.assertEqual(contrast.main(["glass-table", str(source), "--out", str(out)]), 0)
            self.assertEqual(out.read_bytes(), contrast.glass_table_kotlin(result).encode("utf-8"))

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


if __name__ == "__main__":
    unittest.main()
