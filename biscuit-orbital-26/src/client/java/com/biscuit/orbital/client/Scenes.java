package com.biscuit.orbital.client;

import com.biscuit.orbital.Timeline;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/** All the 2D-overlay "space" scenes of the Crumb Comet cinematic. Everything is drawn procedurally from original textures. */
final class Scenes {
	private static final int RED = 0xFF2850;
	private static final int CYAN = 0x40E0D0;
	private static final int BLACK_SPACE = 0xFF03040A;

	static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
	static float smooth(float v) { v = clamp01(v); return v * v * (3f - 2f * v); }
	static float lerp(float a, float b, float t) { return a + (b - a) * t; }
	private static float sin(float v) { return (float) Math.sin(v); }
	private static float cos(float v) { return (float) Math.cos(v); }
	private static float mod(float a, float b) { float r = a % b; return r < 0 ? r + b : r; }

	static void draw(Canvas c, float t, boolean cam, BlockPos target, int radius) {
		float vw = c.vw, vh = Canvas.VH;
		String scale = "SCALE LOCAL";

		if (cam) {
			if (t < Timeline.ASCENT_END) {
				ascent(c, t);
				float u = clamp01((t - Timeline.GROUND_END) / (Timeline.ASCENT_END - Timeline.GROUND_END));
				scale = String.format(Locale.ROOT, "SCALE %.1f KM", 0.002f + 12742f * u * u);
			} else if (t < Timeline.EARTH_END) {
				earth(c, t);
				scale = "SCALE 12,742 KM";
			} else if (t < Timeline.SOLAR_END) {
				solar(c, t);
				scale = "SCALE 1.0E10 KM";
			} else if (t < Timeline.GALAXY_END) {
				galaxy(c, t);
				scale = "SCALE 26,000 LY";
			} else if (t < Timeline.IMPACT) {
				station(c, t);
				scale = "SCALE 58,000 LY";
			} else {
				impact(c, t, radius);
				scale = "SCALE 0.480 KM";
			}
			// quick black dips between space scenes
			float[] edges = {Timeline.EARTH_END, Timeline.SOLAR_END, Timeline.GALAXY_END};
			for (float e : edges) {
				c.rect(0, 0, vw, vh, Canvas.argb(1f - Math.abs(t - e) / 0.4f, 0x000000));
			}
		} else if (t >= Timeline.IMPACT) {
			float u = t - Timeline.IMPACT;
			c.rect(0, 0, vw, vh, Canvas.argb(1f - smooth(u / 1.2f), 0xFFFFFF));
		}

		frame(c, t, target, scale, radius);
	}

	// ---------------------------------------------------------------- ground + ascent (the real world is visible)
	private static void ascent(Canvas c, float t) {
		float vw = c.vw, vh = Canvas.VH;
		float gr = clamp01(t / Timeline.GROUND_END);
		float u = clamp01((t - Timeline.GROUND_END) / (Timeline.ASCENT_END - Timeline.GROUND_END));
		c.rect(0, 0, vw, vh, Canvas.argb(0.14f * gr, RED));

		float pulse = 1f + 0.08f * sin(t * 18f);
		float shrink = 1f - 0.75f * smooth(u);
		float cx = vw / 2f;
		float[] widths = {90, 46, 20, 7};
		float[] alphas = {0.10f, 0.18f, 0.38f, 0.95f};
		int[] cols = {RED, RED, 0xFF7088, 0xFFFFFF};
		for (int i = 0; i < 4; i++) {
			float w = widths[i] * pulse * shrink * gr;
			c.rect(cx - w / 2f, 0, w, vh, Canvas.argb(alphas[i], cols[i]));
		}

		float ra = gr * (1f - clamp01(u * 3f));
		if (ra > 0.01f) {
			float ry = vh * 0.80f;
			c.dashedRing(cx, ry, 130, 24, t * 0.9f, 0.16f, 3, Canvas.argb(ra * 0.9f, 0xFFFFFF));
			c.dashedRing(cx, ry, 96, 16, -t * 1.3f, 0.25f, 3, Canvas.argb(ra * 0.8f, RED));
			c.ring(cx, ry, 160, 1.5f, Canvas.argb(ra * 0.5f, 0xFFFFFF), 64);
		}
		if (u > 0.30f) {
			c.rect(0, 0, vw, vh, Canvas.argb(0.88f * smooth((u - 0.30f) / 0.35f), 0xF0C8D6)); // cloud layer
		}
		if (u > 0.68f) {
			c.rect(0, 0, vw, vh, Canvas.argb(smooth((u - 0.68f) / 0.30f), 0x02030A)); // into space
		}
	}

	// ---------------------------------------------------------------- space
	private static void stars(Canvas c, float ox, float oy, float k) {
		float tile = 1024f * k;
		float sx = -mod(ox, tile), sy = -mod(oy, tile);
		for (float x = sx; x < c.vw; x += tile) {
			for (float y = sy; y < Canvas.VH; y += tile) {
				c.tex(Canvas.STARS, x, y, tile, tile, 1024, 1024);
			}
		}
	}

	private static void earth(Canvas c, float t) {
		float vw = c.vw, vh = Canvas.VH;
		float u = clamp01((t - Timeline.ASCENT_END) / (Timeline.EARTH_END - Timeline.ASCENT_END));
		c.rect(0, 0, vw, vh, BLACK_SPACE);
		stars(c, t * 5f, t * 2f, 1f);
		stars(c, t * 11f, t * 4f, 1.7f);

		float size = 640f + 110f * u;
		float ex = vw * 0.5f, ey = vh * 0.53f;
		c.tex(Canvas.EARTH, ex - size / 2f, ey - size / 2f, size, size, 512, 512);

		float ix = ex - size * 0.10f, iy = ey + size * 0.03f;
		c.glowLine(vw * 0.06f, vh + 20f, ix, iy, RED, 1f);
		float gs = 150f + 30f * sin(t * 9f);
		c.tex(Canvas.GLOW_RED, ix - gs / 2f, iy - gs / 2f, gs, gs, 256, 256);

		c.bracket(ex - size * 0.55f, ey - size * 0.55f, size * 1.1f, size * 1.1f, 38, 3, Canvas.argb(0.8f, CYAN));
		c.text("EARTH", ex + size * 0.58f, ey - size * 0.50f, 2f, Canvas.argb(0.9f, CYAN));
		c.text("12,742 KM", ex + size * 0.58f, ey - size * 0.50f + 22f, 1.6f, Canvas.argb(0.7f, CYAN));
	}

	private static void solar(Canvas c, float t) {
		float vw = c.vw, vh = Canvas.VH;
		float u = clamp01((t - Timeline.EARTH_END) / (Timeline.SOLAR_END - Timeline.EARTH_END));
		c.rect(0, 0, vw, vh, BLACK_SPACE);
		stars(c, t * 3f, t, 0.9f);

		float cx = vw * 0.5f, cy = vh * 0.52f, k = lerp(1.6f, 1.0f, smooth(u)), tilt = -0.35f;
		float[] a = {110, 190, 290, 400};
		String[] names = {"EARTH", "JUPITER", "SATURN", "NEPTUNE"};
		float[] spd = {0.30f, 0.20f, 0.12f, 0.08f};
		float[] ph = {0.3f, 1.2f, 2.5f, 4.0f};

		float sun = 170f * k;
		c.tex(Canvas.GLOW_GOLD, cx - sun / 2f, cy - sun / 2f, sun, sun, 256, 256);
		float[] earthPos = {cx, cy};
		for (int i = 0; i < 4; i++) {
			c.ellipse(cx, cy, a[i] * k, a[i] * k * 0.52f, tilt, 140, 2f, Canvas.argb(0.55f, CYAN));
			float[] p = Canvas.ellipsePoint(cx, cy, a[i] * k, a[i] * k * 0.52f, tilt, ph[i] + spd[i] * t);
			c.rect(p[0] - 4, p[1] - 4, 8, 8, Canvas.argb(1f, i == 0 ? 0x6FC3FF : 0xFFFFFF));
			c.text(names[i], p[0] + 12, p[1] - 8, 1.6f, Canvas.argb(0.85f, CYAN));
			if (i == 0) {
				earthPos = p;
			}
		}
		c.glowLine(vw * 0.74f, -30f, earthPos[0], earthPos[1], RED, 1f);
	}

	private static void galaxy(Canvas c, float t) {
		float vw = c.vw, vh = Canvas.VH;
		float u = clamp01((t - Timeline.SOLAR_END) / (Timeline.GALAXY_END - Timeline.SOLAR_END));
		c.rect(0, 0, vw, vh, BLACK_SPACE);
		stars(c, t * 2f, t * 0.7f, 0.8f);

		float size = 1250f * (1f + 0.12f * u), cx = vw * 0.48f, cy = vh * 0.56f, rot = 0.04f * t;
		c.texRot(Canvas.GALAXY, cx, cy, size, size, rot, 1024, 1024);

		float ox = size * 0.20f, oy = size * 0.07f;
		float sx = cx + ox * cos(rot) - oy * sin(rot), sy = cy + ox * sin(rot) + oy * cos(rot);
		c.bracket(sx - 34, sy - 34, 68, 68, 16, 2, Canvas.argb(0.9f, CYAN));
		c.text("HOME // 26,000 LY", sx + 44, sy - 8, 1.6f, Canvas.argb(0.85f, CYAN));

		float stx = vw * 0.30f, sty = vh * 0.05f;
		c.glowLine(stx, sty, sx, sy, RED, 0.8f);
		c.tex(Canvas.GLOW_RED, stx - 40, sty - 40, 80, 80, 256, 256);

		float a = smooth((u - 0.15f) / 0.2f) * (0.7f + 0.3f * sin(t * 8f));
		c.spaced("EVAC // CLEAR OF THE STRIKE ZONE", vw / 2f, vh * 0.20f, 3f, 6f, Canvas.argb(a, RED));
	}

	private static void station(Canvas c, float t) {
		float vw = c.vw, vh = Canvas.VH;
		float u = clamp01((t - Timeline.GALAXY_END) / (Timeline.IMPACT - Timeline.GALAXY_END));
		c.rect(0, 0, vw, vh, BLACK_SPACE);
		stars(c, t * 4f, t, 1f);
		c.texRot(Canvas.GALAXY, vw * 0.15f, vh * 0.95f, 1500, 1500, 0.02f * t, 1024, 1024);

		float w = 1000f * (0.85f + 0.22f * u), h = w * 0.5f;
		float sx = vw * 0.52f + (-20f + 30f * u), sy = vh * 0.40f;
		float rot = -0.22f + 0.02f * sin(t * 0.7f);
		c.texRot(Canvas.STATION, sx, sy, w, h, rot, 768, 384);

		float lx = (757f / 768f - 0.5f) * w, ly = (190f / 384f - 0.5f) * h;
		float tipx = sx + lx * cos(rot) - ly * sin(rot), tipy = sy + lx * sin(rot) + ly * cos(rot);

		float ta = smooth((t - 21.0f) / 0.8f) * (1f - smooth((t - 24.6f) / 0.4f));
		c.spaced("THE CRUMB COMET", vw / 2f, vh * 0.70f, 6.5f, 14f, Canvas.argb(ta, RED));
		c.spaced("BO-01 ORBITAL RAILGUN // BEYOND THE RIM // 58,000 LY FROM HOME", vw / 2f, vh * 0.70f + 82f, 1.8f, 3f, Canvas.argb(ta * 0.85f, 0xDDE4F0));

		float ch = smooth((t - 22.0f) / (Timeline.FIRE_START - 22.0f));
		if (ch > 0f && t < Timeline.FIRE_START + 0.4f) {
			float gs = 60f + 380f * ch, cs = 30f + 140f * ch;
			c.tex(Canvas.GLOW_RED, tipx - gs / 2f, tipy - gs / 2f, gs, gs, 256, 256);
			c.tex(Canvas.GLOW_WHITE, tipx - cs / 2f, tipy - cs / 2f, cs, cs, 256, 256);
		}
		if (t >= Timeline.FIRE_START) {
			float f = clamp01((t - Timeline.FIRE_START) / 0.25f);
			float ex = tipx + cos(rot) * 3200f * f, ey = tipy + sin(rot) * 3200f * f;
			c.glowLine(tipx, tipy, ex, ey, RED, 1.6f);
			float ms = 700f * (1f - 0.5f * f);
			c.tex(Canvas.GLOW_WHITE, tipx - ms / 2f, tipy - ms / 2f, ms, ms, 256, 256);
		}
		c.rect(0, 0, vw, vh, Canvas.argb(smooth((t - 25.7f) / (Timeline.IMPACT - 25.7f)), 0xFFFFFF));
	}

	// ---------------------------------------------------------------- impact (real world, camera orbiting the crater)
	private static void impact(Canvas c, float t, int radius) {
		float vw = c.vw, vh = Canvas.VH;
		float u = t - Timeline.IMPACT;
		float red = 0.55f * (1f - smooth(u / 4f));
		c.vgrad(0, 0, vw, vh * 0.5f, Canvas.argb(red, 0xA00010), Canvas.argb(0f, 0xA00010));
		c.vgrad(0, vh * 0.5f, vw, vh * 0.5f, Canvas.argb(0f, 0xA00010), Canvas.argb(red, 0xA00010));
		c.rect(0, 0, vw, vh, Canvas.argb(1f - smooth(u / 1.4f), 0xFFFFFF));

		float a = smooth(u / 0.6f) * (1f - smooth((u - 5f) / 1.5f));
		c.spaced("[ IMPACT CONFIRMED ]", vw / 2f, vh * 0.46f, 3f, 6f, Canvas.argb(a, 0xFFFFFF));

		c.rect(0, 0, vw, vh, Canvas.argb(smooth((t - (Timeline.TOTAL - 1.6f)) / 0.8f), 0x000000));
	}

	// ---------------------------------------------------------------- HUD frame
	private static void frame(Canvas c, float t, BlockPos target, String scale, int radius) {
		float vw = c.vw, vh = Canvas.VH;
		int col = Canvas.argb(0.9f, 0xFF2D50);
		float m = 40f;
		c.bracket(m, m, vw - 2 * m, vh - 2 * m, 56, 3, col);
		c.text("UPLINK // BO-01 // THE CRUMB COMET", m + 22, m + 20, 2f, col);
		float remain = Timeline.IMPACT - t;
		String tr = remain > 0 ? String.format(Locale.ROOT, "IMPACT T-%05.2f", remain) : "IMPACT CONFIRMED";
		c.textRight(tr, vw - m - 22, m + 20, 2f, col);
		c.text(scale, m + 22, vh - m - 38, 2f, col);
		c.textRight(String.format(Locale.ROOT, "LOCK %d / %d / %d", target.getX(), target.getY(), target.getZ()), vw - m - 22, vh - m - 38, 2f, col);
		if (t >= Timeline.IMPACT) {
			c.spaced(String.format(Locale.ROOT, "SHAFT %04d // WORLD TOP TO VOID", radius), vw / 2f, vh - m - 62f, 2.2f, 4f, Canvas.argb(0.9f, 0xFFFFFF));
		}
	}

	private Scenes() {
	}
}
