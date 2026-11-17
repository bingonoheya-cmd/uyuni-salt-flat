package com.bingo.uyuni.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** クライアントでだけ行う準備（UyuniMod から、クライアントの時だけ呼ばれる） */
public final class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modBus) {
        // 「Mod」画面の「設定」ボタンで開く画面
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new UyuniConfigScreen(parent)));
        // 設定ファイルを直接書き換えた時も、言語と名前をすぐ反映する
        modBus.addListener((ModConfigEvent.Loading e) -> LanguageOverride.markDirty());
        modBus.addListener((ModConfigEvent.Reloading e) -> LanguageOverride.markDirty());
    }
}
