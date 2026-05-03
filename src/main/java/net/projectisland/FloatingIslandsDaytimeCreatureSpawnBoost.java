package net.projectisland;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Vanilla natural {@link MobCategory#CREATURE} spawning often finds few valid grass columns on small floating islands.
 * During daytime, occasionally pick a random **loaded island surface** column near each player and roll that column's
 * biome {@link MobSpawnSettings} CREATURE weighted list — same mobs the biome would use for natural passives, with
 * {@link MobSpawnType#NATURAL} so {@link FloatingIslandsSpawnTuning} still applies (default keep chance for CREATURE is
 * {@code 1.0}).
 */
public final class FloatingIslandsDaytimeCreatureSpawnBoost {
    private static final java.lang.reflect.Field SPAWNER_DATA_TYPE_FIELD;

    static {
        try {
            SPAWNER_DATA_TYPE_FIELD = MobSpawnSettings.SpawnerData.class.getDeclaredField("type");
            SPAWNER_DATA_TYPE_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private FloatingIslandsDaytimeCreatureSpawnBoost() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsDaytimeCreatureSpawnBoost::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Config.FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_ENABLED.getAsBoolean()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int interval = Math.max(20, Config.FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_INTERVAL_TICKS.getAsInt());
        // Cheap outer gate so we do not scan the player list every tick.
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                continue;
            }
            if (!isRoughDaytime(level)) {
                continue;
            }
            if ((server.getTickCount() + player.getId()) % interval != 0) {
                continue;
            }
            trySpawnNearPlayer(level, player);
        }
    }

    /** Rough vanilla daytime window (after dawn, before dusk). */
    public static boolean isRoughDaytime(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t > 200L && t < 11800L;
    }

    private static void trySpawnNearPlayer(ServerLevel level, ServerPlayer player) {
        FloatingIslandsChunkGenerator gen = ProjectIslandDimensions.floatingIslandsChunkGenerator(level).orElse(null);
        if (gen == null) {
            return;
        }
        RandomSource rnd = level.getRandom();
        int radius = Mth.clamp(Config.FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_RADIUS_BLOCKS.getAsInt(), 8, 160);
        int tries = Mth.clamp(Config.FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_TRIES_PER_PLAYER.getAsInt(), 1, 8);
        int nearbyCap = Mth.clamp(Config.FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_NEARBY_CAP.getAsInt(), 2, 64);
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
            BlockPos feet = findFeetPos(level, wx, surface, wz);
            if (feet == null) {
                continue;
            }
            if (countCreaturesNearby(level, feet, 28.0d) >= nearbyCap) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(feet);
            MobSpawnSettings settings = biome.value().getMobSettings();
            Optional<MobSpawnSettings.SpawnerData> pick = settings.getMobs(MobCategory.CREATURE).getRandom(rnd);
            if (pick.isEmpty()) {
                continue;
            }
            MobSpawnSettings.SpawnerData data = pick.get();
            EntityType<?> entityType = unwrapSpawnerEntityType(data);
            if (!entityType.canSummon()) {
                continue;
            }
            Entity spawned =
                    entityType.spawn(level, null, feet, MobSpawnType.NATURAL, false, false);
            if (spawned != null && Config.DEBUG_LOGGING.getAsBoolean()) {
                ProjectIsland.LOGGER.debug(
                        "Daytime creature boost: {} at {} (biome {})",
                        entityType.getDescriptionId(),
                        feet,
                        biome.unwrapKey().map(k -> k.location().toString()).orElse("?"));
            }
            if (spawned != null) {
                return;
            }
        }
    }

    private static int countCreaturesNearby(ServerLevel level, BlockPos feet, double radius) {
        AABB box = new AABB(feet).inflate(radius);
        return level.getEntitiesOfClass(Mob.class, box, m -> m.getType().getCategory() == MobCategory.CREATURE).size();
    }

    /** First column position with two air blocks above a non-empty block below (feet Y). */
    static BlockPos findFeetPos(ServerLevel level, int wx, int surface, int wz) {
        int maxScan = Math.min(surface + 16, level.getMaxBuildHeight() - 3);
        for (int y = surface + 1; y <= maxScan; y++) {
            BlockPos p = new BlockPos(wx, y, wz);
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            if (!level.getBlockState(p.above()).isAir()) {
                continue;
            }
            if (level.getBlockState(p.below()).isAir()) {
                continue;
            }
            return p;
        }
        return null;
    }

    /**
     * SpawnerData accessors differ slightly across Mojang versions (plain {@link EntityType} vs
     * {@link Holder}&lt;EntityType&lt;?&gt;&gt;); read the {@code type} field reflectively so we compile on 1.21.1.
     */
    @SuppressWarnings("unchecked")
    private static EntityType<?> unwrapSpawnerEntityType(MobSpawnSettings.SpawnerData data) {
        try {
            Object t = SPAWNER_DATA_TYPE_FIELD.get(data);
            if (t instanceof Holder<?> h) {
                return (EntityType<?>) h.value();
            }
            if (t instanceof EntityType<?> direct) {
                return direct;
            }
            throw new IllegalStateException("Unexpected SpawnerData.type: " + (t == null ? "null" : t.getClass().getName()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
