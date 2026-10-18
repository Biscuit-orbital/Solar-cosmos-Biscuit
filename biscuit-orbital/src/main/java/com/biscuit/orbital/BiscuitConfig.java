package com.biscuit.orbital;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny properties-file config: config/biscuit_orbital.properties (created on first start). */
public final class BiscuitConfig {
    private BiscuitConfig() {
    }

    /** false = strikes only hurt entities and make effects, they never destroy blocks. */
    public static boolean terrainDamage = true;
    /** true = only operators (permission level 2) can fire the Biscuit Uplink. */
    public static boolean requireOp = false;
    /** Default for the "Caster Safety" toggle on a fresh Uplink (the caster is spared by his own strikes). */
    public static boolean casterSafeDefault = true;
    /** Multiplies all strike damage. */
    public static double damageMultiplier = 1.0;
    /** Multiplies all cooldowns (0 = no cooldowns). */
    public static double cooldownScale = 1.0;
    /** Milliseconds per tick spent removing blocks when only a little terrain is queued. */
    public static int blockBudgetMs = 6;
    /** Upper limit of that budget: it grows automatically with the backlog so huge craters (nuke) finish in seconds. */
    public static int blockBudgetMaxMs = 30;
    /** Strikes that may run at the same time. */
    public static int maxActiveStrikes = 6;
    /** How far (blocks) the Uplink can lock a target. Only loaded chunks can be targeted. */
    public static int targetRange = 300;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("biscuit_orbital.properties");
    }

    public static void load() {
        Properties p = new Properties();
        Path file = path();
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    p.load(in);
                }
            }
            terrainDamage = bool(p, "terrainDamage", terrainDamage);
            requireOp = bool(p, "requireOp", requireOp);
            casterSafeDefault = bool(p, "casterSafeDefault", casterSafeDefault);
            damageMultiplier = dbl(p, "damageMultiplier", damageMultiplier);
            cooldownScale = dbl(p, "cooldownScale", cooldownScale);
            blockBudgetMs = Math.max(1, Math.min(40, (int) dbl(p, "blockBudgetMs", blockBudgetMs)));
            blockBudgetMaxMs = Math.max(blockBudgetMs, Math.min(45, (int) dbl(p, "blockBudgetMaxMs", blockBudgetMaxMs)));
            maxActiveStrikes = Math.max(1, Math.min(32, (int) dbl(p, "maxActiveStrikes", maxActiveStrikes)));
            targetRange = Math.max(16, Math.min(600, (int) dbl(p, "targetRange", targetRange)));
            save();
        } catch (Exception e) {
            BiscuitOrbital.LOGGER.warn("Could not read {} - using defaults", file, e);
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("terrainDamage", String.valueOf(terrainDamage));
        p.setProperty("requireOp", String.valueOf(requireOp));
        p.setProperty("casterSafeDefault", String.valueOf(casterSafeDefault));
        p.setProperty("damageMultiplier", String.valueOf(damageMultiplier));
        p.setProperty("cooldownScale", String.valueOf(cooldownScale));
        p.setProperty("blockBudgetMs", String.valueOf(blockBudgetMs));
        p.setProperty("blockBudgetMaxMs", String.valueOf(blockBudgetMaxMs));
        p.setProperty("maxActiveStrikes", String.valueOf(maxActiveStrikes));
        p.setProperty("targetRange", String.valueOf(targetRange));
        try (OutputStream out = Files.newOutputStream(path())) {
            p.store(out, "Biscuit Orbital Command config. terrainDamage=false -> strikes never destroy blocks.");
        } catch (Exception e) {
            BiscuitOrbital.LOGGER.warn("Could not write config", e);
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static double dbl(Properties p, String key, double def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
