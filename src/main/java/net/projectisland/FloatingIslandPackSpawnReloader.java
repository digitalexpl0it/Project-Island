package net.projectisland;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.projectisland.FloatingIslandPackSpawnRule.Placement;
import org.slf4j.Logger;

/**
 * Loads {@code data/projectisland/floating_island_pack_spawns/rules.json}. Datapacks can override the built-in file in
 * the mod JAR to add modded mobs (e.g. sky {@code cnb:end_whale} on overworld island biomes).
 */
public final class FloatingIslandPackSpawnReloader implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation RULES =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "floating_island_pack_spawns/rules.json");

    @Override
    public CompletableFuture<Void> reload(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager resourceManager,
            ProfilerFiller prepareProfiler,
            ProfilerFiller applyProfiler,
            Executor backgroundExecutor,
            Executor applyExecutor) {
        CompletableFuture<List<FloatingIslandPackSpawnRule>> prep =
                CompletableFuture.supplyAsync(() -> loadFrom(resourceManager), backgroundExecutor);
        return prep.thenCompose(barrier::wait).thenAcceptAsync(FloatingIslandPackSpawnReloader::applyValidated, applyExecutor);
    }

    @Override
    public String getName() {
        return "projectisland:floating_island_pack_spawns";
    }

    private static void applyValidated(List<FloatingIslandPackSpawnRule> loaded) {
        List<FloatingIslandPackSpawnRule> ok = new ArrayList<>();
        for (FloatingIslandPackSpawnRule rule : loaded) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rule.entityId())) {
                LOGGER.warn(
                        "Skipping pack spawn rule for unknown entity type {} (mod not loaded or wrong id). Fix rules.json or install the mod.",
                        rule.entityId());
                continue;
            }
            ok.add(rule);
        }
        FloatingIslandPackSpawnRules.replace(ok);
    }

    private static List<FloatingIslandPackSpawnRule> loadFrom(ResourceManager rm) {
        Optional<Resource> res = rm.getResource(RULES);
        if (res.isEmpty()) {
            LOGGER.warn("Missing {} — pack spawn rules disabled until present", RULES);
            return List.of();
        }
        try (InputStreamReader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.warn("{} must be a JSON object — using empty rules", RULES);
                return List.of();
            }
            JsonObject o = root.getAsJsonObject();
            if (!o.has("rules") || !o.get("rules").isJsonArray()) {
                LOGGER.warn("{} must contain a \"rules\" array — using empty rules", RULES);
                return List.of();
            }
            JsonArray arr = o.getAsJsonArray("rules");
            List<FloatingIslandPackSpawnRule> out = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                Optional<FloatingIslandPackSpawnRule> rule = parseRule(el.getAsJsonObject());
                rule.ifPresent(out::add);
            }
            LOGGER.info("Loaded {} floating-island pack spawn rule(s) from {}", out.size(), RULES);
            return out;
        } catch (Exception e) {
            LOGGER.error("Failed to load {} — using empty rules", RULES, e);
            return List.of();
        }
    }

    private static Optional<FloatingIslandPackSpawnRule> parseRule(JsonObject o) {
        if (!o.has("entity") || !o.get("entity").isJsonPrimitive()) {
            LOGGER.warn("Pack spawn rule missing \"entity\" — skipped");
            return Optional.empty();
        }
        ResourceLocation entityId = ResourceLocation.parse(o.get("entity").getAsString());
        if (!o.has("biome_tag") || !o.get("biome_tag").isJsonPrimitive()) {
            LOGGER.warn("Pack spawn rule for {} missing \"biome_tag\" — skipped", entityId);
            return Optional.empty();
        }
        ResourceLocation biomeTagId = ResourceLocation.parse(o.get("biome_tag").getAsString());
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, biomeTagId);

        String placementStr = o.has("placement") ? o.get("placement").getAsString() : "ground";
        Placement placement;
        try {
            placement = Placement.valueOf(placementStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown placement \"{}\" for {} — use ground or sky — skipped", placementStr, entityId);
            return Optional.empty();
        }

        int minAbove = o.has("min_y_above_surface") ? o.get("min_y_above_surface").getAsInt() : 16;
        int maxAbove = o.has("max_y_above_surface") ? o.get("max_y_above_surface").getAsInt() : 120;
        if (maxAbove < minAbove) {
            int t = maxAbove;
            maxAbove = minAbove;
            minAbove = t;
        }
        int weight = o.has("weight") ? Math.max(1, o.get("weight").getAsInt()) : 1;
        int maxNearby = o.has("max_nearby_same") ? Math.max(0, o.get("max_nearby_same").getAsInt()) : 2;
        double nearbyR = o.has("nearby_same_radius") ? o.get("nearby_same_radius").getAsDouble() : 64.0d;
        nearbyR = Math.max(8.0d, Math.min(256.0d, nearbyR));

        return Optional.of(
                new FloatingIslandPackSpawnRule(entityId, biomeTag, placement, minAbove, maxAbove, weight, maxNearby, nearbyR));
    }
}
