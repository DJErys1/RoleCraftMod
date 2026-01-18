package org.example.roleplay.rolecraft.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.menu.RoleEnchantMenu;
import org.example.roleplay.rolecraft.network.ModNetwork;
import org.example.roleplay.rolecraft.network.OpenRoleSelectPacket;
import org.example.roleplay.rolecraft.network.SyncRolePacket;
import org.example.roleplay.rolecraft.role.RoleType;
import org.example.roleplay.rolecraft.util.UIUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class ModEvents {

    /* =========================
       LOGIN / SYNC
    ========================= */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        PlayerRoleProvider.get(sp).ifPresent(data -> {
            ModNetwork.CHANNEL.sendTo(
                    new SyncRolePacket(
                            data.getRole(),
                            data.getJob(),
                            data.getRoleXp(),
                            data.getRoleLevel()
                    ),
                    sp.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );

            if (data.getRole() == RoleType.NONE) {
                ModNetwork.CHANNEL.sendTo(
                        new OpenRoleSelectPacket(),
                        sp.connection.connection,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
                );
            }
        });
    }

    /* =========================
       ENCHANTING TABLE – HARD OVERRIDE
    ========================= */
    @SubscribeEvent
    public void onRightClickEnchantingTable(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        if (sp.level().getBlockState(e.getPos()).getBlock() != Blocks.ENCHANTING_TABLE) return;

        RoleType role = getRole(sp);
        int level = getRoleLevel(sp);

        if (role != RoleType.BLACKSMITH) {
            UIUtil.actionBar(
                    sp,
                    Component.translatable("rolecraft.blacksmith.enchant.only_blacksmith")
            );
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (level < 10) {
            UIUtil.actionBar(
                    sp,
                    Component.translatable("rolecraft.blacksmith.enchant.need_lvl10")
            );
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        BlockPos pos = e.getPos();

        NetworkHooks.openScreen(
                sp,
                new net.minecraft.world.MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("container.enchant");
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                            int id,
                            net.minecraft.world.entity.player.Inventory inv,
                            net.minecraft.world.entity.player.Player player
                    ) {
                        return new RoleEnchantMenu(
                                id,
                                inv,
                                ContainerLevelAccess.create(sp.level(), pos),
                                pos
                        );
                    }
                },
                buf -> buf.writeBlockPos(pos)
        );

        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /* =========================
       BLOCK CRAFTING ENCHANT TABLE
    ========================= */
    @SubscribeEvent
    public void onCraftEnchantingTable(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        ItemStack crafted = e.getCrafting();
        if (crafted.isEmpty() || crafted.getItem() != Items.ENCHANTING_TABLE) return;

        RoleType role = getRole(sp);
        int level = getRoleLevel(sp);

        if (role != RoleType.BLACKSMITH || level < 10) {
            crafted.shrink(1);
            UIUtil.actionBar(
                    sp,
                    Component.translatable("rolecraft.blacksmith.craft_enchant_forbidden")
            );
        }
    }

    /* =========================
       HELPERS
    ========================= */
    private static RoleType getRole(ServerPlayer sp) {
        final RoleType[] r = {RoleType.NONE};
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles d) -> r[0] = d.getRole());
        return r[0];
    }

    private static int getRoleLevel(ServerPlayer sp) {
        AtomicInteger lvl = new AtomicInteger(0);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles d) -> lvl.set(d.getRoleLevel()));
        return lvl.get();
    }
}
