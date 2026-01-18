package org.example.roleplay.rolecraft.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.example.roleplay.rolecraft.item.MedkitItem;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MedicEvents {

    private static final int HEAL_TICKS = 15 * 20;
    private static final double MAX_DIST = 4.0;
    private static final double MAX_MOVE = 0.35;

    private static final Map<UUID, HealSession> SESSIONS = new ConcurrentHashMap<>();

    private static class HealSession {
        final UUID healerId;
        final UUID targetId;
        final InteractionHand hand;
        final double startX, startY, startZ;
        int remainingTicks;

        HealSession(ServerPlayer healer, ServerPlayer target, InteractionHand hand) {
            this.healerId = healer.getUUID();
            this.targetId = target.getUUID();
            this.hand = hand;
            this.startX = healer.getX();
            this.startY = healer.getY();
            this.startZ = healer.getZ();
            this.remainingTicks = HEAL_TICKS;
        }
    }

    @SubscribeEvent
    public void onRightClickPlayer(PlayerInteractEvent.EntityInteractSpecific e) {
        if (!(e.getEntity() instanceof ServerPlayer healer)) return;
        Entity clicked = e.getTarget();
        if (!(clicked instanceof ServerPlayer target)) return;
        if (healer.level().isClientSide) return;

        if (!healer.isShiftKeyDown()) return;

        InteractionHand hand = e.getHand();
        if (!MedkitItem.isHoldingMedkit(healer, hand)) return;

        if (!MedkitItem.isMedic(healer)) {
            UIUtil.actionBar(healer, Component.translatable("rolecraft.medic.not_medic"));
            e.setCanceled(true);
            return;
        }

        ItemStack stack = healer.getItemInHand(hand);
        if (!MedkitItem.hasUses(stack)) {
            UIUtil.actionBar(healer, Component.translatable("rolecraft.medic.no_uses"));
            e.setCanceled(true);
            return;
        }

        if (SESSIONS.containsKey(healer.getUUID())) {
            UIUtil.actionBar(healer, Component.translatable("rolecraft.medic.already_healing"));
            e.setCanceled(true);
            return;
        }

        if (healer.distanceTo(target) > MAX_DIST) {
            UIUtil.actionBar(healer, Component.translatable("rolecraft.medic.too_far"));
            e.setCanceled(true);
            return;
        }

        SESSIONS.put(healer.getUUID(), new HealSession(healer, target, hand));

        UIUtil.actionBar(healer, Component.translatable("rolecraft.medic.heal.start", target.getName()));
        UIUtil.actionBar(target, Component.translatable("rolecraft.medic.heal.started_by", healer.getName()));

        healer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HEAL_TICKS + 40, 10, false, false));
        healer.addEffect(new MobEffectInstance(MobEffects.JUMP, HEAL_TICKS + 40, 128, false, false));

        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer healer)) return;
        if (healer.level().isClientSide) return;

        HealSession s = SESSIONS.get(healer.getUUID());
        if (s == null) return;

        ServerPlayer target = healer.server.getPlayerList().getPlayer(s.targetId);
        if (target == null) {
            cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.target_left"));
            return;
        }

        if (!MedkitItem.isHoldingMedkit(healer, s.hand)) {
            cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.no_item"));
            return;
        }

        ItemStack stack = healer.getItemInHand(s.hand);
        if (!MedkitItem.hasUses(stack)) {
            cancel(healer, Component.translatable("rolecraft.medic.no_uses"));
            return;
        }

        if (!MedkitItem.isMedic(healer)) {
            cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.not_medic"));
            return;
        }

        if (healer.distanceTo(target) > MAX_DIST) {
            cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.too_far"));
            return;
        }

        double dx = healer.getX() - s.startX;
        double dy = healer.getY() - s.startY;
        double dz = healer.getZ() - s.startZ;
        if ((dx * dx + dy * dy + dz * dz) > (MAX_MOVE * MAX_MOVE)) {
            cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.moved"));
            return;
        }

        int done = HEAL_TICKS - s.remainingTicks;
        if (done < 0) done = 0;

        // update co 10 ticków
        if (done % 10 == 0) {
            int pct = (int) Math.floor((done / (double) HEAL_TICKS) * 100.0);
            if (pct > 100) pct = 100;

            UIUtil.actionBar(healer, UIUtil.prettyProgress("Healing:", target.getName(), pct));
        }

        s.remainingTicks--;
        if (s.remainingTicks <= 0) finish(healer, target, s);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) return;

        if (e.getEntity() instanceof ServerPlayer hurt) {
            HealSession s = SESSIONS.get(hurt.getUUID());
            if (s != null) {
                cancel(hurt, Component.translatable("rolecraft.medic.heal.cancel.hit"));
                return;
            }

            for (HealSession ss : SESSIONS.values()) {
                if (ss.targetId.equals(hurt.getUUID())) {
                    ServerPlayer healer = hurt.server.getPlayerList().getPlayer(ss.healerId);
                    if (healer != null) cancel(healer, Component.translatable("rolecraft.medic.heal.cancel.hit"));
                    break;
                }
            }
        }
    }

    private static void finish(ServerPlayer healer, ServerPlayer target, HealSession s) {
        if (target.getHealth() < target.getMaxHealth()) {
            target.heal(10.0F);
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true));
        }

        ItemStack stack = healer.getItemInHand(s.hand);
        if (!stack.isEmpty()) stack.hurtAndBreak(1, healer, p -> p.broadcastBreakEvent(s.hand));

        healer.sendSystemMessage(Component.translatable("rolecraft.medic.heal.success", target.getName()));
        target.sendSystemMessage(Component.translatable("rolecraft.medic.heal.successed_by", healer.getName()));

        cleanup(healer);
    }

    private static void cancel(ServerPlayer healer, Component reason) {
        UIUtil.actionBar(healer, reason);
        cleanup(healer);
    }

    private static void cleanup(ServerPlayer healer) {
        SESSIONS.remove(healer.getUUID());
        healer.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        healer.removeEffect(MobEffects.JUMP);
    }
}
