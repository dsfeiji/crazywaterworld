package com.crazywaterworld.client.shop;

import com.crazywaterworld.shop.ShopCatalog;
import com.crazywaterworld.shop.ShopMenu;
import com.crazywaterworld.economy.ItemValues;
import com.crazywaterworld.item.ModItems;
import icyllis.modernui.mc.MenuScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Deep-water trading desk: native container slots with a ModernUI control layer. */
public class ShopScreen extends MenuScreen<ShopMenu> {
	private static final int W = 352;
	private static final int H = 288;
	private static final int GRID_X = 90;
	private static final int GRID_Y = 42;
	private static final int CARD_W = 80;
	private static final int CARD_H = 38;
	private static final int PREVIEW_X = 264;
	private static final int PAGE_SIZE = 4;
	/** Every sellable item, most valuable first; the sell tab pages through these quotes. */
	static final List<Item> MARKET = ItemValues.sellableItems().stream()
			.sorted(Comparator.comparingLong(ItemValues::priceOf).reversed()).toList();

	private static final int FRAME = 0xFF091B22;
	private static final int SURFACE = 0xFF102934;
	private static final int SURFACE_LIGHT = 0xFF1A3A46;
	private static final int INSET = 0xFF0B222C;
	private static final int CARD = 0xFF193843;
	private static final int CARD_ACTIVE = 0xFF23505A;
	private static final int LINE = 0xFF345968;
	private static final int TIDE = 0xFF85DDC6;
	private static final int GOLD = 0xFFFFD487;

	private final ShopFragment fragment;
	private int page;
	private int selected;
	/** Why each product cannot be bought right now (null = purchasable); written on the game thread. */
	private volatile String[] blockers = new String[ShopCatalog.PRODUCTS.size()];
	private String stateKey = "";

	public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
		this(new ShopFragment(menu), menu, inventory, title);
	}

	private ShopScreen(ShopFragment fragment, ShopMenu menu, Inventory inventory, Component title) {
		super(fragment, null, menu, inventory, title);
		this.fragment = fragment;
		fragment.attach(this);
		imageWidth = W;
		imageHeight = H;
	}

	void showPage(int nextPage) {
		page = nextPage;
		selected = nextPage * PAGE_SIZE;
	}

	void selectProduct(int index) {
		selected = index;
	}

	String blocker(int globalIndex) {
		String[] current = blockers;
		return globalIndex >= 0 && globalIndex < current.length ? current[globalIndex] : null;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		String[] next = new String[ShopCatalog.PRODUCTS.size()];
		for (int i = 0; i < next.length; i++) next[i] = computeBlocker(ShopCatalog.PRODUCTS.get(i));
		String key = menu.balance() + "|" + menu.filledSaleSlots() + "|" + Arrays.toString(next);
		if (!key.equals(stateKey)) {
			stateKey = key;
			blockers = next;
			fragment.postRefresh();
		}
	}

	/** Client-side mirror of ShopMenu.buy checks, so the UI can explain a disabled button before clicking. */
	private String computeBlocker(ShopCatalog.Product product) {
		if (product.icon() == ModItems.SALVAGE_NET_ITEM && menu.netsBought() >= ShopCatalog.NET_LIMIT) {
			return ShopFragment.BLOCK_LIMIT;
		}
		if (product.enchantment() != null && minecraft != null && minecraft.player != null) {
			ItemStack rod = minecraft.player.getMainHandItem().is(Items.FISHING_ROD)
					? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
			if (!rod.is(Items.FISHING_ROD)) return ShopFragment.BLOCK_ROD;
			var enchantment = minecraft.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.get(product.enchantment());
			if (enchantment.isPresent() && rod.getEnchantments().getLevel(enchantment.get()) >= product.level()) {
				return ShopFragment.BLOCK_OWNED;
			}
		}
		return menu.balance() < product.price() ? ShopFragment.BLOCK_MONEY : null;
	}

	private List<ShopCatalog.Product> products() {
		return ShopCatalog.ofTab(fragment.activeTab());
	}

	ShopCatalog.Product selectedProduct() {
		List<ShopCatalog.Product> products = products();
		return selected < products.size() ? products.get(selected) : null;
	}

	@Override
	protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + W, y + H, FRAME);
		g.fill(x + 1, y + 1, x + W - 1, y + H - 1, SURFACE);
		g.fill(x + 12, y + 6, x + W - 12, y + 7, TIDE);
		g.fill(x + 10, y + 10, x + W - 10, y + 35, SURFACE_LIGHT);
		g.fill(x + 10, y + 36, x + W - 10, y + 37, LINE);

		// One-pixel dividers define hierarchy; card and slot interiors stay quiet.
		g.fill(x + 88, y + 40, x + 260, y + 137, INSET);
		g.fill(x + 264, y + 40, x + 344, y + 137, SURFACE_LIGHT);
		g.fill(x + 10, y + 140, x + W - 10, y + 194, INSET);
		g.fill(x + 10, y + 140, x + W - 10, y + 141, TIDE);
		g.fill(x + 10, y + 200, x + W - 10, y + 286, INSET);
		g.fill(x + 10, y + 200, x + W - 10, y + 201, LINE);
		g.fill(x + 90, y + 207, x + 91, y + 283, LINE);
		g.fill(x + 98, y + 265, x + 264, y + 285, SURFACE_LIGHT);

		List<ShopCatalog.Product> products = products();
		if (fragment.activeTab() == ShopCatalog.Tab.SELL) {
			for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < MARKET.size(); i++) {
				int cx = x + GRID_X + (i % 2) * 86;
				int cy = y + GRID_Y + (i / 2) * 42;
				g.fill(cx, cy, cx + CARD_W, cy + CARD_H, CARD);
				g.fill(cx + 1, cy + CARD_H - 1, cx + CARD_W - 1, cy + CARD_H, LINE);
			}
		}
		for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < products.size(); i++) {
			int cx = x + GRID_X + (i % 2) * 86;
			int cy = y + GRID_Y + (i / 2) * 42;
			boolean active = page * PAGE_SIZE + i == selected;
			g.fill(cx, cy, cx + CARD_W, cy + CARD_H, active ? CARD_ACTIVE : CARD);
			g.fill(cx + 1, cy + CARD_H - 1, cx + CARD_W - 1, cy + CARD_H, LINE);
			if (active) g.fill(cx, cy + 5, cx + 2, cy + CARD_H - 5, TIDE);
		}

		for (int i = 0; i < ShopMenu.SELL_SIZE; i++) {
			int sx = x + ShopMenu.SELL_X + (i % ShopMenu.SELL_COLS) * 18;
			int sy = y + ShopMenu.SELL_Y + (i / ShopMenu.SELL_COLS) * 18;
			slot(g, sx, sy);
		}
		for (int i = 0; i < 36; i++) {
			int sx = x + ShopMenu.INVENTORY_X + (i % 9) * 18;
			int sy = y + (i < 27 ? ShopMenu.INVENTORY_Y + i / 9 * 18 : ShopMenu.HOTBAR_Y);
			slot(g, sx, sy);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// Vanilla renders the Minecraft item models and slot tooltips; ModernUI draws all controls and text.
		String balance = "金币 " + String.format("%,d", menu.balance());
		int coinX = W - 31 - font.width(balance);
		g.renderItem(new ItemStack(Items.GOLD_INGOT), coinX - 18, 14);
		g.drawString(font, balance, coinX, 18, GOLD, false);
		g.drawString(font, font.plainSubstrByWidth("预估 " + menu.previewPrice() + " 金", 83),
				258, 150, GOLD, false);
		g.drawString(font, menu.rank() > 0 ? "本局第 " + menu.rank() + " 名" : "尚未入榜",
				14, 130, TIDE, false);
		g.drawString(font, "已投放 " + menu.filledSaleSlots() + "/16", 14, 169, TIDE, false);
		for (int i = 0; i < ShopCatalog.Tab.values().length; i++) {
			g.renderItem(new ItemStack(ShopCatalog.Tab.values()[i].icon), 14, 44 + i * 22);
		}
		List<ShopCatalog.Product> products = products();
		if (fragment.activeTab() == ShopCatalog.Tab.SELL) {
			for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < MARKET.size(); i++) {
				Item item = MARKET.get(page * PAGE_SIZE + i);
				int cx = GRID_X + (i % 2) * 86;
				int cy = GRID_Y + (i / 2) * 42;
				g.renderItem(new ItemStack(item), cx + 5, cy + 4);
				g.drawString(font, font.plainSubstrByWidth(item.getDescription().getString(), 54),
						cx + 24, cy + 5, 0xFFEAF5F3, false);
				long current = menu.displayedPrice(item);
				long base = ItemValues.priceOf(item);
				boolean falling = current < base;
				g.drawString(font, falling ? "↓" : "·", cx + 8, cy + 25,
						falling ? 0xFFFF9999 : TIDE, false);
				g.drawString(font, String.format("%,d", current), cx + 24, cy + 25, GOLD, false);
				if (falling) {
					String share = current * 100 / base + "%";
					g.fill(cx + 24, cy + 16, cx + CARD_W - 3, cy + 17, LINE);
					g.fill(cx + 24, cy + 16, cx + 24 + (int) ((CARD_W - 27) * current / base), cy + 17, GOLD);
					if (mouseX >= leftPos + cx && mouseX < leftPos + cx + CARD_W
							&& mouseY >= topPos + cy && mouseY < topPos + cy + CARD_H) {
						g.renderTooltip(font, Component.literal(item.getDescription().getString()
								+ " · 基础价 " + String.format("%,d", base) + " 金 · 当前 " + share),
								mouseX - leftPos, mouseY - topPos);
					}
				}
			}
		}
		for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < products.size(); i++) {
			int cx = GRID_X + (i % 2) * 86;
			int cy = GRID_Y + (i / 2) * 42;
			g.renderItem(new ItemStack(products.get(page * PAGE_SIZE + i).icon()), cx + 5, cy + 4);
		}
		ShopCatalog.Product product = selectedProduct();
		g.renderItem(new ItemStack(product != null ? product.icon() : Items.CHEST), 296, 56);
		if (product != null && product.icon() == ModItems.SALVAGE_NET_ITEM) {
			String bought = menu.netsBought() + "/" + ShopCatalog.NET_LIMIT;
			g.drawString(font, bought, 318, 61, menu.netsBought() >= ShopCatalog.NET_LIMIT
					? 0xFFFF9C8F : TIDE, false);
		}
	}

	private static void slot(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, LINE);
		g.fill(x, y, x + 16, y + 16, INSET);
	}
}
