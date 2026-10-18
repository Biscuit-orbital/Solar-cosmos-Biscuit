package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * BIS-08 Mega Biscuit (the ultimate).
 * Global siren and countdown, a missile falls from 230 blocks, white-out flash, an expanding fireball that
 * erases everything, a blast wave, a ground shockwave, a rising mushroom cloud and radioactive fallout.
 */
public final class NukeStrike extends StrikeInstance {
    private static final int LAUNCH = 40;
    private static final int IMPACT = 100;
    private static final int END = IMPACT + 320;

    private final double radius;
    private double baseY;

    public NukeStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.radius = scale(20.0, 28.0, 38.0);
        this.baseY = pos.y;
    }

    @Override
    protected boolean tick(int t) {
        if (t == 0) {
            baseY = groundY(pos.x, pos.z);
            announce("\u2622 NUCLEAR LAUNCH DETECTED \u2622", "Impact in 5 seconds - GET AWAY FROM THE TARGET", Formatting.RED, 700);
            Fx.sound(world, pos.x, baseY, pos.z, ModSounds.SIREN, 25f, 1.0f);
        }
        if (t < IMPACT) {
            warn(t);
            if (t >= LAUNCH) {
                missile(t);
            }
        } else if (t == IMPACT) {
            detonate();
        }
        if (t >= IMPACT && t < IMPACT + 26) {
            fireball(t - IMPACT);
        }
        if (t >= IMPACT && t < IMPACT + 22) {
            blastWave(t - IMPACT);
        }
        if (t >= IMPACT && t < IMPACT + 24) {
            shockRing(t - IMPACT);
        }
        if (t >= IMPACT + 5) {
            mushroom(t - IMPACT - 5);
        }
        if (t >= IMPACT + 20 && t < END - 20) {
            fallout(t);
        }
        return t >= END;
    }

    private void warn(int t) {
        if ((t & 1) == 0) {
            Fx.ring(world, Fx.RED, pos.x, baseY + 0.3, pos.z, radius, Math.min(140, (int) (radius * 4)), t * 0.02);
            Fx.ring(world, Fx.GOLD, pos.x, baseY + 0.3, pos.z, radius * 1.9, Math.min(120, (int) (radius * 3)), -t * 0.015);
        }
        int gap = t < 50 ? 10 : (t < 80 ? 5 : 2);
        if (t % gap == 0) {
            Fx.sound(world, pos.x, baseY, pos.z, ModSounds.BEEP, 6f, 0.8f + 1.2f * t / IMPACT);
        }
    }

    private void missile(int t) {
        double u = (t - LAUNCH + 1) / (double) (IMPACT - LAUNCH);
        double e = Math.pow(u, 1.6);
        double y = baseY + 230.0 * (1.0 - e);
        double x = pos.x;
        double z = pos.z;
        if (t == LAUNCH) {
            // a half-speed whistle lasts about as long as the fall
            Fx.sound(world, x, baseY + 120.0, z, ModSounds.BOMB_WHISTLE, 20f, 0.5f);
        }
        Fx.fuzz(world, ParticleTypes.FLAME, x, y, z, 24, 0.5, 0.8, 0.5, 0.03);
        Fx.fuzz(world, ParticleTypes.END_ROD, x, y, z, 10, 0.3, 0.6, 0.3, 0.05);
        Fx.fuzz(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 4.0, z, 14, 0.6, 3.0, 0.6, 0.01);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x, y + 9.0, z, 10, 1.0, 4.0, 1.0, 0.01);
    }

    private void detonate() {
        double x = pos.x;
        double z = pos.z;
        double cy = baseY + radius * 0.4;

        Fx.sound(world, x, baseY, z, ModSounds.NUKE_BOOM, 45f, 1.0f);
        Fx.sound(world, x, baseY, z, SoundEvents.ENTITY_GENERIC_EXPLODE, 30f, 0.4f);
        Fx.sound(world, x, baseY, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 30f, 0.45f);
        Fx.sound(world, x, baseY, z, SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 20f, 0.5f);

        for (int i = 0; i < 8; i++) {
            Fx.burst(world, ParticleTypes.FLASH,
                    x + (rng.nextDouble() - 0.5) * radius * 0.6,
                    baseY + 2.0 + rng.nextDouble() * radius * 0.5,
                    z + (rng.nextDouble() - 0.5) * radius * 0.6, 3, 0.0);
        }
        Fx.fuzz(world, ParticleTypes.FLASH, x, cy, z, 40, radius * 0.35, radius * 0.25, radius * 0.35, 0.0);
        Fx.fuzz(world, ParticleTypes.EXPLOSION_EMITTER, x, baseY + 3.0, z, 10, radius * 0.3, 2.0, radius * 0.3, 0.0);

        // flash blindness for everyone who can see it, nausea for the ones near
        double near = radius * 2.5;
        for (ServerPlayerEntity p : world.getPlayers()) {
            double d2 = p.squaredDistanceTo(x, baseY, z);
            if (d2 < 110.0 * 110.0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 90, 0));
            }
            if (d2 < near * near) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            }
        }

        Blast.hurtSphere(this, x, baseY + 1.0, z, radius * 0.55, 3000f, 3000f, 0.0, 25);
        BlockQueue.carveSphere(world, x, baseY, z, radius, 0.8, 0.0, false, BlockQueue.QUIET, null);
    }

    /** Expanding fireball shell (upper hemisphere) plus a glowing, smoking core. */
    private void fireball(int k) {
        double x = pos.x;
        double z = pos.z;
        double f = radius * (k + 1) / 26.0;
        for (int i = 0; i < 26; i++) {
            double nx = rng.nextGaussian();
            double ny = Math.abs(rng.nextGaussian());
            double nz = rng.nextGaussian();
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-6) {
                continue;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            ParticleEffect pe = (i % 3 == 0) ? ParticleTypes.LAVA : ParticleTypes.FLAME;
            Fx.fuzz(world, pe, x + nx * f, baseY + 1.0 + ny * f * 0.9, z + nz * f, 4, 1.4, 1.4, 1.4, 0.1);
        }
        Fx.fuzz(world, ParticleTypes.FLAME, x, baseY + f * 0.4, z, 20, f * 0.45, f * 0.3, f * 0.45, 0.02);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x, baseY + f * 0.6, z, 14, f * 0.5, f * 0.4, f * 0.5, 0.04);
        Blast.hurtSphere(this, x, baseY, z, f, 3000f, 3000f, 0.0, 25);
    }

    /** The pressure wave: every entity is hit exactly once as the front passes it. */
    private void blastWave(int k) {
        double outer = radius * 1.9;
        double front = (k + 1) * outer / 22.0;
        double back = k * outer / 22.0;
        Blast.hurtShell(this, pos.x, baseY, pos.z, back, front, outer, 260f, 30f, 3.0, front < radius * 1.4 ? 8 : 0);
    }

    private void shockRing(int k) {
        double rad = (k + 1) * 3.2;
        if (rad > radius * 2.6) {
            return;
        }
        Fx.ringOut(world, ParticleTypes.CLOUD, pos.x, baseY + 1.0, pos.z, rad, Math.min(150, (int) (rad * 2.4) + 10),
                rng.nextDouble(), 0.35, 0.03);
        Fx.ringOut(world, ParticleTypes.LARGE_SMOKE, pos.x, baseY + 0.5, pos.z, rad * 0.97, Math.min(110, (int) (rad * 1.6) + 8),
                0.0, 0.2, 0.02);
    }

    /** Stem rising from the crater, topped by a rolling torus of fire and smoke. */
    private void mushroom(int m) {
        double x = pos.x;
        double z = pos.z;
        double rise = Math.min(1.0, m / 70.0);
        double hs = radius * 2.5 * rise;

        for (double yy = 0.0; yy < hs; yy += 3.5) {
            double rs = radius * 0.17 + 1.0 + (1.0 - yy / Math.max(hs, 1.0)) * radius * 0.1;
            Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x, baseY + yy, z, 5, rs * 0.55, 1.6, rs * 0.55, 0.01);
            if (yy < hs * 0.6 && m < 200) {
                Fx.fuzz(world, ParticleTypes.FLAME, x, baseY + yy, z, 4, rs * 0.4, 1.4, rs * 0.4, 0.01);
            }
        }

        if (m > 200 && rng.nextInt(3) != 0) {
            return;
        }
        double bigR = radius * 0.78 * Math.min(1.0, m / 60.0);
        double tube = radius * 0.34 * Math.min(1.0, m / 50.0);
        double cy = baseY + hs;
        for (int i = 0; i < 18; i++) {
            double phi = rng.nextDouble() * Math.PI * 2.0;
            double psi = rng.nextDouble() * Math.PI * 2.0;
            double ring = bigR + tube * Math.cos(psi);
            double px = x + ring * Math.cos(phi);
            double pz = z + ring * Math.sin(phi);
            double py = cy + tube * 0.85 * Math.sin(psi);
            ParticleEffect pe;
            if (Math.sin(psi) < 0.0 && m < 130) {
                pe = rng.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LAVA;
            } else {
                pe = Math.cos(psi) > 0.4 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD;
            }
            Fx.fuzz(world, pe, px, py, pz, 4, tube * 0.22, tube * 0.22, tube * 0.22, 0.01);
        }
        Fx.fuzz(world, ParticleTypes.CLOUD, x, cy + tube * 0.8, z, 14, bigR * 0.55, 1.8, bigR * 0.55, 0.0);
    }

    /** Green radioactive mist; everything living inside keeps getting withered. */
    private void fallout(int t) {
        double x = pos.x;
        double z = pos.z;
        double r = radius * 1.25;
        if (t % 3 == 0) {
            Fx.fuzz(world, ParticleTypes.SPORE_BLOSSOM_AIR, x, baseY + 2.0, z, 40, r * 0.7, 3.0, r * 0.7, 0.0);
            Fx.fuzz(world, Fx.GREEN, x, baseY + 3.0, z, 10, r * 0.6, 3.0, r * 0.6, 0.0);
        }
        if (t % 20 == 0) {
            Box box = new Box(x - r, baseY - r, z - r, x + r, baseY + r, z + r);
            List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, ent -> Blast.canHurt(this, ent));
            for (LivingEntity e : list) {
                double dx = e.getX() - x;
                double dz = e.getZ() - z;
                if (dx * dx + dz * dz <= r * r) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 120, 1));
                }
            }
        }
    }
}
