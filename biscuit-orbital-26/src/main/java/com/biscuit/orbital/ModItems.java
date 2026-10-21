package com.biscuit.orbital;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item UPLINK_REMOTE = register("uplink_remote");

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BiscuitOrbital.id(name));
		Item item = new RemoteItem(new Item.Properties().setId(key).stacksTo(1));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
	}

	private ModItems() {
	}
}
