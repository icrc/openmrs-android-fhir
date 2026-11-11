"""Turns the raw captures (screenshots/raw/<lang>/<id>.png + .json) into the images of the guide.

    python scripts/postprocess.py --lang en

Crops the tablet's taskbar, frames the screen (rounded corners, thin border, soft shadow) and draws
the numbered callouts that the manifest asked for. For Arabic the callouts sit on the right edge of
the element (the start of a line in a right-to-left language).
"""
import argparse
import glob
import json
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import gl

RAW = os.path.join(gl.GUIDE, "screenshots", "raw")
OUT = os.path.join(gl.GUIDE, "screenshots")
TASKBAR = 125          # pixels of the launcher's taskbar at the bottom of a 1600x2560 capture
TARGET_WIDTH = 1100
ACCENT = (192, 57, 43)
FONT_CANDIDATES = ["C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/segoeuib.ttf", "DejaVuSans-Bold.ttf"]


def font(size):
    for name in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(name, size)
        except (IOError, OSError):
            continue
    return ImageFont.load_default()


def polish(lang, shot_id):
    png = os.path.join(RAW, lang, shot_id + ".png")
    meta_path = os.path.join(RAW, lang, shot_id + ".json")
    img = Image.open(png).convert("RGB")
    meta = json.load(open(meta_path, encoding="utf-8")) if os.path.exists(meta_path) else {"callouts": []}
    width, height = img.size
    img = img.crop((0, 0, width, height - TASKBAR))
    draw = ImageDraw.Draw(img)
    radius = 34
    rtl = lang == "ar"
    f = font(46)
    for c in meta.get("callouts", []):
        l, t, r, b = c["box"]
        cx = (width - 52) if rtl else 52
        cy = (t + b) // 2
        circle = 36
        # a thin leader line from the badge to the element
        edge = (r + 4) if rtl else (l - 4)
        draw.line((cx, cy, edge, cy), fill=ACCENT, width=5)
        draw.ellipse((cx - circle, cy - circle, cx + circle, cy + circle), fill=ACCENT, outline=(255, 255, 255), width=4)
        text = str(c["n"])
        box = draw.textbbox((0, 0), text, font=f)
        draw.text((cx - (box[2] - box[0]) / 2 - box[0], cy - (box[3] - box[1]) / 2 - box[1]), text, font=f, fill=(255, 255, 255))
    for r in meta.get("redact", []):
        l, tp, rr, bt = r["box"]
        bg = img.getpixel((max(l - 4, 0), min(tp + 2, img.size[1] - 1)))
        draw.rectangle((l - 2, tp - 2, rr + 2, bt + 2), fill=bg)
        size = max(18, int((bt - tp) * 0.62))
        draw.text((l, tp + (bt - tp - size) // 2), r["text"], font=font(size), fill=(60, 60, 60))
    # rounded corners
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius=radius, fill=255)
    pad = 36
    canvas = Image.new("RGB", (img.size[0] + 2 * pad, img.size[1] + 2 * pad), (255, 255, 255))
    shadow = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 8, pad + img.size[0], pad + 8 + img.size[1]), radius=radius, fill=110)
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    canvas.paste((60, 60, 60), (0, 0), shadow)
    canvas.paste(img, (pad, pad), mask)
    ImageDraw.Draw(canvas).rounded_rectangle((pad, pad, pad + img.size[0] - 1, pad + img.size[1] - 1), radius=radius, outline=(190, 190, 190), width=2)
    scale = TARGET_WIDTH / float(canvas.size[0])
    canvas = canvas.resize((TARGET_WIDTH, int(canvas.size[1] * scale)), Image.LANCZOS)
    folder = os.path.join(OUT, lang)
    os.makedirs(folder, exist_ok=True)
    canvas.save(os.path.join(folder, shot_id + ".png"), optimize=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", default="all")
    args = parser.parse_args()
    langs = gl.LANGS if args.lang == "all" else (args.lang,)
    count = 0
    for lang in langs:
        for path in sorted(glob.glob(os.path.join(RAW, lang, "*.png"))):
            polish(lang, os.path.splitext(os.path.basename(path))[0])
            count += 1
    print("polished %d images" % count)


if __name__ == "__main__":
    main()
