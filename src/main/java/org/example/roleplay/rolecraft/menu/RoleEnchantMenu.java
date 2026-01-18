package org.example.roleplay.rolecraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraftforge.network.NetworkHooks;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.role.RoleType;

import java.util.concurrent.atomic.AtomicInteger;

public class RoleEnchantMenu extends EnchantmentMenu {

    private final BlockPos tablePos;
    private final DataSlot allowedRow = DataSlot.standalone();

    public RoleEnchantMenu(int id, Inventory inv, ContainerLevelAccess access, BlockPos pos) {
        super(id, inv, access);
        this.tablePos = pos;
        this.addDataSlot(allowedRow);

        if (inv.player instanceof ServerPlayer sp) {
            int lvl = getRoleLevel(sp);
            allowedRow.set(allowedEnchantRowForBlacksmithLevel(lvl));
        } else {
            allowedRow.set(0);
        }
    }

    public static RoleEnchantMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new RoleEnchantMenu(id, inv, ContainerLevelAccess.create(inv.player.level(), pos), pos);
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        NetworkHooks.openScreen(
                sp,
                new SimpleMenuProvider(
                        (id, inv, p) -> new RoleEnchantMenu(id, inv, ContainerLevelAccess.create(sp.level(), pos), pos),
                        Component.translatable("container.enchant")
                ),
                buf -> buf.writeBlockPos(pos)
        );
    }

    public int getAllowedRow() {
        return allowedRow.get();
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);

        int a = allowedRow.get();
        for (int i = 0; i < 3; i++) {
            if (i > a) {
                this.costs[i] = 0;
                this.enchantClue[i] = -1;
                this.levelClue[i] = -1;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer sp)) return super.stillValid(player);

        RoleType role = getRole(sp);
        int lvl = getRoleLevel(sp);
        if (role != RoleType.BLACKSMITH) return false;
        if (lvl < 10) return false;

        return super.stillValid(player);
    }

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

    private static int allowedEnchantRowForBlacksmithLevel(int roleLevel) {
        if (roleLevel >= 50) return 2;
        if (roleLevel >= 30) return 1;
        if (roleLevel >= 10) return 0;
        return -1;
    }
}
