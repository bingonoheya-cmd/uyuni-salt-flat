package com.bingo.uyuni.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/**
 * ゲームの言語の上に、このModの文章だけを差し替える層をかぶせる。
 *  1. 設定で付けた名前（空欄でなければ）
 *  2. 設定で選んだModの言語（ja / en）の文章
 *  3. それ以外はゲームの言語のまま
 * Mod 以外の文章（バニラや他のMod）には一切触れない。
 */
final class UyuniLanguage extends Language {
    final Language base;
    private final Map<String, String> forced;
    private final Map<String, String> custom;
    private Map<String, String> merged;

    UyuniLanguage(Language base, Map<String, String> forced, Map<String, String> custom) {
        this.base = base;
        this.forced = forced;
        this.custom = custom;
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        String name = custom.get(key);
        if (name != null) return name;
        String text = forced.get(key);
        if (text != null) return text;
        return base.getOrDefault(key, defaultValue);
    }

    /** 名前の変更をしていない時の文章（設定画面に元の名前として出す） */
    String original(String key) {
        String text = forced.get(key);
        return text != null ? text : base.getOrDefault(key, key);
    }

    @Override
    public boolean has(String key) {
        return custom.containsKey(key) || forced.containsKey(key) || base.has(key);
    }

    /** Forge が追加した「全部の文章」も、差し替え後の内容で返す */
    @Override
    public Map<String, String> getLanguageData() {
        if (merged == null) {
            Map<String, String> map = new HashMap<>(base.getLanguageData());
            map.putAll(forced);
            map.putAll(custom);
            merged = map;
        }
        return merged;
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return base.isDefaultRightToLeft();
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return base.getVisualOrder(text);
    }
}
