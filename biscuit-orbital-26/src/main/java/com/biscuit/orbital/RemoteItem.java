package com.biscuit.orbital;

import com.biscuit.orbital.net.OpenMenuS2C;
import com.biscuit.orbital.server.StrikeManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click = fire the orbital strike at the block you look at. Shift + right-click = open the uplink menu. */
public class RemoteItem extends Item {
	public RemoteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (serverPlayer.isShiftKeyDown()) {
			ServerPlayNetworking.send(serverPlayer, OpenMenuS2C.INSTANCE);
		} else {
			StrikeManager.tryFire(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}
