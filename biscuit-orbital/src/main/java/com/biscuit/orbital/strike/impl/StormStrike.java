package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.BlockQueue;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;

/**
 * BIS-04 Thunder Wafer.
 * A rotating black thundercloud forms 44 blocks up, three spiral arms of lightning sweep outward (every bolt
 * chains to the previous one, sand fuses into glass), then a final judgement bolt hits the centre.
 */
public final class StormStrike extends StrikeInstance {
    private static final int BOLTS_FROM = 60;
    private static final int BOLTS_TO = 150;
    private static final int FINAL_AT = 165;
    private static final int END = 195;

    private final double zone;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;

    public StormStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.zone = scale(24.0, 33.0, 44.0);
    }

    @Override
    protected boolean tick(int t) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        double cloudY = y + 44.0;

        if (t == 0) {
            announce("\u26A0 THUNDER WAFER \u26A0", "The sky is charging - move!", Formatting.YELLOW, 190);
            Fx.sound(world, x, y, z, ModSounds.STORM_CHARGE, 12f, 1.0f);
        }

        // the thundercloud
        if (t < END - 10) {
            double ang = t * 0.09;
            for (int k = 0; k < 8; k++) {
                double a = ang + k * Math.PI * 2.0 / 8.0;
                double r = zone * (k % 2 == 0 ? 0.62 : 0.28);
                Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * r, cloudY + (k % 3), z + Math.sin(a) * r,
                        5, 4.5, 1.4, 4.5, 0.0);
            }
            Fx.fuzz(world, ParticleTypes.SQUID_INK, x, cloudY, z, 6, zone * 0.4, 1.0, zone * 0.4, 0.0);
            if (rng.nextInt(9) == 0) {
                double fx = x + (rng.nextDouble() - 0.5) * zone;
                double fz = z + (rng.nextDouble() - 0.5) * zone;
                Fx.fuzz(world, ParticleTypes.FLASH, fx, cloudY, fz, 1, 0.5, 0.5, 0.5, 0.0);
                Fx.fuzz(world, ParticleTypes.ELECTRIC_SPARK, fx, cloudY, fz, 24, 5.0, 1.0, 5.0, 0.25);
            }
        }

        if (t < BOLTS_FROM && (t & 1) == 0) {
            Fx.ring(world, Fx.WHITE, x, y + 0.3, z, zone, Math.min(120, (int) (zone * 3)), t * 0.03);
            Fx.fuzz(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 8, zone * 0.4, 1.0, zone * 0.4, 0.25);
        }
        if (t >= BOLTS_FROM && t <= BOLTS_TO) {
            spiral(t, cloudY);
        }
        if (t == FINAL_AT) {
            judgement(cloudY);
        }
        if (t >= FINAL_AT && t < FINAL_AT + 16) {
            double rad = Math.min(zone, (t - FINAL_AT + 1) * zone / 14.0);
            Fx.ringOut(world, ParticleTypes.ELECTRIC_SPARK, x, y + 0.8, z, rad, Math.min(120, (int) (rad * 3) + 10), 0.0, 0.3, 0.05);
        }
        return t >= END;
    }

    private void spiral(int t, double cloudY) {
        double u = (t - BOLTS_FROM) / (double) (BOLTS_TO - BOLTS_FROM);
        double r = 3.0 + (zone - 3.0) * u;
        for (int arm = 0; arm < 3; arm++) {
            if ((t + arm) % 3 != 0) {
                continue;
            }
            double a = t * 0.27 + arm * (Math.PI * 2.0 / 3.0);
            double bx = pos.x + Math.cos(a) * r + (rng.nextDouble() - 0.5) * 3.0;
            double bz = pos.z + Math.sin(a) * r + (rng.nextDouble() - 0.5) * 3.0;
            strikeAt(bx, bz, cloudY, rng.nextInt(3) == 0);
        }
    }

    private void strikeAt(double bx, double bz, double cloudY, boolean real) {
        double gy = groundY(bx, bz);
        long seed = rng.nextLong();
        Vec3d top = new Vec3d(bx + (rng.nextDouble() - 0.5) * 6.0, cloudY, bz + (rng.nextDouble() - 0.5) * 6.0);
        Vec3d bottom = new Vec3d(bx, gy, bz);
        Fx.bolt(world, ParticleTypes.ELECTRIC_SPARK, top, bottom, 14, 2.2, new Random(seed));
        Fx.bolt(world, Fx.WHITE, top, bottom, 14, 2.2, new Random(seed));
        Fx.fuzz(world, ParticleTypes.ELECTRIC_SPARK, bx, gy + 0.5, bz, 40, 0.6, 0.4, 0.6, 0.35);
        Fx.burst(world, ParticleTypes.FLASH, bx, gy + 1.0, bz, 1, 0.0);
        if (hasLast) {
            Fx.arc(world, ParticleTypes.ELECTRIC_SPARK, new Vec3d(lastX, lastY + 0.4, lastZ), new Vec3d(bx, gy + 0.4, bz),
                    8, 1.2, 1.2, 1.2, rng);
        }
        hasLast = true;
        lastX = bx;
        lastY = gy;
        lastZ = bz;

        Blast.hurtSphere(this, bx, gy + 1.0, bz, 3.6, 38f, 18f, 0.7, 3);
        fuse(bx, gy - 1.0, bz);
        if (real) {
            spawnLightning(bx, gy, bz);
        } else {
            Fx.sound(world, bx, gy, bz, ModSounds.THUNDER_CRACK, 7f, 0.9f + rng.nextFloat() * 0.3f);
        }
    }

    private void judgement(double cloudY) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        for (int i = 0; i < 6; i++) {
            double jx = x + (rng.nextDouble() - 0.5) * 3.0;
            double jz = z + (rng.nextDouble() - 0.5) * 3.0;
            double gy = groundY(jx, jz);
            long seed = rng.nextLong();
            Vec3d top = new Vec3d(jx, cloudY + 20.0, jz);
            Vec3d bottom = new Vec3d(jx, gy, jz);
            Fx.bolt(world, ParticleTypes.ELECTRIC_SPARK, top, bottom, 18, 1.5, new Random(seed));
            Fx.bolt(world, Fx.WHITE, top, bottom, 18, 1.5, new Random(seed));
            if (i < 4) {
                spawnLightning(jx, gy, jz);
            }
        }
        for (int i = 0; i < 5; i++) {
            Fx.burst(world, ParticleTypes.FLASH, x, y + 2.0 + i * 6.0, z, 1, 0.0);
        }
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 2, 0.0);
        Fx.burst(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 300, 1.4);
        Fx.sound(world, x, y, z, ModSounds.THUNDER_CRACK, 22f, 0.65f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 30f, 0.6f);
        Fx.sound(world, x, y, z, SoundEvents.ITEM_TRIDENT_THUNDER, 12f, 0.7f);
        Blast.hurtSphere(this, x, y + 1.0, z, 11.0, 260f, 90f, 2.5, 8);
        BlockQueue.carveSphere(world, x, y, z, 3.5, 0.8, 0.0, false, BlockQueue.LOUD, null);
    }

    private void spawnLightning(double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) {
            return;
        }
        bolt.setPosition(x, y, z);
        world.spawnEntity(bolt);
    }

    /** Lightning fuses sand into glass. */
    private void fuse(double x, double y, double z) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        BlockPos p = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        if (!Fx.loaded(world, p)) {
            return;
        }
        BlockState st = world.getBlockState(p);
        if (st.isOf(Blocks.SAND) || st.isOf(Blocks.RED_SAND)) {
            world.setBlockState(p, Blocks.GLASS.getDefaultState());
        }
    }
}
