package com.bingo.uyuni.client;

import com.bingo.uyuni.ModRegistry;
import com.bingo.uyuni.UyuniMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import com.bingo.uyuni.fluid.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.VertexFormat;

@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /** 鏡の塩水は半透明で描く（バニラの水と同じ層。影パックでも水面として奥行きが残る） */
    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.MIRROR_BRINE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.MIRROR_BRINE_FLOWING.get(), RenderType.translucent());
        });
    }

    /** バケツの中身の色 */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register(new DynamicFluidContainerModel.Colors(), ModFluids.MIRROR_BRINE_BUCKET.get());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.RIPPLE.get(), RippleParticle.Provider::new);
    }

    /** 水鏡シェーダー: assets/uyuni/shaders/core/*.json */
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        // 影MODなし用
        register(event, "mirror_surface", DefaultVertexFormat.POSITION, s -> MirrorRenderer.surfaceShader = s);
        // 影MOD併用モード用
        register(event, "mirror_depth_mask", DefaultVertexFormat.POSITION, s -> ShaderMirror.depthMaskShader = s);
        register(event, "mirror_flag", DefaultVertexFormat.POSITION_TEX, s -> ShaderMirror.flagShader = s);
        register(event, "mirror_sky", DefaultVertexFormat.POSITION, s -> ShaderMirror.skyShader = s);
        register(event, "mirror_sky_table", DefaultVertexFormat.POSITION, s -> ShaderMirror.skyTableShader = s);
        register(event, "mirror_composite", DefaultVertexFormat.POSITION, s -> ShaderMirror.compositeShader = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format,
                                 Consumer<ShaderInstance> onLoad) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(UyuniMod.MODID, name), format), onLoad);
        } catch (IOException e) {
            UyuniMod.LOGGER.error("Failed to load Uyuni shader '{}'; the related mirror feature will be disabled", name, e);
        }
    }
}
