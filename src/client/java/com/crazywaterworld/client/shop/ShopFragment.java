package com.crazywaterworld.client.shop;

import com.crazywaterworld.shop.ShopCatalog;
import com.crazywaterworld.shop.ShopMenu;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

import java.util.List;

/** ModernUI presentation and controls; Minecraft's menu still owns the real item slots. */
public final class ShopFragment extends Fragment implements ScreenCallback {
	private static final int W = 352;
	private static final int H = 288;
	private static final int WHITE = 0xFFF1FAF7;
	private static final int MUTED = 0xFFC9DADC;
	private static final int TIDE = 0xFF85DDC6;
	private static final int GOLD = 0xFFFFD487;
	private static final int DARK = 0xFF193843;
	private static final int ACTIVE = 0xFF276956;
	private static final int DISABLED = 0xFF22333A;
	private static final int DANGER = 0xFFFF9C8F;
	private static final int WARN = 0xFFF2C38B;

	static final String BLOCK_MONEY = "金币不足";
	static final String BLOCK_LIMIT = "本局已限购";
	static final String BLOCK_OWNED = "已有同级附魔";
	static final String BLOCK_ROD = "请手持鱼竿";

	private final ShopMenu menu;
	private ShopScreen screen;
	private FrameLayout panel;
	private int tab;
	private int page;

	public ShopFragment(ShopMenu menu) {
		this.menu = menu;
	}

	void attach(ShopScreen screen) {
		this.screen = screen;
	}

	ShopCatalog.Tab activeTab() {
		return ShopCatalog.Tab.values()[tab];
	}

	@Override
	public boolean shouldBlurBackground() {
		return false;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedState) {
		Context context = requireContext();
		FrameLayout root = new FrameLayout(context);
		panel = new FrameLayout(context);
		root.addView(panel, new FrameLayout.LayoutParams(root.dp(W * 2), root.dp(H * 2), Gravity.CENTER));
		refresh();
		return root;
	}

	/** Called from the Minecraft thread when synced balance or purchase state changes. */
	void postRefresh() {
		if (panel != null) panel.post(this::refresh);
	}

	void refresh() {
		if (panel == null) return;
		panel.removeAllViews();
		text("疯狂水世界", 16, 10, 140, 16, 14, WHITE);
		text("深海交易所  /  DEEP WATER", 16, 24, 200, 10, 8, TIDE);

		ShopCatalog.Tab[] tabs = ShopCatalog.Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			int index = i;
			button(tabs[i].label, 35, 42 + i * 22, 47, 20, i == tab ? ACTIVE : DARK,
					i == tab ? TIDE : MUTED, () -> {
					tab = index;
					page = 0;
					screen.showPage(0);
					send(ShopMenu.BUTTON_TAB_BASE + index);
					refresh();
				});
		}

		List<ShopCatalog.Product> products = ShopCatalog.ofTab(activeTab());
		int entries = activeTab() == ShopCatalog.Tab.SELL ? ShopScreen.MARKET.size() : products.size();
		int pages = Math.max(1, (entries + 3) / 4);
		if (pages > 1) {
			button("<", 117, 123, 16, 14, page > 0 ? DARK : DISABLED, page > 0 ? WHITE : MUTED,
					() -> changePage(Math.max(0, page - 1)));
			text((page + 1) + " / " + pages, 136, 123, 35, 14, 9, MUTED, Gravity.CENTER);
			button(">", 174, 123, 16, 14, page < pages - 1 ? DARK : DISABLED, page < pages - 1 ? WHITE : MUTED,
					() -> changePage(Math.min(pages - 1, page + 1)));
		}

		for (int i = 0; i < 4 && page * 4 + i < products.size(); i++) {
			ShopCatalog.Product product = products.get(page * 4 + i);
			int x = 90 + (i % 2) * 86;
			int y = 42 + (i / 2) * 42;
			int index = page * 4 + i;
			int global = ShopCatalog.PRODUCTS.indexOf(product);
			String blocker = screen.blocker(global);
			Button card = button("", x, y, 80, 22, 0x0015323C, WHITE, () -> {
				screen.selectProduct(index);
				refresh();
			});
			card.setTooltipText(product.name() + "\n" + product.desc());
			text(product.name(), x + 25, y + 3, 53, 18, 10, WHITE);
			text(String.format("%,d", product.price()), x + 6, y + 23, 39, 13, 9,
					blocker == BLOCK_MONEY ? DANGER : GOLD);
			buyButton(blocker == null ? "购买" : shortLabel(blocker), x + 45, y + 22, 33, 14, blocker, global);
		}

		ShopCatalog.Product selected = screen.selectedProduct();
		text(selected == null ? "收购服务" : selected.tab() == ShopCatalog.Tab.APPRAISE ? "鉴定 · 详情" : "装备 · 详情",
				269, 44, 71, 15, 10, TIDE);
		if (selected != null) {
			int global = ShopCatalog.PRODUCTS.indexOf(selected);
			String blocker = screen.blocker(global);
			text(selected.name(), 269, 74, 71, 14, 11, WHITE);
			textLines(selected.desc(), 269, 88, 71, 24, 8, MUTED);
			text(String.format("%,d", selected.price()) + " 金币", 269, 110, 71, 12, 10,
					blocker == BLOCK_MONEY ? DANGER : GOLD);
			String label = blocker == null ? "立即购买"
					: blocker == BLOCK_MONEY ? "还差 " + String.format("%,d", selected.price() - menu.balance()) + " 金"
					: blocker;
			buyButton(label, 268, 122, 73, 13, blocker, global);
		} else {
			text("渔获换金币", 269, 76, 71, 14, 10, WHITE);
			textLines("拖入或 Shift 点击渔获，确认后到账", 269, 90, 71, 20, 8, MUTED);
			textLines("河蚌不收购：去开蚌台或与玩家交易", 269, 112, 71, 20, 8, WARN);
		}

		text("收购中心", 14, 144, 75, 18, 12, WHITE);
		text("拖入渔获", 14, 159, 75, 12, 9, MUTED);
		button("全部放入", 14, 179, 72, 13, DARK, TIDE, () -> send(ShopMenu.BUTTON_DEPOSIT));
		boolean canSell = menu.filledSaleSlots() > 0;
		button("确认出售", 258, 169, 82, 18, canSell ? ACTIVE : DISABLED, canSell ? WHITE : MUTED,
				() -> send(ShopMenu.BUTTON_SELL));
		text("随身背包", 14, 204, 72, 18, 12, WHITE);
		text("Shift 点击", 14, 227, 72, 13, 9, MUTED);
		text("快速投放", 14, 241, 72, 13, 9, TIDE);
		text("快捷栏", 14, 266, 72, 14, 9, MUTED);
		button("×", W - 22, 14, 14, 15, DARK, MUTED,
				() -> Minecraft.getInstance().execute(screen::onClose));
	}

	/** Card buttons only show a few characters; the detail panel uses the full reason. */
	private static String shortLabel(String blocker) {
		return switch (blocker) {
			case BLOCK_MONEY -> "不足";
			case BLOCK_LIMIT -> "限购";
			case BLOCK_OWNED -> "已有";
			case BLOCK_ROD -> "持竿";
			default -> "不可";
		};
	}

	private void buyButton(String label, int x, int y, int w, int h, String blocker, int global) {
		Button view = button(label, x, y, w, h, blocker == null ? ACTIVE : DISABLED,
				blocker == null ? WHITE : MUTED, () -> send(ShopMenu.BUTTON_BUY_BASE + global));
		// Disabled buttons give no server round trip; the server still validates every purchase.
		view.setEnabled(blocker == null);
	}

	private void changePage(int nextPage) {
		page = nextPage;
		screen.showPage(page);
		refresh();
	}

	private void text(String label, int x, int y, int w, int h, int size, int color) {
		text(label, x, y, w, h, size, color, Gravity.CENTER_VERTICAL);
	}

	private void text(String label, int x, int y, int w, int h, int size, int color, int gravity) {
		TextView view = new TextView(requireContext());
		view.setText(label);
		view.setTextSize(size);
		view.setTextColor(color);
		view.setSingleLine(true);
		view.setEllipsize(TextUtils.TruncateAt.END);
		view.setGravity(gravity);
		place(view, x, y, w, h);
	}

	private void textLines(String label, int x, int y, int w, int h, int size, int color) {
		TextView view = new TextView(requireContext());
		view.setText(label);
		view.setTextSize(size);
		view.setTextColor(color);
		view.setMaxLines(3);
		view.setEllipsize(TextUtils.TruncateAt.END);
		place(view, x, y, w, h);
	}

	private Button button(String label, int x, int y, int w, int h, int color, int textColor, Runnable action) {
		Button view = new Button(requireContext());
		view.setText(label);
		view.setTextSize(9);
		view.setTextColor(textColor);
		view.setGravity(Gravity.CENTER);
		ShapeDrawable background = new ShapeDrawable();
		background.setColor(color);
		background.setCornerRadius(panel.dp(4));
		view.setBackground(background);
		view.setOnClickListener(v -> action.run());
		place(view, x, y, w, h);
		return view;
	}

	private void place(View view, int x, int y, int w, int h) {
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panel.dp(w * 2), panel.dp(h * 2),
				Gravity.TOP | Gravity.LEFT);
		params.leftMargin = panel.dp(x * 2);
		params.topMargin = panel.dp(y * 2);
		panel.addView(view, params);
	}

	private void send(int id) {
		Minecraft.getInstance().execute(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.gameMode != null && client.screen == screen) {
				client.gameMode.handleInventoryButtonClick(menu.containerId, id);
			}
		});
	}
}
