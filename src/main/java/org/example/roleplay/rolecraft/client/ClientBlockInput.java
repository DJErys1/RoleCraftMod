package org.example.roleplay.rolecraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.example.roleplay.rolecraft.Rolecraft;

/**
 * Celowo puste.
 * Blokowanie akcji podczas "tworzenia" robimy serwerowo w BlacksmithWorkEvents,
 * a czas tworzenia jest wyłącznie w GUI (barrier w slocie wyniku).
 */
@Mod.EventBusSubscriber(modid = Rolecraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientBlockInput {
    // nic
}
