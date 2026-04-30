package net.projectisland;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Thins vanilla natural mob pressure on small floating islands (esp. night hostiles and creepers).
 * Optional tuning for passives and villagers; optional duplicate roll for more land animals.
 * Does not touch spawners, structures (except structure-driven spawn types may still go through finalize),
 * breeding, eggs, or player-driven spawns.
 */
public final class FloatingIslandsSpawnTuning {
    private static final String CREATURE_BOOST_CHILD_TAG = ProjectIsland.MOD_ID + ":creature_boost_child";

    private FloatingIslandsSpawnTuning() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnTuning::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnTuning::onEntityJoin);
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!Config.FLOATING_ISLANDS_SPAWN_TUNING_ENABLED.getAsBoolean()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        Mob mob = event.getEntity();
        double keep = keepChanceFor(mob);
        if (keep >= 1.0d) {
            return;
        }
        if (keep <= 0.0d) {
            event.setSpawnCancelled(true);
            return;
        }
        RandomSource rnd = level.getRandom();
        if (rnd.nextDouble() >= keep) {
            event.setSpawnCancelled(true);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        if (!Config.FLOATING_ISLANDS_SPAWN_TUNING_ENABLED.getAsBoolean()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        double mult = Config.FLOATING_ISLANDS_NATURAL_CREATURE_SPAWN_MULTIPLIER.getAsDouble();
        if (mult <= 1.0d) {
            return;
        }
        if (!(event.getEntity() instanceof Animal animal)) {
            return;
        }
        Mob mob = animal;
        if (mob.getPersistentData().getBoolean(CREATURE_BOOST_CHILD_TAG)) {
            return;
        }
        MobSpawnType spawnType = mob.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        double extraChance = Math.min(1.0d, mult - 1.0d);
        if (level.getRandom().nextDouble() >= extraChance) {
            return;
        }
        level.getServer().execute(() -> spawnCreatureBoostDuplicate(level, mob));
    }

    private static void spawnCreatureBoostDuplicate(ServerLevel level, Mob original) {
        if (!original.isAlive() || !level.isLoaded(original.blockPosition())) {
            return;
        }
        EntityType<?> type = original.getType();
        Mob dup = (Mob) type.create(level);
        if (dup == null) {
            return;
        }
        RandomSource rnd = level.getRandom();
        double ox = (rnd.nextDouble() - 0.5d) * 5.0d;
        double oz = (rnd.nextDouble() - 0.5d) * 5.0d;
        dup.moveTo(original.getX() + ox, original.getY(), original.getZ() + oz, rnd.nextFloat() * 360.0f, 0.0f);
        dup.finalizeSpawn(level, level.getCurrentDifficultyAt(dup.blockPosition()), MobSpawnType.NATURAL, null);
        dup.getPersistentData().putBoolean(CREATURE_BOOST_CHILD_TAG, true);
        level.addFreshEntity(dup);
    }

    private static double keepChanceFor(Mob mob) {
        if (mob instanceof Villager) {
            return Config.FLOATING_ISLANDS_NATURAL_VILLAGER_SPAWN_KEEP_CHANCE.getAsDouble();
        }
        EntityType<?> type = mob.getType();
        if (type == EntityType.CREEPER) {
            return Config.FLOATING_ISLANDS_NATURAL_CREEPER_SPAWN_KEEP_CHANCE.getAsDouble();
        }
        MobCategory cat = type.getCategory();
        return switch (cat) {
            case MONSTER -> Config.FLOATING_ISLANDS_NATURAL_MONSTER_SPAWN_KEEP_CHANCE.getAsDouble();
            case CREATURE -> Config.FLOATING_ISLANDS_NATURAL_CREATURE_SPAWN_KEEP_CHANCE.getAsDouble();
            case AMBIENT -> Config.FLOATING_ISLANDS_NATURAL_AMBIENT_SPAWN_KEEP_CHANCE.getAsDouble();
            case WATER_CREATURE -> Config.FLOATING_ISLANDS_NATURAL_WATER_CREATURE_SPAWN_KEEP_CHANCE.getAsDouble();
            default -> 1.0d;
        };
    }
}
