package org.example.roleplay.rolecraft.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class SearchMenu extends AbstractContainerMenu {

    private final Container targetInventory; // 36 slotów (kopiujemy referencje przez wrapper)
    private final Player viewer;

    // CLIENT constructor (Forge menu factory)
    public SearchMenu(int windowId, Inventory viewerInv, FriendlyByteBuf buf) {
        this(windowId, viewerInv, getTargetPlayer(viewerInv.player, buf.readUUID()));
    }

    // SERVER constructor
    public SearchMenu(int windowId, Inventory viewerInv, ServerPlayer target) {
        super(ModMenus.SEARCH_MENU.get(), windowId);
        this.viewer = viewerInv.player;

        // jeśli target null -> pusty kontener (żeby nie crashowało)
        if (target == null) {
            this.targetInventory = new SimpleContainer(36);
        } else {
            this.targetInventory = new PlayerInvContainer(target);
        }

        // Target inventory (36): 0-8 hotbar, 9-35 main
        // Layout: 9x4
        int startX = 8;
        int startY = 18;

        // Main inventory rows (3x9) -> sloty 9..35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + row * 9 + col;
                this.addSlot(new Slot(targetInventory, idx, startX + col * 18, startY + row * 18));
            }
        }

        // Hotbar -> 0..8
        int hotbarY = startY + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(targetInventory, col, startX + col * 18, hotbarY));
        }

        // Viewer inventory (player opening the screen)
        int invY = 140;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + row * 9 + col;
                this.addSlot(new Slot(viewerInv, idx, startX + col * 18, invY + row * 18));
            }
        }

        int viewerHotbarY = invY + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(viewerInv, col, startX + col * 18, viewerHotbarY));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player == viewer; // prosto i bezpiecznie
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        // target slots: 0..35 w menu = pierwsze 36 slotów, ale w naszym układzie:
        // dodaliśmy najpierw target (36), potem viewer (36) => razem 72
        int targetStart = 0;
        int targetEnd = 36;
        int viewerStart = 36;
        int viewerEnd = 72;

        if (index < targetEnd) {
            // z target -> do viewer
            if (!this.moveItemStackTo(stack, viewerStart, viewerEnd, true)) return ItemStack.EMPTY;
        } else {
            // z viewer -> do target
            if (!this.moveItemStackTo(stack, targetStart, targetEnd, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    private static ServerPlayer getTargetPlayer(Player viewer, UUID uuid) {
        if (!(viewer instanceof ServerPlayer sp)) return null;
        return sp.server.getPlayerList().getPlayer(uuid);
    }

    /**
     * Wrapper na inventory gracza — pozwala brać/odkładać realne itemy.
     * Kontener mapuje 0..35 na target.getInventory().
     */
    private static class PlayerInvContainer implements Container {
        private final ServerPlayer target;

        PlayerInvContainer(ServerPlayer target) {
            this.target = target;
        }

        @Override public int getContainerSize() { return 36; }
        @Override public boolean isEmpty() { return target.getInventory().isEmpty(); }

        @Override
        public ItemStack getItem(int slot) {
            return target.getInventory().getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = target.getInventory().removeItem(slot, amount);
            target.getInventory().setChanged();
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = target.getInventory().removeItemNoUpdate(slot);
            target.getInventory().setChanged();
            return removed;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            target.getInventory().setItem(slot, stack);
            target.getInventory().setChanged();
        }

        @Override public void setChanged() { target.getInventory().setChanged(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { /* nie czyścimy */ }
    }
}
