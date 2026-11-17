package com.bingo.uyuni.block;

import com.bingo.uyuni.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 「鏡張りの塩原」ブロック。
 *
 * 仕組み:
 *  - 見た目は高さ 13/16 の塩の板。デフォルトで「鏡の塩水」に浸っている(flooded)。
 *  - 浸っている水はバニラの水ではなく、このModの「鏡の塩水」（水面の高さ約 14.2/16）。
 *    影パックの水表現が掛からないので、水面の見た目はこのModの水鏡だけで決まる。
 *  - バケツで塩水を汲む／注ぐことができる（鏡の塩水だけ。バニラの水は入らない）。
 *  - 当たり判定だけ 14.25/16 にしてあり、足が水面ぴったりに乗る。
 *    → プレイヤーは「水中」扱いにならず、泳ぎ減速も水しぶき音も出ない。
 *      見た目は「水面の上に立っている」ウユニ塩湖そのもの。
 */
public class MirrorSaltBlock extends Block implements BucketPickup, LiquidBlockContainer {
    /** 鏡の塩水に浸っているか */
    public static final BooleanProperty FLOODED = BooleanProperty.create("flooded");

    /** 見た目の塩の高さ */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);
    /** 水源の水面(8/9 ≒ 14.22px)よりわずかに上 */
    private static final VoxelShape WET_COLLISION = Block.box(0, 0, 0, 16, 14.25, 16);

    public MirrorSaltBlock(Properties properties) {
        super(properties);
        // flat 生成はブロックのデフォルト状態を置くので、デフォルト＝塩水に浸っている にしておく
        // （以前のバージョンの waterlogged=true のブロックも、読み込むとこの状態になる）
        registerDefaultState(stateDefinition.any().setValue(FLOODED, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLOODED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FLOODED) ? WET_COLLISION : SHAPE;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(FLOODED) ? ModFluids.MIRROR_BRINE.get().getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // 置いた場所に小さな「鏡」ができるよう塩水に浸った状態で設置する。
        // ただしネザーのように水が蒸発する世界では、乾いた塩の板になる
        boolean waterAllowed = !ctx.getLevel().dimensionType().ultraWarm();
        return defaultBlockState().setValue(FLOODED, waterAllowed);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(FLOODED)) {
            Fluid brine = ModFluids.MIRROR_BRINE.get();
            level.scheduleTick(pos, brine, brine.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // ---- バケツ: 鏡の塩水だけを汲む／注ぐ ----

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(FLOODED) && fluid == ModFluids.MIRROR_BRINE.get();
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (state.getValue(FLOODED) || fluid.getType() != ModFluids.MIRROR_BRINE.get()) return false;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(FLOODED, Boolean.TRUE), Block.UPDATE_ALL);
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
        return true;
    }

    @Override
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.getValue(FLOODED)) return ItemStack.EMPTY;
        level.setBlock(pos, state.setValue(FLOODED, Boolean.FALSE), Block.UPDATE_ALL);
        return new ItemStack(ModFluids.MIRROR_BRINE_BUCKET.get());
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    /** 同じブロック同士の側面は描画しない（地平線まで続く平原の描画負荷を抑える。Embeddiumでも有効） */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction dir) {
        if (dir.getAxis().isHorizontal() && adjacent.is(this)) {
            return true;
        }
        return super.skipRendering(state, adjacent, dir);
    }
}
