#!/usr/bin/env python3
"""Generate the canonical NextEdge AI logo family from one construction grid."""

from __future__ import annotations

import argparse
import html
import shutil
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw


INK = "#1C1917"
BRASS = "#A16207"
IVORY = "#FAFAF9"
WHITE = "#FFFFFF"

MARK_POLYGONS = (
    ((8, 56), (8, 16), (16, 8), (20, 8), (20, 56)),
    ((18, 8), (30, 8), (56, 44), (56, 56), (46, 56), (18, 18)),
    ((44, 8), (56, 8), (56, 48), (48, 56), (44, 56)),
)
ACCENT_POLYGON = ((18, 13), (22, 9), (33, 24), (29, 29))


def svg_mark(fill: str, accent: str | None, class_name: str = "nextedge-symbol") -> str:
    shapes = "".join(
        f'<polygon points="{" ".join(f"{x},{y}" for x, y in polygon)}" />'
        for polygon in MARK_POLYGONS
    )
    accent_shape = ""
    if accent:
        points = " ".join(f"{x},{y}" for x, y in ACCENT_POLYGON)
        accent_shape = f'<polygon class="nextedge-edge" points="{points}" fill="{accent}" />'
    return f'<g class="{class_name}" fill="{fill}">{shapes}</g>{accent_shape}'


def text_paths(font_path: Path, text: str, size: float, x: float, baseline: float, fill: str) -> tuple[str, float]:
    font = TTFont(font_path)
    if "fvar" in font:
        font = instantiateVariableFont(font, {"opsz": 32, "wght": 620}, inplace=False)

    units_per_em = font["head"].unitsPerEm
    scale = size / units_per_em
    cmap = font.getBestCmap()
    metrics = font["hmtx"].metrics
    glyph_set = font.getGlyphSet()
    cursor = x
    output: list[str] = []

    for character in text:
        glyph_name = cmap.get(ord(character))
        if glyph_name is None:
            raise ValueError(f"Missing glyph for {character!r}")
        advance, _ = metrics[glyph_name]
        if character != " ":
            pen = SVGPathPen(glyph_set)
            glyph_set[glyph_name].draw(pen)
            commands = html.escape(pen.getCommands(), quote=True)
            output.append(
                f'<path d="{commands}" transform="translate({cursor:.3f} {baseline:.3f}) '
                f'scale({scale:.6f} {-scale:.6f})" />'
            )
        cursor += advance * scale

    return f'<g class="nextedge-wordmark" fill="{fill}">{"".join(output)}</g>', cursor


def lockup_svg(font_path: Path, reverse: bool = False) -> str:
    word_fill = WHITE if reverse else INK
    wordmark, right_edge = text_paths(font_path, "extEdge AI", 23, 29, 25.5, word_fill)
    width = right_edge + 1.5
    symbol = svg_mark(word_fill, BRASS)
    return (
        f'<svg width="{width:.2f}" height="32" viewBox="0 0 {width:.2f} 32" fill="none" '
        'xmlns="http://www.w3.org/2000/svg" role="img" aria-label="NextEdge AI">'
        '<title>NextEdge AI</title>'
        f'<g transform="translate(0 1) scale(.46875)">{symbol}</g>{wordmark}</svg>\n'
    )


def mark_svg(reverse: bool = False, mono: bool = False) -> str:
    fill = WHITE if reverse else INK
    accent = None if mono else BRASS
    symbol = svg_mark(fill, accent)
    return (
        '<svg width="64" height="64" viewBox="0 0 64 64" fill="none" '
        'xmlns="http://www.w3.org/2000/svg" role="img" aria-label="NextEdge AI">'
        '<title>NextEdge AI</title>'
        f'{symbol}</svg>\n'
    )


def draw_mark(size: int, background: str | None = None, safe_scale: float = 1.0) -> Image.Image:
    canvas = 1024
    image = Image.new("RGBA", (canvas, canvas), background or (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    scale = (canvas / 64) * safe_scale
    offset = (canvas - 64 * scale) / 2

    def transform(points: tuple[tuple[int, int], ...]) -> list[tuple[float, float]]:
        return [(offset + x * scale, offset + y * scale) for x, y in points]

    for polygon in MARK_POLYGONS:
        draw.polygon(transform(polygon), fill=INK)
    draw.polygon(transform(ACCENT_POLYGON), fill=BRASS)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", required=True, type=Path, help="Path to the Inter variable TTF")
    parser.add_argument("--root", default=Path(__file__).resolve().parents[1], type=Path)
    args = parser.parse_args()

    if not args.font.is_file():
        raise FileNotFoundError(args.font)

    root = args.root.resolve()
    master = root / "brand" / "final"
    src_assets = root / "spectrum-master" / "src" / "assets" / "brand"
    public_assets = root / "spectrum-master" / "public" / "assets" / "brand"
    for folder in (master, src_assets, public_assets):
        folder.mkdir(parents=True, exist_ok=True)

    svg_assets = {
        "nextedge-mark.svg": mark_svg(),
        "nextedge-mark-mono.svg": mark_svg(mono=True),
        "nextedge-mark-reverse.svg": mark_svg(reverse=True),
        "nextedge-lockup.svg": lockup_svg(args.font),
        "nextedge-lockup-reverse.svg": lockup_svg(args.font, reverse=True),
    }
    for name, content in svg_assets.items():
        write_text(master / name, content)
        write_text(src_assets / name, content)
        write_text(public_assets / name, content)

    png_assets = {
        "favicon-32.png": draw_mark(32),
        "icon-192.png": draw_mark(192),
        "icon-512.png": draw_mark(512),
        "apple-touch-icon.png": draw_mark(180, IVORY, 0.84),
        "icon-maskable-512.png": draw_mark(512, IVORY, 0.72),
    }
    for name, image in png_assets.items():
        image.save(master / name, optimize=True)
        image.save(public_assets / name, optimize=True)

    favicon_images = [draw_mark(size) for size in (16, 32, 48, 64)]
    favicon_images[0].save(
        master / "favicon.ico",
        format="ICO",
        append_images=favicon_images[1:],
        sizes=[(16, 16), (32, 32), (48, 48), (64, 64)],
    )
    shutil.copy2(master / "favicon.ico", root / "spectrum-master" / "public" / "favicon.ico")
    shutil.copy2(master / "favicon-32.png", root / "spectrum-master" / "public" / "assets" / "icons" / "favicon.png")

    print(f"Generated {len(svg_assets)} SVGs, {len(png_assets)} PNGs, and favicon.ico")


if __name__ == "__main__":
    main()
