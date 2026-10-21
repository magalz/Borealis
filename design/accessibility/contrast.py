"""Text contrast over the animated backgrounds, over the glass panels on them, and under a halo.

Sprint 10 bases its choices on these numbers: the glass look of a panel and the text colours on it,
and the halo of the text and the icons that stand straight on the background. The tool measures WCAG
2.x contrast ratios as the worst case (the minimum) of each text token over real frames of each
background, in four cases:

- (a) the text straight on the background;
- (b) the text on one of today's opaque surfaces (`page`, `surface`, `surface-raised`);
- (c) the text on a glass panel: the background blurred, then the `surface` colour on top at an
  alpha, for a grid of alphas and blur radii (a radius of 0 is no blur, the glass of API 28 to 30);
- (d) the text and the icons straight on the background, with a halo: the glyphs widened by a
  stroke, blurred and laid in `page` at an opacity, for a grid of strokes, blurs and opacities.

The colours and the sizes of the text come from `design/tokens.json`, or from the file that
`--tokens` names. The frames come from two places: `extract` writes them from the four clips with
ffmpeg, and the `BackgroundFrameExport` test of `:borealis:designsystem` writes the two drawn
styles. `README.md` has the whole procedure.

    python -I contrast.py extract <frames> [--clips <dir>] [--count N]
    python -I contrast.py measure <frames> --json <result.json> [--tokens <tokens.json>] [--jobs N]
    python -I contrast.py glass-table <result.json> [--out <GlassContrastTable.kt>]
    python -I contrast.py glass-colours [--table <GlassContrastTable.kt>]
    python -I contrast.py halo <frames> --json <halo.json> [--tokens <tokens.json>] [--jobs N]
        [--texts <style> ...] [--icons] [--high-contrast] [--halo <stroke dp> <blur dp> <opacity %>]
        [--font <sans regular> --font-semibold <sans semibold> | --fonts <font.json>]

`measure` is cases (a) to (c) and `halo` is case (d), in IBM Plex Sans unless a font is named: two
files, the regular and the semibold face (a variable font is set to 400 and 600), or a JSON manifest
of them. The text is drawn at the size of the tokens times the scale that sets the x-height of the
font to that of Plex. `glass-table` writes the table of the worst
backdrop behind a glass panel that the app turns into the tint floors and the text colours of the
glass, with the SHA-256 of the clips it was measured over, so that a test of the app tells a table
that the clips have outgrown. `glass-colours` prints those floors and colours, worked out from the
table the app has, for `docs/ux/accessibility.md`.

The library has no state; the tests are in `test_contrast.py`.
"""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
import datetime
import functools
import hashlib
import json
import math
import re
import shutil
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
TOKENS_JSON = REPO / "design" / "tokens.json"
CLIPS_DIR = REPO / "apps/android/borealis/designsystem/src/main/res/raw"
GLASS_TABLE_KT = (
    REPO
    / "apps/android/borealis/designsystem/src/main/kotlin/io/github/magalz/borealis/designsystem"
    / "glass/GlassContrastTable.kt"
)

# The text tokens that are measured, and the surfaces they sit on today.
TEXT_TOKENS = ("ink", "ink-2", "ink-3", "ink-4", "accent-soft")
SURFACE_TOKENS = ("page", "surface", "surface-raised")

# The glass candidates: the tint, and the grid of its alpha and of the blur radius in dp. They are
# the tints and the blurs that Settings offers. A blur of 0 is no blur: the glass of API 28 to 30,
# which cannot blur, draws the tint over the frame as it is.
GLASS_TINT = "surface"
GLASS_ALPHAS = (0.30, 0.40, 0.50, 0.60, 0.70, 0.85)
GLASS_BLURS_DP = (0, 8, 16, 24, 32, 48)

# How many frames of each loop, and the width in dp of the device a frame stands for. The density
# of a frame is its width in pixels over that: 1920 px of a TV is xhdpi (2.0), 1233 px of a phone
# is xxhdpi (3.0).
FRAME_COUNT = 24
DEVICE_WIDTH_DP = {"tv": 960.0, "phone": 411.0}
DEVICE_LABELS = {"tv": "TV", "phone": "phone"}
# The size in pixels of a screen of each device, as the BackgroundFrameExport test has them.
DEVICE_SIZE_PX = {"tv": (1920, 1080), "phone": (1233, 2673)}

# The working names of the styles (design/backgrounds/README.md), by the name of the enum.
STYLE_LABELS = {
    "AuroraCurtains": "Aurora curtains",
    "SoftGlow": "Soft glow",
    "NorthernLights": "Northern lights",
    "LightMist": "Light mist",
    "FlowingLight": "Flowing light",
    "Starfield": "Starfield",
}
# The clip of a style is `background_<name>.mp4`.
CLIP_STYLES = {
    "northern_lights": "NorthernLights",
    "light_mist": "LightMist",
    "flowing_light": "FlowingLight",
    "starfield": "Starfield",
}

# WCAG 2.x, relative luminance of an sRGB colour.
LUMINANCE_WEIGHTS = (0.2126, 0.7152, 0.0722)
SRGB_THRESHOLD = 0.04045
NEAR_BLACK = 0.05

# The contrast ratios WCAG 2.x asks for: large text and graphics, text, and text at level AAA.
WCAG_LIMITS = (3.0, 4.5, 7.0)

# How far the blur reaches, in sigmas. The padding of the frame is that wide, so the circular
# convolution of the FFT never wraps anything in: exp(-25/2) is 4e-6.
KERNEL_SIGMAS = 5


# Colours and the WCAG maths


def parse_hex(value):
    """An opaque `#RRGGBB` colour as three floats of an sRGB colour, from 0 to 1."""
    text = value.lstrip("#")
    if len(text) != 6:
        raise ValueError(f"{value!r} is not an opaque #RRGGBB colour")
    return np.array([int(text[i : i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255


def load_colors(path, names):
    """The hex values of the colour tokens `names` in a design token file."""
    colors = json.loads(Path(path).read_text(encoding="utf-8"))["color"]
    missing = [name for name in names if name not in colors]
    if missing:
        raise ValueError(f"{path} has no colour token {', '.join(missing)}")
    return {name: colors[name]["$value"] for name in names}


def _floats(values):
    values = np.asarray(values)
    return values if np.issubdtype(values.dtype, np.floating) else values.astype(np.float64)


def srgb_to_linear(c):
    """The sRGB transfer function, undone: the curve is linear up to the threshold of 0.04045."""
    c = _floats(c)
    power = ((np.maximum(c, SRGB_THRESHOLD) + 0.055) / 1.055) ** 2.4
    return np.where(c <= SRGB_THRESHOLD, c / 12.92, power)


def relative_luminance(rgb):
    """The relative luminance of sRGB colours, 0 to 1 each, along the last axis."""
    linear = srgb_to_linear(rgb)
    return linear @ np.asarray(LUMINANCE_WEIGHTS, dtype=linear.dtype)


def contrast_ratio(l1, l2):
    """The WCAG contrast ratio of two luminances, the same whichever is the lighter."""
    return (np.maximum(l1, l2) + NEAR_BLACK) / (np.minimum(l1, l2) + NEAR_BLACK)


def hex_contrast(foreground, background):
    return float(
        contrast_ratio(
            relative_luminance(parse_hex(foreground)), relative_luminance(parse_hex(background))
        )
    )


def format_ratio(value):
    """A ratio to two places, or to three, cut off, when it is short of a WCAG limit.

    3:1, 4.5:1 and 7:1 are the limits. A ratio of 4.4962 is not 4.5:1 and must not read as it, which
    two places would make of it.
    """
    text = f"{value:.2f}"
    if any(value < limit <= float(text) for limit in WCAG_LIMITS):
        return f"{math.floor(value * 1000) / 1000:.3f}"
    return text


def contrast_stats(luminance, text_luminance):
    """The contrast of a text over every pixel of a frame: the worst case, p1 and p5.

    `luminance` is the relative luminance of the pixels. The worst case is the lowest ratio; p1
    and p5 are the ratios below which 1% and 5% of the pixels are, for information.
    """
    ratios = contrast_ratio(np.asarray(luminance).ravel(), text_luminance)
    p1, p5 = np.percentile(ratios, [1, 5])
    return {"min": float(ratios.min()), "p1": float(p1), "p5": float(p5)}


# The blur and the glass


def frame_density(width_px, device):
    """The density of a frame of `device`, from the width of the frame in pixels."""
    return width_px / DEVICE_WIDTH_DP[device]


def blur_sigma(radius_px):
    """The sigma of a Gaussian that stands for a blur of `radius_px` pixels.

    This is Skia's conversion from a blur radius to a sigma (`SkBlurMask::ConvertRadiusToSigma`),
    which Android's `RenderEffect.createBlurEffect` and `Modifier.blur` use for the radius they are
    given.
    """
    return 0.57735 * radius_px + 0.5


def _fast_length(n):
    """The least number from `n` up of the form 2^a 3^b 5^c, for which an FFT is fast."""
    best = 1 << (n - 1).bit_length()
    power5 = 1
    while power5 < best:
        power35 = power5
        while power35 < best:
            length = power35
            while length < n:
                length *= 2
            best = min(best, length)
            power35 *= 3
        power5 *= 5
    return best


def gaussian_blurs(image, sigmas):
    """`image` (height, width, channels) blurred by a Gaussian of each of `sigmas`, in pixels.

    The pixels are blurred as the sRGB values they hold, as the platform does, and the edge of the
    frame repeats (clamp), so it does not darken. The blur is done in the frequency domain, one
    forward transform for all the sigmas.
    """
    image = np.asarray(image, dtype=np.float32)
    height, width, _ = image.shape
    pad = math.ceil(KERNEL_SIGMAS * max(sigmas))
    padded = np.pad(image, ((pad, pad), (pad, pad), (0, 0)), mode="edge")
    channels = np.ascontiguousarray(np.moveaxis(padded, 2, 0))
    shape = (_fast_length(height + 2 * pad), _fast_length(width + 2 * pad))
    spectrum = np.fft.rfft2(channels, s=shape)
    squared = np.fft.fftfreq(shape[0])[:, None] ** 2 + np.fft.rfftfreq(shape[1])[None, :] ** 2
    blurred = []
    for sigma in sigmas:
        gain = np.exp(-2 * math.pi**2 * sigma**2 * squared).astype(np.float32)
        full = np.fft.irfft2(spectrum * gain, s=shape)
        blurred.append(np.moveaxis(full[:, pad : pad + height, pad : pad + width], 0, 2))
    return blurred


def gaussian_blur(image, sigma):
    return gaussian_blurs(image, [sigma])[0]


def composite(image, tint, alpha):
    """`tint` over `image` at `alpha`, mixed in the sRGB values as the platform composites.

    `alpha` is one number, or an array with an alpha for each pixel and a last axis of 1.
    """
    return alpha * np.asarray(tint, dtype=np.float32) + (1 - alpha) * np.asarray(
        image, dtype=np.float32
    )


def glass(image, tint, alpha, radius_px):
    """A glass panel over `image`: the image blurred by `radius_px`, the tint on it at `alpha`.

    This is a model oracle for the tests. The measurement does not call it: it blurs a frame once
    for all the alphas of a radius (`_measure_frame`), and the tests check that against this.
    """
    return composite(gaussian_blur(image, blur_sigma(radius_px)), tint, alpha)


# Frames


def frame_indices(count, n):
    """The numbers of `n` frames spread evenly over a clip of `count` frames, from the first."""
    return [i * count // n for i in range(n)]


def cover_geometry(size, source):
    """How the player fits a clip to a screen: the same as `VideoBackground` does.

    The clip is scaled to the smallest rectangle of its aspect that covers the screen, and the
    middle of that is shown. `size` and `source` are (width, height) in pixels. It returns the
    width and height of the scaled clip, and the x and y of the corner of the screen in it.
    """
    (width, height), (clip_width, clip_height) = size, source
    if width * clip_height >= height * clip_width:
        cover = (width, round(width * clip_height / clip_width))
    else:
        cover = (round(height * clip_width / clip_height), height)
    return cover[0], cover[1], (cover[0] - width) // 2, (cover[1] - height) // 2


def select_filter(indices):
    """The ffmpeg filter that keeps only the frames with these numbers."""
    return "select=" + "+".join(f"eq(n\\,{index})" for index in indices)


def load_frame(path):
    """A PNG as sRGB floats, 0 to 1, height by width by three."""
    with Image.open(path) as image:
        return np.asarray(image.convert("RGB"), dtype=np.float32) / 255


def _tool(name):
    path = shutil.which(name)
    if path is None:
        raise SystemExit(f"{name} is not on PATH")
    return path


def clip_filter(indices, size, source):
    """The ffmpeg filter that makes the frames of a clip as a screen of `size` shows them.

    It keeps the frames with these numbers, scales the clip the way the player does (bilinear, as
    the compositor does), turns it into RGB and cuts the screen out of it. The clips carry no
    colour tags. Android decodes an untagged HD stream as BT.709 in limited range, so ffmpeg is
    told to, or it would take BT.601 and shift the colours.
    """
    width, height, x, y = cover_geometry(size, source)
    return (
        select_filter(indices)
        + f",scale={width}:{height}:in_color_matrix=bt709:in_range=tv:out_range=pc:flags=bilinear"
        + f",format=rgb24,crop={size[0]}:{size[1]}:{x}:{y}"
    )


def extract_clips(frames, clips, count=FRAME_COUNT, log=None):
    """Writes `count` frames of each clip, spread over its loop, to `frames/<Style>-<device>/`.

    A TV shows the whole clip. A phone upright shows a slice of the middle of it, scaled up
    about 2.5 times, so a blur of a given size in dp reaches less far into the clip there. It tells
    `log` (the standard error unless given another) what it wrote.
    """
    ffmpeg, ffprobe = _tool("ffmpeg"), _tool("ffprobe")
    for clip in sorted(Path(clips).glob("background_*.mp4")):
        style = CLIP_STYLES.get(clip.stem.removeprefix("background_"))
        if style is None:
            continue
        shown = subprocess.run(
            [ffprobe, "-v", "error", "-select_streams", "v:0", "-count_frames"]
            + ["-show_entries", "stream=width,height,nb_read_frames", "-of", "csv=p=0", str(clip)],
            capture_output=True,
            text=True,
            check=True,
        )
        clip_width, clip_height, clip_frames = (int(v) for v in shown.stdout.strip().split(","))
        for device, size in DEVICE_SIZE_PX.items():
            out = Path(frames) / f"{style}-{device}"
            out.mkdir(parents=True, exist_ok=True)
            for old in out.glob("*.png"):
                old.unlink()
            filters = clip_filter(
                frame_indices(clip_frames, count), size, (clip_width, clip_height)
            )
            subprocess.run(
                [ffmpeg, "-v", "error", "-y", "-i", str(clip), "-vf", filters]
                + ["-fps_mode", "passthrough", "-start_number", "0", str(out / "%02d.png")],
                check=True,
            )
            _say(log, f"{clip.name}: {len(list(out.glob('*.png')))} frames in {out}")


# Measurement


def measure_surfaces(colors):
    """Case (b): the ratio of each text token on each opaque surface. It needs no frame."""
    return [
        {
            "surface": surface,
            "token": token,
            "ratio": hex_contrast(colors[token], colors[surface]),
        }
        for surface in SURFACE_TOKENS
        for token in TEXT_TOKENS
    ]


def _map_frames(function, frames, jobs, *shared):
    """`function(frame, *shared)` for each of `frames`, in order, in `jobs` processes if above 1."""
    if jobs > 1:
        with concurrent.futures.ProcessPoolExecutor(max_workers=jobs) as pool:
            return list(pool.map(function, frames, *([value] * len(frames) for value in shared)))
    return [function(frame, *shared) for frame in frames]


def _token_stats(luminance, text):
    return {token: contrast_stats(luminance, value) for token, value in text.items()}


def _measure_frame(frame, device, colors):
    """The stats of one frame, for case (a) and every glass candidate, for every text token."""
    if not isinstance(frame, np.ndarray):
        frame = load_frame(frame)
    text = {token: float(relative_luminance(parse_hex(colors[token]))) for token in TEXT_TOKENS}
    tint = parse_hex(colors[GLASS_TINT])
    density = frame_density(frame.shape[1], device)
    blurring = [dp for dp in GLASS_BLURS_DP if dp > 0]
    sigmas = [blur_sigma(dp * density) for dp in blurring]
    images = dict(zip(blurring, gaussian_blurs(frame, sigmas)))
    images[0] = frame
    panels = {}
    for dp in GLASS_BLURS_DP:
        image = images[dp]
        for alpha in GLASS_ALPHAS:
            luminance = relative_luminance(composite(image, tint, alpha))
            panels[(alpha, dp)] = _token_stats(luminance, text)
    return {"background": _token_stats(relative_luminance(frame), text), "glass": panels}


def _fold(per_frame):
    """The stats of the frames of one case and token, as one row: the worst of them all."""
    mins = [stats["min"] for stats in per_frame]
    return {
        "min": min(mins),
        "p1": min(stats["p1"] for stats in per_frame),
        "p5": min(stats["p5"] for stats in per_frame),
        "worst_frame": int(np.argmin(mins)),
        "per_frame_min": mins,
    }


def measure_source(frames, style, device, colors, jobs=1):
    """Cases (a) and (c) of one style on one device, over its frames.

    `frames` are arrays or paths of PNGs. The result is plain data: rows of "background" and of
    "glass", each with the worst case ("min") over the frames of a token, p1 and p5 (the lowest
    over the frames), the number of the worst frame and the worst case of each frame.
    """
    measured = _map_frames(_measure_frame, frames, jobs, device, colors)
    where = {"style": style, "device": device}
    background = [
        {**where, "token": token, **_fold([m["background"][token] for m in measured])}
        for token in TEXT_TOKENS
    ]
    panels = [
        {
            **where,
            "alpha": alpha,
            "blur_dp": dp,
            "token": token,
            **_fold([m["glass"][(alpha, dp)][token] for m in measured]),
        }
        for alpha in GLASS_ALPHAS
        for dp in GLASS_BLURS_DP
        for token in TEXT_TOKENS
    ]
    return {"background": background, "glass": panels}


def measure_sources(sources, colors, jobs=1):
    """Every case over `sources`, which maps (style, device) to its frames."""
    result = {
        "meta": {
            "statistic": (
                "WCAG 2.x contrast ratio of each text token over every pixel of a frame; the "
                "worst case is the lowest ratio over the frame, and then over the frames."
            ),
            "blur_model": (
                "Gaussian, sigma = 0.57735 * radius_px + 0.5 (Skia); blurred and composited in "
                "sRGB values; the edge of the frame repeats. A radius of 0 dp is no blur."
            ),
            "colors": colors,
            "text_tokens": list(TEXT_TOKENS),
            "surfaces": list(SURFACE_TOKENS),
            "glass_tint": GLASS_TINT,
            "glass_alphas": list(GLASS_ALPHAS),
            "glass_blurs_dp": list(GLASS_BLURS_DP),
        },
        "sources": [],
        "background": [],
        "glass": [],
        "surfaces": measure_surfaces(colors),
    }
    for (style, device), frames in sorted(sources.items()):
        part = measure_source(frames, style, device, colors, jobs)
        result["sources"].append({"style": style, "device": device, "frames": len(frames)})
        result["background"] += part["background"]
        result["glass"] += part["glass"]
    return result


def _folder_sources(root):
    """The frames in `root/<Style>-<device>/*.png`, by style and device; other folders are left."""
    sources = {}
    for folder in sorted(Path(root).iterdir()):
        style, _, device = folder.name.rpartition("-")
        frames = sorted(folder.glob("*.png")) if folder.is_dir() else []
        if style and device in DEVICE_WIDTH_DP and frames:
            sources[(style, device)] = frames
    if not sources:
        raise SystemExit(f"no <Style>-tv or <Style>-phone folder of PNG frames in {root}")
    return sources


def measure_folder(root, colors, jobs=1):
    """Measures the frames in `root/<Style>-<device>/*.png`; other folders are left alone."""
    return measure_sources(_folder_sources(root), colors, jobs)


# The tables


def _label(style, device):
    return f"{STYLE_LABELS.get(style, style)}, {DEVICE_LABELS[device]}"


def _table(header, rows):
    lines = ["| " + " | ".join(header) + " |", "|" + "|".join(" --- " for _ in header) + "|"]
    return "\n".join(lines + ["| " + " | ".join(row) + " |" for row in rows])


def markdown_tables(result):
    """Tables A, B and C of `docs/ux/accessibility.md`, from the result of a measurement."""
    tokens = result["meta"]["text_tokens"] if "meta" in result else list(TEXT_TOKENS)
    names = [f"`{token}`" for token in tokens]

    def style_table(stat):
        by_source = {}
        for row in result["background"]:
            by_source.setdefault((row["style"], row["device"]), {})[row["token"]] = row[stat]
        return _table(
            ["Style"] + names,
            [
                [_label(*source)] + [format_ratio(by_token[token]) for token in tokens]
                for source, by_token in by_source.items()
            ],
        )

    ratios = {(row["surface"], row["token"]): row["ratio"] for row in result["surfaces"]}
    surfaces = list(dict.fromkeys(row["surface"] for row in result["surfaces"]))
    rows = [
        [f"`{surface}`"] + [format_ratio(ratios[(surface, token)]) for token in tokens]
        for surface in surfaces
    ]
    worst_ratios = [min(ratios[(surface, token)] for surface in surfaces) for token in tokens]
    rows.append(["worst"] + [format_ratio(ratio) for ratio in worst_ratios])
    table_b = _table(["Surface"] + names, rows)

    def glass_table(devices):
        worst = {}
        for row in result["glass"]:
            if row["device"] not in devices:
                continue
            key = (row["alpha"], row["blur_dp"])
            cell = worst.setdefault(key, {}).get(row["token"])
            if cell is None or row["min"] < cell[0]:
                worst[key][row["token"]] = (row["min"], _label(row["style"], row["device"]))
        rows = []
        for (alpha, blur), by_token in worst.items():
            lowest = min(value for value, _ in by_token.values())
            rows.append(
                [f"{alpha:.2f}", "0 (no blur)" if blur == 0 else f"{blur}"]
                + [f"{format_ratio(by_token[token][0])} ({by_token[token][1]})" for token in tokens]
                + [format_ratio(lowest)]
            )
        return _table(["Alpha", "Blur dp"] + names + ["Lowest"], rows)

    present = list(dict.fromkeys(row["device"] for row in result["glass"]))
    per_device = []
    if len(present) > 1:
        for device in sorted(present, key=lambda name: name != "tv"):
            label = DEVICE_LABELS[device]
            per_device += [
                f"Table C, {label}. The same, for the {label} frames only.",
                glass_table({device}),
            ]

    return "\n\n".join(
        [
            "Table A. Text straight on the background: the worst case over the frames.",
            style_table("min"),
            "Table A, p5. The same, for information: the lowest over the frames of the ratio below "
            "which 5% of the pixels are.",
            style_table("p5"),
            "Table B. Text on the opaque surfaces: the contrast ratio.",
            table_b,
            "Table C. Text on a glass panel: the worst case over every style and frame, and the "
            "style it is in.",
            glass_table(set(present)),
        ]
        + per_device
    )


# The table for the app

# What a value of the table is rounded up to: five places of a luminance, 0 to 1.
TABLE_PLACES = 5

KOTLIN_PACKAGE = "io.github.magalz.borealis.designsystem.glass"


def worst_backdrop_luminance(result):
    """The brightest pixel behind a glass panel, as a luminance, per style, blur and alpha.

    It is the worst case over every frame of every device. It is read off the contrast of `ink`,
    the lightest text token, which is lowest where the backdrop is brightest:
    `Lb = (L(ink) + 0.05) / ratio - 0.05`. The value is rounded up, so the app never takes a
    backdrop for darker than it was measured. It returns `{style: {(blur_dp, alpha): luminance}}`.
    """
    ink = float(relative_luminance(parse_hex(result["meta"]["colors"]["ink"])))
    lowest = {}
    for row in result["glass"]:
        if row["token"] == "ink":
            cell = lowest.setdefault(row["style"], {})
            key = (row["blur_dp"], row["alpha"])
            cell[key] = min(cell.get(key, math.inf), row["min"])
    scale = 10**TABLE_PLACES
    return {
        style: {
            key: math.ceil(((ink + NEAR_BLACK) / ratio - NEAR_BLACK) * scale - 1e-6) / scale
            for key, ratio in sorted(cell.items())
        }
        for style, cell in sorted(lowest.items())
    }


# The Kotlin file, with the places that the table fills in marked by @NAME@.
KOTLIN_TEMPLATE = """\
// Generated by `python design/accessibility/contrast.py glass-table`. Do not edit by hand.
// Measured @SOURCE@; docs/ux/accessibility.md has the method.

package @PACKAGE@

import io.github.magalz.borealis.designsystem.background.BackgroundStyle

/**
 * The luminance of the brightest pixel behind the text of a glass panel, for each style, blur and
 * tint: the worst case over every frame of the TV and of the phone, rounded up. The floors and the
 * text colours of the glass are worked out from it in `GlassRules`.
 */
@Suppress("MagicNumber") // a table of measurements
internal object GlassContrastTable {
    /** The tints of the grid, in percent. */
    val tintsPercent = intArrayOf(@TINTS@)

    /** The blur radii of the grid, in dp. A radius of 0 is no blur. */
    val blursDp = intArrayOf(@BLURS@)

    /** For each style, a row for each of [blursDp] with a value for each of [tintsPercent]. */
    val backdropLuminance: Map<BackgroundStyle, Array<DoubleArray>> =
        mapOf(
@ENTRIES@
        )

    /**
     * The SHA-256 of each clip the table was measured over, by the name of its file in
     * `designsystem/src/main/res/raw` without the `.mp4`. A test compares them with the clips, so a
     * clip that is encoded again says that the table is stale. The two styles that are drawn have
     * no clip.
     */
    val clipSha256: Map<String, String> =
        mapOf(
@CLIPS@
        )
}
"""


def clip_hashes(clips=CLIPS_DIR):
    """The SHA-256 of each `background_*.mp4` of `clips`, by the name of its file without `.mp4`."""
    return {
        clip.stem: hashlib.sha256(clip.read_bytes()).hexdigest()
        for clip in sorted(Path(clips).glob("background_*.mp4"))
    }


def glass_table_kotlin(result, clips=CLIPS_DIR):
    """The Kotlin file `GlassContrastTable.kt` for a measurement: the table, row by row.

    A row of a style is one blur of the grid and holds a value for each tint of the grid. The
    floors and the text colours are worked out in Kotlin from it. The file also holds the hash of
    each clip in the folder `clips`, as they are when it is written.
    """
    table = worst_backdrop_luminance(result)
    alphas = sorted({alpha for cell in table.values() for _, alpha in cell})
    blurs = sorted({blur for cell in table.values() for blur, _ in cell})
    meta = result.get("meta", {})
    date = meta.get("date", "an unknown date")
    revision = meta.get("revision", "an unknown revision")
    entries = []
    for style, cell in table.items():
        rows = [
            "                    doubleArrayOf("
            + ", ".join(f"{cell[(blur, alpha)]:.{TABLE_PLACES}f}" for alpha in alphas)
            + "),"
            for blur in blurs
        ]
        entries.append(
            f"            BackgroundStyle.{style} to\n"
            + "                arrayOf(\n"
            + "\n".join(rows)
            + "\n                ),"
        )
    hashes = [
        f'            "{name}" to\n                "{digest}",'
        for name, digest in clip_hashes(clips).items()
    ]
    return (
        KOTLIN_TEMPLATE.replace("@SOURCE@", f"{date} at {revision}")
        .replace("@PACKAGE@", KOTLIN_PACKAGE)
        .replace("@TINTS@", ", ".join(str(round(alpha * 100)) for alpha in alphas))
        .replace("@BLURS@", ", ".join(str(blur) for blur in blurs))
        .replace("@ENTRIES@", "\n".join(entries))
        .replace("@CLIPS@", "\n".join(hashes))
    )


# The floors and the text colours of the glass, for the notes
#
# Kotlin works them out in `GlassRules.kt` and `GlassInk.kt`, and those tests are the ones that run
# in the app. This is the same rule over the same table, so that the notes print what the app does:
# the tests of both pin the same colours. Where a channel lands on half a level it rounds up, as
# Kotlin's `roundToInt` does and Python's `round` does not.

GLASS_TEXT_MARGIN = 1.02
GLASS_WALK_STEPS = 100
# The lightest ink-3 that a glass may need: what Flowing light needs at 40 percent and 8 dp.
GLASS_SECONDARY_BOUND = "#A8ACB1"
# The tint and blur that Settings starts at, and the glass of Reduced and of API 28 to 30.
GLASS_DEFAULT = (0.40, 8)
GLASS_REDUCED = (0.85, 48)
GLASS_NO_BLUR = (0.85, 0)

GLASS_UNCHANGED_TOKENS = ("ink", "ink-2", "accent-soft")


def round_half_up(value):
    """The nearest whole number, with a half rounded up: what Kotlin's `roundToInt` does."""
    return math.floor(value + 0.5)


def _kotlin_ints(text, name):
    """The numbers of `val <name> = intArrayOf(...)` in the Kotlin table."""
    found = re.search(name + r" = intArrayOf\(([^)]*)\)", text)
    return [int(value) for value in found[1].split(",")]


def read_glass_table_kotlin(text):
    """The table of `GlassContrastTable.kt`, as `worst_backdrop_luminance` gives it."""
    tints = _kotlin_ints(text, "tintsPercent")
    blurs = _kotlin_ints(text, "blursDp")
    table = {}
    for entry in re.finditer(r"BackgroundStyle\.(\w+) to\s+arrayOf\((.*?)\n\s*\),", text, re.S):
        rows = re.findall(r"doubleArrayOf\(([^)]*)\)", entry[2])
        table[entry[1]] = {
            (blur, tint / 100): float(value)
            for blur, row in zip(blurs, rows)
            for tint, value in zip(tints, row.split(","))
        }
    return table


def glass_text_target(backdrop):
    """The luminance of the text that keeps 4.5:1, with the margin, over a backdrop."""
    return WCAG_LIMITS[1] * GLASS_TEXT_MARGIN * (backdrop + NEAR_BLACK) - NEAR_BLACK


def _hex_luminance(value):
    return float(relative_luminance(parse_hex(value)))


def walk_colour(start, end, target):
    """The first colour from `start` towards `end`, a percent at a time, that reaches `target`.

    The luminance is checked on the step as it is worked out and the colour is rounded to 8 bits
    only then. A token that reaches the target already is returned as it is. Colours are `#RRGGBB`.
    """
    first = np.rint(parse_hex(start) * 255)
    last = np.rint(parse_hex(end) * 255)
    for step in range(GLASS_WALK_STEPS + 1):
        mixed = first + (last - first) * step / GLASS_WALK_STEPS
        if float(relative_luminance(mixed / 255)) >= target:
            if step == 0:
                return start.upper()
            return "#" + "".join(f"{round_half_up(channel):02X}" for channel in mixed)
    return end.upper()


def glass_floor(cell, blur, colors):
    """The lowest tint of the grid, in percent, that a style may have at a blur (`cell` is its row).

    `ink`, `ink-2` and `accent-soft` keep 4.5:1 as they are, and the adjusted `ink-3` is no lighter
    than `#A8ACB1`. It is 100 where no tint of the grid does.
    """
    bound = _hex_luminance(GLASS_SECONDARY_BOUND)
    for alpha in sorted({alpha for _, alpha in cell}):
        backdrop = cell[(blur, alpha)]
        keeps = all(
            (_hex_luminance(colors[token]) + NEAR_BLACK) / (backdrop + NEAR_BLACK) >= WCAG_LIMITS[1]
            for token in GLASS_UNCHANGED_TOKENS
        )
        ink3 = walk_colour(colors["ink-3"], colors["ink"], glass_text_target(backdrop))
        if keeps and _hex_luminance(ink3) <= bound:
            return round(alpha * 100)
    return 100


def _glass_ink(cell, colors, blur, alpha):
    """The glass colours of ink-3 and ink-4 over a style at a blur and a tint."""
    target = glass_text_target(cell[(blur, alpha)])
    walked = [walk_colour(colors[token], colors["ink"], target) for token in ("ink-3", "ink-4")]
    return [f"`{colour}`" for colour in walked]


def glass_colour_tables(table):
    """Three tables for the notes: the floors, the colours at the default, Reduced and no blur."""
    colors = load_colors(TOKENS_JSON, TEXT_TOKENS)
    styles = [style for style in STYLE_LABELS if style in table]
    blurs = sorted({blur for cell in table.values() for blur, _ in cell if blur > 0})
    floors = _table(
        ["Style"] + [f"{blur} dp" for blur in blurs],
        [
            [STYLE_LABELS[style]]
            + [f"{glass_floor(table[style], blur, colors)} %" for blur in blurs]
            for style in styles
        ],
    )
    tint, blur = GLASS_DEFAULT
    default = []
    for style in styles:
        alpha = max(tint, glass_floor(table[style], blur, colors) / 100)
        default.append(
            [STYLE_LABELS[style], f"{round(alpha * 100)} %"]
            + _glass_ink(table[style], colors, blur, alpha)
        )
    reduced = [
        [STYLE_LABELS[style]]
        + _glass_ink(table[style], colors, GLASS_REDUCED[1], GLASS_REDUCED[0])
        + _glass_ink(table[style], colors, GLASS_NO_BLUR[1], GLASS_NO_BLUR[0])
        for style in styles
    ]
    heads = ["Style", "Reduced ink-3", "Reduced ink-4", "No blur ink-3", "No blur ink-4"]
    return "\n\n".join(
        [floors, _table(["Style", "Tint", "ink-3", "ink-4"], default), _table(heads, reduced)]
    )


# The halo of text straight on the background
#
# Text that sits on the animated background, outside any glass, gets a soft dark halo (issue #33).
# The model is how the app draws it: the glyphs are widened by a stroke, blurred, laid in `page` at
# an opacity over the frame, and the text is drawn on top. The contrast measured is that of the text
# colour against what is around the strokes, over every frame and a grid of places on the frame.

HALO_TINT = "page"
# Ink is the text, and ink-2 is what the secondary text becomes where there is a halo.
HALO_TOKENS = ("ink", "ink-2")
HALO_STROKES_DP = (0, 1, 2, 3)
HALO_BLURS_DP = (2, 4, 8)
HALO_OPACITIES = (0.6, 0.8, 1.0)
# No halo at all, which is Table A's case, measured around the strokes only.
HALO_NONE = (0, 0, 0.0)
# The pixels that count are those not in a glyph and within this many pixels of one.
HALO_RING_PX = 2
# p1 and p5 are taken with the sample text in this many columns and rows of places over the frame.
# The worst case does not depend on them: it is over every place.
HALO_PLACEMENTS = (6, 4)
HALO_SAMPLE = "Living room PC, 4 apps"
# A ratio must clear its limit by this much, for the difference between the model and a device.
HALO_MARGIN = 1.02
# How far the blur of the halo is followed, in sigmas, beyond the widest stroke.
HALO_REACH_SIGMAS = 4
FONTS_DIR = REPO / "design" / "fonts" / "ibm-plex" / "res" / "font"


# The fonts of the halo
#
# The text is set in a font set: the regular face (the body and the small text), the semibold face
# (the title and the label) and a scale that sets the x-height of the font to that of IBM Plex Sans
# at the same size, which the app applies to the size of its text (Sprint 11, issue #48). A face is
# a file, and for a variable font the values of its axes, which set the weight.

FontFace = collections.namedtuple("FontFace", "path axes")
FontSet = collections.namedtuple("FontSet", "name regular semibold scale")

# The weights of the two faces: the tokens `regular` and `semibold`.
FONT_WEIGHTS = {"regular": 400, "semibold": 600}
# The size at which the height of an x is measured, in pixels: a thousandth of it is a unit of Plex.
X_HEIGHT_SIZE = 1000

# IBM Plex Sans is the reference of the scale, so its own scale is one by definition.
PLEX_SANS = FontSet(
    "IBM Plex Sans",
    FontFace(str(FONTS_DIR / "ibm_plex_sans_regular.ttf"), None),
    FontFace(str(FONTS_DIR / "ibm_plex_sans_semibold.ttf"), None),
    1.0,
)


def _sfnt_tables(data):
    """The tables of a font file, `{tag: (offset, length)}`, from its table directory."""
    if data[:4] == b"ttcf":
        raise ValueError("a font collection is not a font file: name one of its fonts")
    count = struct.unpack_from(">H", data, 4)[0]
    entries = struct.iter_unpack(">4sIII", data[12 : 12 + 16 * count])
    return {tag.decode("latin-1"): (offset, length) for tag, _, offset, length in entries}


def font_axes(path):
    """The axes of a variable font, each `(tag, minimum, default, maximum)`, in the order of its
    `fvar` table. A static font has none."""
    data = Path(path).read_bytes()
    tables = _sfnt_tables(data)
    if "fvar" not in tables:
        return []
    offset = tables["fvar"][0]
    _, _, first, _, count, size, _, _ = struct.unpack_from(">HHHHHHHH", data, offset)
    axes = []
    for index in range(count):
        tag, low, default, high = struct.unpack_from(">4siii", data, offset + first + index * size)
        axes.append((tag.decode("ascii"), low / 65536, default / 65536, high / 65536))
    return axes


def font_face(path, weight):
    """The face of a font file at a weight: the file itself for a static font, and for a variable
    one the values of its axes, the weight on the `wght` axis and the default on every other."""
    axes = font_axes(path)
    if not axes:
        return FontFace(str(path), None)
    weights = [axis for axis in axes if axis[0] == "wght"]
    if not weights:
        raise ValueError(f"{path} is a variable font with no weight axis")
    low, high = weights[0][1], weights[0][3]
    if not low <= weight <= high:
        raise ValueError(
            f"{path} is a variable font whose weight axis runs from {low:g} to {high:g}, "
            f"so it has no weight of {weight}"
        )
    values = (float(weight) if tag == "wght" else default for tag, _, default, _ in axes)
    return FontFace(str(path), tuple(values))


def load_face(face, size):
    """The font of a face at `size` pixels, as Pillow draws it, with the layout of the basic engine.

    A variable font is set to the weight of its face, which needs a FreeType of 2.9.1 or later. A
    FreeType that cannot is reported and not worked around, for the weight would be the wrong one.
    """
    font = ImageFont.truetype(face.path, size, layout_engine=ImageFont.Layout.BASIC)
    if face.axes is not None:
        try:
            font.set_variation_by_axes(list(face.axes))
        except NotImplementedError as error:
            raise ValueError(
                f"{face.path} is a variable font and this FreeType cannot set its weight "
                f"({error}): use a file of the weight"
            ) from error
    return font


@functools.lru_cache(maxsize=None)
def _font(face, size):
    return load_face(face, size)


def _measured_x_height(face):
    """The height of an x in a face as a part of the em: its ink above the baseline, as drawn."""
    size = X_HEIGHT_SIZE
    image = Image.new("L", (2 * size, 2 * size), 0)
    font = load_face(face, size)
    ImageDraw.Draw(image).text((size // 2, size), "x", font=font, fill=255, anchor="ls")
    return (size - image.getbbox()[1]) / size


def x_height_ratio(face):
    """The x-height of a face as a part of the em: `sxHeight` of its OS/2 table over the units per
    em, or, where the table has none (before version 2, or zero), the height of an x as drawn."""
    data = Path(face.path).read_bytes()
    tables = _sfnt_tables(data)
    if "head" not in tables:
        raise ValueError(f"{face.path} has no head table: it is not a TrueType or OpenType font")
    units = struct.unpack_from(">H", data, tables["head"][0] + 18)[0]
    if "OS/2" in tables:
        offset, length = tables["OS/2"]
        if length >= 90 and struct.unpack_from(">H", data, offset)[0] >= 2:
            x_height = struct.unpack_from(">h", data, offset + 86)[0]
            if x_height > 0:
                return x_height / units
    return _measured_x_height(face)


def x_height_scale(face, reference=PLEX_SANS.regular):
    """The scale that makes the x-height of `face` that of `reference` (IBM Plex Sans) at the same
    size: a font with a taller x is drawn smaller, by the ratio of the two x-heights."""
    return x_height_ratio(reference) / x_height_ratio(face)


def font_set(regular, semibold, name=None):
    """The font set of two files, the regular and the semibold face, with the scale of the first.

    Both are opened, so that a file that is not there, one that is not a font and a variable font
    that this FreeType cannot set are reported here. `name` is the stem of the regular file by
    default.
    """
    files = {"regular": regular, "semibold": semibold}
    faces = [font_face(file, FONT_WEIGHTS[weight]) for weight, file in files.items()]
    for face in faces:
        load_face(face, 16)
    return FontSet(name or Path(regular).stem, *faces, x_height_scale(faces[0]))


def read_font_manifest(path):
    """The font set that a JSON manifest names: `regular` and `semibold`, the files next to the
    manifest (or by absolute path), and the `name`, which is optional."""
    path = Path(path)
    manifest = json.loads(path.read_text(encoding="utf-8"))
    missing = [weight for weight in FONT_WEIGHTS if weight not in manifest]
    if missing:
        raise ValueError(f"{path} has no {' or '.join(missing)}: a font set is both faces")
    return font_set(
        path.parent / manifest["regular"], path.parent / manifest["semibold"], manifest.get("name")
    )


def _face_weight(face):
    """The weight that a variable face is set to, or None for a static one."""
    if face.axes is None:
        return None
    for (tag, *_), value in zip(font_axes(face.path), face.axes):
        if tag == "wght":
            return value
    return None


def fonts_meta(fonts):
    """A font set as plain data, for the JSON of a measurement."""
    faces = {weight: getattr(fonts, weight) for weight in FONT_WEIGHTS}
    return {
        "name": fonts.name,
        **{
            weight: {"file": Path(face.path).name, "wght": _face_weight(face)}
            for weight, face in faces.items()
        },
        "scale": fonts.scale,
    }

# The text styles of the app that stand on the background, with the size token of each device and
# the weight of the face that sets them (`FontSet`). They
# are the styles of `borealisTypography`: the title is `titleLarge`, the label is the kicker (its
# capitals set 0.07 em apart), the body is `bodyMedium` and the small text is `bodySmall`. Large
# text is 24 sp regular or 18.66 sp bold and over: the title of a TV is, the title of a phone (18 sp
# semibold) is not.
HALO_TEXTS = {
    "title": {
        "sizes": {"tv": ("tv", "title"), "phone": ("phone", "title")},
        "weight": "semibold",
        "tracking": 0.0,
        "upper": False,
        "large": ("tv",),
    },
    "label": {
        "sizes": {"tv": ("tv", "minimum"), "phone": ("phone", "kicker")},
        "weight": "semibold",
        "tracking": 0.07,
        "upper": True,
        "large": (),
    },
    "body": {
        "sizes": {"tv": ("tv", "body"), "phone": ("phone", "body")},
        "weight": "regular",
        "tracking": 0.0,
        "upper": False,
        "large": (),
    },
    "small": {
        "sizes": {"tv": ("tv", "rail"), "phone": ("phone", "secondary")},
        "weight": "regular",
        "tracking": 0.0,
        "upper": False,
        "large": (),
    },
}


# The icons that the app draws straight on the background (issue #33): the back arrow of the bars,
# the actions of the Hosts bar and the toggle of the Apps bar. Each is measured as a glyph mask, the
# paths of `BorealisIcons.kt` stroked 2 units wide on the grid of 24 units, drawn at 24 dp.
BOREALIS_ICONS_KT = (
    REPO
    / "apps/android/borealis/designsystem/src/main/kotlin/io/github/magalz/borealis/designsystem"
    / "components/BorealisIcons.kt"
)
HALO_ICONS = ("Back", "Add", "Gear", "Visibility", "VisibilityOff")
HALO_ICON_SIZE_DP = 24
HALO_ICON_GRID = 24

IconSet = collections.namedtuple("IconSet", "stroke paths")

# A string of the Kotlin source, and the constants that icons are made of: `const val NAME = "..."`,
# one string or several added together. Any run of white space, a line end too, may stand between
# the words and the signs of a declaration, as the formatter wraps it.
_KT_STRING = re.compile(r'"([^"]*)"')
_KT_CONSTANT = re.compile(r'const\s+val\s+(\w+)\s*=\s*((?:"[^"]*"\s*\+?\s*)+)')
_KT_STROKE = re.compile(r"const\s+val\s+STROKE\s*=\s*([\d.]+)f")
_KT_ICON = re.compile(
    r'val\s+(\w+)\s*:\s*ImageVector\s+by\s+lazy\s*\{\s*icon\(\s*"\w+"\s*,(.*?)\)\s*\}',
    re.DOTALL,
)
_KT_ARGUMENT = re.compile(r'"([^"]*)"|\b([A-Z][A-Z_]+)\b')


@functools.lru_cache(maxsize=None)
def read_icons(path=BOREALIS_ICONS_KT):
    """The stroke width and the paths of the icons of `BorealisIcons.kt`, read from its source.

    The result has the `stroke` in units of the grid and `paths`, which maps the name of each icon
    to its path data, a string for each path. The paths are literals of the source or the constants
    that it defines, so the tool measures the icons that the app draws and not a copy of them.
    """
    text = Path(path).read_text(encoding="utf-8")
    constants = {
        name: "".join(_KT_STRING.findall(value)) for name, value in _KT_CONSTANT.findall(text)
    }
    stroke = float(_KT_STROKE.search(text).group(1))
    paths = {}
    for name, arguments in _KT_ICON.findall(text):
        paths[name] = tuple(
            literal or constants[constant] for literal, constant in _KT_ARGUMENT.findall(arguments)
        )
    return IconSet(stroke, paths)


_PATH_TOKEN = re.compile(r"([MmLlHhVvCcSsAaZz])|([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)")
# How many numbers each command of the path data takes.
_PATH_ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "A": 7, "Z": 0}
# A curve is cut into this many segments, and an arc into one for every few degrees.
_CURVE_STEPS = 32
_ARC_STEP = math.radians(3)


def _arc_points(start, rx, ry, rotation, large, sweep, end):
    """The points of an arc from `start` to `end`, the first one after `start`.

    The centre follows from the two points, the radii and the two flags, as the notes on the
    implementation of SVG 1.1 (F.6.5) work it out. The arc is cut into steps of a few degrees.
    """
    if (rx == 0 or ry == 0) or tuple(start) == tuple(end):
        return [end]
    rx, ry = abs(rx), abs(ry)
    cos, sin = math.cos(math.radians(rotation)), math.sin(math.radians(rotation))
    half_x, half_y = (start[0] - end[0]) / 2, (start[1] - end[1]) / 2
    x1, y1 = cos * half_x + sin * half_y, -sin * half_x + cos * half_y
    grown = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if grown > 1:
        rx, ry = rx * math.sqrt(grown), ry * math.sqrt(grown)
    square = rx * rx * y1 * y1 + ry * ry * x1 * x1
    factor = math.sqrt(max(0.0, (rx * rx * ry * ry - square) / square))
    if bool(large) == bool(sweep):
        factor = -factor
    cx1, cy1 = factor * rx * y1 / ry, -factor * ry * x1 / rx
    cx = cos * cx1 - sin * cy1 + (start[0] + end[0]) / 2
    cy = sin * cx1 + cos * cy1 + (start[1] + end[1]) / 2
    first = math.atan2((y1 - cy1) / ry, (x1 - cx1) / rx)
    last = math.atan2((-y1 - cy1) / ry, (-x1 - cx1) / rx)
    turn = last - first
    if not sweep and turn > 0:
        turn -= 2 * math.pi
    elif sweep and turn < 0:
        turn += 2 * math.pi
    steps = max(2, math.ceil(abs(turn) / _ARC_STEP))
    points = []
    for index in range(1, steps + 1):
        angle = first + turn * index / steps
        x, y = rx * math.cos(angle), ry * math.sin(angle)
        points.append((cos * x - sin * y + cx, sin * x + cos * y + cy))
    points[-1] = tuple(end)
    return points


def _cubic_points(start, first, second, end):
    """The points of a cubic curve from `start` to `end`, the first one after `start`."""
    points = []
    for index in range(1, _CURVE_STEPS + 1):
        t = index / _CURVE_STEPS
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t**2, t**3
        points.append(
            tuple(a * start[i] + b * first[i] + c * second[i] + d * end[i] for i in range(2))
        )
    return points


def icon_polylines(data):
    """The polylines, in units of the grid, that the path data of an icon draws.

    It reads the commands that the icons use: move, line, horizontal and vertical line, cubic curve,
    smooth cubic curve, arc and close, absolute or relative. A move starts a polyline and a close
    ends one at its start. Curves and arcs are cut into short segments.
    """
    lines, line = [], []
    at, begin, reflected = (0.0, 0.0), (0.0, 0.0), None
    command, numbers = None, []
    tokens = [(word, number) for word, number in _PATH_TOKEN.findall(data)]
    index = 0

    def start_line(point):
        nonlocal line
        if len(line) > 1:
            lines.append(np.array(line, np.float64))
        line = [point]

    while index < len(tokens):
        word, number = tokens[index]
        if word:
            command, index = word, index + 1
            if command in "Zz":
                if line:
                    line.append(begin)
                    lines.append(np.array(line, np.float64))
                    line = []
                at, reflected = begin, None
                continue
        elif command is None:
            raise ValueError(f"path data does not start with a command: {data!r}")
        arity = _PATH_ARITY[command.upper()]
        numbers = [float(n) for _, n in tokens[index : index + arity]]
        if len(numbers) < arity or any(w for w, _ in tokens[index : index + arity]):
            raise ValueError(f"path data ends in the middle of a command: {data!r}")
        index += arity
        relative = command.islower()
        kind = command.upper()
        ox, oy = at if relative else (0.0, 0.0)
        if kind == "M":
            at = (numbers[0] + ox, numbers[1] + oy)
            begin = at
            start_line(at)
            # More pairs after a move are lines.
            command = "l" if relative else "L"
            reflected = None
            continue
        if not line:
            line = [at]
        if kind == "L":
            at = (numbers[0] + ox, numbers[1] + oy)
            line.append(at)
            reflected = None
        elif kind == "H":
            at = (numbers[0] + (at[0] if relative else 0.0), at[1])
            line.append(at)
            reflected = None
        elif kind == "V":
            at = (at[0], numbers[0] + (at[1] if relative else 0.0))
            line.append(at)
            reflected = None
        elif kind in "CS":
            if kind == "C":
                first = (numbers[0] + ox, numbers[1] + oy)
                rest = numbers[2:]
            else:
                first = at
                if reflected is not None:
                    first = (2 * at[0] - reflected[0], 2 * at[1] - reflected[1])
                rest = numbers
            second = (rest[0] + ox, rest[1] + oy)
            end = (rest[2] + ox, rest[3] + oy)
            line.extend(_cubic_points(at, first, second, end))
            at, reflected = end, second
        else:
            end = (numbers[5] + ox, numbers[6] + oy)
            radius_x, radius_y, rotation, large, sweep = numbers[:5]
            line.extend(_arc_points(at, radius_x, radius_y, rotation, large, sweep, end))
            at, reflected = end, None
    if len(line) > 1:
        lines.append(np.array(line, np.float64))
    return lines


def halo_configs():
    """The candidates of the grid, from the smallest halo to the largest: stroke, blur, opacity."""
    return sorted(
        (stroke, blur, opacity)
        for stroke in HALO_STROKES_DP
        for blur in HALO_BLURS_DP
        for opacity in HALO_OPACITIES
    )


@functools.lru_cache(maxsize=None)
def _text_sizes(path):
    """The sizes of `design/tokens.json` in sp: `{(device, name): sp}`."""
    sizes = json.loads(Path(path).read_text(encoding="utf-8"))["size"]
    return {
        (device, name): float(entry["$value"].removesuffix("sp"))
        for device in ("tv", "phone")
        for name, entry in sizes[device].items()
        if str(entry.get("$value", "")).endswith("sp")
    }


def device_density(device):
    """The density of a device: its frame width in pixels over its width in dp."""
    return frame_density(DEVICE_SIZE_PX[device][0], device)


def text_size_px(text, device, tokens=TOKENS_JSON):
    """The size in pixels of a text style on a device: its sp from the tokens, at the density."""
    return _text_sizes(tokens)[HALO_TEXTS[text]["sizes"][device]] * device_density(device)


def halo_limit(text, device, high_contrast=False):
    """The ratio that a text style needs on a device.

    3 for large text and 4.5 for the rest, and 7 for all of it under high contrast. An icon is a
    non-text indicator, which WCAG 1.4.11 asks 3:1 for, high contrast or not.
    """
    if text in HALO_ICONS:
        return WCAG_LIMITS[0]
    if high_contrast:
        return WCAG_LIMITS[2]
    return WCAG_LIMITS[0] if device in HALO_TEXTS[text]["large"] else WCAG_LIMITS[1]


def load_high_contrast(path=TOKENS_JSON):
    """The colours that high contrast puts in the place of the roles of the same name, and its dim.

    The dim is the opacity of the `page` colour that lies over the animated background.
    """
    section = json.loads(Path(path).read_text(encoding="utf-8"))["high-contrast"]
    return {
        name: entry["$value"]
        for name, entry in section.items()
        if isinstance(entry, dict) and "$value" in entry
    }


def halo_veil(frame, tint, dim):
    """The frame under the veil that high contrast lays over the background.

    `tint` lies over the frame at `dim`, in the encoded values as the platform composites, and the
    result is an 8 bit picture, as the screen is. No dim leaves the frame as it is.
    """
    if dim <= 0:
        return np.asarray(frame, dtype=np.float32)
    return (np.rint(composite(frame, tint, dim) * 255) / 255).astype(np.float32)


# The sample text is set at this many times its size, and what is wanted of it is cut from that:
# its coverage by averaging, and its widening by a stroke on the hard edge of the big picture.
# Android does not hint the text, so the big picture, whose hinting is a fourth of a pixel, is
# nearer it than a picture at the size of the text.
HALO_SUPERSAMPLE = 4


def _set_sample(text, device, pad, tokens=TOKENS_JSON, fonts=PLEX_SANS):
    """The sample text of a style at `HALO_SUPERSAMPLE` times its size, as coverage, 0 to 1.

    The picture is that many times as large as the text with `pad` pixels round it. A label is in
    capitals, with its letters spaced. The size of the text is that of the token file `tokens`,
    times the scale of the font set `fonts`, and the face is its regular or its semibold one.
    """
    if text in HALO_ICONS:
        return _set_icon(text, device, pad)
    style = HALO_TEXTS[text]
    face = getattr(fonts, style["weight"])
    size = text_size_px(text, device, tokens) * fonts.scale
    scale = HALO_SUPERSAMPLE
    sample = HALO_SAMPLE.upper() if style["upper"] else HALO_SAMPLE
    ascent, descent = _font(face, size).getmetrics()
    font = _font(face, size * scale)
    starts = [
        font.getlength(sample[:index]) + index * style["tracking"] * size * scale
        for index in range(len(sample))
    ]
    width = math.ceil(starts[-1] / scale + font.getlength(sample[-1]) / scale) + 1
    image = Image.new("L", ((width + 2 * pad) * scale, (ascent + descent + 2 * pad) * scale), 0)
    draw = ImageDraw.Draw(image)
    for letter, start in zip(sample, starts):
        draw.text((pad * scale + start, pad * scale), letter, font=font, fill=255)
    return np.asarray(image, dtype=np.float32) / 255


def _stroked(lines, size, radius):
    """The pixels of a square picture whose centre is within `radius` of one of the `lines`, a mask.

    The distance is to the segments of the polylines, so the ends and the corners are round, as the
    round caps and joins of the icons make them.
    """
    covered = np.zeros((size, size), bool)
    for line in lines:
        for first, second in zip(line[:-1], line[1:]):
            low = np.maximum(np.floor(np.minimum(first, second) - radius).astype(int), 0)
            high = np.minimum(np.ceil(np.maximum(first, second) + radius).astype(int), size - 1)
            ys, xs = np.mgrid[low[1] : high[1] + 1, low[0] : high[0] + 1]
            px, py = xs + 0.5 - first[0], ys + 0.5 - first[1]
            along = second - first
            length = float(along @ along)
            t = np.clip((px * along[0] + py * along[1]) / length, 0.0, 1.0) if length else 0.0
            near = (px - t * along[0]) ** 2 + (py - t * along[1]) ** 2 <= radius * radius
            covered[low[1] : high[1] + 1, low[0] : high[0] + 1] |= near
    return covered


def _set_icon(name, device, pad):
    """The icon at `HALO_SUPERSAMPLE` times its size, as coverage, 0 to 1, with `pad` pixels round.

    The icon is its paths, read from the source of the app, stroked as wide as the app strokes them
    and drawn at `HALO_ICON_SIZE_DP` on a device. The picture is that many times as large as the
    icon with `pad` pixels round it, as for a text.
    """
    icons = read_icons()
    scale = HALO_SUPERSAMPLE
    side = round(HALO_ICON_SIZE_DP * device_density(device))
    unit = side * scale / HALO_ICON_GRID
    origin = pad * scale
    lines = [
        origin + line * unit for data in icons.paths[name] for line in icon_polylines(data)
    ]
    size = (side + 2 * pad) * scale
    return _stroked(lines, size, icons.stroke / 2 * unit).astype(np.float32)


def _widen(covered, radius):
    """`covered`, a mask of booleans, with every pixel within `radius` pixels of it added.

    It is the convolution of the mask with a disc, done in the frequency domain: a pixel is added
    when the disc round it touches the mask. The widening is the same all round, so it is how a
    stroke of round joins widens a glyph, and it is the same where the glyphs are closer than the
    stroke, which an outline stroker does not get right.
    """
    height, width = covered.shape
    reach = math.ceil(radius)
    disc = np.zeros((height, width), np.float32)
    for dy in range(-reach, reach + 1):
        for dx in range(-reach, reach + 1):
            if dx * dx + dy * dy <= radius * radius:
                disc[dy % height, dx % width] = 1.0
    shape = (height, width)
    touched = np.fft.irfft2(np.fft.rfft2(covered.astype(np.float32)) * np.fft.rfft2(disc), s=shape)
    return touched > 0.5


def _reduce(big):
    """A picture at `HALO_SUPERSAMPLE` times the size, brought down by the mean of each block."""
    scale = HALO_SUPERSAMPLE
    height, width = big.shape[0] // scale, big.shape[1] // scale
    return big.reshape(height, scale, width, scale).mean(axis=(1, 3))


def glyph_mask(text, device, stroke_dp=0.0, pad=0, tokens=TOKENS_JSON, fonts=PLEX_SANS):
    """The coverage, 0 to 1, of the sample text of a style, with each glyph widened by a stroke.

    The text is set in the face of the style (of the font set `fonts`) at its size on the device. A
    label is in capitals, with its letters spaced. `stroke_dp` widens each glyph by that much on
    every side, as a fill under a stroke of twice the width does, with round joins. The picture is
    as large as the text with `pad` pixels on each side, whatever the stroke, so that masks of
    different strokes line up, and `pad` must be at least the stroke. The size of the text is that
    of the token file `tokens`, times the scale of the font set.
    """
    big = _set_sample(text, device, pad, tokens, fonts)
    plain = _reduce(big)
    stroke = stroke_dp * device_density(device)
    if stroke <= 0:
        return plain.astype(np.float32)
    widened = _reduce(_widen(big >= 0.5, stroke * HALO_SUPERSAMPLE).astype(np.float32))
    return np.maximum(plain, widened).astype(np.float32)


def ring_mask(mask, radius=HALO_RING_PX):
    """The pixels that are not in a glyph and are within `radius` pixels of one.

    A pixel that a glyph covers in part is a glyph pixel. The distance is the Euclidean one, so the
    ring is a disc round each glyph pixel.
    """
    glyph = np.asarray(mask) > 0
    height, width = glyph.shape
    padded = np.pad(glyph, radius)
    near = np.zeros_like(glyph)
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx * dx + dy * dy <= radius * radius:
                rows = slice(radius + dy, radius + dy + height)
                columns = slice(radius + dx, radius + dx + width)
                near |= padded[rows, columns]
    return near & ~glyph


def halo_alpha(mask, blur_dp, opacity, density):
    """The alpha of a halo: the mask blurred by `blur_dp`, at `opacity`.

    The blur is the Gaussian that Skia gives a blur radius, of `blur_dp * density` pixels. A radius
    of 0 is no blur: the mask itself.
    """
    mask = np.asarray(mask, dtype=np.float32)
    if blur_dp > 0:
        mask = gaussian_blur(mask[..., None], blur_sigma(blur_dp * density))[..., 0]
    return np.clip(opacity * mask, 0.0, 1.0).astype(np.float32)


def halo_over(image, alpha, tint):
    """`tint` over `image`, as much as `alpha` says at each pixel, mixed in the encoded values."""
    return composite(image, tint, np.asarray(alpha, dtype=np.float32)[..., None])


def halo_contrast(pixels, alpha, tint, text_luminance):
    """The contrast ratio of a text over pixels that have the halo `alpha` laid over them."""
    return contrast_ratio(relative_luminance(halo_over(pixels, alpha, tint)), text_luminance)


def frame_colours(frame):
    """The colours of an 8 bit frame, each once, as floats from 0 to 1, one colour to a row.

    A frame of a video or of the drawn backgrounds has tens of thousands of them, where it has
    millions of pixels, so what is asked of every pixel can be asked of every colour.
    """
    levels = np.rint(np.asarray(frame).reshape(-1, 3) * 255).astype(np.uint32)
    packed = np.unique((levels[:, 0] << 16) | (levels[:, 1] << 8) | levels[:, 2])
    colours = np.stack([(packed >> 16) & 255, (packed >> 8) & 255, packed & 255], axis=1)
    return colours.astype(np.float32) / 255


def _halo_ends(colours, alphas, tint):
    """The luminance of each colour with the thinnest and with the thickest halo of `alphas`."""
    return [
        relative_luminance(halo_over(colours, np.full(len(colours), alpha, np.float32), tint))
        for alpha in alphas
    ]


def _worst_of_ends(ends, text_luminance):
    """The lowest ratio of a text over colours that a halo of any alpha between two ends covers.

    The luminance of a colour moves one way as the alpha grows, and the ratio of a text over it is
    lowest where it is nearest to the text. So the lowest ratio is at one of the two ends, unless
    the range of luminances reaches the text, where the pixel is as bright as the text and the
    ratio is 1:1. The alphas of a ring are many, so the range is taken to be whole.
    """
    darkest, brightest = np.minimum(*ends), np.maximum(*ends)
    if np.any((darkest <= text_luminance) & (text_luminance <= brightest)):
        return 1.0
    return float(min(contrast_ratio(seen, text_luminance).min() for seen in ends))


def halo_worst_ratio(colours, alphas, tint, text_luminance):
    """The lowest ratio of a text over `colours` with a halo of any alpha from `alphas` laid on.

    `alphas` is the least and the greatest alpha of the halo. It is the worst case of the text with
    every colour of a frame at every pixel of the ring, which is the worst case over every place of
    the text on the frame, and a little more at its edges.

    This is a model oracle for the tests. The measurement does not call it: it takes the same two
    steps itself (`_halo_ends` and `_worst_of_ends`), so as to work out the ends once for both text
    tokens, and the tests hold it to this.
    """
    return _worst_of_ends(_halo_ends(colours, alphas, tint), text_luminance)


def halo_placements(frame_shape, size):
    """The top left corners of the places that the sample text takes over a frame.

    `frame_shape` is the (height, width) of the frame and `size` the (height, width) of the text.
    There are `HALO_PLACEMENTS` columns and rows of them, spread from one corner to the other, the
    first at the top left and the last at the bottom right, row by row.
    """
    columns, rows = HALO_PLACEMENTS
    (height, width), (text_height, text_width) = frame_shape[:2], size
    reach_x, reach_y = max(width - text_width, 0), max(height - text_height, 0)
    xs = [round(reach_x * index / (columns - 1)) for index in range(columns)]
    ys = [round(reach_y * index / (rows - 1)) for index in range(rows)]
    return [(x, y) for y in ys for x in xs]


HaloGeometry = collections.namedtuple("HaloGeometry", "configs alphas ring size")


@functools.lru_cache(maxsize=None)
def halo_geometry(text, device, tokens=TOKENS_JSON, fonts=PLEX_SANS):
    """The ring around the glyphs of a style and the alpha of each candidate on it.

    None of it depends on the frame. `ring` is the ring as a mask of its bounding box, `size` the
    (height, width) of that box and `alphas` has a row for each of `configs` (no halo first, then
    the grid) and a column for each pixel of the ring. The text is the size of the token file
    `tokens`, set in the font set `fonts`.
    """
    density = device_density(device)
    sigma = blur_sigma(max(HALO_BLURS_DP) * density)
    pad = math.ceil(max(HALO_STROKES_DP) * density + HALO_REACH_SIGMAS * sigma) + HALO_RING_PX + 1
    ring = ring_mask(glyph_mask(text, device, pad=pad, tokens=tokens, fonts=fonts))
    rows, columns = np.nonzero(ring)
    box = (slice(rows.min(), rows.max() + 1), slice(columns.min(), columns.max() + 1))
    ring = ring[box]
    configs = [HALO_NONE] + halo_configs()
    alphas = {HALO_NONE: np.zeros(int(ring.sum()), np.float32)}
    for stroke in HALO_STROKES_DP:
        widened = glyph_mask(text, device, stroke, pad, tokens, fonts)
        for blur in HALO_BLURS_DP:
            full = halo_alpha(widened, blur, 1.0, density)[box][ring]
            for opacity in HALO_OPACITIES:
                alphas[(stroke, blur, opacity)] = opacity * full
    return HaloGeometry(configs, np.stack([alphas[config] for config in configs]), ring, ring.shape)


def _halo_place(pixels, geometry, tint, lights):
    """The p1 and p5 of each candidate and token, for the ring pixels of one place."""
    stats = {}
    for token, light in lights.items():
        ratios = halo_contrast(pixels[None], geometry.alphas, tint, light)
        stats[token] = tuple(np.percentile(ratios, [1, 5], axis=1))
    return stats


def measure_halo_frame(
    frame, device, colors, texts=None, dim=0.0, tokens=TOKENS_JSON, fonts=PLEX_SANS
):
    """The stats of each text style, candidate and token over one frame.

    The worst case ("min") is over every colour of the frame with every alpha of the ring, which is
    every place that the text can take, as `halo_worst_ratio` says. p1 and p5 are for information:
    in each of the six by four places of the sample over the frame, the ratio of each token over the
    ring pixels with the halo on them, the ratio below which 1% and 5% of them are, and the lowest
    of those over the places. It returns `{text: {config: {token: stats}}}`, where `stats` has
    "min", "p1" and "p5". `frame` is an array or the path of a PNG. A `dim` is the veil of the page
    colour that high contrast lays over the frame, which the text is measured under. The text is
    the size that the token file `tokens` says, set in the font set `fonts`.
    """
    if not isinstance(frame, np.ndarray):
        frame = load_frame(frame)
    tint = parse_hex(colors[HALO_TINT])
    frame = halo_veil(frame, tint, dim)
    lights = {token: float(relative_luminance(parse_hex(colors[token]))) for token in HALO_TOKENS}
    colours = frame_colours(frame)
    result = {}
    for text in texts or HALO_TEXTS:
        geometry = halo_geometry(text, device, tokens, fonts)
        worst = {token: None for token in HALO_TOKENS}
        for x, y in halo_placements(frame.shape, geometry.size):
            height, width = geometry.size
            pixels = frame[y : y + height, x : x + width][geometry.ring]
            for token, found in _halo_place(pixels, geometry, tint, lights).items():
                previous = worst[token]
                merged = found if previous is None else tuple(map(np.minimum, previous, found))
                worst[token] = merged
        result[text] = {}
        for index, config in enumerate(geometry.configs):
            alphas = (geometry.alphas[index].min(), geometry.alphas[index].max())
            ends = _halo_ends(colours, alphas, tint)
            result[text][config] = {
                token: {
                    "min": _worst_of_ends(ends, lights[token]),
                    "p1": float(worst[token][0][index]),
                    "p5": float(worst[token][1][index]),
                }
                for token in HALO_TOKENS
            }
    return result


def measure_halo_source(
    frames, style, device, colors, jobs=1, texts=None, dim=0.0, tokens=TOKENS_JSON, fonts=PLEX_SANS
):
    """The rows of one background style on one device, over its frames.

    A row is a text style, a token and a candidate, with the lowest ratio over the frames ("min"),
    p1 and p5 (their lowest) and the number of the worst frame. The rows are plain data.
    """
    measured = _map_frames(
        measure_halo_frame, frames, jobs, device, colors, texts, dim, tokens, fonts
    )
    rows = []
    for text in texts or HALO_TEXTS:
        for config in [HALO_NONE] + halo_configs():
            for token in HALO_TOKENS:
                folded = _fold([m[text][config][token] for m in measured])
                folded.pop("per_frame_min")
                rows.append(
                    {
                        "style": style,
                        "device": device,
                        "text": text,
                        "token": token,
                        "stroke_dp": config[0],
                        "blur_dp": config[1],
                        "opacity": config[2],
                        **folded,
                    }
                )
    return rows


def measure_halo_sources(
    sources, colors, jobs=1, texts=None, dim=0.0, tokens=TOKENS_JSON, fonts=PLEX_SANS
):
    """The halo over `sources`, which maps (style, device) to its frames.

    The sizes of the text are those of the token file `tokens`, set in the font set `fonts`;
    `colors` are given.
    """
    result = {
        "meta": {
            "statistic": (
                "WCAG 2.x contrast ratio of ink and ink-2 over the pixels not in a glyph and "
                f"within {HALO_RING_PX} px of one, with the halo on them; the worst case is the "
                "lowest ratio over every colour of the frame with every alpha of the ring, which "
                "is every place of the sample text on the frame, and then over the frames. p1 and "
                "p5 are for information, over the places of a grid of the columns and rows of "
                "`placements`."
            ),
            "model": (
                "The glyphs of the sample text, widened by the stroke, blurred by a Gaussian of "
                "sigma 0.57735 * radius_px + 0.5 (Skia), laid in page at the opacity over the "
                "frame in the encoded values, and the text on top."
            ),
            "colors": colors,
            "fonts": fonts_meta(fonts),
            "tokens": list(HALO_TOKENS),
            "tint": HALO_TINT,
            "sample": HALO_SAMPLE,
            "texts": {
                name: {
                    "sizes_sp": {
                        device: _text_sizes(tokens)[size]
                        for device, size in style["sizes"].items()
                    },
                    "weight": style["weight"],
                    "font": Path(getattr(fonts, style["weight"]).path).stem,
                }
                for name, style in HALO_TEXTS.items()
            },
            "icons": {
                "names": list(HALO_ICONS),
                "size_dp": HALO_ICON_SIZE_DP,
                "stroke_units": read_icons().stroke,
            },
            "strokes_dp": list(HALO_STROKES_DP),
            "blurs_dp": list(HALO_BLURS_DP),
            "opacities": list(HALO_OPACITIES),
            "placements": list(HALO_PLACEMENTS),
            "ring_px": HALO_RING_PX,
            "margin": HALO_MARGIN,
            "high_contrast": {"dim": dim} if dim else None,
        },
        "sources": [],
        "halo": [],
    }
    for (style, device), frames in sorted(sources.items()):
        result["sources"].append({"style": style, "device": device, "frames": len(frames)})
        result["halo"] += measure_halo_source(
            frames, style, device, colors, jobs, texts, dim, tokens, fonts
        )
    return result


def measure_halo_folder(
    root, colors, jobs=1, texts=None, dim=0.0, tokens=TOKENS_JSON, fonts=PLEX_SANS
):
    """Measures the halo over the frames in `root/<Style>-<device>/*.png`."""
    return measure_halo_sources(_folder_sources(root), colors, jobs, texts, dim, tokens, fonts)


def _halo_required(row):
    """A row that has to keep its limit: a title is ink only, the rest has both tokens."""
    return row["token"] == "ink" or row["text"] != "title"


def _halo_keeps(row):
    return row["min"] >= halo_limit(row["text"], row["device"]) * HALO_MARGIN


def _halo_config(row):
    return (row["stroke_dp"], row["blur_dp"], row["opacity"])


def halo_passing(rows):
    """The candidates of the grid, from the smallest, that keep every ratio over every row."""
    keeps = {}
    for row in rows:
        config = _halo_config(row)
        if config != HALO_NONE and _halo_required(row):
            keeps[config] = keeps.get(config, True) and _halo_keeps(row)
    return sorted(config for config, kept in keeps.items() if kept)


def choose_halo(rows):
    """The smallest candidate of the grid that keeps every ratio, or None.

    Smallest is the least stroke, then the least blur, then the least opacity. A ratio is kept when
    it reaches its limit (4.5 for text, 3 for large text) with the margin of the model. A title is
    measured with ink only, because ink is the colour of a title.
    """
    passing = halo_passing(rows)
    return passing[0] if passing else None


def halo_choice_text(config):
    stroke, blur, opacity = config
    return f"stroke {stroke} dp, blur {blur} dp, opacity {round(opacity * 100)} %"


def _is_large(row):
    """Whether a row is large text, which needs 3:1 where the rest needs 4.5:1."""
    return halo_limit(row["text"], row["device"]) < WCAG_LIMITS[1]


def _halo_worst(rows, token, large):
    """The lowest ratio of `token` over the rows of text of one kind, large or not, as printed."""
    found = [
        row["min"]
        for row in rows
        if row["token"] == token and _halo_required(row) and _is_large(row) == large
    ]
    return format_ratio(min(found)) if found else "-"


def _grid_line(config, rows, kept):
    """A line of Table D: a candidate, its lowest ratios and whether it keeps them."""
    stroke, blur, opacity = config
    if config == HALO_NONE:
        head, verdict = ["none", "-", "-"], "-"
    else:
        head = [str(stroke), str(blur), f"{round(opacity * 100)} %"]
        verdict = "yes" if config in kept else "no"
    return head + [
        _halo_worst(rows, "ink", False),
        _halo_worst(rows, "ink-2", False),
        _halo_worst(rows, "ink", True),
        verdict,
    ]


def _text_lines(rows, high_contrast=False):
    """The lines of Table E: for each text style and device, the worst of the rows given."""
    worst = {}
    for row in rows:
        key = (row["text"], row["device"], row["token"])
        if key not in worst or row["min"] < worst[key]["min"]:
            worst[key] = row
    lines = []
    for text in [*HALO_TEXTS, *HALO_ICONS]:
        for device in ("tv", "phone"):
            found = [worst.get((text, device, token)) for token in HALO_TOKENS]
            if None in found:
                continue
            limit = halo_limit(text, device, high_contrast)
            line = [text, DEVICE_LABELS[device], format_ratio(limit)]
            for row in found:
                background = STYLE_LABELS.get(row["style"], row["style"])
                line += [f"{format_ratio(row['min'])} ({background})"]
                line += [format_ratio(row["p1"]), format_ratio(row["p5"])]
            lines.append(line)
    return lines


def _background_lines(rows, choice):
    """The lines of Table F: for each background, no halo and the choice, over the text styles."""
    worst = {}
    for row in rows:
        config = _halo_config(row)
        if _halo_required(row) and config in (HALO_NONE, choice):
            key = (row["style"], row["device"], row["token"], config == choice)
            worst[key] = min(worst.get(key, math.inf), row["min"])
    backgrounds = sorted({(row["style"], row["device"]) for row in rows})
    return [
        [_label(*background)]
        + [
            format_ratio(worst[(*background, token, with_halo)])
            if (*background, token, with_halo) in worst
            else "-"
            for token in HALO_TOKENS
            for with_halo in (False, True)
        ]
        for background in backgrounds
    ]


def halo_tables(result):
    """The halo tables of `docs/ux/accessibility.md`, and the choice.

    Table D is every candidate: its lowest ratios over every background, device and text style.
    Table E is the chosen candidate, by text style and device, with p1 and p5. Table F is the
    chosen candidate and no halo, by background.
    """
    rows = [row for row in result["halo"] if row["text"] in HALO_TEXTS]
    names = [f"`{token}`" for token in HALO_TOKENS]
    kept = set(halo_passing(rows))
    by_config = {}
    for row in rows:
        by_config.setdefault(_halo_config(row), []).append(row)
    heads = ["Stroke dp", "Blur dp", "Opacity", f"{names[0]}, small", f"{names[1]}, small"]
    grid = [
        _grid_line(config, by_config[config], kept)
        for config in [HALO_NONE] + halo_configs()
        if config in by_config
    ]
    tables = [
        "Table D. Text on the background with a halo: the lowest ratio over every background, "
        "device and text style, for each candidate. Small text is the label, the body, the small "
        "text and the title of a phone, which need 4.5:1. The title of a TV is large text and "
        "needs 3:1. A candidate keeps the ratios when every one clears its limit by 2%.",
        _table(heads + [f"{names[0]}, large", "Keeps"], grid),
    ]
    choice = choose_halo(rows)
    if choice is None:
        return "\n\n".join(tables + ["No candidate of the grid keeps every ratio."])
    heads = ["Text", "Device", "Needs"]
    for name in names:
        heads += [f"{name} min", f"{name} p1", f"{name} p5"]
    chosen = [row for row in rows if _halo_config(row) == choice]
    fonts = result.get("meta", {}).get("fonts")
    if fonts is None or fonts == fonts_meta(PLEX_SANS):
        verdict = f"The choice: {halo_choice_text(choice)}."
    else:
        # The notes have the line "The choice: ..." once, and a test reads it: it is the font of the
        # app's.
        verdict = f"The smallest candidate for {fonts['name']}: {halo_choice_text(choice)}."
    tables += [
        f"Table E. The chosen halo ({halo_choice_text(choice)}) by text style and device: the "
        "lowest ratio over the backgrounds, with the background it is in, and the lowest p1 "
        "and p5.",
        _table(heads, _text_lines(chosen)),
        "Table F. The lowest ratio over the text styles, by background, with no halo and with "
        "the chosen one.",
        _table(
            ["Background"]
            + [f"{name}, {kind}" for name in names for kind in ("none", "halo")],
            _background_lines(rows, choice),
        ),
        verdict,
    ]
    return "\n\n".join(tables)


def halo_high_contrast_tables(result, config):
    """The tables of loose text under high contrast, for the halo `config`, and their verdict.

    The result is a measurement made with the dim and the colours of high contrast. Table G is the
    lowest ratio over the text styles, by background, with the dim alone and with the dim and the
    halo. Table H is the halo by text style and device. Every text needs 7:1, with no margin.
    """
    rows = [row for row in result["halo"] if row["text"] in HALO_TEXTS]
    names = [f"`{token}`" for token in HALO_TOKENS]
    dim = (result.get("meta", {}).get("high_contrast") or {}).get("dim")
    veil = "the page colour over it" + (f" at {dim}" if dim else "")
    chosen = [row for row in rows if _halo_config(row) == config and _halo_required(row)]
    heads = ["Text", "Device", "Needs"]
    for name in names:
        heads += [f"{name} min", f"{name} p1", f"{name} p5"]
    short = [row for row in chosen if row["min"] < halo_limit(row["text"], row["device"], True)]
    tables = [
        f"Table G. Loose text under high contrast, with {veil} and the ink of high contrast: the "
        "lowest ratio over the text styles, by background, with the dim alone and with the dim and "
        f"the halo ({halo_choice_text(config)}).",
        _table(
            ["Background"] + [f"{name}, {kind}" for name in names for kind in ("dim only", "halo")],
            _background_lines(rows, config),
        ),
        "Table H. The same halo by text style and device: the lowest ratio over the backgrounds, "
        "with the background it is in, and the lowest p1 and p5.",
        _table(heads, _text_lines([r for r in rows if _halo_config(r) == config], True)),
    ]
    worst = min(chosen, key=lambda row: row["min"])
    where = (
        f"`{worst['token']}`, {worst['text']}, {DEVICE_LABELS[worst['device']]}, "
        f"{STYLE_LABELS.get(worst['style'], worst['style'])}"
    )
    if not short:
        tables.append(
            "Under high contrast every text keeps 7:1 over every background: the lowest is "
            f"{format_ratio(worst['min'])} ({where})."
        )
    else:
        tables.append(
            f"Under high contrast the text falls short of 7:1 in {len(short)} of {len(chosen)} "
            f"cases; the lowest is {format_ratio(worst['min'])} ({where})."
        )
    return "\n\n".join(tables)


def halo_icon_tables(result, config):
    """The tables of the icons with the halo `config`, and their verdict.

    Table I is each icon on each device: the lowest ratio over the backgrounds, with the background
    it is in, and the lowest p1 and p5. Table J is the lowest ratio over the icons, by background,
    with no halo and with the halo. An icon is a non-text indicator and needs 3:1 (WCAG 1.4.11):
    with 2% to spare for the difference between the model and a device, as the text has, and with
    none under high contrast, where the veil of the page is laid over the background first.
    """
    rows = [row for row in result["halo"] if row["text"] in HALO_ICONS]
    dim = (result.get("meta", {}).get("high_contrast") or {}).get("dim")
    margin = 1.0 if dim else HALO_MARGIN
    names = [f"`{token}`" for token in HALO_TOKENS]
    chosen = [row for row in rows if _halo_config(row) == config]
    limit = f"{WCAG_LIMITS[0]:g}"
    under = (
        f", under high contrast, with the page colour over the background at {dim} and the ink of "
        "high contrast"
        if dim
        else ""
    )
    heads = ["Icon", "Device", "Needs"]
    for name in names:
        heads += [f"{name} min", f"{name} p1", f"{name} p5"]
    tables = [
        f"Table I. The icons with the halo ({halo_choice_text(config)}){under}, by icon and "
        "device: the lowest ratio over the backgrounds, with the background it is in, and the "
        "lowest p1 and p5.",
        _table(heads, _text_lines(chosen, high_contrast=bool(dim))),
        "Table J. The lowest ratio over the icons, by background, with no halo and with the halo.",
        _table(
            ["Background"] + [f"{name}, {kind}" for name in names for kind in ("none", "halo")],
            _background_lines(rows, config),
        ),
    ]
    worst = min(chosen, key=lambda row: row["min"])
    where = (
        f"`{worst['token']}`, {worst['text']}, {DEVICE_LABELS[worst['device']]}, "
        f"{STYLE_LABELS.get(worst['style'], worst['style'])}"
    )
    short = [row for row in chosen if row["min"] < WCAG_LIMITS[0] * margin]
    spare = "" if dim else ", with 2% to spare"
    if short:
        tables.append(
            f"With this halo the icon falls short of {limit}:1 in {len(short)} of {len(chosen)} "
            f"cases; the lowest is {format_ratio(worst['min'])} ({where})."
        )
    else:
        lead = "Under high contrast" if dim else "With this halo"
        tables.append(
            f"{lead} every icon keeps {limit}:1 over every background{spare}: the lowest is "
            f"{format_ratio(worst['min'])} ({where})."
        )
    alone = None if dim else choose_halo(rows)
    if alone is not None:
        tables.append(
            f"On their own the icons need no more than the smallest candidate of the grid that "
            f"keeps {limit}:1: {halo_choice_text(alone)}. The app draws the halo of the text."
        )
    return "\n\n".join(tables)


# The accent candidates
#
# Sprint 11 lets the viewer choose the accent colour (issue #50), and the gate of the owner chooses
# the candidates from these numbers. `accents.json` holds each candidate as a full set of roles, the
# ones of `design/tokens.json`. For each set the tool checks:
#
# - the label on an accent fill, and the accent and accent-soft as text on the surfaces and on the
#   tint of a selected row;
# - the accent and accent-soft over the brightest pixel behind the glass of each style, at the
#   default of Settings and at Reduced, as text and as an outline, and the selected label, which is
#   the accent over its own tint on that glass;
# - the high contrast set: text at 7:1 and borders at 3:1 over every fill;
# - the CIEDE2000 distance of the accent from the status colours, as the viewers of each palette see
#   them, and from the ink.
#
# The backgrounds are as they are drawn today, with their fixed violet: Sprint 11 stops the drawn
# ones from taking the accent, so the glass table stays as it is.

ACCENTS_JSON = REPO / "design" / "accessibility" / "accents.json"

ACCENT_ROLES = ("accent", "accent-soft", "accent-strong", "on-accent", "accent-tint", "accent-line")
# The tint and the line are drawn with an alpha. The other roles are opaque colours.
ACCENT_ALPHA_ROLES = ("accent-tint", "accent-line")
ACCENT_HIGH_CONTRAST_ROLES = ("accent", "accent-soft", "accent-line")
# The neutral tokens that the accents are checked against.
ACCENT_NEUTRALS = (
    "page",
    "chrome",
    "surface",
    "surface-raised",
    "ink",
    "ink-2",
    "ink-3",
    "hover",
)
# The fills that text lies on in high contrast, before the tint of a selected row and the hover.
ACCENT_FILLS = ("page", "chrome", "surface", "surface-raised")
STATUS_COLOURS = ("success", "warning", "danger", "offline")
STATUS_PALETTES = ("default", "red-green", "blue-yellow")

ACCENT_TEXT = WCAG_LIMITS[1]
ACCENT_OUTLINE = WCAG_LIMITS[0]
ACCENT_HIGH_CONTRAST_TEXT = WCAG_LIMITS[2]
ACCENT_HIGH_CONTRAST_BORDER = WCAG_LIMITS[0]
# Two colours are apart when CIEDE2000 puts them 20 or more from each other, as in
# `ColourVisionPaletteTest`.
ACCENT_DISTANCE = 20.0

# The glass the accents are measured over: the 8 dp of the default of Settings at every tint of the
# grid (the tint of a style is its floor), and the glass of Reduced.
ACCENT_PANELS = [(GLASS_DEFAULT[1], alpha) for alpha in GLASS_ALPHAS] + [
    (GLASS_REDUCED[1], GLASS_REDUCED[0])
]

# The Machado, Oliveira and Fernandes (2009) matrices of the dichromacies at severity 1.0, as
# `ColourMeasures.kt` has them. A matrix acts on linear RGB, and each row adds up to 1.
DICHROMACIES = {
    "protanopia": (
        (0.152286, 1.052583, -0.204868),
        (0.114503, 0.786281, 0.099216),
        (-0.003882, -0.048116, 1.051998),
    ),
    "deuteranopia": (
        (0.367322, 0.860646, -0.227968),
        (0.280085, 0.672501, 0.047413),
        (-0.011820, 0.042940, 0.968881),
    ),
    "tritanopia": (
        (1.255528, -0.076749, -0.178779),
        (-0.078411, 0.930809, 0.147602),
        (0.004733, 0.691367, 0.303900),
    ),
}
VISIONS = ("normal", *DICHROMACIES)
# An accent whose CIELAB chroma is under this is a neutral, and tells from the ink by its distance.
ACCENT_NEUTRAL_CHROMA = 10.0
# Each palette is made for some vision, and the distance is held to 20 where it is made for it:
# the status colours of today for normal vision, the red-green palette for protanopia and
# deuteranopia, the blue-yellow palette for tritanopia, and each of them for normal vision too.
ACCENT_CLAIMED = {
    ("default", "normal"),
    ("red-green", "normal"),
    ("red-green", "protanopia"),
    ("red-green", "deuteranopia"),
    ("blue-yellow", "normal"),
    ("blue-yellow", "tritanopia"),
}

_RGBA = re.compile(r"#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?\Z")
LAB_WHITE_X, LAB_WHITE_Z = 0.95047, 1.08883
LAB_EPSILON, LAB_KAPPA = 216.0 / 24389.0, 24389.0 / 27.0


def parse_rgba(value):
    """A colour `#RRGGBB` or `#RRGGBBAA`, as the tokens write them, as its sRGB floats and alpha."""
    found = _RGBA.match(value) if isinstance(value, str) else None
    if found is None:
        raise ValueError(f"{value!r} is not a #RRGGBB or #RRGGBBAA colour")
    alpha = int(found[2], 16) / 255 if found[2] else 1.0
    return parse_hex("#" + found[1]), alpha


def over(foreground, background):
    """The colour `foreground` (`#RRGGBB` or `#RRGGBBAA`) over the opaque `background`, as sRGB
    floats, mixed in the encoded values as the platform composites.

    The result is whole 8 bit values, as it is in Compose, whose sRGB colours hold 8 bits to a
    channel and round half up: `HighContrastColorsTest` measures the colour that this gives.
    """
    rgb, alpha = parse_rgba(foreground)
    mixed = alpha * rgb + (1 - alpha) * np.asarray(background, dtype=np.float64)
    return np.floor(mixed * 255 + 0.5) / 255


def rgb_contrast(first, second):
    """The WCAG contrast ratio of two opaque colours given as sRGB floats."""
    return float(contrast_ratio(relative_luminance(first), relative_luminance(second)))


def seen_by(rgb, kind):
    """An sRGB colour as someone with the dichromacy `kind` sees it, to 8 bits a channel.

    This is `seenBy` of `ColourMeasures.kt`: the matrix acts on the linear values, which are clipped
    to 0 to 1, encoded and rounded half up.
    """
    linear = srgb_to_linear(np.asarray(rgb, np.float64))
    seen = np.clip(np.asarray(DICHROMACIES[kind]) @ linear, 0.0, 1.0)
    encoded = np.where(seen <= 0.0031308, seen * 12.92, 1.055 * seen ** (1 / 2.4) - 0.055)
    return np.floor(encoded * 255 + 0.5) / 255


def srgb_to_lab(rgb):
    """CIELAB under D65, as a tuple of the lightness and a and b, of an sRGB colour of floats."""
    r, g, b = srgb_to_linear(np.asarray(rgb, np.float64))
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / LAB_WHITE_X
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / LAB_WHITE_Z

    def f(t):
        return math.cbrt(t) if t > LAB_EPSILON else (LAB_KAPPA * t + 16) / 116

    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def _hue(b, a):
    return 0.0 if a == 0 and b == 0 else (math.degrees(math.atan2(b, a)) + 360) % 360


def _hue_difference(c1, c2, h1, h2):
    if c1 * c2 == 0:
        return 0.0
    difference = h2 - h1
    if abs(difference) <= 180:
        return difference
    return difference - 360 if difference > 180 else difference + 360


def _mean_hue(c1, c2, h1, h2):
    if c1 * c2 == 0:
        return h1 + h2
    if abs(h1 - h2) <= 180:
        return (h1 + h2) / 2
    return (h1 + h2 + 360) / 2 if h1 + h2 < 360 else (h1 + h2 - 360) / 2


def delta_e2000(lab1, lab2):
    """The colour difference CIEDE2000 of two colours given in CIELAB (Sharma, Wu and Dalal, 2005).

    2 or so is the least that is seen. It is `deltaE2000` of `ColourMeasures.kt`.
    """
    (l1, a1, b1), (l2, a2, b2) = lab1, lab2
    mean_chroma = (math.hypot(a1, b1) + math.hypot(a2, b2)) / 2
    g = 0.5 * (1 - math.sqrt(mean_chroma**7 / (mean_chroma**7 + 25.0**7)))
    a1p, a2p = (1 + g) * a1, (1 + g) * a2
    c1, c2 = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1, h2 = _hue(b1, a1p), _hue(b2, a2p)
    d_light, d_chroma = l2 - l1, c2 - c1
    d_hue = 2 * math.sqrt(c1 * c2) * math.sin(math.radians(_hue_difference(c1, c2, h1, h2) / 2))
    mean_light, mean_c, mean_h = (l1 + l2) / 2, (c1 + c2) / 2, _mean_hue(c1, c2, h1, h2)
    t = (
        1
        - 0.17 * math.cos(math.radians(mean_h - 30))
        + 0.24 * math.cos(math.radians(2 * mean_h))
        + 0.32 * math.cos(math.radians(3 * mean_h + 6))
        - 0.20 * math.cos(math.radians(4 * mean_h - 63))
    )
    rotation = (
        -math.sin(math.radians(2 * 30 * math.exp(-(((mean_h - 275) / 25) ** 2))))
        * 2
        * math.sqrt(mean_c**7 / (mean_c**7 + 25.0**7))
    )
    sl = 1 + 0.015 * (mean_light - 50) ** 2 / math.sqrt(20 + (mean_light - 50) ** 2)
    sc = 1 + 0.045 * mean_c
    sh = 1 + 0.015 * mean_c * t
    return math.sqrt(
        (d_light / sl) ** 2
        + (d_chroma / sc) ** 2
        + (d_hue / sh) ** 2
        + rotation * (d_chroma / sc) * (d_hue / sh)
    )


def colour_distance(first, second, kind=None):
    """CIEDE2000 of two `#RRGGBB` colours, as someone with the dichromacy `kind` sees them, or as
    anyone does when `kind` is None."""
    colours = [parse_hex(value) for value in (first, second)]
    if kind is not None:
        colours = [seen_by(colour, kind) for colour in colours]
    return delta_e2000(*(srgb_to_lab(colour) for colour in colours))


def _check_accent_candidate(name, found):
    """A candidate of `accents.json` made valid: every role there, every colour well formed, the
    alpha only where it is drawn with one, and a note on how each value was derived."""
    candidate = {
        "label": found.get("label", name),
        "source": found.get("source", ""),
        "color": found.get("color", {}),
        "high-contrast": found.get("high-contrast", {}),
        "notes": found.get("notes", {}),
        "cannot_pass": list(found.get("cannot_pass", [])),
    }
    sections = (
        ("color", "", ACCENT_ROLES, ACCENT_ALPHA_ROLES),
        ("high-contrast", "high-contrast.", ACCENT_HIGH_CONTRAST_ROLES, ()),
    )
    for section, prefix, roles, with_alpha in sections:
        missing = [role for role in roles if role not in candidate[section]]
        if missing:
            raise ValueError(f"{name}: {section} has no {', '.join(missing)}")
        for role in roles:
            value = candidate[section][role]
            try:
                parse_rgba(value)
            except ValueError:
                raise ValueError(
                    f"{name}: {prefix}{role} is not a #RRGGBB colour: {value!r}"
                ) from None
            if role not in with_alpha and len(value) != 7:
                raise ValueError(
                    f"{name}: {prefix}{role} must be an opaque #RRGGBB colour: {value}"
                )
            if f"{prefix}{role}" not in candidate["notes"]:
                raise ValueError(f"{name}: {prefix}{role} has no note on how it was derived")
    return candidate


def load_accents(path=ACCENTS_JSON):
    """The candidates of `accents.json`, by name, each checked (`_check_accent_candidate`)."""
    found = json.loads(Path(path).read_text(encoding="utf-8")).get("candidates") or {}
    if not found:
        raise ValueError(f"{path} has no candidate")
    return {name: _check_accent_candidate(name, entry) for name, entry in found.items()}


def accent_context(tokens=TOKENS_JSON):
    """The colours of the token file that the accents are checked against: the neutrals, and the
    status colours of the default and of the two colour-blind palettes."""
    data = json.loads(Path(tokens).read_text(encoding="utf-8"))
    colors = load_colors(tokens, ACCENT_NEUTRALS)
    status = {"default": {name: data["color"][name]["$value"] for name in STATUS_COLOURS}}
    for palette in STATUS_PALETTES[1:]:
        status[palette] = {
            name: data["colour-vision"][palette][name]["$value"] for name in STATUS_COLOURS
        }
    return {"colors": colors, "status": status}


def _backdrop_frame(frame, device, surface, overlays):
    """The luminance of the brightest pixel of each glass panel over one frame, plain and with the
    tint of each candidate laid on it: `{(blur_dp, alpha, overlay): luminance}`.

    A panel is the frame blurred and the `surface` colour over it at an alpha, and an overlay is
    `(rgb, alpha)`, which is laid over the panel as the platform composites the tint of a selected
    row. The text is lighter than every backdrop, so its lowest ratio is over the brightest pixel.
    """
    if not isinstance(frame, np.ndarray):
        frame = load_frame(frame)
    density = frame_density(frame.shape[1], device)
    blurs = sorted({blur for blur, _ in ACCENT_PANELS})
    images = dict(zip(blurs, gaussian_blurs(frame, [blur_sigma(dp * density) for dp in blurs])))
    found = {}
    for blur, alpha in ACCENT_PANELS:
        panel = composite(images[blur], surface, alpha)
        found[(blur, alpha, None)] = float(relative_luminance(panel).max())
        for name, (rgb, tint_alpha) in overlays.items():
            laid = composite(panel, rgb, tint_alpha)
            found[(blur, alpha, name)] = float(relative_luminance(laid).max())
    return found


def measure_backdrops(sources, candidates, context, jobs=1):
    """The brightest pixel behind each glass, for every style and device in `sources` (which maps
    (style, device) to its frames), plain and with the accent tint of each candidate on it.

    A row is `{style, device, blur_dp, alpha, overlay, luminance}`, with the highest luminance over
    the frames of the source. `overlay` is None for the glass alone, else the name of a candidate.
    """
    surface = parse_hex(context["colors"][GLASS_TINT])
    overlays = {
        name: parse_rgba(found["color"]["accent-tint"]) for name, found in candidates.items()
    }
    rows = []
    for (style, device), frames in sorted(sources.items()):
        measured = _map_frames(_backdrop_frame, frames, jobs, device, surface, overlays)
        for blur, alpha in ACCENT_PANELS:
            for overlay in (None, *overlays):
                rows.append(
                    {
                        "style": style,
                        "device": device,
                        "blur_dp": blur,
                        "alpha": alpha,
                        "overlay": overlay,
                        "luminance": max(m[(blur, alpha, overlay)] for m in measured),
                    }
                )
    return rows


def _worst_backdrops(rows):
    """The rows folded over the devices to the brightest: `{(style, blur, alpha, overlay): L}`."""
    worst = {}
    for row in rows:
        key = (row["style"], row["blur_dp"], row["alpha"], row["overlay"])
        worst[key] = max(worst.get(key, 0.0), row["luminance"])
    return worst


def _luminance(value):
    return float(relative_luminance(parse_hex(value)))


def _accent_fills(roles, colors):
    """The checks on the opaque fills: the label on the accent, and the accent and accent-soft as
    text on the surfaces and on the tint of a selected row."""
    fills = [
        {
            "id": "on-accent/accent",
            "label": "`on-accent` on `accent`",
            "ratio": rgb_contrast(parse_hex(roles["on-accent"]), parse_hex(roles["accent"])),
            "limit": ACCENT_TEXT,
        }
    ]
    for role in ("accent", "accent-soft"):
        text = parse_hex(roles[role])
        for surface in SURFACE_TOKENS:
            fills.append(
                {
                    "id": f"{role}/{surface}",
                    "label": f"`{role}` on `{surface}`",
                    "ratio": rgb_contrast(text, parse_hex(colors[surface])),
                    "limit": ACCENT_TEXT,
                }
            )
        for surface in ("surface", "surface-raised"):
            tint = over(roles["accent-tint"], parse_hex(colors[surface]))
            fills.append(
                {
                    "id": f"{role}/tint-on-{surface}",
                    "label": f"`{role}` on the tint over `{surface}`",
                    "ratio": rgb_contrast(text, tint),
                    "limit": ACCENT_TEXT,
                }
            )
    strong = parse_hex(roles["accent-strong"])
    for surface in SURFACE_TOKENS:
        fills.append(
            {
                "id": f"accent-strong/{surface}",
                "label": f"`accent-strong` on `{surface}` (information)",
                "ratio": rgb_contrast(strong, parse_hex(colors[surface])),
                "limit": None,
            }
        )
    return fills


def _grid_alpha(cell, tint):
    """The tint of the grid that a tint is read as: the next one below it, which is the brighter
    backdrop, as `backdropLuminance` of the app reads it."""
    alphas = sorted({alpha for _, alpha in cell})
    below = [alpha for alpha in alphas if alpha <= tint + 1e-9]
    return below[-1] if below else alphas[0]


def _accent_glass(name, roles, colors, worst):
    """The rows over the glass of each style at the default and at Reduced, the floors, and the
    failures. The default is the tint of Settings raised to the floor of the style, which is worked
    out with the accent-soft of the candidate, as it is one of the colours that the floor keeps."""
    floor_colors = {**colors, "accent-soft": roles["accent-soft"]}
    text = {role: _luminance(roles[role]) for role in ("accent", "accent-soft")}
    rows, floors, fails = [], {}, []
    for style in STYLE_LABELS:
        cell = {
            (blur, alpha): value
            for (found, blur, alpha, overlay), value in worst.items()
            if found == style and overlay is None
        }
        if not cell:
            continue
        floors[style] = glass_floor(cell, GLASS_DEFAULT[1], floor_colors)
        specs = {
            "standard": (max(round(GLASS_DEFAULT[0] * 100), floors[style]), GLASS_DEFAULT[1]),
            "reduced": (round(GLASS_REDUCED[0] * 100), GLASS_REDUCED[1]),
        }
        for spec, (tint, blur) in specs.items():
            column = {key: value for key, value in cell.items() if key[0] == blur}
            if not column:
                continue
            alpha = _grid_alpha(column, tint / 100)
            backdrop = column[(blur, alpha)]
            laid = worst.get((style, blur, alpha, name))
            row = {
                "style": style,
                "spec": spec,
                "tint": tint,
                "backdrop": backdrop,
                "accent": float(contrast_ratio(text["accent"], backdrop)),
                "accent-soft": float(contrast_ratio(text["accent-soft"], backdrop)),
                "selected": None if laid is None else float(contrast_ratio(text["accent"], laid)),
            }
            rows.append(row)
            where = f"glass:{spec}:{style}"
            for role in ("accent", "accent-soft"):
                if row[role] < ACCENT_TEXT:
                    fails.append(f"{where}:{role}-text")
                if row[role] < ACCENT_OUTLINE:
                    fails.append(f"{where}:{role}-outline")
            if row["selected"] is not None and row["selected"] < ACCENT_TEXT:
                fails.append(f"{where}:selected")
    return rows, floors, fails


def _accent_high_contrast(roles, high, colors):
    """The checks of high contrast: text at 7:1 and borders at 3:1 over every fill that text lies
    on, which are the surfaces, the tint of a selected row on them and the hover on the raised one.
    """
    surfaces = {name: parse_hex(colors[name]) for name in ACCENT_FILLS}
    fills = dict(surfaces)
    for surface in ("surface", "surface-raised"):
        fills[f"selected row on {surface}"] = over(roles["accent-tint"], surfaces[surface])
    fills["hover on surface-raised"] = over(colors["hover"], surfaces["surface-raised"])

    def lowest(role, limit):
        colour = parse_hex(high[role])
        where = min(fills, key=lambda fill: rgb_contrast(colour, fills[fill]))
        return {
            "id": f"hc:{role}",
            "label": f"high contrast `{role}`",
            "ratio": rgb_contrast(colour, fills[where]),
            "limit": limit,
            "on": where,
        }

    return [
        lowest("accent", ACCENT_HIGH_CONTRAST_TEXT),
        lowest("accent-soft", ACCENT_HIGH_CONTRAST_TEXT),
        lowest("accent-line", ACCENT_HIGH_CONTRAST_BORDER),
        {
            "id": "hc:on-accent/accent",
            "label": "`on-accent` on high contrast `accent`",
            "ratio": rgb_contrast(parse_hex(roles["on-accent"]), parse_hex(high["accent"])),
            "limit": ACCENT_HIGH_CONTRAST_TEXT,
            "on": "accent",
        },
    ]


def _accent_distance(accent, context):
    """The least CIEDE2000 distance of the accent from the status colours of each palette as each
    vision sees them, and from `ink` and `ink-2`, which only a neutral accent has to keep (an accent
    with chroma has a hue to tell it by, and the distance is for information)."""
    found = []
    for palette, colours in context["status"].items():
        for vision in VISIONS:
            kind = None if vision == "normal" else vision
            apart = {status: colour_distance(accent, hue, kind) for status, hue in colours.items()}
            nearest = min(apart, key=apart.get)
            found.append(
                {
                    "id": f"distance:{palette}:{vision}",
                    "label": f"`accent` from the {palette} status colours, {vision} vision",
                    "palette": palette,
                    "vision": vision,
                    "least": apart[nearest],
                    "nearest": nearest,
                    "limit": ACCENT_DISTANCE,
                    "relevant": (palette, vision) in ACCENT_CLAIMED,
                }
            )
    _, a, b = srgb_to_lab(parse_hex(accent))
    neutral = math.hypot(a, b) < ACCENT_NEUTRAL_CHROMA
    for ink in ("ink", "ink-2"):
        found.append(
            {
                "id": f"distance:{ink}",
                "label": f"`accent` from `{ink}`, normal vision",
                "palette": None,
                "vision": "normal",
                "least": colour_distance(accent, context["colors"][ink]),
                "nearest": ink,
                "limit": ACCENT_DISTANCE,
                "relevant": neutral,
            }
        )
    return found


def accent_report(candidates, context, rows):
    """For each candidate (`{name: candidate}`), the checks that issue #50 asks for.

    `rows` are those of `measure_backdrops`, and may be empty: the checks over the glass are then
    left out. Each check is a dict with an `id`, a `label`, a ratio (or a `least` distance) and its
    `limit`; `fails` lists the ids of the checks that are short of their limit, in the order of
    fills, glass, high contrast and distance.
    """
    worst = _worst_backdrops(rows)
    colors = context["colors"]
    report = {}
    for name, found in candidates.items():
        roles, high = found["color"], found["high-contrast"]
        fills = _accent_fills(roles, colors)
        glass, floors, glass_fails = _accent_glass(name, roles, colors, worst)
        hc = _accent_high_contrast(roles, high, colors)
        distance = _accent_distance(roles["accent"], context)
        fails = [c["id"] for c in fills if c["limit"] is not None and c["ratio"] < c["limit"]]
        fails += glass_fails
        fails += [c["id"] for c in hc if c["ratio"] < c["limit"]]
        fails += [c["id"] for c in distance if c["relevant"] and c["least"] < c["limit"]]
        report[name] = {
            "label": found["label"],
            "roles": {"color": roles, "high-contrast": high},
            "fills": fills,
            "glass": glass,
            "floors": floors,
            "high_contrast": hc,
            "distance": distance,
            "fails": fails,
        }
    return report


def measure_accents(root, candidates, context, jobs=1):
    """The accents over the frames in `root/<Style>-<device>/*.png`: the rows of the backdrops and
    the report of each candidate, as plain data."""
    sources = _folder_sources(root)
    rows = measure_backdrops(sources, candidates, context, jobs)
    return {
        "meta": {
            "statistic": (
                "The luminance of the brightest pixel behind the text of a glass panel, over every "
                "frame of each source and then over both devices: the frame blurred, the surface "
                "colour on it at the tint, and for the selected label the accent tint on that. The "
                "ratio of the accent over it is the lowest of the text on that glass. The tint of "
                "the default is that of Settings, 40 %, raised to the floor of the style."
            ),
            "colors": context["colors"],
            "status": context["status"],
            "glass_tint": GLASS_TINT,
            "glass_default": {"tint_percent": round(GLASS_DEFAULT[0] * 100), "blur_dp": 8},
            "glass_reduced": {"tint_percent": round(GLASS_REDUCED[0] * 100), "blur_dp": 48},
            "limits": {
                "text": ACCENT_TEXT,
                "outline": ACCENT_OUTLINE,
                "high_contrast_text": ACCENT_HIGH_CONTRAST_TEXT,
                "high_contrast_border": ACCENT_HIGH_CONTRAST_BORDER,
                "distance": ACCENT_DISTANCE,
            },
        },
        "sources": [
            {"style": style, "device": device, "frames": len(frames)}
            for (style, device), frames in sorted(sources.items())
        ],
        "backdrops": rows,
        "candidates": accent_report(candidates, context, rows),
    }


def _ratio_cell(ratio, text=ACCENT_TEXT, outline=None):
    """A ratio as a cell: with a word when it is short of its limit, and, if there is a second and
    lower limit for an outline, when it is only above that."""
    if ratio is None:
        return "-"
    cell = format_ratio(ratio)
    if ratio >= text:
        return cell
    if outline is not None and ratio >= outline:
        return f"{cell} (outline only)"
    return f"{cell} (short)"


def _glass_cells(reports, names, row, cell):
    """The cells of a line of a glass table: `cell` of the glass of each candidate that is the style
    and the glass of `row`."""
    where = (row["style"], row["spec"])
    return [
        cell(next(g for g in reports[name]["glass"] if (g["style"], g["spec"]) == where))
        for name in names
    ]


def _glass_table(reports, names, cell):
    """A table over the glass: a line for each style and glass, a column for each candidate."""
    lines = [
        [STYLE_LABELS[row["style"]], row["spec"].capitalize()]
        + _glass_cells(reports, names, row, cell)
        for row in reports[names[0]]["glass"]
    ]
    return _table(["Style", "Glass"] + [reports[name]["label"] for name in names], lines)


def _glass_ratio(key, outline):
    """The cell of a glass table for the ratio `key` of a row, with the limit of an outline."""
    return lambda row: _ratio_cell(row[key], ACCENT_TEXT, outline)


def _limit_label(label, limit):
    return label if limit is None else f"{label}, {format_ratio(limit)}:1"


def accent_tables(result):
    """The tables of the accent candidates for `docs/ux/accessibility.md`, from a result.

    N is the role sets, O the checks on the opaque fills, P the glass of each candidate (its tint
    and the brightest pixel), Q and R the accent and the accent-soft over the glass, S the selected
    label over the glass, T high contrast and U the distances. A line for each candidate says which
    checks it fails.
    """
    reports = result["candidates"]
    names = list(reports)
    heads = [reports[name]["label"] for name in names]
    sections = []

    def add(title, header, lines):
        sections.extend([title, _table(header, lines)])

    roles = []
    for section, prefix in (("color", ""), ("high-contrast", "high contrast ")):
        for role in reports[names[0]]["roles"][section]:
            cells = [f"`{reports[name]['roles'][section][role]}`" for name in names]
            roles.append([f"{prefix}`{role}`"] + cells)
    add("Table N. The role sets of the candidates.", ["Role"] + heads, roles)

    lines = []
    for index, fill in enumerate(reports[names[0]]["fills"]):
        found = [reports[name]["fills"][index] for name in names]
        cells = [_ratio_cell(one["ratio"], one["limit"] or 0.0) for one in found]
        lines.append([_limit_label(fill["label"], fill["limit"])] + cells)
    add(
        "Table O. Text on the opaque fills: the contrast ratio, and the label on an accent fill. "
        "The limit of each is in its first column.",
        ["Check"] + heads,
        lines,
    )

    if any(reports[name]["glass"] for name in names):
        sections.append(
            "Table P. The glass: its tint, which is the 40 % of Settings raised to the floor of "
            "the style for the accent-soft of the candidate (85 % at Reduced), and the luminance "
            "of the brightest pixel behind the text over every frame of both devices."
        )
        sections.append(
            _glass_table(reports, names, lambda g: f"{g['tint']} %, {g['backdrop']:.4f}")
        )
        for title, key, outline in (
            ("Table Q. `accent` over the glass: text needs 4.5:1, an outline 3:1.", "accent", 3.0),
            ("Table R. `accent-soft` over the glass: text 4.5:1, outline 3:1.", "accent-soft", 3.0),
            ("Table S. The selected label, `accent` over its tint on the glass.", "selected", None),
        ):
            sections.append(title)
            sections.append(_glass_table(reports, names, _glass_ratio(key, outline)))

    lines = []
    for index, check in enumerate(reports[names[0]]["high_contrast"]):
        found = [reports[name]["high_contrast"][index] for name in names]
        cells = [f"{_ratio_cell(one['ratio'], one['limit'])} ({one['on']})" for one in found]
        lines.append([_limit_label(check["label"], check["limit"])] + cells)
    add(
        "Table T. High contrast: the lowest ratio over the fills that text lies on, the surfaces, "
        "the tint of a selected row on them and the hover, and the fill it is on. Text needs 7:1 "
        "and a border 3:1.",
        ["Check"] + heads,
        lines,
    )

    def distance_cell(one):
        if not one["relevant"]:
            note = " (not claimed)"
        else:
            note = " (short)" if one["least"] < one["limit"] else ""
        return f"{one['least']:.1f}, {one['nearest']}{note}"

    lines = []
    for index, check in enumerate(reports[names[0]]["distance"]):
        found = [reports[name]["distance"][index] for name in names]
        lines.append([check["label"]] + [distance_cell(one) for one in found])
    add(
        "Table U. The least CIEDE2000 distance of `accent` from the status colours as each vision "
        "sees them, and from the ink. 20 or more apart is distinct. A row that its palette is not "
        "for is given for information.",
        ["Check"] + heads,
        lines,
    )

    for name in names:
        label, fails = reports[name]["label"], reports[name]["fails"]
        sections.append(
            f"{label} fails no check." if not fails else f"{label} fails: {', '.join(fails)}."
        )
    return "\n\n".join(sections)


# The command line


def _revision():
    try:
        return subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=REPO,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _say(log, message):
    """`message` on the stream `log`, which is the standard error unless a caller gives another."""
    print(message, file=sys.stderr if log is None else log)


def _save_result(result, args):
    """Puts the date and the revision in the `meta` of a result and writes it to `args.json`, if
    there is one: `accents` has it only when it is asked for."""
    result["meta"]["date"] = datetime.date.today().isoformat()
    result["meta"]["revision"] = args.revision or _revision()
    if args.json is None:
        return
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(result, indent=1) + "\n", encoding="utf-8")


def _run_extract(args, log):
    extract_clips(args.frames, args.clips, args.count, log)
    return 0


def _run_measure(args, log):
    colors = load_colors(args.tokens, TEXT_TOKENS + SURFACE_TOKENS)
    result = measure_folder(args.frames, colors, args.jobs)
    _save_result(result, args)
    print(markdown_tables(result))
    return 0


def _run_glass_table(args, log):
    result = json.loads(args.result.read_text(encoding="utf-8"))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    # Written as bytes, so the file has unix line ends on every platform.
    args.out.write_bytes(glass_table_kotlin(result).encode("utf-8"))
    _say(log, f"wrote {args.out}")
    return 0


def _run_glass_colours(args, log):
    table = read_glass_table_kotlin(args.table.read_text(encoding="utf-8"))
    print(glass_colour_tables(table))
    return 0


def _run_halo(args, log):
    colors = load_colors(args.tokens, TEXT_TOKENS + SURFACE_TOKENS)
    dim = 0.0
    if args.high_contrast:
        found = load_high_contrast(args.tokens)
        colors = {**colors, "ink": found["ink"], "ink-2": found["ink-2"]}
        dim = found["dim"]
    texts = list(HALO_ICONS) if args.icons else args.texts
    fonts = args.font_set
    result = measure_halo_folder(args.frames, colors, args.jobs, texts, dim, args.tokens, fonts)
    _save_result(result, args)
    if fonts != PLEX_SANS:
        files = ", ".join(Path(face.path).name for face in (fonts.regular, fonts.semibold))
        print(f"Font: {fonts.name} ({files}), x-height scale {fonts.scale:.3f}.\n")
    if args.halo is None:
        print(halo_tables(result))
    else:
        stroke, blur, percent = args.halo
        config = (int(stroke), int(blur), round(percent / 100, 2))
        print((halo_icon_tables if args.icons else halo_high_contrast_tables)(result, config))
    return 0


def _run_accents(args, log):
    result = measure_accents(args.frames, args.candidates, args.context, args.jobs)
    _save_result(result, args)
    print(accent_tables(result))
    return 0


COMMANDS = {
    "extract": _run_extract,
    "measure": _run_measure,
    "glass-table": _run_glass_table,
    "glass-colours": _run_glass_colours,
    "halo": _run_halo,
    "accents": _run_accents,
}


def _add_measuring_arguments(command):
    """The arguments of the two commands that measure a folder of frames."""
    command.add_argument("frames", type=Path, help="the folder of <Style>-<tv|phone>/NN.png")
    command.add_argument("--json", type=Path, required=True, help="where the result goes")
    command.add_argument("--tokens", type=Path, default=TOKENS_JSON, help="the design tokens")
    command.add_argument("--jobs", type=int, default=4, help="processes, one frame each")
    command.add_argument("--revision", help="the revision measured (default: HEAD)")


def _parser():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)

    extract = commands.add_parser("extract", help="write the frames of the four clips with ffmpeg")
    extract.add_argument("frames", type=Path, help="the folder the frames go to")
    extract.add_argument("--clips", type=Path, default=CLIPS_DIR, help="the folder of the clips")
    extract.add_argument("--count", type=int, default=FRAME_COUNT, help="frames of each clip")

    measure = commands.add_parser("measure", help="measure the frames in a folder")
    _add_measuring_arguments(measure)

    table = commands.add_parser("glass-table", help="write the Kotlin table of the glass")
    table.add_argument("result", type=Path, help="the JSON that measure wrote")
    table.add_argument("--out", type=Path, default=GLASS_TABLE_KT, help="the Kotlin file")

    colours = commands.add_parser("glass-colours", help="print the floors and colours of the glass")
    colours.add_argument("--table", type=Path, default=GLASS_TABLE_KT, help="the Kotlin table")

    halo = commands.add_parser("halo", help="measure the halo of text on the background")
    _add_measuring_arguments(halo)
    halo.add_argument("--texts", nargs="+", choices=sorted(HALO_TEXTS), help="the text styles")
    halo.add_argument("--icons", action="store_true", help="measure the icons, not the text")
    halo.add_argument("--high-contrast", action="store_true", help="measure under high contrast")
    halo.add_argument(
        "--halo",
        type=float,
        nargs=3,
        metavar=("STROKE_DP", "BLUR_DP", "OPACITY_PERCENT"),
        help="the halo to check under high contrast",
    )
    halo.add_argument("--font", type=Path, help="the regular face of the font (default: Plex Sans)")
    halo.add_argument("--font-semibold", type=Path, help="the semibold face, with --font")
    halo.add_argument("--fonts", type=Path, help="a JSON manifest of the font, for --font")

    accents = commands.add_parser("accents", help="measure the accent colour candidates")
    accents.add_argument("frames", type=Path, help="the folder of <Style>-<tv|phone>/NN.png")
    accents.add_argument("--accents", type=Path, default=ACCENTS_JSON, help="the candidates")
    accents.add_argument("--tokens", type=Path, default=TOKENS_JSON, help="the design tokens")
    accents.add_argument("--json", type=Path, help="where the result goes (default: nowhere)")
    accents.add_argument("--jobs", type=int, default=4, help="processes, one frame each")
    accents.add_argument("--revision", help="the revision measured (default: HEAD)")
    return parser


def _font_set(parser, args):
    """The font set that the options of `halo` name, IBM Plex Sans if they name none."""
    if args.fonts and (args.font or args.font_semibold):
        parser.error("--fonts and --font are not asked together")
    if bool(args.font) != bool(args.font_semibold):
        parser.error("--font and --font-semibold go together")
    if args.icons and (args.fonts or args.font):
        parser.error("--icons and a font are not asked together")
    try:
        if args.fonts:
            return read_font_manifest(args.fonts)
        if args.font:
            return font_set(args.font, args.font_semibold)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    return PLEX_SANS


def _check_halo(parser, args):
    if args.icons and args.texts:
        parser.error("--icons and --texts are not asked together")
    if (args.high_contrast or args.icons) != (args.halo is not None):
        parser.error("--high-contrast and --icons go with --halo: the halo to check is named")
    args.font_set = _font_set(parser, args)


def _check_accents(parser, args):
    """Reads the candidates and the colours that `accents` is asked to measure, or says why not."""
    try:
        args.candidates = load_accents(args.accents)
        args.context = accent_context(args.tokens)
    except (OSError, ValueError) as error:
        parser.error(str(error))


def main(argv=None, log=None):
    """Runs the command that `argv` names and returns its exit code.

    `log` takes the lines that a command says about what it wrote. They go to the standard error
    when no stream is given; a test gives its own, so that nothing is printed.
    """
    parser = _parser()
    args = parser.parse_args(argv)
    if args.command == "halo":
        _check_halo(parser, args)
    elif args.command == "accents":
        _check_accents(parser, args)
    return COMMANDS[args.command](args, log)


if __name__ == "__main__":
    sys.exit(main())
