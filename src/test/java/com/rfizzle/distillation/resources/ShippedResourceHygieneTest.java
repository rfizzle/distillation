// Tier: 1 (pure JUnit — walks the processed resource roots on the test classpath)
package com.rfizzle.distillation.resources;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the boundary between what ships and what only ever serves a test (mc-mod-testing, "Every
 * mod ships this guard"). Its counterpart is {@code GametestRegistrationTest}: the two guard
 * opposite ends of the same mistake — registration drift means the tests never run, fixture leak
 * means the tests ship — and neither catches the other's failure.
 *
 * <p>Gametest fixtures — structure templates and bespoke loot tables — resolve through the merged
 * {@code ResourceManager} by namespace, so they work from any loaded mod's resource root. That means
 * they belong in the gametest source set, whose manifest declares a separate {@code
 * distillation-gametest} mod that never enters the jar.
 *
 * <p>Keeping them out of the shipped roots is worth a guard on two counts. A loot table or recipe in
 * the {@code distillation} namespace is eagerly parsed and validated on every datapack reload on
 * every server, including the integrated server behind a singleplayer world, purely to serve a test.
 * A structure template is cheaper — it loads on demand rather than on reload — but it is still
 * listed for {@code /place template} autocomplete, so it surfaces test fixtures to operators.
 *
 * <p>These assertions read the test classpath rather than the source tree, because the classpath is
 * what the jar is built from — it is the shipped artifact under test. Run against a clean build: a
 * stale {@code build/resources/} tree can still hold a fixture that was relocated in source, which
 * reads as a live leak a rebuild makes disappear.
 */
class ShippedResourceHygieneTest {

    /**
     * A shipped resource root, located by anchoring on a file known to live at its top level.
     *
     * @param anchor  classpath path of the anchor file
     * @param markers entries that must exist directly under the resolved root, so that an anchor
     *                which moves into a subdirectory fails loudly instead of silently narrowing the
     *                walk to that subdirectory
     */
    private record ShippedRoot(String anchor, List<String> markers) {
    }

    /**
     * Both source sets that contribute to the jar. Under {@code splitEnvironmentSourceSets()} the
     * client set is processed into its own root, so a guard that walked only {@code main} would miss
     * a fixture dropped into the other. {@code src/main/resources} and {@code src/main/generated}
     * are processed into a single {@code main} root and are covered by the one entry.
     */
    private static final List<ShippedRoot> SHIPPED_ROOTS = List.of(
            new ShippedRoot("/distillation.accesswidener",
                    List.of("fabric.mod.json", "data/distillation", "assets/distillation")),
            new ShippedRoot("/distillation.client.mixins.json",
                    List.of("distillation.client.mixins.json")));

    /** Path segment that marks a file as existing only to serve the gametest suite. */
    private static final String TEST_ONLY_SEGMENT = "gametest";

    /**
     * Structure templates are a gametest-only format here — Distillation ships no structures of its
     * own, and its suites all run against {@code FabricGameTest.EMPTY_STRUCTURE} — so the extension
     * is disqualifying wherever it appears, not just under a {@code gametest/} directory.
     */
    private static final String TEMPLATE_EXTENSION = ".snbt";

    /**
     * Distillation currently owns no gametest fixture files at all, so the "known fixtures are
     * absent" assertion the skill lists first has nothing to name. Its real job here is the one it
     * shares with every other assertion below — proving the walk is not vacuous — so it is stated
     * directly: exactly the two shipped roots resolve. Without this, adding {@code
     * src/client/resources} content later (or renaming an anchor) could leave a root quietly
     * unscanned while every assertion below still passed.
     */
    @Test
    void everyShippedResourceRootResolves() {
        assertEquals(SHIPPED_ROOTS.size(), shippedResourceRoots().size(),
                "every shipped resource root must resolve, or the sweeps below are silently narrower"
                        + " than they read");
    }

    /** No gametest-only file may sit in a root that is processed into the jar. */
    @Test
    void noGametestPathSegment_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> hasSegment(relative, TEST_ONLY_SEGMENT));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain gametest-only files, but found "
                        + offenders.size() + ": " + offenders
                        + " — move them to src/gametest/resources/, which is on the "
                        + "runGametest classpath but never enters the jar");
    }

    /** Catches a template parked outside a {@code gametest/} directory, which the sweep above misses. */
    @Test
    void noStructureTemplates_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> relative.getFileName().toString().endsWith(TEMPLATE_EXTENSION));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain structure templates, but found "
                        + offenders.size() + ": " + offenders
                        + " — templates serve the gametest suite only and belong in "
                        + "src/gametest/resources/");
    }

    /** Walks every shipped root, collecting root-relative paths of files matching the rule. */
    private static List<String> scanShippedRoots(Predicate<Path> offending) {
        List<String> offenders = new ArrayList<>();

        for (Path root : shippedResourceRoots()) {
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .filter(offending)
                        .map(Path::toString)
                        .forEach(offenders::add);
            } catch (Exception e) {
                fail("could not walk shipped resource root " + root, e);
            }
        }

        offenders.sort(String::compareTo);
        return offenders;
    }

    private static boolean hasSegment(Path relative, String segment) {
        for (Path part : relative) {
            if (segment.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves every directory the shipped resources are processed into, by anchoring on a file
     * known to sit at that root's top level and taking its parent.
     */
    private static List<Path> shippedResourceRoots() {
        List<Path> roots = new ArrayList<>();

        for (ShippedRoot shipped : SHIPPED_ROOTS) {
            URL anchor = ShippedResourceHygieneTest.class.getResource(shipped.anchor());
            if (anchor == null) {
                fail("could not locate " + shipped.anchor() + " on the test classpath — the "
                        + "anchor this guard uses to find a shipped resource root has moved");
            }
            if (!"file".equals(anchor.getProtocol())) {
                fail("expected the shipped resource root to be a directory on the test "
                        + "classpath, but " + shipped.anchor() + " resolved to " + anchor);
            }

            Path root;
            try {
                root = Path.of(anchor.toURI()).getParent();
            } catch (Exception e) {
                return fail("could not resolve a filesystem path for " + anchor, e);
            }

            for (String marker : shipped.markers()) {
                assertTrue(Files.exists(root.resolve(marker)),
                        "resolved shipped resource root " + root + " is missing expected entry '"
                                + marker + "' — the anchor " + shipped.anchor() + " has most "
                                + "likely moved into a subdirectory, which would silently narrow "
                                + "this guard's coverage");
            }

            roots.add(root);
        }

        return roots;
    }
}
