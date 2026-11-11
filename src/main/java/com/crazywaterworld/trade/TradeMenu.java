package com.crazywaterworld.trade;

import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.shop.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Identical slot layout for both participants; only the first four slots belong to the viewer.
 * Offer slots accept clams only; each side's coins are its asking price (what it receives).
 */
public final class TradeMenu extends AbstractContainerMenu {
	public static final int READY = 0;
	public static final int CANCEL = 1;
	public static final int CLEAR = 2;
	public static final int PLUS_BASE = 10;
	public static final int MINUS_BASE = 20;
	/** Coin steps for the ± buttons; button id = base + index. */
	public static final int[] STEPS = {100, 1_000, 10_000};
	public static final int OWN_X = 37;
	public static final int PEER_X = 211;
	public static final int OFFER_Y = 56;
	public static final int INVENTORY_X = 79;
	public static final int INVENTORY_Y = 204;
	public static final int HOTBAR_Y = 262;
	private static final int DATA_COUNT = 9;

	public final UUID otherId;
	private final TradeSession session;
	private final Inventory inventory;
	private final ContainerData data;

	public TradeMenu(int id, Inventory inventory, UUID otherId) {
		this(id, inventory, otherId, null);
	}

	public TradeMenu(int id, Inventory inventory, UUID otherId, TradeSession session) {
		super(ModMenus.TRADE, id);
		this.otherId = otherId;
		this.session = session;
		this.inventory = inventory;
		ServerPlayer owner = inventory.player instanceof ServerPlayer player ? player : null;
		Container own = owner == null ? new SimpleContainer(TradeSession.SLOTS) : session.items(owner);
		Container peer = owner == null ? new SimpleContainer(TradeSession.SLOTS) : session.items(session.other(owner));
		data = owner == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case 0 -> session.coins(owner) & 0xFFFF;
					case 1 -> session.coins(owner) >>> 16;
					case 2 -> session.coins(session.other(owner)) & 0xFFFF;
					case 3 -> session.coins(session.other(owner)) >>> 16;
					case 4 -> session.ready(owner) ? 1 : 0;
					case 5 -> session.ready(session.other(owner)) ? 1 : 0;
					case 6 -> (int) (Economy.balance(owner.getServer(), owner.getUUID()) & 0xFFFF);
					case 7 -> (int) ((Economy.balance(owner.getServer(), owner.getUUID()) >>> 16) & 0xFFFF);
					case 8 -> session.cooldown();
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
		addDataSlots(data);
		for (int i = 0; i < TradeSession.SLOTS; i++) {
			addSlot(new Slot(own, i, OWN_X + i * 18, OFFER_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return TradeSession.tradable(stack);
				}
			});
		}
		for (int i = 0; i < TradeSession.SLOTS; i++) {
			addSlot(new Slot(peer, i, PEER_X + i * 18, OFFER_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player player) {
					return false;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, 9 + row * 9 + col,
						INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
	}

	TradeSession session() {
		return session;
	}

	public long ownCoins() {
		return ((data.get(1) & 0xFFFFL) << 16) | (data.get(0) & 0xFFFFL);
	}

	public long peerCoins() {
		return ((data.get(3) & 0xFFFFL) << 16) | (data.get(2) & 0xFFFFL);
	}

	public long balance() {
		return ((data.get(7) & 0xFFFFL) << 16) | (data.get(6) & 0xFFFFL);
	}

	public boolean ownReady() {
		return data.get(4) != 0;
	}

	public boolean peerReady() {
		return data.get(5) != 0;
	}

	/** Ticks until confirming is allowed again after the last offer change. */
	public int cooldown() {
		return data.get(8);
	}

	public int ownClams() {
		return clams(0);
	}

	public int peerClams() {
		return clams(TradeSession.SLOTS);
	}

	private int clams(int start) {
		int count = 0;
		for (int i = start; i < start + TradeSession.SLOTS; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (TradeSession.tradable(stack)) count += stack.getCount();
		}
		return count;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer) || session == null || !session.active()) return false;
		if (id == CANCEL) {
			session.cancel();
			return true;
		}
		if (id == READY) {
			session.confirm(serverPlayer);
			return true;
		}
		if (id == CLEAR) {
			session.setCoins(serverPlayer, 0);
			return true;
		}
		if (id >= PLUS_BASE && id < PLUS_BASE + STEPS.length) {
			session.adjust(serverPlayer, STEPS[id - PLUS_BASE]);
			return true;
		}
		if (id >= MINUS_BASE && id < MINUS_BASE + STEPS.length) {
			session.adjust(serverPlayer, -STEPS[id - MINUS_BASE]);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int offers = TradeSession.SLOTS * 2;
		if (index < 0 || index >= slots.size() || index >= TradeSession.SLOTS && index < offers) return ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < TradeSession.SLOTS) {
			if (!moveItemStackTo(stack, offers, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!TradeSession.tradable(stack) || !moveItemStackTo(stack, 0, TradeSession.SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return session == null || session.valid();
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (session != null && session.active()) session.cancel();
	}
}
