package org.example.roleplay.rolecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.role.RoleType;

import java.util.function.Supplier;

public class SetRolePacket {
    private final String role;

    public SetRolePacket(RoleType role) {
        this.role = role.name();
    }

    public SetRolePacket(String role) {
        this.role = role;
    }

    public static void encode(SetRolePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role);
    }

    public static SetRolePacket decode(FriendlyByteBuf buf) {
        return new SetRolePacket(buf.readUtf());
    }

    public static void handle(SetRolePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            RoleType chosen = RoleType.fromString(msg.role);

            PlayerRoleProvider.get(sp).ifPresent(data -> {
                // tylko pierwsze ustawienie z GUI
                if (data.getRole() == RoleType.NONE && chosen != RoleType.NONE) {
                    data.setRole(chosen);

                    // jeśli nowy gracz, zaczynamy od 0
                    if (data.getRoleXp() < 0) data.setRoleXp(0);
                    if (data.getRoleLevel() < 0) data.setRoleLevel(0);

                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Wybrano rolę: " + chosen.plName
                    ));

                    // ✅ SYNC roli+pracy+xp+level do klienta
                    ModNetwork.CHANNEL.sendTo(
                            new SyncRolePacket(data.getRole(), data.getJob(), data.getRoleXp(), data.getRoleLevel()),
                            sp.connection.connection,
                            NetworkDirection.PLAY_TO_CLIENT
                    );
                } else {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Nie możesz zmienić roli w ten sposób."
                    ));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
