package com.bingo.uyuni.client;

import com.bingo.uyuni.UyuniMod;

/**
 * 水鏡が「いまどう動いているか」の記録。/uyuni debug で表示する。
 * 不具合の報告をもらった時に、どこで止まっているかを突き止めるためのもの。
 */
public final class MirrorDiagnostics {
    private MirrorDiagnostics() {}

    /** 影MODなしの水鏡：反射を描いたフレーム数 / 水面を描いたフレーム数 */
    static long vanillaReflectionFrames;
    static long vanillaSurfaceFrames;

    /** 影MOD併用：鏡像を影パックに渡したフレーム数 / 合成まで終わったフレーム数 */
    static long shaderDecalFrames;
    static long shaderCompositeFrames;
    /** 最後のフレームで鏡像として渡した四角形の数 */
    static int lastDecalQuads;
    /** 空マップが「止まっている時の空」を覚えたフレーム数（カメラを回している間は増えない） */
    static long skyStillFrames;
    /** 時刻・天気が変わって、空マップを塗り替えた回数 */
    static long skyRefreshes;
    /** 合成を飛ばした最後の理由 */
    static String lastSkipReason = "-";

    /** 止まった機能と、その理由 */
    static String vanillaFailure;
    static String shaderReflectionFailure;
    static String shaderCompositeFailure;

    static void fail(String system, Throwable t) {
        String message = t.getClass().getSimpleName() + ": " + t.getMessage();
        switch (system) {
            case "vanilla" -> vanillaFailure = message;
            case "reflection" -> shaderReflectionFailure = message;
            default -> shaderCompositeFailure = message;
        }
        UyuniMod.LOGGER.error("Uyuni water mirror ({}) failed and was turned off. Use /uyuni retry to try again.", system, t);
    }

    static void clearFailures() {
        vanillaFailure = null;
        shaderReflectionFailure = null;
        shaderCompositeFailure = null;
    }
}
