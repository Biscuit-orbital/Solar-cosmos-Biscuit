package com.biscuit.orbital.net;

import com.biscuit.orbital.BiscuitOrbital;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CinematicS2C(BlockPos target, int radius, float speed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CinematicS2C> TYPE = new CustomPacketPayload.Type<>(BiscuitOrbital.id("cinematic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CinematicS2C> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, CinematicS2C::target,
			ByteBufCodecs.INT, CinematicS2C::radius,
			ByteBufCodecs.FLOAT, CinematicS2C::speed,
			CinematicS2C::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
