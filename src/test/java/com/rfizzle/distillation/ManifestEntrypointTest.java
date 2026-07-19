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
import java.util.stream.StreamSupport;
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

    /**
     * Matches a gametest annotation in declaration position — at the start of a line, modulo
     * indentation. Anchoring this way skips prose that merely mentions {@code @GameTest} in a
     * comment or string, which would otherwise add a spurious class to the discovered set and
     * fail the parity assertion while pointing at a manifest that is actually correct.
     */
    private static final Pattern GAMETEST_ANNOTATION =
            Pattern.compile("(?m)^\\s*@GameTest(Generator)?\\b");

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject entrypoints = readEntrypoints(MAIN_MANIFEST);
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
        JsonObject entrypoints = readEntrypoints(GAMETEST_MANIFEST);
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
        // Joins the path segments rather than replacing a separator character, so the derived
        // name is identical on platforms whose Path.toString() is not '/'-separated.
        Path relative = GAMETEST_SOURCE_ROOT.relativize(source);
        String className = StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("."));
        return className.substring(0, className.length() - ".java".length());
    }

    /**
     * Reads a manifest's {@code entrypoints} object, asserting its presence first so a renamed or
     * missing key fails with the offending path rather than an unexplained NPE.
     */
    private static JsonObject readEntrypoints(Path path) {
        JsonObject root = readJson(path);
        assertTrue(root.has("entrypoints"), path + " has no \"entrypoints\" object");
        return root.getAsJsonObject("entrypoints");
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
