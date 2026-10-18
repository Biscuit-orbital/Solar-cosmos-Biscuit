package com.biscuit.orbital.strike;

import com.biscuit.orbital.BiscuitOrbital;
import com.biscuit.orbital.fx.Fx;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Heightmap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;

/** One running strike. Ticked once per world tick by {@link StrikeManager} until {@link #tick(int)} returns true. */
public abstract class StrikeInstance {
    public final ServerWorld world;
    /** Locked target (a point on the surface). */
    public final Vec3d pos;
    /** Caster, may be null for command blocks / console. */
    public final UUID casterId;
    /** 1 (normal) .. 3 (max): scales radius, damage and counts. */
    public final int power;
    /** true = the caster is spared by his own strike. */
    public final boolean safe;
    /** Caster yaw at the moment of the shot (directional strikes use it). */
    public final float yaw;
    public final Random rng = new Random();
    protected int age;

    protected StrikeInstance(ServerWorld world, Vec3d pos, UUID casterId, int power, boolean safe, float yaw) {
        this.world = world;
        this.pos = pos;
        this.casterId = casterId;
        this.power = Math.max(1, Math.min(3, power));
        this.safe = safe;
        this.yaw = yaw;
    }

    /** Runs one tick. @return true when the strike is over. */
    public final boolean advance() {
        boolean done = tick(age);
        age++;
        return done;
    }

    protected abstract boolean tick(int t);

    public ServerPlayerEntity caster() {
        if (casterId == null) {
            return null;
        }
        PlayerEntity p = world.getPlayerByUuid(casterId);
        if (p instanceof ServerPlayerEntity sp) {
            return sp;
        }
        return null;
    }

    protected double scale(double a, double b, double c) {
        if (power <= 1) {
            return a;
        }
        return power == 2 ? b : c;
    }

    /** Damage source: custom "Biscuit Orbital Strike" type (bypasses armor), credited to the caster. */
    public DamageSource damageSource() {
        try {
            RegistryEntry<DamageType> entry =
                    world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BiscuitOrbital.ORBITAL_DAMAGE);
            return new DamageSource(entry, caster());
        } catch (Exception e) {
            return world.getDamageSources().magic();
        }
    }

    /** First free Y above the terrain at x/z (never loads chunks). */
    protected double groundY(double x, double z) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        if (!Fx.loaded(world, bx, bz)) {
            return pos.y;
        }
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** Big title + subtitle for every player within range of the target. */
    protected void announce(String title, String sub, Formatting color, double range) {
        Text t = Text.literal(title).formatted(color, Formatting.BOLD);
        Text s = Text.literal(sub).formatted(Formatting.GRAY);
        double r2 = range * range;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(pos) > r2) {
                continue;
            }
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 45, 15));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(s));
            p.networkHandler.sendPacket(new TitleS2CPacket(t));
        }
    }
}
