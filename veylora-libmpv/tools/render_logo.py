#!/usr/bin/env python3
"""Renders the OwnTV libmpv logo into extras/.

    python tools/render_logo.py --brand <OwnTV_Core>/tools/brand/render_brand.py --font <Roboto.ttf>

The OwnTV card is drawn by OwnTV_Core's own brand script (Eggshell, the default colour), so it stays
identical to the apps' logo. Next to it: a flat mpv-style mark (purple tile, ring, white disc, play) —
flat like the OwnTV brand, no gradients — then the #227 "owntv" wordmark and "libmpv" in the font.
Writes logo_light.png (for light backgrounds), logo_dark.png (for dark ones) and icon.png.
"""
import argparse
import importlib.util
import os

from PIL import Image, ImageDraw

MPV = dict(tile='#6D2A8C', ring='#8A4AA6', disc='#FFFFFF', play='#6D2A8C')   # flat purples, mpv's hue
INK = '#1F2328'


def load_brand(path):
    spec = importlib.util.spec_from_file_location('render_brand', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def mpv_mark(px):
    """Flat mpv-style mark in the same 56-unit box as the OwnTV card (VB_TIGHT), so both sit alike."""
    ss = 4
    s = px * ss / 56.0
    img = Image.new('RGBA', (px * ss, px * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # the tile matches the OwnTV card's footprint: x 32..76, y 29..75 of 108 → 6..50 / 3..49 of 56
    d.rounded_rectangle((6 * s, 3 * s, 50 * s, 49 * s), radius=10 * s, fill=MPV['tile'])
    cx, cy = 28 * s, 26 * s
    for r, colour in ((16.5, MPV['ring']), (11.5, MPV['disc'])):
        d.ellipse((cx - r * s, cy - r * s, cx + r * s, cy + r * s), fill=colour)
    tri = [(cx - 3.2 * s, cy - 5.2 * s), (cx + 5.6 * s, cy), (cx - 3.2 * s, cy + 5.2 * s)]
    d.polygon(tri, fill=MPV['play'])
    rj = 1.1 * s                                        # round the play's corners like the OwnTV play
    d.line(tri + [tri[0]], fill=MPV['play'], width=int(2 * rj), joint='curve')
    for x, y in tri:
        d.ellipse((x - rj, y - rj, x + rj, y + rj), fill=MPV['play'])
    return img.resize((px, px), Image.LANCZOS)


def lockup(b, mark_px, font_px, font_path, own, tv):
    p = b.PAL['eggshell']
    f = b.font(font_path, font_px)
    tr = -0.025 * font_px
    gap = round(mark_px * .26)
    plus_w = round(mark_px * .42)
    # "owntv" is the #227 wordmark the apps use (drawn by the brand script); " libmpv" is set in the font.
    word = b.wordmark_227(round(mark_px * .6 * 988 / 182), tv, own)
    lib = [(' libmpv', MPV['tile'] if own == INK else '#C08BDB')]
    lib_w = b.word_width(' libmpv', f, tr) + tr
    w = mark_px * 2 + plus_w + gap + word.width + int(lib_w) + 8
    h = max(mark_px, int(font_px * 1.2)) + 8
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    y = (h - mark_px) // 2
    img.alpha_composite(b.mark(p, mark_px, 'flat', b.VB_TIGHT), (0, y))
    d = ImageDraw.Draw(img)
    # "+" between the two marks, in the wordmark's ink
    cx, cy, arm, th = mark_px + plus_w / 2, h / 2, mark_px * .11, max(2, round(mark_px * .045))
    d.rounded_rectangle((cx - arm, cy - th / 2, cx + arm, cy + th / 2), radius=th / 2, fill=own)
    d.rounded_rectangle((cx - th / 2, cy - arm, cx + th / 2, cy + arm), radius=th / 2, fill=own)
    img.alpha_composite(mpv_mark(mark_px), (mark_px + plus_w, y))
    x = mark_px * 2 + plus_w + gap
    img.alpha_composite(word, (x, (h - word.height) // 2))
    asc = f.getbbox('libmpv', anchor='ls')
    base = h / 2 - (asc[1] + asc[3]) / 2
    b.wordmark(d, (x + word.width, base), lib, f, tr)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--brand', required=True, help="OwnTV_Core's tools/brand/render_brand.py")
    ap.add_argument('--font', required=True, help='Roboto variable font (the apps\' wordmark font)')
    ap.add_argument('--out', default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'extras'))
    a = ap.parse_args()
    b = load_brand(a.brand)
    p = b.PAL['eggshell']
    os.makedirs(a.out, exist_ok=True)
    lockup(b, 120, 84, a.font, INK, p['ui']).save(os.path.join(a.out, 'logo_light.png'))
    lockup(b, 120, 84, a.font, b.WORD_OWN, p['acc']).save(os.path.join(a.out, 'logo_dark.png'))
    icon = Image.new('RGBA', (512 * 2 + 40, 512), (0, 0, 0, 0))
    icon.alpha_composite(b.mark(p, 512, 'flat', b.VB_TIGHT), (0, 0))
    icon.alpha_composite(mpv_mark(512), (512 + 40, 0))
    icon.save(os.path.join(a.out, 'icon.png'))
    print('written to', a.out)


if __name__ == '__main__':
    main()
