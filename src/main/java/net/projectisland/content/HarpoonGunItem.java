package net.projectisland.content;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.island.FloatingIslandKey;
import net.projectisland.island.FloatingIslandSavedData;
import net.projectisland.island.IslandWorld;
import net.projectisland.island.RopeLink;
import net.projectisland.island.RopeProgression;
import net.projectisland.island.RopeTopology;
import net.projectisland.network.ActionBarToastPayload;
import net.projectisland.worldgen.FloatingIslandLayout;

/**
 * MVP: server-side raycast \"harpoon\". First successful anchor arms the rope; second anchor creates a saved-data link.
 * A future projectile can replace this without changing the server validation or link storage.
 */
public final class HarpoonGunItem extends Item {
    private static final String TAG_PENDING = "projectisland_pending_rope";
    private static final String TAG_POS = "Pos";
    private static final String TAG_KEY = "Key";

    public HarpoonGunItem(Properties props) {
        super(props);
    }

    private static void actionBar(ServerPlayer player, String translationKey, Object... args) {
        ActionBarToastPayload.sendWithArgs(player, translationKey, args);
    }

    /**
     * Removes the first-shot rope anchor and clears pending harpoon state when the second shot cannot complete a link.
     */
    private static void clearPendingFirstAnchor(ServerLevel sl, ServerPlayer sp, CompoundTag pd) {
        CompoundTag p = pd.getCompound(TAG_PENDING);
        long packed = p.getLong(TAG_POS);
        UUID pendingLinkId = p.getUUID("Link");
        pd.remove(TAG_PENDING);
        BlockPos aPos = BlockPos.of(packed);
        var be = sl.getBlockEntity(aPos);
        if (be instanceof RopeAnchorBlockEntity ra && pendingLinkId.equals(ra.getLinkId())) {
            ra.handleServerBreak(sl, sp);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(sl)) {
            actionBar(sp, "projectisland.harpoon.wrong_dimension");
            return InteractionResultHolder.pass(stack);
        }

        int raycastRange = Math.max(1, Config.ROPE_LINK_RAYCAST_RANGE_BLOCKS.getAsInt());
        int maxLinkLenBase = Math.max(1, Config.ROPE_LINK_MAX_LENGTH_BLOCKS.getAsInt());
        RopeProgression.RopeTier tier = RopeProgression.tierFor(sp);
        int maxLinkLen = Math.max(1, (int) Math.round(maxLinkLenBase * tier.maxLengthMultiplier));

        BlockHitResult hit = raycast(sl, sp, raycastRange);
        if (hit.getType() != HitResult.Type.BLOCK) {
            actionBar(sp, "projectisland.harpoon.no_block", raycastRange);
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = sl.getBlockState(pos);
        if (state.getBlock() instanceof FallingBlock) {
            actionBar(sp, "projectisland.harpoon.falling_block");
            return InteractionResultHolder.pass(stack);
        }
        if (!canBecomeAnchor(state)) {
            actionBar(sp, "projectisland.harpoon.bad_block");
            return InteractionResultHolder.pass(stack);
        }

        Optional<FloatingIslandKey> keyOpt = FloatingIslandLayout.islandOwningSurface(
                pos.getX(), pos.getZ(), sl.getMinBuildHeight(), sl.getMaxBuildHeight());
        if (keyOpt.isEmpty()) {
            actionBar(sp, "projectisland.harpoon.not_island_surface");
            return InteractionResultHolder.pass(stack);
        }
        FloatingIslandKey key = keyOpt.get();

        FloatingIslandSavedData data = IslandWorld.get(sl);
        CompoundTag pd = sp.getPersistentData();
        if (!pd.contains(TAG_PENDING)) {
            // First endpoint.
            UUID linkId = UUID.randomUUID();
            if (!placeAnchor(sl, pos, state, linkId, 1)) {
                actionBar(sp, "projectisland.harpoon.place_first_failed");
                return InteractionResultHolder.pass(stack);
            }
            CompoundTag p = new CompoundTag();
            p.putLong(TAG_POS, pos.asLong());
            p.putString(TAG_KEY, key.toStorageKey());
            p.putUUID("Link", linkId);
            pd.put(TAG_PENDING, p);
            stack.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            actionBar(sp, "projectisland.harpoon.first_set", maxLinkLen);
            return InteractionResultHolder.success(stack);
        }

        // Second endpoint (do not clear pending until validation passes — mis-clicks should keep the first shot armed).
        CompoundTag p = pd.getCompound(TAG_PENDING);
        long packed = p.getLong(TAG_POS);
        BlockPos aPos = BlockPos.of(packed);
        UUID linkId = p.getUUID("Link");
        Optional<FloatingIslandKey> aKeyOpt = FloatingIslandKey.parseStorageKey(p.getString(TAG_KEY));
        if (aKeyOpt.isEmpty()) {
            pd.remove(TAG_PENDING);
            actionBar(sp, "projectisland.harpoon.corrupt_pending");
            return InteractionResultHolder.pass(stack);
        }
        FloatingIslandKey aKey = aKeyOpt.get();
        if (aKey.equals(key)) {
            actionBar(sp, "projectisland.harpoon.same_island");
            return InteractionResultHolder.pass(stack);
        }
        double dist = Math.sqrt(aPos.distSqr(pos));
        if (dist > maxLinkLen) {
            clearPendingFirstAnchor(sl, sp, pd);
            ActionBarToastPayload.sendForDuration(
                    sp, "projectisland.harpoon.too_far", ActionBarToastPayload.LONG_READ_VISIBLE_TICKS, maxLinkLen, Math.round(dist));
            return InteractionResultHolder.pass(stack);
        }
        Optional<String> topo = RopeTopology.validateNewRopeLink(data, sp.getUUID(), aKey, key);
        if (topo.isPresent()) {
            clearPendingFirstAnchor(sl, sp, pd);
            ActionBarToastPayload.send(sp, topo.get(), ActionBarToastPayload.LONG_READ_VISIBLE_TICKS);
            return InteractionResultHolder.pass(stack);
        }
        if (!placeAnchor(sl, pos, state, linkId, 2)) {
            clearPendingFirstAnchor(sl, sp, pd);
            ActionBarToastPayload.send(sp, "projectisland.harpoon.second_place_failed", ActionBarToastPayload.LONG_READ_VISIBLE_TICKS);
            return InteractionResultHolder.pass(stack);
        }

        pd.remove(TAG_PENDING);
        float ropeMaxHpBase = (float) Config.ROPE_LINK_MAX_HEALTH.getAsDouble();
        float ropeMaxHp = (float) (ropeMaxHpBase * tier.maxHealthMultiplier);
        data.putRopeLink(new RopeLink(linkId, sp.getUUID(), aKey, key, aPos, pos, maxLinkLen, ropeMaxHp, ropeMaxHp));
        boolean autoClaimed = data.tryAutoClaimIslandAfterRopePlaced(sp.getUUID(), aKey, key, sl.getGameTime());
        stack.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (autoClaimed) {
            actionBar(sp, "projectisland.harpoon.linked_and_claimed");
        } else {
            actionBar(sp, "projectisland.harpoon.linked");
        }
        return InteractionResultHolder.success(stack);
    }

    private static BlockHitResult raycast(ServerLevel level, ServerPlayer player, int rangeBlocks) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.x * rangeBlocks, look.y * rangeBlocks, look.z * rangeBlocks);
        return level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static boolean canBecomeAnchor(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof FallingBlock) {
            return false;
        }
        // Avoid turning air-like / replaceables into anchors.
        return !state.isAir() && state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0f;
    }

    private static boolean placeAnchor(ServerLevel level, BlockPos pos, BlockState original, UUID linkId, int endpointKind) {
        // Replace the block with the anchor and store original state in BE for future restore behavior.
        boolean ok = level.setBlock(pos, ProjectIslandContent.ROPE_ANCHOR.defaultBlockState(), Block.UPDATE_ALL);
        if (!ok) {
            return false;
        }
        var be = level.getBlockEntity(pos);
        if (be instanceof RopeAnchorBlockEntity anchor) {
            anchor.setOriginalState(original);
            anchor.setLink(linkId, endpointKind);
            return true;
        }
        ProjectIsland.LOGGER.warn("Placed rope anchor block but missing block entity at {}", pos);
        return false;
    }
}

