package com.biscuit.orbital.fx;

import java.util.Arrays;

/**
 * Pre-computed block shapes. Each long packs: [sort key | dx | dy | dz], so sorting the array sorts the
 * blocks by key (inside-out for spheres, top-down for the railgun bore) with a ragged, natural-looking edge.
 */
public final class Shapes {
    private Shapes() {
    }

    static long pack(long key, int x, int y, int z) {
        return (key << 33) | ((long) (x + 1024) << 22) | ((long) (y + 1024) << 11) | (long) (z + 1024);
    }

    public static int dx(long v) {
        return (int) ((v >>> 22) & 2047L) - 1024;
    }

    public static int dy(long v) {
        return (int) ((v >>> 11) & 2047L) - 1024;
    }

    public static int dz(long v) {
        return (int) (v & 2047L) - 1024;
    }

    private static double noise(int x, int y, int z, long seed) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ seed * 2654435761L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return (h & 0xFFFFL) / 65535.0;
    }

    /**
     * Ragged sphere / ellipsoid sorted from the centre outwards.
     *
     * @param yScale    1 = sphere, below 1 = flatter crater
     * @param innerR    skip everything closer than this (use to carve only a growing shell)
     * @param lowerOnly only the lower half (y <= 0)
     */
    public static long[] sphere(double r, double yScale, double innerR, boolean lowerOnly, long seed) {
        int rx = Math.min(1000, (int) Math.ceil(r * 1.15));
        int ry = Math.min(1000, (int) Math.ceil(r * yScale * 1.15));
        double inner2 = innerR * innerR;
        long[] buf = new long[4096];
        int n = 0;
        for (int x = -rx; x <= rx; x++) {
            for (int y = -ry; y <= ry; y++) {
                if (lowerOnly && y > 0) {
                    continue;
                }
                double ys = y / yScale;
                for (int z = -rx; z <= rx; z++) {
                    double d2 = x * x + ys * ys + z * z;
                    double rr = r * (0.88 + 0.24 * noise(x, y, z, seed));
                    if (d2 > rr * rr) {
                        continue;
                    }
                    if (inner2 > 0 && d2 < inner2) {
                        continue;
                    }
                    if (n == buf.length) {
                        buf = Arrays.copyOf(buf, n * 2);
                    }
                    buf[n++] = pack((long) (Math.sqrt(d2) * 16.0), x, y, z);
                }
            }
        }
        long[] out = Arrays.copyOf(buf, n);
        Arrays.sort(out);
        return out;
    }

    /** Vertical cylinder from topOff down to bottomOff (offsets relative to the origin), ordered top-down. */
    public static long[] cylinder(double r, int topOff, int bottomOff, long seed) {
        int top = Math.min(topOff, 1000);
        int bottom = Math.max(bottomOff, -1000);
        int ri = Math.min(1000, (int) Math.ceil(r * 1.12));
        long[] buf = new long[4096];
        int n = 0;
        for (int y = top; y >= bottom; y--) {
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double rr = r * (0.90 + 0.20 * noise(x, y, z, seed));
                    if (x * x + z * z > rr * rr) {
                        continue;
                    }
                    long key = ((long) (top - y) << 8) | (long) ((x * 31 + z * 17 + y * 7) & 255);
                    if (n == buf.length) {
                        buf = Arrays.copyOf(buf, n * 2);
                    }
                    buf[n++] = pack(key, x, y, z);
                }
            }
        }
        long[] out = Arrays.copyOf(buf, n);
        Arrays.sort(out);
        return out;
    }
}
