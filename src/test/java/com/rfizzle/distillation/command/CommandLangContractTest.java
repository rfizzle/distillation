// Tier: 1 (pure JUnit — parses the shipped lang JSON off the classpath, no Fabric runtime)
package com.rfizzle.distillation.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code command.distillation.*} key the command tree sends must exist non-blank in the
 * shipped lang file — a missing one renders as a raw key in chat with no compile error.
 */
class CommandLangContractTest {

    private static final List<String> COMMAND_KEYS = List.of(
            "command.distillation.recipes.count",
            "command.distillation.recipes.count.other",
            "command.distillation.recipes.latest",
            "command.distillation.discover",
            "command.distillation.discover.already",
            "command.distillation.discover.all",
            "command.distillation.forget",
            "command.distillation.forget.not_found",
            "command.distillation.forget.all",
            "command.distillation.unknown_recipe",
            "command.distillation.reload",
            "command.distillation.reload_failed");

    @Test
    void everyCommandFeedbackKeyExistsNonBlank() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : COMMAND_KEYS) {
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "command feedback keys missing or blank in en_us.json: " + missing);
    }

    private static JsonObject lang() {
        String resource = "/assets/distillation/lang/en_us.json";
        try (InputStream in = CommandLangContractTest.class.getResourceAsStream(resource)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Path.of("src/main/resources" + resource), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }
}
