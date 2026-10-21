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
            ("label", "tv"): 12 * 2.0,
            ("label", "phone"): 11 * 3.0,
            ("body", "tv"): 15 * 2.0,
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
        # 12 sp are 24 px on a TV, and capitals are wider than the lower case of the same size.
        self.assertGreater(spaced.shape[1], contrast.glyph_mask("small", "tv").shape[1] * 0.8)
        self.assertGreater(spaced.shape[1], 24 * 0.55 * len(contrast.HALO_SAMPLE))

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


if __name__ == "__main__":
    unittest.main()
