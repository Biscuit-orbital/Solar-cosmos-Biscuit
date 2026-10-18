package com.biscuit.orbital.strike;

import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Static description of one orbital strike (name, menu icon, cooldown) plus its factory. */
public final class StrikeType {
    public interface Factory {
        StrikeInstance create(ServerWorld world, Vec3d pos, UUID caster, int power, boolean safe, float yaw);
    }

    public final String id;
    public final String code;
    public final String name;
    public final Formatting color;
    public final Item icon;
    public final int danger;
    public final int cooldownSeconds;
    public final String[] lore;
    public final Factory factory;

    public StrikeType(String id, String code, String name, Formatting color, Item icon, int danger, int cooldownSeconds,
                      String[] lore, Factory factory) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.danger = danger;
        this.cooldownSeconds = cooldownSeconds;
        this.lore = lore;
        this.factory = factory;
    }
}
