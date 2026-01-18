package org.example.roleplay.rolecraft.client;

import net.minecraft.client.Minecraft;

public class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void openRoleSelectScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new RoleSelectScreen());
        }
    }
}
