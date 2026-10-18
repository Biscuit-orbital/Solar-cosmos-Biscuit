package com.biscuit.orbital;

import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.item.UplinkItem;
import com.biscuit.orbital.strike.StrikeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Biscuit Orbital Command - entry point.
 * Registers the Biscuit Uplink, the sounds, the commands and the per-tick strike / terrain engines.
 */
public class BiscuitOrbital implements ModInitializer {
    public static final String MOD_ID = "biscuit_orbital";
    public static final Logger LOGGER = LoggerFactory.getLogger("BiscuitOrbital");

    /** Custom damage type (data/biscuit_orbital/damage_type/orbital_strike.json): bypasses armor, custom death message. */
    public static final RegistryKey<DamageType> ORBITAL_DAMAGE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(MOD_ID, "orbital_strike"));

    public static Item UPLINK;

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        BiscuitConfig.load();
        ModSounds.init();

        UPLINK = Registry.register(Registries.ITEM, id("biscuit_uplink"),
                new UplinkItem(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)));

        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(UPLINK))
                .displayName(Text.translatable("itemGroup." + MOD_ID))
                .entries((context, entries) -> entries.add(UPLINK))
                .build();
        Registry.register(Registries.ITEM_GROUP, id("main"), group);

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            StrikeManager.tick(world);
            BlockQueue.tick(world);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StrikeManager.clear();
            BlockQueue.clear();
            UplinkItem.clearCooldowns();
        });
        CommandRegistrationCallback.EVENT.register(BiscuitCommands::register);

        LOGGER.info("Biscuit Orbital Command online - 8 orbital strikes armed.");
    }
}
