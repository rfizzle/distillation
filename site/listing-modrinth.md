# Distillation — Alchemy Overhaul

**_Every drop counts._**

![Distillation logo](https://raw.githubusercontent.com/rfizzle/distillation/master/art/logo.png)

**Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/distillation-alchemy-overhaul)
and [GitHub Releases](https://github.com/rfizzle/distillation/releases).**
Visit the [website](https://distillation.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Distillation is an alchemy overhaul for **Minecraft 1.21.1 (Fabric)** — the
brewing stand, potion recipes and durations, and how effects are applied and
cured. Vanilla brewing is learned from a wiki or not at all; Distillation's
stand teaches its own recipes, brews the effects vanilla shipped without one,
scales known recipes to six bottles, and makes curing a decision instead of a
bucket of milk.

**Beta.** The first public release is out and every system is complete, but it
has not had a long public soak yet — back up a world before adding it to one
you care about.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- Every feature independently toggleable through Mod Menu / Cloth Config or
  `config/distillation.json` — hot-reload with `/distillation reload`.
- Every recipe brews from items vanilla already ships — no new crops, plants,
  mobs, or dimensions.
- MIT licensed.

## Features

### Brew by Discovery

Hold an ingredient over the stand and the vapor clouds with a color hint of
what it would become. A failed combination bottles a **Murky Draught** whose
tooltip names one ingredient that would have worked, and every success is
written permanently into your recipes page, right in the brewing screen. No
wiki required — the stand is the teacher.

A recipe you know can be copied onto paper as a **Recipe Note** — tradeable,
giftable, and still asking its reader to brew the thing once themselves.

### The Missing Brews

Every vanilla effect that shipped without a recipe becomes brewable:
**Resistance** from a shulker shell, **Haste** from honey over a sugar brew,
**Absorption** from a golden apple, **Luck** from a nautilus shell,
**Glowing** from a glow ink sac. Redstone and glowstone variants where the
effect supports them.

### Batch Brewing

Set your brewing stand on a water cauldron heated from below and a recipe
you've already discovered fills **6 bottles in one pass at 1.5× the
ingredient cost** — one brewing session kits the whole raid party. The
shortcut is earned: undiscovered recipes still brew three at a time.

### Honest Durations & Draughts

Utility potions (Fire Resistance, Water Breathing, Night Vision, Invisibility)
base at **8:00 instead of 3:00**; combat potions keep their short timers.
Sneak-drink to sip half a potion now and stopper the other half for later — and
a half goes down in half the time, a quick swallow you can afford mid-fight.
Re-drinking a brew that is still running tops its timer up instead of resetting
it, up to twice the base duration — no bottle wasted for being drunk early.

### Premium Brews

Redstone and glowstone stop being mutually exclusive: brew a potion's own
reagent onto it again — two blaze powder total for Strength — and the
concentrated brew takes both dusts for an extended *and* amplified result:
**Strength II for 4:00** instead of choosing between II at 1:30 or I at 8:00.

### Antidotes

Surgical cures brewed from the affliction's own source — poison from a
fermented spider eye, wither from a wither rose, and six more. Each strips
exactly one effect and nothing else; milk still clears everything, buffs
included. Throwable as splash and lingering for mid-fight support.

### The Flask

A copper-and-glass vessel holding three doses of one discovered brew,
refillable forever. Pour a bottled potion in or fill it straight off a batch
pass, then drink a dose at a time — or sneak to sip a half. One brew at a
time, never mixed.

### Lingering Worth Throwing

Lingering clouds last **60 seconds** at a **4½-block radius**; splash potions
apply a flat **87.5%** of the drinkable duration, no distance falloff. A
beneficial splash or cloud you throw is attuned to your side — it buffs only
players and their pets, never the hostiles standing in it — while harmful
potions stay grenades for everyone. A dedicated brewer becomes a viable
support role.

---

Part of **[Concord](https://concord.rfizzle.com)** — a modular collection of
system overhauls. Install any, combine all.

## Companion mods

Distillation is part of [Concord](https://github.com/rfizzle/concord) — a modular collection of
system overhauls. Install any, combine all:

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.
- [Respite](https://respite.rfizzle.com) — Make the night count.
- [Cultivation](https://cultivation.rfizzle.com) — Worth growing.
- [Instinct](https://instinct.rfizzle.com) — Worth raising.
