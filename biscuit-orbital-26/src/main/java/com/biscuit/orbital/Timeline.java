package com.biscuit.orbital;

/** Shared cinematic timeline (scene-seconds at speed 1.0). Server and client both read this so they stay in sync. */
public final class Timeline {
	public static final float GROUND_END = 2.5F;
	public static final float ASCENT_END = 8.5F;
	public static final float EARTH_END = 12.5F;
	public static final float SOLAR_END = 16.5F;
	public static final float GALAXY_END = 20.5F;
	public static final float FIRE_START = 25.0F;
	public static final float IMPACT = 26.5F;
	public static final float IMPACT_END = 38.0F;
	public static final float TOTAL = 40.0F;

	private Timeline() {
	}
}
