#!/usr/bin/env python3
"""Compose the pixel-art exploration variant of the Distillation logo.

The shipped hero master (art/logo.png) is an illustrated Gemini render — see
design/DESIGN.md §4; this generator's output lives in art/exploration/ as the
retained procedural alternative.

The suite logo formula (concord design/DESIGN-SYSTEM.md §4): dark stone
brickwork, one central glowing motif in a circular medallion, the mod name in
blocky pixel type below. Distillation's motif (design/DESIGN.md §1) is the
brewing stand mid-brew — three round-bottomed bottles glowing potion magenta
on copper-blade arms, magenta vapor curling up past the frame's top edge —
with thin copper piping threaded along the wall's top course, dripping a
single glowing drop from the keystone fitting. Everything is computed
deterministically — brick courses, the medallion (shared composition with
icon.gen.py), parametric flasks, and a bitmap pixel font for the wordmark and
subtitle — and written straight to PNG through the vendored glyph.py encoder
(the canvas is wide, and .glyph frames are square by design, so the generator
itself is the committed re-renderable source). Native grid 320×192, shipped
at 1280×768 by integer nearest-neighbor upscale. Light from the upper left;
palette per design/DESIGN.md.
"""
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location(
    "glyph", ROOT / ".ai/skills/mc-textures/scripts/glyph.py")
glyph = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(glyph)

W, H = 320, 192
SCALE_OUT = 4

MCX, MCY = 160.0, 72.0          # medallion centre
R_IN, R_OUT = 42.0, 52.0

COL = {
    # background brickwork (near-black plums, a step darker than the field)
    'bg':        '#150816', 'bg_lit': '#1d0b1e', 'bg_deep': '#100612',
    'bg_dk':     '#0c040d', 'bg_dk2': '#080409',
    'bg_mortar': '#0a040b', 'bg_mortar_dk': '#070308',
    'star':      '#7e3d8a', 'star_dim': '#4e2458',
    # medallion halo (pre-blended over brick — canvas is opaque)
    'glow1':     '#752e7a', 'glow2': '#522056', 'glow3': '#341437',
    # plum stone bezel
    'ink':       '#0a0a0a',
    'bz_sh':     '#3a1440', 'bz_dark': '#5e2264', 'bz_mid': '#8e3596',
    'bz_lit':    '#c44dcc', 'bz_spec': '#da79e3',
    # medallion brick field — Still Dark / Vapor Plum
    'br_deep':   '#140714', 'br': '#1a0a18', 'br_lit': '#2e102c',
    'mortar':    '#0d040d', 'vig': '#0a030a',
    'br_warm':   '#431b45', 'br_warm2': '#2e1132', 'mortar_w': '#1e0a20',
    # brewing-stand base (neutral stone greys)
    'base_lit':  '#6e6376', 'base': '#4a4152', 'base_dk': '#332b3a',
    # copper — rod, blade arms, piping, keystone fitting
    'cu_lit':    '#f5a37d', 'cu': '#e77c56', 'cu_dark': '#a84f30',
    'cu_sh':     '#6e3320',
    # bottle glass ramp — Glass #AFC6CE
    'gs_hi':     '#dcecf2', 'gs_lit': '#afc6ce', 'gs_mid': '#7d95a3',
    'gs_dark':   '#4c5f6e', 'air': '#1f0e24',
    # cork stoppers
    'ck_lit':    '#c99b66', 'ck_mid': '#9a7148', 'ck_dark': '#6b4c2e',
    # liquid glow ramp — Potion Magenta core, Elixir rim
    'lq_core':   '#f7d4fa', 'lq_pale': '#e9a5ef', 'lq_bright': '#da79e3',
    'lq':        '#c44dcc', 'lq_deep': '#8e35a0', 'lq_dark': '#5e2170',
    # vapor wisps
    'vp':        '#a94fb8', 'vp_dim': '#6e2f7e',
    # glow of the falling drop (pre-blended over brick)
    'dg1':       '#5e2a68', 'dg2': '#3a1743',
    # wordmark (magenta gradient face #C44DCC → #DA79E3, extruded)
    'wm_hi':     '#da79e3', 'wm_mid': '#c44dcc', 'wm_low': '#93309c',
    'wm_ex':     '#3f1245', 'wm_glow1': '#6e2a76', 'wm_glow2': '#43184a',
    # subtitle (Copper face)
    'st_hi':     '#f5a37d', 'st_low': '#e77c56',
    'st_ex':     '#6e3320', 'st_glow': '#2e1226',
}


def rgba(hexstr):
    hexstr = hexstr.lstrip('#')
    return (int(hexstr[0:2], 16), int(hexstr[2:4], 16), int(hexstr[4:6], 16), 255)


PAL = {k: rgba(v) for k, v in COL.items()}
G = [['bg'] * W for _ in range(H)]
BG_KEYS = {'bg', 'bg_lit', 'bg_deep', 'bg_dk', 'bg_dk2',
           'bg_mortar', 'bg_mortar_dk'}

# ---- 1. background brickwork with corner vignette and sparse motes ----------
BRW2, BRH2 = 32, 16
DARKER = {'bg_lit': 'bg', 'bg': 'bg_deep', 'bg_deep': 'bg_dk', 'bg_dk': 'bg_dk2'}
for y in range(H):
    for x in range(W):
        row = y // BRH2
        off = (BRW2 // 2) if (row % 2) else 0
        my = (y % BRH2) < 2
        mx = ((x - off) % BRW2) < 2
        d = max(abs(x - W / 2) / (W / 2), abs(y - H / 2) / (H / 2))
        if my or mx:
            G[y][x] = 'bg_mortar_dk' if d > 0.8 else 'bg_mortar'
            continue
        tone = (row * 3 + (x - off) // BRW2) % 5
        key = 'bg_lit' if tone == 0 else ('bg_deep' if tone == 3 else 'bg')
        if d > 0.78:
            key = DARKER[key]
        if d > 0.96:
            key = DARKER.get(key, key)
        G[y][x] = key

# sparse vapor motes drifting in the dark (deterministic positions)
STARS = [(26, 34), (54, 66), (88, 22), (32, 122), (286, 30), (262, 74),
         (298, 118), (40, 160), (290, 162), (16, 90), (302, 52), (72, 108)]
for i, (sx, sy) in enumerate(STARS):
    G[sy][sx] = 'star'
    if i % 4 == 0:  # a few soft crosses, most stay single points
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= sy + dy < H and 0 <= sx + dx < W:
                G[sy + dy][sx + dx] = 'star_dim'

# ---- 2. copper piping threaded along the top course --------------------------
for x in range(W):
    G[1][x] = 'ink'
    G[2][x] = 'cu_lit'
    G[3][x] = 'cu'
    G[4][x] = 'cu_dark'
    G[5][x] = 'ink'
# pipe collars where the run is bracketed to the wall
for bx in (24, 88, 232, 296):
    for y in range(0, 7):
        for xx in range(bx, bx + 4):
            G[y][xx] = 'cu_lit' if y <= 1 else ('cu' if xx == bx else 'cu_dark')
    for y in range(0, 7):
        G[y][bx - 1] = 'ink'
        G[y][bx + 4] = 'ink'
    for xx in range(bx - 1, bx + 5):
        G[7][xx] = 'ink'

# keystone fitting at the centre of the run (wider at the top, tapering down)
for y in range(0, 10):
    hw = 8 - y * 0.34
    for x in range(int(MCX - hw), int(MCX + hw) + 1):
        dx = x - MCX
        if abs(dx) > hw:
            continue
        edge = abs(dx) > hw - 1.2 or y == 9
        if edge:
            G[y][x] = 'ink'
        else:
            G[y][x] = ('cu_lit' if (y <= 1 or dx < -hw + 3) else
                       'cu_dark' if (dx > hw - 3.2 or y >= 8) else 'cu')
# nozzle
for y in range(10, 12):
    for x in (158, 159, 160, 161):
        G[y][x] = 'cu_dark' if 158 < x < 161 else 'ink'

# ---- 3. medallion halo (pre-blended rings over the brick) --------------------


def dist(x, y):
    return math.hypot(x - MCX, y - MCY)


def ang(x, y):
    return math.atan2(y - MCY, x - MCX)


for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# the single glowing drop, mid-fall between the nozzle and the medallion
DROP = [(13, (159, 160), 'lq'),
        (14, (158, 159, 160, 161), 'lq_bright'),
        (15, (157, 158, 159, 160, 161, 162), 'lq_pale'),
        (16, (157, 158, 159, 160, 161, 162), 'lq_bright'),
        (17, (158, 159, 160, 161), 'lq')]
drop_px = set()
for y, xs, key in DROP:
    for x in xs:
        G[y][x] = key
        drop_px.add((x, y))
G[15][159] = 'lq_core'
G[15][160] = 'lq_core'
for (x, y) in list(drop_px):                      # soft glow, over brick only
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            p = (x + dx, y + dy)
            if p in drop_px or not (0 <= p[0] < W and 0 <= p[1] < H):
                continue
            ring = max(abs(dx), abs(dy))
            if G[p[1]][p[0]] in BG_KEYS:
                G[p[1]][p[0]] = 'dg1' if ring <= 1 else 'dg2'

# ---- 4. plum stone bezel annulus ---------------------------------------------
for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            shade = math.cos(a - math.radians(225))
            bump = 0.6 * math.sin(a * 8) + 0.4 * math.sin(a * 15 + 1.1)
            base = shade + bump * 0.3
            if d >= R_OUT - 1.2 or d <= R_IN + 1.0:
                G[y][x] = 'ink'
            elif base > 0.85:
                G[y][x] = 'bz_spec'
            elif base > 0.25:
                G[y][x] = 'bz_lit'
            elif base > -0.35:
                G[y][x] = 'bz_mid'
            elif base > -0.8:
                G[y][x] = 'bz_dark'
            else:
                G[y][x] = 'bz_sh'

# ---- 5. medallion brick field, warmed near the three brews --------------------
GLOWS = [(137.0, 73.0), (183.0, 73.0), (160.0, 80.0)]   # per-bottle glow centres
BRH, BRW = 8, 16
for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (MCY - R_IN)) // BRH)
        off = 4 + ((BRW // 2) if (row % 2) else 0)   # centre corridor kept clear
        my = ((y - (MCY - R_IN)) % BRH) < 1
        mx = ((x - off) % BRW) < 1
        warm = min(math.hypot(x - gx, y - gy) for gx, gy in GLOWS)
        if my or mx:
            G[y][x] = 'mortar_w' if warm < 23 else 'mortar'
        elif warm < 16:
            G[y][x] = 'br_warm'
        elif warm < 23:
            G[y][x] = 'br_warm2'
        else:
            tone = (row * 3 + int((x - off) // BRW)) % 5
            G[y][x] = 'br_lit' if tone == 0 else ('br_deep' if tone == 3 else 'br')
        if d > R_IN - 5 and warm >= 16:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

for y in range(H):
    for x in range(W):
        if R_IN - 1.5 <= dist(x, y) < R_IN:
            G[y][x] = 'ink'

# ---- 6. the brewing stand mid-brew ---------------------------------------------
S = {}


def sput(x, y, key):
    x, y = int(x), int(y)
    if 0 <= x < W and 0 <= y < H:
        S[(x, y)] = key


# two-tier stone base (drawn first; everything sits over it)
for y in range(96, 100):                      # upper tier
    for x in range(151, 170):
        sput(x, y, 'base_lit' if y == 96 else ('base_dk' if y == 99 else 'base'))
for y in range(100, 104):                     # wider footing
    for x in range(145, 176):
        if y == 100:
            key = 'base_lit'
        elif y >= 103:
            key = 'base_dk'
        else:
            key = 'base_dk' if ((x // 5 + y) % 4) == 0 else 'base'
        sput(x, y, key)

# copper rod (drawn before the centre bottle, which hangs in front of it)
for y in range(44, 97):
    sput(159, y, 'cu')
    sput(160, y, 'cu_dark')
for y in range(42, 44):                      # finial
    for x in range(158, 162):
        sput(x, y, 'cu_lit' if y == 42 else 'cu')

# copper-blade arms out to the side bottles
for tip_x in (138, 182):
    steps = 22
    for i in range(steps + 1):
        t = i / steps
        x = 160 + (tip_x - 160) * t
        y = 50 + 7 * t
        sput(x, y, 'cu_lit' if t < 0.35 else 'cu')
        sput(x, y + 1, 'cu')
        sput(x, y + 2, 'cu_dark')


def bottle(bx, by, rb, neck_h):
    """A mini round-bottomed flask: bulb centre (bx, by), bulb radius rb."""
    ly = by - rb * 0.25                       # liquid fill line
    gx, gy = bx, by + 1                       # glow centre
    for y in range(int(by - rb) - 1, int(by + rb) + 2):
        for x in range(int(bx - rb) - 1, int(bx + rb) + 2):
            d = math.hypot(x - bx, y - by)
            if d > rb:
                continue
            a = math.atan2(y - by, x - bx)
            lit = math.cos(a - math.radians(225))
            if d > rb - 2:                    # glass wall band
                if y < ly:
                    key = ('gs_lit' if lit > 0.5 else
                           'gs_mid' if lit > 0.0 else 'gs_dark')
                else:
                    key = 'lq_dark' if lit < -0.45 else 'lq_deep'
            elif y < ly:
                key = 'air'
            elif y < ly + 1.5:                # elixir meniscus
                key = 'lq_pale' if abs(x - gx) < 3 else 'lq_bright'
            else:
                dd = math.hypot(x - gx, (y - gy) / 1.2)
                key = ('lq_core' if dd < 2.5 else
                       'lq_pale' if dd < 4.5 else
                       'lq_bright' if dd < 6.5 else
                       'lq' if dd < rb - 1.5 else 'lq_deep')
            sput(x, y, key)
    # specular glint on the upper-left shoulder
    gxs = int(bx - rb * 0.55)
    gys = int(by - rb * 0.55)
    sput(gxs, gys, 'gs_hi')
    sput(gxs + 1, gys - 1, 'gs_hi')
    # neck
    nt = int(by - rb - neck_h)
    for y in range(nt, int(by - rb) + 3):
        for x in range(int(bx - 2.2), int(bx + 2.2) + 2):
            dx = x - bx
            if abs(dx) > 2.2:
                continue
            key = ('gs_lit' if dx < -1.0 else
                   'gs_dark' if dx > 1.2 else 'air')
            sput(x, y, key)
    # lip
    for y in range(nt - 1, nt + 1):
        for x in range(int(bx - 3.2), int(bx + 3.2) + 2):
            dx = x - bx
            if abs(dx) > 3.2:
                continue
            key = ('gs_lit' if (y == nt - 1 and dx < 1.5) else
                   'gs_dark' if dx > 1.8 else 'gs_mid')
            sput(x, y, key)
    # cork
    ct = nt - 4
    for y in range(ct, nt - 1):
        hw = 1.0 if y == ct else 1.6
        for x in range(int(bx - hw), int(bx + hw) + 2):
            dx = x - bx
            if abs(dx) > hw:
                continue
            key = ('ck_lit' if (y == ct or dx < -0.6) else
                   'ck_dark' if (dx > 0.8 or y == nt - 2) else 'ck_mid')
            sput(x, y, key)


bottle(137.5, 72.0, 8.0, 5)      # left
bottle(182.5, 72.0, 8.0, 5)      # right
bottle(160.0, 79.0, 9.0, 5)      # centre, hanging in front of the rod

# ink-outline the stand silhouette, then composite
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < W and 0 <= ny < H:
            G[ny][nx] = 'ink'
for (x, y), key in S.items():
    G[y][x] = key

# ---- 7. vapor wisps curling up past the frame's top edge -----------------------


def wisp(x0, y_top, y_base, amp, phase, thick_from, brighten_bezel):
    for yy in range(y_top, y_base + 1):
        xf = x0 + amp * math.sin((y_base - yy) * 0.35 + phase)
        px = int(round(xf))
        if yy >= y_base - 5:
            key = 'lq_bright'
        elif yy >= y_base - 14:
            key = 'vp'
        elif brighten_bezel and 24 <= yy < 38:
            key = 'lq_pale'                   # stays luminous over the bezel
        elif yy >= 18:
            key = 'vp'
        else:
            key = 'vp_dim'
        if (px, yy) not in S:
            G[yy][px] = key
        if yy >= thick_from and (px + 1, yy) not in S:
            G[yy][px + 1] = key


wisp(137, 15, 52, 2.2, 0.3, 45, True)         # left, past the frame top
wisp(183, 17, 52, 2.2, 2.1, 45, True)         # right, past the frame top
wisp(160, 36, 58, 2.6, 1.0, 52, False)        # centre, shorter

# ---- 8. wordmark & subtitle -----------------------------------------------------
FONT57 = {
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
}
FONT35 = {
    'A': ["111", "101", "111", "101", "101"],
    'C': ["111", "100", "100", "100", "111"],
    'E': ["111", "100", "110", "100", "111"],
    'F': ["111", "100", "110", "100", "100"],
    'H': ["101", "101", "111", "101", "101"],
    'I': ["111", "010", "010", "010", "111"],
    'L': ["100", "100", "100", "100", "111"],
    'M': ["10001", "11011", "10101", "10001", "10001"],
    'N': ["1001", "1101", "1011", "1001", "1001"],
    'O': ["111", "101", "101", "101", "111"],
    'R': ["110", "101", "110", "101", "101"],
    'T': ["111", "010", "010", "010", "010"],
    'U': ["101", "101", "101", "101", "111"],
    'V': ["101", "101", "101", "101", "010"],
    'Y': ["101", "101", "010", "010", "010"],
    ' ': ["00", "00", "00", "00", "00"],
}


def text_cells(text, font, scale, gap):
    """Face-pixel set for a text run, plus its total width."""
    cells, cx = set(), 0
    for chn in text:
        rows = font[chn]
        for ry, rrow in enumerate(rows):
            for rx, bit in enumerate(rrow):
                if bit == '1':
                    for sy in range(scale):
                        for sx in range(scale):
                            cells.add((cx + rx * scale + sx, ry * scale + sy))
        cx += len(rows[0]) * scale + gap
    return cells, cx - gap


def emboss(cells, x0, y0, face_of, ex_key, glow1, glow2, ex=2):
    """Paint glow rings, ink outline, extrusion, then the gradient face."""
    face = {(x0 + cx, y0 + cy) for cx, cy in cells}
    extr = {(x + ex, y + ex) for x, y in face} - face
    sil = face | extr
    ring1, ring2 = set(), set()
    for (x, y) in sil:
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                p = (x + dx, y + dy)
                if p in sil:
                    continue
                (ring1 if max(abs(dx), abs(dy)) <= 1 else ring2).add(p)
    ring2 -= ring1
    for x, y in ring2:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = glow2
    for x, y in ring1:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = 'ink'
    if glow1:
        for x, y in ring2:
            if 0 <= x < W and 0 <= y < H and (x + y) % 2 == 0:
                G[y][x] = glow1
    for x, y in extr:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = ex_key
    for x, y in face:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = face_of(y - y0)
    return sil


WM_SCALE, WM_GAP = 4, 4
wm_cells, wm_w = text_cells("DISTILLATION", FONT57, WM_SCALE, WM_GAP)
WMX, WMY = (W - wm_w) // 2, 136


def wm_face(row):
    return 'wm_hi' if row < 9 else ('wm_mid' if row < 19 else 'wm_low')


emboss(wm_cells, WMX, WMY, wm_face, 'wm_ex', 'wm_glow1', 'wm_glow2', ex=2)

ST_SCALE, ST_GAP = 2, 2
st_cells, st_w = text_cells("MINECRAFT ALCHEMY OVERHAUL", FONT35, ST_SCALE, ST_GAP)
STX, STY = (W - st_w) // 2, 172


def st_face(row):
    return 'st_hi' if row < 5 else 'st_low'


emboss(st_cells, STX, STY, st_face, 'st_ex', None, 'st_glow', ex=1)

# ---- 9. upscale and write --------------------------------------------------------
px = []
for y in range(H):
    for _sy in range(SCALE_OUT):
        row = []
        for x in range(W):
            row.extend([PAL[G[y][x]]] * SCALE_OUT)
        px.extend(row)
pixels = px
OUT = ROOT / "art/exploration/logo-pixel.png"
glyph.write_png(OUT, pixels, W * SCALE_OUT, H * SCALE_OUT)
print(f"wrote {OUT}  ({W * SCALE_OUT}x{H * SCALE_OUT})")

