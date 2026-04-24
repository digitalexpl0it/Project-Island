package net.projectisland.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.projectisland.ClientConfig;
import net.projectisland.ProjectIsland;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

@EventBusSubscriber(modid = ProjectIsland.MOD_ID, value = Dist.CLIENT)
public final class IslandHudRenderer {
    private static final int ID_LINE_BASE = 0xFFB0B8C8;
    /** Max distance vs {@code renderDistance * 16} so labels disappear a bit before chunk mesh typically drops. */
    private static final double HUD_DISTANCE_FRACTION_OF_RENDER_RADIUS = 0.90d;

    private IslandHudRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // AFTER_LEVEL: runs after the world pass (including fabulous/translucent targets). Translucent stage can skip
        // custom geometry in some graphics modes; world-space HUD labels are safer here.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!ClientConfig.ISLAND_HUD_SHOW.getAsBoolean()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!Level.OVERWORLD.equals(mc.level.dimension())) {
            return;
        }
        ClientLevel clientLevel = (ClientLevel) mc.level;
        float scale = (float) ClientConfig.ISLAND_HUD_TEXT_SCALE.getAsDouble();
        boolean seeThrough = ClientConfig.ISLAND_HUD_SEE_THROUGH_TEXT.getAsBoolean();

        var pose = event.getPoseStack();
        var frustum = event.getFrustum();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double cullSpan = Mth.clamp(56.0d * (scale / 0.02d), 48.0d, 160.0d);

        for (IslandHudBeacon b : IslandHudClientCache.beacons()) {
            if (!shouldDrawHudForBeacon(clientLevel, mc, b)) {
                continue;
            }
            Vec3 at = new Vec3(b.x(), b.y(), b.z());
            if (!frustum.isVisible(AABB.ofSize(at, cullSpan, cullSpan * 1.6d, cullSpan))) {
                continue;
            }
            int titleC = brightenForNight(mc, event, b.x(), b.y(), b.z(), b.titleColorArgb());
            int statusC = brightenForNight(mc, event, b.x(), b.y(), b.z(), b.statusColorArgb());
            int idC = brightenForNight(mc, event, b.x(), b.y(), b.z(), ID_LINE_BASE);
            IslandHudWorldBillboard.render(mc, pose, buffers, b, scale, seeThrough, titleC, statusC, idC);
        }
        buffers.endBatch();
    }

    /**
     * Hide labels when the beacon column is not in a loaded client chunk or is farther than roughly chunk render
     * distance, so tags do not float in empty sky where islands are not drawn.
     */
    private static boolean shouldDrawHudForBeacon(ClientLevel level, Minecraft mc, IslandHudBeacon b) {
        if (!mc.gameRenderer.getMainCamera().isInitialized()) {
            return false;
        }
        int chunkX = Mth.floor(b.x()) >> 4;
        int chunkZ = Mth.floor(b.z()) >> 4;
        int renderChunks = Mth.clamp(mc.options.getEffectiveRenderDistance(), 2, 32);
        if (!level.hasChunk(chunkX, chunkZ)) {
            int px = mc.player.blockPosition().getX() >> 4;
            int pz = mc.player.blockPosition().getZ() >> 4;
            int cheb = Math.max(Math.abs(chunkX - px), Math.abs(chunkZ - pz));
            if (cheb > renderChunks + 3) {
                return false;
            }
        }
        double maxBlocks = renderChunks * 16.0 * HUD_DISTANCE_FRACTION_OF_RENDER_RADIUS;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = b.x() - cam.x;
        double dy = b.y() - cam.y;
        double dz = b.z() - cam.z;
        return dx * dx + dy * dy + dz * dz <= maxBlocks * maxBlocks;
    }

    private static int brightenForNight(Minecraft mc, RenderLevelStageEvent event, double x, double y, double z, int argb) {
        double strength = ClientConfig.ISLAND_HUD_NIGHT_COLOR_BOOST.getAsDouble();
        if (strength <= 0.0d || !(mc.level instanceof ClientLevel level)) {
            return argb;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float skyDarken = level.getSkyDarken(partialTick);
        BlockPos pos = BlockPos.containing(x, y, z);
        int lit = Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        float localShadow = 1f - lit / 15f;
        float t = Mth.clamp((float) (strength * (0.55f * skyDarken + 0.45f * localShadow)), 0f, 1f);
        return lerpArgbTowardWhite(argb, t);
    }

    private static int lerpArgbTowardWhite(int argb, float t) {
        if (t <= 0f) {
            return argb;
        }
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
