package com.biscuit.orbital.server;

import com.biscuit.orbital.Settings;
import com.biscuit.orbital.Timeline;
import com.biscuit.orbital.net.CinematicS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of the orbital strike: validates the shot, schedules the impact, then carves the shaft world-top to void. */
public final class StrikeManager {
	// setBlock flags: 2 = send to clients, 16 = skip neighbour shape updates, 32 = no drops
	private static final int CARVE_FLAGS = 2 | 16 | 32;
	private static final long TICK_BUDGET_NANOS = 22_000_000L;

	private static final Map<UUID, Settings> SETTINGS = new ConcurrentHashMap<>();
	private static final List<Job> JOBS = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StrikeManager::tick);
	}

	public static void setSettings(UUID player, Settings settings) {
		SETTINGS.put(player, settings.clamped());
	}

	public static Settings settingsFor(UUID player) {
		return SETTINGS.getOrDefault(player, Settings.DEFAULT);
	}

	public static void tryFire(ServerPlayer player) {
		for (Job job : JOBS) {
			if (job.owner.equals(player.getUUID())) {
				player.sendSystemMessage(Component.literal("Uplink busy - a strike is already in progress."));
				return;
			}
		}
		ServerLevel level = (ServerLevel) player.level();
		int viewChunks = level.getServer().getPlayerList().getViewDistance();
		double range = Math.min(480, Math.max(48, (viewChunks - 1) * 16));
		HitResult hit = player.pick(range, 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.sendSystemMessage(Component.literal("No target - look at a block (max " + (int) range + " blocks)."));
			return;
		}
		BlockPos target = ((BlockHitResult) hit).getBlockPos();
		Settings s = settingsFor(player.getUUID());

		CinematicS2C packet = new CinematicS2C(target, s.radius(), s.speed());
		if (s.everyone()) {
			for (ServerPlayer other : level.players()) {
				ServerPlayNetworking.send(other, packet);
			}
		} else {
			ServerPlayNetworking.send(player, packet);
		}

		int wait = s.cutscenes() ? Math.round(Timeline.IMPACT * 20.0F / s.speed()) : 40;
		JOBS.add(new Job(player.getUUID(), level, target, s.radius(), s.damage(), wait));
	}

	private static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		Iterator<Job> it = JOBS.iterator();
		while (it.hasNext()) {
			if (it.next().step()) {
				it.remove();
			}
		}
	}

	private static final class Job {
		final UUID owner;
		final ServerLevel level;
		final BlockPos center;
		final int radius;
		final int damage;
		int waitTicks;
		boolean impacted;
		final long[] keys;
		final int count;
		int index;

		Job(UUID owner, ServerLevel level, BlockPos center, int radius, int damage, int waitTicks) {
			this.owner = owner;
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.damage = damage;
			this.waitTicks = waitTicks;

			// Every column inside the circle, sorted by distance so the shaft opens outward like a shockwave.
			int side = radius * 2 + 1;
			long[] k = new long[side * side];
			int n = 0;
			int r2 = radius * radius;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 <= r2) {
						k[n++] = ((long) d2 << 32) | ((long) ((dx + 1024) & 0xFFF) << 12) | ((dz + 1024) & 0xFFF);
					}
				}
			}
			Arrays.sort(k, 0, n);
			this.keys = k;
			this.count = n;
		}

		/** @return true when the job is finished. */
		boolean step() {
			if (waitTicks > 0) {
				waitTicks--;
				return false;
			}
			if (!impacted) {
				impacted = true;
				killEntities();
			}
			long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			BlockState air = Blocks.AIR.defaultBlockState();
			int minY = level.getMinY();
			int maxY = level.getMaxY();
			while (index < count) {
				long key = keys[index++];
				int dx = (int) ((key >> 12) & 0xFFF) - 1024;
				int dz = (int) (key & 0xFFF) - 1024;
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				if (level.hasChunk(x >> 4, z >> 4)) {
					for (int y = maxY; y >= minY; y--) {
						pos.set(x, y, z);
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, air, CARVE_FLAGS);
						}
					}
				}
				if ((index & 15) == 0 && System.nanoTime() > deadline) {
					break;
				}
			}
			return index >= count;
		}

		private void killEntities() {
			double cx = center.getX() + 0.5;
			double cz = center.getZ() + 0.5;
			AABB box = new AABB(cx - radius, level.getMinY(), cz - radius, cx + radius, level.getMaxY() + 1, cz + radius);
			float amount = damage == 0 ? 1_000_000.0F : (float) damage;
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
				if (entity.getUUID().equals(owner)) {
					continue;
				}
				double dx = entity.getX() - cx;
				double dz = entity.getZ() - cz;
				if (dx * dx + dz * dz <= (double) radius * radius) {
					entity.hurtServer(level, level.damageSources().genericKill(), amount);
				}
			}
		}
	}

	private StrikeManager() {
	}
}
