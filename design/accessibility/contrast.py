"""Text contrast over the animated backgrounds, and over the glass panels planned for them.

Sprint 10 bases two choices on these numbers: the glass look of a panel and the text colours on
it. The tool measures WCAG 2.x contrast ratios as the worst case (the minimum) of each text
token over real frames of each background, in three cases:

- (a) the text straight on the background;
- (b) the text on one of today's opaque surfaces (`page`, `surface`, `surface-raised`);
- (c) the text on a glass panel: the background blurred, then the `surface` colour on top at an
  alpha, for a grid of alphas and blur radii (a radius of 0 is no blur, the glass of API 28 to 30).

The colours come from `design/tokens.json`. The frames come from two places: `extract` writes them
from the four clips with ffmpeg, and the `BackgroundFrameExport` test of `:borealis:designsystem`
writes the two drawn styles. `README.md` has the whole procedure.

    python -I contrast.py extract <frames> [--clips <dir>]
    python -I contrast.py measure <frames> --json <result.json> [--jobs N]
    python -I contrast.py glass-table <result.json> [--out <GlassContrastTable.kt>]
    python -I contrast.py glass-colours [--table <GlassContrastTable.kt>]

`glass-table` writes the table of the worst backdrop behind a glass panel that the app turns into
the tint floors and the text colours of the glass. `glass-colours` prints those floors and colours,
worked out from the table the app has, for `docs/ux/accessibility.md`.

The library has no state; the tests are in `test_contrast.py`.
"""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
import datetime
import functools
import json
import math
import re
import shutil
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

# Machado, Oliveira and Fernandes, "A Physiologically-based Model for Simulation of Color Vision
# Deficiency" (2009), severity 1.0. They act on linear RGB. A row adds up to 1, so white and the
# greys stay as they are.
CVD_MATRICES = {
    "protanopia": np.array(
        [
            [0.152286, 1.052583, -0.204868],
            [0.114503, 0.786281, 0.099216],
            [-0.003882, -0.048116, 1.051998],
        ]
    ),
    "deuteranopia": np.array(
        [
            [0.367322, 0.860646, -0.227968],
            [0.280085, 0.672501, 0.047413],
            [-0.011820, 0.042940, 0.968881],
        ]
    ),
    "tritanopia": np.array(
        [
            [1.255528, -0.076749, -0.178779],
            [-0.078411, 0.930809, 0.147602],
            [0.004733, 0.691367, 0.303900],
        ]
    ),
}


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


def linear_to_srgb(c):
    c = np.clip(_floats(c), 0.0, 1.0)
    power = 1.055 * np.maximum(c, 0.0031308) ** (1 / 2.4) - 0.055
    return np.where(c <= 0.0031308, c * 12.92, power)


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
    """`tint` over `image` at `alpha`, mixed in the sRGB values as the platform composites."""
    return alpha * np.asarray(tint, dtype=np.float32) + (1 - alpha) * np.asarray(
        image, dtype=np.float32
    )


def glass(image, tint, alpha, radius_px):
    """A glass panel over `image`: the image blurred by `radius_px`, the tint on it at `alpha`."""
    return composite(gaussian_blur(image, blur_sigma(radius_px)), tint, alpha)


# Colour-vision simulation


def simulate_cvd(rgb, kind):
    """sRGB colours (0 to 1, along the last axis) as someone with `kind` of dichromacy sees them."""
    linear = srgb_to_linear(rgb)
    seen = np.clip(linear @ CVD_MATRICES[kind].T, 0.0, 1.0)
    return linear_to_srgb(seen)


def simulate_hex(value, kind):
    seen = np.rint(simulate_cvd(parse_hex(value), kind) * 255).astype(int)
    return "#" + "".join(f"{channel:02X}" for channel in seen)


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


def extract_clips(frames, clips, count=FRAME_COUNT):
    """Writes `count` frames of each clip, spread over its loop, to `frames/<Style>-<device>/`.

    A TV shows the whole clip. A phone upright shows a slice of the middle of it, scaled up
    about 2.5 times, so a blur of a given size in dp reaches less far into the clip there.
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
            print(f"{clip.name}: {len(list(out.glob('*.png')))} frames in {out}", file=sys.stderr)


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
    if jobs > 1:
        with concurrent.futures.ProcessPoolExecutor(max_workers=jobs) as pool:
            measured = list(
                pool.map(_measure_frame, frames, [device] * len(frames), [colors] * len(frames))
            )
    else:
        measured = [_measure_frame(frame, device, colors) for frame in frames]
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
}
"""


def glass_table_kotlin(result):
    """The Kotlin file `GlassContrastTable.kt` for a measurement: the table, row by row.

    A row of a style is one blur of the grid and holds a value for each tint of the grid. The
    floors and the text colours are worked out in Kotlin from it.
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
    return (
        KOTLIN_TEMPLATE.replace("@SOURCE@", f"{date} at {revision}")
        .replace("@PACKAGE@", KOTLIN_PACKAGE)
        .replace("@TINTS@", ", ".join(str(round(alpha * 100)) for alpha in alphas))
        .replace("@BLURS@", ", ".join(str(blur) for blur in blurs))
        .replace("@ENTRIES@", "\n".join(entries))
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

# The text styles of the app that stand on the background, with the size token of each device. They
# are the styles of `borealisTypography`: the title is `titleLarge`, the label is the kicker (its
# capitals set 0.07 em apart), the body is `bodyMedium` and the small text is `bodySmall`. Large
# text is 24 sp regular or 18.66 sp bold and over: the title of a TV is, the title of a phone (18 sp
# semibold) is not.
HALO_TEXTS = {
    "title": {
        "sizes": {"tv": ("tv", "title"), "phone": ("phone", "title")},
        "font": "ibm_plex_sans_semibold",
        "tracking": 0.0,
        "upper": False,
        "large": ("tv",),
    },
    "label": {
        "sizes": {"tv": ("tv", "minimum"), "phone": ("phone", "kicker")},
        "font": "ibm_plex_sans_semibold",
        "tracking": 0.07,
        "upper": True,
        "large": (),
    },
    "body": {
        "sizes": {"tv": ("tv", "body"), "phone": ("phone", "body")},
        "font": "ibm_plex_sans_regular",
        "tracking": 0.0,
        "upper": False,
        "large": (),
    },
    "small": {
        "sizes": {"tv": ("tv", "rail"), "phone": ("phone", "secondary")},
        "font": "ibm_plex_sans_regular",
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
# one string or several added together.
_KT_STRING = re.compile(r'"([^"]*)"')
_KT_CONSTANT = re.compile(r'const val (\w+) =\s*((?:"[^"]*"\s*\+?\s*)+)')
_KT_STROKE = re.compile(r"const val STROKE = ([\d.]+)f")
_KT_ICON = re.compile(r'val (\w+): ImageVector by lazy \{ icon\("\w+",(.*?)\) \}', re.DOTALL)
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


@functools.lru_cache(maxsize=None)
def _font(name, size):
    return ImageFont.truetype(
        str(FONTS_DIR / f"{name}.ttf"), size, layout_engine=ImageFont.Layout.BASIC
    )


# The sample text is set at this many times its size, and what is wanted of it is cut from that:
# its coverage by averaging, and its widening by a stroke on the hard edge of the big picture.
# Android does not hint the text, so the big picture, whose hinting is a fourth of a pixel, is
# nearer it than a picture at the size of the text.
HALO_SUPERSAMPLE = 4


def _set_sample(text, device, pad):
    """The sample text of a style at `HALO_SUPERSAMPLE` times its size, as coverage, 0 to 1.

    The picture is that many times as large as the text with `pad` pixels round it. A label is in
    capitals, with its letters spaced.
    """
    if text in HALO_ICONS:
        return _set_icon(text, device, pad)
    style = HALO_TEXTS[text]
    size = text_size_px(text, device)
    scale = HALO_SUPERSAMPLE
    sample = HALO_SAMPLE.upper() if style["upper"] else HALO_SAMPLE
    ascent, descent = _font(style["font"], size).getmetrics()
    font = _font(style["font"], size * scale)
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


def glyph_mask(text, device, stroke_dp=0.0, pad=0):
    """The coverage, 0 to 1, of the sample text of a style, with each glyph widened by a stroke.

    The text is set in the font of the style at its size on the device. A label is in capitals, with
    its letters spaced. `stroke_dp` widens each glyph by that much on every side, as a fill under a
    stroke of twice the width does, with round joins. The picture is as large as the text with `pad`
    pixels on each side, whatever the stroke, so that masks of different strokes line up, and `pad`
    must be at least the stroke.
    """
    big = _set_sample(text, device, pad)
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
    alpha = np.asarray(alpha, dtype=np.float32)[..., None]
    return alpha * np.asarray(tint, dtype=np.float32) + (1 - alpha) * np.asarray(
        image, dtype=np.float32
    )


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
def halo_geometry(text, device):
    """The ring around the glyphs of a style and the alpha of each candidate on it.

    None of it depends on the frame. `ring` is the ring as a mask of its bounding box, `size` the
    (height, width) of that box and `alphas` has a row for each of `configs` (no halo first, then
    the grid) and a column for each pixel of the ring.
    """
    density = device_density(device)
    sigma = blur_sigma(max(HALO_BLURS_DP) * density)
    pad = math.ceil(max(HALO_STROKES_DP) * density + HALO_REACH_SIGMAS * sigma) + HALO_RING_PX + 1
    ring = ring_mask(glyph_mask(text, device, pad=pad))
    rows, columns = np.nonzero(ring)
    box = (slice(rows.min(), rows.max() + 1), slice(columns.min(), columns.max() + 1))
    ring = ring[box]
    configs = [HALO_NONE] + halo_configs()
    alphas = {HALO_NONE: np.zeros(int(ring.sum()), np.float32)}
    for stroke in HALO_STROKES_DP:
        widened = glyph_mask(text, device, stroke_dp=stroke, pad=pad)
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


def measure_halo_frame(frame, device, colors, texts=None, dim=0.0):
    """The stats of each text style, candidate and token over one frame.

    The worst case ("min") is over every colour of the frame with every alpha of the ring, which is
    every place that the text can take, as `halo_worst_ratio` says. p1 and p5 are for information:
    in each of the six by four places of the sample over the frame, the ratio of each token over the
    ring pixels with the halo on them, the ratio below which 1% and 5% of them are, and the lowest
    of those over the places. It returns `{text: {config: {token: stats}}}`, where `stats` has
    "min", "p1" and "p5". `frame` is an array or the path of a PNG. A `dim` is the veil of the page
    colour that high contrast lays over the frame, which the text is measured under.
    """
    if not isinstance(frame, np.ndarray):
        frame = load_frame(frame)
    tint = parse_hex(colors[HALO_TINT])
    frame = halo_veil(frame, tint, dim)
    lights = {token: float(relative_luminance(parse_hex(colors[token]))) for token in HALO_TOKENS}
    colours = frame_colours(frame)
    result = {}
    for text in texts or HALO_TEXTS:
        geometry = halo_geometry(text, device)
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


def measure_halo_source(frames, style, device, colors, jobs=1, texts=None, dim=0.0):
    """The rows of one background style on one device, over its frames.

    A row is a text style, a token and a candidate, with the lowest ratio over the frames ("min"),
    p1 and p5 (their lowest) and the number of the worst frame. The rows are plain data.
    """
    if jobs > 1:
        with concurrent.futures.ProcessPoolExecutor(max_workers=jobs) as pool:
            measured = list(
                pool.map(
                    measure_halo_frame,
                    frames,
                    [device] * len(frames),
                    [colors] * len(frames),
                    [texts] * len(frames),
                    [dim] * len(frames),
                )
            )
    else:
        measured = [measure_halo_frame(frame, device, colors, texts, dim) for frame in frames]
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


def measure_halo_sources(sources, colors, jobs=1, texts=None, dim=0.0):
    """The halo over `sources`, which maps (style, device) to its frames."""
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
            "tokens": list(HALO_TOKENS),
            "tint": HALO_TINT,
            "sample": HALO_SAMPLE,
            "texts": {
                name: {
                    "sizes_sp": {
                        device: _text_sizes(TOKENS_JSON)[size]
                        for device, size in style["sizes"].items()
                    },
                    "font": style["font"],
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
        result["halo"] += measure_halo_source(frames, style, device, colors, jobs, texts, dim)
    return result


def measure_halo_folder(root, colors, jobs=1, texts=None, dim=0.0):
    """Measures the halo over the frames in `root/<Style>-<device>/*.png`."""
    return measure_halo_sources(_folder_sources(root), colors, jobs, texts, dim)


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
        f"The choice: {halo_choice_text(choice)}.",
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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)

    extract = commands.add_parser("extract", help="write the frames of the four clips with ffmpeg")
    extract.add_argument("frames", type=Path, help="the folder the frames go to")
    extract.add_argument("--clips", type=Path, default=CLIPS_DIR, help="the folder of the clips")
    extract.add_argument("--count", type=int, default=FRAME_COUNT, help="frames of each clip")

    measure = commands.add_parser("measure", help="measure the frames in a folder")
    measure.add_argument("frames", type=Path, help="the folder of <Style>-<tv|phone>/NN.png")
    measure.add_argument("--json", type=Path, required=True, help="where the result goes")
    measure.add_argument("--tokens", type=Path, default=TOKENS_JSON, help="the design tokens")
    measure.add_argument("--jobs", type=int, default=4, help="processes, one frame each")
    measure.add_argument("--revision", help="the revision measured (default: HEAD)")

    table = commands.add_parser("glass-table", help="write the Kotlin table of the glass")
    table.add_argument("result", type=Path, help="the JSON that measure wrote")
    table.add_argument("--out", type=Path, default=GLASS_TABLE_KT, help="the Kotlin file")

    colours = commands.add_parser("glass-colours", help="print the floors and colours of the glass")
    colours.add_argument("--table", type=Path, default=GLASS_TABLE_KT, help="the Kotlin table")

    halo = commands.add_parser("halo", help="measure the halo of text on the background")
    halo.add_argument("frames", type=Path, help="the folder of <Style>-<tv|phone>/NN.png")
    halo.add_argument("--json", type=Path, required=True, help="where the result goes")
    halo.add_argument("--jobs", type=int, default=4, help="processes, one frame each")
    halo.add_argument("--texts", nargs="+", choices=sorted(HALO_TEXTS), help="the text styles")
    halo.add_argument("--revision", help="the revision measured (default: HEAD)")
    halo.add_argument("--icons", action="store_true", help="measure the icons, not the text")
    halo.add_argument("--high-contrast", action="store_true", help="measure under high contrast")
    halo.add_argument(
        "--halo",
        type=float,
        nargs=3,
        metavar=("STROKE_DP", "BLUR_DP", "OPACITY_PERCENT"),
        help="the halo to check under high contrast",
    )

    args = parser.parse_args(argv)
    if args.command == "halo":
        if args.icons and args.texts:
            parser.error("--icons and --texts are not asked together")
        if (args.high_contrast or args.icons) != (args.halo is not None):
            parser.error("--high-contrast and --icons go with --halo: the halo to check is named")
        if args.icons:
            args.texts = list(HALO_ICONS)
        colors = load_colors(TOKENS_JSON, TEXT_TOKENS + SURFACE_TOKENS)
        dim = 0.0
        if args.high_contrast:
            found = load_high_contrast()
            colors = {**colors, "ink": found["ink"], "ink-2": found["ink-2"]}
            dim = found["dim"]
        result = measure_halo_folder(args.frames, colors, args.jobs, args.texts, dim)
        result["meta"]["date"] = datetime.date.today().isoformat()
        result["meta"]["revision"] = args.revision or _revision()
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(result, indent=1) + "\n", encoding="utf-8")
        if args.halo is not None:
            stroke, blur, percent = args.halo
            config = (int(stroke), int(blur), round(percent / 100, 2))
            if args.icons:
                print(halo_icon_tables(result, config))
            else:
                print(halo_high_contrast_tables(result, config))
        else:
            print(halo_tables(result))
        return 0
    if args.command == "glass-colours":
        table = read_glass_table_kotlin(args.table.read_text(encoding="utf-8"))
        print(glass_colour_tables(table))
        return 0
    if args.command == "glass-table":
        result = json.loads(args.result.read_text(encoding="utf-8"))
        args.out.parent.mkdir(parents=True, exist_ok=True)
        # Written as bytes, so the file has unix line ends on every platform.
        args.out.write_bytes(glass_table_kotlin(result).encode("utf-8"))
        print(f"wrote {args.out}", file=sys.stderr)
        return 0
    if args.command == "extract":
        extract_clips(args.frames, args.clips, args.count)
        return 0

    colors = load_colors(args.tokens, TEXT_TOKENS + SURFACE_TOKENS)
    result = measure_folder(args.frames, colors, args.jobs)
    result["meta"]["date"] = datetime.date.today().isoformat()
    result["meta"]["revision"] = args.revision or _revision()
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(result, indent=1) + "\n", encoding="utf-8")
    print(markdown_tables(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
