package com.bingo.uyuni;

import com.bingo.uyuni.block.MirrorSaltBlock;
import com.bingo.uyuni.item.SaltMirrorItem;
import com.bingo.uyuni.fluid.ModFluids;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, UyuniMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, UyuniMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, UyuniMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UyuniMod.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, UyuniMod.MODID);

    // ---- Sounds ----
    /** 浅い水を踏む足音（sounds.json でバニラの水音を小さく・高めに鳴らす） */
    public static final RegistryObject<SoundEvent> MIRROR_STEP = SOUNDS.register("block.mirror_salt.step",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(UyuniMod.MODID, "block.mirror_salt.step")));

    private static final SoundType WET_SALT = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.CALCITE_BREAK,
            MIRROR_STEP,
            () -> SoundEvents.CALCITE_PLACE,
            () -> SoundEvents.CALCITE_HIT,
            () -> SoundEvents.CALCITE_FALL);

    // ---- Particles ----
    public static final RegistryObject<SimpleParticleType> RIPPLE =
            PARTICLES.register("ripple", () -> new SimpleParticleType(false));

    // ---- Blocks ----
    public static final RegistryObject<Block> SALT_BLOCK = BLOCKS.register("salt_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.8F)
                    .sound(SoundType.CALCITE)));

    public static final RegistryObject<Block> MIRROR_SALT = BLOCKS.register("mirror_salt",
            () -> new MirrorSaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.8F)
                    .sound(WET_SALT)));

    // ---- Items ----
    public static final RegistryObject<Item> SALT_BLOCK_ITEM = ITEMS.register("salt_block",
            () -> new BlockItem(SALT_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> MIRROR_SALT_ITEM = ITEMS.register("mirror_salt",
            () -> new BlockItem(MIRROR_SALT.get(), new Item.Properties()));
    public static final RegistryObject<Item> SALT_MIRROR = ITEMS.register("salt_mirror",
            () -> new SaltMirrorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---- Creative tab ----
    /** クリエイティブの専用タブ「ウユニ塩湖」 */
    public static final RegistryObject<CreativeModeTab> UYUNI_TAB = CREATIVE_TABS.register("uyuni",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.uyuni"))
                    .icon(() -> new ItemStack(SALT_MIRROR.get()))
                    .displayItems((params, output) -> {
                        output.accept(SALT_MIRROR.get());
                        output.accept(MIRROR_SALT_ITEM.get());
                        output.accept(SALT_BLOCK_ITEM.get());
                        output.accept(ModFluids.MIRROR_BRINE_BUCKET.get());
                    })
                    .build());
}
