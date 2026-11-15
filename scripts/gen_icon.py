#!/usr/bin/env python3
"""Original pixel art: a worn trail winding across a grass field, seen from above."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
GRASS = ("#5d9a3f", "#6aa84a", "#538c38", "#74b354")
TRAIL = {0: "#8a6a45", 1: "#9b7a50", 2: "#7b5d3c"}
EDGE = "#6f7f3d"
size = 16
image = Image.new("RGB", (size, size))
# Trail centre column per row: a gentle S-curve from bottom-left to top-right.
centre = [11, 11, 10, 10, 9, 8, 8, 7, 7, 6, 6, 5, 5, 5, 4, 4]
for y in range(size):
    for x in range(size):
        noise = (x * 7 + y * 13 + (x * y) % 5) % 4
        distance = abs(x - centre[y])
        if distance <= 1:
            colour = TRAIL[(x + y * 3) % 3]
        elif distance == 2 and noise != 0:
            colour = EDGE
        else:
            colour = GRASS[noise]
        image.putpixel((x, y), tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5)))
# Two footprints on the trail.
for x, y in ((9, 4), (7, 9), (5, 13)):
    image.putpixel((x, y), (0x5e, 0x45, 0x2b))
for path, scale in ((root / "src/main/resources/assets/worn_ground/icon.png", 128), (root / "docs/icon.png", 512)):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((scale, scale), Image.Resampling.NEAREST).save(path)
