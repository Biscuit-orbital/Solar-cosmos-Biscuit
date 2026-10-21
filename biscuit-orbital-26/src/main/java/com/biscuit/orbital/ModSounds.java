package com.biscuit.orbital;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent LOCK_BEEP = register("lock_beep");
	public static final SoundEvent RISER = register("riser");
	public static final SoundEvent CHARGE = register("charge");
	public static final SoundEvent FIRE = register("fire");
	public static final SoundEvent IMPACT = register("impact");
	public static final SoundEvent RUMBLE = register("rumble");

	private static SoundEvent register(String name) {
		Identifier id = BiscuitOrbital.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}

	private ModSounds() {
	}
}
