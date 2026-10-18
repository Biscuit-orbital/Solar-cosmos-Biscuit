package com.biscuit.orbital.fx;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.BiscuitOrbital;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Time-budgeted terrain destruction. Big craters are processed over many ticks (a few ms per tick),
 * so even the nuke carves the world without freezing the server. Never touches unloaded chunks or bedrock.
 */
public final class BlockQueue {
    private BlockQueue() {
    }

    /** Full block updates: water flows in, sand falls, torches pop. */
    public static final int LOUD = Block.NOTIFY_ALL;
    /** Client updates only: much cheaper for gigantic holes. */
    public static final int QUIET = Block.NOTIFY_LISTENERS;

    public interface Job {
        ServerWorld world();

        /** @return true when finished */
        boolean step(long deadlineNanos);

        /** Blocks still waiting to be processed. */
        int remaining();
    }

    private static final List<Job> JOBS = new ArrayList<>();

    public static void add(Job j) {
        JOBS.add(j);
    }

    public static void clear() {
        JOBS.clear();
    }

    public static int pending() {
        return JOBS.size();
    }

    public static void tick(ServerWorld world) {
        if (JOBS.isEmpty()) {
            return;
        }
        Job[] snapshot = JOBS.toArray(new Job[0]);
        long backlog = 0L;
        for (Job j : snapshot) {
            if (j.world() == world) {
                backlog += j.remaining();
            }
        }
        // the more terrain is waiting, the more time we may spend per tick (capped), so big craters finish fast
        long extra = Math.max(0L, Math.min(BiscuitConfig.blockBudgetMaxMs - BiscuitConfig.blockBudgetMs, backlog / 6000L));
        long deadline = System.nanoTime() + (BiscuitConfig.blockBudgetMs + extra) * 1_000_000L;
        for (Job j : snapshot) {
            if (j.world() != world) {
                continue;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            boolean done;
            try {
                done = j.step(deadline);
            } catch (Throwable t) {
                BiscuitOrbital.LOGGER.error("Biscuit block job failed", t);
                done = true;
            }
            if (done) {
                JOBS.remove(j);
            }
        }
    }

    public static void carve(ServerWorld world, BlockPos origin, long[] shape, int flags, Runnable after) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        add(new ShapeJob(world, origin, shape, flags, after));
    }

    public static void carveSphere(ServerWorld w, double x, double y, double z, double r, double yScale, double innerR,
                                   boolean lowerOnly, int flags, Runnable after) {
        if (!BiscuitConfig.terrainDamage) {
            return;
        }
        BlockPos origin = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        long seed = origin.asLong();
        carve(w, origin, Shapes.sphere(r, yScale, innerR, lowerOnly, seed), flags, after);
    }

    /** Removes every breakable block of a pre-computed shape, a time slice per tick. */
    public static final class ShapeJob implements Job {
        private final ServerWorld world;
        private final int ox;
        private final int oy;
        private final int oz;
        private final long[] shape;
        private final int flags;
        private final Runnable after;
        private final Random rng = new Random();
        private final int particleOdds;
        private int cursor;

        public ShapeJob(ServerWorld world, BlockPos origin, long[] shape, int flags, Runnable after) {
            this.world = world;
            this.ox = origin.getX();
            this.oy = origin.getY();
            this.oz = origin.getZ();
            this.shape = shape;
            this.flags = flags;
            this.after = after;
            this.particleOdds = shape.length > 20000 ? 70 : 20;
        }

        @Override
        public int remaining() {
            return shape.length - cursor;
        }

        @Override
        public ServerWorld world() {
            return world;
        }

        @Override
        public boolean step(long deadline) {
            BlockPos.Mutable m = new BlockPos.Mutable();
            int bottom = world.getBottomY();
            int top = world.getTopY();
            while (cursor < shape.length) {
                long v = shape[cursor++];
                int y = oy + Shapes.dy(v);
                if (y >= bottom && y < top) {
                    m.set(ox + Shapes.dx(v), y, oz + Shapes.dz(v));
                    if (Fx.loaded(world, m)) {
                        BlockState st = world.getBlockState(m);
                        if (!st.isAir() && st.getHardness(world, m) >= 0.0f) {
                            if (rng.nextInt(particleOdds) == 0) {
                                Fx.fuzz(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, st),
                                        m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 5, 0.35, 0.35, 0.35, 0.05);
                            }
                            world.setBlockState(m, Blocks.AIR.getDefaultState(), flags);
                        }
                    }
                }
                if ((cursor & 31) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
            }
            if (after != null) {
                try {
                    after.run();
                } catch (Throwable t) {
                    BiscuitOrbital.LOGGER.error("Biscuit post-carve hook failed", t);
                }
            }
            return true;
        }
    }
}
