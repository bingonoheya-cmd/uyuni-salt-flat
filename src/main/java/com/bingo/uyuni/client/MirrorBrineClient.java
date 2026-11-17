package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

/**
 * 鏡の塩水の見た目。
 *  - 青いバニラの水ではなく、白っぽく澄んだガラスのような水。小さな塩の粒がゆっくりきらめく
 *  - 中にもぐると、白く霞んだ塩水の中（すぐ先が見えなくなる）
 */
public class MirrorBrineClient implements IClientFluidTypeExtensions {
    private static final ResourceLocation STILL = new ResourceLocation(UyuniMod.MODID, "block/mirror_brine_still");
    private static final ResourceLocation FLOW = new ResourceLocation(UyuniMod.MODID, "block/mirror_brine_flow");

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOW;
    }

    /** ARGB。テクスチャ自体が色を持っているので、色はそのまま・透明度だけ少し下げる */
    @Override
    public int getTintColor() {
        return 0xD8FFFFFF;
    }

    @Override
    public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                   float darkenWorldAmount, Vector3f fluidFogColor) {
        return new Vector3f(0.80F, 0.88F, 0.90F);
    }

    @Override
    public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
                                float nearDistance, float farDistance, FogShape shape) {
        RenderSystem.setShaderFogStart(-2.0F);
        RenderSystem.setShaderFogEnd(10.0F);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
