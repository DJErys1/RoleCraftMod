package org.example.roleplay.rolecraft.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.roleplay.rolecraft.Rolecraft;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Rolecraft.MODID);

    public static final RegistryObject<Item> BANDAGE =
            ITEMS.register("bandage", () -> new BandageItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> MEDKIT =
            ITEMS.register("medkit", () -> new MedkitItem(new Item.Properties().stacksTo(1).durability(10)));

    public static final RegistryObject<Item> HANDCUFFS =
            ITEMS.register("handcuffs", () -> new HandcuffsItem(new Item.Properties().stacksTo(1)));
}
