package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemRegistering {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AmourDePommeDeTerre.MODID);

    public static final RegistryObject<Item> DBG_ITEM = ITEMS.register("debug_item", () -> new ItemList(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1)));

    public void register() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
