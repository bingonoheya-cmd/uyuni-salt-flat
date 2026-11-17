package com.bingo.uyuni.fluid;

import com.bingo.uyuni.ModRegistry;
import com.bingo.uyuni.UyuniMod;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidInteractionRegistry;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「鏡の塩水」: ウユニ塩湖の水。見た目も性質もバニラの水とは別物の液体。
 *
 *  - 薄く白みがかった、ガラスのように澄んだ水（青くない）。小さな塩の粒がきらめく
 *  - 塩分が濃いので重く、とろりとしていて、流れは遅く 2〜3 ブロックしか広がらない
 *  - バニラの水と違って無限水源は作れない（汲めば減る）
 *  - 溶岩に触れると、流れている溶岩は「塩のブロック」に固まる（水分が飛んで塩が残る）
 *  - ネザーでは蒸発する
 *
 * 影MODとの関係:
 *  影パックはバニラの水(minecraft:water)にだけ、自前の水の表現（波・反射・水中の色）を掛ける。
 *  この塩水はそのリストに入っていないので、影パックの水表現が掛からず、
 *  水面の見た目はこのModの水鏡だけで決まる（影パックの水との上書き合戦や、波による縞が起きない）。
 */
public final class ModFluids {
    private ModFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, UyuniMod.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, UyuniMod.MODID);

    public static final RegistryObject<FluidType> MIRROR_BRINE_TYPE = FLUID_TYPES.register("mirror_brine",
            () -> new MirrorBrineType(FluidType.Properties.create()
                    .descriptionId("fluid_type.uyuni.mirror_brine")
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .canExtinguish(true)
                    .canHydrate(true)
                    .supportsBoating(true)
                    .canConvertToSource(false)        // 無限水源にならない
                    .fallDistanceModifier(0.0F)
                    .motionScale(0.010D)              // 水(0.014)より流れに押されにくい
                    .density(1200)                    // 濃い塩水は水より重い
                    .viscosity(1600)                  // とろりとしている
                    .temperature(290)
                    .pathType(BlockPathTypes.WATER)
                    .adjacentPathType(null)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));

    public static final RegistryObject<FlowingFluid> MIRROR_BRINE =
            FLUIDS.register("mirror_brine", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> MIRROR_BRINE_FLOWING =
            FLUIDS.register("flowing_mirror_brine", () -> new ForgeFlowingFluid.Flowing(properties()));

    public static final RegistryObject<LiquidBlock> MIRROR_BRINE_BLOCK = ModRegistry.BLOCKS.register("mirror_brine",
            () -> new LiquidBlock(MIRROR_BRINE, BlockBehaviour.Properties.copy(Blocks.WATER)
                    .mapColor(MapColor.SNOW)));

    public static final RegistryObject<Item> MIRROR_BRINE_BUCKET = ModRegistry.ITEMS.register("mirror_brine_bucket",
            () -> new BucketItem(MIRROR_BRINE, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(MIRROR_BRINE_TYPE, MIRROR_BRINE, MIRROR_BRINE_FLOWING)
                .bucket(MIRROR_BRINE_BUCKET)
                .block(MIRROR_BRINE_BLOCK)
                .slopeFindDistance(2)        // 水(4)より近くしか探さない
                .levelDecreasePerBlock(3)    // 水(1)よりすぐ薄くなる → 2〜3 ブロックで止まる
                .tickRate(15)                // 水(5)よりゆっくり流れる
                .explosionResistance(100.0F);
    }

    /** 溶岩との反応。共通セットアップ（enqueueWork）で呼ぶ */
    public static void registerInteractions() {
        FluidInteractionRegistry.addInteraction(ForgeMod.LAVA_TYPE.get(),
                new FluidInteractionRegistry.InteractionInformation(MIRROR_BRINE_TYPE.get(),
                        lava -> lava.isSource()
                                ? Blocks.OBSIDIAN.defaultBlockState()
                                : ModRegistry.SALT_BLOCK.get().defaultBlockState()));
    }
}
