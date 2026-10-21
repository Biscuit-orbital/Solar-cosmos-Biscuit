package com.biscuit.orbital;

import com.biscuit.orbital.net.CinematicS2C;
import com.biscuit.orbital.net.OpenMenuS2C;
import com.biscuit.orbital.net.SettingsC2S;
import com.biscuit.orbital.server.StrikeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiscuitOrbital implements ModInitializer {
	public static final String MOD_ID = "biscuit_orbital";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.init();
		ModSounds.init();

		PayloadTypeRegistry.clientboundPlay().register(CinematicS2C.TYPE, CinematicS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenMenuS2C.TYPE, OpenMenuS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SettingsC2S.TYPE, SettingsC2S.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SettingsC2S.TYPE, (payload, context) ->
				StrikeManager.setSettings(context.player().getUUID(), payload.settings()));

		StrikeManager.init();
		LOGGER.info("Biscuit Orbital Command online.");
	}
}
