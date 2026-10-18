package com.biscuit.orbital;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Custom sound events. The audio itself lives in assets/biscuit_orbital/sounds/*.ogg (see sounds.json). */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent UPLINK_OPEN = reg("uplink_open");
    public static final SoundEvent UPLINK_LOCK = reg("uplink_lock");
    public static final SoundEvent BEEP = reg("beep");
    public static final SoundEvent RAILGUN_CHARGE = reg("railgun_charge");
    public static final SoundEvent RAILGUN_FIRE = reg("railgun_fire");
    public static final SoundEvent SIREN = reg("siren");
    public static final SoundEvent BOMB_WHISTLE = reg("bomb_whistle");
    public static final SoundEvent BOMB_BOOM = reg("bomb_boom");
    public static final SoundEvent METEOR_WHOOSH = reg("meteor_whoosh");
    public static final SoundEvent METEOR_IMPACT = reg("meteor_impact");
    public static final SoundEvent STORM_CHARGE = reg("storm_charge");
    public static final SoundEvent THUNDER_CRACK = reg("thunder_crack");
    public static final SoundEvent SINGULARITY_HUM = reg("singularity_hum");
    public static final SoundEvent SINGULARITY_COLLAPSE = reg("singularity_collapse");
    public static final SoundEvent CRYO_INCOMING = reg("cryo_incoming");
    public static final SoundEvent ICE_SHATTER = reg("ice_shatter");
    public static final SoundEvent FIRE_ROAR = reg("fire_roar");
    public static final SoundEvent NUKE_BOOM = reg("nuke_boom");

    private static SoundEvent reg(String name) {
        Identifier id = new Identifier(BiscuitOrbital.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Touching this class registers every sound. */
    public static void init() {
    }
}
