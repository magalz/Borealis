"""Text contrast over the animated backgrounds, and over the glass panels planned for them.

Sprint 10 bases two choices on these numbers: the glass look of a panel and the fixes of the text
tokens. The tool measures WCAG 2.x contrast ratios as the worst case (the minimum) of each text
token over real frames of each background, in three cases:

- (a) the text straight on the background;
- (b) the text on one of today's opaque surfaces (`page`, `surface`, `surface-raised`);
- (c) the text on a glass panel: the background blurred, then the `surface` colour on top at an
  alpha, for a grid of alphas and blur radii.

The colours come from `design/tokens.json`. The frames come from two places: `extract` writes them
from the four clips with ffmpeg, and the `BackgroundFrameExport` test of `:borealis:designsystem`
writes the two drawn styles. `README.md` has the whole procedure.

    python -I contrast.py extract <frames> [--clips <dir>]
    python -I contrast.py measure <frames> --json <result.json> [--jobs N]

The library has no state; the tests are in `test_contrast.py`.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
TOKENS_JSON = REPO / "design" / "tokens.json"
CLIPS_DIR = REPO / "apps/android/borealis/designsystem/src/main/res/raw"

# The text tokens that are measured, and the surfaces they sit on today.
TEXT_TOKENS = ("ink", "ink-2", "ink-3", "ink-4", "accent-soft")
SURFACE_TOKENS = ("page", "surface", "surface-raised")

# The glass candidates: the tint, and the grid of its alpha and of the blur radius in dp.
GLASS_TINT = "surface"
GLASS_ALPHAS = (0.55, 0.70, 0.85)
GLASS_BLURS_DP = (16, 32, 48)

# How many frames of each loop, and the width in dp of the device a frame stands for. The density
# of a frame is its width in pixels over that: 1920 px of a TV is xhdpi (2.0), 1233 px of a phone
# is xxhdpi (3.0).
FRAME_COUNT = 24
DEVICE_WIDTH_DP = {"tv": 960.0, "phone": 411.0}
DEVICE_LABELS = {"tv": "TV", "phone": "phone"}

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


def extract_clips(frames, clips, count=FRAME_COUNT):
    """Writes `count` frames of each clip, spread over its loop, to `frames/<Style>-tv/NN.png`.

    The clips carry no colour tags. Android decodes an untagged HD stream as BT.709 in limited
    range, so ffmpeg is told to, or it would take BT.601 and shift the colours.
    """
    ffmpeg, ffprobe = _tool("ffmpeg"), _tool("ffprobe")
    for clip in sorted(Path(clips).glob("background_*.mp4")):
        style = CLIP_STYLES.get(clip.stem.removeprefix("background_"))
        if style is None:
            continue
        shown = subprocess.run(
            [ffprobe, "-v", "error", "-select_streams", "v:0", "-count_frames"]
            + ["-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", str(clip)],
            capture_output=True,
            text=True,
            check=True,
        )
        out = Path(frames) / f"{style}-tv"
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.png"):
            old.unlink()
        filters = select_filter(frame_indices(int(shown.stdout.strip()), count))
        filters += ",scale=in_color_matrix=bt709:in_range=tv:out_range=pc,format=rgb24"
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
            "ratio": round(hex_contrast(colors[token], colors[surface]), 4),
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
    blurred = gaussian_blurs(frame, [blur_sigma(dp * density) for dp in GLASS_BLURS_DP])
    panels = {}
    for dp, image in zip(GLASS_BLURS_DP, blurred):
        for alpha in GLASS_ALPHAS:
            luminance = relative_luminance(composite(image, tint, alpha))
            panels[(alpha, dp)] = _token_stats(luminance, text)
    return {"background": _token_stats(relative_luminance(frame), text), "glass": panels}


def _fold(per_frame):
    """The stats of the frames of one case and token, as one row: the worst of them all."""
    mins = [stats["min"] for stats in per_frame]
    return {
        "min": round(min(mins), 4),
        "p1": round(min(stats["p1"] for stats in per_frame), 4),
        "p5": round(min(stats["p5"] for stats in per_frame), 4),
        "worst_frame": int(np.argmin(mins)),
        "per_frame_min": [round(value, 4) for value in mins],
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
                "sRGB values; the edge of the frame repeats."
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


def measure_folder(root, colors, jobs=1):
    """Measures the frames in `root/<Style>-<device>/*.png`; other folders are left alone."""
    sources = {}
    for folder in sorted(Path(root).iterdir()):
        style, _, device = folder.name.rpartition("-")
        frames = sorted(folder.glob("*.png")) if folder.is_dir() else []
        if style and device in DEVICE_WIDTH_DP and frames:
            sources[(style, device)] = frames
    if not sources:
        raise SystemExit(f"no <Style>-tv or <Style>-phone folder of PNG frames in {root}")
    return measure_sources(sources, colors, jobs)


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
                [_label(*source)] + [f"{by_token[token]:.2f}" for token in tokens]
                for source, by_token in by_source.items()
            ],
        )

    ratios = {(row["surface"], row["token"]): row["ratio"] for row in result["surfaces"]}
    surfaces = list(dict.fromkeys(row["surface"] for row in result["surfaces"]))
    rows = [
        [f"`{surface}`"] + [f"{ratios[(surface, token)]:.2f}" for token in tokens]
        for surface in surfaces
    ]
    worst_ratios = [min(ratios[(surface, token)] for surface in surfaces) for token in tokens]
    rows.append(["worst"] + [f"{ratio:.2f}" for ratio in worst_ratios])
    table_b = _table(["Surface"] + names, rows)

    worst = {}
    for row in result["glass"]:
        key = (row["alpha"], row["blur_dp"])
        cell = worst.setdefault(key, {}).get(row["token"])
        if cell is None or row["min"] < cell[0]:
            worst[key].update({row["token"]: (row["min"], _label(row["style"], row["device"]))})
    rows = []
    for (alpha, blur), by_token in worst.items():
        lowest = min(value for value, _ in by_token.values())
        rows.append(
            [f"{alpha:.2f}", f"{blur}"]
            + [f"{by_token[token][0]:.2f} ({by_token[token][1]})" for token in tokens]
            + [f"{lowest:.2f}"]
        )
    table_c = _table(["Alpha", "Blur dp"] + names + ["Lowest"], rows)

    return "\n\n".join(
        [
            "Table A. Text straight on the background: the worst case over the frames.",
            style_table("min"),
            "Table A, p5. The same, for information: the ratio below which 5% of the pixels are, "
            "in the worst frame.",
            style_table("p5"),
            "Table B. Text on the opaque surfaces: the contrast ratio.",
            table_b,
            "Table C. Text on a glass panel: the worst case over every style and frame, and the "
            "style it is in.",
            table_c,
        ]
    )


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

    args = parser.parse_args(argv)
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
