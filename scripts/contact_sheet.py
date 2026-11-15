#!/usr/bin/env python3
"""Tile screenshots into small preview sheets: contact_sheet.py <out-prefix> <image>..."""
import sys
from PIL import Image

prefix, names = sys.argv[1], sys.argv[2:]
for i in range(0, len(names), 3):
    sheet = Image.new("RGB", (1440, 270))
    for j, name in enumerate(names[i:i + 3]):
        sheet.paste(Image.open(name).convert("RGB").resize((480, 270)), (j * 480, 0))
    sheet.save(f"{prefix}{i // 3}.png")
