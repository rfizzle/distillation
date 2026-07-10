# Distillation — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source column
> flags a pixel asset with no `.glyph` source yet — a candidate for the glyph
> pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths are under
> `src/main/resources/` unless noted. Rendered previews under `art/glyphs/` are
> gitignored review artifacts, not entries.

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Bottle brand glyph 16×16 (Jade/recipe viewers, suite footer) | `/glyph` → `art/glyphs/bottle-16.glyph` | `assets/distillation/textures/gui/bottle.png` — (planned, with implementation) |
| Murky Draught item 16×16 | `/glyph` → `art/glyphs/murky_draught.glyph` | `assets/distillation/textures/item/murky_draught.png` — (planned, with implementation) |
| Half-draught bottle item 16×16 (model override) | `/glyph` → `art/glyphs/draught_half.glyph` | `assets/distillation/textures/item/draught_half.png` — (planned, with implementation) |
| Antidote bottle item 16×16 (greyscale, tint-layered per cure) | `/glyph` → `art/glyphs/antidote.glyph` | `assets/distillation/textures/item/antidote.png` — (planned, with implementation) |
| Recipes-page tab button 16×16 | `/glyph` → `art/glyphs/recipes_tab.glyph` | `assets/distillation/textures/gui/recipes_tab.png` — (planned, with implementation) |
| Batch-row slot/steam sprite | `/glyph` → `art/glyphs/batch_row.glyph` | `assets/distillation/textures/gui/batch_row.png` — (planned, with implementation) |
| Discovery chime | `/sfx` → `art/audio/recipe_learned.sfx` | `assets/distillation/sounds/recipe_learned.ogg` — (planned, with implementation) |
| Murky fizzle | `/sfx` → `art/audio/murky_fizzle.sfx` | `assets/distillation/sounds/murky_fizzle.ogg` — (planned, with implementation) |
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` → `site/assets/logo.png` — (planned, branding) |
| Mod icon 128×128 | Gemini (prompt in `DESIGN.md` §4) or `/glyph` size ladder | `art/icon-128.png` → `site/assets/icon.png`, `fabric.mod.json` icon — (planned, branding) |
| OG image 1200×630 | composed from full logo on Ink | `site/assets/og-image.png` — (planned, branding) |
| Favicon set | derived from icon-128 | `site/assets/favicon.ico`, `favicon-32.png`, `apple-touch-icon.png` — (planned, branding) |
| Bottle glyph web copy | rendered from `art/glyphs/bottle-16.glyph` | `site/assets/glyph-16.png` — (planned, with site assets) |
