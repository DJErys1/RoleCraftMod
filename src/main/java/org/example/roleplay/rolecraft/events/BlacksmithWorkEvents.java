package org.example.roleplay.rolecraft.events;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.role.RoleType;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BlacksmithWorkEvents {

    private static final int WORK_TICKS = 15 * 20; // 15 sekund
    private static final int SOUND_EVERY_TICKS = 20; // co 1 sek
    private static final int TOOLTIP_EVERY_TICKS = 10; // odśwież tooltip co 0.5s (żeby było “płynniej”)

    // Indeksy slotów inputów w menu (sloty menu, nie inventory indexy)
    private static final int CRAFTING_TABLE_INPUT_START = 1; // CraftingMenu: 1..9
    private static final int CRAFTING_TABLE_INPUT_END = 9;

    private static final int PLAYER_2X2_INPUT_START = 1; // InventoryMenu: 1..4
    private static final int PLAYER_2X2_INPUT_END = 4;

    private static final String TAG_WORK_BARRIER = "rolecraft_work_barrier";
    private static final String TAG_WORK_ITEMNAME = "rolecraft_work_itemname";

    // 1 gracz na stanowisko (crafting table/anvil/smithing)
    private static final Map<BlockPos, UUID> STATION_LOCKS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> LAST_STATION_POS = new ConcurrentHashMap<>();

    private enum Phase { WORKING, READY_TAKE }

    private enum MenuKind { CRAFTING_TABLE, PLAYER_2X2, ANVIL, SMITHING }

    private static class Session {
        final UUID playerId;
        final MenuKind kind;
        final int outputSlot;
        final List<Integer> inputSlots;
        final BlockPos stationPos;

        final ItemStack result;           // docelowy item
        final List<ItemStack> refundAll;  // WSZYSTKO co oddać po anulowaniu/wyjściu

        int ticksLeft = WORK_TICKS;
        int soundTick = SOUND_EVERY_TICKS;
        int tooltipTick = TOOLTIP_EVERY_TICKS;
        Phase phase = Phase.WORKING;

        Session(UUID playerId, MenuKind kind, int outputSlot, List<Integer> inputSlots, BlockPos stationPos,
                ItemStack result, List<ItemStack> refundAll) {
            this.playerId = playerId;
            this.kind = kind;
            this.outputSlot = outputSlot;
            this.inputSlots = inputSlots;
            this.stationPos = stationPos;
            this.result = result.copy();
            this.refundAll = refundAll;
        }
    }

    private static class OutputWatch {
        int outputSlot = -1;
        ItemStack lastOutput = ItemStack.EMPTY;
        final Map<Integer, ItemStack> inputSnapshot = new HashMap<>();
        void clear() {
            outputSlot = -1;
            lastOutput = ItemStack.EMPTY;
            inputSnapshot.clear();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, OutputWatch> WATCH = new ConcurrentHashMap<>();

    /* =========================
       ROLE / RESTRICTIONS
    ========================= */

    private static RoleType getRole(ServerPlayer sp) {
        AtomicReference<RoleType> r = new AtomicReference<>(RoleType.NONE);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles d) -> r.set(d.getRole()));
        return r.get();
    }

    private static boolean isBlacksmith(ServerPlayer sp) {
        return getRole(sp) == RoleType.BLACKSMITH;
    }

    private static boolean isArmor(ItemStack s) {
        return s.getItem() instanceof ArmorItem;
    }

    private static boolean isToolBetterThanStone(ItemStack s) {
        if (!(s.getItem() instanceof TieredItem t)) return false;
        Tier tier = t.getTier();
        return tier != Tiers.WOOD && tier != Tiers.STONE;
    }

    // “kowalskie” rzeczy z timerem
    private static boolean restrictedResult(ItemStack s) {
        return isArmor(s) || isToolBetterThanStone(s);
    }

    /* =========================
       STATIONS (lock + last pos)
    ========================= */

    private static boolean isAnyAnvil(Block b) {
        return b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL;
    }

    private static boolean isLockableStation(Block b) {
        return b == Blocks.CRAFTING_TABLE || isAnyAnvil(b) || b == Blocks.SMITHING_TABLE;
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        BlockPos pos = e.getPos();
        Block b = sp.level().getBlockState(pos).getBlock();

        // zapamiętaj ostatnie stanowisko
        if (isLockableStation(b) || b == Blocks.BLAST_FURNACE) {
            LAST_STATION_POS.put(sp.getUUID(), pos.immutable());
        }

        // blast furnace tylko kowal (bez timera)
        if (b == Blocks.BLAST_FURNACE && !isBlacksmith(sp)) {
            UIUtil.actionBar(sp, Component.literal("Only Blacksmith can use Blast Furnace."));
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        // lock stanowiska (1 gracz)
        if (isLockableStation(b)) {
            UUID owner = STATION_LOCKS.get(pos);
            if (owner != null && !owner.equals(sp.getUUID())) {
                UIUtil.actionBar(sp, Component.literal("This station is currently used by another player."));
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    private static void lockStation(Session s) {
        if (s.stationPos == null || s.stationPos.equals(BlockPos.ZERO)) return;
        STATION_LOCKS.put(s.stationPos, s.playerId);
    }

    private static void unlockStation(Session s) {
        if (s == null) return;
        if (s.stationPos == null || s.stationPos.equals(BlockPos.ZERO)) return;
        UUID cur = STATION_LOCKS.get(s.stationPos);
        if (cur != null && cur.equals(s.playerId)) STATION_LOCKS.remove(s.stationPos);
    }

    /* =========================
       INVENTORY HELPERS
    ========================= */

    private static void give(ServerPlayer sp, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        ItemStack copy = s.copy();
        if (!sp.getInventory().add(copy)) sp.drop(copy, false);
    }

    private static void giveBack(ServerPlayer sp, List<ItemStack> items) {
        if (items == null) return;
        for (ItemStack s : items) give(sp, s);
    }

    private static boolean isWorkBarrier(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() != Items.BARRIER) return false;
        CompoundTag t = s.getTag();
        return t != null && t.getBoolean(TAG_WORK_BARRIER);
    }

    private static ItemStack makeBarrier(String itemName, int secLeft) {
        ItemStack b = new ItemStack(Items.BARRIER);
        CompoundTag tag = b.getOrCreateTag();
        tag.putBoolean(TAG_WORK_BARRIER, true);
        tag.putString(TAG_WORK_ITEMNAME, itemName);
        b.setHoverName(Component.literal("Crafting... " + itemName + " (" + secLeft + "s)"));
        return b;
    }

    // kasujemy “wyciągnięte” bariery (cursor + inv)
    private static void removeWorkBarriersEverywhere(ServerPlayer sp) {
        // cursor
        ItemStack carried = sp.containerMenu.getCarried();
        if (isWorkBarrier(carried)) sp.containerMenu.setCarried(ItemStack.EMPTY);

        // inventory/hotbar
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (isWorkBarrier(s)) sp.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    // usuń wynik, który vanilla zdążyła dać na cursor/inv (anti-free craft)
    private static void wipeOutputEverywhere(ServerPlayer sp, ItemStack crafted) {
        if (crafted == null || crafted.isEmpty()) return;

        // cursor
        ItemStack carried = sp.containerMenu.getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameTags(carried, crafted)) {
            int take = Math.min(carried.getCount(), crafted.getCount());
            carried.shrink(take);
            if (carried.isEmpty()) sp.containerMenu.setCarried(ItemStack.EMPTY);
        }

        // inventory
        int need = crafted.getCount();
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (!ItemStack.isSameItemSameTags(s, crafted)) continue;

            int take = Math.min(need, s.getCount());
            s.shrink(take);
            need -= take;
            if (need <= 0) break;
        }
    }

    /* =========================
       MENU HELPERS
    ========================= */

    private static MenuKind menuKind(AbstractContainerMenu m) {
        if (m instanceof CraftingMenu) return MenuKind.CRAFTING_TABLE;
        if (m instanceof InventoryMenu) return MenuKind.PLAYER_2X2;
        if (m instanceof AnvilMenu) return MenuKind.ANVIL;
        if (m instanceof SmithingMenu) return MenuKind.SMITHING;
        return null;
    }

    private static int outputSlotFor(MenuKind k) {
        if (k == MenuKind.CRAFTING_TABLE) return 0;
        if (k == MenuKind.PLAYER_2X2) return 0;
        if (k == MenuKind.ANVIL) return 2;
        if (k == MenuKind.SMITHING) return 3;
        return -1;
    }

    private static List<Integer> inputSlotsFor(MenuKind k) {
        ArrayList<Integer> list = new ArrayList<>();
        if (k == MenuKind.CRAFTING_TABLE) {
            for (int i = CRAFTING_TABLE_INPUT_START; i <= CRAFTING_TABLE_INPUT_END; i++) list.add(i);
        } else if (k == MenuKind.PLAYER_2X2) {
            for (int i = PLAYER_2X2_INPUT_START; i <= PLAYER_2X2_INPUT_END; i++) list.add(i);
        } else if (k == MenuKind.ANVIL) {
            list.add(0); list.add(1);
        } else if (k == MenuKind.SMITHING) {
            list.add(0); list.add(1); list.add(2);
        }
        return list;
    }

    private static BlockPos stationPosFor(ServerPlayer sp, MenuKind k) {
        if (k == MenuKind.PLAYER_2X2) return BlockPos.ZERO;

        BlockPos last = LAST_STATION_POS.getOrDefault(sp.getUUID(), BlockPos.ZERO);
        if (last.equals(BlockPos.ZERO)) return BlockPos.ZERO;

        Block b = sp.level().getBlockState(last).getBlock();
        if (k == MenuKind.CRAFTING_TABLE && b == Blocks.CRAFTING_TABLE) return last;
        if (k == MenuKind.ANVIL && isAnyAnvil(b)) return last;
        if (k == MenuKind.SMITHING && b == Blocks.SMITHING_TABLE) return last;

        return BlockPos.ZERO;
    }

    // klucz: ustawiamy item BEZPOŚREDNIO w kontenerze slota (vanilla slot.set() bywa nadpisany)
    private static void setSlotContainerItem(AbstractContainerMenu menu, int menuSlotIndex, ItemStack stack) {
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) return;
        Slot sl = menu.getSlot(menuSlotIndex);

        // kontener, który stoi za slotem (np ResultContainer)
        sl.container.setItem(sl.getContainerSlot(), stack);
        sl.setChanged();
    }

    private static void snapshotInputs(AbstractContainerMenu m, OutputWatch w, List<Integer> inputSlots) {
        w.inputSnapshot.clear();
        for (int slot : inputSlots) {
            if (slot < 0 || slot >= m.slots.size()) continue;
            ItemStack s = m.getSlot(slot).getItem();
            if (!s.isEmpty()) w.inputSnapshot.put(slot, s.copy());
        }
    }

    private static List<ItemStack> computeConsumedFromSnapshot(AbstractContainerMenu m, OutputWatch w) {
        List<ItemStack> consumed = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : w.inputSnapshot.entrySet()) {
            int slot = entry.getKey();
            ItemStack before = entry.getValue();
            ItemStack after = m.getSlot(slot).getItem();

            int beforeCount = before.getCount();
            int afterCount = after.isEmpty() ? 0 : after.getCount();

            int diff = beforeCount - afterCount;
            if (diff > 0) {
                ItemStack pay = before.copy();
                pay.setCount(diff);
                consumed.add(pay);
            }
        }
        return consumed;
    }

    private static List<ItemStack> takeAllRemainingInputsAndClear(AbstractContainerMenu m, List<Integer> inputSlots) {
        List<ItemStack> remaining = new ArrayList<>();
        for (int slot : inputSlots) {
            if (slot < 0 || slot >= m.slots.size()) continue;
            ItemStack s = m.getSlot(slot).getItem();
            if (!s.isEmpty()) {
                remaining.add(s.copy());
                m.getSlot(slot).set(ItemStack.EMPTY); // inputy można normalnie czyścić
            }
        }
        // UWAGA: broadcastChanges NIE odpala recompute receptury tak jak slotsChanged, a sync jest potrzebny
        m.broadcastChanges();
        return remaining;
    }

    /* =========================
       SESSION FLOW
    ========================= */

    private static void startSession(ServerPlayer sp, MenuKind kind, int outSlot, List<Integer> inSlots,
                                     BlockPos stationPos, ItemStack craftedResult, List<ItemStack> refundAll) {

        UUID id = sp.getUUID();
        if (SESSIONS.containsKey(id)) return;

        // anti-dupe (wynik który vanilla dała graczowi -> kasujemy)
        wipeOutputEverywhere(sp, craftedResult);

        Session s = new Session(id, kind, outSlot, inSlots, stationPos, craftedResult, refundAll);
        SESSIONS.put(id, s);
        lockStation(s);

        // wstawiamy barrier bezpośrednio do kontenera output slota
        String name = craftedResult.getHoverName().getString();
        int sec = (WORK_TICKS + 19) / 20;
        setSlotContainerItem(sp.containerMenu, outSlot, makeBarrier(name, sec));
        sp.containerMenu.broadcastChanges();

        UIUtil.actionBar(sp, Component.literal("Crafting started... (15s)"));
    }

    private static void cancelSession(ServerPlayer sp, Session s) {
        if (s == null) return;

        // wyczyść output (kontener)
        setSlotContainerItem(sp.containerMenu, s.outputSlot, ItemStack.EMPTY);
        sp.containerMenu.broadcastChanges();

        // usuń ewentualne “wyciągnięte” bariery
        removeWorkBarriersEverywhere(sp);

        // oddaj wszystko
        giveBack(sp, s.refundAll);

        unlockStation(s);
        SESSIONS.remove(sp.getUUID());

        UIUtil.actionBar(sp, Component.literal("Crafting cancelled. Ingredients returned."));
    }

    private static void finishToReadyTake(ServerPlayer sp, Session s) {
        s.phase = Phase.READY_TAKE;

        removeWorkBarriersEverywhere(sp);

        // wstaw docelowy item do output kontenera
        setSlotContainerItem(sp.containerMenu, s.outputSlot, s.result.copy());
        sp.containerMenu.broadcastChanges();

        UIUtil.actionBar(sp, Component.literal("Crafting done. Take the item."));
    }

    private static void endSession(ServerPlayer sp, Session s) {
        removeWorkBarriersEverywhere(sp);
        unlockStation(s);
        SESSIONS.remove(sp.getUUID());
    }

    /* =========================
       BLOCK ACTIONS while WORKING (serwer)
    ========================= */

    private static boolean isWorking(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        return s != null && s.phase == Phase.WORKING;
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (!isWorking(sp)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public void onRightClickBlockBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (!isWorking(sp)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (!isWorking(sp)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (!isWorking(sp)) return;
        e.setCanceled(true);
    }

    /* =========================
       MAIN TICK (watch output + run session)
    ========================= */

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        UUID id = sp.getUUID();

        // ===== 1) jeżeli sesja aktywna
        Session s = SESSIONS.get(id);
        if (s != null) {
            AbstractContainerMenu menu = sp.containerMenu;
            MenuKind cur = (menu == null ? null : menuKind(menu));

            // jeśli zamknął GUI:
            if (cur == null || cur != s.kind) {
                if (s.phase == Phase.WORKING) cancelSession(sp, s);
                else endSession(sp, s); // READY_TAKE -> jeśli zamknął po zakończeniu, nic nie “dupujemy”
                return;
            }

            // twarde usuwanie wyciągniętych barrierów
            removeWorkBarriersEverywhere(sp);

            if (s.phase == Phase.WORKING) {
                // output zawsze barrier
                ItemStack out = menu.getSlot(s.outputSlot).getItem();
                if (!isWorkBarrier(out)) {
                    int secLeft = (s.ticksLeft + 19) / 20;
                    String name = s.result.getHoverName().getString();
                    setSlotContainerItem(menu, s.outputSlot, makeBarrier(name, secLeft));
                    menu.broadcastChanges();
                }

                // blokada inputów: nie wolno wrzucać nic w trakcie pracy
                for (int slotIndex : s.inputSlots) {
                    if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
                    ItemStack in = menu.getSlot(slotIndex).getItem();
                    if (!in.isEmpty()) {
                        menu.getSlot(slotIndex).set(ItemStack.EMPTY);
                        give(sp, in);
                    }
                }

                // dźwięk “pracy” co 1 sek
                s.soundTick--;
                if (s.soundTick <= 0) {
                    s.soundTick = SOUND_EVERY_TICKS;
                    sp.level().playSound(null, sp.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.75f, 1.0f);
                }

                // odśwież tooltip w barrierze
                s.tooltipTick--;
                if (s.tooltipTick <= 0) {
                    s.tooltipTick = TOOLTIP_EVERY_TICKS;
                    int secLeft = (s.ticksLeft + 19) / 20;
                    String name = s.result.getHoverName().getString();
                    setSlotContainerItem(menu, s.outputSlot, makeBarrier(name, secLeft));
                    menu.broadcastChanges();
                }

                // timer
                s.ticksLeft--;
                if (s.ticksLeft < 0) s.ticksLeft = 0;

                if (s.ticksLeft == 0) {
                    finishToReadyTake(sp, s);
                }
                return;
            }

            // READY_TAKE: czekamy aż output stanie się pusty (czyli gracz zabrał wynik)
            if (s.phase == Phase.READY_TAKE) {
                ItemStack out = menu.getSlot(s.outputSlot).getItem();
                if (out.isEmpty()) {
                    endSession(sp, s);
                }
                return;
            }
        }

        // ===== 2) brak sesji -> watcher na “wzięcie wyniku”
        AbstractContainerMenu menu = sp.containerMenu;
        MenuKind k = (menu == null ? null : menuKind(menu));
        if (k == null) {
            WATCH.remove(id);
            return;
        }

        int outSlot = outputSlotFor(k);
        if (outSlot < 0 || outSlot >= menu.slots.size()) {
            WATCH.remove(id);
            return;
        }

        List<Integer> inputSlots = inputSlotsFor(k);
        OutputWatch w = WATCH.computeIfAbsent(id, x -> new OutputWatch());
        w.outputSlot = outSlot;

        ItemStack outNow = menu.getSlot(outSlot).getItem();
        ItemStack outPrev = w.lastOutput;

        // snapshot inputów jeśli output jest niepusty
        if (!outNow.isEmpty()) {
            snapshotInputs(menu, w, inputSlots);
        }

        // DETEKCJA: było coś, teraz pusto -> gracz zabrał wynik
        if (!outPrev.isEmpty() && outNow.isEmpty()) {
            ItemStack crafted = outPrev.copy();

            // jeśli nie jest “kowalskie” -> nie ingerujemy
            if (!restrictedResult(crafted)) {
                w.lastOutput = outNow.copy();
                return;
            }

            // tylko kowal
            if (!isBlacksmith(sp)) {
                wipeOutputEverywhere(sp, crafted);
                UIUtil.actionBar(sp, Component.literal("Only Blacksmith can craft this item."));
                w.clear();
                return;
            }

            // lock stanowiska (1 gracz) dla GUI stanowisk (nie dotyczy 2x2)
            BlockPos stationPos = stationPosFor(sp, k);
            if (k != MenuKind.PLAYER_2X2 && stationPos != null && !stationPos.equals(BlockPos.ZERO)) {
                UUID owner = STATION_LOCKS.get(stationPos);
                if (owner != null && !owner.equals(sp.getUUID())) {
                    // zabierz wynik (żeby nie było craftu)
                    wipeOutputEverywhere(sp, crafted);

                    // oddaj snapshot inputów (fail-safe)
                    List<ItemStack> refund = new ArrayList<>();
                    for (ItemStack snap : w.inputSnapshot.values()) refund.add(snap.copy());
                    giveBack(sp, refund);

                    UIUtil.actionBar(sp, Component.literal("This station is currently used by another player."));
                    w.clear();
                    return;
                }
            }

            // policz co vanilla zużyła
            List<ItemStack> consumed = computeConsumedFromSnapshot(menu, w);

            // zbierz resztki (to co zostało w inputach) i wyczyść inputy
            List<ItemStack> remaining = takeAllRemainingInputsAndClear(menu, inputSlots);

            // refund = zużyte + resztki => przy anulowaniu wszystko wróci
            List<ItemStack> refundAll = new ArrayList<>();
            refundAll.addAll(consumed);
            refundAll.addAll(remaining);

            // FAIL-SAFE: jeśli z jakiegoś powodu snapshot jest pusty i remaining puste, to i tak oddajemy snapshot (gdyby coś poszło źle)
            if (refundAll.isEmpty() && !w.inputSnapshot.isEmpty()) {
                for (ItemStack snap : w.inputSnapshot.values()) refundAll.add(snap.copy());
            }

            // start sesji
            try {
                startSession(sp, k, outSlot, inputSlots, stationPos, crafted, refundAll);
            } catch (Exception ex) {
                // JEŚLI COKOLWIEK poszło nie tak -> NIE MA opcji “zjadło i nic nie oddało”
                wipeOutputEverywhere(sp, crafted);
                giveBack(sp, refundAll);
                UIUtil.actionBar(sp, Component.literal("Crafting error. Ingredients returned."));
            }

            w.clear();
            return;
        }

        w.lastOutput = outNow.copy();
    }

    /* =========================
       LOGOUT CLEANUP
    ========================= */

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        Session s = SESSIONS.remove(sp.getUUID());
        if (s != null) {
            removeWorkBarriersEverywhere(sp);

            // jeśli wyszedł w trakcie pracy -> oddaj składniki
            if (s.phase == Phase.WORKING) giveBack(sp, s.refundAll);

            unlockStation(s);
        }

        WATCH.remove(sp.getUUID());
        LAST_STATION_POS.remove(sp.getUUID());
    }
}
