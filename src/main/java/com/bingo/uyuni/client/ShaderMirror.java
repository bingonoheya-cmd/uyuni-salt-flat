package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Map;

/**
 * 影MOD(Oculus)と併用するための水鏡。
 *
 * いつ描くか（Forge と Oculus のソースで確認済み）:
 *   Oculus は LevelRenderer#renderLevel の最後で影パックの合成(composite/final)を終える。
 *   Forge はその直後、手のための奥行きリセットより「前」に AFTER_LEVEL を呼ぶ。
 *   → AFTER_LEVEL なら、影パックの完成した絵と、まだ消されていない奥行き（水面・Mob・手）が両方そろっている。
 *   （以前は GUI の直前に描いていたため奥行きが消えていて、Mob を描き直して代用していた）
 *
 * 手順:
 *  1. マスク   … 奥行きから「水面が見えている画素(a)」「空が見えている画素(b)」を求める。
 *                 Mob・パーティクル・ブロック・手など、手前に何があっても正しく除かれる。
 *  2. 鏡像の形 … ShaderReflection が影パックに渡した四角形をそのまま描く（その画素は空で上書きしない）
 *  3. 空マップ … 影パックが描いた空を、カメラが止まっている時だけ方向ごとに覚える
 *  4. 合成     … 水面の画素を、波紋で傾いた水面の向きで空を映して描き直す。鏡像の画素は揺らすだけ。
 *
 * 視線は「目の位置」から計算する。影パック使用中、Oculus は画面の揺れ(view bobbing)を
 * カメラの行列へ移すので、目はカメラ相対の原点から少しずれている。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShaderMirror {
    private ShaderMirror() {}

    static ShaderInstance depthMaskShader;
    static ShaderInstance flagShader;
    static ShaderInstance compositeShader;
    static ShaderInstance skyShader;
    /** 時刻ごとの空の色の表を覚える。読み込めなくても水鏡は動く（表なしの動きになる） */
    static ShaderInstance skyTableShader;

    private static final int SKY_SIZE = 1024;
    /** これより速くカメラを回している間は、すでに覚えた空を上書きしない（度/フレーム）。影パックのモーションブラー対策 */
    private static final float STILL_DEGREES = 0.08F;
    private static final float MOVING_DEGREES = 0.6F;
    private static final Vector3f PREV_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);

    private static TextureTarget mask;
    private static TextureTarget flag;
    private static TextureTarget sceneCopy;
    private static final TextureTarget[] SKY = new TextureTarget[2];
    private static int skyRead;
    private static boolean skyNeedsInit = true;
    private static ClientLevel skyLevel;

    // 時刻ごとの空の色の表（mirror_sky_table.fsh）
    private static final int TABLE_TIME_BINS = 64;
    private static final int TABLE_ELEV_BINS = 8;
    private static final TextureTarget[] TABLE = new TextureTarget[2];
    private static int tableRead;
    private static boolean tableNeedsInit = true;
    /** 空マップの「見た空」が、見ていない間に完全に古くなるまでの秒数（空の様子が変わらない時） */
    private static final float SKY_FRESH_SECONDS = 150.0F;
    /** 空の色・星・雨が変わる速さに応じて、さらに早く古くする度合い */
    private static final float SKY_CHANGE_AGING = 3.0F;
    private static final float[] SKY_STATE_PREV = new float[6];
    private static boolean haveSkyStatePrev;
    private static float skyStateDelta;
    private static long lastSkyNanos;

    // 空マップを覚えた時の空の様子（時刻・天気）。大きく変わったら、覚えている空を「古い空」にする
    private static final float[] SKY_STATE = new float[6];
    private static final float[] SKY_STATE_NOW = new float[6];
    private static boolean haveSkyState;
    private static float prevTimeOfDay = -1.0F;
    /** 1 フレームで時刻がこれ以上飛んだら（/time set など）すぐ塗り替える（1 日 = 1.0） */
    private static final float TIME_JUMP = 0.004F;

    static boolean failed;

    // 今フレームのカメラ情報（世界の描画中に記録しておく）
    private static long frame;
    private static long capturedFrame = -1;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static float partialTick;

    static long currentFrame() {
        return frame;
    }

    static boolean isAvailable() {
        return !failed && ClientConfig.MIRROR_WITH_SHADERS.get()
                && depthMaskShader != null && flagShader != null && compositeShader != null && skyShader != null;
    }

    static void requestSkyReset() {
        skyNeedsInit = true;
        tableNeedsInit = true;
    }

    private static boolean shouldRun() {
        return ShaderCompat.shaderPackInUse() && MirrorRenderer.isActive();
    }

    // ------------------------------------------------------------------ hooks

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (capturedFrame == frame || !shouldRun()) return;
            VIEW.set(event.getPoseStack().last().pose());
            PROJECTION.set(event.getProjectionMatrix());
            cameraPos = event.getCamera().getPosition();
            partialTick = event.getPartialTick();
            capturedFrame = frame;
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            composite();
        }
    }

    // ------------------------------------------------------------------ main

    private static void composite() {
        if (!shouldRun()) return;
        if (capturedFrame != frame) {
            MirrorDiagnostics.lastSkipReason = "camera info was not captured this frame";
            return;
        }
        if (cameraPos.y <= MirrorRenderer.MIRROR_Y) {
            MirrorDiagnostics.lastSkipReason = "camera is below the water surface";
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderTarget main = mc.getMainRenderTarget();
            ensureTargets(main.width, main.height);
            if (skyLevel != level) {
                skyLevel = level;
                skyNeedsInit = true;
                tableNeedsInit = true;
            }
            RenderSystem.setProjectionMatrix(new Matrix4f(PROJECTION), VertexSorting.DISTANCE_TO_ORIGIN);

            Matrix4f projView = new Matrix4f(PROJECTION).mul(VIEW);
            Matrix4f invProjView = new Matrix4f(projView).invert();
            Vector3f eye = new Matrix4f(VIEW).invert().transformPosition(new Vector3f());
            float planeY = (float) (MirrorRenderer.MIRROR_Y - cameraPos.y);

            renderDepthMask(mc, main, invProjView, eye, planeY);
            renderFlags();
            copyScene(main);
            updateSky(projView);
            updateTable(projView);
            drawComposite(main, projView, invProjView, eye, planeY);
            MirrorDiagnostics.shaderCompositeFrames++;
            MirrorDiagnostics.lastSkipReason = "-";
        } catch (Throwable t) {
            failed = true;
            MirrorDiagnostics.fail("composite", t);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    /** 1. 奥行きから mask を作る: a=水面が見えている、b=空が見えている */
    private static void renderDepthMask(Minecraft mc, RenderTarget main, Matrix4f invProjView, Vector3f eye, float planeY) {
        mask.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        mask.clear(Minecraft.ON_OSX);
        mask.bindWrite(true);

        ShaderInstance shader = depthMaskShader;
        shader.safeGetUniform("InvProjView").set(invProjView);
        shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z);
        shader.safeGetUniform("PlaneY").set(planeY);
        shader.safeGetUniform("Radius").set(mc.options.getEffectiveRenderDistance() * 16.0F + 16.0F);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, main.getDepthTextureId());
        drawFullscreen();
    }

    /** 2. 鏡像の形（ShaderReflection が影パックに渡した四角形そのもの） */
    private static void renderFlags() {
        flag.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        flag.clear(Minecraft.ON_OSX);
        if (!ShaderReflection.DECALS.isFor(frame)) return;
        flag.bindWrite(true);

        ShaderInstance shader = flagShader;
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        for (Map.Entry<ResourceLocation, FloatArrayList> entry : ShaderReflection.DECALS.textured().entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            RenderSystem.setShaderTexture(0, entry.getKey());
            shader.safeGetUniform("UseTexture").set(1.0F);
            drawQuads(entry.getValue());
        }
        FloatArrayList plain = ShaderReflection.DECALS.untextured();
        if (!plain.isEmpty()) {
            RenderSystem.setShaderTexture(0, MissingTextureAtlasSprite.getLocation());
            shader.safeGetUniform("UseTexture").set(0.0F);
            drawQuads(plain);
        }
    }

    private static void drawQuads(FloatArrayList data) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[] a = data.elements();
        int n = data.size() - data.size() % 20;
        for (int i = 0; i < n; i += 5) {
            bb.vertex(a[i], a[i + 1], a[i + 2]).uv(a[i + 3], a[i + 4]).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** 3a. 影パックが完成させた絵をコピー */
    private static void copyScene(RenderTarget main) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, sceneCopy.width, sceneCopy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /** 3b. 空マップを更新（前のフレームの空マップを読んで、新しい方へ書く） */
    private static void updateSky(Matrix4f projView) {
        ShaderInstance shader = skyShader;
        int write = 1 - skyRead;
        float mode;
        if (skyNeedsInit) {
            mode = 0.0F;
            skyNeedsInit = false;
        } else {
            mode = 1.0F;
        }

        // カメラの回る速さ（前のフレームの向きとの角度）。止まっている時だけ、覚えた空を上書きする
        Vector3f forward = VIEW.positiveZ(new Vector3f()).negate();
        float degrees = (float) Math.toDegrees(Math.acos(Mth.clamp(forward.dot(PREV_FORWARD), -1.0F, 1.0F)));
        PREV_FORWARD.set(forward);
        float capture = 1.0F - Mth.clamp((degrees - STILL_DEGREES) / (MOVING_DEGREES - STILL_DEGREES), 0.0F, 1.0F);
        if (capture > 0.5F) MirrorDiagnostics.skyStillFrames++;

        shader.safeGetUniform("ProjView").set(projView);
        // 空はできるだけリアルタイムに: 見えている方向は毎フレーム書き足す。
        //  - 止まっている時は 35% ずつ（すぐ今の空になる）
        //  - 動いている時も 6% ずつ（影パックのモーションブラーで流れた星や雲を焼き付けない程度に）
        // 見えていない方向は、毎フレーム 3% ずつまわりの空になじませる
        // （古い雲がくっきり残って、今の空との境目がパネル状の継ぎ目にならないように）
        float blend = 0.06F + (0.35F - 0.06F) * capture;
        float staleAll = mode > 0.5F && skyChanged() ? 1.0F : 0.0F;
        if (mode < 0.5F) haveSkyState = false;
        shader.safeGetUniform("SkyParams").set(mode, blend, 0.03F, staleAll);

        // 見ていない空が古くなる速さ。普段は SKY_FRESH_SECONDS 秒で、夕焼けや雨の降り始めのように
        // 空の様子が変わっている間はもっと早く古くなる（古くなった所は水面で「今の時刻の空」に置き換わる）
        long now = System.nanoTime();
        float dt = lastSkyNanos == 0L ? 0.0F : Mth.clamp((now - lastSkyNanos) / 1.0e9F, 0.0F, 0.25F);
        lastSkyNanos = now;
        float aging = dt / SKY_FRESH_SECONDS + SKY_CHANGE_AGING * skyStateDelta;
        // 「見た空」は 0.6〜1.0 の 102 段。1 フレームに確率 p で 1 段下がる
        float p = Mth.clamp(aging * 102.0F, 0.0F, 1.0F);
        shader.safeGetUniform("SkyDecay").set(p, (float) (frame % 4096L) * 7.31F);
        shader.safeGetUniform("SkyTexel").set(1.0F / SKY_SIZE, 1.0F / SKY_SIZE);
        shader.safeGetUniform("ScreenTexel").set(1.0F / sceneCopy.width, 1.0F / sceneCopy.height);

        SKY[write].bindWrite(true);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());
        RenderSystem.setShaderTexture(1, SKY[skyRead].getColorTextureId());
        RenderSystem.setShaderTexture(2, mask.getColorTextureId());
        drawFullscreen();
        skyRead = write;
    }

    /**
     * 時刻が一気に飛んだか（/time set など）。
     * 飛んでいたら、覚えている空はすべて「古い空」になり、水面では今の時刻の空（表）に置き換わる。
     * 自然な時間の流れ（夕焼けなど）は、空マップが少しずつ古くなることで追従する。
     * ついでに、1 フレームでの空の様子の変化量（skyStateDelta）も求める。
     */
    private static boolean skyChanged() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        Vec3 color = level.getSkyColor(cameraPos, partialTick);
        SKY_STATE_NOW[0] = (float) color.x;
        SKY_STATE_NOW[1] = (float) color.y;
        SKY_STATE_NOW[2] = (float) color.z;
        SKY_STATE_NOW[3] = level.getStarBrightness(partialTick);
        SKY_STATE_NOW[4] = level.getRainLevel(partialTick);
        SKY_STATE_NOW[5] = level.getThunderLevel(partialTick);

        float time = level.getTimeOfDay(partialTick);
        boolean jumped = false;
        if (prevTimeOfDay >= 0.0F) {
            float d = Math.abs(time - prevTimeOfDay);
            d = Math.min(d, 1.0F - d);   // 日付をまたいでも正しく
            jumped = d > TIME_JUMP;
        }
        prevTimeOfDay = time;

        float delta = 0.0F;
        if (haveSkyStatePrev && !jumped) {
            for (int i = 0; i < SKY_STATE_NOW.length; i++) {
                delta = Math.max(delta, Math.abs(SKY_STATE_NOW[i] - SKY_STATE_PREV[i]));
            }
        }
        skyStateDelta = delta;
        System.arraycopy(SKY_STATE_NOW, 0, SKY_STATE_PREV, 0, SKY_STATE_NOW.length);
        haveSkyStatePrev = true;

        if (!haveSkyState || jumped) {
            System.arraycopy(SKY_STATE_NOW, 0, SKY_STATE, 0, SKY_STATE.length);
            boolean changed = haveSkyState;
            haveSkyState = true;
            if (changed) MirrorDiagnostics.skyRefreshes++;
            return changed;
        }
        return false;
    }

    /** 3b. 時刻ごとの空の色の表を更新する（画面に写っている空から、今の時刻のマスを覚える） */
    private static void updateTable(Matrix4f projView) {
        ShaderInstance shader = skyTableShader;
        ClientLevel level = Minecraft.getInstance().level;
        if (shader == null || level == null) return;
        int write = 1 - tableRead;
        float mode = tableNeedsInit ? 0.0F : 1.0F;
        tableNeedsInit = false;
        int bin = Mth.floor(level.getTimeOfDay(partialTick) * TABLE_TIME_BINS) % TABLE_TIME_BINS;

        shader.safeGetUniform("ProjView").set(projView);
        shader.safeGetUniform("ScreenTexel").set(1.0F / sceneCopy.width, 1.0F / sceneCopy.height);
        shader.safeGetUniform("TableParams").set((float) bin, 0.0F, mode, 0.08F);

        TABLE[write].bindWrite(true);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());
        RenderSystem.setShaderTexture(1, TABLE[tableRead].getColorTextureId());
        RenderSystem.setShaderTexture(2, mask.getColorTextureId());
        drawFullscreen();
        tableRead = write;
    }

    /** 4. 水面の画素を描き直す（水面以外は何もしない） */
    private static void drawComposite(RenderTarget main, Matrix4f projView, Matrix4f invProjView, Vector3f eye, float planeY) {
        main.bindWrite(true);
        ShaderInstance shader = compositeShader;
        shader.safeGetUniform("ProjView").set(projView);
        shader.safeGetUniform("InvProjView").set(invProjView);
        shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z);
        shader.safeGetUniform("PlaneY").set(planeY);
        shader.safeGetUniform("MirrorParams").set(
                ClientConfig.MIRROR_SHADER_REFLECTIVITY.get().floatValue(),
                ClientConfig.MIRROR_GRAZING_REFLECTIVITY.get().floatValue(),
                ClientConfig.MIRROR_WOBBLE.get().floatValue(),
                0.0F);
        MirrorRenderer.setRippleUniforms(shader, cameraPos, partialTick);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            float time = level.getTimeOfDay(partialTick);
            shader.safeGetUniform("SkyState").set(time * TABLE_TIME_BINS, time,
                    level.getStarBrightness(partialTick) * (1.0F - level.getRainLevel(partialTick)), 0.0F);
        }

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());
        RenderSystem.setShaderTexture(1, SKY[skyRead].getColorTextureId());
        RenderSystem.setShaderTexture(2, mask.getColorTextureId());
        RenderSystem.setShaderTexture(3, flag.getColorTextureId());
        RenderSystem.setShaderTexture(4, TABLE[tableRead].getColorTextureId());
        drawFullscreen();
    }

    private static void drawFullscreen() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, 1.0, 0.0).endVertex();
        bb.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    // ------------------------------------------------------------------ helpers

    private static void ensureTargets(int w, int h) {
        if (mask == null) {
            mask = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            flag = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            sceneCopy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            for (int i = 0; i < 2; i++) {
                SKY[i] = new TextureTarget(SKY_SIZE, SKY_SIZE, false, Minecraft.ON_OSX);
                SKY[i].setFilterMode(GL11.GL_LINEAR);
                TABLE[i] = new TextureTarget(TABLE_TIME_BINS, TABLE_ELEV_BINS, false, Minecraft.ON_OSX);
                TABLE[i].setFilterMode(GL11.GL_NEAREST);
            }
            skyNeedsInit = true;
            tableNeedsInit = true;
        } else if (mask.width != w || mask.height != h) {
            mask.resize(w, h, Minecraft.ON_OSX);
            flag.resize(w, h, Minecraft.ON_OSX);
            sceneCopy.resize(w, h, Minecraft.ON_OSX);
        }
        sceneCopy.setFilterMode(GL11.GL_LINEAR);
        mask.setFilterMode(GL11.GL_NEAREST);
        flag.setFilterMode(GL11.GL_NEAREST);
    }
}
