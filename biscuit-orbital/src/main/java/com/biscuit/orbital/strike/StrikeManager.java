package com.biscuit.orbital.strike;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.BiscuitOrbital;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns every running strike. A crash inside one strike is logged and only ends that strike, never the server. */
public final class StrikeManager {
    private StrikeManager() {
    }

    private static final List<StrikeInstance> ACTIVE = new ArrayList<>();

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** @return false if the orbital network is saturated. */
    public static boolean start(StrikeType type, ServerWorld world, ServerPlayerEntity caster, Vec3d pos, int power, boolean safe) {
        if (ACTIVE.size() >= BiscuitConfig.maxActiveStrikes) {
            return false;
        }
        UUID id = caster == null ? null : caster.getUuid();
        float yaw = caster == null ? 0.0f : caster.getYaw();
        ACTIVE.add(type.factory.create(world, pos, id, power, safe, yaw));
        return true;
    }

    public static void tick(ServerWorld world) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        StrikeInstance[] snapshot = ACTIVE.toArray(new StrikeInstance[0]);
        for (StrikeInstance s : snapshot) {
            if (s.world != world) {
                continue;
            }
            boolean done;
            try {
                done = s.advance();
            } catch (Throwable t) {
                BiscuitOrbital.LOGGER.error("Orbital strike {} crashed and was cancelled", s.getClass().getSimpleName(), t);
                done = true;
            }
            if (done) {
                ACTIVE.remove(s);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
