package com.crazywaterworld.shop;

import com.crazywaterworld.round.GameSwitch;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * 打开商店的入口。服务端调用 openMenu，并把打开位置作为额外数据传给客户端。
 */
public final class ShopOpener {
	private ShopOpener() {
	}

	public static void open(ServerPlayer player, BlockPos pos) {
		if (!GameSwitch.active(player.getServer())) return;
		player.openMenu(new ExtendedScreenHandlerFactory<BlockPos>() {
			@Override
			public ShopMenu createMenu(int containerId, Inventory inv, Player p) {
				return new ShopMenu(containerId, inv, pos);
			}

			@Override
			public Component getDisplayName() {
				return Component.literal("疯狂水世界商店");
			}

			@Override
			public BlockPos getScreenOpeningData(ServerPlayer serverPlayer) {
				return pos;
			}
		});
	}
}
