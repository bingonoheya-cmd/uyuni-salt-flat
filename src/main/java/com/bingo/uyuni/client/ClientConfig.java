package com.bingo.uyuni.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * config/uyuni-client.toml
 * Mod を入れて最初に起動した時に、自動で作られる。
 * ゲーム内でも「Mod」画面 → Uyuni Salt Flat →「設定」から変えられる（UyuniConfigScreen）。
 */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ForgeConfigSpec SPEC;

    // [general]
    public static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static final ForgeConfigSpec.BooleanValue PAUSE_BUTTON;

    // [names]
    public static final ForgeConfigSpec.ConfigValue<String> NAME_TAB;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_SALT_MIRROR;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_MIRROR_SALT;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_SALT_BLOCK;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_MIRROR_BRINE;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_MIRROR_BRINE_BUCKET;

    // [ripples]
    public static final ForgeConfigSpec.BooleanValue RIPPLES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RIPPLES_IN_ALL_SHALLOW_WATER;
    public static final ForgeConfigSpec.DoubleValue RIPPLE_SCALE;
    public static final ForgeConfigSpec.IntValue RIPPLE_RANGE;
    public static final ForgeConfigSpec.BooleanValue SPLASH_DROPLETS;

    // [mirror]
    public static final ForgeConfigSpec.BooleanValue MIRROR_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MIRROR_WITH_SHADERS;
    public static final ForgeConfigSpec.DoubleValue MIRROR_REFLECTIVITY;
    public static final ForgeConfigSpec.DoubleValue MIRROR_GRAZING_REFLECTIVITY;
    public static final ForgeConfigSpec.DoubleValue MIRROR_SHADER_REFLECTIVITY;
    public static final ForgeConfigSpec.DoubleValue MIRROR_WOBBLE;
    public static final ForgeConfigSpec.IntValue MIRROR_BLOCK_RADIUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("ウユニ塩湖Mod の設定", "ゲーム内の設定画面からも変えられます（Esc画面のボタン / /uyuni config）").push("general");
        LANGUAGE = b.comment("Modの言語  auto=ゲームと同じ / ja=日本語 / en=英語")
                .defineInList("language", "auto", List.of("auto", "ja", "en"));
        PAUSE_BUTTON = b.comment("ポーズ画面に設定ボタンを出す")
                .define("pauseButton", true);
        b.pop();

        b.comment("名前の変更（空欄で元の名前）").push("names");
        NAME_TAB = b.comment("クリエイティブのタブ").define("tab", "");
        NAME_SALT_MIRROR = b.comment("塩の手鏡").define("saltMirror", "");
        NAME_MIRROR_SALT = b.comment("鏡張りの塩原").define("mirrorSalt", "");
        NAME_SALT_BLOCK = b.comment("塩のブロック").define("saltBlock", "");
        NAME_MIRROR_BRINE = b.comment("鏡の塩水").define("mirrorBrine", "");
        NAME_MIRROR_BRINE_BUCKET = b.comment("鏡の塩水入りバケツ").define("mirrorBrineBucket", "");
        b.pop();

        b.push("ripples");
        RIPPLES_ENABLED = b.comment("波紋を出す")
                .define("enabled", true);
        RIPPLES_IN_ALL_SHALLOW_WATER = b.comment("普通の浅瀬でも波紋を出す")
                .define("inAllShallowWater", false);
        RIPPLE_SCALE = b.comment("波紋の大きさ")
                .defineInRange("scale", 1.0, 0.25, 4.0);
        RIPPLE_RANGE = b.comment("波紋が出る距離（ブロック）")
                .defineInRange("range", 96, 8, 256);
        SPLASH_DROPLETS = b.comment("着地で水しぶきを出す")
                .define("splashDroplets", false);
        b.pop();

        b.push("mirror");
        MIRROR_ENABLED = b.comment("水面を鏡にする")
                .define("enabled", true);
        MIRROR_WITH_SHADERS = b.comment("影MOD中も水鏡を使う")
                .define("withShaders", true);
        MIRROR_REFLECTIVITY = b.comment("映り込みの強さ（真下）")
                .defineInRange("reflectivity", 0.82, 0.0, 1.0);
        MIRROR_GRAZING_REFLECTIVITY = b.comment("映り込みの強さ（遠く）")
                .defineInRange("grazingReflectivity", 0.97, 0.0, 1.0);
        MIRROR_SHADER_REFLECTIVITY = b.comment("影MOD中の映り込みの強さ（真下）")
                .defineInRange("shaderReflectivity", 0.93, 0.0, 1.0);
        MIRROR_WOBBLE = b.comment("歩いた時の揺れ（0で揺れない）")
                .defineInRange("wobble", 1.0, 0.0, 3.0);
        MIRROR_BLOCK_RADIUS = b.comment("ブロックを映す範囲（0で映さない・大きいほど重い）")
                .defineInRange("blockRadius", 24, 0, 64);
        b.pop();

        SPEC = b.build();
    }
}
