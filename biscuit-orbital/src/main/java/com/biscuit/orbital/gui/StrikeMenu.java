package com.biscuit.orbital.gui;

import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.item.UplinkItem;
import com.biscuit.orbital.strike.StrikeType;
import com.biscuit.orbital.strike.Strikes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * The strike selection screen: a normal chest GUI driven entirely by the server (no client code needed).
 * Every click is intercepted: nothing can be taken out, the buttons just change the Uplink's settings.
 */
public class StrikeMenu extends ScreenHandler {
    private static final int SIZE = 36;
    private static final int[] STRIKE_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};
    private static final int BRAND_SLOT = 4;
    private static final int POWER_SLOT = 29;
    private static final int SAFE_SLOT = 31;
    private static final int CLOSE_SLOT = 33;

    private final SimpleInventory menu;
    private final Hand hand;

    private StrikeMenu(int syncId, PlayerInventory playerInventory, SimpleInventory menu, Hand hand) {
        super(ScreenHandlerType.GENERIC_9X4, syncId);
        this.menu = menu;
        this.hand = hand;
        // The vanilla client opens a normal 9x4 chest screen and expects exactly this slot order:
        // 36 menu slots, then the 27 main inventory slots, then the 9 hotbar slots.
        for (int i = 0; i < SIZE; i++) {
            this.addSlot(new Slot(menu, i, 0, 0));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 0, 0));
        }
    }

    public static void open(ServerPlayerEntity player, Hand hand) {
        SimpleInventory inv = new SimpleInventory(SIZE);
        fill(inv, player.getStackInHand(hand));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new StrikeMenu(syncId, playerInventory, inv, hand),
                Text.literal("\u2726 BISCUIT ORBITAL COMMAND \u2726")));
        Fx.soundAs(player.getServerWorld(), player.getX(), player.getY(), player.getZ(), ModSounds.UPLINK_OPEN,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= SIZE || !(player instanceof ServerPlayerEntity sp)) {
            return;
        }
        ItemStack uplink = sp.getStackInHand(hand);
        if (!(uplink.getItem() instanceof UplinkItem)) {
            sp.closeHandledScreen();
            return;
        }
        for (int i = 0; i < STRIKE_SLOTS.length; i++) {
            if (slotIndex == STRIKE_SLOTS[i]) {
                UplinkItem.setStrike(uplink, i);
                StrikeType t = Strikes.get(i);
                sp.sendMessage(Text.literal("Uplink armed: " + t.code + " " + t.name).formatted(t.color), true);
                click(sp, 1.4f);
                fill(menu, uplink);
                return;
            }
        }
        if (slotIndex == POWER_SLOT) {
            int next = UplinkItem.getPower(uplink) % 3 + 1;
            UplinkItem.setPower(uplink, next);
            click(sp, 0.8f + 0.3f * next);
            fill(menu, uplink);
        } else if (slotIndex == SAFE_SLOT) {
            UplinkItem.setSafe(uplink, !UplinkItem.isSafe(uplink));
            click(sp, 1.0f);
            fill(menu, uplink);
        } else if (slotIndex == CLOSE_SLOT) {
            sp.closeHandledScreen();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.getStackInHand(hand).getItem() instanceof UplinkItem;
    }

    private static void click(ServerPlayerEntity sp, float pitch) {
        Fx.soundAs(sp.getServerWorld(), sp.getX(), sp.getY(), sp.getZ(), ModSounds.BEEP, SoundCategory.PLAYERS, 0.6f, pitch);
    }

    // ------------------------------------------------------------------ building the screen

    private static void fill(SimpleInventory inv, ItemStack uplink) {
        int selected = UplinkItem.getStrike(uplink);
        int power = UplinkItem.getPower(uplink);
        boolean safe = UplinkItem.isSafe(uplink);

        ItemStack dark = icon(Items.BROWN_STAINED_GLASS_PANE, 1, plain(" ", Formatting.GRAY), null, false);
        ItemStack amber = icon(Items.ORANGE_STAINED_GLASS_PANE, 1, plain(" ", Formatting.GRAY), null, false);
        for (int i = 0; i < SIZE; i++) {
            boolean edge = i < 9 || i >= 27;
            inv.setStack(i, (edge ? amber : dark).copy());
        }

        List<Text> brand = new ArrayList<>();
        brand.add(plain("Orbital strikes on demand.", Formatting.GRAY));
        brand.add(plain("Pick a strike here, then right-click", Formatting.GRAY));
        brand.add(plain("the Uplink at your target.", Formatting.GRAY));
        inv.setStack(BRAND_SLOT, icon(Items.COOKIE, 1, bold("BISCUIT ORBITAL COMMAND", Formatting.GOLD), brand, true));

        for (int i = 0; i < STRIKE_SLOTS.length; i++) {
            StrikeType t = Strikes.get(i);
            boolean sel = i == selected;
            List<Text> lore = new ArrayList<>();
            lore.add(plain(stars(t.danger) + "   " + t.code, t.color));
            for (String line : t.lore) {
                lore.add(plain(line, Formatting.GRAY));
            }
            lore.add(plain(" ", Formatting.GRAY));
            lore.add(plain("Cooldown: " + t.cooldownSeconds + "s", Formatting.DARK_GRAY));
            lore.add(sel ? bold("\u2714 SELECTED", Formatting.GREEN) : plain("Click to select", Formatting.YELLOW));
            inv.setStack(STRIKE_SLOTS[i], icon(t.icon, 1, bold(t.name, t.color), lore, sel));
        }

        List<Text> pl = new ArrayList<>();
        pl.add(plain("Scales radius, damage and number of hits.", Formatting.GRAY));
        pl.add(plain("I = standard   II = heavy   III = apocalyptic", Formatting.GRAY));
        pl.add(plain(" ", Formatting.GRAY));
        pl.add(plain("Click to cycle", Formatting.YELLOW));
        inv.setStack(POWER_SLOT, icon(Items.BEACON, power, bold("Strike Power: " + UplinkItem.roman(power), Formatting.AQUA), pl, false));

        List<Text> sl = new ArrayList<>();
        if (safe) {
            sl.add(plain("You are NOT hurt by your own strikes.", Formatting.GRAY));
            sl.add(plain(" ", Formatting.GRAY));
            sl.add(plain("Click to turn OFF (hardcore)", Formatting.YELLOW));
            inv.setStack(SAFE_SLOT, icon(Items.SHIELD, 1, bold("Caster Safety: ON", Formatting.GREEN), sl, false));
        } else {
            sl.add(plain("Your own strikes CAN kill you!", Formatting.RED));
            sl.add(plain(" ", Formatting.GRAY));
            sl.add(plain("Click to turn ON", Formatting.YELLOW));
            inv.setStack(SAFE_SLOT, icon(Items.SKELETON_SKULL, 1, bold("Caster Safety: OFF", Formatting.RED), sl, false));
        }

        inv.setStack(CLOSE_SLOT, icon(Items.BARRIER, 1, bold("Close", Formatting.RED), null, false));
    }

    private static String stars(int danger) {
        int d = Math.max(0, Math.min(5, danger));
        return "\u2605".repeat(d) + "\u2606".repeat(5 - d);
    }

    private static MutableText plain(String s, Formatting color) {
        return Text.literal(s).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static MutableText bold(String s, Formatting color) {
        return Text.literal(s).setStyle(Style.EMPTY.withColor(color).withBold(true).withItalic(false));
    }

    private static ItemStack icon(Item item, int count, Text name, List<Text> lore, boolean glow) {
        ItemStack st = new ItemStack(item, count);
        st.setCustomName(name);
        if (lore != null && !lore.isEmpty()) {
            NbtList list = new NbtList();
            for (Text line : lore) {
                list.add(NbtString.of(Text.Serializer.toJson(line)));
            }
            st.getOrCreateSubNbt("display").put("Lore", list);
        }
        if (glow) {
            st.addEnchantment(Enchantments.UNBREAKING, 1);
        }
        st.getOrCreateNbt().putInt("HideFlags", 127);
        return st;
    }
}
