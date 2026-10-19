"""Make a seamless loop: drop the first F frames and crossfade the tail into them.

out[k] = orig[k+F] for k < N-2F; over the last F output frames the tail fades
into orig[0..F-1], so the last output frame is orig[F-1] and the first is orig[F].
Usage: python -I loopfade.py in.mp4 out.mp4 [fade_frames] [skip_start] [drop_end]
"""
import subprocess, sys
import numpy as np

src, dst = sys.argv[1], sys.argv[2]
F = int(sys.argv[3]) if len(sys.argv) > 3 else 24
START = int(sys.argv[4]) if len(sys.argv) > 4 else 0  # first source frame to use
END = int(sys.argv[5]) if len(sys.argv) > 5 else 0  # source frames to drop at the end
# No B-frames and a steady quality: smooth gradients otherwise pulse every few frames.
ENC = ["-crf", "23", "-bf", "0", "-tune", "grain"]
probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-count_frames",
                        "-show_entries", "stream=width,height,r_frame_rate,nb_read_frames",
                        "-of", "csv=p=0", src], capture_output=True, text=True).stdout.strip().split(",")
w, h, rate, n = int(probe[0]), int(probe[1]), probe[2], int(probe[3])
size = w * h * 3
dec = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-i", src, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                       stdout=subprocess.PIPE)
enc = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                        "-s", f"{w}x{h}", "-r", rate, "-i", "-", "-c:v", "libx264", "-preset", "slow",
                        *ENC, "-pix_fmt", "yuv420p", "-movflags", "+faststart", dst],
                       stdin=subprocess.PIPE)
for _ in range(START):
    dec.stdout.read(size)
n = n - START - END
head = [np.frombuffer(dec.stdout.read(size), np.uint8).reshape(h, w, 3) for _ in range(F)]
start_fade = n - 2 * F
for k in range(n - F):
    frame = np.frombuffer(dec.stdout.read(size), np.uint8).reshape(h, w, 3)
    if k >= start_fade:
        a = (k - start_fade + 1) / F
        frame = ((1 - a) * frame.astype(np.float32) + a * head[k - start_fade].astype(np.float32)).round().astype(np.uint8)
    enc.stdin.write(frame.tobytes())
enc.stdin.close(); enc.wait(); dec.stdout.close(); dec.wait()
print(dst, "frames", n - F)
