// Tier: 1 (pure JUnit — scans the hand-built config-screen source against the config POJO)
package com.rfizzle.distillation.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the config-GUI completeness contract: every server-authoritative field on
 * {@link DistillationConfig} has an entry in the hand-built Cloth/ModMenu screen. The screen is
 * assembled by explicit {@code addEntry(...)} calls, not reflection, so a new field is easy to add
 * to the POJO (and its lang keys) yet forget in the GUI — leaving a shipped toggle a player cannot
 * reach. This scans the builder source and fails on any server field it never references.
 */
class ClothConfigScreenContractTest {

    private static final Path BUILDER_SOURCE = Path.of(
            "src/client/java/com/rfizzle/distillation/compat/modmenu/ClothConfigScreenBuilder.java");

    @Test
    void everyServerConfigFieldHasAScreenEntry() throws IOException {
        String source = Files.readString(BUILDER_SOURCE, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Field field : DistillationConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            // The client block is edited in its own category; configVersion is not user-facing.
            if (name.equals("client") || name.equals("configVersion")) {
                continue;
            }
            // Every entry binds the field with a `config.<field>` read/save consumer.
            if (!source.contains("config." + name)) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                "server config fields with no Cloth screen entry (add them to ClothConfigScreenBuilder): " + missing);
    }
}
