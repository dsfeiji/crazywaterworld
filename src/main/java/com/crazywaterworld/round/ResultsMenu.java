package com.crazywaterworld.round;

import com.crazywaterworld.shop.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Read-only results screen. The server sends the final standings on open. */
public final class ResultsMenu extends AbstractContainerMenu {
	public final String summary;

	public ResultsMenu(int id, Inventory inventory, String summary) {
		super(ModMenus.RESULTS, id);
		this.summary = summary;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}
}
