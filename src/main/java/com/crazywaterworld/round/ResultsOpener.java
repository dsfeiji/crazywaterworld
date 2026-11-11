package com.crazywaterworld.round;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class ResultsOpener {
	private ResultsOpener() {
	}

	public static void open(ServerPlayer player, String summary) {
		if (!GameSwitch.active(player.getServer())) return;
		player.openMenu(new ExtendedScreenHandlerFactory<String>() {
			@Override
			public ResultsMenu createMenu(int id, Inventory inventory, Player ignored) {
				return new ResultsMenu(id, inventory, summary);
			}

			@Override
			public Component getDisplayName() {
				return Component.literal("疯狂水世界 · 本局结算");
			}

			@Override
			public String getScreenOpeningData(ServerPlayer recipient) {
				return summary;
			}
		});
	}
}
