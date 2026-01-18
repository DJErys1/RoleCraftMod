package org.example.roleplay.rolecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.example.roleplay.rolecraft.client.ClientPacketHandlers;

import java.util.function.Supplier;

public class OpenRoleSelectPacket {

    public OpenRoleSelectPacket() {}

    public static void encode(OpenRoleSelectPacket msg, FriendlyByteBuf buf) {
        // brak danych
    }

    public static OpenRoleSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenRoleSelectPacket();
    }

    public static void handle(OpenRoleSelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openRoleSelectScreen());
        });
        ctx.get().setPacketHandled(true);
    }
}
