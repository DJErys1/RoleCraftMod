package org.example.roleplay.rolecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.example.roleplay.rolecraft.util.UIUtil;

import javax.annotation.Nullable;
import java.util.List;

public class HandcuffsItem extends Item {

    public HandcuffsItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rolecraft.tooltip.handcuffs.title").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("rolecraft.tooltip.handcuffs.line1").withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.empty());
        tooltip.add(UIUtil.holdShiftLine());

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("rolecraft.tooltip.handcuffs.shift1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("rolecraft.tooltip.handcuffs.shift2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("rolecraft.tooltip.handcuffs.shift3").withStyle(ChatFormatting.GRAY));
        }
    }
}
