package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * 影MOD使用中、水鏡に映るプレイヤー・Mob・ブロックを「影パックに描かせる」ための仕組み。
 *
 * 影パックの光・影・霧・色調は、世界を描いている最中に普通の方法で描いたものにしか掛からない。
 * そこで、エンティティの描画直後(AFTER_ENTITIES)に、上下反転した Mob を世界の一部として描く。
 *
 * 反転した Mob は本来なら水面の下（塩の地面の中）にあって隠れてしまうので、各頂点を
 * 「目から見た同じ視線の上」で水面のすぐ上の薄い層まで引き寄せる。
 *   - 同じ視線の上を動かすだけなので、画面上の見た目（形・位置）は本物の鏡像とまったく同じ
 *   - 奥行きだけが水面のすぐ上になるので、地面にも影パックの水にも隠れず、くっきり映る
 *   - 体のパーツ同士の前後関係は、引き寄せる量に差をつけて保つ
 *
 * 注意点（Oculus のソースで確認済み）:
 *   - 影パック使用中、Oculus は歩いた時の画面の揺れ(view bobbing)をカメラの行列の方へ移す。
 *     なので「目」はカメラ相対の原点ではなく、少しずれた所にある。目の位置を行列から求めて、
 *     そこからの視線に沿って引き寄せる（原点から引き寄せると、歩行中に映り込みが最大 約27px ずれていた）。
 *   - Oculus は世界の描画中、Mob の頂点を自前のバッファにためておき、半透明の前に並べ替えてまとめて描く。
 *     ここで勝手に描き出す(endBatch)と、その順番を壊してしまうので、ためるだけにして Oculus に任せる。
 *
 * 描いた四角形はそのまま {@link #DECALS} に記録し、ShaderMirror が「鏡像がある画素」の判定に使う
 * （描いたものと判定がぴったり一致する）。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShaderReflection {
    private ShaderReflection() {}

    /**
     * 鏡像は水面の「すぐ上」の薄い層に置く（水面の下に置くと、影パックが自分の水＝反射や色を
     * 上から重ねてしまい、映った Mob が白っぽく透けて見えていた）。
     * 層の上端・下端は遠いほど少し高くする（遠くでも奥行きの精度で水面と区別できるように）。
     */
    private static final float LAYER_TOP = 0.03F, LAYER_TOP_PER_BLOCK = 0.0003F;
    private static final float LAYER_FLOOR = 0.004F, LAYER_FLOOR_PER_BLOCK = 0.0002F;
    /**
     * パーツ同士の前後関係を保つため、本来の奥行き x を x/(x+L) で層の中に押し込む（L ブロック）。
     * 以前は「奥行き×0.008」を層の底で打ち切っていたので、高く飛ぶ Mob（空クジラなど）の鏡像は
     * ほとんどの点が層の底に貼り付いて同じ奥行きになり、ヒレと胴体がちらついて崩れていた。
     * x/(x+L) なら、どれだけ深くても打ち切らずに順番が保たれる（計算で確認: 重なったパーツの前後の逆転 0%）。
     */
    private static final float DEPTH_SQUEEZE = 8.0F;

    static boolean failed;

    /** このフレームに描いた鏡像の四角形（画面座標の手前、カメラ空間の位置＋テクスチャ座標） */
    static final DecalQuads DECALS = new DecalQuads();

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (failed || !ShaderCompat.shaderPackInUse() || ShaderCompat.isRenderingShadowPass()) return;
        if (!MirrorRenderer.isActive()) return;

        Vec3 cam = event.getCamera().getPosition();
        if (cam.y <= MirrorRenderer.MIRROR_Y) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        try {
            Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
            // 目の位置（カメラ相対）。影パック使用中は画面の揺れのぶん原点からずれている
            Vector3f eye = new Matrix4f(view).invert().transformPosition(new Vector3f());
            float waterY = (float) (MirrorRenderer.MIRROR_Y - cam.y);
            if (eye.y <= waterY + 0.1F) return;

            // 水面で上下反転するだけの姿勢（カメラの回転は FlattenSource 側で掛ける）
            PoseStack mirror = new PoseStack();
            mirror.last().pose().identity()
                    .translate(0.0F, (float) (2.0 * (MirrorRenderer.MIRROR_Y - cam.y)), 0.0F)
                    .scale(1.0F, -1.0F, 1.0F);
            mirror.last().normal().identity(); // 法線は本物の向きのまま（日光の当たり方を本物と同じにする）

            DECALS.begin(ShaderMirror.currentFrame());
            FlattenSource source = new FlattenSource(mc.renderBuffers().bufferSource(), view, eye, waterY);

            MirrorRenderer.renderEntities(mc, level, mirror, source, cam, event.getPartialTick(), true);
            MirrorRenderer.renderBlocks(mc, level, mirror, source, cam);
            // ここでは描き出さない(endBatch しない)。Oculus が自分の順番でまとめて描く。

            MirrorDiagnostics.shaderDecalFrames++;
            MirrorDiagnostics.lastDecalQuads = DECALS.quadCount();
        } catch (Throwable t) {
            failed = true;
            DECALS.begin(-1);
            MirrorDiagnostics.fail("reflection", t);
        }
    }

    // ------------------------------------------------------------------

    /** 鏡像の四角形の記録。テクスチャごとに、頂点 (x, y, z, u, v) を並べて持つ */
    static final class DecalQuads {
        private final Map<ResourceLocation, FloatArrayList> textured = new HashMap<>();
        private final FloatArrayList untextured = new FloatArrayList();
        private long frame = -1;

        void begin(long frame) {
            this.frame = frame;
            textured.values().forEach(FloatArrayList::clear);
            untextured.clear();
        }

        boolean isFor(long frame) {
            return this.frame == frame;
        }

        FloatArrayList listFor(ResourceLocation texture) {
            return texture == null ? untextured : textured.computeIfAbsent(texture, t -> new FloatArrayList());
        }

        Map<ResourceLocation, FloatArrayList> textured() {
            return textured;
        }

        FloatArrayList untextured() {
            return untextured;
        }

        int quadCount() {
            int floats = untextured.size();
            for (FloatArrayList list : textured.values()) floats += list.size();
            return floats / 20;
        }
    }

    /** 受け取った頂点を引き寄せてから、本来のバッファ（影パックが用意したもの）へ流す */
    private static final class FlattenSource implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final Matrix4f view;
        private final Matrix3f viewNormal;
        private final Vector3f eye;
        private final float waterY;

        FlattenSource(MultiBufferSource delegate, Matrix4f view, Vector3f eye, float waterY) {
            this.delegate = delegate;
            this.view = view;
            this.viewNormal = new Matrix3f(view);
            this.eye = eye;
            this.waterY = waterY;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            // 四角形で描くエンティティ用の形式だけ扱う（文字・光沢・線などは映さない）
            if (type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS) {
                return NullConsumer.INSTANCE;
            }
            return new FlattenConsumer(delegate.getBuffer(type), this,
                    DECALS.listFor(RenderTypeTextures.of(type)));
        }
    }

    /**
     * 頂点を 4 つためて、
     *  ・位置を「目からの視線」に沿って水面のすぐ下へ引き寄せ、カメラの行列を掛ける
     *  ・法線にカメラの回転を掛ける
     *  ・反転で裏返った四角形の頂点順を逆にする（表が正しく表になるように）
     * してから流す。同じものを DECALS にも記録する。
     */
    private static final class FlattenConsumer implements VertexConsumer {
        private final VertexConsumer out;
        private final FlattenSource s;
        private final FloatArrayList record;

        private final float[][] pos = new float[4][3];
        private final int[][] color = new int[4][4];
        private final float[][] uv = new float[4][2];
        private final int[][] overlay = new int[4][2];
        private final int[][] light = new int[4][2];
        private final float[][] normal = new float[4][3];
        private int count;

        private boolean hasDefaultColor;
        private final int[] defaultColor = new int[4];

        FlattenConsumer(VertexConsumer out, FlattenSource s, FloatArrayList record) {
            this.out = out;
            this.s = s;
            this.record = record;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            float[] p = pos[count];
            p[0] = (float) x;
            p[1] = (float) y;
            p[2] = (float) z;
            int[] c = color[count];
            if (hasDefaultColor) {
                System.arraycopy(defaultColor, 0, c, 0, 4);
            } else {
                c[0] = c[1] = c[2] = c[3] = 255;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            int[] c = color[count];
            c[0] = r; c[1] = g; c[2] = b; c[3] = a;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            uv[count][0] = u;
            uv[count][1] = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            overlay[count][0] = u;
            overlay[count][1] = v;
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            light[count][0] = u;
            light[count][1] = v;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            normal[count][0] = x;
            normal[count][1] = y;
            normal[count][2] = z;
            return this;
        }

        @Override
        public void endVertex() {
            count++;
            if (count == 4) {
                for (int i = 3; i >= 0; i--) emit(i);
                count = 0;
            }
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            hasDefaultColor = true;
            defaultColor[0] = r; defaultColor[1] = g; defaultColor[2] = b; defaultColor[3] = a;
        }

        @Override
        public void unsetDefaultColor() {
            hasDefaultColor = false;
        }

        private void emit(int i) {
            Vector3f p = flatten(pos[i][0], pos[i][1], pos[i][2]);
            s.view.transformPosition(p);
            Vector3f n = new Vector3f(normal[i][0], normal[i][1], normal[i][2]);
            s.viewNormal.transform(n);
            int[] c = color[i];
            out.vertex(p.x, p.y, p.z)
                    .color(c[0], c[1], c[2], c[3])
                    .uv(uv[i][0], uv[i][1])
                    .overlayCoords(overlay[i][0], overlay[i][1])
                    .uv2(light[i][0], light[i][1])
                    .normal(n.x, n.y, n.z)
                    .endVertex();
            record.add(p.x);
            record.add(p.y);
            record.add(p.z);
            record.add(uv[i][0]);
            record.add(uv[i][1]);
        }

        /** カメラ相対の位置を、目からの同じ視線の上で、水面のすぐ上の薄い層へ引き寄せる */
        private Vector3f flatten(float x, float y, float z) {
            Vector3f e = s.eye;
            float vx = x - e.x, vy = y - e.y, vz = z - e.z;
            float dist = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (dist < 1.0E-5F) return new Vector3f(x, y, z);
            float dx = vx / dist, dy = vy / dist, dz = vz / dist;
            if (dy > -1.0E-5F) return new Vector3f(x, y, z);
            float tWater = (s.waterY - e.y) / dy;                 // 視線が水面に届く距離
            float topY = s.waterY + LAYER_TOP + LAYER_TOP_PER_BLOCK * tWater;
            if (y >= topY) return new Vector3f(x, y, z);          // すでに十分手前
            float floorY = s.waterY + LAYER_FLOOR + LAYER_FLOOR_PER_BLOCK * tWater;
            float tTop = (topY - e.y) / dy;
            float tFloor = (floorY - e.y) / dy;
            float depth = Math.max(0.0F, dist - tTop);            // 本来どれだけ奥にあるか
            // 奥のものほど層の底へ近づくが、底には届かない（＝前後関係がずっと保たれる）
            float t = tTop + (tFloor - tTop) * (depth / (depth + DEPTH_SQUEEZE));
            return new Vector3f(e.x + dx * t, e.y + dy * t, e.z + dz * t);
        }
    }

    /** 何も描かない VertexConsumer */
    static final class NullConsumer implements VertexConsumer {
        static final NullConsumer INSTANCE = new NullConsumer();

        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
}
