package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.ThrownRebalance;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The splash &amp; lingering rebalance of {@code design/SPEC.md} §7 on a live server. Splash effects
 * apply a <em>flat</em> fraction of the drinkable duration to every hit entity regardless of
 * distance (instants keep vanilla's distance scaling); a thrown lingering potion's cloud opens at
 * the configured radius and lifetime with a linear shrink; clouds from other sources and the
 * {@code enableThrownRebalance=false} toggle stay bit-for-bit vanilla; and a dispenser-thrown potion
 * goes through the very same seam as a player-thrown one.
 *
 * <p>Both {@code applySplash} and {@code makeAreaOfEffectCloud} are {@code private} on the vanilla
 * {@code ThrownPotion}; the suite invokes them reflectively on a real spawned potion, which drives
 * the exact (mixined) bytecode while giving deterministic control over hit distance — the physics of
 * a thrown entity would make the distance-independence assertion flaky. The dispenser case triggers a
 * real dispense to prove the dispatch spawns a genuine {@code ThrownPotion}, then drives that very
 * entity's splash.
 */
public class ThrownRebalanceGameTest implements FabricGameTest {

    private static final float EPS = 1e-6F;

    // Speed 3:00 — a plain duration-bearing potion, absent from the §4 honest-duration table, so its
    // base duration is exactly vanilla's and the splash factor is the only thing under test.
    private static final Holder<Potion> DURATION_POTION = Potions.SWIFTNESS;

    // --- Splash: a flat share of duration, independent of distance ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void splashAppliesTheFlatDurationFactor(GameTestHelper helper) {
        ThrownPotion potion = potionAt(helper, 1, 2, 1);
        PotionContents contents = new PotionContents(DURATION_POTION);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));

        applySplash(potion, contents, cow); // direct hit: vanilla would give 100%, the rebalance gives 87.5%

        int base = firstDuration(contents);
        int expected = ThrownRebalance.splashDuration(base, Distillation.getConfig().splashDurationFactor);
        MobEffectInstance applied = cow.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(applied != null, "the splash must apply Speed to the hit cow");
        helper.assertTrue(applied.getDuration() == expected,
                "a splash must apply the flat factor: expected " + expected + " got "
                        + (applied == null ? "none" : applied.getDuration()));
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void splashDurationIsDistanceIndependent(GameTestHelper helper) {
        ThrownPotion potion = potionAt(helper, 1, 2, 1);
        PotionContents contents = new PotionContents(DURATION_POTION);
        Cow near = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1)); // ~1 block away
        Cow far = helper.spawn(EntityType.COW, new BlockPos(4, 2, 1));  // ~3 blocks away, still in range

        // No direct-hit entity: vanilla would scale both by distance (near ~75%, far ~25%).
        applySplash(potion, contents, null);

        int expected = ThrownRebalance.splashDuration(firstDuration(contents),
                Distillation.getConfig().splashDurationFactor);
        MobEffectInstance nearEffect = near.getEffect(MobEffects.MOVEMENT_SPEED);
        MobEffectInstance farEffect = far.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(nearEffect != null && farEffect != null, "both cows in range must be splashed");
        helper.assertTrue(nearEffect.getDuration() == expected && farEffect.getDuration() == expected,
                "a flat splash gives every hit the same duration regardless of distance: expected "
                        + expected + " near=" + nearEffect.getDuration() + " far=" + farEffect.getDuration());
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void splashInstantsKeepDistanceScaling(GameTestHelper helper) {
        ThrownPotion potion = potionAt(helper, 1, 2, 1);
        PotionContents harming = new PotionContents(Potions.HARMING);
        Cow near = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        Cow far = helper.spawn(EntityType.COW, new BlockPos(4, 2, 1));
        float startHealth = near.getHealth();

        applySplash(potion, harming, null);

        // The instant branch is untouched by the rebalance, so it still scales damage by distance.
        helper.assertTrue(near.getHealth() < startHealth, "a Harming splash must damage the near cow");
        helper.assertTrue(near.getHealth() < far.getHealth(),
                "instants keep vanilla distance scaling: the near cow takes more than the far one ("
                        + near.getHealth() + " vs " + far.getHealth() + ")");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dispenserThrownGoesThroughTheSameSeam(GameTestHelper helper) {
        // A dispenser dispatches a splash potion through ProjectileItem.asProjectile — the same call
        // vanilla's ProjectileDispenseBehavior makes. Driving it directly proves the dispensed entity
        // is a ThrownPotion (so the same seam applies), without depending on projectile physics.
        ProjectileItem splashItem = (ProjectileItem) Items.SPLASH_POTION;
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, DURATION_POTION);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        Vec3 origin = new Vec3(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
        Projectile projectile = splashItem.asProjectile(helper.getLevel(), origin, stack, Direction.NORTH);

        helper.assertTrue(projectile instanceof ThrownPotion,
                "a dispensed splash potion is thrown as a ThrownPotion — the same entity a player throws");

        PotionContents contents = new PotionContents(DURATION_POTION);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        applySplash((ThrownPotion) projectile, contents, cow);

        int expected = ThrownRebalance.splashDuration(firstDuration(contents),
                Distillation.getConfig().splashDurationFactor);
        MobEffectInstance applied = cow.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(applied != null && applied.getDuration() == expected,
                "a dispenser-thrown potion applies the same flat factor as a player-thrown one");
        helper.succeed();
    }

    // --- Lingering: a longer, wider cloud that shrinks linearly ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void lingeringCloudIsRebalanced(GameTestHelper helper) {
        ThrownPotion potion = potionAt(helper, 1, 2, 1);
        AreaEffectCloud cloud = makeCloud(helper, potion, new PotionContents(Potions.SWIFTNESS));

        float radius = Distillation.getConfig().lingeringCloudRadius;
        int duration = Distillation.getConfig().lingeringCloudDurationTicks;
        helper.assertTrue(cloud != null, "a thrown lingering potion must spawn a cloud");
        helper.assertTrue(cloud.getRadius() == radius, "cloud opens at the configured radius: " + radius);
        helper.assertTrue(cloud.getDuration() == duration, "cloud lasts the configured lifetime: " + duration);
        helper.assertTrue(Math.abs(cloud.getRadiusPerTick() - ThrownRebalance.cloudRadiusPerTick(radius, duration)) < EPS,
                "cloud shrinks linearly to zero over its lifetime");
        helper.assertTrue(cloud.getRadiusOnUse() == -0.5F, "the per-pickup radius cost stays vanilla (-0.5)");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void directlySpawnedCloudIsUntouched(GameTestHelper helper) {
        // A cloud not born of a thrown potion (dragon's breath, item destruction, direct spawn) must
        // never see the rebalance — the seam is ThrownPotion.makeAreaOfEffectCloud, not the cloud itself.
        AreaEffectCloud cloud = helper.spawn(EntityType.AREA_EFFECT_CLOUD, new BlockPos(2, 2, 2));

        helper.assertTrue(cloud.getDuration() == 600,
                "a directly spawned cloud keeps vanilla's 600-tick lifetime, not the rebalanced 1200");
        helper.assertTrue(cloud.getRadius() != Distillation.getConfig().lingeringCloudRadius,
                "a directly spawned cloud is not opened to the rebalanced radius");
        helper.succeed();
    }

    // --- The toggle restores vanilla exactly ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toggleOffRestoresVanillaNumbers(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableThrownRebalance;
        Distillation.getConfig().enableThrownRebalance = false;
        try {
            ThrownPotion potion = potionAt(helper, 1, 2, 1);

            // Splash: a direct hit is vanilla's full 100% (e = 1.0), not the flat factor.
            PotionContents contents = new PotionContents(DURATION_POTION);
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
            applySplash(potion, contents, cow);
            int base = firstDuration(contents);
            MobEffectInstance applied = cow.getEffect(MobEffects.MOVEMENT_SPEED);
            helper.assertTrue(applied != null && applied.getDuration() == base,
                    "with the rebalance off a direct-hit splash keeps vanilla's full duration " + base);

            // Lingering: vanilla 3.0 radius / 600 ticks / -0.005 per tick.
            AreaEffectCloud cloud = makeCloud(helper, potion, new PotionContents(Potions.SWIFTNESS));
            helper.assertTrue(cloud != null && cloud.getRadius() == 3.0F && cloud.getDuration() == 600,
                    "with the rebalance off a thrown lingering cloud is vanilla 3.0 radius / 600 ticks");
        } finally {
            Distillation.getConfig().enableThrownRebalance = saved;
        }
        helper.succeed();
    }

    // --- helpers ---

    /** A real {@code ThrownPotion} placed at the given relative block (centred), no owner, not flying. */
    private static ThrownPotion potionAt(GameTestHelper helper, int x, int y, int z) {
        BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
        return new ThrownPotion(helper.getLevel(), abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
    }

    private static int firstDuration(PotionContents contents) {
        return contents.getAllEffects().iterator().next().getDuration();
    }

    /** Drives the real (mixined) {@code ThrownPotion.applySplash}; {@code directHit} may be null. */
    private static void applySplash(ThrownPotion potion, PotionContents contents, Entity directHit) {
        try {
            Method m = ThrownPotion.class.getDeclaredMethod("applySplash", Iterable.class, Entity.class);
            m.setAccessible(true);
            m.invoke(potion, contents.getAllEffects(), directHit);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke ThrownPotion.applySplash", e);
        }
    }

    /** Drives the real (mixined) {@code ThrownPotion.makeAreaOfEffectCloud} and returns the spawned cloud. */
    private static AreaEffectCloud makeCloud(GameTestHelper helper, ThrownPotion potion, PotionContents contents) {
        try {
            Method m = ThrownPotion.class.getDeclaredMethod("makeAreaOfEffectCloud", PotionContents.class);
            m.setAccessible(true);
            m.invoke(potion, contents);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke ThrownPotion.makeAreaOfEffectCloud", e);
        }
        List<AreaEffectCloud> clouds = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class,
                potion.getBoundingBox().inflate(8.0));
        return clouds.isEmpty() ? null : clouds.get(clouds.size() - 1);
    }
}
