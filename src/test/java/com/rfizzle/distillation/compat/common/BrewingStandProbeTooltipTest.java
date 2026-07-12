// Tier: 1 (pure JUnit — CompoundTag and Component build without registries; fabric-loader-junit links MC)
package com.rfizzle.distillation.compat.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the brewing-stand probe formatter ({@code design/SPEC.md} §Compatibility): an absent presence
 * flag yields no lines, and each packed field renders its localized line with the right key and args.
 * The tag is hand-built exactly as the server writer would pack it, so the two stay in lockstep.
 */
class BrewingStandProbeTooltipTest {

    @Test
    void absentPresenceFlagYieldsNoLines() {
        assertTrue(BrewingStandProbeTooltip.buildLines(null).isEmpty(), "a null tag is inert");
        assertTrue(BrewingStandProbeTooltip.buildLines(new CompoundTag()).isEmpty(), "no presence flag is inert");
    }

    @Test
    void brewProgressRendersAsAPercentLine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BrewingStandProbeTooltip.KEY_PRESENT, true);
        tag.putInt(BrewingStandProbeTooltip.KEY_BREW_PROGRESS, 42);
        List<Component> lines = BrewingStandProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.distillation.probe.brewing", key(lines.get(0)));
        assertEquals(42, args(lines.get(0))[0]);
    }

    @Test
    void riggedRendersWaterLevelAndHeatLines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BrewingStandProbeTooltip.KEY_PRESENT, true);
        tag.putBoolean(BrewingStandProbeTooltip.KEY_RIGGED, true);
        tag.putInt(BrewingStandProbeTooltip.KEY_WATER, 2);
        tag.putInt(BrewingStandProbeTooltip.KEY_MAX_WATER, 3);
        tag.putString(BrewingStandProbeTooltip.KEY_HEAT, "command.distillation.rig.heat.campfire");
        List<Component> lines = BrewingStandProbeTooltip.buildLines(tag);
        assertEquals(2, lines.size());
        assertEquals("tooltip.distillation.probe.rigged", key(lines.get(0)));
        assertEquals(2, args(lines.get(0))[0]);
        assertEquals(3, args(lines.get(0))[1]);
        assertEquals("tooltip.distillation.probe.heat", key(lines.get(1)));
    }

    @Test
    void ownerRendersItsName() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BrewingStandProbeTooltip.KEY_PRESENT, true);
        tag.putString(BrewingStandProbeTooltip.KEY_OWNER, "Steve");
        List<Component> lines = BrewingStandProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.distillation.probe.owner", key(lines.get(0)));
        assertEquals("Steve", args(lines.get(0))[0]);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }
}
