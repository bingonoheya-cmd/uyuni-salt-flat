package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側だけで動くコマンド（サーバーに Mod が無くても使える）。
 *   /uyuni config     … 設定画面を開く
 *   /uyuni debug      … 水鏡がいまどう動いているかを表示
 *   /uyuni retry      … エラーで止まった機能をもう一度動かす
 *   /uyuni sky reset  … 空マップ（影MOD使用中に覚えた空）を消して覚え直す
 * 表示する文章はすべて lang ファイル（uyuni.debug.*）から取るので、ゲームの言語に合わせて切り替わる。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DebugCommands {
    private DebugCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("uyuni")
                .then(Commands.literal("debug").executes(ctx -> {
                    showStatus();
                    return 1;
                }))
                .then(Commands.literal("config").executes(ctx -> {
                    ConfigAccess.openLater();
                    return 1;
                }))
                .then(Commands.literal("retry").executes(ctx -> {
                    MirrorRenderer.failed = false;
                    ShaderReflection.failed = false;
                    ShaderMirror.failed = false;
                    MirrorDiagnostics.clearFailures();
                    ShaderMirror.requestSkyReset();
                    say(t("uyuni.debug.retry").withStyle(ChatFormatting.AQUA));
                    return 1;
                }))
                .then(Commands.literal("sky").then(Commands.literal("reset").executes(ctx -> {
                    ShaderMirror.requestSkyReset();
                    say(t("uyuni.debug.sky_reset").withStyle(ChatFormatting.AQUA));
                    return 1;
                }))));
    }

    private static void showStatus() {
        Minecraft mc = Minecraft.getInstance();
        boolean inSaltFlat = mc.level != null && mc.level.dimension() == UyuniMod.SALT_FLAT;
        boolean oculus = ShaderCompat.irisApiPresent();
        boolean shaders = ShaderCompat.shaderPackInUse();

        say(t("uyuni.debug.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        line("in_salt_flat", t(inSaltFlat ? "uyuni.debug.yes" : "uyuni.debug.no"));
        line("oculus", t(oculus ? (shaders ? "uyuni.debug.oculus.pack_on" : "uyuni.debug.oculus.pack_off")
                : "uyuni.debug.oculus.none"));
        line("config", Component.literal(ClientConfig.MIRROR_ENABLED.get() + " / " + ClientConfig.MIRROR_WITH_SHADERS.get()));
        line("mode", modeDescription(inSaltFlat, shaders));

        if (shaders) {
            line("shaders", Component.literal(loaded(ShaderMirror.depthMaskShader, "depth_mask")
                    + " " + loaded(ShaderMirror.flagShader, "flag")
                    + " " + loaded(ShaderMirror.skyShader, "sky")
                    + " " + loaded(ShaderMirror.compositeShader, "composite")));
            line("decal_frames", number(MirrorDiagnostics.shaderDecalFrames));
            line("decal_quads", number(MirrorDiagnostics.lastDecalQuads));
            line("composite_frames", number(MirrorDiagnostics.shaderCompositeFrames));
            line("sky_still_frames", number(MirrorDiagnostics.skyStillFrames));
            line("sky_refreshes", number(MirrorDiagnostics.skyRefreshes));
            line("skip_reason", Component.literal(MirrorDiagnostics.lastSkipReason));
        } else {
            line("shaders", Component.literal(loaded(MirrorRenderer.surfaceShader, "surface")));
            line("reflection_frames", number(MirrorDiagnostics.vanillaReflectionFrames));
            line("surface_frames", number(MirrorDiagnostics.vanillaSurfaceFrames));
        }
        line("ripples", number(RippleField.all().size()));

        failure("vanilla", MirrorDiagnostics.vanillaFailure);
        failure("reflection", MirrorDiagnostics.shaderReflectionFailure);
        failure("composite", MirrorDiagnostics.shaderCompositeFailure);
    }

    private static Component modeDescription(boolean inSaltFlat, boolean shaders) {
        if (!inSaltFlat) return t("uyuni.debug.mode.outside");
        if (!ClientConfig.MIRROR_ENABLED.get()) return t("uyuni.debug.mode.disabled");
        if (shaders) {
            if (!ClientConfig.MIRROR_WITH_SHADERS.get()) return t("uyuni.debug.mode.pack_only");
            if (ShaderMirror.failed || ShaderReflection.failed) return t("uyuni.debug.mode.stopped");
            return t(ShaderMirror.isAvailable() ? "uyuni.debug.mode.shaders" : "uyuni.debug.mode.not_loaded");
        }
        if (MirrorRenderer.failed) return t("uyuni.debug.mode.stopped");
        return t(MirrorRenderer.isVanillaAvailable() ? "uyuni.debug.mode.vanilla" : "uyuni.debug.mode.not_loaded");
    }

    private static String loaded(Object shader, String name) {
        return name + (shader != null ? "=OK" : "=NG");
    }

    private static Component number(long n) {
        return Component.literal(String.valueOf(n));
    }

    private static net.minecraft.network.chat.MutableComponent t(String key) {
        return Component.translatable(key);
    }

    private static void line(String labelKey, Component value) {
        say(Component.literal("  ").append(t("uyuni.debug.label." + labelKey)).append(": ")
                .withStyle(ChatFormatting.GRAY)
                .append(value.copy().withStyle(ChatFormatting.WHITE)));
    }

    private static void failure(String partKey, String message) {
        if (message == null) return;
        say(Component.literal("  ").append(Component.translatable("uyuni.debug.failed",
                t("uyuni.debug.part." + partKey), message)).withStyle(ChatFormatting.RED));
        say(Component.literal("  ").append(t("uyuni.debug.failed_hint")).withStyle(ChatFormatting.RED));
    }

    private static void say(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(component, false);
    }
}
