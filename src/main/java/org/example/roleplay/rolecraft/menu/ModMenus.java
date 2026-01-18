package org.example.roleplay.rolecraft.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.roleplay.rolecraft.Rolecraft;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Rolecraft.MODID);

    public static final RegistryObject<MenuType<SearchMenu>> SEARCH_MENU =
            MENUS.register("search_menu", () -> IForgeMenuType.create(SearchMenu::new));

    public static final RegistryObject<MenuType<RoleEnchantMenu>> ROLE_ENCHANT_MENU =
            MENUS.register("role_enchant_menu", () -> IForgeMenuType.create(RoleEnchantMenu::fromNetwork));
}
