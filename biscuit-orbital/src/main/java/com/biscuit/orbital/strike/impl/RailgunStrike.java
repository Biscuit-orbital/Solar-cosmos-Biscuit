package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.fx.Shapes;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * BIS-01 Biscuit Railgun.
 * Lock-on ring + countdown beeps, a 3.5 second rising charge, then a beam from orbit that kills everything
 * in the column and drills a shaft from the sky down to bedrock.
 */
public final class RailgunStrike extends StrikeInstance {
    private static final int FIRE = 75;
    private static final int END = FIRE + 75;

    private final double radius;

    public RailgunStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.radius = scale(5.5, 8.0, 11.0);
    }

    @Override
    protected boolean tick(int t) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        double top = Math.min(world.getTopY() + 30.0, y + 220.0);

        if (t == 0) {
            announce("\u26A0 ORBITAL RAILGUN \u26A0", "Biscuit Satellite locking on - get out of the circle!", Formatting.WHITE, 170);
            Fx.sound(world, x, y, z, ModSounds.RAILGUN_CHARGE, 10f, 1.0f);
            Fx.sound(world, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, 8f, 0.55f);
        }

        if (t < FIRE) {
            lockOn(t, x, y, z, top);
        } else if (t == FIRE) {
            fire(x, y, z);
        }

        if (t >= FIRE && t < FIRE + 32) {
            double k = (t - FIRE) / 32.0;
            Fx.beam(world, x, z, y - 4.0, top, radius * (1.05 - 0.25 * k), 1.0 - 0.8 * k);
            double rad = 2.0 + (t - FIRE) * 3.4;
            if (rad < 80.0) {
                int pts = Math.min(120, (int) (rad * 2.2) + 8);
                Fx.ringOut(world, ParticleTypes.CLOUD, x, y + 0.6, z, rad, pts, rng.nextDouble(), 0.15, 0.02);
                if ((t & 1) == 0) {
                    Fx.ringOut(world, ParticleTypes.END_ROD, x, y + 0.8, z, rad * 0.92, pts / 2, 0.0, 0.05, 0.0);
                }
            }
        }
        if (t >= FIRE) {
            Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x, y + 3, z, 10, radius * 0.6, 2.5, radius * 0.6, 0.02);
            Fx.fuzz(world, ParticleTypes.LAVA, x, y + 1, z, 4, radius * 0.5, 1.0, radius * 0.5, 0.0);
            Fx.fuzz(world, ParticleTypes.FLAME, x, y + 1, z, 6, radius * 0.6, 1.0, radius * 0.6, 0.04);
        }
        return t >= END;
    }

    private void lockOn(int t, double x, double y, double z, double top) {
        double pulse = 1.0 + 0.05 * Math.sin(t * 0.6);
        double rr = (radius + 1.5) * pulse;
        int pts = (int) Math.min(90, rr * 5);
        Fx.ring(world, Fx.RED, x, y + 0.2, z, rr, pts, 0.0);
        Fx.ring(world, Fx.GOLD, x, y + 0.2, z, rr * 0.55, Math.max(16, pts / 2), t * 0.08);
        for (int k = 0; k < 4; k++) {
            double a = t * 0.12 + k * Math.PI / 2.0;
            double cx = x + Math.cos(a) * (rr + 0.8);
            double cz = z + Math.sin(a) * (rr + 0.8);
            Fx.point(world, ParticleTypes.END_ROD, cx, y + 0.3, cz, 0.0, 0.06, 0.0);
            Fx.fuzz(world, Fx.RED, cx, y + 0.5, cz, 3, 0.2, 0.3, 0.2, 0.0);
        }
        // thin guide beam from orbit
        Fx.beam(world, x, z, y, top, 0.5, 0.35);
        // countdown beeps accelerate toward the shot
        int gap = t < 30 ? 10 : (t < 55 ? 5 : 2);
        if (t % gap == 0) {
            Fx.sound(world, x, y, z, ModSounds.BEEP, 3f, 0.8f + 1.4f * t / FIRE);
        }
        // charge: sparks rush in from the ring while the column swells
        if (t >= 40) {
            double charge = (t - 40) / (double) (FIRE - 40);
            for (int i = 0; i < 12; i++) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                double r = (radius + 4.0) * (1.0 - charge * 0.8);
                Fx.point(world, ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y + 0.5, z + Math.sin(a) * r,
                        -Math.cos(a) * 0.3, 0.12, -Math.sin(a) * 0.3);
            }
            Fx.beam(world, x, z, y, top, radius * (0.1 + 0.55 * charge), 0.5 + charge);
        }
        if (t == FIRE - 5) {
            Fx.fuzz(world, ParticleTypes.FLASH, x, y + 2, z, 3, 1.5, 1.0, 1.5, 0.0);
        }
    }

    private void fire(double x, double y, double z) {
        // vertical column kill: sky to void
        Blast.hurtColumn(this, x, z, radius + 2.0, 900f, 3.0);

        if (BiscuitConfig.terrainDamage) {
            int topOff = (int) Math.min(world.getTopY() - 1 - y, 60.0);
            int botOff = (int) (world.getBottomY() - y);
            BlockPos origin = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
            BlockQueue.carve(world, origin, Shapes.cylinder(radius, topOff, botOff, rng.nextLong()), BlockQueue.QUIET, null);
        }

        Fx.sound(world, x, y, z, ModSounds.RAILGUN_FIRE, 16f, 1.0f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, 14f, 0.45f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 12f, 0.8f);
        Fx.sound(world, x, y, z, SoundEvents.ITEM_TRIDENT_THUNDER, 12f, 0.6f);
        for (int i = 0; i < 3; i++) {
            Fx.burst(world, ParticleTypes.FLASH, x, y + 1.5 + i * 2.0, z, 1, 0.0);
        }
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 3, 0.0);
        Fx.burst(world, ParticleTypes.END_ROD, x, y + 1, z, 220, 1.2);
        Fx.burst(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 160, 1.0);
    }
}
