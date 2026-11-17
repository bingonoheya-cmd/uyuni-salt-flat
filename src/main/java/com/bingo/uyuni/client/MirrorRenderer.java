package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.bingo.uyuni.block.MirrorSaltBlock;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 塩原の「水鏡」。
 *
 * 1. 空の描画直後(AFTER_SKY)に、水面を境に上下反転した世界
 *    （空・太陽・星・雲・Mob・プレイヤー・置いたブロック）を専用の画面バッファへ描く。
 * 2. 半透明ブロックの描画後(AFTER_TRANSLUCENT_BLOCKS)に、水面の高さに大きな板を置き、
 *    専用シェーダーでそのバッファを貼る。
 *    - 見る角度で映り込みの強さが変わる(フレネル)。真下は足元の塩がうっすら透ける。
 *    - RippleField の波紋で反射像を歪ませる → 歩くと水鏡が揺れる。
 *
 * 影パック(Oculus)使用中は描画パイプラインが影パックのものに置き換わるため、
 * 自動でオフになり、影パック自身の水面反射が使われる。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MirrorRenderer {
    private MirrorRenderer() {}

    /** 塩原の水面の高さ（y=64 の水浸しブロックの水面 = 64 + 8/9） */
    public static final double MIRROR_Y = 64.0 + 8.0 / 9.0;
    private static final int MAX_RIPPLES = 16;
    private static final String[] RIPPLE_UNIFORMS = new String[MAX_RIPPLES];
    static {
        for (int i = 0; i < MAX_RIPPLES; i++) RIPPLE_UNIFORMS[i] = "Ripple" + i;
    }

    static ShaderInstance surfaceShader;
    private static TextureTarget reflection;
    private static boolean drawnThisFrame;
    static boolean failed;

    private static final List<BlockPos> BLOCK_CACHE = new ArrayList<>();
    private static long lastScanTime = Long.MIN_VALUE;
    private static BlockPos lastScanCenter = BlockPos.ZERO;

    /** 水鏡（どちらかのモード）が働いている状態か。波紋をパーティクルにするか水鏡の揺れにするかの判定に使う */
    public static boolean isActive() {
        if (!ClientConfig.MIRROR_ENABLED.get()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != UyuniMod.SALT_FLAT) return false;
        return ShaderCompat.shaderPackInUse() ? ShaderMirror.isAvailable() : isVanillaAvailable();
    }

    /** 影MODなし用の水鏡が使えるか */
    static boolean isVanillaAvailable() {
        return !failed && surfaceShader != null && !ShaderCompat.shaderPackInUse();
    }

    private static boolean isVanillaActive() {
        return isActive() && isVanillaAvailable();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            drawnThisFrame = false;
            if (!isVanillaActive() || event.getCamera().getPosition().y <= MIRROR_Y) return;
            try {
                renderReflection(event);
                drawnThisFrame = true;
                MirrorDiagnostics.vanillaReflectionFrames++;
            } catch (Throwable t) {
                failed = true;
                MirrorDiagnostics.fail("vanilla", t);
                restoreState();
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && drawnThisFrame) {
            try {
                drawSurface(event);
                MirrorDiagnostics.vanillaSurfaceFrames++;
            } catch (Throwable t) {
                failed = true;
                MirrorDiagnostics.fail("vanilla", t);
            }
        }
    }

    // ------------------------------------------------------------------ reflection pass

    private static void renderReflection(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        RenderTarget main = mc.getMainRenderTarget();
        ensureTarget(main.width, main.height);

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        Matrix4f projection = event.getProjectionMatrix();
        float renderDistance = mc.gameRenderer.getRenderDistance();

        float[] fog = RenderSystem.getShaderFogColor();
        reflection.setClearColor(fog[0], fog[1], fog[2], 1.0F);
        reflection.clear(Minecraft.ON_OSX);
        reflection.bindWrite(true);

        // 上下反転すると三角形の表裏が逆になるので、表面の定義も逆にする
        GL11.glFrontFace(GL11.GL_CW);
        try {
            // --- 空（無限遠なので回転だけ反転） ---
            PoseStack skyPose = copy(event.getPoseStack());
            skyPose.scale(1.0F, -1.0F, 1.0F);
            // renderSky は「いま設定されているシェーダー」で空の円盤を描く作りなので、
            // バニラと同じく直前に位置シェーダーを設定しておく（しないと前に使われたシェーダーで描かれて空が崩れる）
            RenderSystem.setShader(GameRenderer::getPositionShader);
            mc.levelRenderer.renderSky(skyPose, projection, pt, camera, false,
                    () -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, renderDistance, false, pt));

            FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(renderDistance, 32.0F), false, pt);

            // --- 世界：水面 y=MIRROR_Y を境に反転 ---
            PoseStack world = copy(event.getPoseStack());
            world.translate(0.0, 2.0 * (MIRROR_Y - cam.y), 0.0);
            world.scale(1.0F, -1.0F, 1.0F);

            if (mc.options.getCloudsType() != CloudStatus.OFF) {
                mc.levelRenderer.renderClouds(world, projection, pt, cam.x, cam.y, cam.z);
            }

            Lighting.setupLevel(world.last().pose());
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

            renderEntities(mc, level, world, buffers, cam, pt);
            renderBlocks(mc, level, world, buffers, cam);

            buffers.endBatch();
        } finally {
            restoreState();
        }
    }

    private static void renderEntities(Minecraft mc, ClientLevel level, PoseStack world,
                                       MultiBufferSource.BufferSource buffers, Vec3 cam, float pt) {
        renderEntities(mc, level, world, buffers, cam, pt, true);
    }

    /**
     * エンティティを描く。距離の上限は「普段その Mob が見える距離」と同じ
     * （ビデオ設定の「エンティティ描画距離」に従う）ので、見えている Mob はどれだけ遠くても映る。
     *
     * @param includeCamera 一人称視点の自分自身も描くか（水鏡には自分が映るので true）
     */
    static void renderEntities(Minecraft mc, ClientLevel level, PoseStack pose,
                               MultiBufferSource buffers, Vec3 cam, float pt, boolean includeCamera) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean shadows = mc.options.entityShadows().get();
        Entity cameraEntity = mc.getCameraEntity();
        boolean firstPerson = !mc.gameRenderer.getMainCamera().isDetached();
        dispatcher.setRenderShadow(false);
        try {
            for (Entity entity : level.entitiesForRendering()) {
                boolean isSelf = entity == cameraEntity && firstPerson;
                if (isSelf && !includeCamera) continue;
                if (!isSelf && !entity.shouldRender(cam.x, cam.y, cam.z)) continue;
                double x = Mth.lerp(pt, entity.xOld, entity.getX());
                double y = Mth.lerp(pt, entity.yOld, entity.getY());
                double z = Mth.lerp(pt, entity.zOld, entity.getZ());
                if (y + entity.getBbHeight() < MIRROR_Y) continue; // 水面より下にいるものは映らない
                float yaw = Mth.lerp(pt, entity.yRotO, entity.getYRot());
                try {
                    // Mob ごとに姿勢をコピーして渡す。どれか 1 体の描画が途中で失敗しても、
                    // 積まれたまま戻らない姿勢が後の Mob に影響しないように
                    dispatcher.render(entity, x - cam.x, y - cam.y, z - cam.z, yaw, pt, copy(pose), buffers,
                            dispatcher.getPackedLightCoords(entity, pt));
                } catch (Throwable ignored) {
                    // 他MODのエンティティで失敗しても水鏡全体は止めない
                }
            }
        } finally {
            dispatcher.setRenderShadow(shadows);
        }
    }

    static void renderBlocks(Minecraft mc, ClientLevel level, PoseStack world,
                             MultiBufferSource buffers, Vec3 cam) {
        int radius = ClientConfig.MIRROR_BLOCK_RADIUS.get();
        if (radius <= 0) {
            BLOCK_CACHE.clear();
            return;
        }
        BlockPos center = BlockPos.containing(cam.x, MIRROR_Y, cam.z);
        long time = level.getGameTime();
        if (time - lastScanTime >= 20 || center.distManhattan(lastScanCenter) > 4) {
            scanBlocks(level, center, radius);
            lastScanTime = time;
            lastScanCenter = center;
        }
        for (BlockPos pos : BLOCK_CACHE) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
            PoseStack local = copy(world);
            local.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            try {
                mc.getBlockRenderer().renderSingleBlock(state, local, buffers,
                        LevelRenderer.getLightColor(level, pos), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            } catch (Throwable ignored) {
                // 他MODのブロックで失敗しても水鏡全体は止めない
            }
        }
    }

    private static void scanBlocks(ClientLevel level, BlockPos center, int radius) {
        BLOCK_CACHE.clear();
        int baseY = Mth.floor(MIRROR_Y);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = baseY; y <= baseY + 32; y++) {
                    m.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState state = level.getBlockState(m);
                    if (state.isAir() || state.getBlock() instanceof MirrorSaltBlock) continue;
                    if (state.getRenderShape() != RenderShape.MODEL) continue;
                    BLOCK_CACHE.add(m.immutable());
                    if (BLOCK_CACHE.size() >= 4096) return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ surface pass

    private static void drawSurface(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = surfaceShader;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();

        setRippleUniforms(shader, cam, pt);
        shader.safeGetUniform("MirrorParams").set(
                ClientConfig.MIRROR_REFLECTIVITY.get().floatValue(),
                ClientConfig.MIRROR_GRAZING_REFLECTIVITY.get().floatValue(),
                ClientConfig.MIRROR_WOBBLE.get().floatValue(),
                0.0F);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(event.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, reflection.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        float y = (float) (MIRROR_Y - cam.y + 0.002);
        float r = mc.options.getEffectiveRenderDistance() * 16.0F + 32.0F;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-r, y, -r).endVertex();
        bb.vertex(-r, y, r).endVertex();
        bb.vertex(r, y, r).endVertex();
        bb.vertex(r, y, -r).endVertex();
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    static void setRippleUniforms(ShaderInstance shader, Vec3 cam, float pt) {
        // 波紋ごとのいびつさを場所に固定するための、カメラ位置（float の精度に収まるよう 1024 の余り）
        shader.safeGetUniform("CameraXZ").set((float) Math.floorMod((long) Math.floor(cam.x), 1024L) + (float) (cam.x - Math.floor(cam.x)),
                (float) Math.floorMod((long) Math.floor(cam.z), 1024L) + (float) (cam.z - Math.floor(cam.z)));
        float now = RippleField.now(pt);
        List<RippleField.Ripple> active = new ArrayList<>(RippleField.all());
        active.sort(Comparator.comparingDouble(rp -> {
            double dx = rp.x() - cam.x, dz = rp.z() - cam.z;
            return dx * dx + dz * dz;
        }));
        int i = 0;
        for (RippleField.Ripple rp : active) {
            if (i >= MAX_RIPPLES) break;
            float age = (now - rp.startTick()) / 20.0F;
            if (age < 0.0F || age > RippleField.LIFE_SECONDS) continue;
            shader.safeGetUniform(RIPPLE_UNIFORMS[i]).set(
                    (float) (rp.x() - cam.x), (float) (rp.z() - cam.z), age, rp.strength());
            i++;
        }
        for (; i < MAX_RIPPLES; i++) {
            shader.safeGetUniform(RIPPLE_UNIFORMS[i]).set(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void ensureTarget(int width, int height) {
        if (reflection == null) {
            reflection = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            reflection.setFilterMode(GL11.GL_LINEAR);
        } else if (reflection.width != width || reflection.height != height) {
            reflection.resize(width, height, Minecraft.ON_OSX);
            reflection.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static void restoreState() {
        GL11.glFrontFace(GL11.GL_CCW);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    static PoseStack copy(PoseStack src) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(src.last().pose());
        ps.last().normal().set(src.last().normal());
        return ps;
    }
}
