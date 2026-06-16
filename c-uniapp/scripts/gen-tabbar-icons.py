#!/usr/bin/env python3
"""生成金霖二十四养 TabBar 剪影图标 (81x81 PNG, 无外部依赖)"""
import struct, zlib, math, os

SIZE = 81
OUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'static', 'tabbar')
COLOR_GRAY  = (0x7B, 0x83, 0x78, 0xFF)   # inactive
COLOR_GREEN = (0x6F, 0x9F, 0x58, 0xFF)   # active
ALPHA = (0,0,0,0)

# --------------- PNG primitives ---------------
def make_chunk(ctype, data):
    c = ctype + data
    return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xFFFFFFFF)

def save_png(path, pixels, w, h):
    """pixels: flat list of (r,g,b,a) tuples, row-major"""
    raw = b''
    for y in range(h):
        raw += b'\x00'  # filter byte (none)
        for x in range(w):
            raw += bytes(pixels[y * w + x])
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = b'\x89PNG\r\n\x1a\n'
    png += make_chunk(b'IHDR', ihdr)
    png += make_chunk(b'IDAT', zlib.compress(raw))
    png += make_chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f'  ✓ {path}')

# --------------- drawing helpers ---------------
def blank():
    return [ALPHA] * (SIZE * SIZE)

def draw_pixel(px, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[y * SIZE + x] = color

def draw_circle(px, cx, cy, r, color):
    for y in range(max(0, int(cy-r)), min(SIZE, int(cy+r+1))):
        for x in range(max(0, int(cx-r)), min(SIZE, int(cx+r+1))):
            if (x-cx)**2 + (y-cy)**2 <= r**2:
                px[y * SIZE + x] = color

def draw_rect(px, x1, y1, x2, y2, color):
    for y in range(max(0, y1), min(SIZE, y2+1)):
        for x in range(max(0, x1), min(SIZE, x2+1)):
            px[y * SIZE + x] = color

def draw_line(px, x1, y1, x2, y2, w, color):
    """thick line using Bresenham + perpendicular spread"""
    dx, dy = abs(x2-x1), abs(y2-y1)
    sx = 1 if x1 < x2 else -1
    sy = 1 if y1 < y2 else -1
    err = dx - dy
    cx, cy = x1, y1
    while True:
        for ox in range(-w//2, (w+1)//2):
            for oy in range(-w//2, (w+1)//2):
                draw_pixel(px, cx+ox, cy+oy, color)
        if cx == x2 and cy == y2:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy; cx += sx
        if e2 < dx:
            err += dx; cy += sy

# --------------- icons ---------------
C = SIZE // 2  # center

def icon_leaf(color):
    """首页 — 叶子剪影"""
    px = blank()
    # Stem
    draw_line(px, C, C+20, C, SIZE-8, 4, color)
    # Main leaf body
    for y in range(8, C+22):
        for x in range(10, SIZE-10):
            # leaf shape: wider at bottom, pointy at top and bottom
            leaf_w = int(28 * math.sin(math.pi * (y - 8) / (C+14)))
            if abs(x - C) <= leaf_w:
                dx = abs(x - C) / max(leaf_w, 1)
                # add vein gap
                if dx > 0.8 or abs(y - C + 5) > leaf_w * 0.6 or dx < 0.15:
                    px[y * SIZE + x] = color
    # Center vein line
    draw_line(px, C-1, 14, C-1, C+18, 2, color)
    # Small leaf tip highlight — leave as is
    return px

def icon_basket(color):
    """购物车 — 竹篮剪影"""
    px = blank()
    # Handle (arch)
    for y in range(6, 32):
        w = int(18 * math.sin(math.pi * (y-6) / 26))
        if w > 0:
            draw_rect(px, C-w, y, C+w, y+3, color)
    # Basket body (trapezoid)
    top_w, bot_w = 24, 18
    for y in range(30, 62):
        progress = (y - 30) / 32.0
        w = int(top_w + (bot_w - top_w) * progress)
        draw_rect(px, C-w, y, C+w, y+1, color)
    # Basket bottom rim
    draw_rect(px, C-20, 60, C+20, 63, color)
    # Basket base
    draw_rect(px, C-14, 63, C+14, 65, color)
    return px

def icon_scroll(color):
    """订单 — 书简/卷轴剪影"""
    px = blank()
    # Scroll body
    draw_rect(px, C-16, 16, C+16, 60, color)
    # Horizontal text lines (cutouts)
    for ly in [24, 33, 42, 51]:
        draw_rect(px, C-10, ly, C+10, ly+1, ALPHA)
    # Top roll
    draw_rect(px, C-19, 12, C+19, 18, color)
    draw_rect(px, C-17, 10, C+17, 12, color)
    # Bottom roll
    draw_rect(px, C-19, 60, C+19, 66, color)
    draw_rect(px, C-17, 66, C+17, 68, color)
    # Folded corner hint
    draw_rect(px, C+5, 16, C+16, 24, ALPHA)
    return px

def icon_person(color):
    """我的 — 人物剪影（极简东方）"""
    px = blank()
    # Head
    draw_circle(px, C, 22, 14, color)
    # Body (peaked shoulders, narrow waist — 东方含蓄)
    for y in range(36, 72):
        progress = (y - 36) / 36.0
        if progress < 0.25:
            w = int(22 + 4 * progress / 0.25)  # shoulders widening
        elif progress < 0.6:
            w = int(26 - 6 * ((progress-0.25)/0.35))  # taper to waist
        else:
            w = int(20 + 4 * ((progress-0.6)/0.4))  # slight flare
        draw_rect(px, C-w, y, C+w, y, color)
    return px

# --------------- main ---------------
icons = {
    'home':     ('home',     icon_leaf),
    'cart':     ('cart',     icon_basket),
    'orders':   ('orders',   icon_scroll),
    'my':       ('my',       icon_person),
}

for name, (fname, fn) in icons.items():
    for variant, clr in [('', COLOR_GRAY), ('-active', COLOR_GREEN)]:
        path = os.path.join(OUT_DIR, f'{fname}{variant}.png')
        px = fn(clr)
        save_png(path, px, SIZE, SIZE)
