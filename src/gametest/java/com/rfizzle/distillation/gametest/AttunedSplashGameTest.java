package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Method;

/**
 * The attuned-splash rule of {@code design/SPEC.md} §7 on a live server: a beneficial, duration-bearing
 * effect from a player's thrown splash or lingering cloud reaches only allies (players and their pets),
 * while harmful and neutral effects, instant effects, ownerless (dispensed/witch) throws, and the
 * feature toggle-off all keep vanilla's indiscriminate targeting.
 *
 * <p>Splash cases drive the real (mixined) {@code ThrownPotion.applySplash} reflectively — the same
 * pattern {@link ThrownRebalanceGameTest} uses — so the assertion tests the installed mixin, not a
 * hand-rolled copy. The thrower is a mock {@link ServerPlayer} set as the potion's owner; the ally
 * targets are {@code helper.spawn}'d (so they are indexed for the synchronous splash query). The cloud
 * case ticks a real player-owned {@link AreaEffectCloud} for a spell, which also lets the moved player
 * settle into the entity index in time to be a target — proving a player, not only a pet, is an ally.
 */
public class AttunedSplashGameTest implements FabricGameTest {

    // Strength — a beneficial, duration-bearing line: exactly what must attune to allies only.
    private static final Holder<Potion> BENEFICIAL = Potions.STRENGTH;
    // Poison — a harmful line that still lands on any living entity (a Cow is not undead-immune).
    private static final Holder<Potion> HARMFUL = Potions.POISON;

    // --- Splash: a player's beneficial brew reaches allies, skips everyone else ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void beneficialSplashReachesAPetButNotHostilesOrStrangers(GameTestHelper helper) {
        ServerPlayer thrower = mockPlayer(helper);
        try {
            Wolf pet = tamedWolf(helper, thrower, new BlockPos(2, 2, 2));
            Zombie hostile = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
            Cow bystander = helper.spawn(EntityType.COW, new BlockPos(2, 2, 3));

            applySplash(playerOwnedPotion(helper, thrower, 2, 2, 2), new PotionContents(BENEFICIAL), null);

            helper.assertTrue(pet.hasEffect(MobEffects.DAMAGE_BOOST),
                    "a player's beneficial splash must buff their tamed pet");
            helper.assertTrue(!hostile.hasEffect(MobEffects.DAMAGE_BOOST),
                    "a player's beneficial splash must never reach a hostile");
            helper.assertTrue(!bystander.hasEffect(MobEffects.DAMAGE_BOOST),
                    "a player's beneficial splash must never reach a non-ally animal");
        } finally {
            thrower.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void harmfulSplashStaysIndiscriminate(GameTestHelper helper) {
        ServerPlayer thrower = mockPlayer(helper);
        try {
            Wolf pet = tamedWolf(helper, thrower, new BlockPos(2, 2, 2));
            Cow bystander = helper.spawn(EntityType.COW, new BlockPos(2, 2, 3));

            applySplash(playerOwnedPotion(helper, thrower, 2, 2, 2), new PotionContents(HARMFUL), null);

            helper.assertTrue(bystander.hasEffect(MobEffects.POISON),
                    "a harmful splash is still a grenade: it lands on a non-ally");
            helper.assertTrue(pet.hasEffect(MobEffects.POISON),
                    "a harmful splash lands on allies too — friendly fire and all");
        } finally {
            thrower.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void instantBeneficialSplashStaysIndiscriminate(GameTestHelper helper) {
        // Instant effects never reach the wrapped addEffect (they take the applyInstantenous branch),
        // so this pins that they are untouched by attunement: a player's Instant Health splash still
        // lands on a hostile — and, on undead, still hurts it, the anti-undead tactic left intact.
        ServerPlayer thrower = mockPlayer(helper);
        try {
            Zombie undead = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
            float startHealth = undead.getHealth();

            applySplash(playerOwnedPotion(helper, thrower, 2, 2, 2), new PotionContents(Potions.HEALING), undead);

            helper.assertTrue(undead.getHealth() < startHealth,
                    "a player's Instant Health splash still hurts undead — the instant branch is not attuned");
        } finally {
            thrower.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownerlessBeneficialSplashStaysIndiscriminate(GameTestHelper helper) {
        // A dispensed or witch-thrown potion has no player owner — attunement is the brewer's hand.
        Zombie hostile = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

        applySplash(potionAt(helper, 2, 2, 2), new PotionContents(BENEFICIAL), null);

        helper.assertTrue(hostile.hasEffect(MobEffects.DAMAGE_BOOST),
                "without a player thrower a beneficial splash is vanilla-indiscriminate");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationAttunedSplashToggle")
    public void toggleOffRestoresIndiscriminateSplash(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableAttunedSplash;
        Distillation.getConfig().enableAttunedSplash = false;
        ServerPlayer thrower = mockPlayer(helper);
        try {
            Zombie hostile = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

            applySplash(playerOwnedPotion(helper, thrower, 2, 2, 2), new PotionContents(BENEFICIAL), null);

            helper.assertTrue(hostile.hasEffect(MobEffects.DAMAGE_BOOST),
                    "with attuned splash off a player's beneficial splash reaches everyone (vanilla)");
        } finally {
            thrower.discard();
            Distillation.getConfig().enableAttunedSplash = saved;
        }
        helper.succeed();
    }

    // --- Lingering: a player-owned cloud attunes its beneficial effect too ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void beneficialCloudReachesThePlayerButNotAHostile(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper);
        BlockPos center = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(center);
        owner.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        Zombie hostile = helper.spawn(EntityType.ZOMBIE, center);

        AreaEffectCloud cloud = helper.spawn(EntityType.AREA_EFFECT_CLOUD, center);
        cloud.setOwner(owner); // the brewer's hand — a player-thrown cloud
        cloud.setPotionContents(new PotionContents(BENEFICIAL));
        cloud.setRadius(6.0F);
        cloud.setWaitTime(0); // apply on the next eligible tick rather than after vanilla's warm-up

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(owner.hasEffect(MobEffects.DAMAGE_BOOST),
                    "a player-owned beneficial cloud must buff the player standing in it");
            helper.assertTrue(!hostile.hasEffect(MobEffects.DAMAGE_BOOST),
                    "a player-owned beneficial cloud must not buff a hostile standing in it");
            owner.discard();
            helper.succeed();
        });
    }

    // --- helpers ---

    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Wolf tamedWolf(GameTestHelper helper, ServerPlayer owner, BlockPos pos) {
        Wolf wolf = helper.spawn(EntityType.WOLF, pos);
        wolf.tame(owner);
        return wolf;
    }

    /** A real {@code ThrownPotion} at the given relative block, no owner (a dispensed-style throw). */
    private static net.minecraft.world.entity.projectile.ThrownPotion potionAt(GameTestHelper helper,
                                                                               int x, int y, int z) {
        BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
        return new net.minecraft.world.entity.projectile.ThrownPotion(helper.getLevel(),
                abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
    }

    /** The same potion, but thrown by a player — its owner gates attunement. */
    private static net.minecraft.world.entity.projectile.ThrownPotion playerOwnedPotion(GameTestHelper helper,
                                                                                        ServerPlayer thrower,
                                                                                        int x, int y, int z) {
        net.minecraft.world.entity.projectile.ThrownPotion potion = potionAt(helper, x, y, z);
        potion.setOwner(thrower);
        return potion;
    }

    /** Drives the real (mixined) {@code ThrownPotion.applySplash}; {@code directHit} may be null. */
    private static void applySplash(net.minecraft.world.entity.projectile.ThrownPotion potion,
                                    PotionContents contents, Entity directHit) {
        try {
            Method m = net.minecraft.world.entity.projectile.ThrownPotion.class
                    .getDeclaredMethod("applySplash", Iterable.class, Entity.class);
            m.setAccessible(true);
            m.invoke(potion, contents.getAllEffects(), directHit);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke ThrownPotion.applySplash", e);
        }
    }
}
