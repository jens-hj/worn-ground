#!/usr/bin/env python3
"""Compose the Modrinth gallery images from showcase screenshots and lab measurements.

Inputs: build/gallery-src/*.png (copied from build/showcase/screenshots) and docs/measurements.json.
Output: docs/gallery/*.png
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
src = root / "build/gallery-src"
out = root / "docs/gallery"
fonts = out / "fonts"
W, H = 1920, 1080
INK, PAPER, ACCENT = (24, 20, 16), (247, 240, 225), (232, 178, 92)
COLOURS = {"grass": (106, 168, 74), "dirt": (134, 96, 67), "coarse dirt": (112, 82, 58), "dirt path": (176, 142, 84)}


def font(size, bold=True):
    return ImageFont.truetype(str(fonts / ("Silkscreen-Bold.ttf" if bold else "Silkscreen-Regular.ttf")), size)


def load(name):
    return Image.open(src / f"{name}.png").convert("RGB")


def chip(draw, xy, text, size=34, anchor="la", fill=PAPER, ink=INK, pad=12):
    f = font(size)
    box = draw.textbbox(xy, text, font=f, anchor=anchor)
    draw.rectangle((box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad), fill=fill)
    draw.text(xy, text, font=f, fill=ink, anchor=anchor)


def banner(image, title, subtitle=None):
    """Caption bar along the bottom."""
    draw = ImageDraw.Draw(image, "RGBA")
    height = 118 if subtitle else 84
    draw.rectangle((0, image.height - height, image.width, image.height), fill=(*INK, 215))
    draw.text((36, image.height - height + 16), title, font=font(40), fill=ACCENT)
    if subtitle:
        draw.text((36, image.height - 50), subtitle, font=font(26, False), fill=PAPER)
    return image


def grid(names, labels, columns, title, subtitle, file, crop=None):
    rows = -(-len(names) // columns)
    cell_w = W // columns
    cell_h = round(cell_w * 9 / 16) if crop is None else round(cell_w * crop[3] / crop[2])
    sheet = Image.new("RGB", (W, cell_h * rows), INK)
    draw = ImageDraw.Draw(sheet)
    for i, (name, label) in enumerate(zip(names, labels)):
        image = load(name)
        if crop:
            image = image.crop((crop[0], crop[1], crop[0] + crop[2], crop[1] + crop[3]))
        image = image.resize((cell_w - 6, cell_h - 6), Image.Resampling.LANCZOS)
        x, y = (i % columns) * cell_w + 3, (i // columns) * cell_h + 3
        sheet.paste(image, (x, y))
        chip(draw, (x + 22, y + 22), label, 30)
    banner(sheet, title, subtitle).save(out / file, quality=90)


def single(name, title, subtitle, file, labels=()):
    image = load(name)
    draw = ImageDraw.Draw(image)
    for xy, text, anchor in labels:
        chip(draw, xy, text, 30, anchor)
    banner(image, title, subtitle).save(out / file, quality=90)


def chart(file):
    data = json.load(open(root / "docs/measurements.json"))
    image = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(image)
    draw.text((60, 40), "HOW MANY TRIPS UNTIL A PATH?", font=font(54), fill=INK)
    draw.text((60, 108), "Measured in-game: 32 blocks of grass, one trip a minute, real regrowth. Mean of 6 runs (car: 1 run).",
              font=font(24, False), fill=INK)
    panels = [("WALKING", data["lanes"]["every-minute/walk"], 300, "trips"),
              ("SPRINTING", data["lanes"]["every-minute/sprint"], 300, "trips"),
              ("SPRINT-JUMPING", data["lanes"]["every-minute/sprint-jump"], 300, "trips"),
              ("OFFROAD CAR", data["vehicles"]["car/every-minute"], 60, "passes")]
    keys = list(COLOURS)
    for index, (name, series, limit, unit) in enumerate(panels):
        x0, y0, w, h = 60 + index * 462, 270, 414, 530
        series = series[:limit]
        draw.text((x0, y0 - 50), name, font=font(34), fill=INK)
        # Stacked areas, most worn stage at the bottom.
        order = [3, 2, 1, 0]
        base = [0.0] * len(series)
        for k in order:
            top = [b + row[k] for b, row in zip(base, series)]
            points = [(x0 + w * i / (len(series) - 1), y0 + h - h * min(1, t)) for i, t in enumerate(top)]
            points += [(x0 + w * i / (len(series) - 1), y0 + h - h * min(1, b)) for i, b in reversed(list(enumerate(base)))]
            draw.polygon(points, fill=COLOURS[keys[k]])
            base = top
        draw.rectangle((x0, y0, x0 + w, y0 + h), outline=INK, width=4)
        for tick in range(0, limit + 1, limit // 3):
            x = x0 + w * tick / limit
            draw.line((x, y0 + h, x, y0 + h + 12), fill=INK, width=3)
            draw.text((x, y0 + h + 18), str(tick), font=font(22, False), fill=INK, anchor="la" if tick == 0 else "ra" if tick == limit else "ma")
        draw.text((x0 + w / 2, y0 + h + 52), unit, font=font(24), fill=INK, anchor="ma")
        worn = next((i + 1 for i, row in enumerate(series) if 1 - row[0] >= 0.5), None)
        permanent = next((i + 1 for i, row in enumerate(series) if row[2] + row[3] >= 0.5), None)
        path = next((i + 1 for i, row in enumerate(series) if row[3] >= 0.5), None)
        for line, (label, value) in enumerate((("half worn", worn), ("half permanent", permanent), ("half path", path))):
            draw.text((x0, y0 + h + 100 + line * 38), f"{label}: ~{value}", font=font(26), fill=INK)
    x = 60
    for key in keys:
        draw.rectangle((x, 160, x + 34, 194), fill=COLOURS[key], outline=INK, width=3)
        draw.text((x + 46, 162), key, font=font(26), fill=INK)
        x += 80 + int(draw.textlength(key, font=font(26)))
    draw.text((x + 20, 164), "grass regrows over dirt; coarse dirt and path are permanent", font=font(22, False), fill=INK)
    image.save(out / file, quality=90)


if __name__ == "__main__":
    out.mkdir(parents=True, exist_ok=True)
    grid(["timelapse-0", "timelapse-20", "timelapse-80", "timelapse-300"],
         ["untouched", "20 trips", "80 trips", "300 trips"], 2,
         "NOBODY BUILT THIS PATH", "Cabin, well and field. One walk a minute along each route, nothing placed by hand.",
         "01-timelapse.jpg")
    single("timelapse-300", "THE GROUND REMEMBERS WHERE YOU WALK", "Grass wears to dirt, coarse dirt and finally a dirt path.", "02-homestead.jpg")
    chart("03-how-many-trips.jpg")
    grid(["car-3", "car-13", "car-41"], ["3 passes", "13 passes", "41 passes"], 3,
         "VEHICLES LEAVE RUTS", "Create Aeronautics / Offroad wheels wear two tracks. Real Sable physics, nothing under the chassis.",
         "04-vehicle-ruts.jpg", crop=(480, 150, 960, 900))
    grid(["pen-0", "pen-4-days", "pen-15-days"], ["day 0", "day 4", "day 15"], 3,
         "LIVESTOCK WEAR THEIR PEN", "Four cows on the left, an empty pen on the right. Mobs wear at a quarter of the player rate.",
         "05-cow-pen.jpg", crop=(560, 300, 960, 500))
    single("gaits", "YOUR PACE DECIDES", "The same 300 trips on each line.", "06-sprint-walk-sneak.jpg", labels=[
        ((480, 318), "sprinting: twice the wear", "lm"), ((480, 458), "walking", "lm"),
        ((480, 640), "sneaking or leather boots: no trace", "lm")])
    single("safe", "YOUR BUILDS STAY PUT", "Ground under rails, tracks, machines and crops is never turned into a path block.",
           "07-rails-and-flowers.jpg", labels=[((590, 522), "rail keeps its footing", "mb"),
                                               ((1400, 800), "flowers get trampled (optional)", "mb")])
    single("stages", "FOUR STAGES", "Grass regrows over plain dirt. Coarse dirt and paths stay until you dig or hoe them.",
           "08-stages.jpg", labels=[((320, 470), "grass", "mb"), ((720, 470), "dirt", "mb"), ((1108, 470), "coarse dirt", "mb"),
                                    ((1480, 470), "dirt path", "mb")])
    single("door", "WORN IN, NOT PLACED", "The doorstep after 300 trips.", "09-doorstep.jpg")
    print("composed", sorted(p.name for p in out.glob("*.jpg")))
