# Distillation — Design Specification

> Alchemy Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Distillation makes brewing a craft you learn instead of a chart you look up: a stand that teaches, potions worth planning around, cures that are decisions. The name evokes patient refinement — running something through the still until only the essential remains. The visual language draws from **the alchemist's bench**: glass bottles, rising vapor, copper fittings, and the magenta glow of a live brew. This is the mod's one mythic register (suite `VISION.md` §2): the still and the drop.

### Tagline

*"Every drop counts."*

### Motif

The motif object is the **alchemist's still** — an alembic bench mid-distillation: a copper retort over a flame, coiled copper piping, round-bottomed flasks of glowing magenta. It may appear in the logo, site headers, and flavor art; it never appears in another mod's assets. The 16×16 glyph reduces the motif to a single round-bottomed potion bottle with a rising vapor wisp.

### Logo Description

**Full logo (`art/logo.png`):** An illustrated pixel-art render, 2784×1504. A rune-carved circular stone medallion with a neon potion-magenta ring glow, set on dark plum brickwork threaded with copper pipes along the top courses and shadowed vines with violet blooms in the corners; a single glowing magenta drop falls from above the keystone. Inside the medallion, the alchemist's still: a copper alembic over a small burner flame, feeding round-bottomed and conical flasks of glowing magenta liquid through coiled copper piping, with rose-quartz crystals and an open rune-marked folio on the stone bench. Below, "DISTILLATION" in blocky beveled pixel type in the magenta gradient, with "MINECRAFT ALCHEMY OVERHAUL" as the subtitle line. A procedural pixel-art alternative is retained at `art/exploration/logo-pixel.png` (`art/glyphs/logo.gen.py`).

**Icon (`art/icon-128.png`):** The single potion bottle isolated — round-bottomed flask, cork stopper, liquid glowing potion magenta with a copper-highlight rim, one vapor wisp rising — against a dark/transparent background. Reads cleanly at 128×128.

**Glyph (`art/glyphs/bottle-16.glyph`):** A 16×16 round-bottomed potion bottle — magenta liquid, pale glass highlight, a two-pixel vapor wisp, `ink` outline — for Jade/WTHIT and recipe-viewer contexts. Distillation has no HUD slot (§2 below), so this glyph never renders as a HUD element.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary surface | Still Dark | `#1a0a18` | Backgrounds, dark surfaces |
| Secondary surface | Vapor Plum | `#2e102c` | Mid-tones, card backgrounds |
| Accent 1 | Potion Magenta | `#C44DCC` | Glows, highlights, headings, interactive elements |
| Accent 2 | Copper | `#E77C56` | Fittings, warm highlights, secondary accents, links |
| Bright | Elixir | `#DA79E3` | Hover states, heading gradient end, emphasis |
| Working shade | Murk | `#5E5548` | Murky Draught liquid, failed-brew greys |
| Working shade | Glass | `#AFC6CE` | Bottle glass highlights |

Shared neutrals (text and surfaces) follow the standard tokens as-is — `--color-bone`, `--color-ash`, `--color-smoke`, `--color-ink`, `--color-card`, `--color-elevated` — see concord [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §1.

**Pairing-rule check (DESIGN-SYSTEM §2, all rows including reserved):** magenta-with-copper is Distillation's member row in the standard. Potion Magenta sits near Meridian's Arcane Purple on the wheel, but purple-with-gold reads Meridian and magenta-with-copper reads Distillation — no shared accent. Copper sits in the same warm family as Stratum's reserved Copper Orange and Tribulation's Ember; sharing at most one accent is permitted, and copper-with-grey reads Stratum, crimson-with-ember reads Tribulation, magenta-with-copper reads Distillation. Nothing is shared with Mercantile (emerald/emerald), Prosperity (gold/cyan), Respite (indigo/candleglow), or the reserved Husbandry (amber/leaf) and Tempest (blue/white) rows. Surfaces are a dark tint of the mod's own magenta hue in the `#1a..`/`#2e..` range, per §7 admission.

### Typography

- **Headings:** pixel/blocky display treatment in the accent gradient `#C44DCC` → `#DA79E3`.
- Everything else is the standard (DESIGN-SYSTEM §3); in-game is the vanilla font, always.

---

## 2. HUD Decision

**No slot, by design.** The standard's test (concord [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md)) grants a slot only for persistent ambient state the player needs while walking around. Distillation's state is either **already surfaced by vanilla UI** (active potion effects render as vanilla status-effect icons — the exact case the suite's Apothecary profile anticipated) or **screen-local** (discovery hints and the recipes page live inside the brewing stand screen; brew progress is the stand's own UI). Nothing needs a permanent screen element. The 16×16 bottle glyph exists for Jade/recipe-viewer contexts only, and the HUD accessors (`isHudVisible()` / `getHudHeight()`) are intentionally absent from the API — siblings' stacking math treats Distillation as never occupying a slot.

---

## 3. Assets

The full asset manifest — every `.glyph`/`.sfx` source under `art/`, the final resource/site path it ships as, and what is still `MISSING` a source — lives in [`ASSETS.md`](ASSETS.md).

Asset-family judgments (the suite stance: custom where it earns its place, vanilla where vanilla is right):

- **New bottle items get custom pixel art** — the Murky Draught (murk-grey liquid, crooked cork) and the half-full draught bottle are new items with no exact vanilla analogue; each is a 16×16 glyph-pipeline sprite. Ordinary potions keep vanilla's tint-layered bottle sprites — new potion types color themselves through vanilla's own tinting, no new textures.
- **Antidotes get one custom bottle** — a single shared 16×16 antidote bottle (tall-neck silhouette, tinted per cure through the vanilla layer system) so a cure reads differently from a buff in the hotbar.
- **Screen furniture is custom but vanilla-styled** — the recipes-page tab button and the batch-row slot overlay are small GUI sprites drawn in vanilla's grey GUI palette; the discovery vapor hint is the stand's existing bubble region tinted in code, no texture.
- **Everything else stays vanilla** — the brewing stand block, cauldron, all reagent items, potion glint, and nearly all sounds (see SPEC — Sound Design; two custom cues earn their place: the discovery chime and the murky fizzle).

---

## 4. Generation Prompts

The full logo is a **Gemini render**; its regeneration prompt lives in `art/exploration/logo-gemini-prompt.md`, kept beside the palette hexes it embeds. The web copies derive from that master deterministically: `site/assets/logo.png` is a ½-scale lanczos resize, and `site/assets/og-image.png` is the master contained on an Ink field at 1200×630 (both via ffmpeg). The 128/512 icons are composed deterministically by `art/glyphs/icon.gen.py`, and the bottle glyph is `.glyph`-authored (`art/glyphs/bottle-16.glyph`) — re-render either by re-running its generator or the pipeline, per DESIGN-SYSTEM §8. A fully procedural pixel-art logo variant is retained in `art/exploration/` (`art/glyphs/logo.gen.py`).

Pixel-art sources (glyph, item sprites, GUI sprites) are `.glyph` files under `art/` — authored through the glyph pipeline, referenced from `ASSETS.md`, never duplicated here. Sound sources are `.sfx` files under `art/audio/`.

---

## 5. Image References

Exploration renders, rejected variants, and reference shots live in `art/exploration/` (currently: the logo regeneration prompt and the procedural pixel-art logo variant).

---

## 6. Website & Listing Brand Notes

Content lives elsewhere — page copy in `site/` (rendered by the shared Concord template), store copy in `site/listing-*.md`; this section carries only brand direction.

- **Accent usage:** Potion Magenta (→ Elixir for hover/emphasis) carries headings, hero glow, and interactive elements; Copper carries links, secondary highlights, and the "warm" states (batch heat, antidote accents). Surfaces and body text stay on the shared neutrals over the Still Dark/Vapor Plum tints. Accents are declared once in `site.json`'s theme block.
- **Hero art direction:** full logo over dark stone and rising vapor.
- **Gallery shots (1920×1080, vanilla or light shader):** the stand's vapor clouding magenta over a hovered ingredient; a Murky Draught tooltip naming the ingredient that would have worked; the recipes page half-filled; a batch rig — stand on a steaming cauldron over a campfire — with six bottles; a thrown antidote cloud stripping poison mid-fight; a hotbar of half-drunk draughts.
- **OG image:** full logo on Ink at 1200×630, per DESIGN-SYSTEM §6.

---

## 7. Concord Context

Distillation owns the **alchemy silo**: the brewing stand, potion recipes and durations, status-effect application and removal, and splash/lingering behavior — beside Meridian (enchanting, violet/gold, compass rose), Mercantile (villagers & trade, emerald/emerald, market stall), Tribulation (difficulty, crimson/ember, hourglass), Prosperity (loot, gold/cyan, treasure chest), and Respite (vitality, indigo/candleglow, hanging lantern). Its magenta-with-copper signature is Distillation's member row in concord's DESIGN-SYSTEM §2, and reads distinct from every sibling under the pairing rule — the nearest hue (Meridian's purple) is disambiguated by both partners. Suite standards this document defers to: concord [`VISION.md`](../../concord/VISION.md), [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md), [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md), [`API-STANDARD.md`](../../concord/API-STANDARD.md).
