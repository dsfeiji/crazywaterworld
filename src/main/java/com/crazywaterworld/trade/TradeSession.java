package com.crazywaterworld.trade;

import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * One server-thread escrow for both parties. Never trust a client's ready or money values.
 * Only clams may be offered. Each side's coin amount is its asking price: what that side RECEIVES
 * from the other player for the clams it gives. Asks are netted, so only one side ever pays.
 * A side may only ask for coins while it is giving at least one clam, and a trade without any
 * clam is rejected, so coins cannot simply be gifted between players.
 */
public final class TradeSession {
	public static final int SLOTS = 4;
	/** After any offer change, confirming is blocked briefly so a last-second swap cannot be accepted blindly. */
	public static final int CONFIRM_DELAY_TICKS = 40;

	private final ServerPlayer first;
	private final ServerPlayer second;
	private final SimpleContainer firstItems = offer(true);
	private final SimpleContainer secondItems = offer(false);
	private int firstCoins;
	private int secondCoins;
	private boolean firstReady;
	private boolean secondReady;
	private boolean active = true;
	private int changedAt;

	TradeSession(ServerPlayer first, ServerPlayer second) {
		this.first = first;
		this.second = second;
		this.changedAt = first.getServer().getTickCount();
	}

	public static boolean tradable(ItemStack stack) {
		return stack.is(ModItems.CLAM);
	}

	private SimpleContainer offer(boolean firstSide) {
		return new SimpleContainer(SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				// Nothing left to sell means nothing to ask for.
				if (clams(this) == 0) {
					if (firstSide) firstCoins = 0;
					else secondCoins = 0;
				}
				offerChanged();
			}

			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return tradable(stack);
			}
		};
	}

	private void offerChanged() {
		firstReady = secondReady = false;
		if (first != null) changedAt = first.getServer().getTickCount();
	}

	public boolean active() {
		return active;
	}

	public boolean valid() {
		return active && GameSwitch.active(first.getServer())
				&& first.getServer().getPlayerList().getPlayer(first.getUUID()) == first
				&& second.getServer().getPlayerList().getPlayer(second.getUUID()) == second
				&& first.level() == second.level() && first.distanceToSqr(second) <= 64;
	}

	public ServerPlayer other(ServerPlayer player) {
		return player == first ? second : first;
	}

	public SimpleContainer items(ServerPlayer player) {
		return player == first ? firstItems : secondItems;
	}

	/** The asking price of this player: coins they receive from the other side. */
	public int coins(ServerPlayer player) {
		return player == first ? firstCoins : secondCoins;
	}

	public boolean ready(ServerPlayer player) {
		return player == first ? firstReady : secondReady;
	}

	/** Ticks left before either side may confirm again. */
	public int cooldown() {
		return Math.max(0, CONFIRM_DELAY_TICKS - (first.getServer().getTickCount() - changedAt));
	}

	public void adjust(ServerPlayer player, int delta) {
		setCoins(player, (long) coins(player) + delta);
	}

	public void setCoins(ServerPlayer player, long amount) {
		if (!valid()) {
			cancel();
			return;
		}
		int current = coins(player);
		long next = clams(items(player)) == 0 ? 0L : Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
		if (next == current) return;
		if (player == first) firstCoins = (int) next;
		else secondCoins = (int) next;
		offerChanged();
	}

	public void confirm(ServerPlayer player) {
		if (!valid()) {
			cancel();
			return;
		}
		boolean ready = ready(player);
		if (!ready && cooldown() > 0) return;
		if (!ready && clams(firstItems) + clams(secondItems) == 0) {
			player.sendSystemMessage(Component.literal("§e交易至少要包含一个河蚌，金币不能单独赠送"));
			return;
		}
		if (player == first) firstReady = !ready;
		else secondReady = !ready;
		if (!firstReady || !secondReady) return;
		if (!onlyClams(firstItems) || !onlyClams(secondItems)) {
			firstReady = secondReady = false;
			return;
		}
		// Positive: first pays second; negative: second pays first.
		long firstPays = (long) secondCoins - firstCoins;
		if (!Economy.exchange(first, second, Math.max(0, firstPays), Math.max(0, -firstPays))) {
			firstReady = secondReady = false;
			ServerPlayer payer = firstPays > 0 ? first : second;
			Component failed = Component.literal("§c交易失败：" + payer.getName().getString() + " 的金币不足以支付要价，已取消确认");
			first.sendSystemMessage(failed);
			second.sendSystemMessage(failed);
			return;
		}
		active = false;
		TradeManager.forget(this, first, second);
		first.sendSystemMessage(summary(clams(secondItems), clams(firstItems), -firstPays));
		second.sendSystemMessage(summary(clams(firstItems), clams(secondItems), firstPays));
		transfer(firstItems, second);
		transfer(secondItems, first);
		closeMenus();
	}

	private static Component summary(int clamsIn, int clamsOut, long net) {
		StringBuilder text = new StringBuilder("§a[疯狂水世界] 交易完成：");
		if (clamsIn > 0) text.append("获得 ").append(clamsIn).append(" 个河蚌 ");
		if (clamsOut > 0) text.append("交出 ").append(clamsOut).append(" 个河蚌 ");
		if (net != 0) text.append("§e金币 ").append(net > 0 ? "+" : "").append(String.format("%,d", net));
		return Component.literal(text.toString().trim());
	}

	public void cancel() {
		if (!active) return;
		active = false;
		TradeManager.forget(this, first, second);
		transfer(firstItems, first);
		transfer(secondItems, second);
		if (!first.hasDisconnected()) first.sendSystemMessage(Component.literal("§e交易已取消，河蚌已退回"));
		if (!second.hasDisconnected()) second.sendSystemMessage(Component.literal("§e交易已取消，河蚌已退回"));
		closeMenus();
	}

	static int clams(SimpleContainer container) {
		int count = 0;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = container.getItem(i);
			if (tradable(stack)) count += stack.getCount();
		}
		return count;
	}

	private static boolean onlyClams(SimpleContainer container) {
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && !tradable(stack)) return false;
		}
		return true;
	}

	private void transfer(SimpleContainer container, ServerPlayer recipient) {
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = container.removeItemNoUpdate(i);
			if (!stack.isEmpty()) recipient.getInventory().placeItemBackInInventory(stack);
		}
	}

	private void closeMenus() {
		if (first.containerMenu instanceof TradeMenu menu && menu.session() == this) {
			if (first.hasDisconnected()) first.doCloseContainer();
			else first.closeContainer();
		}
		if (second.containerMenu instanceof TradeMenu menu && menu.session() == this) {
			if (second.hasDisconnected()) second.doCloseContainer();
			else second.closeContainer();
		}
	}
}
