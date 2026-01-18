package org.example.roleplay.rolecraft.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Player2x2RestrictEvents {

    private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

    private static boolean isToolBetterThanStone(ItemStack s) {
        if (!(s.getItem() instanceof TieredItem t)) return false;
        Tier tier = t.getTier();
        return tier != Tiers.WOOD && tier != Tiers.STONE;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        if (!(sp.containerMenu instanceof InventoryMenu menu)) return;

        Slot resultSlot = menu.getSlot(0);
        ItemStack result = resultSlot.getItem();
        if (result.isEmpty()) return;

        if (!isToolBetterThanStone(result)) return;

        resultSlot.set(ItemStack.EMPTY);
        menu.broadcastChanges();

        int cd = COOLDOWN.getOrDefault(sp.getUUID(), 0);
        if (cd <= 0) {
            UIUtil.actionBar(sp, net.minecraft.network.chat.Component.translatable("rolecraft.crafting2x2.repair_blocked"));
            COOLDOWN.put(sp.getUUID(), 20);
        } else {
            COOLDOWN.put(sp.getUUID(), cd - 1);
        }
    }
}
