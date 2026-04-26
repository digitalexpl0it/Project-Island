package net.projectisland;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Thins vanilla natural mob pressure on small floating islands (esp. night hostiles and creepers).
 * Does not touch spawners, structures, breeding, or player-driven spawns.
 */
public final class FloatingIslandsSpawnTuning {
    private FloatingIslandsSpawnTuning() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnTuning::onFinalizeSpawn);
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

    private static double keepChanceFor(Mob mob) {
        EntityType<?> type = mob.getType();
        if (type == EntityType.CREEPER) {
            return Config.FLOATING_ISLANDS_NATURAL_CREEPER_SPAWN_KEEP_CHANCE.getAsDouble();
        }
        MobCategory cat = type.getCategory();
        return switch (cat) {
            case MONSTER -> Config.FLOATING_ISLANDS_NATURAL_MONSTER_SPAWN_KEEP_CHANCE.getAsDouble();
            case AMBIENT -> Config.FLOATING_ISLANDS_NATURAL_AMBIENT_SPAWN_KEEP_CHANCE.getAsDouble();
            case WATER_CREATURE -> Config.FLOATING_ISLANDS_NATURAL_WATER_CREATURE_SPAWN_KEEP_CHANCE.getAsDouble();
            default -> 1.0d;
        };
    }
}
