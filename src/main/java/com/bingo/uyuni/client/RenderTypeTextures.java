package com.bingo.uyuni.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RenderType が使っているテクスチャを調べる（水鏡に映った Mob の「形」を、透明部分を抜いて描くため）。
 * 開発環境と製品版でフィールド名が違うので、名前ではなく「型」で探す。
 * 分からなかった時は empty（その場合は四角形のまま扱う）。
 */
final class RenderTypeTextures {
    private RenderTypeTextures() {}

    private static final Map<RenderType, Optional<ResourceLocation>> CACHE = new IdentityHashMap<>();

    static ResourceLocation of(RenderType type) {
        return CACHE.computeIfAbsent(type, RenderTypeTextures::find).orElse(null);
    }

    private static Optional<ResourceLocation> find(RenderType type) {
        try {
            Object state = findFieldValue(type, "CompositeState");
            if (state == null) return Optional.empty();
            Object textureShard = findFieldValue(state, "TextureStateShard");
            if (textureShard == null) return Optional.empty();
            for (Class<?> c = textureShard.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == Optional.class) {
                        m.setAccessible(true);
                        Object result = m.invoke(textureShard);
                        if (result instanceof Optional<?> opt && opt.isPresent()
                                && opt.get() instanceof ResourceLocation rl) {
                            return Optional.of(rl);
                        }
                        return Optional.empty();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static Object findFieldValue(Object owner, String typeNameSuffix) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType().getSimpleName().endsWith(typeNameSuffix)) {
                    f.setAccessible(true);
                    return f.get(owner);
                }
            }
        }
        return null;
    }
}
