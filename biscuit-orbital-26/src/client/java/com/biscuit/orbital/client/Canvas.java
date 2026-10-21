package com.biscuit.orbital.client;

import com.biscuit.orbital.BiscuitOrbital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * A fixed 1080-unit-tall virtual canvas that is scaled to whatever GUI scale the player uses,
 * so lines and text stay crisp. Everything (HUD cinematic and menu) draws through this.
 */
final class Canvas {
	static final int VH = 1080;

	static final Identifier STARS = gui("stars");
	static final Identifier EARTH = gui("earth");
	static final Identifier GALAXY = gui("galaxy");
	static final Identifier STATION = gui("station");
	static final Identifier THUMB = gui("thumb");
	static final Identifier GLOW_RED = gui("glow_red");
	static final Identifier GLOW_WHITE = gui("glow_white");
	static final Identifier GLOW_GOLD = gui("glow_gold");
	static final Identifier REMOTE = BiscuitOrbital.id("textures/item/uplink_remote.png");

	private static Identifier gui(String name) {
		return BiscuitOrbital.id("textures/gui/" + name + ".png");
	}

	final GuiGraphicsExtractor g;
	final Minecraft mc = Minecraft.getInstance();
	final float s;
	final float vw;

	Canvas(GuiGraphicsExtractor g) {
		this.g = g;
		int gw = mc.getWindow().getGuiScaledWidth();
		int gh = mc.getWindow().getGuiScaledHeight();
		this.s = gh / (float) VH;
		this.vw = gw / this.s;
		g.pose().pushMatrix();
		g.pose().scale(this.s, this.s);
	}

	void end() {
		g.pose().popMatrix();
	}

	static int argb(float a, int rgb) {
		int al = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	void rect(float x, float y, float w, float h, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb);
	}

	void vgrad(float x, float y, float w, float h, int top, int bottom) {
		g.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), top, bottom);
	}

	void box(float x, float y, float w, float h, float th, int argb) {
		rect(x, y, w, th, argb);
		rect(x, y + h - th, w, th, argb);
		rect(x, y, th, h, argb);
		rect(x + w - th, y, th, h, argb);
	}

	void line(float x1, float y1, float x2, float y2, float th, int argb) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.5f || (argb >>> 24) == 0) {
			return;
		}
		Matrix3x2fStack m = g.pose();
		m.pushMatrix();
		m.translate(x1, y1);
		m.rotate((float) Math.atan2(dy, dx));
		int half = Math.max(1, Math.round(th / 2f));
		g.fill(0, -half, Math.round(len), half, argb);
		m.popMatrix();
	}

	/** Layered red/white energy beam. */
	void glowLine(float x1, float y1, float x2, float y2, int rgb, float k) {
		line(x1, y1, x2, y2, 26 * k, argb(0.08f, rgb));
		line(x1, y1, x2, y2, 14 * k, argb(0.16f, rgb));
		line(x1, y1, x2, y2, 6 * k, argb(0.55f, rgb));
		line(x1, y1, x2, y2, 2.5f * k, argb(0.95f, 0xFFFFFF));
	}

	void ring(float cx, float cy, float r, float th, int argb, int segs) {
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
			line(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
					cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, th, argb);
		}
	}

	void dashedRing(float cx, float cy, float r, int dashes, float rot, float arcLen, float th, int argb) {
		for (int i = 0; i < dashes; i++) {
			double a0 = rot + i * Math.PI * 2 / dashes, a1 = a0 + arcLen;
			line(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
					cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, th, argb);
		}
	}

	/** Point on a tilted ellipse. */
	static float[] ellipsePoint(float cx, float cy, float a, float b, float tilt, float ang) {
		float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang), ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
		return new float[]{cx + a * ca * ct - b * sa * st, cy + a * ca * st + b * sa * ct};
	}

	void ellipse(float cx, float cy, float a, float b, float tilt, int segs, float th, int argb) {
		float[] prev = ellipsePoint(cx, cy, a, b, tilt, 0f);
		for (int i = 1; i <= segs; i++) {
			float[] p = ellipsePoint(cx, cy, a, b, tilt, (float) (i * Math.PI * 2 / segs));
			line(prev[0], prev[1], p[0], p[1], th, argb);
			prev = p;
		}
	}

	void bracket(float x, float y, float w, float h, float len, float th, int argb) {
		rect(x, y, len, th, argb); rect(x, y, th, len, argb);
		rect(x + w - len, y, len, th, argb); rect(x + w - th, y, th, len, argb);
		rect(x, y + h - th, len, th, argb); rect(x, y + h - len, th, len, argb);
		rect(x + w - len, y + h - th, len, th, argb); rect(x + w - th, y + h - len, th, len, argb);
	}

	void tex(Identifier id, float x, float y, float w, float h, int tw, int th) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(x), Math.round(y), 0f, 0f, Math.round(w), Math.round(h), tw, th, tw, th);
	}

	/** Draw a texture centred on (cx, cy), rotated by rot radians. */
	void texRot(Identifier id, float cx, float cy, float w, float h, float rot, int tw, int th) {
		Matrix3x2fStack m = g.pose();
		m.pushMatrix();
		m.translate(cx, cy);
		m.rotate(rot);
		g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(-w / 2f), Math.round(-h / 2f), 0f, 0f, Math.round(w), Math.round(h), tw, th, tw, th);
		m.popMatrix();
	}

	void text(String str, float x, float y, float scale, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		Matrix3x2fStack m = g.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale, scale);
		g.text(mc.font, str, 0, 0, argb, false);
		m.popMatrix();
	}

	float textWidth(String str, float scale) {
		return mc.font.width(str) * scale;
	}

	void textRight(String str, float xRight, float y, float scale, int argb) {
		text(str, xRight - textWidth(str, scale), y, scale, argb);
	}

	void textCenter(String str, float cx, float y, float scale, int argb) {
		text(str, cx - textWidth(str, scale) / 2f, y, scale, argb);
	}

	/** Letter-spaced, centred text. */
	void spaced(String str, float cx, float y, float scale, float spacing, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		float total = -spacing;
		for (int i = 0; i < str.length(); i++) {
			total += mc.font.width(String.valueOf(str.charAt(i))) * scale + spacing;
		}
		float x = cx - total / 2f;
		for (int i = 0; i < str.length(); i++) {
			String ch = String.valueOf(str.charAt(i));
			text(ch, x, y, scale, argb);
			x += mc.font.width(ch) * scale + spacing;
		}
	}
}
