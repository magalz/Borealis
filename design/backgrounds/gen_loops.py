"""Generate the two code-made looping backgrounds (3a noise field, 3b particle sky).

Every time-dependent term uses an integer number of cycles over the loop period, so the last
frame flows into the first with no seam. Frames are piped raw to ffmpeg.

Usage: python -I gen_loops.py <out_dir> [width height seconds fps]
"""

import subprocess
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1]
W = int(sys.argv[2]) if len(sys.argv) > 2 else 1920
H = int(sys.argv[3]) if len(sys.argv) > 3 else 1080
SECONDS = float(sys.argv[4]) if len(sys.argv) > 4 else 20.0
FPS = int(sys.argv[5]) if len(sys.argv) > 5 else 30
N = int(SECONDS * FPS)

PAGE = np.array([0x0E, 0x10, 0x13], dtype=np.float32) / 255
VIOLET_DEEP = np.array([0x7C, 0x3A, 0xED], dtype=np.float32) / 255
VIOLET = np.array([0xA7, 0x8B, 0xFA], dtype=np.float32) / 255
TEAL = np.array([0x1F, 0x8A, 0x8A], dtype=np.float32) / 255

rng = np.random.default_rng(7)


def ffmpeg(path):
    return subprocess.Popen(
        [
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
            "-i", "-",
            "-c:v", "libx264", "-preset", "slow", "-crf", "23", "-bf", "0", "-tune", "grain",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", path,
        ],
        stdin=subprocess.PIPE,
    )


def waves(count, scale, gx, gy, t, cycles_max):
    """A sum of travelling waves; each has an integer number of cycles per loop."""
    total = np.zeros_like(gx)
    for _ in range(count):
        kx, ky = rng.normal(0, scale, 2)
        n = int(rng.integers(1, cycles_max + 1)) * (1 if rng.random() < 0.5 else -1)
        phase = rng.uniform(0, 2 * np.pi)
        amp = rng.uniform(0.5, 1.0)
        total += amp * np.sin(kx * gx + ky * gy + 2 * np.pi * n * t + phase)
    return total / count


def to_frame(rgb_small):
    """Upscale a low-resolution float image smoothly and dither it to avoid banding."""
    channels = []
    for c in range(3):
        ch = Image.fromarray((np.clip(rgb_small[..., c], 0, 1) * 255).astype(np.float32), mode="F")
        channels.append(np.asarray(ch.resize((W, H), Image.Resampling.BICUBIC)))
    full = np.stack(channels, axis=-1)
    full += rng.uniform(-0.6, 0.6, full.shape).astype(np.float32)  # dither and fine grain
    return np.clip(full, 0, 255).astype(np.uint8)


def noise_field(path):
    sw, sh = 192, 108
    gy, gx = np.mgrid[0:sh, 0:sw].astype(np.float32)
    gx /= sw
    gy /= sh
    state = rng.bit_generator.state
    proc = ffmpeg(path)
    for i in range(N):
        t = i / N
        rng.bit_generator.state = state  # the same waves every frame, only t moves
        warp_x = waves(5, 3.0, gx, gy, t, 1)
        warp_y = waves(5, 3.0, gx, gy, t, 1)
        # Curtains: ridges of a warped field, stretched vertically, read as folds of light.
        v = waves(7, 9.0, gx + 0.22 * warp_x, (gy + 0.30 * warp_y) * 0.30, t, 2)
        ridge = np.clip(1.0 - np.abs(v) * 2.2, 0, 1) ** 3
        body = (waves(4, 4.0, gx + 0.1 * warp_x, gy * 0.5, t, 1) * 0.5 + 0.5) ** 2
        top = np.clip(1.25 - gy * 1.5, 0, 1) ** 1.3  # brighter in the upper part
        glow = (0.75 * ridge + 0.35 * body) * top
        tint = np.clip(waves(3, 1.5, gx, gy, t, 1) * 0.5 + 0.5, 0, 1)[..., None]
        color = VIOLET_DEEP * (1 - tint) + TEAL * tint
        rgb = PAGE + glow[..., None] * (color * 0.62 + VIOLET * 0.12)
        proc.stdin.write(to_frame(rgb).tobytes())
    proc.stdin.close()
    proc.wait()


def particle_sky(path):
    sw, sh = 192, 108
    gy, gx = np.mgrid[0:sh, 0:sw].astype(np.float32)
    gx /= sw
    gy /= sh
    layers = []
    for depth, count, size, radius in ((0.4, 140, 1.0, 6), (0.7, 70, 1.4, 12), (1.0, 30, 1.9, 22)):
        layers.append(
            dict(
                x=rng.uniform(0, W, count), y=rng.uniform(0, H, count),
                base=rng.uniform(0.15, 0.55, count) * depth,
                amp=rng.uniform(0.1, 0.35, count) * depth,
                cycles=rng.integers(1, 4, count), phase=rng.uniform(0, 2 * np.pi, count),
                size=size, radius=radius, angle=rng.uniform(0, 2 * np.pi),
            )
        )
    yy, xx = np.mgrid[-3:4, -3:4].astype(np.float32)
    proc = ffmpeg(path)
    for i in range(N):
        t = i / N
        breath = 0.5 + 0.5 * np.sin(2 * np.pi * t)
        glow = np.exp(-(((gx - 0.5) / 0.55) ** 2 + ((gy - 0.15) / 0.45) ** 2))
        rgb_small = PAGE + glow[..., None] * (VIOLET_DEEP * (0.10 + 0.04 * breath))
        frame = to_frame(rgb_small).astype(np.float32)
        for layer in layers:
            dx = layer["radius"] * np.cos(2 * np.pi * t + layer["angle"])
            dy = layer["radius"] * np.sin(2 * np.pi * t + layer["angle"])
            bright = layer["base"] + layer["amp"] * np.sin(2 * np.pi * layer["cycles"] * t + layer["phase"])
            kernel = np.exp(-(xx ** 2 + yy ** 2) / (2 * layer["size"] ** 2))
            for x, y, b in zip(layer["x"] + dx, layer["y"] + dy, bright):
                cx, cy = int(x) % W, int(y) % H
                if 3 <= cx < W - 4 and 3 <= cy < H - 4:
                    frame[cy - 3:cy + 4, cx - 3:cx + 4] += kernel[..., None] * b * np.array([200, 190, 255])
        proc.stdin.write(np.clip(frame, 0, 255).astype(np.uint8).tobytes())
    proc.stdin.close()
    proc.wait()


if __name__ == "__main__":
    noise_field(f"{OUT}/3a-noise-field.mp4")
    particle_sky(f"{OUT}/3b-particle-sky.mp4")
