package org.example.roleplay.rolecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.util.UIUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MedkitItem extends Item {

    public static final int SELF_USE_TICKS = 60; // 3 sek
    public static final int COOLDOWN_TICKS = 6 * 20; // 6 sek cooldown po użyciu (możesz zmienić)

    public MedkitItem(Properties props) {
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
            if (!isMedic(player)) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.medic.not_medic"));
                return InteractionResultHolder.fail(stack);
            }
            if (!hasUses(stack)) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.medic.no_uses"));
                return InteractionResultHolder.fail(stack);
            }
            if (player.getHealth() >= player.getMaxHealth()) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.medic.full_hp"));
                return InteractionResultHolder.fail(stack);
            }
            if (player.getCooldowns().isOnCooldown(this)) {
                UIUtil.actionBar(player, Component.translatable("rolecraft.medic.cooldown"));
                return InteractionResultHolder.fail(stack);
            }

            player.startUsingItem(hand);
            UIUtil.actionBar(player, Component.translatable("rolecraft.medic.medkit.self.start"));
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

        if (!isMedic(player)) {
            UIUtil.actionBar(player, Component.translatable("rolecraft.medic.not_medic"));
            return stack;
        }
        if (!hasUses(stack)) {
            UIUtil.actionBar(player, Component.translatable("rolecraft.medic.no_uses"));
            return stack;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            UIUtil.actionBar(player, Component.translatable("rolecraft.medic.full_hp"));
            return stack;
        }

        // heal
        player.heal(6.0F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));

        // zużycie (durability)
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));

        // cooldown
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        UIUtil.actionBar(player, Component.translatable("rolecraft.medic.medkit.self.done"));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.title").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.line1").withStyle(ChatFormatting.GRAY));

        int usesLeft = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.uses", usesLeft, stack.getMaxDamage())
                .withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.empty());

        // Zamiast sprawdzać SHIFT (client-only), pokazujemy po prostu opis zawsze:
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.shift1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.shift2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rolecraft.tooltip.medkit.shift3").withStyle(ChatFormatting.GRAY));
    }

    public static boolean isMedic(Player p) {
        AtomicBoolean ok = new AtomicBoolean(false);
        PlayerRoleProvider.get(p).ifPresent((IPlayerRoles data) -> ok.set(data.getJob() == JobType.MEDIC));
        return ok.get();
    }

    public static boolean isHoldingMedkit(ServerPlayer p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        return !s.isEmpty() && s.getItem() instanceof MedkitItem;
    }

    public static boolean hasUses(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }
}
