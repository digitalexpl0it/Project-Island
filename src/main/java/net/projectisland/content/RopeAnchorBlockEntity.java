package net.projectisland.content;

import java.util.Optional;
import java.util.UUID;


import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.projectisland.ProjectIsland;
import net.projectisland.island.FloatingIslandSavedData;
import net.projectisland.island.IslandWorld;
import net.projectisland.island.RopeLink;
import org.jetbrains.annotations.Nullable;

public final class RopeAnchorBlockEntity extends BlockEntity {
    private static final String TAG_ORIG = "Orig";
    private static final String TAG_LINK = "Link";
    private static final String TAG_ENDPOINT = "Endpoint";

    @Nullable
    private BlockState originalState;

    @Nullable
    private UUID linkId;

    /** 0 = unknown, 1 = A, 2 = B */
    private int endpointKind;

    public RopeAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ProjectIslandContent.ROPE_ANCHOR_BE, pos, state);
    }

    public boolean hasLink() {
        return linkId != null;
    }

    public Optional<UUID> linkId() {
        return Optional.ofNullable(linkId);
    }

    public void setOriginalState(BlockState state) {
        this.originalState = state;
        setChanged();
    }

    public void setLink(UUID link, int endpointKind) {
        this.linkId = link;
        this.endpointKind = endpointKind;
        setChanged();
    }

    public void clearLink() {
        this.linkId = null;
        this.endpointKind = 0;
        setChanged();
    }

    @Nullable
    public BlockState getOriginalState() {
        return originalState;
    }

    @Nullable
    public UUID getLinkId() {
        return linkId;
    }

    /**
     * Called before this anchor block is removed. Removes saved {@link RopeLink} data when present, clears the peer
     * anchor's link flag, and schedules restoring both positions to their stored original blocks (deferred one tick so
     * vanilla break logic does not overwrite the restore in the same tick).
     */
    /**
     * Removes saved link data and restores anchor blocks. {@code breaker} is optional (e.g. strain snap has no
     * player).
     */
    public void handleServerBreak(ServerLevel level, @Nullable Player breaker) {
        FloatingIslandSavedData data;
        try {
            data = IslandWorld.get(level);
        } catch (IllegalStateException ignored) {
            return;
        }

        final BlockPos selfPos = worldPosition.immutable();
        final BlockState selfOrig = originalState;
        final UUID lid = linkId;

        BlockPos otherPos = null;
        BlockState otherOrig = null;
        if (lid != null) {
            Optional<RopeLink> linkOpt = data.getRopeLink(lid);
            if (linkOpt.isPresent()) {
                RopeLink link = linkOpt.get();
                otherPos = link.otherAnchor(selfPos);
                if (otherPos != null) {
                    BlockEntity peerBe = level.getBlockEntity(otherPos);
                    if (peerBe instanceof RopeAnchorBlockEntity peer) {
                        otherOrig = peer.originalState;
                        peer.clearLink();
                    }
                }
            }
            data.removeRopeLink(lid);
        }
        clearLink();

        final BlockPos fOther = otherPos;
        final BlockState fOtherOrig = otherOrig;
        level.getServer()
                .execute(
                        () -> restoreOriginalOrAir(level, selfPos, selfOrig, fOther, fOtherOrig));
    }

    private static void restoreOriginalOrAir(
            ServerLevel level, BlockPos selfPos, @Nullable BlockState selfOrig, @Nullable BlockPos otherPos, @Nullable BlockState otherOrig) {
        restoreOne(level, selfPos, selfOrig);
        restoreOne(level, otherPos, otherOrig);
    }

    private static void restoreOne(ServerLevel level, @Nullable BlockPos pos, @Nullable BlockState original) {
        if (pos == null) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() != ProjectIslandContent.ROPE_ANCHOR && !current.isAir()) {
            return;
        }
        if (original != null) {
            level.setBlock(pos, original, Block.UPDATE_ALL);
        } else if (current.getBlock() == ProjectIslandContent.ROPE_ANCHOR) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (originalState != null) {
            tag.put(TAG_ORIG, net.minecraft.nbt.NbtUtils.writeBlockState(originalState));
        }
        if (linkId != null) {
            tag.putUUID(TAG_LINK, linkId);
        }
        if (endpointKind != 0) {
            tag.putInt(TAG_ENDPOINT, endpointKind);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        originalState = null;
        linkId = null;
        endpointKind = 0;
        if (tag.contains(TAG_ORIG)) {
            try {
                originalState = net.minecraft.nbt.NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound(TAG_ORIG));
            } catch (Exception e) {
                ProjectIsland.LOGGER.debug("Failed to read rope anchor original state at {}", worldPosition, e);
            }
        }
        if (tag.hasUUID(TAG_LINK)) {
            linkId = tag.getUUID(TAG_LINK);
        }
        if (tag.contains(TAG_ENDPOINT)) {
            endpointKind = tag.getInt(TAG_ENDPOINT);
        }
    }

    /**
     * Removes {@code link} from saved data and restores both anchor blocks (same as breaking one anchor by hand).
     */
    public static void severLinkFromSavedData(ServerLevel level, RopeLink link) {
        BlockEntity be = level.getBlockEntity(link.fromAnchorPos());
        if (be instanceof RopeAnchorBlockEntity ra && link.id().equals(ra.getLinkId())) {
            ra.handleServerBreak(level, null);
            return;
        }
        be = level.getBlockEntity(link.toAnchorPos());
        if (be instanceof RopeAnchorBlockEntity rb && link.id().equals(rb.getLinkId())) {
            rb.handleServerBreak(level, null);
            return;
        }
        IslandWorld.get(level).removeRopeLink(link.id());
        ProjectIsland.LOGGER.warn(
                "Rope link {} could not sever at anchors (chunks or blocks missing) — removed from saved data only",
                link.id());
    }
}

