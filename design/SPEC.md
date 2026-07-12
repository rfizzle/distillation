# Distillation — Feature Spec

Minecraft 1.21.1 Fabric mod. Alchemy overhaul.

**Architectural philosophy:** Vanilla-stand deepening. Distillation never replaces the brewing stand block, its block entity type, or its screen type — it widens what the existing stand accepts, resolves every brew through a single recipe-graph seam built over vanilla's own brewing registry, and extends the stand's screen in place (a recipes page and a batch row, not a new menu). Per-player state (recipe discovery) lives in a persistent Fabric data attachment; per-stand state (batch owner) lives in the block entity's existing NBT. New registered content is potion types, two custom sounds, and one item (the Murky Draught) — nothing block-shaped, no world generation, no new mobs. All gameplay decisions are server-side; the client receives render-only sync (discovery set for hints and the recipes page).

**Asset philosophy:** Bottles and reagents stay vanilla wherever vanilla is right — new potion types color themselves through vanilla's tint-layer system with no new textures. Custom pixel art is limited to what has no vanilla analogue: the Murky Draught, the half-drunk draught bottle, one shared antidote bottle, and two small GUI sprites, all through Concord's glyph pipeline (concord `design/DESIGN-SYSTEM.md` §8; see `design/DESIGN.md` §3 and `design/ASSETS.md`). Sound is vanilla except two earned custom cues — the discovery chime and the murky fizzle — through the `.sfx` pipeline (§9 of the design system; see Sound Design below).

---

## 1. Recipe Discovery

The stand teaches its own recipes: hover hints, instructive failures, and a permanent per-player recipes page.

### Problem

Brewing is the least-used major system in vanilla largely because its recipes are invisible in-game — nothing in the world tells you what an ingredient does, so brewing is learned from a wiki or not at all.

### The Recipe Graph

At server start (and on `/distillation reload` and datapack reload), Distillation builds a **recipe graph**: the complete set of `(input potion, ingredient) → output` brewing conversions, sourced from vanilla's brewing registry plus Distillation's own additions (§2, §5, §6) plus anything third-party mods registered through the same vanilla registry. Each conversion gets a **stable recipe id**: `distillation:<ingredient item path>/<input potion path>` (namespaces prefixed when not `minecraft`, e.g. `distillation:shulker_shell/awkward`). Container conversions (gunpowder → splash, dragon's breath → lingering) are graph entries like any other and are discoverable.

An item is a **graph ingredient** if it appears as the ingredient of at least one conversion.

### Behavior — Vapor Hints

In the brewing stand screen, while the player holds an ItemStack on the cursor above the ingredient slot:

1. The client resolves the cursor item against the graph for each potion currently in the three bottle slots.
2. If at least one pair is a valid conversion, the stand's bubble/vapor region tints with the **output potion's color** at 60% opacity (multiple distinct outputs blend their colors). No text is shown for undiscovered conversions — the color is the whole clue.
3. If every pair the player has **already discovered**, the tint is joined by a tooltip line naming the output(s): `Potion of Haste`.
4. If no pair is valid (or the item is not a graph ingredient), no tint renders.

Hints read only client-side data (the graph is deterministic from registries and synced config; the discovery set is synced per §Sync). Hidden entirely when `showVaporHints` (client) is off.

### Behavior — Murky Draughts

The stand's ingredient slot accepts any **graph ingredient** (vanilla accepts only ingredients valid *somewhere*; Distillation keeps that gate but resolves validity per bottle):

1. A brew cycle starts when the ingredient is a graph ingredient, fuel is present, and at least one bottle slot holds a receptive bottle. Cycle length is vanilla's 400 ticks.
2. On completion, **each bottle resolves independently**: a bottle whose `(potion, ingredient)` pair is a valid conversion becomes the output; a bottle whose pair is invalid becomes a **Murky Draught** (`distillation:murky_draught`).
3. A Murky Draught records, in item components: the input potion it came from, and a **hint ingredient** — one ingredient chosen from the set of valid conversions for that input potion, uniformly at random (seeded by stand position + game time, so the three bottles of one failed pass agree). Its tooltip reads: *"Perhaps a <ingredient> would have taken."* If the input potion has no valid conversions at all (e.g. a lingering potion — nothing brews onward from it), the draught records no hint and its tooltip reads: *"Perhaps nothing would have taken."*
4. Drinking a Murky Draught applies Nausea 0:15 plus the **flicker** — a taste of the brew the hint pointed at: the hinted conversion's output potion applies its effects at amplifier 0, duration effects capped at 0:20 (400 ticks), instant effects applied once. A hintless draught is nausea alone. The flicker never records discovery; the drink restores nothing and returns the glass bottle either way. Murky Draughts are **inert to further brewing**: the stand treats them as non-receptive bottles.

With `enableMurkyDraughts=false`, invalid pairs simply do not brew (the bottle passes through unchanged), and a cycle needs at least one valid pair to start — near-vanilla gating.

### Behavior — Discovery & the Recipes Page

1. When a player **removes a brewed output** from a bottle slot, the conversion that produced it is recorded in that player's persistent discovery set. First-time discovery shows the ✦ toast (action bar): *"✦ Recipe learned: Potion of Resistance"* and plays the discovery chime (Sound Design).
2. The stand screen carries a **recipes page button** (a small bottle-glyph tab, top-right of the panel). It opens a paged overlay listing every discovered conversion as `input + ingredient → output`, in discovery order, with page arrows and a running count (`23 / 61`). At full discovery the count renders gilded with the discovery marker (`✦ 61 / 61`). It is a screen overlay, not a HUD surface.
3. Discovery is permanent: it survives death, relog, and dimension change. `/distillation forget` (Commands) is the only removal.
4. With `startDiscovered=true`, every player's set starts (and joins) complete — for servers of veterans.

### Sync

The server pushes the owner's discovery set on join and on change (`DiscoverySyncS2C`, id-list delta). The client uses it for hints, tooltips, the recipes page, and the recipe-viewer filter (Compatibility); all recording happens server-side.

### Edge Cases

- **Hopper automation:** hoppers may insert any graph ingredient (the widened slot gate applies to automation too), so an automated stand can produce Murky Draughts. Hopper-extracted outputs teach nobody — discovery requires a player's hand in the output slot. Accepted: automation trades away learning.
- **Two players, one stand:** whoever removes each output learns its conversion. The brew doesn't care who loaded the stand.
- **Already-discovered brews** re-record harmlessly (set semantics); the toast and chime fire only on first discovery.
- **Graph changes** (mods/datapacks added or removed): discovery entries whose recipe id no longer resolves are retained in storage but hidden from the page and count — they reappear if the recipe returns.
- **Creative mode:** identical behavior; creative players discover normally.
- **Multiplayer:** discovery is strictly per player; no shared or server-wide unlocks (fairness: late joiners learn the same way founders did).

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableDiscovery` | bool | true | — |
| `enableMurkyDraughts` | bool | true | — |
| `startDiscovered` | bool | false | — |

Client: `showVaporHints` (bool, default true).

`enableDiscovery=false` disables recording, the toast, the recipes page button, and vapor hints (server-authoritative kill switch); Murky Draughts are governed independently. Batch brewing (§3) treats every recipe as discovered while discovery is disabled.

### Implementation Notes

- Graph builder: iterate the vanilla `PotionBrewing` mix/container lists (via accessor or the 1.21.1 builder hook) plus Distillation's registrations into one immutable `RecipeGraph` (maps keyed by ingredient item and by input potion). Rebuilt on datapack reload; synced config version stamped.
- Ingredient-slot gate: a `BrewingStandBlockEntity#canPlaceItem` mixin widening slot 3 to graph ingredients; the brew-completion seam is one mixin at `BrewingStandBlockEntity`'s `doBrew` equivalent, resolving per-bottle through the graph — murky fallback, §3 batch, and `DistillationBrewCallback` (Public API) all live in this one choke point.
- Discovery attachment `DiscoveryData` (persistent player attachment, `Set<ResourceLocation>` with codec); extraction hook via the stand menu's output-slot `onTake`.
- Screen additions live in a client mixin on `BrewingStandScreen`: vapor tint pass, tooltip lines, recipes-page button + overlay widget. No new `Screen` is registered.

---

## 2. The Missing Brews

Every vanilla effect that shipped without a recipe becomes brewable.

### Problem

Resistance, Haste, Absorption, Luck, and Glowing exist in vanilla — with icons, ids, and (for Luck) even a bottled potion item — but no survival path. The effect roster is creative-mode-only for no stated reason.

### Behavior

Five new potion lines, registered under the `distillation:` namespace and brewed at the stand like any potion:

| Effect | Recipe | Base | + Redstone | + Glowstone |
|---|---|---|---|---|
| Resistance | Awkward + Shulker Shell | Resistance 3:00 | 8:00 | Resistance II 1:30 |
| Haste | Potion of Swiftness + Honey Bottle | Haste 8:00 | 20:00 | Haste II 4:00 |
| Absorption | Awkward + Golden Apple | Absorption 3:00 | 8:00 | Absorption II 1:30 |
| Luck | Awkward + Nautilus Shell | Luck 8:00 | 20:00 | — |
| Glowing | Awkward + Glow Ink Sac | Glowing 3:00 | 8:00 | — |

- Haste routes through Swiftness deliberately — honey over a sugar brew — so the utility line costs two steps. Haste and Luck are utility-class and take the §4 long durations; Resistance, Absorption, and Glowing are combat/marker effects and keep vanilla-scale timers.
- The Honey Bottle is consumed whole (no empty bottle back), matching the dragon's-breath precedent.
- Luck and Glowing have no meaningful second level; glowstone on them is an invalid pair (→ Murky Draught, which the absent vapor hint warns about).
- **Corruptions** (fermented spider eye): Haste → Mining Fatigue 3:00 (+redstone 8:00); Luck → Bad Luck 8:00 (+redstone 20:00). Resistance, Absorption, and Glowing have no corruption (invalid pair).
- All five lines take gunpowder (splash) and dragon's breath (lingering) as vanilla, subject to §7.
- **The Mundane bottle's onward arrow:** Mundane Potion + Fermented Spider Eye → Weakness 1:30 (+redstone 4:00 as vanilla), alongside vanilla's untouched water-bottle route. With §6's Thick base, neither of vanilla's dead-end base bottles ends the graph — every conversion the stand teaches leads somewhere.

### Edge Cases

- Vanilla's recipe-less `minecraft:luck` potion item is left untouched (still obtainable only by command); the brewed line is Distillation's own. Commands/loot referencing the vanilla potion keep working.
- **Luck's vanilla surface is fishing:** the fishing loot table is vanilla's only consumer of the luck attribute — each luck point shifts the junk/treasure split, stacking with Luck of the Sea. Distillation never widens what luck touches (loot tables belong to the world); siblings that consume the attribute do so in their own repos.
- **Absorption stacking:** drinking Absorption while holding golden-apple absorption follows vanilla effect-merge rules (higher amplifier wins; equal amplifier takes the longer duration). No special casing.
- **Multiplayer:** none beyond §1 discovery — recipes are world rules, identical for everyone.
- **Disabled** (`enableMissingBrews=false`): the conversions leave the graph (existing bottles keep working; hints and murky logic follow the graph).

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableMissingBrews` | bool | true | — |

### Implementation Notes

- Potion registrations: `distillation:resistance` / `long_` / `strong_` variants and likewise per line (no `strong_luck`/`strong_glowing`; corruption lines analogous), registered with the durations above; conversions registered through the vanilla brewing builder so they enter the §1 graph with no special casing.

---

## 3. Batch Brewing

Discovered recipes scale to six bottles over a heated cauldron.

### Problem

Three bottles per 20-second cycle is sized for one player; kitting a group means re-running the same known brew all evening. Scaling should reward mastery (a recipe you have learned) and infrastructure (a rig you built), not just patience.

### The Batch Rig

A brewing stand is **rigged** when: the block directly below it is a **water cauldron with at least 1 water level**, and the block directly below the cauldron is a heat source — lit campfire, lit soul campfire, fire, soul fire, a lava source block, or a magma block.

### Behavior

1. While rigged, the stand's screen shows a **batch row**: three additional bottle slots above the vanilla three, plus a steam indicator over the cauldron icon. The batch slots accept exactly what bottle slots accept.
2. A brew pass engages the batch row when all hold: the rig is valid, the batch row holds ≥1 receptive bottle, the ingredient slot holds **≥3** of the ingredient, and the stand's **batch owner** has discovered the conversion for that batch-row bottle (per bottle; see Ownership).
3. An engaged pass runs one vanilla-length cycle (400 ticks) and consumes: **3 ingredients** (vs 2 for the same six bottles in two normal passes — the 1.5× cost), **2 fuel charges**, and **1 cauldron water level**.
4. All six bottles resolve per §1's per-bottle rules. Batch-row bottles whose conversion the owner has not discovered are **skipped untouched** (never murked — the rig refuses politely; the bottom row still brews and teaches normally).
5. If the batch row is empty, ingredients are short, or the rig is invalid, the pass is a normal vanilla 3-bottle brew at normal cost.

### Ownership

The stand records the UUID of the player who last inserted into the ingredient slot (the **batch owner**). Hopper insertion clears the owner — automated stands never batch-brew and hoppers can neither see nor touch the batch slots. Ownership persists in the block entity across unload.

### Edge Cases

- **Rig broken mid-cycle:** the rig is validated when the pass starts; completion is unconditional (the water/fuel/ingredients were already consumed).
- **Rig removed while batch slots hold items:** the slots eject their contents into the world above the stand, then hide.
- **Cauldron refill:** dripstone and rain refill the cauldron as vanilla; the rig only ever consumes water levels, never the cauldron.
- **Non-water cauldrons** (lava, powder snow) never form a rig.
- **`startDiscovered` / `enableDiscovery=false`:** every conversion counts as discovered; the rig gates on infrastructure and cost alone.
- **Multiplayer:** the owner gate means you batch-brew *your* knowledge — a guest can't scale recipes only the host has learned by borrowing a loaded stand. Two players can share a rig by each inserting their own ingredient stack.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableBatchBrewing` | bool | true | — |
| `batchIngredientCost` | int | 3 | 2–6 |
| `batchFuelCost` | int | 2 | 1–4 |

### Implementation Notes

- The block entity's container grows to 8 slots (indices 5–7 = batch row), with `WorldlyContainer` sided access unchanged (slots 5–7 exposed to no side). Menu/screen show the row conditionally on a synced `rigged` flag; rig detection runs on neighbor updates + a cheap recheck at brew start.
- Batch engagement and per-bottle resolution ride §1's brew choke point; owner UUID stored in block entity NBT, set from the menu's ingredient-slot insert path, cleared on hopper insert.

---

## 4. Honest Durations & Draughts

Durations sized to how people play; potions drinkable in halves.

### Problem

Utility potions time out in transit: 3:00 of Fire Resistance barely covers finding the fortress, let alone raiding it. And a potion is all-or-nothing — there is no way to spend half now and keep half for the trip home.

### Behavior — Duration Retune

Utility-class potions get durations worth planning around; combat-class potions keep vanilla timers. The full override table (ticks; formatted times for reference):

| Potion line | Vanilla base / long | Distillation base / long |
|---|---|---|
| Fire Resistance | 3:00 / 8:00 | **8:00 / 20:00** |
| Water Breathing | 3:00 / 8:00 | **8:00 / 20:00** |
| Night Vision | 3:00 / 8:00 | **8:00 / 20:00** |
| Invisibility | 3:00 / 8:00 | **8:00 / 20:00** |
| Slow Falling | 1:30 / 4:00 | **4:00 / 10:00** |
| Haste (§2) | — | 8:00 / 20:00 (defined there) |
| Luck (§2) | — | 8:00 / 20:00 (defined there) |

Unchanged (combat/mobility class): Swiftness, Leaping, Strength, Regeneration, Poison, Weakness, Slowness, Turtle Master, Healing, Harming, and §2's Resistance, Absorption, Glowing. The overrides apply wherever the potion is created — brewed, found as loot, traded, or creative-picked.

### Behavior — Draughts (sip half)

1. **Sneak + use** on a full, drinkable, non-instant potion drinks a **half**: the usual 1.6s drink, each effect applied at ⌊duration ÷ 2⌋, and the item becomes a **half draught** — same potion, marked half-full (bottle renders half-empty; name suffixed *"(Half)"*; tooltip shows the halved durations).
2. Drinking a half draught (sneaking or not) applies the remaining half and returns the glass bottle. Halves do not split further.
3. Instant potions (Healing, Harming, antidotes §6) cannot be sipped — sneak-use drinks them whole. Splash, lingering, and Murky Draughts cannot be sipped.
4. Effect stacking follows vanilla merge rules — sipping the second half while the first is active extends the timer by replacing it with the fresh half duration if longer.

### Edge Cases

- **Half draughts and brewing:** a half draught is not a receptive bottle — the stand rejects it (no topping up).
- **Existing worlds:** potions bottled before install retune on next effect application (duration is resolved at drink time, not stored in the item).
- **Multiplayer:** per-player consumption; nothing shared.
- **Disabled:** `enableHonestDurations=false` restores vanilla timers (already-applied effects tick out unchanged); `enableDraughts=false` makes sneak-use drink normally (existing half draughts remain drinkable for their stored half).

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableHonestDurations` | bool | true | — |
| `enableDraughts` | bool | true | — |

### Implementation Notes

- Duration overrides: an internal map (potion id → base ticks) applied at the single seam where potion contents instantiate their `MobEffectInstance`s (a `PotionContents` mixin), so loot/trade/creative potions retune identically. §2's own lines bake their durations at registration and skip the map.
- Draughts: a `distillation:draught` item component (fraction consumed); interaction via `use` interception on potion items when sneaking; the half state drives an item-model override (half-full bottle texture) and tooltip lines.

---

## 5. Concentrated & Premium Brews

Redstone and glowstone stop being mutually exclusive — at a doubled reagent cost.

### Problem

Vanilla forces every potion to choose: long (redstone) or strong (glowstone), never both. The top of the brewing curve is a fork, not a peak.

### Behavior

1. **Concentration:** brewing a potion's own effect reagent onto the finished potion — e.g. Potion of Strength + Blaze Powder — yields a **Concentrated** potion: identical stats, marked concentrated (deeper liquid color; tooltip line *"Concentrated"*). This is the "double the base reagent" step: two blaze powder total have gone into the bottle. Concentration is valid only for effects that have a strong (level II) form.
2. A Concentrated potion accepts **both** modifiers cumulatively, in either order: redstone then glowstone, or glowstone then redstone. The finished **premium** potion is extended *and* amplified: **level II at the extended duration ÷ 2**.
3. Premium results by line: Strength II 4:00, Swiftness II 4:00, Leaping II 4:00, Regeneration II 1:30 (from extended 3:00... see table), Poison II 1:30, Slowness IV 2:00, Turtle Master II 2:00, Resistance II 4:00, Absorption II 4:00, Haste II 10:00. The general formula: `premium duration = long-variant duration ÷ 2`, amplifier = the strong variant's.
4. On a **non-concentrated** potion, redstone and glowstone keep exact vanilla behavior (including vanilla's trades when applied to an already-modified potion). Concentration entries are graph conversions like any other — hintable, discoverable, murk-able.

### Edge Cases

- **Effects with no strong form** (Fire Resistance, Night Vision, Invisibility, Water Breathing, Slow Falling, Luck, Glowing): concentration is an invalid pair (→ Murky Draught; the missing vapor hint warns first).
- **Concentrating an already-modified potion** (long or strong) is invalid — concentration applies to the base potion only. The order is always: brew → concentrate → dusts.
- **Splash/lingering:** gunpowder and dragon's breath apply to premium potions as to any potion (§7).
- **Multiplayer:** world rules, identical for everyone.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enablePremiumBrews` | bool | true | — |

Disabling removes the concentration conversions from the graph; existing Concentrated and premium bottles keep working.

### Implementation Notes

- Concentrated is a parallel potion registration per eligible line (`distillation:concentrated_strength`, etc.) plus the premium output (`distillation:premium_strength`); all conversions registered through the standard builder. The reagent-onto-its-own-potion mapping lives in one declarative table beside the §2 registrations.

---

## 6. Antidotes

Surgical cures, brewed from the affliction's own source.

### Problem

Curing is all-or-nothing: milk wipes your 8-minute buffs to shake a 30-second poison. Vanilla has no targeted counterplay to individual afflictions.

### Behavior

Eight antidotes, each brewed on a **Thick Potion** base (water + glowstone dust — glowstone's body carries the cure, and vanilla's dead-end bottle becomes the medicine base), each an **instant** potion that removes exactly one effect type (all amplifiers, any remaining duration) and touches nothing else:

| Antidote | Recipe (Thick +) | Cures | Source logic |
|---|---|---|---|
| Poison Antidote | Fermented Spider Eye | Poison | the eye that poisons |
| Wither Antidote | Wither Rose | Wither | the rose the Wither leaves |
| Mining Fatigue Antidote | Prismarine Crystals | Mining Fatigue | the guardian's gemstone |
| Blindness Antidote | Ink Sac | Blindness | the ink that blinds |
| Darkness Antidote | Echo Shard | Darkness | the deep's own echo |
| Levitation Antidote | Popped Chorus Fruit | Levitation | the shulker's fruit, grounded |
| Slowness Antidote | Sugar | Slowness | the sugar that quickens |
| Weakness Antidote | Blaze Powder | Weakness | the blaze's restored vigor |

- Drinking an antidote whose target effect is absent consumes it with no other outcome (the fizz plays either way).
- Antidotes take gunpowder and dragon's breath: a **splash antidote** cures everyone it hits; a **lingering antidote** is a 60-second cleansing cloud (§7) — the support-brewer's tool.
- Antidotes accept no redstone, glowstone, concentration, or corruption (invalid pairs).
- **Milk is untouched**: it still clears everything, buffs included. The antidote is the precision alternative, not a replacement.

### Edge Cases

- **Thick + Fermented Spider Eye** — and every other antidote pair — is undefined in vanilla (Thick has no vanilla onward conversions at all), so the antidote lines claim empty slots in the graph, colliding with nothing, including §2's Mundane route to Weakness.
- **The Wither Antidote is post-boss by design:** pre-boss wither exposure is a 10-second brush from wither skeletons; sustained exposure is Wither farming, which begins exactly when wither roses do. Beat the boss once and every rematch is one you enter stocked.
- **Mobs:** splash/lingering antidotes affect any living entity (curing a drowned's nothing is a no-op; curing a poisoned ally works). Effects flagged un-removable by their source (e.g. a boss fight's scripted effect from another mod) are skipped silently.
- **Multiplayer:** consumption is per player; clouds serve whoever stands in them.
- **Disabled** (`enableAntidotes=false`): conversions leave the graph; existing bottles keep working.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableAntidotes` | bool | true | — |

### Implementation Notes

- One `distillation:antidote` potion registration per entry with an instant `MobEffect` (`distillation:cleanse`) carrying the target effect id in the potion's own table — the effect's `applyInstantenousEffect` removes the target from the entity. The antidote→target mapping is the registry the Public API's `registerAntidote` appends to.
- Bottle rendering: the shared antidote bottle item-model override, tinted per cure (DESIGN §3).

---

## 7. Splash & Lingering Rebalance

Thrown potions rebalanced as support tools.

### Problem

Splash potions tax duration 25% and lingering clouds evaporate in seconds over a 3-block puddle — throwing a potion at an ally is strictly worse than handing it over, so the support role never forms.

### Behavior

- **Splash:** duration-bearing effects apply a flat **87.5%** of drinkable duration to every entity hit, regardless of distance. Instant effects keep vanilla's distance-scaled potency.
- **Lingering:** the cloud lasts **60 seconds** (vanilla 30) and opens at a **4.5-block radius** (vanilla 3.0), shrinking linearly to 0 over its lifetime; the per-pickup radius cost stays vanilla (−0.5). Per-application effect duration stays vanilla's quarter of drinkable duration — which §4 has already raised for utility lines.
- Applies to every splash/lingering potion from any source — player-thrown, dispensed, or witch-thrown (a Tribulation-hardened witch benefits like anyone; her arsenal is her own mod's business).

### Edge Cases

- **Dispensers** throw with identical physics and the same rebalanced numbers.
- **Creeper/ghast-popped clouds** (a lingering potion destroyed as an item) are unaffected — the rebalance touches thrown entities and their clouds, not item despawn.
- **Multiplayer/PvP:** harmful splash/lingering potions gain the same numbers — support and sabotage scale together. Servers wanting vanilla PvP numbers disable `enableThrownRebalance`.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableThrownRebalance` | bool | true | — |
| `splashDurationFactor` | float | 0.875 | 0.5–1.0 |
| `lingeringCloudDurationTicks` | int | 1200 | 600–2400 |
| `lingeringCloudRadius` | float | 4.5 | 3.0–6.0 |

### Implementation Notes

- Splash factor: a `ThrownPotion.applySplash` mixin replaces vanilla's distance-scaled duration operator with the flat factor, on the thrown path only — instant effects never take that operator, so they keep vanilla's distance scaling. Lingering: an `AreaEffectCloud` configuration mixin at spawn from a thrown lingering potion (duration, radius, radius-per-tick), leaving other cloud sources (dragon breath attack) untouched.

---

## 8. Commands

### `/distillation` Command Tree

| Command | Permission | Behavior |
|---|---|---|
| `/distillation recipes` | 0 | The caller's discovery count and five most recent entries — e.g. `47 / 61 recipes discovered. Latest: Haste, Premium Strength, …` |
| `/distillation recipes <player>` | 2 | The same report for another player. |
| `/distillation discover <recipe\|all> [player]` | 2 | Grants a discovery (recipe-id suggestion support), default target: caller. |
| `/distillation forget <recipe\|all> [player]` | 2 | Removes discoveries. |
| `/distillation rig` | 0 | Reports the batch-rig status of the brewing stand the caller is looking at (≤ 10 blocks): `Rigged: water 2/3, soul campfire.` or the first missing piece: `Not rigged: cauldron is empty.` |
| `/distillation reload` | 2 | Reloads the JSON config and rebuilds the recipe graph. |

All feedback is localized (`command.distillation.*`). Diagnostic density is favored over prose in op-only output.

---

## 9. Advancements

Seven entries, parented under vanilla's **Local Brewery** (`minecraft:nether/brew_potion`) — extending the brewing story vanilla already tells.

| Id | Title | Trigger |
|---|---|---|
| `trial_and_error` | Trial and Error | bottle a Murky Draught |
| `scholar_of_the_still` | Scholar of the Still | discover 10 recipes |
| `the_missing_shelf` | The Missing Shelf | brew all five §2 effects at least once |
| `round_for_the_table` | Round for the Table | complete a six-bottle batch pass |
| `surgical` | Surgical | an antidote strips an effect while you keep ≥ 2 other effects |
| `the_good_stuff` | The Good Stuff | brew a premium (extended + amplified) potion |
| `every_drop` | Every Drop | the capstone: discover every recipe in the graph |

Custom criterion triggers fired from the §1 brew/discovery choke points, the §3 batch pass, and the §6 consume path. `every_drop` is evaluated at each discovery against the live graph (stale ids hidden per §1 don't count against it); once granted it persists as any advancement, even if a later mod or datapack grows the graph. Icons reuse the mod's item sprites and vanilla potions.

---

## Configuration

All features are independently toggleable via a ModMenu / Cloth Config screen and a JSON config file (`config/distillation.json`), created with defaults on first launch. `configVersion` is **1**. Unknown/missing fields are filled with defaults and clamped to valid ranges after load; a corrupted file falls back to defaults and is left untouched.

### Server Config

| Key | Type | Default | Description |
|---|---|---|---|
| `enableDiscovery` | bool | true | Recipe recording, toasts, recipes page, hints (§1) |
| `enableMurkyDraughts` | bool | true | Failed brews bottle Murky Draughts (§1) |
| `startDiscovered` | bool | false | Players start with every recipe discovered (§1) |
| `enableMissingBrews` | bool | true | The five recipe-less effect lines (§2) |
| `enableBatchBrewing` | bool | true | The heated-cauldron batch rig (§3) |
| `batchIngredientCost` | int | 3 | Ingredients consumed per six-bottle pass |
| `batchFuelCost` | int | 2 | Fuel charges consumed per six-bottle pass |
| `enableHonestDurations` | bool | true | Utility-potion duration retune (§4) |
| `enableDraughts` | bool | true | Sneak-drink half potions (§4) |
| `enablePremiumBrews` | bool | true | Concentration + both-dusts premium path (§5) |
| `enableAntidotes` | bool | true | The eight antidote lines (§6) |
| `enableThrownRebalance` | bool | true | Splash/lingering rebalance (§7) |
| `splashDurationFactor` | float | 0.875 | Fraction of drinkable duration a splash applies |
| `lingeringCloudDurationTicks` | int | 1200 | Lingering cloud lifetime |
| `lingeringCloudRadius` | float | 4.5 | Lingering cloud starting radius |

### Client Config

| Key | Type | Default | Description |
|---|---|---|---|
| `showVaporHints` | bool | true | Color hints over the ingredient slot (§1) |
| `recipeViewerShowsUndiscovered` | bool | false | Recipe viewers list conversions you haven't discovered |

---

## Public API

Per concord [`API-STANDARD.md`](../../concord/API-STANDARD.md): the only stable package is **`com.rfizzle.distillation.api`** (local `@Stable` marker — no shared jar); everything outside it is internal. Read-only by default, server-authoritative, provider errors isolated by the host.

### Surface

- `DistillationAPI.isDiscovered(ServerPlayer, ResourceLocation recipeId): boolean`
- `DistillationAPI.getDiscoveredRecipes(ServerPlayer): Set<ResourceLocation>` — immutable copy.
- `DistillationAPI.getRecipeIds(): Set<ResourceLocation>` — the current graph, immutable.
- `DistillationAPI.registerAntidote(ResourceLocation effectId, Ingredient reagent): boolean` — the sanctioned additive-registration point: adds a Thick-based antidote line for the given effect (false and no-op if the effect already has one). Callable during mod init only; the graph builds after all registrations.
- **`DistillationBrewCallback`** — Fabric event fired server-side from the brew choke point after a cycle completes: `(ServerLevel, BlockPos, ItemStack ingredient, List<ItemStack> results, @Nullable UUID batchOwner, boolean batch)`. Results are an immutable view — observation only (potion identity is the recipe graph's job, not a mutation surface). A listener that throws is caught, logged, and skipped.
- **`DistillationDiscoveryCallback`** — Fabric event fired server-side when a player first discovers a recipe: `(ServerPlayer, ResourceLocation recipeId)`.

### Deliberate absences

- **No HUD accessors** — Distillation holds no HUD slot (see `design/DESIGN.md` §2); siblings' stacking sums treat it as absent.
- **No discovery mutators** — discovery is earned gameplay state; outside mods observe it (commands are the admin path).
- **No brew-result mutation** — a sibling that wants a brewable item registers a real conversion (`registerAntidote`, or the vanilla brewing registry, which the graph absorbs automatically).

---

## Compatibility

### Required

- Fabric Loader ≥ 0.16.10, Fabric API (data attachments, events, screen extension), Minecraft 1.21.1

### Optional Integrations

- **ModMenu + Cloth Config** — config screen.
- **Jade / WTHIT** — brewing stand: brew progress, batch-rig status (`Rigged — water 2/3`), batch owner name; cauldron below a stand: "batch rig" line with heat state.
- **EMI / REI / JEI** — a brewing category listing every graph conversion, including §2/§5/§6 lines. By default only the viewing player's **discovered** conversions render (`recipeViewerShowsUndiscovered=false`) — the recipe viewer never spoils what the stand wants to teach; servers and veterans can open it up.

### Sibling & Mod Compatibility

- **Distillation is provider-side for most of the suite:** siblings consume its stable potion/item ids — Mercantile's cleric trade packs (reagents at reputation), Prosperity's loot injections (rare reagents at distance), Tribulation's high-tier witches (throwing Distillation brews) all live in the consumer's repo per the suite pattern, keyed on `distillation:` ids and the graph API above.
- **Tribulation:** no runtime coupling in either direction. The debuffs Tribulation's shards can inflict are the vanilla effects Slowness, Mining Fatigue, and Weakness, and §6 ships native antidotes for all three (against the vanilla effect, from a vanilla reagent) — so a player facing those debuffs has a cure whether or not Tribulation is installed, with nothing keyed on a `tribulation:` id. Tribulation's own use of Distillation (high-tier witches throwing Distillation brews) is provider-side and lives in Tribulation's repo.
- **Meridian:** no runtime coupling in either direction — potion effects and enchantment effects never share definitions (the suite's policed boundary). Nothing to guard because nothing is consumed.
- **Third-party brewing mods:** anything registered through the vanilla brewing registry enters the recipe graph automatically — discoverable, hintable, batchable, murk-consistent — with zero per-mod code.

---

## Sound Design

Vanilla covers the stand's foley (brewing loop, bottle fills, cauldron, fire). Two custom cues earn their place, both synthesized through the `.sfx` pipeline (concord DESIGN-SYSTEM §9), mono Ogg, subtitled:

| Sound id | Cue | Trigger | Subtitle |
|---|---|---|---|
| `distillation:ui.recipe_learned` | bright two-tone rising chime, < 1 s | first-time discovery (§1), client of the discovering player | *Recipe learned* |
| `distillation:block.brewing_stand.murky` | dull, damp fizzle, < 1 s | a brew cycle produces ≥ 1 Murky Draught, at the stand | *Brew goes murky* |

Everything else vanilla: `block.brewing_stand.brew`, `item.bottle.fill`, `entity.splash_potion.throw`/`break`, `block.campfire.crackle` (the rig's ambience is the heat source's own).

---

## Localization

All user-facing text uses translation keys in `assets/distillation/lang/en_us.json`, namespaced by surface per concord DESIGN-SYSTEM §10. Enum-like states (rig status, hint states) route through `translationKey()` helpers — code never formats an enum for the player.

| Pattern | Example | Used for |
|---|---|---|
| `config.distillation.*` (+ `.tooltip`) | `config.distillation.enable_batch_brewing` | Cloth Config labels and descriptions |
| `command.distillation.*` | `command.distillation.rig.not_rigged` | Command feedback |
| `notification.distillation.*` | `notification.distillation.recipe_learned` | The ✦ discovery toast (marker inside the localized value) |
| `tooltip.distillation.*` | `tooltip.distillation.murky.hint` | Murky hint line, draught "(Half)" lines, Concentrated tag, Jade/WTHIT lines |
| `gui.distillation.*` | `gui.distillation.recipes_page.count` | Recipes page button, header, count, page dots |
| `item.distillation.<id>` | `item.distillation.murky_draught` | Item names (vanilla-mandated) |
| `effect.distillation.<id>` | `effect.distillation.cleanse` | Effect names (vanilla-mandated) |
| `advancements.distillation.*` | `advancements.distillation.surgical.title` | Advancement titles/descriptions |
| `subtitles.distillation.*` | `subtitles.distillation.recipe_learned` | The two custom sound subtitles |

Potion display names ride vanilla's `potion.effect.*`/`item.minecraft.potion.effect.*` key pattern for the `distillation:` potion registrations.

---

## HUD

Distillation ships **no HUD element**. The slot decision and reasoning live in `design/DESIGN.md` §2; the API's deliberate omission of HUD accessors is recorded under Public API above. Discovery state lives in the brewing screen (hints, recipes page); active effects use vanilla's own effect icons; rig state lives on Jade/WTHIT and `/distillation rig`.

---

## Testing Strategy

### Unit Tests (JUnit + `fabric-loader-junit`)

- Recipe graph: construction from a synthetic registry, stable id derivation (namespaced ingredients), per-bottle validity resolution, murky hint-candidate selection (seeded determinism, candidate always valid for the input potion, empty candidate set → hintless draught)
- Duration retune math: override table application, §2 lines exempt from double-scaling, splash factor application, draught halving (⌊÷2⌋, no quarter-splits)
- Premium formula: `long ÷ 2` durations and amplifiers per line; concentration validity (strong-form-only, base-potion-only)
- Discovery set semantics: idempotent re-discovery, forget, stale-id hiding vs retention
- Config round-trip, clamping, corrupted-file fallback

### Gametests (Fabric Gametest API)

- Brew each §2 line (base, redstone, glowstone where defined); glowstone on Luck murks; corruptions brew; Mundane + Fermented Spider Eye brews Weakness while the water-bottle route stays intact
- Invalid pair produces Murky Draughts with a valid hint ingredient; `enableMurkyDraughts=false` leaves bottles unbrewed; murky bottles are inert to further brewing
- Drinking a Murky Draught applies Nausea 0:15 plus the hinted output's flicker (amplifier 0, 400 ticks); a hintless draught (lingering-potion input) applies nausea alone; the flicker records no discovery
- Output extraction records discovery exactly once and fires the callback; hopper extraction records nothing
- Batch rig: detection across all six heat sources; missing water/heat/cauldron fails; engaged pass consumes 3 ingredients + 2 fuel + 1 water level and fills six bottles; undiscovered batch-row bottle skipped untouched; hopper insert clears owner and blocks batching; hoppers cannot reach batch slots
- Draughts: sneak-drink halves duration and yields a half; half returns bottle; instants and splash refuse to sip
- Concentration → both dusts (either order) yields premium at `long ÷ 2`; concentrating a modified potion murks
- Each antidote brews from a Thick base (Awkward + the same reagent murks) and strips exactly its effect and nothing else; absent-effect drink consumes silently; splash antidote cures a poisoned entity; lingering antidote cloud lasts 1200 ticks at 4.5 radius
- Discovering the final graph recipe grants Every Drop
- Splash potion applies 87.5% duration; dispenser-thrown identical
- Commands: `recipes`, `discover`, `forget`, `rig` behave and permission-gate as specced

### Manual Testing

- Vapor hint tinting (single and blended outputs), tooltip gating on discovery, recipes page paging and count, the gilded `✦` count at full discovery
- Half-bottle, murky, antidote, and concentrated item rendering; recipe-viewer filtering with and without `recipeViewerShowsUndiscovered`
- The two custom cues at vanilla loudness beside stand foley; subtitles
- Batch row appearing/ejecting as the rig is built/broken; Jade/WTHIT lines
