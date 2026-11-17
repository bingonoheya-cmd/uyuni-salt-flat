package com.bingo.uyuni.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 水面に寝かせて描く、広がって消えるリング状の波紋。
 *
 * 影MOD対策:
 *  - 独自シェーダーや独自RenderTypeは使わず、バニラの PARTICLE_SHEET_TRANSLUCENT で描画。
 *    Oculus/Iris はこれを「半透明パーティクル」として扱い、水の描画後に重ねてくれるので
 *    反射した水面の上にきれいに乗る。
 *  - ライトはワールドの明るさを使う（夜に光って浮かない）。
 *
 * パラメータ (addParticle の速度欄を流用):
 *   xd = 最大半径, yd = 不透明度, zd = 表示開始までの遅延tick
 */
public class RippleParticle extends TextureSheetParticle {
    private final float maxRadius;
    private final float baseAlpha;
    private final int delay;

    protected RippleParticle(ClientLevel level, double x, double y, double z,
                             float maxRadius, float alpha, int delay, SpriteSet sprites) {
        super(level, x, y, z);
        this.maxRadius = Math.max(0.05F, maxRadius);
        this.baseAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
        this.delay = Math.max(0, delay);
        this.lifetime = this.delay + (int) (24 + this.maxRadius * 12);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        // 視錐台カリング用に当たり箱を波紋サイズまで広げる
        this.setSize(this.maxRadius * 2.0F, 0.05F);
        this.rCol = 0.93F;
        this.gCol = 0.97F;
        this.bCol = 1.0F;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float local = this.age + partialTicks - this.delay;
        if (local < 0) return;

        float life = Math.max(1, this.lifetime - this.delay);
        float t = Mth.clamp(local / life, 0.0F, 1.0F);

        // ease-out: 最初は速く、だんだんゆっくり広がる
        float inv = 1.0F - t;
        float ease = 1.0F - inv * inv * inv;
        float radius = this.maxRadius * (0.10F + 0.90F * ease);

        float fadeIn = Math.min(1.0F, local / 2.0F);
        float a = this.baseAlpha * (float) Math.pow(inv, 1.3) * fadeIn;
        if (a <= 0.01F) return;

        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        float u0 = this.getU0(), u1 = this.getU1();
        float v0 = this.getV0(), v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        // 上から見て反時計回り = 上向きの面（水面の上から見える向き）
        buffer.vertex(px - radius, py, pz - radius).uv(u0, v0).color(rCol, gCol, bCol, a).uv2(light).endVertex();
        buffer.vertex(px - radius, py, pz + radius).uv(u0, v1).color(rCol, gCol, bCol, a).uv2(light).endVertex();
        buffer.vertex(px + radius, py, pz + radius).uv(u1, v1).color(rCol, gCol, bCol, a).uv2(light).endVertex();
        buffer.vertex(px + radius, py, pz - radius).uv(u1, v0).color(rCol, gCol, bCol, a).uv2(light).endVertex();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new RippleParticle(level, x, y, z, (float) xd, (float) yd, (int) zd, this.sprites);
        }
    }
}
