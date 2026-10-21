package com.biscuit.orbital.client;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.Timeline;
import com.biscuit.orbital.net.CinematicS2C;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Drives the strike cinematic on the client: takes over the camera with an invisible client-only entity,
 * flies it up through the clouds, hands the screen to the space scenes, then orbits the crater.
 */
public final class Cinematic {
	private static boolean active;
	private static boolean useCamera;
	private static long startMs;
	private static float speed = 1f;
	private static float endTime = Timeline.TOTAL;
	private static BlockPos target = BlockPos.ZERO;
	private static int radius = 96;
	private static Marker cam;
	private static Vec3 origin = Vec3.ZERO;
	private static float yaw0, pitch0;
	private static final boolean[] fired = new boolean[8];

	public static void start(CinematicS2C packet) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		stop(mc);
		target = packet.target();
		radius = packet.radius();
		speed = packet.speed();
		startMs = Util.getMillis();
		Arrays.fill(fired, false);
		useCamera = ClientSettings.current.cutscenes();
		endTime = useCamera ? Timeline.TOTAL : Timeline.IMPACT + 5f;
		active = true;
		if (useCamera) {
			origin = mc.player.getEyePosition();
			yaw0 = mc.player.getYRot();
			pitch0 = mc.player.getXRot();
			cam = new Marker(EntityType.MARKER, mc.level);
			place(0f);
			mc.setCameraEntity(cam);
		}
	}

	private static void stop(Minecraft mc) {
		if (cam != null && mc.player != null) {
			mc.setCameraEntity(mc.player);
		}
		cam = null;
		active = false;
	}

	public static void tick(Minecraft mc) {
		if (active && (mc.level == null || mc.player == null)) {
			cam = null;
			active = false;
		}
	}

	private static void place(float t) {
		double x, y, z;
		float yaw, pitch;
		if (t < Timeline.IMPACT) {
			float asc = Scenes.smooth((t - Timeline.GROUND_END) / (Timeline.ASCENT_END - Timeline.GROUND_END));
			x = origin.x;
			z = origin.z;
			y = origin.y + 6000.0 * asc * asc;
			yaw = yaw0;
			pitch = Scenes.lerp(pitch0, -89f, Scenes.smooth(t / Timeline.GROUND_END));
		} else {
			float u = t - Timeline.IMPACT;
			double ang = 0.35 * u + 0.8;
			double orbit = Math.min(220.0, Math.max(48.0, radius * 0.9));
			double h = Math.min(200.0, 30.0 + radius * 0.55) * (1.0 - 0.45 * Scenes.clamp01(u / (Timeline.IMPACT_END - Timeline.IMPACT)));
			x = target.getX() + 0.5 + Math.cos(ang) * orbit;
			z = target.getZ() + 0.5 + Math.sin(ang) * orbit;
			y = target.getY() + h;
			double dx = target.getX() + 0.5 - x, dz = target.getZ() + 0.5 - z, dy = target.getY() - y;
			yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		}
		cam.setPos(x, y, z);
		cam.setYRot(yaw);
		cam.setXRot(pitch);
		cam.xo = x;
		cam.yo = y;
		cam.zo = z;
		cam.yRotO = yaw;
		cam.xRotO = pitch;
	}

	/** HUD element, called every rendered frame. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!active) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			stop(mc);
			return;
		}
		float t = (Util.getMillis() - startMs) / 1000f * speed;
		if (t >= endTime) {
			stop(mc);
			return;
		}
		if (useCamera && cam != null) {
			place(t);
		}
		Canvas canvas = new Canvas(graphics);
		try {
			Scenes.draw(canvas, t, useCamera, target, radius);
		} finally {
			canvas.end();
		}
		cues(t);
	}

	private static void cues(float t) {
		cue(0, t, 0.2f, ModSounds.LOCK_BEEP, 0.6f);
		cue(1, t, 1.2f, ModSounds.LOCK_BEEP, 0.6f);
		if (useCamera) {
			cue(2, t, Timeline.GROUND_END, ModSounds.RISER, 0.8f);
			cue(3, t, 21.0f, ModSounds.CHARGE, 0.8f);
			cue(4, t, Timeline.FIRE_START, ModSounds.FIRE, 0.9f);
		}
		cue(5, t, Timeline.IMPACT, ModSounds.IMPACT, 1.0f);
		cue(6, t, Timeline.IMPACT + 0.6f, ModSounds.RUMBLE, 0.7f);
	}

	private static void cue(int i, float t, float at, SoundEvent sound, float volume) {
		if (!fired[i] && t >= at) {
			fired[i] = true;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
		}
	}

	private Cinematic() {
	}
}
