package org.example.roleplay.rolecraft;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.client.SearchScreen;
import org.example.roleplay.rolecraft.commands.ModCommands;
import org.example.roleplay.rolecraft.events.BlacksmithWorkEvents;
import org.example.roleplay.rolecraft.events.MedicEvents;
import org.example.roleplay.rolecraft.events.ModEvents;
import org.example.roleplay.rolecraft.events.PoliceEvents;
import org.example.roleplay.rolecraft.events.Player2x2RestrictEvents;
import org.example.roleplay.rolecraft.events.RolePerkEvents;
import org.example.roleplay.rolecraft.item.ModItems;
import org.example.roleplay.rolecraft.menu.ModMenus;
import org.example.roleplay.rolecraft.network.ModNetwork;
import org.slf4j.Logger;

@Mod(Rolecraft.MODID)
public class Rolecraft {

    public static final String MODID = "rolecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Rolecraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        Config.register(modBus);
        ModNetwork.init();

        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);

        MinecraftForge.EVENT_BUS.register(new ModEvents());
        MinecraftForge.EVENT_BUS.register(new ModCommands());
        MinecraftForge.EVENT_BUS.register(new MedicEvents());
        MinecraftForge.EVENT_BUS.register(new PoliceEvents());
        MinecraftForge.EVENT_BUS.register(new RolePerkEvents());
        MinecraftForge.EVENT_BUS.register(new BlacksmithWorkEvents());
        MinecraftForge.EVENT_BUS.register(new Player2x2RestrictEvents());

        MinecraftForge.EVENT_BUS.addGenericListener(
                net.minecraft.world.entity.Entity.class,
                PlayerRoleProvider::attach
        );

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientOnly::initClient);
    }

    private static class ClientOnly {
        static void initClient() {
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(ClientOnly::onClientSetup);
        }

        private static void onClientSetup(final FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenus.SEARCH_MENU.get(), SearchScreen::new);
                MenuScreens.register(ModMenus.ROLE_ENCHANT_MENU.get(), EnchantmentScreen::new);
            });
        }
    }
}
