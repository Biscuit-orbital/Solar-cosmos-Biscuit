package com.biscuit.orbital.strike;

import com.biscuit.orbital.strike.impl.CarpetStrike;
import com.biscuit.orbital.strike.impl.CryoStrike;
import com.biscuit.orbital.strike.impl.InfernoStrike;
import com.biscuit.orbital.strike.impl.MeteorStrike;
import com.biscuit.orbital.strike.impl.NukeStrike;
import com.biscuit.orbital.strike.impl.RailgunStrike;
import com.biscuit.orbital.strike.impl.SingularityStrike;
import com.biscuit.orbital.strike.impl.StormStrike;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The strike catalogue. To rename a strike (brand it your way) or change its cooldown, edit it here.
 */
public final class Strikes {
    private Strikes() {
    }

    public static final List<StrikeType> ALL;

    static {
        List<StrikeType> l = new ArrayList<>();
        l.add(new StrikeType("railgun", "BIS-01", "Biscuit Railgun", Formatting.WHITE, Items.END_CRYSTAL, 5, 25,
                new String[]{"A beam from orbit drills a shaft through",
                        "the world, sky to bedrock. Nothing in",
                        "the column survives."},
                RailgunStrike::new));
        l.add(new StrikeType("carpet", "BIS-02", "Crumb Carpet", Formatting.DARK_RED, Items.TNT, 4, 30,
                new String[]{"A bomber squadron flies along your facing",
                        "direction and carpet-bombs a long runway."},
                CarpetStrike::new));
        l.add(new StrikeType("meteor", "BIS-03", "Choco-Chip Meteors", Formatting.GOLD, Items.MAGMA_BLOCK, 4, 40,
                new String[]{"A storm of burning meteors. Some of them",
                        "hunt players. Ends with a mother meteor."},
                MeteorStrike::new));
        l.add(new StrikeType("storm", "BIS-04", "Thunder Wafer", Formatting.YELLOW, Items.LIGHTNING_ROD, 4, 35,
                new String[]{"A thundercloud spirals chain lightning",
                        "outward, fuses sand to glass, then drops",
                        "a final judgement bolt."},
                StormStrike::new));
        l.add(new StrikeType("singularity", "BIS-05", "Black Hole Biscuit", Formatting.DARK_PURPLE, Items.ENDER_EYE, 5, 60,
                new String[]{"A black hole drags in everything and digs",
                        "the earth up, then collapses into a",
                        "supernova."},
                SingularityStrike::new));
        l.add(new StrikeType("cryo", "BIS-06", "Frostbite Cookie", Formatting.AQUA, Items.BLUE_ICE, 4, 35,
                new String[]{"An ice comet erupts a wave of ice spikes",
                        "and freezes everything solid. Then it",
                        "all shatters."},
                CryoStrike::new));
        l.add(new StrikeType("inferno", "BIS-07", "Toasted Tornado", Formatting.RED, Items.BLAZE_POWDER, 4, 40,
                new String[]{"A fireball lands and spawns a fire tornado",
                        "that hunts the nearest target."},
                InfernoStrike::new));
        l.add(new StrikeType("nuke", "BIS-08", "Mega Biscuit", Formatting.GREEN, Items.NETHER_STAR, 5, 120,
                new String[]{"Tactical nuke: blinding flash, giant crater,",
                        "shockwave, mushroom cloud and fallout."},
                NukeStrike::new));
        ALL = Collections.unmodifiableList(l);
    }

    public static StrikeType get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static StrikeType byId(String id) {
        for (StrikeType t : ALL) {
            if (t.id.equalsIgnoreCase(id) || t.code.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }
}
