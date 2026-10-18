package com.biscuit.orbital.fx;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Heightmap;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Random;

/** Damage + knockback helpers and small terrain decorations (fire, magma). */
public final class Blast {
    private Blast() {
    }

    public static boolean canHurt(StrikeInstance s, LivingEntity e) {
        if (!e.isAlive()) {
            return false;
        }
        if (e instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) {
                return false;
            }
            if (s.safe && p.getUuid().equals(s.casterId)) {
                return false;
            }
        }
        return true;
    }

    public static void hurt(StrikeInstance s, LivingEntity e, float dmg) {
        e.damage(s.damageSource(), dmg * (float) BiscuitConfig.damageMultiplier);
    }

    private static void knockFrom(LivingEntity e, double dx, double dy, double dz, double dist, double k) {
        double len = Math.max(dist, 0.001);
        Fx.push(e, dx / len * k, dy / len * k + 0.25 * k, dz / len * k);
    }

    /** Everything inside the sphere takes damage that falls off from maxDmg (centre) to minDmg (edge). */
    public static int hurtSphere(StrikeInstance s, double cx, double cy, double cz, double r, float maxDmg, float minDmg,
                                 double knock, int igniteSec) {
        Box box = new Box(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
        List<LivingEntity> list = s.world.getEntitiesByClass(LivingEntity.class, box, e -> canHurt(s, e));
        int hit = 0;
        for (LivingEntity e : list) {
            double dx = e.getX() - cx;
            double dy = e.getY() + e.getHeight() * 0.5 - cy;
            double dz = e.getZ() - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > r) {
                continue;
            }
            double f = 1.0 - d / r;
            hurt(s, e, (float) (minDmg + (maxDmg - minDmg) * f));
            if (igniteSec > 0) {
                e.setOnFireFor(igniteSec);
            }
            if (knock > 0 && e.isAlive()) {
                knockFrom(e, dx, dy, dz, d, knock * (0.35 + 0.65 * f));
            }
            hit++;
        }
        return hit;
    }

    /** Hits only entities between rInner and rOuter: used for expanding blast fronts (each entity is hit once). */
    public static int hurtShell(StrikeInstance s, double cx, double cy, double cz, double rInner, double rOuter,
                                double falloffR, float maxDmg, float minDmg, double knock, int igniteSec) {
        Box box = new Box(cx - rOuter, cy - rOuter, cz - rOuter, cx + rOuter, cy + rOuter, cz + rOuter);
        List<LivingEntity> list = s.world.getEntitiesByClass(LivingEntity.class, box, e -> canHurt(s, e));
        int hit = 0;
        for (LivingEntity e : list) {
            double dx = e.getX() - cx;
            double dy = e.getY() + e.getHeight() * 0.5 - cy;
            double dz = e.getZ() - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= rInner || d > rOuter) {
                continue;
            }
            double f = Math.max(0.0, 1.0 - d / falloffR);
            hurt(s, e, (float) (minDmg + (maxDmg - minDmg) * f));
            if (igniteSec > 0 && f > 0.25) {
                e.setOnFireFor(igniteSec);
            }
            if (knock > 0 && e.isAlive()) {
                knockFrom(e, dx, dy, dz, d, knock * (0.3 + 0.7 * f));
            }
            hit++;
        }
        return hit;
    }

    /** Infinite vertical cylinder (sky to void): used by the railgun. */
    public static int hurtColumn(StrikeInstance s, double cx, double cz, double r, float dmg, double knock) {
        ServerWorld w = s.world;
        Box box = new Box(cx - r, w.getBottomY(), cz - r, cx + r, w.getTopY(), cz + r);
        List<LivingEntity> list = w.getEntitiesByClass(LivingEntity.class, box, e -> canHurt(s, e));
        int hit = 0;
        for (LivingEntity e : list) {
            double dx = e.getX() - cx;
            double dz = e.getZ() - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) {
                continue;
            }
            hurt(s, e, dmg);
            if (knock > 0 && e.isAlive()) {
                knockFrom(e, dx, 0.0, dz, d, knock);
            }
            hit++;
        }
        return hit;
    }

    /** Sets random spots inside a disc on fire (only where fire can actually exist). */
    public static void scatterFire(ServerWorld w, Random rng, double cx, double cz, double radius, int count) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rng.nextDouble()) * radius;
            int x = MathHelper.floor(cx + Math.cos(a) * d);
            int z = MathHelper.floor(cz + Math.sin(a) * d);
            if (!Fx.loaded(w, x, z)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            BlockState fire = Blocks.FIRE.getDefaultState();
            if (w.getBlockState(p).isAir() && fire.canPlaceAt(w, p)) {
                w.setBlockState(p, fire);
            }
        }
    }

    /** Turns random surface blocks inside a disc into magma. */
    public static void magmaFloor(ServerWorld w, Random rng, double cx, double cz, double radius, int count) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rng.nextDouble()) * radius;
            int x = MathHelper.floor(cx + Math.cos(a) * d);
            int z = MathHelper.floor(cz + Math.sin(a) * d);
            if (!Fx.loaded(w, x, z)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos p = new BlockPos(x, y, z);
            BlockState st = w.getBlockState(p);
            if (!st.isAir() && st.getHardness(w, p) >= 0.0f && st.getFluidState().isEmpty()) {
                w.setBlockState(p, Blocks.MAGMA_BLOCK.getDefaultState());
            }
        }
    }
}
