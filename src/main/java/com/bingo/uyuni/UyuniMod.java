package com.bingo.uyuni;

import com.bingo.uyuni.client.ClientConfig;
import com.bingo.uyuni.fluid.ModFluids;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(UyuniMod.MODID)
public class UyuniMod {
    public static final String MODID = "uyuni";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 塩原ディメンション。中身は data/uyuni/dimension/salt_flat.json で定義 */
    public static final ResourceKey<Level> SALT_FLAT =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(MODID, "salt_flat"));

    public UyuniMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 鏡の塩水（ブロックとバケツは ModRegistry の BLOCKS/ITEMS に入るので、先に読み込んでおく）
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.SOUNDS.register(modBus);
        ModRegistry.PARTICLES.register(modBus);
        ModRegistry.CREATIVE_TABS.register(modBus);

        modBus.addListener(this::commonSetup);

        // クライアント専用設定（サーバーでは読み込まれるだけで使われない）
        // （Mod を入れて最初に起動した時に config/uyuni-client.toml が自動で作られる）
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.bingo.uyuni.client.ClientInit.init(modBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModFluids::registerInteractions);
    }

}
