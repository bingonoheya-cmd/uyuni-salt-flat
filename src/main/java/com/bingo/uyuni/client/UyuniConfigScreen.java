package com.bingo.uyuni.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * ゲーム内の設定画面（「Mod」→ Uyuni Salt Flat →「設定」）。
 * 左: Modの言語・波紋・水鏡のオン/オフ   右: アイテムの名前の変更（空欄で元の名前）
 * 「完了」で config/uyuni-client.toml に保存され、すぐ反映される。
 */
public class UyuniConfigScreen extends Screen {
    private static final int W = 150;
    private static final int ROW = 24;

    private final Screen parent;
    private String language;
    private boolean ripples;
    private boolean mirror;
    private boolean withShaders;
    private boolean pauseButton;
    private final List<NameField> names = new ArrayList<>();

    private record NameField(ForgeConfigSpec.ConfigValue<String> value, String key, EditBox box) {}

    public UyuniConfigScreen(Screen parent) {
        super(Component.translatable("uyuni.config.title"));
        this.parent = parent;
        this.language = ClientConfig.LANGUAGE.get();
        this.ripples = ClientConfig.RIPPLES_ENABLED.get();
        this.mirror = ClientConfig.MIRROR_ENABLED.get();
        this.withShaders = ClientConfig.MIRROR_WITH_SHADERS.get();
        this.pauseButton = ClientConfig.PAUSE_BUTTON.get();
    }

    @Override
    protected void init() {
        int left = width / 2 - W - 5;
        int right = width / 2 + 5;
        int top = 44;

        // ---- 左: 基本の設定
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("uyuni.config.language." + v))
                .withValues("auto", "ja", "en")
                .withInitialValue(language)
                .withTooltip(v -> Tooltip.create(Component.translatable("uyuni.config.language.tooltip")))
                .create(left, top, W, 20, Component.translatable("uyuni.config.language"), (b, v) -> language = v));
        addRenderableWidget(CycleButton.onOffBuilder(ripples)
                .create(left, top + ROW, W, 20, Component.translatable("uyuni.config.ripples"), (b, v) -> ripples = v));
        addRenderableWidget(CycleButton.onOffBuilder(mirror)
                .create(left, top + ROW * 2, W, 20, Component.translatable("uyuni.config.mirror"), (b, v) -> mirror = v));
        addRenderableWidget(CycleButton.onOffBuilder(withShaders)
                .withTooltip(v -> Tooltip.create(Component.translatable("uyuni.config.with_shaders.tooltip")))
                .create(left, top + ROW * 3, W, 20, Component.translatable("uyuni.config.with_shaders"),
                        (b, v) -> withShaders = v));
        addRenderableWidget(CycleButton.onOffBuilder(pauseButton)
                .withTooltip(v -> Tooltip.create(Component.translatable("uyuni.config.pause_button.tooltip")))
                .create(left, top + ROW * 4, W, 20, Component.translatable("uyuni.config.pause_button"),
                        (b, v) -> pauseButton = v));

        // ---- 右: 名前の変更（空欄で元の名前。薄い文字が元の名前）
        names.clear();
        addName(ClientConfig.NAME_TAB, "itemGroup.uyuni", right, top);
        addName(ClientConfig.NAME_SALT_MIRROR, "item.uyuni.salt_mirror", right, top + ROW);
        addName(ClientConfig.NAME_MIRROR_SALT, "block.uyuni.mirror_salt", right, top + ROW * 2);
        addName(ClientConfig.NAME_SALT_BLOCK, "block.uyuni.salt_block", right, top + ROW * 3);
        addName(ClientConfig.NAME_MIRROR_BRINE, "block.uyuni.mirror_brine", right, top + ROW * 4);
        addName(ClientConfig.NAME_MIRROR_BRINE_BUCKET, "item.uyuni.mirror_brine_bucket", right, top + ROW * 5);

        // ---- 下: 完了 / キャンセル
        int bottom = height - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(width / 2 - W - 5, bottom, W, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 5, bottom, W, 20).build());
    }

    private void addName(ForgeConfigSpec.ConfigValue<String> value, String key, int x, int y) {
        String original = LanguageOverride.originalText(key);
        EditBox box = new EditBox(font, x, y, W, 20, Component.literal(original));
        box.setMaxLength(64);
        box.setValue(value.get());
        box.setHint(Component.literal(original));
        box.setTooltip(Tooltip.create(Component.translatable("uyuni.config.name.tooltip", original)));
        addRenderableWidget(box);
        names.add(new NameField(value, key, box));
    }

    private void saveAndClose() {
        ClientConfig.LANGUAGE.set(language);
        ClientConfig.RIPPLES_ENABLED.set(ripples);
        ClientConfig.MIRROR_ENABLED.set(mirror);
        ClientConfig.MIRROR_WITH_SHADERS.set(withShaders);
        ClientConfig.PAUSE_BUTTON.set(pauseButton);
        for (NameField field : names) {
            field.value().set(field.box().getValue().trim());
        }
        ClientConfig.SPEC.save();
        LanguageOverride.markDirty();
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        g.drawString(font, Component.translatable("uyuni.config.section.general"), width / 2 - W - 5, 32, 0xA0E8F0);
        g.drawString(font, Component.translatable("uyuni.config.section.names"), width / 2 + 5, 32, 0xA0E8F0);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
