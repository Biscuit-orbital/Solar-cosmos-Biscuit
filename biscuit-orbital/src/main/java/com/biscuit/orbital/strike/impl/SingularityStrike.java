package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
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
 * BIS-05 Black Hole Biscuit.
 * A black hole with a spinning accretion disk and polar jets forms high above the target, sinks toward the
 * ground while dragging every entity in and digging a growing hole, then collapses into a supernova shockwave.
 */
public final class SingularityStrike extends StrikeInstance {
    private static final int FORM = 40;
    private static final int COLLAPSE = 180;
    private static final int END = 235;

    private static final ParticleEffect[] DEBRIS = {
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState())
    };

    private final double pullR;
    private final double collapseCarve;
    private double baseY;

    public SingularityStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.pullR = scale(36.0, 50.0, 66.0);
        this.collapseCarve = scale(11.0, 15.0, 19.0);
        this.baseY = pos.y;
    }

    private double coreH(int t) {
        double u = Math.max(0.0, Math.min(1.0, (t - FORM) / (double) (COLLAPSE - FORM)));
        double e = u * u * (3.0 - 2.0 * u);
        return 27.0 + (9.0 - 27.0) * e;
    }

    @Override
    protected boolean tick(int t) {
        if (t == 0) {
            baseY = groundY(pos.x, pos.z);
            announce("\u26A0 SINGULARITY \u26A0", "Black Hole Biscuit forming - RUN!", Formatting.DARK_PURPLE, 200);
            Fx.sound(world, pos.x, baseY, pos.z, ModSounds.SINGULARITY_HUM, 14f, 1.0f);
            Fx.sound(world, pos.x, baseY, pos.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, 6f, 0.5f);
        }
        if (t > 0 && t % 90 == 0 && t < COLLAPSE - 20) {
            Fx.sound(world, pos.x, baseY, pos.z, ModSounds.SINGULARITY_HUM, 14f, 1.0f);
        }
        if (t == COLLAPSE - 48) {
            Fx.sound(world, pos.x, baseY + 9.0, pos.z, ModSounds.SINGULARITY_COLLAPSE, 22f, 1.0f);
        }

        if (t < COLLAPSE) {
            core(t);
            pull(t);
            dig(t);
        } else if (t == COLLAPSE) {
            supernova();
        }
        if (t >= COLLAPSE && t < COLLAPSE + 24) {
            shock(t);
        }
        return t >= END;
    }

    private void core(int t) {
        double cx = pos.x;
        double cy = baseY + coreH(t);
        double cz = pos.z;
        double s = Math.min(1.0, (t + 4) / (double) FORM);

        Fx.fuzz(world, ParticleTypes.SQUID_INK, cx, cy, cz, (int) (16 * s) + 2, 0.9 * s, 0.9 * s, 0.9 * s, 0.0);
        Fx.fuzz(world, ParticleTypes.SMOKE, cx, cy, cz, (int) (6 * s) + 1, 1.1 * s, 1.1 * s, 1.1 * s, 0.0);

        // accretion disk: three spiral arms, blue-hot inside, violet outside
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 9; k++) {
                double rr = (1.8 + k * 0.85) * (0.4 + 0.6 * s);
                double ang = t * (0.5 / Math.sqrt(rr)) + arm * (Math.PI * 2.0 / 3.0) + k * 0.25;
                ParticleEffect pe = k < 3 ? ParticleTypes.SOUL_FIRE_FLAME
                        : (k < 6 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH);
                Fx.point(world, pe, cx + Math.cos(ang) * rr, cy + Math.sin(t * 0.05 + k) * 0.15, cz + Math.sin(ang) * rr,
                        0.0, 0.0, 0.0);
            }
        }
        // polar jets
        Fx.fuzz(world, ParticleTypes.REVERSE_PORTAL, cx, cy + 3.0, cz, 4, 0.3, 2.5, 0.3, 0.03);
        Fx.fuzz(world, ParticleTypes.REVERSE_PORTAL, cx, cy - 3.0, cz, 4, 0.3, 2.5, 0.3, 0.03);

        // chunks of the world streaming into the hole
        for (int i = 0; i < 9; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = 5.0 + rng.nextDouble() * (pullR * 0.55);
            double gx = cx + Math.cos(a) * d;
            double gz = cz + Math.sin(a) * d;
            double gy = groundY(gx, gz);
            Vec3d dir = new Vec3d(cx - gx, cy - gy, cz - gz).normalize();
            Fx.point(world, DEBRIS[rng.nextInt(DEBRIS.length)], gx, gy + 0.2, gz,
                    dir.x * 0.55, dir.y * 0.55 + 0.1, dir.z * 0.55);
        }
    }

    private void pull(int t) {
        double cx = pos.x;
        double cy = baseY + coreH(t);
        double cz = pos.z;
        Box box = new Box(cx - pullR, baseY - 10.0, cz - pullR, cx + pullR, cy + pullR * 0.5, cz + pullR);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, e -> Blast.canHurt(this, e));
        for (LivingEntity e : list) {
            double dx = cx - e.getX();
            double dy = cy - (e.getY() + e.getHeight() * 0.5);
            double dz = cz - e.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > pullR || d < 0.001) {
                continue;
            }
            double f = 0.35 + 1.25 * (1.0 - d / pullR);
            double pull = 0.075 * f;
            Fx.push(e, dx / d * pull, dy / d * pull + 0.03, dz / d * pull);
            if (d < 7.5 && t % 5 == 0) {
                Blast.hurt(this, e, d < 3.0 ? 220f : 30f);
            }
        }
    }

    /** The ground under the hole is slowly dug out, ring by ring. */
    private void dig(int t) {
        if (t < FORM || t % 6 != 0) {
            return;
        }
        double r = 3.0 + (t - FORM) * 0.07;
        BlockQueue.carveSphere(world, pos.x, baseY - 1.0, pos.z, r, 0.8, Math.max(0.0, r - 2.4), false, BlockQueue.LOUD, null);
    }

    private void supernova() {
        double cx = pos.x;
        double cy = baseY + coreH(COLLAPSE);
        double cz = pos.z;
        Fx.burst(world, ParticleTypes.FLASH, cx, cy, cz, 6, 0.0);
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, 0.0);
        Fx.burst(world, ParticleTypes.END_ROD, cx, cy, cz, 260, 1.6);
        Fx.burst(world, ParticleTypes.DRAGON_BREATH, cx, cy, cz, 180, 1.1);
        Fx.burst(world, ParticleTypes.SQUID_INK, cx, cy, cz, 120, 0.9);
        Fx.burst(world, ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 140, 0.8);
        Fx.sound(world, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE, 25f, 0.45f);
        Fx.sound(world, cx, cy, cz, SoundEvents.BLOCK_END_PORTAL_SPAWN, 15f, 0.4f);
        Fx.sound(world, cx, cy, cz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 30f, 0.5f);
        BlockQueue.carveSphere(world, pos.x, baseY - 1.0, pos.z, collapseCarve, 0.9, 0.0, false, BlockQueue.QUIET, null);
    }

    /** Expanding supernova front: every entity is hit once as the wave passes it. */
    private void shock(int t) {
        double cx = pos.x;
        double cy = baseY + coreH(COLLAPSE);
        double cz = pos.z;
        double rad = (t - COLLAPSE + 1) * 2.7;
        double prev = (t - COLLAPSE) * 2.7;
        Blast.hurtShell(this, cx, cy, cz, prev, rad, 62.0, 320f, 45f, 3.4, 10);
        int pts = Math.min(130, (int) (rad * 2.4) + 10);
        Fx.ringOut(world, ParticleTypes.END_ROD, cx, baseY + 1.0, cz, rad, pts, rng.nextDouble(), 0.12, 0.0);
        if ((t & 1) == 0) {
            Fx.ringOut(world, ParticleTypes.DRAGON_BREATH, cx, baseY + 1.5, cz, rad * 0.95, pts / 2, 0.0, 0.1, 0.02);
        }
    }
}
