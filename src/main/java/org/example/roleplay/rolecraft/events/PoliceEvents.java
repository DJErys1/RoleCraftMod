package org.example.roleplay.rolecraft.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.item.HandcuffsItem;
import org.example.roleplay.rolecraft.item.ModItems;
import org.example.roleplay.rolecraft.menu.SearchMenu;
import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PoliceEvents {

    private static final String NBT_CUFFED = "rolecraft_cuffed";
    private static final String NBT_CUFFED_BY = "rolecraft_cuffed_by";

    private static final int CUFF_TICKS = 4 * 20;    // 4 sekundy zakuwanie
    private static final int UNCUFF_TICKS = 2 * 20;  // 2 sekundy rozkuwanie
    private static final double MAX_DIST = 4.0;
    private static final double MAX_MOVE = 0.40;

    private static final Map<UUID, CuffSession> SESSIONS = new ConcurrentHashMap<>();

    private static class CuffSession {
        final UUID copId;
        final UUID targetId;
        final InteractionHand hand;
        final boolean isUncuff;
        final double startX, startY, startZ;
        int remainingTicks;

        CuffSession(ServerPlayer cop, ServerPlayer target, InteractionHand hand, boolean isUncuff) {
            this.copId = cop.getUUID();
            this.targetId = target.getUUID();
            this.hand = hand;
            this.isUncuff = isUncuff;
            this.startX = cop.getX();
            this.startY = cop.getY();
            this.startZ = cop.getZ();
            this.remainingTicks = isUncuff ? UNCUFF_TICKS : CUFF_TICKS;
        }
    }

    @SubscribeEvent
    public void onRightClickPlayer(PlayerInteractEvent.EntityInteractSpecific e) {
        if (!(e.getEntity() instanceof ServerPlayer cop)) return;
        Entity clicked = e.getTarget();
        if (!(clicked instanceof ServerPlayer target)) return;
        if (cop.level().isClientSide) return;

        InteractionHand hand = e.getHand();
        ItemStack held = cop.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() == null) return;
        if (!(held.getItem() instanceof HandcuffsItem) && !held.is(ModItems.HANDCUFFS.get())) return;

        // SHIFT + PPM na zakutym -> przeszukanie
        if (cop.isShiftKeyDown() && isCuffed(target)) {
            if (!isPoliceman(cop)) {
                UIUtil.actionBar(cop, Component.translatable("rolecraft.police.not_police"));
                e.setCanceled(true);
                return;
            }
            openSearch(cop, target);
            e.setCanceled(true);
            return;
        }

        // Normalne PPM -> zakuj / rozkuj (tylko policjant)
        if (!isPoliceman(cop)) {
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.not_police"));
            e.setCanceled(true);
            return;
        }

        if (SESSIONS.containsKey(cop.getUUID())) {
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.busy"));
            e.setCanceled(true);
            return;
        }

        if (cop.distanceTo(target) > MAX_DIST) {
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.too_far"));
            e.setCanceled(true);
            return;
        }

        boolean targetIsCuffed = isCuffed(target);
        boolean doUncuff = targetIsCuffed;

        // start session
        SESSIONS.put(cop.getUUID(), new CuffSession(cop, target, hand, doUncuff));

        if (doUncuff) {
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.uncuff.start", target.getName()));
            UIUtil.actionBar(target, Component.translatable("rolecraft.police.uncuff.started_by", cop.getName()));
        } else {
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.cuff.start", target.getName()));
            UIUtil.actionBar(target, Component.translatable("rolecraft.police.cuff.started_by", cop.getName()));
        }

        // “zamrożenie” policjanta w trakcie
        int ticks = doUncuff ? UNCUFF_TICKS : CUFF_TICKS;
        cop.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 20, 10, false, false));
        cop.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks + 20, 128, false, false));

        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer cop)) return;
        if (cop.level().isClientSide) return;

        // session tick
        CuffSession s = SESSIONS.get(cop.getUUID());
        if (s != null) {
            ServerPlayer target = cop.server.getPlayerList().getPlayer(s.targetId);
            if (target == null) {
                cancel(cop, Component.translatable("rolecraft.police.cancel.target_left"));
                return;
            }

            // nadal trzyma kajdanki?
            ItemStack held = cop.getItemInHand(s.hand);
            if (held.isEmpty() || !(held.getItem() instanceof HandcuffsItem) && !held.is(ModItems.HANDCUFFS.get())) {
                cancel(cop, Component.translatable("rolecraft.police.cancel.no_item"));
                return;
            }

            if (!isPoliceman(cop)) {
                cancel(cop, Component.translatable("rolecraft.police.not_police"));
                return;
            }

            if (cop.distanceTo(target) > MAX_DIST) {
                cancel(cop, Component.translatable("rolecraft.police.cancel.too_far"));
                return;
            }

            double dx = cop.getX() - s.startX;
            double dy = cop.getY() - s.startY;
            double dz = cop.getZ() - s.startZ;
            if ((dx * dx + dy * dy + dz * dz) > (MAX_MOVE * MAX_MOVE)) {
                cancel(cop, Component.translatable("rolecraft.police.cancel.moved"));
                return;
            }

            int total = s.isUncuff ? UNCUFF_TICKS : CUFF_TICKS;
            int done = total - s.remainingTicks;
            if (done < 0) done = 0;

            // progress co 5 ticków
            if (done % 5 == 0) {
                int pct = (int) Math.floor((done / (double) total) * 100.0);
                if (pct > 100) pct = 100;

                String label = s.isUncuff ? "Uncuffing:" : "Cuffing:";
                UIUtil.actionBar(cop, UIUtil.prettyProgress(label, target.getName(), pct));
            }

            s.remainingTicks--;
            if (s.remainingTicks <= 0) {
                finish(cop, target, s);
            }
        }

        // enforce cuff effects on all cuffed players (server side)
        // robimy to w ticku wszystkich graczy zakutych - ale tu mamy tylko tick “cop”.
        // więc dodatkowo łapiemy to w osobnym ticku dla każdego gracza:
    }

    @SubscribeEvent
    public void onEveryPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (isCuffed(sp)) {
            // utrzymuj efekty
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 4, false, false));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, 25, 128, false, false));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 1, false, false));

            // zablokuj “dziwne” GUI (np. crafting) – zamknij jeśli nie jest nasze / inventory
            if (sp.containerMenu != sp.inventoryMenu && !(sp.containerMenu instanceof SearchMenu)) {
                sp.closeContainer();
            }
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        Player p = e.getPlayer();
        if (!(p instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (isCuffed(sp)) {
            e.setCanceled(true);
            UIUtil.actionBar(sp, Component.translatable("rolecraft.police.cuffed.no_break"));
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (isCuffed(sp)) {
            e.setCanceled(true);
            UIUtil.actionBar(sp, Component.translatable("rolecraft.police.cuffed.no_interact"));
        }
    }

    @SubscribeEvent
    public void onInteractItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (isCuffed(sp)) {
            e.setCanceled(true);
            UIUtil.actionBar(sp, Component.translatable("rolecraft.police.cuffed.no_interact"));
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) return;

        // jeśli policjant w trakcie lub target w trakcie dostanie hit -> anulujemy
        if (e.getEntity() instanceof ServerPlayer hurt) {
            // healer/copper
            CuffSession s = SESSIONS.get(hurt.getUUID());
            if (s != null) {
                cancel(hurt, Component.translatable("rolecraft.police.cancel.hit"));
                return;
            }

            // target
            for (CuffSession ss : SESSIONS.values()) {
                if (ss.targetId.equals(hurt.getUUID())) {
                    ServerPlayer cop = hurt.server.getPlayerList().getPlayer(ss.copId);
                    if (cop != null) cancel(cop, Component.translatable("rolecraft.police.cancel.hit"));
                    break;
                }
            }
        }
    }

    private static void finish(ServerPlayer cop, ServerPlayer target, CuffSession s) {
        if (s.isUncuff) {
            setCuffed(target, false, null);
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.uncuff.done", target.getName()));
            UIUtil.actionBar(target, Component.translatable("rolecraft.police.uncuffed", cop.getName()));
        } else {
            setCuffed(target, true, cop.getUUID());
            UIUtil.actionBar(cop, Component.translatable("rolecraft.police.cuff.done", target.getName()));
            UIUtil.actionBar(target, Component.translatable("rolecraft.police.cuffed", cop.getName()));
        }

        cleanup(cop);
    }

    private static void cancel(ServerPlayer cop, Component reason) {
        UIUtil.actionBar(cop, reason);
        cleanup(cop);
    }

    private static void cleanup(ServerPlayer cop) {
        SESSIONS.remove(cop.getUUID());
        cop.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        cop.removeEffect(MobEffects.JUMP);
    }

    private static boolean isCuffed(ServerPlayer p) {
        return p.getPersistentData().getBoolean(NBT_CUFFED);
    }

    private static void setCuffed(ServerPlayer target, boolean cuffed, UUID by) {
        target.getPersistentData().putBoolean(NBT_CUFFED, cuffed);
        if (cuffed && by != null) {
            target.getPersistentData().putUUID(NBT_CUFFED_BY, by);
        } else {
            target.getPersistentData().remove(NBT_CUFFED_BY);
        }
    }

    private static boolean isPoliceman(ServerPlayer p) {
        AtomicBoolean ok = new AtomicBoolean(false);
        PlayerRoleProvider.get(p).ifPresent((IPlayerRoles data) -> ok.set(data.getJob() == JobType.POLICEMAN));
        return ok.get();
    }

    private static void openSearch(ServerPlayer cop, ServerPlayer target) {
        NetworkHooks.openScreen(
                cop,
                new net.minecraft.world.MenuProvider() {
                    @Override public Component getDisplayName() {
                        return Component.translatable("rolecraft.police.search.title", target.getName());
                    }
                    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                        return new SearchMenu(id, inv, target);
                    }
                },
                buf -> buf.writeUUID(target.getUUID())
        );
    }
}
