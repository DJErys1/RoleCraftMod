package org.example.roleplay.rolecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.example.roleplay.rolecraft.client.ClientRoleCache;
import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

import java.util.function.Supplier;

public class SyncRolePacket {

    public final String role;
    public final String job;
    public final int roleXp;
    public final int roleLevel;

    public SyncRolePacket(RoleType role, JobType job, int roleXp, int roleLevel) {
        this.role = (role == null ? "NONE" : role.name());
        this.job = (job == null ? "NONE" : job.name());
        this.roleXp = roleXp;
        this.roleLevel = roleLevel;
    }

    public SyncRolePacket(String role, String job, int roleXp, int roleLevel) {
        this.role = role;
        this.job = job;
        this.roleXp = roleXp;
        this.roleLevel = roleLevel;
    }

    public static void encode(SyncRolePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role);
        buf.writeUtf(msg.job);
        buf.writeInt(msg.roleXp);
        buf.writeInt(msg.roleLevel);
    }

    public static SyncRolePacket decode(FriendlyByteBuf buf) {
        return new SyncRolePacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(SyncRolePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientRoleCache.ROLE = RoleType.fromString(msg.role);
            ClientRoleCache.JOB = JobType.fromString(msg.job);
            ClientRoleCache.ROLE_XP = msg.roleXp;
            ClientRoleCache.ROLE_LEVEL = msg.roleLevel;
        });
        ctx.get().setPacketHandled(true);
    }
}
