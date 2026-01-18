package org.example.roleplay.rolecraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

public class UIUtil {

    private UIUtil() {}

    public static void actionBar(Player p, Component msg) {
        p.displayClientMessage(msg, true);
    }

    /**
     * Ładny pasek:
     * ▰ = wypełnione, ▱ = puste
     * Kolory: zielony -> żółty -> czerwony (dla klimatu “progressu”)
     */
    public static MutableComponent prettyProgress(String label, Component targetName, int pct) {
        int total = 14; // długość paska
        int filled = (int) Math.round((pct / 100.0) * total);
        if (filled < 0) filled = 0;
        if (filled > total) filled = total;

        ChatFormatting fillColor =
                (pct >= 70) ? ChatFormatting.GREEN :
                        (pct >= 35) ? ChatFormatting.YELLOW :
                                ChatFormatting.RED;

        MutableComponent bar = Component.literal("");

        for (int i = 0; i < filled; i++) {
            bar.append(Component.literal("▰").withStyle(fillColor));
        }
        for (int i = filled; i < total; i++) {
            bar.append(Component.literal("▱").withStyle(ChatFormatting.DARK_GRAY));
        }

        MutableComponent msg = Component.literal("")
                .append(Component.literal(label).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" "))
                .append(targetName.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  "))
                .append(bar)
                .append(Component.literal(" "))
                .append(Component.literal(pct + "%").withStyle(ChatFormatting.GRAY));

        return msg;
    }

    public static MutableComponent holdShiftLine() {
        return Component.literal("")
                .append(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" for details").withStyle(ChatFormatting.DARK_GRAY));
    }
}
