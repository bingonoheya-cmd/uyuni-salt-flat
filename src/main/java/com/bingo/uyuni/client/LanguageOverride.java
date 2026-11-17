package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 設定の「Modの言語」と「名前の変更」を、ゲームの文章に反映する。
 *
 * ゲームは言語を切り替えたりリソースを読み直したりするたびに、新しい言語データに入れ替える。
 * なので毎ティック「いまの言語データがこのModの層をかぶっているか」を確かめて、かぶっていなければかぶせ直す。
 * 設定が変わった時も（markDirty）かぶせ直す。翻訳済みの文字は言語データごとに覚えられているので、
 * 新しい層に入れ替えれば、アイテム名などもすぐ新しい文章になる。
 */
@Mod.EventBusSubscriber(modid = UyuniMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LanguageOverride {
    private LanguageOverride() {}

    private static volatile boolean dirty = true;
    private static boolean failed;

    /** 名前の設定 → 置き換える翻訳キー */
    private static final String[][] NAME_KEYS = {
            {"tab", "itemGroup.uyuni"},
            {"saltMirror", "item.uyuni.salt_mirror"},
            {"mirrorSalt", "block.uyuni.mirror_salt"},
            {"saltBlock", "block.uyuni.salt_block"},
            {"mirrorBrine", "block.uyuni.mirror_brine", "fluid_type.uyuni.mirror_brine"},
            {"mirrorBrineBucket", "item.uyuni.mirror_brine_bucket"},
    };

    /** 名前の変更をしていない時の文章 */
    static String originalText(String key) {
        Language language = Language.getInstance();
        if (language instanceof UyuniLanguage layer) return layer.original(key);
        return language.getOrDefault(key, key);
    }

    static void markDirty() {
        dirty = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        if (!ClientConfig.SPEC.isLoaded()) return;   // 設定ファイルを読み込む前は何もしない
        Language current = Language.getInstance();
        if (current instanceof UyuniLanguage && !dirty) return;
        try {
            Language base = current instanceof UyuniLanguage layer ? layer.base : current;
            Language.inject(new UyuniLanguage(base, forcedTexts(), customNames()));
            dirty = false;
        } catch (Throwable t) {
            failed = true;   // 言語の差し替えに失敗しても、ゲームの言語のまま遊べる
            UyuniMod.LOGGER.error("Uyuni: could not apply the mod language / custom names", t);
        }
    }

    /** 設定で ja / en を選んでいれば、その言語のこのModの文章 */
    private static Map<String, String> forcedTexts() {
        String mode = ClientConfig.LANGUAGE.get();
        if ("ja".equals(mode)) return load("ja_jp");
        if ("en".equals(mode)) return load("en_us");
        return Map.of();
    }

    private static Map<String, String> customNames() {
        Map<String, String> names = new HashMap<>();
        for (String[] entry : NAME_KEYS) {
            String value = nameValue(entry[0]).trim();
            if (value.isEmpty()) continue;
            for (int i = 1; i < entry.length; i++) names.put(entry[i], value);
        }
        return names;
    }

    private static String nameValue(String id) {
        return switch (id) {
            case "tab" -> ClientConfig.NAME_TAB.get();
            case "saltMirror" -> ClientConfig.NAME_SALT_MIRROR.get();
            case "mirrorSalt" -> ClientConfig.NAME_MIRROR_SALT.get();
            case "saltBlock" -> ClientConfig.NAME_SALT_BLOCK.get();
            case "mirrorBrine" -> ClientConfig.NAME_MIRROR_BRINE.get();
            case "mirrorBrineBucket" -> ClientConfig.NAME_MIRROR_BRINE_BUCKET.get();
            default -> "";
        };
    }

    private static Map<String, String> load(String code) {
        Map<String, String> map = new HashMap<>();
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(new ResourceLocation(UyuniMod.MODID, "lang/" + code + ".json"));
        if (resource.isEmpty()) return map;
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            UyuniMod.LOGGER.warn("Uyuni: could not read lang/{}.json", code, e);
        }
        return map;
    }
}
