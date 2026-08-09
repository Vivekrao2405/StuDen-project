"""One-off generator for StuDen's PWA icon assets.

Run manually with `python scripts/generate_pwa_icons.py` from `frontend/`.
Requires Python 3 + Pillow (already available in this environment). Not part
of the npm build — the output PNGs are committed as static assets like any
other file in `public/`.
"""

from pathlib import Path

from PIL import Image, ImageDraw

BLUE = (37, 99, 235, 255)  # #2563EB — Tailwind blue-600, matches the app's --primary token
WHITE = (255, 255, 255, 255)

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "public"


def draw_mark(draw: ImageDraw.ImageDraw, size: int, scale: float = 1.0) -> None:
    """Draws a simplified graduation-cap glyph centered in a `size`x`size` canvas.

    `scale` shrinks the glyph (used for the maskable icon's safe-zone padding).
    """
    cx, cy = size / 2, size / 2 * 0.96
    w = size * 0.60 * scale
    h = size * 0.30 * scale

    # Mortarboard (diamond)
    diamond = [
        (cx, cy - h / 2),
        (cx + w / 2, cy),
        (cx, cy + h / 2),
        (cx - w / 2, cy),
    ]
    draw.polygon(diamond, fill=WHITE)

    # Band beneath the mortarboard
    band_w = size * 0.30 * scale
    band_h = size * 0.20 * scale
    band_top = cy + h * 0.12
    draw.rounded_rectangle(
        [cx - band_w / 2, band_top, cx + band_w / 2, band_top + band_h],
        radius=band_h * 0.25,
        fill=WHITE,
    )

    # Tassel: a thin line plus a small ball, hanging off the right point of the diamond
    tassel_start = (cx + w * 0.30, cy - h * 0.05)
    tassel_end = (cx + w * 0.42, cy + h * 0.55)
    draw.line([tassel_start, tassel_end], fill=WHITE, width=max(2, round(size * 0.012 * scale)))
    ball_r = size * 0.035 * scale
    draw.ellipse(
        [tassel_end[0] - ball_r, tassel_end[1] - ball_r, tassel_end[0] + ball_r, tassel_end[1] + ball_r],
        fill=WHITE,
    )


def make_icon(size: int, *, maskable: bool = False, corner_radius_ratio: float = 0.22) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    if maskable:
        # Maskable icons must fill edge-to-edge with no rounding — the OS applies its own mask shape.
        draw.rectangle([0, 0, size, size], fill=BLUE)
        draw_mark(draw, size, scale=0.6)
    else:
        radius = size * corner_radius_ratio
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=BLUE)
        draw_mark(draw, size, scale=1.0)

    return img


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    make_icon(192).save(OUTPUT_DIR / "pwa-192x192.png")
    make_icon(512).save(OUTPUT_DIR / "pwa-512x512.png")
    make_icon(512, maskable=True).save(OUTPUT_DIR / "maskable-icon-512x512.png")

    # Apple touch icon: solid square background (iOS applies its own corner rounding), no alpha needed.
    apple_icon = make_icon(180, corner_radius_ratio=0).convert("RGB")
    apple_icon.save(OUTPUT_DIR / "apple-touch-icon-180x180.png")

    print(f"Wrote 4 icons to {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
