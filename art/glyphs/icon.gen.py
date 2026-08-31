#!/usr/bin/env python3
"""Compose the Distillation potion-bottle mod icon as a 128px .glyph grid.

The brand motif reduced for the icon (design/DESIGN.md §1) is the single
round-bottomed potion bottle — cork stopper, copper neck band, liquid glowing
Potion Magenta with an Elixir rim highlight, one thin vapor wisp — set in a
circular stone medallion with a magenta rim-glow over a dark plum brickwork
field, the suite icon convention (cf. the sibling icon.gen.py generators).
Everything is computed deterministically: true circles for the medallion and
the flask bulb, tiling brick courses, a radial liquid glow, a sine-curl wisp.
Emitted as an ASCII-grid .glyph; glyph.py rasterizes it, so the source
re-renders byte-identically. This script also renders art/icon-128.png and
the ×4 nearest-neighbor art/icon-512.png through the vendored glyph.py.
"""
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location(
    "glyph", ROOT / ".ai/skills/mc-textures/scripts/glyph.py")
glyph = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(glyph)

N = 128
CX = CY = (N - 1) / 2.0

# ---- palette (Distillation magenta/copper over plum neutrals) ---------------
COL = {
    'ink':       '#0a0a0a',
    # magenta rim glow (alpha falloff) — Potion Magenta #C44DCC / Elixir #DA79E3
    'glow1':     '#da79e3b0',
    'glow2':     '#c44dcc80',
    'glow3':     '#c44dcc40',
    # plum stone bezel (lit upper-left), ramp up to the accents
    'bz_sh':     '#3a1440',
    'bz_dark':   '#5e2264',
    'bz_mid':    '#8e3596',
    'bz_lit':    '#c44dcc',
    'bz_spec':   '#da79e3',
    # dark plum brickwork field — Still Dark #1a0a18 / Vapor Plum #2e102c
    'br_deep':   '#140714',
    'br':        '#1a0a18',
    'br_lit':    '#2e102c',
    'mortar':    '#0d040d',
    'vig':       '#0a030a',     # inner-edge vignette
    # bricks warmed by the brew glow
    'br_warm':   '#431b45',
    'br_warm2':  '#2e1132',
    'mortar_w':  '#1e0a20',
    # bottle glass ramp — Glass #AFC6CE highlight
    'gs_hi':     '#dcecf2',
    'gs_lit':    '#afc6ce',
    'gs_mid':    '#7d95a3',
    'gs_dark':   '#4c5f6e',
    'air':       '#1f0e24',     # empty glass interior
    # cork stopper
    'ck_lit':    '#c99b66',
    'ck_mid':    '#9a7148',
    'ck_dark':   '#6b4c2e',
    # copper neck band — Copper #E77C56
    'cu_lit':    '#f5a37d',
    'cu':        '#e77c56',
    'cu_dark':   '#a84f30',
    # liquid glow ramp — Potion Magenta core, Elixir rim
    'lq_core':   '#f7d4fa',
    'lq_pale':   '#e9a5ef',
    'lq_bright': '#da79e3',
    'lq':        '#c44dcc',
    'lq_deep':   '#8e35a0',
    'lq_dark':   '#5e2170',
    # vapor wisp
    'vp':        '#a94fb8',
    'vp_dim':    '#6e2f7e',
}

G = [[None] * N for _ in range(N)]


def dist(x, y):
    return math.hypot(x - CX, y - CY)


def ang(x, y):
    return math.atan2(y - CY, x - CX)


R_IN = 46.0
R_OUT = 56.0

# ---- 1. magenta glow halo ----------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# ---- 2. plum stone bezel annulus ----------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            shade = math.cos(a - math.radians(225))          # light from UL
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

# ---- 3. dark plum brickwork field, warmed near the brew ------------------------
# bottle geometry (shared with the blit below)
BX, BY = CX, 80.0          # bulb centre
RB = 16.0                  # bulb radius
LY = BY - 5                # liquid fill line
GX, GY = CX, BY + 2        # liquid glow centre
NT = 52                    # neck/lip top
NW = 4.0                   # neck half-width

BRH, BRW = 8, 16
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (CY - R_IN)) // BRH)
        # anchor shifted +4 so no vertical joint runs down the icon's center
        # corridor (behind the neck and wisp), where a dark line half a pixel
        # off-axis reads as an off-center bottle
        off = 4 + ((BRW // 2) if (row % 2) else 0)
        my = ((y - (CY - R_IN)) % BRH) < 1          # horizontal mortar
        mx = ((x - off) % BRW) < 1                   # vertical mortar
        warm = math.hypot(x - GX, y - GY)
        if my or mx:
            G[y][x] = 'mortar_w' if warm < 38 else 'mortar'
        elif warm < 30:
            G[y][x] = 'br_warm'
        elif warm < 38:
            G[y][x] = 'br_warm2'
        else:
            tone = (row * 3 + int((x - off) // BRW)) % 5
            G[y][x] = 'br_lit' if tone == 0 else ('br_deep' if tone == 3 else 'br')
        # inner-edge vignette so the bottle pops off the field
        if d > R_IN - 5 and warm >= 30:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

# inner rim shadow ring (depth under the bezel lip)
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN - 1.5 <= d < R_IN:
            G[y][x] = 'ink'

# ---- 4. the round-bottomed potion bottle ---------------------------------------
S = {}


def put(x, y, key):
    if 0 <= x < N and 0 <= y < N:
        S[(x, y)] = key


# bulb: true circle; glass wall band, radial liquid glow, elixir meniscus
for y in range(int(BY - RB) - 1, int(BY + RB) + 2):
    for x in range(int(BX - RB) - 1, int(BX + RB) + 2):
        d = math.hypot(x - BX, y - BY)
        if d > RB:
            continue
        a = math.atan2(y - BY, x - BX)
        lit = math.cos(a - math.radians(225))            # 1 at upper-left
        if d > RB - 2.5:                                  # glass wall band
            if y < LY:
                key = ('gs_lit' if lit > 0.55 else
                       'gs_mid' if lit > 0.0 else
                       'gs_dark')
            else:                                         # wall seen through brew
                key = 'lq_dark' if lit < -0.45 else 'lq_deep'
        elif y < LY:
            key = 'air'
        elif y < LY + 2:                                  # elixir rim highlight
            key = 'lq_pale' if abs(x - GX) < 6 else 'lq_bright'
        else:                                             # radial brew glow
            dd = math.hypot(x - GX, (y - GY) / 1.2)
            key = ('lq_core' if dd < 4.5 else
                   'lq_pale' if dd < 8 else
                   'lq_bright' if dd < 11.5 else
                   'lq' if dd < 15 else 'lq_deep')
        put(x, y, key)

# specular streak on the upper-left shoulder (above the liquid)
for (x, y), key in list(S.items()):
    if y >= LY or key != 'air':
        continue
    d = math.hypot(x - BX, y - BY)
    a = math.atan2(y - BY, x - BX)
    if RB - 6.5 < d < RB - 2.6 and -2.85 < a < -1.75:
        S[(x, y)] = 'gs_lit'
    if RB - 5.2 < d < RB - 3.2 and -2.65 < a < -1.95:
        S[(x, y)] = 'gs_hi'

# neck: glass walls, empty interior, down into the shoulder
for y in range(NT, int(BY - RB) + 5):
    for x in range(int(BX - NW), int(BX + NW) + 2):
        dx = x - BX
        if abs(dx) > NW:
            continue
        if abs(dx) > NW - 1.8:
            key = 'gs_lit' if dx < 0 else 'gs_dark'
        else:
            key = 'air'
        put(x, y, key)

# cork stopper (rounded top, lit upper-left)
CT = NT - 7
for y in range(CT, NT):
    hw = 1.8 if y == CT else 2.8
    for x in range(int(BX - hw), int(BX + hw) + 2):
        if abs(x - BX) > hw:
            continue
        dx = x - BX
        key = ('ck_lit' if (y <= CT + 1 or dx < -1.2) else
               'ck_dark' if (dx > 1.4 or y >= NT - 1) else 'ck_mid')
        put(x, y, key)

# glass lip at the neck top
for y in range(NT, NT + 2):
    for x in range(int(BX - 5.2), int(BX + 5.2) + 2):
        dx = x - BX
        if abs(dx) > 5.2:
            continue
        key = ('gs_lit' if (y == NT and dx < 2.5) else
               'gs_dark' if dx > 3.0 else 'gs_mid')
        put(x, y, key)

# copper band at the neck (wraps a hair proud of the glass so it reads)
BW = NW + 0.9
for y in range(NT + 5, NT + 8):
    for x in range(int(BX - BW), int(BX + BW) + 2):
        dx = x - BX
        if abs(dx) > BW:
            continue
        key = ('cu_lit' if (y == NT + 5 or dx < -2.6) else
               'cu_dark' if (dx > 2.8 or y == NT + 7) else 'cu')
        put(x, y, key)

# ---- 5. ink-outline the bottle silhouette, then composite ----------------------
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < N and 0 <= ny < N:
            G[ny][nx] = 'ink'
for (x, y), key in S.items():
    G[y][x] = key

# ---- 6. one thin vapor wisp (no outline — it is vapor) -------------------------
# a short S-curl above the cork: 2px body tapering to 1px, fading as it rises,
# with a small detached puff at the top
for yy in range(31, CT - 1):
    xf = CX + 0.8 + 2.6 * math.sin((CT - 2 - yy) * 0.42 + 0.2)
    px = int(round(xf))
    key = 'lq_bright' if yy >= CT - 5 else ('vp' if yy >= 36 else 'vp_dim')
    if (px, yy) not in S:
        G[yy][px] = key
    if yy >= 37 and (px + 1, yy) not in S:
        G[yy][px + 1] = key
G[29][int(CX) - 1] = 'vp_dim'
G[28][int(CX)] = 'vp_dim'

# ---- emit .glyph ----------------------------------------------------------------
pool = "@$%&*+=oOxX0123456789abcdefghijklmnpqrstuvwzABCDEFGHIJKLMNPQRSTUVWZ?!~^"
used = []
for row in G:
    for c in row:
        if c and c not in used:
            used.append(c)
assert len(used) <= len(pool), f"too many colors: {len(used)}"
key2ch = {k: pool[i] for i, k in enumerate(used)}

lines = ["# Distillation potion-bottle mod icon — generated by icon.gen.py",
         f"size: {N}",
         "kind: icon",
         "ships: art/icon-128.png",
         "ships: src/main/resources/assets/distillation/icon.png",
         "ships: site/assets/icon.png",
         "ships: art/icon-512.png 512",
         "", "legend:", "  . transparent"]
for k in used:
    lines.append(f"  {key2ch[k]} {COL[k]}")
lines.append("")
lines.append("frame:")
for row in G:
    lines.append("  " + "".join(key2ch[c] if c else "." for c in row))
text = "\n".join(lines) + "\n"

OUT = ROOT / "art/glyphs/icon.glyph"
OUT.write_text(text)
print(f"wrote {OUT}  ({len(used)} colors)")

# ---- render the committed masters through the vendored glyph.py -----------------
legend, frames_rows, declared, _meta, _used = glyph.parse_spec(text)
frames_px, size = glyph.build_frames(legend, frames_rows, declared)
px = frames_px[0]
glyph.write_png(ROOT / "art/icon-128.png", px, N, N)
print(f"wrote {ROOT / 'art/icon-128.png'}  ({N}x{N})")
px512, w512, h512 = glyph.scale_nearest(px, N, N, 4)
glyph.write_png(ROOT / "art/icon-512.png", px512, w512, h512)
print(f"wrote {ROOT / 'art/icon-512.png'}  ({w512}x{h512})")
