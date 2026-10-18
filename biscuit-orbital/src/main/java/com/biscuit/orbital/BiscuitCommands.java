package com.biscuit.orbital;

import com.biscuit.orbital.strike.StrikeManager;
import com.biscuit.orbital.strike.StrikeType;
import com.biscuit.orbital.strike.Strikes;
import com.biscuit.orbital.strike.Targeting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * /biscuit give | list | strike &lt;type&gt; [power] [pos] | terrain &lt;true|false&gt;   (operators only)
 * Handy for testing: "/biscuit strike nuke 1 ~ ~ ~" fires without needing the Uplink.
 */
public final class BiscuitCommands {
    private BiscuitCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("biscuit")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                        .executes(ctx -> give(ctx.getSource())))
                .then(CommandManager.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(CommandManager.literal("terrain")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> terrain(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(CommandManager.literal("strike")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(ids(), builder))
                                .executes(ctx -> strike(ctx.getSource(), StringArgumentType.getString(ctx, "type"), 1, null))
                                .then(CommandManager.argument("power", IntegerArgumentType.integer(1, 3))
                                        .executes(ctx -> strike(ctx.getSource(), StringArgumentType.getString(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "power"), null))
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> strike(ctx.getSource(), StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "power"),
                                                        BlockPosArgumentType.getBlockPos(ctx, "pos"))))))));
    }

    private static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (StrikeType t : Strikes.ALL) {
            out.add(t.id);
        }
        return out;
    }

    private static int give(ServerCommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        p.getInventory().offerOrDrop(new ItemStack(BiscuitOrbital.UPLINK));
        src.sendFeedback(() -> Text.literal("Biscuit Uplink delivered."), false);
        return 1;
    }

    private static int list(ServerCommandSource src) {
        for (StrikeType t : Strikes.ALL) {
            src.sendFeedback(() -> Text.literal(t.code + "  " + t.id + "  -  " + t.name).formatted(t.color), false);
        }
        return Strikes.ALL.size();
    }

    private static int terrain(ServerCommandSource src, boolean enabled) {
        BiscuitConfig.terrainDamage = enabled;
        BiscuitConfig.save();
        src.sendFeedback(() -> Text.literal("Biscuit strikes " + (enabled ? "now DESTROY terrain." : "no longer destroy terrain."))
                .formatted(enabled ? Formatting.RED : Formatting.GREEN), true);
        return 1;
    }

    private static int strike(ServerCommandSource src, String id, int power, BlockPos at) {
        StrikeType type = Strikes.byId(id);
        if (type == null) {
            src.sendError(Text.literal("Unknown strike '" + id + "'. Use /biscuit list"));
            return 0;
        }
        ServerWorld world = src.getWorld();
        ServerPlayerEntity player = src.getPlayer();
        Vec3d target = null;
        if (at != null) {
            target = new Vec3d(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        } else if (player != null) {
            target = Targeting.find(world, player, BiscuitConfig.targetRange);
        }
        if (target == null) {
            target = src.getPosition();
        }
        if (!StrikeManager.start(type, world, player, target, power, BiscuitConfig.casterSafeDefault)) {
            src.sendError(Text.literal("Orbital network busy - too many strikes running."));
            return 0;
        }
        Vec3d shown = target;
        src.sendFeedback(() -> Text.literal(type.code + " " + type.name + " launched at "
                + (int) shown.x + " " + (int) shown.y + " " + (int) shown.z).formatted(type.color), true);
        return 1;
    }
}
