package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * BIS-07 Toasted Tornado.
 * A giant fireball slams down and spawns a fire tornado that hunts the nearest living target for ten
 * seconds, lifting, spinning, burning and shredding everything it touches, then it blows up.
 */
public final class InfernoStrike extends StrikeInstance {
    private static final int FALL = 36;
    private static final int SPIN = 200;
    private static final int END = FALL + SPIN + 30;

    private final double height;
    private final double topRadius;
    private final double leash;
    private final double craterR;

    private double tx;
    private double tz;
    private double baseY;
    private double heading;
    private boolean chasing;

    public InfernoStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.height = scale(28.0, 36.0, 46.0);
        this.topRadius = scale(7.0, 9.0, 12.0);
        this.leash = scale(22.0, 30.0, 38.0);
        this.craterR = scale(5.0, 7.0, 9.0);
        this.tx = pos.x;
        this.tz = pos.z;
        this.baseY = pos.y;
    }

    @Override
    protected boolean tick(int t) {
        if (t == 0) {
            baseY = groundY(pos.x, pos.z);
            tx = pos.x;
            tz = pos.z;
            heading = rng.nextDouble() * Math.PI * 2.0;
            announce("\u26A0 TOASTED TORNADO \u26A0", "Biscuit Firestorm - it hunts!", Formatting.RED, 190);
            Fx.sound(world, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GHAST_SHOOT, 12f, 0.4f);
            Fx.sound(world, pos.x, pos.y, pos.z, ModSounds.METEOR_WHOOSH, 10f, 0.75f);
        }
        if (t < FALL) {
            fireball(t);
        } else if (t == FALL) {
            impact();
        }
        int k = t - FALL;
        if (k >= 0 && k < SPIN) {
            tornado(k);
        } else if (k == SPIN) {
            collapse();
        }
        return t >= END;
    }

    private void fireball(int t) {
        double u = (t + 1) / (double) FALL;
        double e = u * u;
        double px = pos.x + 14.0 * (1.0 - e);
        double py = pos.y + 95.0 * (1.0 - e);
        double pz = pos.z + 8.0 * (1.0 - e);
        Fx.fuzz(world, ParticleTypes.FLAME, px, py, pz, 40, 1.5, 1.5, 1.5, 0.03);
        Fx.fuzz(world, ParticleTypes.LAVA, px, py, pz, 6, 1.2, 1.2, 1.2, 0.0);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, px + 1.0, py + 2.0, pz + 0.6, 12, 1.2, 1.2, 1.2, 0.02);
        if (t % 3 == 0) {
            Fx.ring(world, Fx.RED, pos.x, pos.y + 0.3, pos.z, 14.0, 60, t * 0.05);
        }
    }

    private void impact() {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 2, 0.0);
        Fx.burst(world, ParticleTypes.FLAME, x, y + 1.0, z, 240, 0.7);
        Fx.fuzz(world, ParticleTypes.LAVA, x, y + 1.0, z, 40, 0.5, 0.2, 0.5, 0.6);
        Fx.ringOut(world, ParticleTypes.FLAME, x, y + 0.4, z, 2.0, 30, 0.0, 0.7, 0.02);
        Fx.sound(world, x, y, z, ModSounds.FIRE_ROAR, 14f, 0.9f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, 14f, 0.6f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 10f, 0.7f);
        Blast.hurtSphere(this, x, y + 1.0, z, 15.0, 140f, 35f, 2.2, 15);
        BlockQueue.carveSphere(world, x, y - craterR * 0.3, z, craterR, 0.7, 0.0, false, BlockQueue.LOUD, () -> {
            Blast.magmaFloor(world, rng, x, z, craterR * 0.8, 6);
            Blast.scatterFire(world, rng, x, z, craterR + 4.0, 12);
        });
    }

    /** Picks a heading: toward the nearest living target, or a random wander if nobody is around. */
    private void steer() {
        double r = 42.0;
        Box box = new Box(tx - r, baseY - 15.0, tz - r, tx + r, baseY + 30.0, tz + r);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, e -> Blast.canHurt(this, e));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double dx = e.getX() - tx;
            double dz = e.getZ() - tz;
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) {
            heading = Math.atan2(best.getZ() - tz, best.getX() - tx);
            chasing = true;
        } else {
            heading += (rng.nextDouble() - 0.5) * 1.2;
            chasing = false;
        }
    }

    private void tornado(int k) {
        if (k % 8 == 0) {
            steer();
        }
        double speed = chasing ? 0.34 : 0.18;
        tx += Math.cos(heading) * speed;
        tz += Math.sin(heading) * speed;
        double dx = tx - pos.x;
        double dz = tz - pos.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > leash) {
            tx = pos.x + dx / d * leash;
            tz = pos.z + dz / d * leash;
            heading = Math.atan2(-dz, -dx) + (rng.nextDouble() - 0.5);
        }
        if (k % 5 == 0) {
            baseY = groundY(tx, tz);
        }

        render(k);
        grab(k);

        if (k % 4 == 0) {
            Blast.scatterFire(world, rng, tx, tz, 6.0, 3);
        }
        if (k % 50 == 0) {
            Fx.sound(world, tx, baseY + 5.0, tz, ModSounds.FIRE_ROAR, 12f, 1.0f);
        }
        if (k % 8 == 0) {
            Fx.sound(world, tx, baseY + 2.0, tz, SoundEvents.BLOCK_FIRE_AMBIENT, 3f, 0.6f);
        }
    }

    private double radiusAt(double yy) {
        double f = Math.max(0.0, Math.min(1.0, yy / height));
        return 1.1 + Math.pow(f, 1.25) * topRadius;
    }

    private void render(int k) {
        double rot = k * 0.55;
        int levels = (int) (height / 1.8);
        for (int i = 0; i <= levels; i++) {
            double yy = i * 1.8;
            double r = radiusAt(yy);
            for (int a = 0; a < 2; a++) {
                double ang = rot + yy * 0.33 + a * Math.PI;
                double px = tx + Math.cos(ang) * r;
                double pz = tz + Math.sin(ang) * r;
                double py = baseY + yy;
                ParticleEffect p = (i + a) % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME;
                Fx.point(world, p, px, py, pz, -Math.sin(ang) * 0.12, 0.14, Math.cos(ang) * 0.12);
                if (rng.nextInt(7) == 0) {
                    Fx.point(world, ParticleTypes.LAVA, px, py, pz, 0.0, 0.1, 0.0);
                }
            }
        }
        Fx.fuzz(world, ParticleTypes.FLAME, tx, baseY + 2.0, tz, 10, 0.7, 1.5, 0.7, 0.02);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, tx, baseY + height, tz, 10, topRadius * 0.8, 1.5, topRadius * 0.8, 0.02);
    }

    /** Spins everything near the funnel upward, burns it and shreds it. */
    private void grab(int k) {
        double inf = topRadius + 3.5;
        Box box = new Box(tx - inf, baseY - 3.0, tz - inf, tx + inf, baseY + height, tz + inf);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, e -> Blast.canHurt(this, e));
        for (LivingEntity e : list) {
            double dx = e.getX() - tx;
            double dz = e.getZ() - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double reach = radiusAt(e.getY() - baseY) + 3.0;
            if (d > reach + 1.0) {
                continue;
            }
            double nx = d < 0.001 ? 0.0 : dx / d;
            double nz = d < 0.001 ? 0.0 : dz / d;
            double up = e.getVelocity().y < 1.1 ? 0.13 : 0.0;
            Fx.push(e, -nz * 0.16 - nx * 0.04, up, nx * 0.16 - nz * 0.04);
            if (k % 6 == 0) {
                Blast.hurt(this, e, 14f);
                e.setOnFireFor(10);
            }
        }
    }

    private void collapse() {
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, tx, baseY + 3.0, tz, 2, 0.0);
        Fx.burst(world, ParticleTypes.FLAME, tx, baseY + 3.0, tz, 260, 0.9);
        Fx.fuzz(world, ParticleTypes.LAVA, tx, baseY + 3.0, tz, 40, 1.0, 1.0, 1.0, 0.6);
        Fx.ringOut(world, ParticleTypes.FLAME, tx, baseY + 0.6, tz, 3.0, 40, 0.0, 0.8, 0.03);
        Fx.sound(world, tx, baseY, tz, SoundEvents.ENTITY_GENERIC_EXPLODE, 16f, 0.55f);
        Fx.sound(world, tx, baseY, tz, ModSounds.FIRE_ROAR, 14f, 0.7f);
        Blast.hurtSphere(this, tx, baseY + 3.0, tz, 13.0, 160f, 40f, 2.6, 12);
        BlockQueue.carveSphere(world, tx, baseY - 1.0, tz, 3.5, 0.7, 0.0, false, BlockQueue.LOUD, null);
        Blast.scatterFire(world, rng, tx, tz, 8.0, 10);
    }
}
