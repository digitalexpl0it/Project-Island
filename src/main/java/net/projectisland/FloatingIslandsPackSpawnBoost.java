package net.projectisland;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.projectisland.FloatingIslandPackSpawnRule.Placement;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Throttled extra spawns from reloadable {@linkplain FloatingIslandPackSpawnReloader rules} (tries
 * {@link MobSpawnType#NATURAL} first, then looser reasons if mods reject natural/sky). Complements biome table rolls and
 * {@link FloatingIslandsDaytimeCreatureSpawnBoost}.
 */
public final class FloatingIslandsPackSpawnBoost {
    private static final ConcurrentMap<Long, Long> LAST_PACK_SPAWN_GAME_TIME_BY_CHUNK = new ConcurrentHashMap<>();

    private FloatingIslandsPackSpawnBoost() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsPackSpawnBoost::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Config.FLOATING_ISLANDS_PACK_SPAWN_BOOST_ENABLED.getAsBoolean()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int interval = Math.max(40, Config.FLOATING_ISLANDS_PACK_SPAWN_BOOST_INTERVAL_TICKS.getAsInt());
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        if (server.getTickCount() % 6000 == 0) {
            pruneChunkCooldowns(server.getTickCount(), Math.max(200, Config.FLOATING_ISLANDS_PACK_SPAWN_CHUNK_COOLDOWN_TICKS.getAsInt()) * 4L);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                continue;
            }
            if (Config.FLOATING_ISLANDS_PACK_SPAWN_BOOST_DAY_ONLY.getAsBoolean()
                    && !FloatingIslandsDaytimeCreatureSpawnBoost.isRoughDaytime(level)) {
                continue;
            }
            if ((server.getTickCount() + player.getId()) % interval != 0) {
                continue;
            }
            trySpawnNearPlayer(level, player);
        }
    }

    private static void pruneChunkCooldowns(long gameTime, long olderThanTicks) {
        LAST_PACK_SPAWN_GAME_TIME_BY_CHUNK.entrySet().removeIf(e -> gameTime - e.getValue() > olderThanTicks);
    }

    private static void trySpawnNearPlayer(ServerLevel level, ServerPlayer player) {
        List<FloatingIslandPackSpawnRule> all = FloatingIslandPackSpawnRules.rules();
        if (all.isEmpty()) {
            return;
        }
        FloatingIslandsChunkGenerator gen = ProjectIslandDimensions.floatingIslandsChunkGenerator(level).orElse(null);
        if (gen == null) {
            return;
        }
        RandomSource rnd = level.getRandom();
        int radius = Mth.clamp(Config.FLOATING_ISLANDS_PACK_SPAWN_BOOST_RADIUS_BLOCKS.getAsInt(), 16, 192);
        int tries = Mth.clamp(Config.FLOATING_ISLANDS_PACK_SPAWN_BOOST_TRIES_PER_PLAYER.getAsInt(), 1, 12);
        long cooldown = Math.max(0L, Config.FLOATING_ISLANDS_PACK_SPAWN_CHUNK_COOLDOWN_TICKS.getAsInt());
        double px = player.getX();
        double pz = player.getZ();
        for (int i = 0; i < tries; i++) {
            double ox = (rnd.nextDouble() - 0.5d) * 2.0d * radius;
            double oz = (rnd.nextDouble() - 0.5d) * 2.0d * radius;
            int wx = Mth.floor(px + ox);
            int wz = Mth.floor(pz + oz);
            if (!level.hasChunk(wx >> 4, wz >> 4)) {
                continue;
            }
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            int surface = FloatingIslandsChunkGenerator.islandSurfaceBlockY(gen, wx, wz, minY, maxY);
            if (surface == Integer.MIN_VALUE) {
                continue;
            }
            Holder<Biome> biomeHolder = level.getBiome(new BlockPos(wx, surface + 1, wz));
            List<FloatingIslandPackSpawnRule> matching = new ArrayList<>();
            int totalW = 0;
            for (FloatingIslandPackSpawnRule rule : all) {
                if (!biomeHolder.is(rule.biomeTag())) {
                    continue;
                }
                matching.add(rule);
                totalW += rule.weight();
            }
            if (matching.isEmpty() || totalW <= 0) {
                continue;
            }
            int roll = rnd.nextInt(totalW);
            int acc = 0;
            FloatingIslandPackSpawnRule pick = matching.getFirst();
            for (FloatingIslandPackSpawnRule r : matching) {
                acc += r.weight();
                if (roll < acc) {
                    pick = r;
                    break;
                }
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(pick.entityId());
            if (type == null || !type.canSummon()) {
                continue;
            }
            long chunkKey = ChunkPos.asLong(wx >> 4, wz >> 4);
            if (cooldown > 0L) {
                Long last = LAST_PACK_SPAWN_GAME_TIME_BY_CHUNK.get(chunkKey);
                if (last != null && level.getGameTime() - last < cooldown) {
                    continue;
                }
            }
            BlockPos spawnPos = resolveSpawnPos(level, rnd, wx, wz, surface, pick);
            if (spawnPos == null) {
                continue;
            }
            if (pick.maxNearbySame() > 0) {
                AABB box = new AABB(spawnPos).inflate(pick.nearbySameRadius());
                int n = level.getEntitiesOfClass(Mob.class, box, m -> m.getType() == type).size();
                if (n >= pick.maxNearbySame()) {
                    continue;
                }
            }
            Entity spawned = spawnPackEntity(level, type, spawnPos);
            if (spawned != null) {
                if (cooldown > 0L) {
                    LAST_PACK_SPAWN_GAME_TIME_BY_CHUNK.put(chunkKey, level.getGameTime());
                }
                if (Config.DEBUG_LOGGING.getAsBoolean()) {
                    ProjectIsland.LOGGER.debug(
                            "Pack spawn boost: {} at {} (placement {})",
                            pick.entityId(),
                            spawnPos,
                            pick.placement());
                }
                return;
            }
        }
    }

    private static BlockPos resolveSpawnPos(
            ServerLevel level,
            RandomSource rnd,
            int wx,
            int wz,
            int surface,
            FloatingIslandPackSpawnRule rule) {
        if (rule.placement() == Placement.GROUND) {
            return FloatingIslandsDaytimeCreatureSpawnBoost.findFeetPos(level, wx, surface, wz);
        }
        int minA = Math.max(4, rule.minYAboveSurface());
        int maxA = Math.max(minA, rule.maxYAboveSurface());
        int yLo = Mth.clamp(surface + minA, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 8);
        int yHi = Mth.clamp(surface + maxA, yLo, level.getMaxBuildHeight() - 8);
        int span = yHi - yLo;
        int start = span > 0 ? Mth.randomBetweenInclusive(rnd, 0, span) : 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(wx, yLo, wz);
        for (int i = 0; i <= span; i++) {
            int y = yLo + ((start + i) % (span + 1));
            p.set(wx, y, wz);
            if (hasSkySpawnFooting(level, p)) {
                return p.immutable();
            }
        }
        return null;
    }

    /** Several vertical air blocks so large flyers are less likely to intersect terrain or fail mod spawn checks. */
    private static boolean hasSkySpawnFooting(ServerLevel level, BlockPos feet) {
        for (int dy = 0; dy < 6; dy++) {
            if (!level.getBlockState(feet.above(dy)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Many mod mobs reject {@link MobSpawnType#NATURAL} in void/sky (fluid checks, light, etc.). Try progressively looser
     * spawn reasons so datapack-driven pack spawns still work; island spawn tuning only applies to NATURAL/CHUNK_GENERATION.
     */
    private static Entity spawnPackEntity(ServerLevel level, EntityType<?> type, BlockPos spawnPos) {
        Entity spawned = type.spawn(level, null, spawnPos, MobSpawnType.NATURAL, false, false);
        if (spawned != null) {
            return spawned;
        }
        spawned = type.spawn(level, null, spawnPos, MobSpawnType.EVENT, false, false);
        if (spawned != null) {
            return spawned;
        }
        spawned = type.spawn(level, null, spawnPos, MobSpawnType.MOB_SUMMONED, false, false);
        if (spawned != null) {
            return spawned;
        }
        return type.spawn(level, null, spawnPos, MobSpawnType.COMMAND, false, false);
    }
}
