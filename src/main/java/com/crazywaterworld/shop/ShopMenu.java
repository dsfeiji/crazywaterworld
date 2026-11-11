package com.crazywaterworld.shop;

import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.economy.ItemValues;
import com.crazywaterworld.economy.MarketData;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.RoundGame;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Items;

/**
 * 商店菜单。
 * 按钮协议（clickMenuButton）：
 *   0          出售出售格中的物品
 *   1          把背包中可出售的渔获全部放入出售格（河蚌不可出售）
 *   100 + n    切换到第 n 个分类（n 为 ShopCatalog.Tab 的序号）
 *   200 + n    购买第 n 件商品（n 为 ShopCatalog.PRODUCTS 的序号）
 * 价格、余额都由服务端计算，客户端发送的只是按钮编号。
 */
public class ShopMenu extends AbstractContainerMenu {
	public static final int BUTTON_SELL = 0;
	public static final int BUTTON_DEPOSIT = 1;
	public static final int BUTTON_TAB_BASE = 100;
	public static final int BUTTON_BUY_BASE = 200;

	/** 出售区槽位数量：2 行 x 8 列 */
	public static final int SELL_COLS = 8;
	public static final int SELL_ROWS = 2;
	public static final int SELL_SIZE = SELL_COLS * SELL_ROWS;
	/** 出售区第一个槽位的菜单索引（出售槽在最前面） */
	public static final int SELL_START = 0;

	public static final int SELL_X = 94;
	public static final int SELL_Y = 152;
	public static final int INVENTORY_X = 101;
	public static final int INVENTORY_Y = 210;
	public static final int HOTBAR_Y = 268;

	// Menu data values are transmitted as 16-bit integers in 1.21.
	public static final int DATA_BALANCE_LOW = 0;
	public static final int DATA_BALANCE_HIGH = 1;
	public static final int DATA_TAB = 2;
	public static final int DATA_PRICE_START = 3;
	public static final int DATA_RANK = DATA_PRICE_START + ItemValues.sellableItems().size();
	public static final int DATA_NETS = DATA_RANK + 1;

	public final BlockPos pos;

	private final Container sellContainer = new SimpleContainer(SELL_SIZE);
	private final ContainerData data;
	private final Inventory playerInventory;

	public ShopMenu(int containerId, Inventory playerInventory, BlockPos pos) {
		super(ModMenus.SHOP, containerId);
		this.pos = pos;
		this.playerInventory = playerInventory;
		this.data = new SimpleContainerData(DATA_NETS + 1);
		this.addDataSlots(data);
		data.set(DATA_TAB, ShopCatalog.Tab.RECOMMEND.ordinal());
		if (playerInventory.player instanceof ServerPlayer serverPlayer) {
			refreshBalance(serverPlayer);
		}

		// 出售区：2 行 x 8 列，只接受有售价的物品
		for (int r = 0; r < SELL_ROWS; r++) {
			for (int c = 0; c < SELL_COLS; c++) {
				int idx = r * SELL_COLS + c;
				this.addSlot(new Slot(sellContainer, idx, SELL_X + c * 18, SELL_Y + r * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return ItemValues.priceOf(stack.getItem()) > 0;
					}
				});
			}
		}

		// 玩家背包（3 行 9 列）
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
						INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	/** 客户端调用：当前余额（两段 16 位数据由服务端同步）。 */
	public long balance() {
		return (data.get(DATA_BALANCE_LOW) & 0xFFFFL)
				| ((data.get(DATA_BALANCE_HIGH) & 0xFFFFL) << 16);
	}

	public ShopCatalog.Tab tab() {
		return ShopCatalog.Tab.values()[data.get(DATA_TAB)];
	}

	/** 出售区所有物品的预估总价（客户端显示用，服务端会重新计算） */
	public long previewPrice() {
		long total = 0;
		for (int i = 0; i < SELL_SIZE; i++) {
			ItemStack stack = sellContainer.getItem(i);
			total += displayedPrice(stack.getItem()) * stack.getCount();
		}
		return total;
	}

	public int rank() {
		return data.get(DATA_RANK);
	}

	/** 本局已购买的打捞网数量（客户端显示限购用） */
	public int netsBought() {
		return data.get(DATA_NETS);
	}

	public long displayedPrice(net.minecraft.world.item.Item item) {
		int index = ItemValues.sellableItems().indexOf(item);
		return index < 0 ? 0 : data.get(DATA_PRICE_START + index) & 0xFFFFL;
	}

	public int filledSaleSlots() {
		int filled = 0;
		for (int i = 0; i < SELL_SIZE; i++) {
			if (!sellContainer.getItem(i).isEmpty()) filled++;
		}
		return filled;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		if (!GameSwitch.active(serverPlayer.getServer())) return false;
		if (id == BUTTON_SELL) {
			return sell(serverPlayer);
		}
		if (id == BUTTON_DEPOSIT) {
			return deposit();
		}
		if (id >= BUTTON_TAB_BASE && id < BUTTON_TAB_BASE + ShopCatalog.Tab.values().length) {
			data.set(DATA_TAB, id - BUTTON_TAB_BASE);
			return true;
		}
		if (id >= BUTTON_BUY_BASE) {
			return buy(serverPlayer, id - BUTTON_BUY_BASE);
		}
		return false;
	}

	/** 与 Shift 点击相同的移动规则，只是一次处理整个背包。 */
	private boolean deposit() {
		boolean moved = false;
		for (int i = SELL_SIZE; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			ItemStack stack = slot.getItem();
			if (!Economy.autoSellable(stack)) continue;
			int before = stack.getCount();
			moveItemStackTo(stack, SELL_START, SELL_START + SELL_SIZE, false);
			if (stack.getCount() != before) {
				moved = true;
				if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
				else slot.setChanged();
			}
		}
		return moved;
	}

	private boolean sell(ServerPlayer player) {
		if (!GameSwitch.active(player.getServer())) return false;
		if (Economy.data(player.getServer()).finished()) {
			player.sendSystemMessage(Component.literal("§e本局已结束，用 /cww results 查看成绩"));
			return false;
		}
		long price = 0;
		for (int i = 0; i < SELL_SIZE; i++) {
			ItemStack stack = sellContainer.getItem(i);
			price += MarketData.price(player.getServer(), stack.getItem()) * stack.getCount();
		}
		if (price <= 0 || Economy.balance(player.getServer(), player.getUUID()) > Long.MAX_VALUE - price) {
			return false;
		}
		for (int i = 0; i < SELL_SIZE; i++) {
			ItemStack stack = sellContainer.getItem(i);
			if (MarketData.price(player.getServer(), stack.getItem()) <= 0) continue;
			MarketData.sold(player.getServer(), stack.getItem(), stack.getCount());
			sellContainer.setItem(i, ItemStack.EMPTY);
		}
		Economy.credit(player, price);
		refreshBalance(player);
		player.sendSystemMessage(Component.literal("§a[疯狂水世界] 收购成功，获得 " + price + " 金币"));
		return true;
	}

	private boolean buy(ServerPlayer player, int index) {
		if (!GameSwitch.active(player.getServer())) return false;
		if (Economy.data(player.getServer()).finished()) {
			player.sendSystemMessage(Component.literal("§e本局已结束，无法继续购买"));
			return false;
		}
		ShopCatalog.Product product = ShopCatalog.get(index);
		if (product == null || product.price() <= 0) {
			return false;
		}
		boolean salvageNet = product.icon() == ModItems.SALVAGE_NET_ITEM;
		if (salvageNet && Economy.data(player.getServer()).netsBought(player.getUUID()) >= ShopCatalog.NET_LIMIT) {
			player.sendSystemMessage(Component.literal("§e本局打捞网已达限购 " + ShopCatalog.NET_LIMIT + " 张"));
			return false;
		}
		ItemStack rod = ItemStack.EMPTY;
		if (product.enchantment() != null) {
			rod = player.getMainHandItem().is(Items.FISHING_ROD) ? player.getMainHandItem() : player.getOffhandItem();
			if (!rod.is(Items.FISHING_ROD)) {
				player.sendSystemMessage(Component.literal("§c请先将要附魔的钓鱼竿拿在主手或副手"));
				return false;
			}
			var enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(product.enchantment());
			if (rod.getEnchantments().getLevel(enchantment) >= product.level()) {
				player.sendSystemMessage(Component.literal("§e这根鱼竿已有同级或更高的附魔"));
				return false;
			}
		}
		if (!Economy.spend(player, product.price())) {
			player.sendSystemMessage(Component.literal("§c金币不足"));
			return false;
		}
		if (product.enchantment() != null) {
			rod.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(product.enchantment()), product.level());
		} else {
			if (salvageNet) Economy.data(player.getServer()).boughtNet(player.getUUID());
			player.getInventory().placeItemBackInInventory(new ItemStack(product.icon()));
		}
		refreshBalance(player);
		player.sendSystemMessage(Component.literal("§b[疯狂水世界] 已购买 " + product.name()));
		return true;
	}

	/** 把服务端余额写入同步槽，客户端下一次同步时可见。 */
	private void refreshBalance(ServerPlayer player) {
		long balance = Economy.balance(player.getServer(), player.getUUID());
		data.set(DATA_BALANCE_LOW, (int) (balance & 0xFFFF));
		data.set(DATA_BALANCE_HIGH, (int) ((balance >>> 16) & 0xFFFF));
		for (int i = 0; i < ItemValues.sellableItems().size(); i++) {
			data.set(DATA_PRICE_START + i, (int) MarketData.price(player.getServer(), ItemValues.sellableItems().get(i)));
		}
		data.set(DATA_RANK, RoundGame.rank(player.getServer(), player.getUUID()));
		data.set(DATA_NETS, Economy.data(player.getServer()).netsBought(player.getUUID()));
	}

	@Override
	public void broadcastChanges() {
		// The menu can stay open while coins change through commands or other players.
		if (playerInventory.player instanceof ServerPlayer serverPlayer) {
			refreshBalance(serverPlayer);
		}
		super.broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return result;
		}
		ItemStack stack = slot.getItem();
		result = stack.copy();

		if (index < SELL_SIZE) {
			// 出售区 -> 背包
			if (!this.moveItemStackTo(stack, SELL_SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, SELL_START, SELL_START + SELL_SIZE, false)) {
			// 背包 -> 出售区（只接受可出售物品）
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return !(player instanceof ServerPlayer serverPlayer) || GameSwitch.active(serverPlayer.getServer());
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.clearContainer(player, sellContainer);
	}
}
