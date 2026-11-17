package com.bingo.uyuni.client;

import com.bingo.uyuni.ModRegistry;
import com.bingo.uyuni.UyuniMod;
import com.bingo.uyuni.block.MirrorSaltBlock;
import com.bingo.uyuni.fluid.ModFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 歩くたびに足元へ波紋を出すクライアント側の処理。
 *
 *  - 足が水面に着くタイミングは、脚の振りアニメーション(walkAnimation)から取る。
 *    なので見た目の足運びとぴったり合い、左右の足それぞれの位置に1つずつ波紋が出る。
 *  - ジャンプの着地は両足同時なので、左右に2つ。
 *  - 水鏡が有効な時は「反射像を揺らす波」として、
 *    影MOD使用中などは控えめなリングのパーティクルとして表示する。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RippleHandler {
    private RippleHandler() {}

    private static final double ABOVE_TOLERANCE = 0.08;
    private static final double MAX_SHALLOW_DEPTH = 1.0;
    private static final double SURFACE_OFFSET = 0.012;
    /** HumanoidModel の脚の振り: cos(position * 0.6662) */
    private static final float LIMB_FREQ = 0.6662F;

    private static final Map<Integer, Tracker> TRACKERS = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long clock;

    private static final class Tracker {
        int phase = Integer.MIN_VALUE;
        boolean wasOnGround = true;
        int airTicks;
        double peakY;
        long lastSeen;

        Tracker(Entity e) {
            peakY = e.getY();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            TRACKERS.clear();
            RippleField.clear();
            lastLevel = null;
            return;
        }
        if (mc.isPaused()) return;
        if (level != lastLevel) {
            TRACKERS.clear();
            RippleField.clear();
            lastLevel = level;
        }
        RippleField.tick();
        if (!ClientConfig.RIPPLES_ENABLED.get()) return;

        clock++;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = ClientConfig.RIPPLE_RANGE.get();
        double rangeSq = range * range;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity.isSpectator()) continue;
            if (entity.distanceToSqr(cam) > rangeSq) continue;
            Tracker t = TRACKERS.computeIfAbsent(entity.getId(), id -> new Tracker(entity));
            t.lastSeen = clock;
            update(level, living, t);
        }
        TRACKERS.values().removeIf(t -> clock - t.lastSeen > 20);
    }

    private static void update(ClientLevel level, LivingEntity e, Tracker t) {
        boolean onGround = e.onGround();
        if (!onGround) {
            t.airTicks++;
            t.peakY = Math.max(t.peakY, e.getY());
        }

        double surface = findSurface(level, e);
        boolean onWater = !Double.isNaN(surface);
        float size = Mth.clamp(e.getBbWidth() / 0.6F, 0.35F, 2.5F);

        // 脚の振りが前後の端に来た瞬間 = 片足が水面に着いた瞬間
        int phase = Mth.floor(e.walkAnimation.position() * LIMB_FREQ / Mth.PI);
        boolean stepped = t.phase != Integer.MIN_VALUE && phase != t.phase;
        boolean rightFoot = (phase & 1) == 0;
        t.phase = phase;

        if (onWater && onGround && !t.wasOnGround && t.airTicks >= 3) {
            // ---- 着地：両足ぶん、左右に2つ ----
            double drop = Mth.clamp(t.peakY - e.getY(), 0.0, 6.0);
            float strength = (float) Mth.clamp(0.7 + drop * 0.12, 0.7, 1.3);
            footRipple(level, e, surface, true, strength, size, 0.0);
            footRipple(level, e, surface, false, strength, size, 0.0);
            if (ClientConfig.SPLASH_DROPLETS.get()) {
                splash(level, e.getX(), surface, e.getZ(), 4 + (int) drop, e.getBbWidth());
            }
        } else if (onWater && onGround && stepped && e.walkAnimation.speed() > 0.08F) {
            // ---- 歩行：着いた方の足の位置に1つ ----
            float strength = e.isShiftKeyDown() ? 0.3F : (e.isSprinting() ? 0.75F : 0.55F);
            footRipple(level, e, surface, rightFoot, strength, size, 0.12);
        }

        if (onGround) {
            t.airTicks = 0;
            t.peakY = e.getY();
        }
        t.wasOnGround = onGround;
    }

    /** 体の向きから左右の足の位置を出して、そこに波紋を置く */
    private static void footRipple(ClientLevel level, LivingEntity e, double surface,
                                   boolean right, float strength, float size, double forward) {
        double yaw = Math.toRadians(e.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // 前
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // 右
        double lateral = 0.12 * size * (right ? 1.0 : -1.0);
        double x = e.getX() + rx * lateral + fx * forward * size;
        double z = e.getZ() + rz * lateral + fz * forward * size;

        float scale = ClientConfig.RIPPLE_SCALE.get().floatValue();
        float s = strength * scale * Mth.sqrt(size);

        if (MirrorRenderer.isActive()) {
            // 水鏡モード：反射像を揺らす（リングもシェーダー側で描く）
            RippleField.add(x, z, s);
        } else {
            // 影MODモードなど：控えめなリング1つ
            float radius = (0.55F + strength * 0.45F) * size * scale;
            float alpha = Mth.clamp(0.22F + strength * 0.2F, 0.0F, 0.45F);
            level.addParticle(ModRegistry.RIPPLE.get(), x, surface + SURFACE_OFFSET, z, radius, alpha, 0);
        }
    }

    /** 足元の水面の高さ。対象外なら NaN */
    private static double findSurface(ClientLevel level, Entity e) {
        BlockPos pos = BlockPos.containing(e.getX(), e.getY() + 0.05, e.getZ());
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!isRippleFluid(fluid)) return Double.NaN;

        boolean mirror = state.getBlock() instanceof MirrorSaltBlock;
        if (!mirror && !ClientConfig.RIPPLES_IN_ALL_SHALLOW_WATER.get()) return Double.NaN;
        if (isRippleFluid(level.getFluidState(pos.above()))) return Double.NaN;

        double surface = pos.getY() + fluid.getHeight(level, pos);
        double depth = surface - e.getY();
        if (depth < -ABOVE_TOLERANCE || depth > MAX_SHALLOW_DEPTH) return Double.NaN;
        return surface;
    }

    /** 波紋が立つ液体: 鏡の塩水と、バニラの水 */
    private static boolean isRippleFluid(FluidState fluid) {
        return fluid.getFluidType() == ModFluids.MIRROR_BRINE_TYPE.get() || fluid.is(FluidTags.WATER);
    }

    private static void splash(ClientLevel level, double x, double surface, double z, int count, float spread) {
        RandomSource rand = level.random;
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5) * spread;
            double oz = (rand.nextDouble() - 0.5) * spread;
            level.addParticle(ParticleTypes.SPLASH, x + ox, surface + 0.05, z + oz,
                    ox * 0.4, 0.08 + rand.nextDouble() * 0.05, oz * 0.4);
        }
    }
}
