package com.biscuit.orbital.strike.impl;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Blast;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.strike.StrikeInstance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Heightmap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BIS-06 Frostbite Cookie.
 * An ice comet streaks down, a frost wave erupts ice spikes outward and freezes every living thing it
 * touches, a freezing fog drains them, then everything shatters (frozen targets take massive damage).
 */
public final class CryoStrike extends StrikeInstance {
    private static final int FLIGHT = 54;
    private static final int WAVE = 36;
    private static final int SHATTER = 170;
    private static final int END = 200;

    private static final ParticleEffect ICE_CHUNK =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState());

    private final double zone;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<UUID> frozen = new HashSet<>();
    private int removeCursor;
    private Vec3d lastComet;

    public CryoStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.zone = scale(22.0, 30.0, 42.0);
    }

    @Override
    protected boolean tick(int t) {
        if (t == 0) {
            announce("\u2744 ABSOLUTE ZERO \u2744", "Frostbite Cookie falling - nowhere to hide!", Formatting.AQUA, 200);
            Fx.sound(world, pos.x, pos.y, pos.z, ModSounds.CRYO_INCOMING, 12f, 1.0f);
        }
        if (t < FLIGHT) {
            comet(t);
        } else if (t == FLIGHT) {
            impact();
        }
        if (t >= FLIGHT && t < FLIGHT + WAVE) {
            wave(t - FLIGHT);
        }
        if (t >= FLIGHT + WAVE && t < SHATTER) {
            chill(t);
        }
        if (t == SHATTER) {
            shatter();
        }
        if (t >= SHATTER) {
            removeIce();
        }
        return t >= END && removeCursor >= placed.size();
    }

    private void comet(int t) {
        double u = (t + 1) / (double) FLIGHT;
        double e = u * u;
        Vec3d start = new Vec3d(pos.x - 30.0, pos.y + 125.0, pos.z - 18.0);
        Vec3d p = new Vec3d(start.x + (pos.x - start.x) * e, start.y + (pos.y - start.y) * e, start.z + (pos.z - start.z) * e);
        Fx.fuzz(world, ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 24, 0.9, 0.9, 0.9, 0.05);
        Fx.fuzz(world, Fx.CYAN, p.x, p.y, p.z, 10, 0.7, 0.7, 0.7, 0.0);
        Fx.fuzz(world, ParticleTypes.END_ROD, p.x, p.y, p.z, 6, 0.5, 0.5, 0.5, 0.02);
        Fx.fuzz(world, ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, 6, 0.8, 0.8, 0.8, 0.02);
        if (lastComet != null) {
            Fx.line(world, ParticleTypes.SNOWFLAKE, lastComet, p, 1.2);
        }
        lastComet = p;
        if (t % 3 == 0) {
            Fx.ring(world, Fx.CYAN, pos.x, pos.y + 0.2, pos.z, zone, Math.min(120, (int) (zone * 3)), t * 0.03);
            Fx.ring(world, Fx.WHITE, pos.x, pos.y + 0.2, pos.z, 6.0, 24, -t * 0.05);
        }
    }

    private void impact() {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        Fx.burst(world, ParticleTypes.FLASH, x, y + 1.0, z, 3, 0.0);
        Fx.burst(world, ParticleTypes.SNOWFLAKE, x, y + 1.0, z, 220, 0.8);
        Fx.burst(world, ParticleTypes.CLOUD, x, y + 1.0, z, 120, 0.7);
        Fx.burst(world, ICE_CHUNK, x, y + 1.0, z, 160, 0.6);
        Fx.sound(world, x, y, z, ModSounds.ICE_SHATTER, 14f, 0.7f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, 10f, 0.6f);
        Fx.sound(world, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, 10f, 0.5f);
        Blast.hurtSphere(this, x, y + 1.0, z, 9.0, 150f, 60f, 2.2, 0);
    }

    private void wave(int k) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        double rOuter = zone * (k + 1) / WAVE;
        double rInner = zone * k / WAVE;
        Fx.ringOut(world, ParticleTypes.SNOWFLAKE, x, y + 0.6, z, rOuter, Math.min(120, (int) (rOuter * 3) + 12), 0.0, 0.25, 0.04);
        Fx.ringOut(world, ParticleTypes.CLOUD, x, y + 0.4, z, rOuter, Math.min(90, (int) (rOuter * 2) + 8), 0.3, 0.12, 0.0);

        int spikes = (int) scale(5.0, 7.0, 9.0);
        for (int i = 0; i < spikes; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = rInner + rng.nextDouble() * (rOuter - rInner + 1.0);
            double sx = x + Math.cos(a) * d;
            double sz = z + Math.sin(a) * d;
            raiseSpike(MathHelper.floor(sx), MathHelper.floor(groundY(sx, sz)), MathHelper.floor(sz), d);
        }
        if (k % 2 == 0) {
            Fx.sound(world, x + Math.cos(k) * rOuter, y, z + Math.sin(k) * rOuter,
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 4f, 0.6f);
        }
        freezeInside(rOuter);
        surfaceFreeze(rInner, rOuter);
    }

    private void raiseSpike(int sx, int gy, int sz, double dist) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        double near = 1.0 - Math.min(1.0, dist / zone);
        int h = 4 + rng.nextInt(7 + (int) (near * 12.0));
        int baseR = h >= 9 ? 2 : 1;
        for (int k = 0; k < h; k++) {
            int rr = (int) Math.round(baseR * (1.0 - (double) k / h));
            BlockState st = k < h * 0.35 ? Blocks.PACKED_ICE.getDefaultState()
                    : (k < h * 0.8 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.ICE.getDefaultState());
            int lim = rr + (rr > 1 ? 1 : 0);
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > lim) {
                        continue;
                    }
                    placeIce(sx + dx, gy + k, sz + dz, st);
                }
            }
        }
    }

    private void placeIce(int x, int y, int z, BlockState st) {
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return;
        }
        BlockPos p = new BlockPos(x, y, z);
        if (!Fx.loaded(world, p)) {
            return;
        }
        BlockState cur = world.getBlockState(p);
        if (cur.isAir() || cur.isOf(Blocks.SNOW)) {
            world.setBlockState(p, st, Block.NOTIFY_LISTENERS);
            placed.add(p);
        }
    }

    private void freezeInside(double r) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        Box box = new Box(x - r, y - 10.0, z - r, x + r, y + 40.0, z + r);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, ent -> Blast.canHurt(this, ent));
        for (LivingEntity e : list) {
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            if (frozen.add(e.getUuid())) {
                Fx.fuzz(world, ICE_CHUNK, e.getX(), e.getY() + 1.0, e.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
                Fx.sound(world, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_GLASS_BREAK, 2f, 0.6f);
            }
            applyFreeze(e);
        }
    }

    private void applyFreeze(LivingEntity e) {
        e.setFrozenTicks(240);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
    }

    /** Water turns to ice, bare ground gets a snow cover (both stay after the strike). */
    private void surfaceFreeze(double rInner, double rOuter) {
        for (int i = 0; i < 10; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = rInner + rng.nextDouble() * (rOuter - rInner + 1.0);
            int bx = MathHelper.floor(pos.x + Math.cos(a) * d);
            int bz = MathHelper.floor(pos.z + Math.sin(a) * d);
            if (!Fx.loaded(world, bx, bz)) {
                continue;
            }
            int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz) - 1;
            BlockPos wp = new BlockPos(bx, top, bz);
            BlockState st = world.getBlockState(wp);
            if (st.isOf(Blocks.WATER)) {
                world.setBlockState(wp, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
            } else if (BiscuitConfig.terrainDamage) {
                BlockPos sp = wp.up();
                if (world.getBlockState(sp).isAir() && st.isSideSolidFullSquare(world, wp, Direction.UP)) {
                    world.setBlockState(sp, Blocks.SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private void chill(int t) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        if ((t & 1) == 0) {
            Fx.fuzz(world, ParticleTypes.SNOWFLAKE, x, y + 3.0, z, 50, zone * 0.45, 5.0, zone * 0.45, 0.03);
            Fx.fuzz(world, ParticleTypes.CLOUD, x, y + 0.8, z, 12, zone * 0.5, 0.6, zone * 0.5, 0.01);
        }
        if (t % 10 == 0) {
            for (UUID id : frozen) {
                Entity en = world.getEntity(id);
                if (en instanceof LivingEntity le && le.isAlive() && Blast.canHurt(this, le)) {
                    Blast.hurt(this, le, 9f);
                    applyFreeze(le);
                }
            }
        }
    }

    private void shatter() {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        Fx.sound(world, x, y, z, ModSounds.ICE_SHATTER, 16f, 0.9f);
        Fx.sound(world, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, 12f, 0.5f);
        Fx.sound(world, x, y, z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 12f, 0.5f);
        for (UUID id : frozen) {
            Entity en = world.getEntity(id);
            if (en instanceof LivingEntity le && le.isAlive() && Blast.canHurt(this, le)) {
                Fx.burst(world, ICE_CHUNK, le.getX(), le.getY() + 1.0, le.getZ(), 80, 0.5);
                Blast.hurt(this, le, 220f);
            }
        }
        Fx.fuzz(world, ParticleTypes.SNOWFLAKE, x, y + 5.0, z, 300, zone * 0.5, 6.0, zone * 0.5, 0.4);
        Fx.ringOut(world, ParticleTypes.CLOUD, x, y + 1.0, z, zone * 0.5, 100, 0.0, 0.6, 0.05);
    }

    /** Removes the spikes we placed, a slice per tick, with ice-chip particles. */
    private void removeIce() {
        if (removeCursor >= placed.size()) {
            return;
        }
        int per = Math.max(40, placed.size() / 10 + 1);
        for (int i = 0; i < per && removeCursor < placed.size(); i++) {
            BlockPos p = placed.get(removeCursor++);
            if (!Fx.loaded(world, p)) {
                continue;
            }
            BlockState st = world.getBlockState(p);
            if (st.isOf(Blocks.PACKED_ICE) || st.isOf(Blocks.BLUE_ICE) || st.isOf(Blocks.ICE)) {
                if (rng.nextInt(6) == 0) {
                    Fx.fuzz(world, ICE_CHUNK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.08);
                }
                world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }
}
