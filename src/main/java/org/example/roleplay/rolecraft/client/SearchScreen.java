package org.example.roleplay.rolecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.example.roleplay.rolecraft.Rolecraft;
import org.example.roleplay.rolecraft.menu.SearchMenu;

public class SearchScreen extends AbstractContainerScreen<SearchMenu> {

    private static final ResourceLocation TEX =
            new ResourceLocation(Rolecraft.MODID, "textures/gui/search.png");

    public SearchScreen(SearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Jeśli nie chcesz robić customowej tekstury teraz,
        // to możesz zostawić to na później i użyć vanilla tła:
        // g.blit(new ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), ...)

        // Na start robimy prosto: zwykły panel z MC (bez custom png):
        g.blit(new ResourceLocation("minecraft", "textures/gui/container/generic_54.png"),
                leftPos, topPos, 0, 0, 176, 222);
    }
}
