package net.projectisland.island;

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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.projectisland.ProjectIsland;
import org.slf4j.Logger;

/**
 * Loads {@code data/&lt;namespace&gt;/floating_island_display_names/names.json} on server reload; datapacks can override
 * the built-in file in the mod JAR.
 */
public final class FloatingIslandDisplayNameReloader implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation NAMES =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "floating_island_display_names/names.json");
    private static final int MAX_WORDS = 400;
    private static final int MAX_WORD_LEN = 48;

    @Override
    public CompletableFuture<Void> reload(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager resourceManager,
            ProfilerFiller prepareProfiler,
            ProfilerFiller applyProfiler,
            Executor backgroundExecutor,
            Executor applyExecutor) {
        CompletableFuture<Optional<WordLists>> prep =
                CompletableFuture.supplyAsync(() -> loadFrom(resourceManager), backgroundExecutor);
        return prep.thenCompose(barrier::wait)
                .thenAcceptAsync(
                        opt -> {
                            if (opt.isPresent()) {
                                WordLists w = opt.get();
                                FloatingIslandDisplayName.applyReload(w.adjectives(), w.nouns());
                            } else {
                                FloatingIslandDisplayName.applyReloadBuiltin();
                            }
                        },
                        applyExecutor);
    }

    @Override
    public String getName() {
        return "projectisland:floating_island_display_names";
    }

    private static Optional<WordLists> loadFrom(ResourceManager rm) {
        Optional<Resource> res = rm.getResource(NAMES);
        if (res.isEmpty()) {
            LOGGER.warn("Missing {} — using built-in island name lists", NAMES);
            return Optional.empty();
        }
        try (InputStreamReader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.warn("{} is not a JSON object — using built-in lists", NAMES);
                return Optional.empty();
            }
            JsonObject o = root.getAsJsonObject();
            List<String> adj = readStringArray(o, "adjectives");
            List<String> nouns = readStringArray(o, "nouns");
            if (adj.isEmpty() || nouns.isEmpty()) {
                LOGGER.warn("{} must contain non-empty adjectives and nouns — using built-in lists", NAMES);
                return Optional.empty();
            }
            LOGGER.info("Loaded {} adjectives and {} nouns for floating island display names", adj.size(), nouns.size());
            return Optional.of(new WordLists(adj, nouns));
        } catch (Exception e) {
            LOGGER.warn("Failed to read {} — using built-in lists", NAMES, e);
            return Optional.empty();
        }
    }

    private static List<String> readStringArray(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray arr = o.getAsJsonArray(key);
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                continue;
            }
            String s = el.getAsString().trim();
            if (s.isEmpty() || s.length() > MAX_WORD_LEN) {
                continue;
            }
            out.add(s);
            if (out.size() >= MAX_WORDS) {
                break;
            }
        }
        return out;
    }

    record WordLists(List<String> adjectives, List<String> nouns) {}
}
