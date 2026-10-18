package com.biscuit.orbital.item;

import com.biscuit.orbital.BiscuitConfig;
import com.biscuit.orbital.ModSounds;
import com.biscuit.orbital.fx.Fx;
import com.biscuit.orbital.gui.StrikeMenu;
import com.biscuit.orbital.strike.StrikeManager;
import com.biscuit.orbital.strike.StrikeType;
import com.biscuit.orbital.strike.Strikes;
import com.biscuit.orbital.strike.Targeting;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Biscuit Uplink: a walkie-talkie to the Biscuit orbital network.
 * <ul>
 *   <li>Sneak + right-click: open the strike menu (pick strike, power, caster safety)</li>
 *   <li>Right-click: fire the selected strike at the block under your crosshair</li>
 * </ul>
 * The selection is stored in the item itself (NBT), so every Uplink remembers its own settings.
 */
public class UplinkItem extends Item {
    private static final String K_STRIKE = "Strike";
    private static final String K_POWER = "Power";
    private static final String K_SAFE = "Safe";

    /** Server tick at which each player may fire again. */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public UplinkItem(Settings settings) {
        super(settings);
    }

    public static void clearCooldowns() {
        READY_AT.clear();
    }

    // ------------------------------------------------------------------ stored settings

    public static int getStrike(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n != null && n.contains(K_STRIKE)) {
            return Math.floorMod(n.getInt(K_STRIKE), Strikes.ALL.size());
        }
        return 0;
    }

    public static void setStrike(ItemStack stack, int index) {
        stack.getOrCreateNbt().putInt(K_STRIKE, Math.floorMod(index, Strikes.ALL.size()));
    }

    public static int getPower(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n != null && n.contains(K_POWER)) {
            return Math.max(1, Math.min(3, n.getInt(K_POWER)));
        }
        return 1;
    }

    public static void setPower(ItemStack stack, int power) {
        stack.getOrCreateNbt().putInt(K_POWER, Math.max(1, Math.min(3, power)));
    }

    public static boolean isSafe(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n != null && n.contains(K_SAFE)) {
            return n.getBoolean(K_SAFE);
        }
        return BiscuitConfig.casterSafeDefault;
    }

    public static void setSafe(ItemStack stack, boolean safe) {
        stack.getOrCreateNbt().putBoolean(K_SAFE, safe);
    }

    public static String roman(int power) {
        if (power <= 1) {
            return "I";
        }
        return power == 2 ? "II" : "III";
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld serverWorld = (ServerWorld) world;

        if (BiscuitConfig.requireOp && !player.getCommandSource().hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("Biscuit Uplink: operator permission required.").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        if (player.isSneaking()) {
            StrikeMenu.open(player, hand);
            return TypedActionResult.success(stack);
        }
        try {
            fire(serverWorld, player, stack);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Uplink error: " + e.getClass().getSimpleName()).formatted(Formatting.RED), true);
            com.biscuit.orbital.BiscuitOrbital.LOGGER.error("Uplink failed", e);
        }
        return TypedActionResult.success(stack);
    }

    private static void fire(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        StrikeType type = Strikes.get(getStrike(stack));
        long now = world.getServer().getTicks();
        Long ready = READY_AT.get(player.getUuid());
        if (!player.isCreative() && ready != null && now < ready) {
            double secs = (ready - now) / 20.0;
            player.sendMessage(Text.literal(String.format(Locale.ROOT, "Uplink recharging... %.1fs", secs))
                    .formatted(Formatting.RED), true);
            return;
        }

        Vec3d target = Targeting.find(world, player, BiscuitConfig.targetRange);
        if (target == null) {
            player.sendMessage(Text.literal("No target lock: aim at a block within " + BiscuitConfig.targetRange
                    + " blocks (loaded chunks only).").formatted(Formatting.RED), true);
            return;
        }
        if (!StrikeManager.start(type, world, player, target, getPower(stack), isSafe(stack))) {
            player.sendMessage(Text.literal("Orbital network busy - wait for the running strikes to finish.")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (!player.isCreative()) {
            READY_AT.put(player.getUuid(), now + (long) (type.cooldownSeconds * 20.0 * BiscuitConfig.cooldownScale));
        }
        Fx.soundAs(world, player.getX(), player.getEyeY(), player.getZ(), ModSounds.UPLINK_LOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.literal(type.code + " " + type.name + " locked at "
                + (int) target.x + " " + (int) target.y + " " + (int) target.z).formatted(type.color), true);
    }

    /** Live HUD in the action bar while the Uplink is in the selected hotbar slot. */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !selected || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        if (world.getTime() % 20 != 0) {
            return;
        }
        StrikeType type = Strikes.get(getStrike(stack));
        MutableText msg = Text.literal("\u2726 BISCUIT UPLINK  ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(type.code + " " + type.name).formatted(type.color, Formatting.BOLD))
                .append(Text.literal("  |  Power " + roman(getPower(stack)) + "  |  Safe "
                        + (isSafe(stack) ? "ON" : "OFF") + "  |  ").formatted(Formatting.GRAY));
        long now = ((ServerWorld) world).getServer().getTicks();
        Long ready = READY_AT.get(player.getUuid());
        if (!player.isCreative() && ready != null && now < ready) {
            msg.append(Text.literal(String.format(Locale.ROOT, "RECHARGING %.1fs", (ready - now) / 20.0))
                    .formatted(Formatting.RED, Formatting.BOLD));
        } else {
            msg.append(Text.literal("READY").formatted(Formatting.GREEN, Formatting.BOLD));
        }
        player.sendMessage(msg, true);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        StrikeType type = Strikes.get(getStrike(stack));
        tooltip.add(Text.literal("Biscuit Orbital Command").formatted(Formatting.GOLD, Formatting.ITALIC));
        tooltip.add(Text.literal("Right-click: fire at your crosshair").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + right-click: strike menu").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Selected: " + type.code + " " + type.name).formatted(type.color));
        tooltip.add(Text.literal("Power " + roman(getPower(stack)) + "  |  Caster safety "
                + (isSafe(stack) ? "ON" : "OFF")).formatted(Formatting.DARK_GRAY));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
