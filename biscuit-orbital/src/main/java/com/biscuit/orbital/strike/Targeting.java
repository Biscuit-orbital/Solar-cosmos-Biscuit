package com.biscuit.orbital.strike;

import com.biscuit.orbital.fx.Fx;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Finds the block the player is looking at without ever loading or generating chunks. */
public final class Targeting {
    private Targeting() {
    }

    /** @return the surface point under the crosshair, or null when nothing solid is in range. */
    public static Vec3d find(ServerWorld world, ServerPlayerEntity player, double range) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        BlockPos.Mutable m = new BlockPos.Mutable();
        double step = 0.5;
        Vec3d previous = eye;
        for (double d = 1.0; d <= range; d += step) {
            double x = eye.x + dir.x * d;
            double y = eye.y + dir.y * d;
            double z = eye.z + dir.z * d;
            if (y < world.getBottomY() || y >= world.getTopY()) {
                previous = new Vec3d(x, y, z);
                continue;
            }
            m.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
            if (!Fx.loaded(world, m)) {
                return null;
            }
            BlockState st = world.getBlockState(m);
            if (!st.isAir() && !st.getCollisionShape(world, m).isEmpty()) {
                return previous;
            }
            previous = new Vec3d(x, y, z);
        }
        return null;
    }
}
