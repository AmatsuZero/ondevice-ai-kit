import argparse
import math
import random
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


W, H = 3840, 1080


class Picture:
    def __init__(self, shade=150):
        self.p = bytearray([shade]) * (W * H)

    def rect(self, x, y, w, h, shade):
        x0, x1 = max(0, x), min(W, x + w)
        y0, y1 = max(0, y), min(H, y + h)
        if x0 < x1 and y0 < y1:
            row = bytes([shade]) * (x1 - x0)
            for yy in range(y0, y1):
                i = yy * W + x0
                self.p[i:i + len(row)] = row

    def line(self, x0, y0, x1, y1, shade):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            if 0 <= x0 < W and 0 <= y0 < H:
                self.p[y0 * W + x0] = shade
            if (x0, y0) == (x1, y1):
                break
            e = 2 * err
            if e >= dy:
                err, x0 = err + dy, x0 + sx
            if e <= dx:
                err, y0 = err + dx, y0 + sy

    def poly(self, points, shade):
        points = [(round(x), round(y)) for x, y in points]
        for y in range(max(0, min(p[1] for p in points)),
                       min(H, max(p[1] for p in points) + 1)):
            xs = []
            for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]):
                if (y1 <= y < y2) or (y2 <= y < y1):
                    xs.append(round(x1 + (y - y1) * (x2 - x1) / (y2 - y1)))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                self.rect(a, y, b - a + 1, 1, shade)

    def ellipse(self, x, y, rx, ry, shade):
        for yy in range(max(0, y - ry), min(H, y + ry + 1)):
            dx = round(rx * math.sqrt(max(0, 1 - ((yy - y) / ry) ** 2)))
            self.rect(x - dx, yy, 2 * dx + 1, 1, shade)

    def save(self, path):
        with open(path, "wb") as f:
            f.write(f"P5\n{W} {H}\n255\n".encode("ascii"))
            f.write(self.p)


def scene_fence():
    c = Picture(194)
    for x in range(0, W, 640):
        c.rect(x, 80, 600, 570, 174 - (x // 640 % 3) * 12)
        c.rect(x + 24, 120, 550, 12, 118)
        for wx in range(x + 48, x + 570, 112):
            c.rect(wx, 180, 62, 92, 63)
            c.rect(wx + 7, 188, 48, 76, 222)
            c.rect(wx + 28, 188, 5, 76, 106)
            c.rect(wx + 7, 223, 48, 5, 106)
        c.rect(x + 18, 320, 570, 18, 105)
        c.rect(x + 80, 390, 380, 7, 127)
    c.rect(0, 655, W, 425, 93)
    for y in (710, 900):
        c.rect(0, y, W, 22, 43)
    rng = random.Random(31)
    x = 14
    while x < W:
        c.rect(x, 665, rng.choice((7, 10, 14)), 415, rng.choice((38, 48, 58)))
        x += rng.choice((42, 48, 54, 61))
    for x in range(35, W, 173):
        c.rect(x, 620, 7, 80, 35)
    return c


FONT = {
    "V": ("10001", "10001", "10001", "10001", "01010", "01010", "00100"),
    "I": ("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    "D": ("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    "E": ("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    "O": ("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    "3": ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    "0": ("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    "F": ("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    "P": ("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    "S": ("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
}


def text(c, x, y, value, unit, shade):
    for ch in value:
        if ch == " ":
            x += 6 * unit
            continue
        for row, bits in enumerate(FONT[ch]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    c.rect(x + col * unit, y + row * unit, unit, unit, shade)
        x += 6 * unit


def scene_letters():
    c = Picture(210)
    for x in range(0, W, 96):
        c.rect(x, 0, 2, H, 187 if x % 192 else 162)
    for y, shade, thick in ((170, 55, 2), (240, 95, 4), (720, 65, 1),
                            (790, 115, 7), (900, 75, 3)):
        c.rect(0, y, W, thick, shade)
    for x in (180, 2180):
        c.rect(x - 45, 345, 770, 150, 45)
        text(c, x, 385, "VIDEO 30", 9, 244)
        text(c, x + 80, 570, "30 FPS", 5, 65)
    for x in range(0, W, 320):
        c.rect(x, 1000, 160, 12, 65)
    return c


def scene_profiles():
    c = Picture(158)
    rng = random.Random(45)
    for _ in range(8000):
        x, y = rng.randrange(W), rng.randrange(H)
        shade = rng.choice((112, 128, 178, 190, 205))
        c.rect(x, y, rng.choice((2, 3, 5)), rng.choice((2, 3, 6)), shade)
    for x in range(120, W, 260):
        c.line(x, 0, x - 100, H, 143)
    shape = [(0, 0), (120, 0), (200, 35), (235, 90), (245, 130),
             (290, 150), (245, 170), (240, 230), (210, 275),
             (175, 295), (175, 370), (65, 370), (60, 280), (35, 245),
             (20, 190), (0, 150)]
    for i, x in enumerate((430, 1450, 2480, 3370)):
        s, y = (1.0, 300) if i % 2 == 0 else (0.82, 420)
        flip = i % 2 == 1
        pts = [(round(x + (290 - px if flip else px) * s),
                round(y + py * s)) for px, py in shape]
        c.poly(pts, (53, 72, 48, 80)[i])
        c.poly([(x + (290 - px if flip else px) * s, y + py * s)
                for px, py in ((0, 0), (120, 0), (198, 35), (220, 95),
                               (150, 75), (105, 120), (38, 115))],
               (31, 43, 29, 48)[i])
    return c


def main():
    parser = argparse.ArgumentParser(description="Generate three synthetic motion clips.")
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    out = args.output_dir
    if not shutil.which("ffmpeg"):
        parser.error("ffmpeg was not found on PATH")
    if out.is_symlink():
        parser.error("refusing a symlink output directory")
    if out.exists() and not out.is_dir():
        parser.error("output path is not a directory")
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        parser.error("output directory must be empty")
    reserved = []
    generated = False
    try:
        for i in range(1, 4):
            path = out / f"{i}.mp4"
            with path.open("xb"):
                pass
            reserved.append(path)
        with tempfile.TemporaryDirectory(prefix="motion-pair-") as tmp:
            images = [Path(tmp) / f"{i}.pgm" for i in range(1, 4)]
            for maker, image in zip((scene_fence, scene_letters, scene_profiles), images):
                maker().save(image)
            audio = (r"aevalsrc=0.12*sin(2*PI*1000*t)*(lt(mod(t\,1)\,0.08))"
                     r":s=48000:d=6")
            for image, output, speed in zip(images, reserved, (320, 80, 160)):
                vf = (f"crop=1920:1080:x={speed}*t:y=0,"
                      "scale=960:540:flags=lanczos,"
                      "drawbox=x=16:y=16:w=24:h=24:color=white:t=fill:enable='lt(mod(t,1),0.08)',setsar=1,setparams=range=limited:color_primaries=bt709:color_trc=bt709:colorspace=bt709,format=yuv420p")
                subprocess.run([
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-loop", "1", "-framerate", "30", "-i", str(image),
                    "-f", "lavfi", "-i", audio, "-map", "0:v:0", "-map", "1:a:0",
                    "-vf", vf, "-frames:v", "180", "-t", "6",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                    "-pix_fmt", "yuv420p", "-r", "30", "-c:a", "aac",
                    "-b:a", "64k", "-ar", "48000", "-ac", "1",
                    "-bsf:v", "h264_metadata=colour_primaries=1:transfer_characteristics=1:matrix_coefficients=1",
                    "-color_primaries", "bt709", "-color_trc", "bt709",
                    "-colorspace", "bt709", "-movflags", "+faststart+write_colr", str(output)
                ], check=True, capture_output=True, text=True)
        generated = True
    except (OSError, subprocess.CalledProcessError) as exc:
        detail = exc.stderr.strip() if isinstance(exc, subprocess.CalledProcessError) else str(exc)
        print(f"clip generation failed: {detail}", file=sys.stderr)
        return 1
    finally:
        if not generated:
            for path in reserved:
                path.unlink(missing_ok=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
