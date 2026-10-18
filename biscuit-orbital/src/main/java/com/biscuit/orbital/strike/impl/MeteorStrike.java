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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * BIS-03 Choco-Chip Meteors.
 * A red zone appears, then burning meteors scream in from the sky (some hunt living targets, some burn
 * blue), and a huge mother meteor lands on the target at the end.
 */
public final class MeteorStrike extends StrikeInstance {
    private static final int START = 50;
    private static final int STOP = 175;
    private static final int MOTHER_AT = 185;
    private static final double DX = -0.45;
    private static final double DY = -1.0;
    private static final double DZ = -0.30;

    private static final ParticleEffect CHUNK = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.getDefaultState());

    private static final class Meteor {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        double tx;
        double ty;
        double tz;
        boolean blue;
        boolean mother;
    }

    private final double zone;
    private final double craterR;
    private final double blastR;
    private final int spawnEvery;
    private final List<Meteor> meteors = new ArrayList<>();
    private boolean motherSpawned;

    public MeteorStrike(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        super(world, pos, casterId, power, safe, yaw);
        this.zone = scale(26.0, 36.0, 48.0);
        this.craterR = scale(3.6, 4.4, 5.2);
        this.blastR = scale(7.0, 8.5, 10.0);
        this.spawnEvery = (int) scale(4.0, 3.0, 2.0);
    }

    @Override
    protected boolean tick(int t) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        if (t == 0) {
            announce("\u2604 METEOR STORM", "Choco-Chip Meteors incoming - find cover!", Formatting.GOLD, 200);
            Fx.sound(world, x, y, z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 12f, 0.5f);
        }
        if (t < START) {
            if ((t & 1) == 0) {
                Fx.ring(world, Fx.RED, x, y + 0.3, z, zone, Math.min(120, (int) (zone * 3)), t * 0.02);
            }
            // a warm glow high in the sky, right where they will come from
            Fx.fuzz(world, ParticleTypes.FLAME, x + 30, y + 95, z + 20, 6, 6.0, 3.0, 6.0, 0.0);
        }
        if (t >= START && t < STOP && (t - START) % spawnEvery == 0) {
            spawn(false);
        }
        if (t == MOTHER_AT) {
            spawn(true);
            motherSpawned = true;
        }
        updateMeteors();
        return motherSpawned && meteors.isEmpty();
    }

    private LivingEntity pickVictim() {
        double r = zone + 8.0;
        Box box = new Box(pos.x - r, pos.y - 40.0, pos.z - r, pos.x + r, pos.y + 60.0, pos.z + r);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, e -> Blast.canHurt(this, e));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(rng.nextInt(list.size()));
    }

    private void spawn(boolean mother) {
        double tx;
        double tz;
        LivingEntity victim = null;
        if (!mother && rng.nextInt(100) < 38) {
            victim = pickVictim();
        }
        if (mother) {
            tx = pos.x;
            tz = pos.z;
        } else if (victim != null) {
            tx = victim.getX() + (rng.nextDouble() - 0.5) * 2.0;
            tz = victim.getZ() + (rng.nextDouble() - 0.5) * 2.0;
        } else {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rng.nextDouble()) * zone;
            tx = pos.x + Math.cos(a) * d;
            tz = pos.z + Math.sin(a) * d;
        }
        double gy = groundY(tx, tz);
        double len = Math.sqrt(DX * DX + DY * DY + DZ * DZ);
        double dx = DX / len;
        double dy = DY / len;
        double dz = DZ / len;
        double height = mother ? 130.0 : 88.0;
        double dist = height / -dy;
        double speed = mother ? 3.0 : 3.4;

        Meteor m = new Meteor();
        m.tx = tx;
        m.ty = gy;
        m.tz = tz;
        m.mother = mother;
        m.blue = !mother && rng.nextInt(5) == 0;
        m.x = tx - dx * dist;
        m.y = gy - dy * dist;
        m.z = tz - dz * dist;
        m.vx = dx * speed;
        m.vy = dy * speed;
        m.vz = dz * speed;
        meteors.add(m);
        Fx.sound(world, tx, gy, tz, ModSounds.METEOR_WHOOSH, mother ? 16f : 9f,
                mother ? 0.7f : 0.9f + rng.nextFloat() * 0.25f);
    }

    private void updateMeteors() {
        Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            if (m.y <= m.ty) {
                impact(m);
                it.remove();
            } else {
                trail(m);
            }
        }
    }

    private void trail(Meteor m) {
        double s = m.mother ? 1.6 : 0.55;
        ParticleEffect fire = m.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        Fx.fuzz(world, fire, m.x, m.y, m.z, m.mother ? 60 : 14, s, s, s, 0.02);
        Fx.fuzz(world, ParticleTypes.LAVA, m.x, m.y, m.z, m.mother ? 8 : 2, s * 0.8, s * 0.8, s * 0.8, 0.0);
        Fx.fuzz(world, CHUNK, m.x, m.y, m.z, m.mother ? 16 : 4, s * 0.5, s * 0.5, s * 0.5, 0.05);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, m.x - m.vx * 1.5, m.y - m.vy * 1.5, m.z - m.vz * 1.5,
                m.mother ? 20 : 6, s, s, s, 0.01);
    }

    private void impact(Meteor m) {
        double x = m.tx;
        double y = m.ty;
        double z = m.tz;
        boolean mother = m.mother;
        double br = mother ? scale(14.0, 19.0, 25.0) : blastR;
        double cr = mother ? scale(8.0, 11.0, 14.0) : craterR;

        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, mother ? 4 : 1, 0.0);
        Fx.burst(world, m.blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y + 1.0, z,
                mother ? 320 : 70, mother ? 0.9 : 0.45);
        Fx.fuzz(world, ParticleTypes.LAVA, x, y + 1.0, z, mother ? 60 : 16, 0.5, 0.2, 0.5, 0.55);
        Fx.fuzz(world, ParticleTypes.LARGE_SMOKE, x, y + 2.0, z, mother ? 60 : 16, cr * 0.4, 1.5, cr * 0.4, 0.06);
        Fx.ringOut(world, ParticleTypes.FLAME, x, y + 0.4, z, 1.5, mother ? 48 : 24, 0.0, mother ? 0.9 : 0.5, 0.02);

        Fx.sound(world, x, y, z, ModSounds.METEOR_IMPACT, mother ? 22f : 11f, mother ? 0.6f : 0.9f + rng.nextFloat() * 0.2f);
        Fx.sound(world, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, mother ? 18f : 8f, mother ? 0.5f : 0.75f);
        if (mother) {
            Fx.sound(world, x, y, z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 14f, 0.6f);
        }

        Blast.hurtSphere(this, x, y + 1.0, z, br, mother ? 500f : 85f, mother ? 80f : 25f, mother ? 2.8 : 1.6, mother ? 15 : 6);
        BlockQueue.carveSphere(world, x, y - cr * 0.25, z, cr, 0.85, 0.0, false,
                mother ? BlockQueue.QUIET : BlockQueue.LOUD, () -> afterCrater(x, z, cr, mother));
    }

    private void afterCrater(double x, double z, double cr, boolean mother) {
        Blast.magmaFloor(world, rng, x, z, cr * 0.7, mother ? 40 : 6);
        Blast.scatterFire(world, rng, x, z, cr + 3.0, mother ? 36 : 6);
    }
}
