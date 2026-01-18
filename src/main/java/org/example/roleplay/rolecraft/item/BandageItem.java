package org.example.roleplay.rolecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.example.roleplay.rolecraft.util.UIUtil;

import javax.annotation.Nullable;
import java.util.List;

public class BandageItem extends Item {

    public static final int SELF_USE_TICKS = 40; // 2 sek
    public static final int COOLDOWN_TICKS = 3 * 20; // 3 sek cooldown (możesz zmienić)

    public BandageItem(Properties props) {
        super(props);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return SELF_USE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            if (player.getHealth() >= player.getMaxHealth()) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.medic.full_hp"));
                return InteractionResultHolder.fail(stack);
            }
            if (player.getCooldowns().isOnCooldown(this)) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.bandage.cooldown"));
                return InteractionResultHolder.fail(stack);
            }

            player.startUsingItem(hand);
            UIUtil.actionBar(player, Component.translatable("rolecraft.bandage.self.start"));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer player)) return;

        int used = SELF_USE_TICKS - remainingUseTicks;
        if (used < 0) used = 0;
        if (used % 5 != 0) return;

        int pct = (int) Math.floor((used / (double) SELF_USE_TICKS) * 100.0);
        if (pct > 100) pct = 100;

        UIUtil.actionBar(player, UIUtil.prettyProgress(
                "Healing:",
                Component.translatable("rolecraft.ui.you"),
                pct
        ));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide) return stack;
        if (!(entity instanceof ServerPlayer player)) return stack;

        if (player.getHealth() >= player.getMaxHealth()) {
            UIUtil.actionBar(player, Component.translatable("rolecraft.medic.full_hp"));
            return stack;
        }

        player.heal(4.0F);
        stack.shrink(1);

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        UIUtil.actionBar(player, Component.translatable("rolecraft.bandage.self.done"));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rolecraft.tooltip.bandage.title").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("rolecraft.tooltip.bandage.line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        // server-safe: bez Screen.hasShiftDown()
        tooltip.add(Component.translatable("rolecraft.tooltip.bandage.shift1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rolecraft.tooltip.bandage.shift2").withStyle(ChatFormatting.GRAY));
    }
}
