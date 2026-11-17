package com.bingo.uyuni.client;

import java.lang.reflect.Method;

/**
 * Oculus / Iris で影パックが有効かどうかを調べる。
 * 依存関係を増やさないようにリフレクションで Iris API を呼ぶ（無ければ false）。
 */
public final class ShaderCompat {
    private ShaderCompat() {}

    private static boolean initialized;
    private static Object api;
    private static Method isShaderPackInUse;
    private static Class<?> apiClass;

    /** Oculus / Iris が入っているか */
    public static boolean irisApiPresent() {
        shaderPackInUse(); // 初期化
        return api != null;
    }

    public static boolean shaderPackInUse() {
        if (!initialized) {
            initialized = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                apiClass = c;
                api = c.getMethod("getInstance").invoke(null);
                isShaderPackInUse = c.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (Boolean) isShaderPackInUse.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method isRenderingShadowPass;

    /** 影パックが「影」を作るための描画中か（その時は鏡像を描かない＝鏡像は影を落とさない） */
    public static boolean isRenderingShadowPass() {
        if (!shaderPackInUse()) return false;
        try {
            if (isRenderingShadowPass == null) {
                isRenderingShadowPass = apiClass.getMethod("isRenderingShadowPass");
            }
            return (Boolean) isRenderingShadowPass.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }
}
