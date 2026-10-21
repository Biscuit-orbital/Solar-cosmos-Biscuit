package com.biscuit.orbital.client;

import com.biscuit.orbital.BiscuitOrbital;
import com.biscuit.orbital.Settings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves the GUI settings to config/biscuit_orbital.properties. */
public final class ClientSettings {
	public static Settings current = Settings.DEFAULT;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("biscuit_orbital.properties");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (InputStream in = Files.newInputStream(f)) {
			Properties p = new Properties();
			p.load(in);
			current = new Settings(
					Integer.parseInt(p.getProperty("radius", "96")),
					Integer.parseInt(p.getProperty("damage", "0")),
					Float.parseFloat(p.getProperty("speed", "1.0")),
					Boolean.parseBoolean(p.getProperty("cutscenes", "true")),
					Boolean.parseBoolean(p.getProperty("everyone", "true"))).clamped();
		} catch (Exception e) {
			BiscuitOrbital.LOGGER.warn("Could not read Biscuit Orbital config, using defaults", e);
		}
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("radius", Integer.toString(current.radius()));
		p.setProperty("damage", Integer.toString(current.damage()));
		p.setProperty("speed", Float.toString(current.speed()));
		p.setProperty("cutscenes", Boolean.toString(current.cutscenes()));
		p.setProperty("everyone", Boolean.toString(current.everyone()));
		try (OutputStream out = Files.newOutputStream(file())) {
			p.store(out, "Biscuit Orbital Command");
		} catch (Exception e) {
			BiscuitOrbital.LOGGER.warn("Could not save Biscuit Orbital config", e);
		}
	}

	private ClientSettings() {
	}
}
