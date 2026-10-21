package com.biscuit.orbital;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-player strike settings. damage == 0 means infinite. */
public record Settings(int radius, int damage, float speed, boolean cutscenes, boolean everyone) {
	public static final Settings DEFAULT = new Settings(96, 0, 1.0F, true, true);

	public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Settings::radius,
			ByteBufCodecs.INT, Settings::damage,
			ByteBufCodecs.FLOAT, Settings::speed,
			ByteBufCodecs.BOOL, Settings::cutscenes,
			ByteBufCodecs.BOOL, Settings::everyone,
			Settings::new);

	public Settings clamped() {
		return new Settings(
				Math.max(16, Math.min(512, radius)),
				Math.max(0, Math.min(100, damage)),
				Math.max(0.5F, Math.min(3.0F, speed)),
				cutscenes, everyone);
	}

	public Settings withRadius(int v) { return new Settings(v, damage, speed, cutscenes, everyone).clamped(); }
	public Settings withDamage(int v) { return new Settings(radius, v, speed, cutscenes, everyone).clamped(); }
	public Settings withSpeed(float v) { return new Settings(radius, damage, v, cutscenes, everyone).clamped(); }
	public Settings withCutscenes(boolean v) { return new Settings(radius, damage, speed, v, everyone); }
	public Settings withEveryone(boolean v) { return new Settings(radius, damage, speed, cutscenes, v); }
}
