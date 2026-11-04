#!/usr/bin/env python3
"""Bake the boot-screen glyph atlas.

The loading overlay is drawn *while* the resource reload is still running, so none of
Minecraft's font providers (including the TrueType ones in assets/carnation/font) exist yet.
The boot sequence therefore ships its own glyph atlas: plain white glyphs on transparent, laid
out in a fixed cell grid, plus a metrics table in em fractions.  Colours are applied per vertex
at draw time, so one atlas serves every tint and fade.

    python tools/build_boot_font.py

Outputs (built into the mod jar):
    src/main/resources/assets/carnation/boot/font.json
    src/main/resources/assets/carnation/boot/font_bold.png
    src/main/resources/assets/carnation/boot/font_regular.png
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "carnation" / "font"
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "carnation" / "boot"

EM = 96          # design size the metrics are normalised against
CELL = 128       # square cell per glyph, leaves room for ascenders and the CJK square
PAD = 12         # transparent margin inside a cell
COLS = 16

ASCII = [chr(c) for c in range(0x20, 0x7F)]
EXTRA = ["·", "×", "°", "±"]
# Glyphs used by the Chinese caption at the bottom of the reference boot screen.
CJK = list(dict.fromkeys("身份信息确认：莱茵生命终端接入系统档案分析实验室已就绪加载中欢迎进入权限验证源石科技研究数据单人游戏多选项退出语言无障碍设置继续新的世界"))

WEIGHTS = {
    "bold": {"latin": "figtree-bold.ttf", "cjk": "misans-gb2312.ttf", "cjk_stroke": 2},
    "regular": {"latin": "figtree-regular.ttf", "cjk": "misans-gb2312.ttf", "cjk_stroke": 0},
}


def bake(weight: str, config: dict) -> tuple[Image.Image, dict]:
    latin = ImageFont.truetype(str(FONT_DIR / config["latin"]), EM)
    cjk = ImageFont.truetype(str(FONT_DIR / config["cjk"]), EM)

    chars = ASCII + [c for c in EXTRA if c not in ASCII]
    cjk_chars = [c for c in CJK if c not in chars]
    total = len(chars) + len(cjk_chars)
    rows = math.ceil(total / COLS)
    sheet = Image.new("RGBA", (COLS * CELL, rows * CELL), (0, 0, 0, 0))
    draw = ImageDraw.Draw(sheet)

    glyphs: dict[str, list[float]] = {}
    index = 0
    for ch in chars + cjk_chars:
        is_cjk = ch in cjk_chars
        font = cjk if is_cjk else latin
        stroke = config["cjk_stroke"] if is_cjk else 0
        col, row = index % COLS, index // COLS
        cell_x, cell_y = col * CELL, row * CELL
        advance = float(font.getlength(ch))
        bbox = font.getbbox(ch, anchor="ls", stroke_width=stroke)
        left, top, right, bottom = (float(v) for v in bbox)
        width, height = right - left, bottom - top

        if width > CELL - 2 * PAD or height > CELL - 2 * PAD:
            raise SystemExit(f"glyph {ch!r} U+{ord(ch):04X} does not fit its cell ({width}x{height})")

        # Place the glyph's bitmap box at (PAD, PAD) inside the cell so the cell itself is the UV rect.
        origin_x = cell_x + PAD - left
        origin_y = cell_y + PAD - top
        draw.text(
            (origin_x, origin_y),
            ch,
            font=font,
            fill=(255, 255, 255, 255),
            anchor="ls",
            stroke_width=stroke,
            stroke_fill=(255, 255, 255, 255),
        )

        u0 = (cell_x + PAD) / sheet.width
        v0 = (cell_y + PAD) / sheet.height
        u1 = (cell_x + PAD + width) / sheet.width
        v1 = (cell_y + PAD + height) / sheet.height
        glyphs[ch] = [
            advance / EM, left / EM, top / EM, width / EM, height / EM,
            u0, v0, u1, v1,
        ]
        index += 1

    return sheet, glyphs


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    document = {"em": EM, "cell": CELL, "pad": PAD, "weights": {}}
    for name, config in WEIGHTS.items():
        sheet, glyphs = bake(name, config)
        file_name = f"font_{name}.png"
        sheet.save(OUT_DIR / file_name, optimize=True)
        document["weights"][name] = {
            "file": file_name,
            "width": sheet.width,
            "height": sheet.height,
            "glyphs": glyphs,
        }
        print(f"{name}: {sheet.width}x{sheet.height}, {len(glyphs)} glyphs -> {OUT_DIR / file_name}")

    (OUT_DIR / "font.json").write_text(json.dumps(document, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"metrics -> {OUT_DIR / 'font.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
