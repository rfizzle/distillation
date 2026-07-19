package com.rfizzle.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the split between the shipped manifest and the gametest-only manifest.
 *
 * <p>fabric-gametest-api-v1's {@code main} entrypoint is ungated: on every server launch it
 * iterates the {@code fabric-gametest} entrypoints and instantiates each class. The gametest
 * classes are only on the gametest run classpath, so declaring them in the shipped manifest
 * crashes any other server launch (runServer, runDatagen) with ClassNotFoundException. The
 * entrypoints therefore live in {@code src/gametest/resources/fabric.mod.json} under a separate
 * mod id, which exists only where the classes do.
 */
class ManifestEntrypointTest {

    private static final Path MAIN_MANIFEST = Path.of("src/main/resources/fabric.mod.json");
    private static final Path GAMETEST_MANIFEST = Path.of("src/gametest/resources/fabric.mod.json");
    private static final Path GAMETEST_SOURCE_ROOT = Path.of("src/gametest/java");

    /** Matches the gametest annotations, but not an {@code import ...GameTest;} line. */
    private static final Pattern GAMETEST_ANNOTATION =
            Pattern.compile("@GameTest(Generator)?\\b");

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject entrypoints = readJson(MAIN_MANIFEST).getAsJsonObject("entrypoints");
        assertFalse(
                entrypoints.has("fabric-gametest"),
                "src/main/resources/fabric.mod.json must not declare fabric-gametest entrypoints — "
                        + "they belong in src/gametest/resources/fabric.mod.json, which is not "
                        + "bundled in the jar. Declaring them here crashes runServer/runDatagen.");
    }

    @Test
    void gametestManifestListsExactlyTheAnnotatedGametestClasses() {
        Set<String> declared = declaredGametestEntrypoints();
        Set<String> discovered = annotatedGametestClasses();

        assertEquals(
                discovered,
                declared,
                "src/gametest/resources/fabric.mod.json's fabric-gametest entrypoints must match "
                        + "the @GameTest-annotated classes under src/gametest/java exactly. An "
                        + "unlisted class silently never runs; a listed-but-absent class crashes "
                        + "the gametest server at startup.");
    }

    @Test
    void everyDeclaredGametestEntrypointHasASourceFile() {
        for (String className : declaredGametestEntrypoints()) {
            Path source = GAMETEST_SOURCE_ROOT.resolve(className.replace('.', '/') + ".java");
            assertTrue(
                    Files.exists(source),
                    "Declared gametest entrypoint " + className + " has no source file at " + source);
        }
    }

    private static Set<String> declaredGametestEntrypoints() {
        JsonObject entrypoints = readJson(GAMETEST_MANIFEST).getAsJsonObject("entrypoints");
        assertTrue(
                entrypoints.has("fabric-gametest"),
                GAMETEST_MANIFEST + " must declare the fabric-gametest entrypoints");
        return entrypoints.getAsJsonArray("fabric-gametest").asList().stream()
                .map(element -> element.getAsString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> annotatedGametestClasses() {
        try (Stream<Path> sources = Files.walk(GAMETEST_SOURCE_ROOT)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(ManifestEntrypointTest::declaresGametest)
                    .map(ManifestEntrypointTest::toClassName)
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + GAMETEST_SOURCE_ROOT, e);
        }
    }

    private static boolean declaresGametest(Path source) {
        return GAMETEST_ANNOTATION.matcher(readString(source)).find();
    }

    private static String toClassName(Path source) {
        String relative = GAMETEST_SOURCE_ROOT.relativize(source).toString();
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }

    private static JsonObject readJson(Path path) {
        return JsonParser.parseString(readString(path)).getAsJsonObject();
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
