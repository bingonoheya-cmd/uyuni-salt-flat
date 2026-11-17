package com.bingo.uyuni.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 水鏡を揺らすための「いま広がっている波紋」の一覧。
 * MirrorRenderer がこれをシェーダーに渡して反射像を歪ませる。
 */
public final class RippleField {
    private RippleField() {}

    /** 波紋が消えるまでの秒数（シェーダー側の LIFE と合わせる） */
    public static final float LIFE_SECONDS = 2.4F;
    private static final int MAX = 96;

    public record Ripple(double x, double z, float startTick, float strength) {}

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static long ticks;

    static void tick() {
        ticks++;
        RIPPLES.removeIf(r -> (ticks - r.startTick()) / 20.0F > LIFE_SECONDS);
    }

    static void clear() {
        RIPPLES.clear();
    }

    static void add(double x, double z, float strength) {
        if (RIPPLES.size() >= MAX) RIPPLES.remove(0);
        RIPPLES.add(new Ripple(x, z, ticks, strength));
    }

    public static float now(float partialTick) {
        return ticks + partialTick;
    }

    public static List<Ripple> all() {
        return RIPPLES;
    }
}
