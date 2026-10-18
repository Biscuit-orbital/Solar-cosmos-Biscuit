package com.biscuit.orbital.fx;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Low level effect helpers shared by every strike.
 * <p>
 * Everything is sent server side, so a vanilla client renders it (no client code, no shaders = no crashes).
 * Particles are sent with the "long distance" flag so they can be seen from far away.
 */
public final class Fx {
    private Fx() {
    }

    /** Players further than this (blocks) from an effect do not receive its particles. */
    public static final double VIEW = 280.0;
    private static final double VIEW_SQ = VIEW * VIEW;

    public static final DustParticleEffect RED = dust(1.0f, 0.12f, 0.08f, 1.5f);
    public static final DustParticleEffect GOLD = dust(1.0f, 0.74f, 0.20f, 1.5f);
    public static final DustParticleEffect CYAN = dust(0.45f, 0.90f, 1.0f, 1.7f);
    public static final DustParticleEffect WHITE = dust(1.0f, 1.0f, 1.0f, 1.7f);
    public static final DustParticleEffect GREEN = dust(0.40f, 1.0f, 0.30f, 1.5f);

    public static DustParticleEffect dust(float r, float g, float b, float size) {
        return new DustParticleEffect(new Vector3f(r, g, b), size);
    }


    /** True when the chunk holding this block column is fully loaded. Never loads or generates anything. */
    public static boolean loaded(ServerWorld w, int blockX, int blockZ) {
        return w.getChunkManager().getWorldChunk(blockX >> 4, blockZ >> 4) != null;
    }

    public static boolean loaded(ServerWorld w, BlockPos p) {
        return loaded(w, p.getX(), p.getZ());
    }

    private static List<ServerPlayerEntity> viewers(ServerWorld w, double x, double y, double z) {
        List<ServerPlayerEntity> list = null;
        for (ServerPlayerEntity v : w.getPlayers()) {
            if (v.squaredDistanceTo(x, y, z) <= VIEW_SQ) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(v);
            }
        }
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    private static void send(ServerWorld w, List<ServerPlayerEntity> vs, ParticleEffect p, double x, double y, double z,
                             int count, double a, double b, double c, double speed) {
        for (ServerPlayerEntity v : vs) {
            w.spawnParticles(v, p, true, x, y, z, count, a, b, c, speed);
        }
    }

    /** A gaussian cloud of {@code count} particles (sx/sy/sz = standard deviation, speed = random velocity spread). */
    public static void fuzz(ServerWorld w, ParticleEffect p, double x, double y, double z, int count,
                            double sx, double sy, double sz, double speed) {
        List<ServerPlayerEntity> vs = viewers(w, x, y, z);
        if (!vs.isEmpty()) {
            send(w, vs, p, x, y, z, count, sx, sy, sz, speed);
        }
    }

    /** One precisely placed particle that drifts with the given velocity. */
    public static void point(ServerWorld w, ParticleEffect p, double x, double y, double z, double vx, double vy, double vz) {
        List<ServerPlayerEntity> vs = viewers(w, x, y, z);
        if (!vs.isEmpty()) {
            send(w, vs, p, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    /** Spherical burst: all particles start at one point and fly out in random directions. */
    public static void burst(ServerWorld w, ParticleEffect p, double x, double y, double z, int count, double speed) {
        fuzz(w, p, x, y, z, count, 0.0, 0.0, 0.0, speed);
    }

    public static void ring(ServerWorld w, ParticleEffect p, double cx, double cy, double cz, double r, int points, double rot) {
        ringOut(w, p, cx, cy, cz, r, points, rot, 0.0, 0.0);
    }

    /** A flat ring of particles; {@code out} pushes them outward, {@code up} upward. */
    public static void ringOut(ServerWorld w, ParticleEffect p, double cx, double cy, double cz, double r, int points,
                               double rot, double out, double up) {
        List<ServerPlayerEntity> vs = viewers(w, cx, cy, cz);
        if (vs.isEmpty() || points <= 0) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double a = rot + (Math.PI * 2.0 * i) / points;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            send(w, vs, p, cx + ca * r, cy, cz + sa * r, 0, ca * out, up, sa * out, 1.0);
        }
    }

    public static void line(ServerWorld w, ParticleEffect p, Vec3d a, Vec3d b, double step) {
        List<ServerPlayerEntity> vs = viewers(w, (a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
        if (vs.isEmpty()) {
            return;
        }
        int n = Math.max(1, (int) (a.distanceTo(b) / step));
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            send(w, vs, p, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f, 0, 0.0, 0.0, 0.0, 1.0);
        }
    }

    /** Jagged arc between two points (lightning, chain arcs). */
    public static void arc(ServerWorld w, ParticleEffect p, Vec3d a, Vec3d b, int segs, double jx, double jy, double jz, Random rng) {
        List<ServerPlayerEntity> vs = viewers(w, b.x, b.y, b.z);
        if (vs.isEmpty() || segs <= 0) {
            return;
        }
        double px = a.x;
        double py = a.y;
        double pz = a.z;
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            boolean last = i == segs;
            double cx = a.x + (b.x - a.x) * f + (last ? 0.0 : (rng.nextDouble() - 0.5) * 2.0 * jx);
            double cy = a.y + (b.y - a.y) * f + (last ? 0.0 : (rng.nextDouble() - 0.5) * 2.0 * jy);
            double cz = a.z + (b.z - a.z) * f + (last ? 0.0 : (rng.nextDouble() - 0.5) * 2.0 * jz);
            for (int k = 0; k < 3; k++) {
                double g = k / 3.0;
                send(w, vs, p, px + (cx - px) * g, py + (cy - py) * g, pz + (cz - pz) * g, 0, 0.0, 0.0, 0.0, 1.0);
            }
            px = cx;
            py = cy;
            pz = cz;
        }
    }

    /** Vertical zig-zag bolt from the sky. */
    public static void bolt(ServerWorld w, ParticleEffect p, Vec3d top, Vec3d bottom, int segs, double jitter, Random rng) {
        arc(w, p, top, bottom, segs, jitter, 0.0, jitter, rng);
    }

    /** Glowing vertical column made of a few big gaussian puffs (cheap: one packet per 16 blocks). */
    public static void beam(ServerWorld w, double x, double z, double y0, double y1, double radius, double density) {
        int c1 = Math.max(2, (int) (density * radius * 2.2));
        int c2 = Math.max(1, c1 / 3);
        double s = Math.max(0.15, radius * 0.33);
        for (double y = y0; y < y1; y += 16.0) {
            double cy = Math.min(y + 8.0, y1);
            fuzz(w, ParticleTypes.END_ROD, x, cy, z, c1, s, 8.0, s, 0.0);
            fuzz(w, ParticleTypes.ELECTRIC_SPARK, x, cy, z, c2, s * 1.3, 8.0, s * 1.3, 0.15);
        }
    }

    /** Sound that carries: volume above 1 extends the range (volume 20 is audible ~320 blocks away). */
    public static void sound(ServerWorld w, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        soundAs(w, x, y, z, s, SoundCategory.WEATHER, vol, pitch);
    }

    public static void sound(ServerWorld w, Vec3d p, SoundEvent s, float vol, float pitch) {
        soundAs(w, p.x, p.y, p.z, s, SoundCategory.WEATHER, vol, pitch);
    }

    public static void soundAs(ServerWorld w, double x, double y, double z, SoundEvent s, SoundCategory c, float vol, float pitch) {
        w.playSound(null, x, y, z, s, c, vol, pitch);
    }

    /** Adds velocity and makes sure players actually feel it (players need an explicit packet). */
    public static void push(Entity e, double vx, double vy, double vz) {
        e.addVelocity(vx, vy, vz);
        e.velocityModified = true;
        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
        }
    }
}
