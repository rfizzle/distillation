#!/usr/bin/env bash
# distillation — half-draught liquid overlay, 16×16 (design/SPEC.md §4).
#
# This is a tint-layer mask, not palette pixel art: it rides the vanilla potion
# item at layer0 (tintindex 0), so the vanilla potion tint colors it. Authoring it
# by hand would drift from the bottle silhouette and fight the tint, so it is
# DERIVED from vanilla `item/potion_overlay` — the same greyscale liquid, clipped
# to the lower half of the bulb (rows 11–13 of the 8–13 liquid band) so the potion
# reads half-full. Re-render with ImageMagick against the dev Minecraft jar:
#
#   art/glyphs/draught_half.gen.sh
#
# Output: src/main/resources/assets/distillation/textures/item/draught_half.png
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$repo_root/src/main/resources/assets/distillation/textures/item/draught_half.png"
client_jar="$(find "$HOME/.gradle/caches/fabric-loom" -name 'minecraft-*client*.jar' 2>/dev/null \
    | grep -iv sources | head -1)"
if [[ -z "$client_jar" ]]; then
  echo "no Minecraft client jar in the Loom cache — run a Gradle build first" >&2
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -o "$client_jar" 'assets/minecraft/textures/item/potion_overlay.png' -d "$work" >/dev/null
overlay="$work/assets/minecraft/textures/item/potion_overlay.png"

# Keep the overlay's own liquid silhouette and greyscale, intersected with the
# lower-bulb mask (rows 11–15), then re-encode RGBA so Minecraft loads the alpha.
magick -size 16x16 xc:black -fill white -draw 'rectangle 0,11 15,15' "$work/mask.png"
magick "$overlay" -alpha extract "$work/a.png"
magick "$work/a.png" "$work/mask.png" -compose Multiply -composite "$work/na.png"
magick "$overlay" "$work/na.png" -alpha off -compose CopyOpacity -composite \
    -define png:color-type=6 "PNG32:$out"
echo "wrote $out"
