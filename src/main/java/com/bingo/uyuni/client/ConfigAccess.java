package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 設定画面の入口（「Mod」画面の「設定」ボタン以外）。
 *   1. ポーズ画面（Esc）の左上の「ウユニ塩湖の設定」ボタン
 *   2. コマンド /uyuni config
 *   3. キー割り当て（「設定」→「操作設定」→「キー割り当て」→ ウユニ塩湖。最初は未割り当て）
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ConfigAccess {
    private ConfigAccess() {}

    public static final KeyMapping OPEN_KEY = new KeyMapping("key.uyuni.config",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.uyuni");

    /** コマンドから開く時は、チャット画面が閉じてから開く */
    private static boolean openNextTick;

    static void openLater() {
        openNextTick = true;
    }

    static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new UyuniConfigScreen(parent));
    }

    /** ポーズ画面の左上にボタンを足す */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.PAUSE_BUTTON.get()) return;
        event.addListener(Button.builder(Component.translatable("uyuni.config.open"), b -> open(pause))
                .bounds(4, 4, 110, 20)
                .build());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (openNextTick && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            openNextTick = false;
            open(mc.screen);
            return;
        }
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null) open(null);
        }
    }

    @Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
        }
    }
}
