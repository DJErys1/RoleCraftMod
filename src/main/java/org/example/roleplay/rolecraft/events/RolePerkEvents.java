package org.example.roleplay.rolecraft.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.menu.RoleEnchantMenu;
import org.example.roleplay.rolecraft.role.RoleType;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class RolePerkEvents {

    private static int getRoleLevel(ServerPlayer sp) {
        AtomicInteger lvl = new AtomicInteger(0);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles d) -> lvl.set(d.getRoleLevel()));
        return lvl.get();
    }

    private static RoleType getRole(ServerPlayer sp) {
        final RoleType[] r = {RoleType.NONE};
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles d) -> r[0] = d.getRole());
        return r[0];
    }

    @SubscribeEvent
    public void onEnchantTableUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (sp.level().getBlockState(e.getPos()).getBlock() != Blocks.ENCHANTING_TABLE) return;

        RoleType role = getRole(sp);
        int level = getRoleLevel(sp);

        if (role != RoleType.BLACKSMITH) {
            UIUtil.actionBar(sp, net.minecraft.network.chat.Component.translatable("rolecraft.blacksmith.enchant.only_blacksmith"));
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (level < 10) {
            UIUtil.actionBar(sp, net.minecraft.network.chat.Component.translatable("rolecraft.blacksmith.enchant.need_lvl10"));
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        BlockPos pos = e.getPos();
        RoleEnchantMenu.open(sp, pos);

        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onCraftEnchantingTable(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        ItemStack crafted = e.getCrafting();
        if (crafted.isEmpty() || crafted.getItem() != Items.ENCHANTING_TABLE) return;

        RoleType role = getRole(sp);
        int level = getRoleLevel(sp);

        if (role != RoleType.BLACKSMITH || level < 10) {
            crafted.shrink(1);
            UIUtil.actionBar(sp, net.minecraft.network.chat.Component.translatable("rolecraft.blacksmith.craft_enchant_forbidden"));
        }
    }
}
