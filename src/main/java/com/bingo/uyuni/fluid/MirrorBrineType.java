package com.bingo.uyuni.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import java.util.function.Consumer;

/** 鏡の塩水の種類。見た目はクライアント側の MirrorBrineClient が決める */
public class MirrorBrineType extends FluidType {
    public MirrorBrineType(Properties properties) {
        super(properties);
    }

    /** ネザーのような暑い世界では、バケツで置いても蒸発する */
    @Override
    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return level.dimensionType().ultraWarm();
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        // FluidType のコンストラクタから、クライアントでだけ呼ばれる
        consumer.accept(new com.bingo.uyuni.client.MirrorBrineClient());
    }
}
