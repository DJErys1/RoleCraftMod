package org.example.roleplay.rolecraft.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.example.roleplay.rolecraft.Rolecraft;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(Rolecraft.MODID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        int id = 0;

        CHANNEL.messageBuilder(OpenRoleSelectPacket.class, id++)
                .encoder(OpenRoleSelectPacket::encode)
                .decoder(OpenRoleSelectPacket::decode)
                .consumerMainThread(OpenRoleSelectPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetRolePacket.class, id++)
                .encoder(SetRolePacket::encode)
                .decoder(SetRolePacket::decode)
                .consumerMainThread(SetRolePacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncRolePacket.class, id++)
                .encoder(SyncRolePacket::encode)
                .decoder(SyncRolePacket::decode)
                .consumerMainThread(SyncRolePacket::handle)
                .add();
    }
}
