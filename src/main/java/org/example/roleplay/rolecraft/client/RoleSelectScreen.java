package org.example.roleplay.rolecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.example.roleplay.rolecraft.network.ModNetwork;
import org.example.roleplay.rolecraft.network.SetRolePacket;
import org.example.roleplay.rolecraft.role.RoleType;

public class RoleSelectScreen extends Screen {
    public RoleSelectScreen() {
        super(Component.literal("Wybór roli"));
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 60;

        addRenderableWidget(Button.builder(Component.literal("Drwal"), b -> choose(RoleType.LUMBERJACK))
                .bounds(x, y, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Górnik"), b -> choose(RoleType.MINER))
                .bounds(x, y + 25, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Kowal"), b -> choose(RoleType.BLACKSMITH))
                .bounds(x, y + 50, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Farmer"), b -> choose(RoleType.FARMER))
                .bounds(x, y + 75, w, h).build());
    }

    private void choose(RoleType role) {
        ModNetwork.CHANNEL.sendToServer(new SetRolePacket(role));
        this.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // nie pozwalamy zamknąć bez wyboru (serwer też wymusi)
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        RenderSystem.enableBlend();

        g.drawCenteredString(this.font, "Wybierz rolę (jednorazowo)", this.width / 2, this.height / 2 - 90, 0xFFFFFF);
        g.drawCenteredString(this.font, "Rola determinuje szybkość kopania i umiejętności.", this.width / 2, this.height / 2 - 78, 0xB0B0B0);

        super.render(g, mouseX, mouseY, partialTicks);
    }
}
