package net.projectisland.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.projectisland.island.IslandSecondaryClaim;
import net.projectisland.island.IslandWorld;
import net.projectisland.island.RopeSurfingState;
import net.projectisland.network.ActionBarToastPayload;

public final class RopeAnchorBlock extends Block implements EntityBlock {
    private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 10, 16);
    private static final VoxelShape LOOP = Block.box(4, 10, 3, 12, 16, 13);
    private static final VoxelShape OUTLINE = Shapes.or(BASE, LOOP);

    public RopeAnchorBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RopeAnchorBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RopeAnchorBlockEntity anchor) {
                anchor.handleServerBreak(serverLevel, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RopeAnchorBlockEntity anchor && anchor.hasLink()) {
            return 15;
        }
        return 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // Prevent neighbor face-culling “void boxes” around the anchor.
        return Shapes.empty();
    }

    /**
     * Sneak + use empty hand: try to {@linkplain IslandSecondaryClaim claim} the island region this anchor sits on
     * (same rules as the command, plus this anchor must be your linked harpoon endpoint). Non-sneak empty hand: try
     * {@linkplain RopeSurfingState rope surfing} toward the linked other anchor when enabled.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return IslandWorld.keyAt(sl, pos)
                    .map(
                            key -> {
                                IslandSecondaryClaim.Outcome o = IslandSecondaryClaim.tryAtIsland(sp, sl, key, pos);
                                ActionBarToastPayload.send(sp, IslandSecondaryClaim.translationKey(o));
                                return o == IslandSecondaryClaim.Outcome.SUCCESS
                                        ? InteractionResult.SUCCESS
                                        : InteractionResult.FAIL;
                            })
                    .orElse(InteractionResult.PASS);
        }
        return RopeSurfingState.tryStart(sl, sp, pos);
    }
}

