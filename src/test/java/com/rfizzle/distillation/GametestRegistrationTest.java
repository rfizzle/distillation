// Tier: 1 (pure JUnit — reads the gametest source tree and both manifests off disk)
package com.rfizzle.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Tier-1 guard that the gametest suites on disk and the {@code fabric-gametest} entrypoints in the
 * companion manifest stay in lockstep, and that the split between the shipped manifest and the
 * gametest-only manifest holds (mc-mod-testing, "The canonical guard").
 *
 * <p>Registration fails silently in <em>both</em> directions: an unregistered {@code FabricGameTest}
 * never runs and never warns, so a suite can rot for months while CI stays green; a stale entrypoint
 * naming a deleted class crashes the gametest server at startup.
 *
 * <p>fabric-gametest-api-v1's {@code main} entrypoint is ungated: on every server launch it iterates
 * the {@code fabric-gametest} entrypoints and instantiates each class. The gametest classes are only
 * on the gametest run classpath, so declaring them in the shipped manifest crashes any other server
 * launch (runServer, runDatagen) with ClassNotFoundException. The entrypoints therefore live in
 * {@code src/gametest/resources/fabric.mod.json} under a separate mod id, which exists only where
 * the classes do.
 *
 * <p>That split makes the shipped manifest the single declaration of the loader, Minecraft, Java,
 * and Fabric API floors, since the gametest manifest inherits them transitively through its
 * dependency on the main mod. The {@code depends} assertions below hold both halves of that
 * arrangement in place: the floors stay declared once, and they stay declared where they are.
 *
 * <p>The gametest source set is not on the test classpath, so its classes cannot be enumerated
 * reflectively — the guard reads the source tree instead, walking it recursively so suites in
 * subpackages are not missed. The task inputs it reads are declared in {@code build.gradle};
 * without that block Gradle sees no dependency on a tree it never compiles, and the check would
 * report {@code UP-TO-DATE} exactly when registration had drifted.
 */
class GametestRegistrationTest {

    private static final Path GAMETEST_SOURCES = Path.of("src/gametest/java");
    private static final Path GAMETEST_MANIFEST = Path.of("src/gametest/resources/fabric.mod.json");
    private static final Path SHIPPED_MANIFEST = Path.of("src/main/resources/fabric.mod.json");

    /** The dependency floors the shipped manifest is the sole declaration of. */
    private static final Set<String> REQUIRED_FLOORS =
            Set.of("fabricloader", "minecraft", "java", "fabric-api");

    /**
     * Matches a class's {@code implements} clause naming FabricGameTest — the canonical detection
     * basis (mc-mod-testing). Not a filename suffix, which would let a suite named {@code
     * BrewingTests} go missing from both sides of the comparison at once; and not an annotation
     * regex, which describes "holds a test method" rather than "is a class the loader instantiates"
     * and (unless line-anchored) also matches an {@code @GameTest} written inside a comment or a
     * string. Matched as a pattern rather than a literal so a suite declaring {@code implements
     * Tickable, FabricGameTest} is still seen. This is the same predicate the loader itself uses.
     */
    private static final Pattern IMPLEMENTS_FABRIC_GAMETEST =
            Pattern.compile("implements\\s+[^{]*\\bFabricGameTest\\b");

    @Test
    void everySuiteOnDiskIsRegistered() {
        TreeSet<String> unregistered = new TreeSet<>(suitesOnDisk());
        unregistered.removeAll(declaredGametestEntrypoints());
        assertTrue(
                unregistered.isEmpty(),
                "gametest suites exist but are not declared in " + GAMETEST_MANIFEST
                        + " — they will silently never run: " + unregistered);
    }

    @Test
    void everyRegisteredEntrypointIsASuiteOnDisk() {
        TreeSet<String> dangling = new TreeSet<>(declaredGametestEntrypoints());
        dangling.removeAll(suitesOnDisk());
        assertTrue(
                dangling.isEmpty(),
                GAMETEST_MANIFEST + " declares entrypoints that are not FabricGameTest classes on"
                        + " disk — the gametest server will fail to load them at startup: " + dangling);
    }

    /**
     * The two-way naming check. Matching suites by interface closes the "helper flagged as
     * unregistered" hole — {@code MockPlayers} is not a suite and must not be registered; enforcing
     * the name closes the other one, where a suite called {@code BrewingTests} goes missing from the
     * source-tree scan and the manifest at the same time and the guards above stay green.
     */
    @Test
    void suiteNamingConventionHoldsInBothDirections() {
        TreeSet<String> misnamedSuites = new TreeSet<>();
        TreeSet<String> impostors = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            boolean suite = IMPLEMENTS_FABRIC_GAMETEST.matcher(source).find();
            boolean named = className.endsWith("GameTest");
            if (suite && !named) {
                misnamedSuites.add(className);
            } else if (!suite && named) {
                impostors.add(className);
            }
        });
        assertTrue(misnamedSuites.isEmpty(),
                "FabricGameTest implementors must be named *GameTest: " + misnamedSuites);
        assertTrue(impostors.isEmpty(),
                "classes named *GameTest must implement FabricGameTest: " + impostors);
    }

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject entrypoints = readEntrypoints(SHIPPED_MANIFEST);
        assertFalse(
                entrypoints.has("fabric-gametest"),
                SHIPPED_MANIFEST + " must not declare fabric-gametest entrypoints — they belong in "
                        + GAMETEST_MANIFEST + ", which is not bundled in the jar. Declaring them "
                        + "here crashes runServer/runDatagen.");
    }

    /**
     * Asserts the presence of the floor keys, never their version strings. The values move on every
     * toolchain bump — they are pinned suite-wide in concord's {@code versions-common.properties} —
     * so restating them here would make every bump a two-file edit, with the missed one caught by
     * nothing.
     */
    @Test
    void shippedManifestDeclaresItsDependencyFloors() {
        Set<String> depends = declaredDependencies(SHIPPED_MANIFEST);

        // Collected rather than asserted one at a time, so a thinned block names every floor it
        // dropped instead of reporting only the first.
        Set<String> missing = new TreeSet<>(REQUIRED_FLOORS);
        missing.removeAll(depends);

        assertTrue(
                missing.isEmpty(),
                SHIPPED_MANIFEST + " must declare its dependency floors, and is missing " + missing
                        + ". This is the only manifest that declares them: the gametest manifest "
                        + "depends on the main mod alone and inherits them transitively, so a "
                        + "floor dropped here is a floor the mod enforces nowhere.");
    }

    @Test
    void gametestManifestDependsOnExactlyTheMainMod() {
        // Set equality, not containment: the loader, Minecraft, Java, and Fabric API floors are
        // enforced transitively — this mod cannot load unless distillation did, and distillation
        // declares them itself. Restating one here makes every toolchain bump a two-file edit whose
        // missed half fails only under runGametest, as a confusing load error. A containment check
        // would pass while exactly that stale floor rotted in place.
        assertEquals(
                Set.of("distillation"),
                declaredDependencies(GAMETEST_MANIFEST),
                GAMETEST_MANIFEST + " must depend on the main mod alone.");
    }

    private static Set<String> suitesOnDisk() {
        TreeSet<String> suites = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            if (IMPLEMENTS_FABRIC_GAMETEST.matcher(source).find()) {
                suites.add(className);
            }
        });
        return suites;
    }

    /** Fully-qualified names of every class under the gametest tree, mapped to its source text. */
    private static TreeMap<String, String> gametestSources() {
        TreeMap<String, String> sources = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(GAMETEST_SOURCES)) {
            tree.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> sources.put(toClassName(path), readString(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + GAMETEST_SOURCES, e);
        }
        return sources;
    }

    private static String toClassName(Path source) {
        // Joins the path segments rather than replacing a separator character, so the derived name
        // is identical on platforms whose Path.toString() is not '/'-separated.
        Path relative = GAMETEST_SOURCES.relativize(source);
        String className = StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("."));
        return className.substring(0, className.length() - ".java".length());
    }

    private static Set<String> declaredGametestEntrypoints() {
        JsonObject entrypoints = readEntrypoints(GAMETEST_MANIFEST);
        assertTrue(
                entrypoints.has("fabric-gametest"),
                GAMETEST_MANIFEST + " declares no fabric-gametest entrypoints — every gametest "
                        + "suite would silently stop running");
        JsonArray entries = entrypoints.getAsJsonArray("fabric-gametest");
        TreeSet<String> declared = new TreeSet<>();
        for (JsonElement entry : entries) {
            declared.add(entry.getAsString());
        }
        return declared;
    }

    /**
     * Reads a manifest's declared dependency ids, asserting the shape of {@code depends} first so a
     * renamed key or a block edited to the wrong JSON type fails with the offending path rather than
     * an unexplained NPE or a bare "Not a JSON Object".
     */
    private static Set<String> declaredDependencies(Path path) {
        JsonObject root = readJson(path);
        assertTrue(root.has("depends"), path + " has no \"depends\" object");
        assertTrue(root.get("depends").isJsonObject(), path + " has a non-object \"depends\"");
        return new TreeSet<>(root.getAsJsonObject("depends").keySet());
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
