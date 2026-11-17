package com.bingo.uyuni.item;

import com.bingo.uyuni.UyuniMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「塩の手鏡」。右クリックで塩原ディメンションへ行き来する。
 * 行く前にいた場所を覚えておき、塩原で使うとそこへ帰る。
 */
public class SaltMirrorItem extends Item {
    /** Forge が死亡時にもコピーしてくれるサブタグ名 */
    private static final String PERSISTED = "PlayerPersisted";
    private static final String RETURN_TAG = "uyuni_return";
    /** 塩原の地表（鏡の水面）の少し上 */
    private static final double ARRIVAL_Y = 65.0;

    public SaltMirrorItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            MinecraftServer server = sp.getServer();
            if (server == null) return InteractionResultHolder.fail(stack);

            CompoundTag root = sp.getPersistentData();
            CompoundTag persisted = root.getCompound(PERSISTED);

            if (level.dimension() == UyuniMod.SALT_FLAT) {
                // ---- 帰る ----
                ServerLevel target = null;
                double x = 0, y = 0, z = 0;
                if (persisted.contains(RETURN_TAG, Tag.TAG_COMPOUND)) {
                    CompoundTag r = persisted.getCompound(RETURN_TAG);
                    ResourceLocation id = ResourceLocation.tryParse(r.getString("dim"));
                    if (id != null) {
                        target = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                        x = r.getDouble("x");
                        y = r.getDouble("y");
                        z = r.getDouble("z");
                    }
                }
                if (target == null) {
                    target = server.overworld();
                    BlockPos spawn = target.getSharedSpawnPos();
                    x = spawn.getX() + 0.5;
                    z = spawn.getZ() + 0.5;
                    y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
                }
                persisted.remove(RETURN_TAG);
                root.put(PERSISTED, persisted);
                travel(sp, target, x, y, z);
            } else {
                // ---- 行く ----
                ServerLevel target = server.getLevel(UyuniMod.SALT_FLAT);
                if (target == null) {
                    sp.displayClientMessage(Component.translatable("message.uyuni.no_dimension")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
                CompoundTag r = new CompoundTag();
                r.putString("dim", level.dimension().location().toString());
                r.putDouble("x", sp.getX());
                r.putDouble("y", sp.getY());
                r.putDouble("z", sp.getZ());
                persisted.put(RETURN_TAG, r);
                root.put(PERSISTED, persisted);
                travel(sp, target, sp.getX(), ARRIVAL_Y, sp.getZ());
            }
            sp.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void travel(ServerPlayer sp, ServerLevel target, double x, double y, double z) {
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        sp.stopRiding();
        sp.teleportTo(target, x, y, z, sp.getYRot(), sp.getXRot());
        target.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.uyuni.salt_mirror.tooltip").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
