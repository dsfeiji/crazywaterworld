package com.crazywaterworld.economy;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.round.RoundGame;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 金币的统一入口。所有金币变动都经过这里，服务端负责校验和胜利判定。
 */
public final class Economy {
	private Economy() {
	}

	public static GoldData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(GoldData.FACTORY, GoldData.NAME);
	}

	public static long balance(MinecraftServer server, UUID player) {
		return data(server).get(player);
	}

	/**
	 * 把玩家背包里所有可出售物品换成金币。
	 * @return 本次获得的金币数量
	 */
	public static boolean autoSellable(ItemStack stack) {
		return !stack.isEmpty() && ItemValues.priceOf(stack.getItem()) > 0;
	}

	public static long sellAll(MinecraftServer server, ServerPlayer player) {
		if (!GameSwitch.active(server)) return 0;
		if (data(server).finished()) return 0;
		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> items = inventory.items;
		long earned = 0;

		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (!autoSellable(stack)) continue;
			long price = MarketData.price(server, stack.getItem());
			earned = Math.addExact(earned, price * stack.getCount());
		}

		if (earned > 0) {
			GoldData data = data(server);
			if (data.get(player.getUUID()) > Long.MAX_VALUE - earned) return 0;
			long total = data.get(player.getUUID()) + earned;
			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				if (!autoSellable(stack)) continue;
				MarketData.sold(server, stack.getItem(), stack.getCount());
				inventory.setItem(i, ItemStack.EMPTY);
			}
			data.set(player.getUUID(), total);
			afterGain(server, player, total);
		}
		return earned;
	}

	/** 扣除玩家金币，余额不足返回 false（服务端调用） */
	public static boolean spend(ServerPlayer player, long amount) {
		if (!GameSwitch.active(player.getServer())) return false;
		if (amount < 0) return false;
		if (amount == 0) return true;
		GoldData data = data(player.getServer());
		if (data.finished()) return false;
		long current = data.get(player.getUUID());
		if (current < amount) {
			return false;
		}
		data.set(player.getUUID(), current - amount);
		return true;
	}

	/** Atomically exchange offers on the server thread; insufficient balances change nothing. */
	public static boolean exchange(ServerPlayer first, ServerPlayer second, long offeredFirst, long offeredSecond) {
		if (!GameSwitch.active(first.getServer())) return false;
		if (offeredFirst < 0 || offeredSecond < 0 || first == second || first.getServer() != second.getServer()) {
			return false;
		}
		GoldData data = data(first.getServer());
		if (data.finished()) return false;
		long firstBalance = data.get(first.getUUID());
		long secondBalance = data.get(second.getUUID());
		if (firstBalance < offeredFirst || secondBalance < offeredSecond) return false;
		long nextFirst;
		long nextSecond;
		try {
			nextFirst = Math.addExact(firstBalance - offeredFirst, offeredSecond);
			nextSecond = Math.addExact(secondBalance - offeredSecond, offeredFirst);
		} catch (ArithmeticException e) {
			return false;
		}
		data.set(first.getUUID(), nextFirst);
		data.set(second.getUUID(), nextSecond);
		if (nextFirst > firstBalance) afterGain(first.getServer(), first, nextFirst);
		if (nextSecond > secondBalance) afterGain(first.getServer(), second, nextSecond);
		return true;
	}

	/** 给玩家加金币并检查胜利（服务端调用） */
	public static void credit(ServerPlayer player, long amount) {
		if (!GameSwitch.active(player.getServer())) return;
		GoldData data = data(player.getServer());
		if (amount <= 0 || data.finished()) return;
		long total = Math.addExact(data.get(player.getUUID()), amount);
		data.set(player.getUUID(), total);
		afterGain(player.getServer(), player, total);
	}

	private static final int[] MILESTONES = {90, 75, 50, 25};

	/** 金币增加后：首次越过 25/50/75/90% 时全服播报进度，首次达到目标时宣布胜利 */
	private static void afterGain(MinecraftServer server, ServerPlayer player, long total) {
		long target = CrazyWaterWorld.CONFIG.winTarget;
		GoldData data = data(server);
		if (total < target) {
			for (int percent : MILESTONES) {
				if (total * 100 >= target * percent) {
					if (data.reachMilestone(player.getUUID(), percent)) {
						server.getPlayerList().broadcastSystemMessage(Component.literal("§e[疯狂水世界] "
								+ player.getName().getString() + " 已完成目标的 " + percent + "%（" + total + " / " + target + " 金币）"), false);
					}
					break;
				}
			}
		}
		if (total >= target && data(server).declareWinner(player.getUUID(), player.getName().getString(),
				server.overworld().getGameTime())) RoundGame.scheduleResults(server);
	}
}
