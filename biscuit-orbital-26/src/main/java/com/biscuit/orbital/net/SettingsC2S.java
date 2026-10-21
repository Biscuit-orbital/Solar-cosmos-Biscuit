package com.biscuit.orbital.net;

import com.biscuit.orbital.BiscuitOrbital;
import com.biscuit.orbital.Settings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettingsC2S(Settings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SettingsC2S> TYPE = new CustomPacketPayload.Type<>(BiscuitOrbital.id("settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsC2S> CODEC = StreamCodec.composite(
			Settings.CODEC, SettingsC2S::settings,
			SettingsC2S::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
