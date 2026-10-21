package com.biscuit.orbital.client;

import com.biscuit.orbital.Settings;
import com.biscuit.orbital.net.SettingsC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;

/** The Uplink menu: floating white cards, tunable sliders and toggles. Drag the sliders, click the toggles. */
public class MenuScreen extends Screen {
	private static final int INK = 0xFF2A2630;
	private static final int MUTED = 0xFF9A96A4;
	private static final int ACCENT = 0xFFE23A5C;
	private static final String[] LOCKED_NAMES = {
			"BO-02 // MERIDIAN LINE", "BO-03 // HAMMERFALL", "BO-04 // SEVEN SPRINKLES",
			"BO-05 // TESSERACT WAFER", "BO-06 // ORRERY"};

	private boolean wasDown;
	private int dragging = -1;
	private final long openedAt = Util.getMillis();

	// layout (recomputed each frame)
	private float lx, ly, rx, ry, sx0, sx1;
	private final float[] rowY = new float[3];
	private final float[] togY = new float[2];

	public MenuScreen() {
		super(Component.literal("Biscuit Orbital Command"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Canvas c = new Canvas(graphics);
		try {
			layout(c.vw);
			interact(mouseX / c.s, mouseY / c.s);
			draw(c);
		} finally {
			c.end();
		}
	}

	private void layout(float vw) {
		float x0 = (vw - (460f + 120f + 600f)) / 2f;
		lx = x0;
		ly = 215f;
		rx = x0 + 580f;
		ry = 150f;
		sx0 = rx + 210f;
		sx1 = rx + 600f - 150f;
		for (int i = 0; i < 3; i++) {
			rowY[i] = ry + 132f + i * 62f;
		}
		togY[0] = ly + 440f + 24f;
		togY[1] = togY[0] + 76f;
	}

	private static boolean in(float mx, float my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private void interact(float mx, float my) {
		boolean down = Minecraft.getInstance().mouseHandler.isLeftPressed();
		boolean pressed = down && !wasDown;
		boolean released = !down && wasDown;
		wasDown = down;

		if (pressed) {
			for (int i = 0; i < 3; i++) {
				if (in(mx, my, sx0 - 16, rowY[i], sx1 - sx0 + 32, 62)) {
					dragging = i;
				}
			}
			if (in(mx, my, lx, togY[0], 460, 64)) {
				ClientSettings.current = ClientSettings.current.withCutscenes(!ClientSettings.current.cutscenes());
				commit();
			}
			if (in(mx, my, lx, togY[1], 460, 64)) {
				ClientSettings.current = ClientSettings.current.withEveryone(!ClientSettings.current.everyone());
				commit();
			}
		}
		if (down && dragging >= 0) {
			float p = Math.max(0f, Math.min(1f, (mx - sx0) / (sx1 - sx0)));
			Settings s = ClientSettings.current;
			switch (dragging) {
				case 0 -> s = s.withRadius(16 + Math.round(p * (512 - 16) / 8f) * 8);
				case 1 -> s = s.withDamage(p >= 0.97f ? 0 : 1 + Math.round(p / 0.97f * 99f));
				default -> s = s.withSpeed(0.5f + Math.round(p * 50f) / 20f);
			}
			ClientSettings.current = s;
		}
		if (released && dragging >= 0) {
			dragging = -1;
			commit();
		}
	}

	private void commit() {
		ClientSettings.save();
		ClientPlayNetworking.send(new SettingsC2S(ClientSettings.current));
	}

	// ------------------------------------------------------------------ drawing
	private void card(Canvas c, float x, float y, float w, float h, boolean accent) {
		c.rect(x + 7, y + 10, w, h, 0x24101020);
		c.rect(x, y, w, h, 0xFFFFFFFF);
		c.box(x, y, w, h, 2, 0xFFE6E1EA);
		if (accent) {
			c.rect(x, y, w, 6, ACCENT);
		}
	}

	private void draw(Canvas c) {
		float vw = c.vw, vh = Canvas.VH;
		float time = (Util.getMillis() - openedAt) / 1000f;
		Settings s = ClientSettings.current;

		c.rect(0, 0, vw, vh, 0xF2F8F6FB);

		// title on an arc
		String title = "BISCUIT ORBITAL COMMAND";
		float cx = vw / 2f, sc = 3.6f, sp = 18f;
		float total = -sp;
		for (int i = 0; i < title.length(); i++) {
			total += c.textWidth(String.valueOf(title.charAt(i)), sc) + sp;
		}
		float tx = cx - total / 2f;
		for (int i = 0; i < title.length(); i++) {
			String ch = String.valueOf(title.charAt(i));
			float w = c.textWidth(ch, sc);
			float mid = tx + w / 2f - cx;
			c.text(ch, tx, 62f + mid * mid * 0.00045f, sc, INK);
			tx += w + sp;
		}
		for (int i = 0; i < 64; i++) {
			float xa = cx - 470f + i * 940f / 64f, xb = cx - 470f + (i + 1) * 940f / 64f;
			float ya = 122f + (xa - cx) * (xa - cx) * 0.00045f, yb = 122f + (xb - cx) * (xb - cx) * 0.00045f;
			c.line(xa, ya, xb, yb, 2, MUTED);
		}

		// ---- left: remote card
		card(c, lx, ly, 460, 440, true);
		c.spaced("UPLINK REMOTE", lx + 230, ly + 34, 2.6f, 6f, INK);
		c.tex(Canvas.GLOW_GOLD, lx + 230 - 170, ly + 70, 340, 340, 256, 256);
		float bob = (float) Math.sin(time * 1.6f) * 5f;
		c.tex(Canvas.REMOTE, lx + 130, ly + 100 + bob, 200, 200, 32, 32);
		c.textCenter("A walkie-talkie from the BiscuitCorp", lx + 230, ly + 318, 1.7f, MUTED);
		c.textCenter("satellite. Aim, press, and the sky", lx + 230, ly + 342, 1.7f, MUTED);
		c.textCenter("answers. Probably does nothing... right?", lx + 230, ly + 366, 1.7f, MUTED);
		c.rect(lx + 230 - 70, ly + 394, 140, 32, 0xFFE4F6EA);
		c.textCenter("Carried", lx + 230, ly + 402, 1.8f, 0xFF2E8B57);

		// ---- left: toggles
		toggle(c, lx, togY[0], "Cutscenes", s.cutscenes());
		toggle(c, lx, togY[1], "Play for everyone", s.everyone());

		// ---- right: active strike card
		card(c, rx, ry, 600, 330, true);
		c.tex(Canvas.THUMB, rx + 22, ry + 26, 150, 94, 128, 80);
		c.text("BO-01 // ORBITAL RAILGUN", rx + 196, ry + 30, 2.3f, INK);
		c.text("THE CRUMB COMET", rx + 196, ry + 66, 1.7f, MUTED);
		c.rect(rx + 600 - 118, ry + 28, 92, 32, 0xFFFDE8EC);
		c.textCenter("READY", rx + 600 - 72, ry + 36, 1.8f, ACCENT);

		float pr = (s.radius() - 16) / (512f - 16f);
		float pd = s.damage() == 0 ? 1f : (s.damage() - 1) / 99f * 0.97f;
		float ps = (s.speed() - 0.5f) / 2.5f;
		slider(c, 0, "Radius", pr, Integer.toString(s.radius()));
		slider(c, 1, "Damage", pd, s.damage() == 0 ? "\u221E" : Integer.toString(s.damage()));
		slider(c, 2, "Speed", ps, String.format(Locale.ROOT, "%.2fx", s.speed()));

		// ---- right: locked strikes
		for (int i = 0; i < LOCKED_NAMES.length; i++) {
			float y = ry + 330 + 16 + i * 94 + (float) Math.sin(time * 1.3f + i) * 3f;
			card(c, rx, y, 600, 84, false);
			c.tex(Canvas.THUMB, rx + 18, y + 10, 100, 64, 128, 80);
			c.rect(rx + 18, y + 10, 100, 64, 0xCCFFFFFF);
			c.text(LOCKED_NAMES[i], rx + 140, y + 22, 2f, MUTED);
			c.text("Arrives in phase 2", rx + 140, y + 50, 1.6f, 0xFFC4C0CC);
			c.textRight("LOCKED", rx + 600 - 24, y + 32, 1.8f, 0xFFB8B4C2);
		}

		c.textCenter("Drag the sliders  //  Right-click with the remote to fire  //  Esc to leave", cx, vh - 56, 1.8f, MUTED);
	}

	private void toggle(Canvas c, float x, float y, String label, boolean on) {
		card(c, x, y, 460, 64, false);
		c.text(label, x + 26, y + 20, 2f, INK);
		float tx = x + 460 - 26 - 84, ty = y + 14;
		c.rect(tx, ty, 84, 36, on ? 0xFFF2B233 : 0xFFD8D4DE);
		c.rect(on ? tx + 84 - 32 : tx + 4, ty + 4, 28, 28, 0xFFFFFFFF);
		c.textRight(on ? "On" : "Off", tx - 14, y + 22, 1.8f, MUTED);
	}

	private void slider(Canvas c, int i, String label, float p, String value) {
		float y = rowY[i], cy = y + 31f;
		c.text(label, rx + 28, y + 20, 2f, INK);
		c.rect(sx0, cy - 3, sx1 - sx0, 6, 0xFFE8E3EC);
		c.rect(sx0, cy - 3, (sx1 - sx0) * p, 6, ACCENT);
		float kx = sx0 + (sx1 - sx0) * p;
		c.rect(kx - 14, cy - 14, 28, 28, 0xFFFFFFFF);
		c.box(kx - 14, cy - 14, 28, 28, 3, dragging == i ? 0xFF2A2630 : ACCENT);
		c.textRight(value, rx + 600 - 26, y + 20, 2f, INK);
	}
}
