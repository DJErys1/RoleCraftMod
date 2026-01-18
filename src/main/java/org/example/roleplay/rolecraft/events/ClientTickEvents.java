package org.example.roleplay.rolecraft.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.roleplay.rolecraft.Rolecraft;

/**
 * Zostawiamy plik, ale bez jakiejkolwiek logiki "work lock".
 * (czas pracy ma być tylko w GUI po stronie serwera)
 */
@Mod.EventBusSubscriber(modid = Rolecraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTickEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        // celowo pusto
    }
}
