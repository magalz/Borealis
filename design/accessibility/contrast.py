"""Stubs: every function returns 0 until the implementation lands."""

import numpy as np

TEXT_TOKENS = ("ink", "ink-2", "ink-3", "ink-4", "accent-soft")
SURFACE_TOKENS = ("page", "surface", "surface-raised")
CVD_MATRICES = {"protanopia": np.zeros((3, 3)), "deuteranopia": np.zeros((3, 3))}


def parse_hex(value):
    return np.zeros(3)


def load_colors(path, names):
    return {name: "#000000" for name in names}


def srgb_to_linear(c):
    return 0


def linear_to_srgb(c):
    return 0


def relative_luminance(rgb):
    return np.zeros(np.shape(rgb)[:-1])


def contrast_ratio(l1, l2):
    return 0


def hex_contrast(foreground, background):
    return 0


def contrast_stats(luminance, text_luminance):
    return {"min": 0, "p1": 0, "p5": 0}


def frame_density(width_px, device):
    return 0


def blur_sigma(radius_px):
    return 0


def gaussian_blur(image, sigma):
    return np.zeros_like(image)


def composite(image, tint, alpha):
    return np.zeros_like(image)


def glass(image, tint, alpha, radius_px):
    return np.zeros_like(image)


def simulate_cvd(rgb, kind):
    return np.zeros_like(rgb)


def simulate_hex(value, kind):
    return "#000000"


def frame_indices(count, n):
    return []


def select_filter(indices):
    return ""


def measure_surfaces(colors):
    return []


def measure_source(frames, style, device, colors):
    return {}


def measure_sources(sources, colors):
    return {}


def measure_folder(root, colors):
    return {}


def markdown_tables(result):
    return ""
