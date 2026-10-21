package com.biscuit.orbital.client;

import com.biscuit.orbital.BiscuitOrbital;
import com.biscuit.orbital.net.CinematicS2C;
import com.biscuit.orbital.net.OpenMenuS2C;
import com.biscuit.orbital.net.SettingsC2S;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;

public class BiscuitOrbitalClient implements ClientModInitializer {
	public static KeyMapping openMenuKey;

	@Override
	public void onInitializeClient() {
		ClientSettings.load();

		KeyMapping.Category category = KeyMapping.Category.register(BiscuitOrbital.id("main"));
		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.biscuit_orbital.open_menu", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				if (client.player != null) {
					client.gui.setScreen(new MenuScreen());
				}
			}
			Cinematic.tick(client);
		});

		HudElementRegistry.addLast(BiscuitOrbital.id("cinematic"), Cinematic::extract);

		ClientPlayNetworking.registerGlobalReceiver(CinematicS2C.TYPE, (payload, context) ->
				context.client().execute(() -> Cinematic.start(payload)));
		ClientPlayNetworking.registerGlobalReceiver(OpenMenuS2C.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new MenuScreen())));

		// Tell the server our saved settings as soon as we join a world.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new SettingsC2S(ClientSettings.current)));
	}
}
