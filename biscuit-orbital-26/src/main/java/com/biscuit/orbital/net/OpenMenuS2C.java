package com.biscuit.orbital.net;

import com.biscuit.orbital.BiscuitOrbital;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenMenuS2C() implements CustomPacketPayload {
	public static final OpenMenuS2C INSTANCE = new OpenMenuS2C();
	public static final CustomPacketPayload.Type<OpenMenuS2C> TYPE = new CustomPacketPayload.Type<>(BiscuitOrbital.id("open_menu"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuS2C> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
