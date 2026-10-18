package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * BIS-02 Crumb Carpet.
 * Air-raid siren, a red runway is painted along the caster's facing direction, then three bombers
 * fly down it dropping whistling bombs (each one a crater).
 */
public final class CarpetStrike extends StrikeInstance {
    private static final int WARN = 70;
    private static final int SWEEP = 56;
    private static final int FALL = 28;
    private static final int DROP_EVERY = 3;

    private static final class Bomb {
        final double x;
        final double z;
        final double y0;
        final double gy;
        final int born;

        Bomb(double x, double z, double y0, double gy, int born) {
            this.x = x;
            this.z = z;
            this.y0 = y0;
            this.gy = gy;
            this.born = born;
        }
    }

    private final double length;
    private final double lateral;
    private final double blastR;
    private final double carveR;
    private final double dirX;
    private final double dirZ;
    private final double sideX;
    private final double sideZ;
    private final List<Bomb> bombs = new ArrayList<>();
    private int dropped;

    public CarpetStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.length = scale(64.0, 90.0, 124.0);
        this.lateral = scale(8.0, 11.0, 14.0);
        this.blastR = scale(6.5, 8.0, 10.0);
        this.carveR = scale(3.6, 4.6, 5.8);
        double yr = Math.toRadians(yaw);
        this.dirX = -Math.sin(yr);
        this.dirZ = Math.cos(yr);
        this.sideX = dirZ;
        this.sideZ = -dirX;
    }

    @Override
    protected boolean tick(int t) {
        double half = length * 0.5;
        if (t == 0) {
            announce("\u26A0 AIR RAID \u26A0", "Crumb Carpet inbound - clear the runway!", Formatting.DARK_RED, 190);
            Fx.sound(world, pos, ModSounds.SIREN, 9f, 1.0f);
        }
        if (t < WARN) {
            markRunway(t, half);
        }
        if (t >= WARN && t < WARN + SWEEP) {
            fly(t, half);
        }
        updateBombs(t);
        return t >= WARN + SWEEP + FALL + 4 && bombs.isEmpty();
    }

    private void markRunway(int t, double half) {
        if ((t & 1) != 0) {
            return;
        }
        for (int lane = -1; lane <= 1; lane++) {
            for (double s = -half; s <= half; s += 4.0) {
                double px = pos.x + dirX * s + sideX * lane * lateral;
                double pz = pos.z + dirZ * s + sideZ * lane * lateral;
                Fx.point(world, Fx.RED, px, groundY(px, pz) + 0.3, pz, 0.0, 0.0, 0.0);
            }
        }
        // a moving glow shows the direction of flight
        double s = -half + ((t * 2.2) % length);
        for (int lane = -1; lane <= 1; lane++) {
            double px = pos.x + dirX * s + sideX * lane * lateral;
            double pz = pos.z + dirZ * s + sideZ * lane * lateral;
            Fx.fuzz(world, Fx.GOLD, px, groundY(px, pz) + 0.6, pz, 6, 0.6, 0.3, 0.6, 0.0);
        }
    }

    private void fly(int t, double half) {
        int k = t - WARN;
        double s = -half + length * (k + 1) / (double) SWEEP;
        double alt = pos.y + 62.0;
        if (k == 0) {
            Fx.sound(world, pos.x - dirX * half, alt, pos.z - dirZ * half, SoundEvents.ENTITY_PHANTOM_SWOOP, 12f, 0.5f);
        }
        for (int lane = -1; lane <= 1; lane++) {
            double cx = pos.x + dirX * s + sideX * lane * lateral;
            double cz = pos.z + dirZ * s + sideZ * lane * lateral;
            drawBomber(cx, alt, cz);
            if (k % DROP_EVERY == 0) {
                double bx = cx + (rng.nextDouble() - 0.5) * 5.0;
                double bz = cz + (rng.nextDouble() - 0.5) * 5.0;
                if (dropped++ % 4 == 0) {
                    Fx.sound(world, bx, alt - 2.0, bz, ModSounds.BOMB_WHISTLE, 8f, 0.9f + rng.nextFloat() * 0.25f);
                }
                bombs.add(new Bomb(bx, bz, alt - 2.0, groundY(bx, bz), t));
            }
        }
        if (k % 6 == 0) {
            Fx.sound(world, pos.x + dirX * s, alt, pos.z + dirZ * s, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 10f, 0.55f);
        }
    }

    private void drawBomber(double cx, double alt, double cz) {
        for (int i = -2; i <= 2; i++) {
            Fx.point(world, ParticleTypes.SMOKE, cx + dirX * i * 1.4, alt, cz + dirZ * i * 1.4, 0.0, 0.0, 0.0);
        }
        for (int i = -3; i <= 3; i++) {
            Fx.point(world, ParticleTypes.SMOKE, cx - dirX * 0.5 + sideX * i * 1.6, alt, cz - dirZ * 0.5 + sideZ * i * 1.6,
                    0.0, 0.0, 0.0);
        }
        for (int e = -1; e <= 1; e += 2) {
            Fx.point(world, ParticleTypes.FLAME, cx + sideX * e * 3.2 - dirX, alt, cz + sideZ * e * 3.2 - dirZ,
                    -dirX * 0.2, 0.0, -dirZ * 0.2);
        }
        Fx.fuzz(world, ParticleTypes.CLOUD, cx - dirX * 5.0, alt, cz - dirZ * 5.0, 4, 0.4, 0.2, 0.4, 0.0);
    }

    private void updateBombs(int t) {
        Iterator<Bomb> it = bombs.iterator();
        while (it.hasNext()) {
            Bomb b = it.next();
            int k = t - b.born;
            if (k >= FALL) {
                explode(b);
                it.remove();
                continue;
            }
            double f = (k + 1) / (double) FALL;
            double by = b.y0 - (b.y0 - b.gy) * f * f;
            Fx.fuzz(world, ParticleTypes.FLAME, b.x, by, b.z, 3, 0.15, 0.3, 0.15, 0.01);
            Fx.point(world, ParticleTypes.SMOKE, b.x, by + 1.0, b.z, 0.0, 0.1, 0.0);
            Fx.point(world, Fx.RED, b.x, by, b.z, 0.0, 0.0, 0.0);
        }
    }

    private void explode(Bomb b) {
        double gy = b.gy;
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, b.x, gy + 1.0, b.z, 1, 0.0);
        Fx.fuzz(world, ParticleTypes.FLAME, b.x, gy + 1.0, b.z, 40, 1.4, 1.0, 1.4, 0.12);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, b.x, gy + 2.0, b.z, 18, 1.6, 1.4, 1.6, 0.05);
        Fx.fuzz(world, ParticleTypes.LAVA, b.x, gy + 1.0, b.z, 8, 0.6, 0.4, 0.6, 0.3);
        Fx.sound(world, b.x, gy, b.z, ModSounds.BOMB_BOOM, 9f, 0.85f + rng.nextFloat() * 0.3f);
        Blast.hurtSphere(this, b.x, gy, b.z, blastR, 70f, 25f, 1.5, 4);
        BlockQueue.carveSphere(world, b.x, gy - 0.5, b.z, carveR, 0.7, 0.0, false, BlockQueue.LOUD, null);
        if (rng.nextInt(3) == 0) {
            Blast.scatterFire(world, rng, b.x, b.z, carveR + 2.0, 2);
        }
    }
}
