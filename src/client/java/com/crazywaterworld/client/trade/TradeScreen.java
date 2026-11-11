package com.crazywaterworld.client.trade;

import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.trade.TradeMenu;
import com.crazywaterworld.trade.TradeSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Clam exchange desk. Left card: the clams I sell and my asking price. Right card: the other side.
 * The settlement bar shows exactly what I get and how my coins change before anyone confirms.
 * All controls are drawn here (no vanilla buttons) so the screen keeps one visual style.
 */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {
	private static final int W = 320;
	private static final int H = 286;
	private static final int OWN_LEFT = 10;
	private static final int PEER_LEFT = 184;
	private static final int CARD_W = 126;
	private static final int CARD_TOP = 34;
	private static final int CARD_BOTTOM = 148;
	private static final int BAR_TOP = 152;
	private static final int BAR_BOTTOM = 188;
	private static final int BAG_TOP = 194;

	private static final int FRAME = 0xFF06141A;
	private static final int BODY = 0xFF0E2630;
	private static final int HEADER = 0xFF143642;
	private static final int CARD = 0xFF0A1E26;
	private static final int CARD_LINE = 0xFF24505C;
	private static final int SLOT_EDGE = 0xFF3A6B76;
	private static final int SLOT_FILL = 0xFF0C1A20;
	private static final int TIDE = 0xFF85DDC6;
	private static final int GOLD = 0xFFFFD487;
	private static final int WHITE = 0xFFEFF7F5;
	private static final int MUTED = 0xFF8FA9AD;
	private static final int GAIN = 0xFF8CE59B;
	private static final int LOSS = 0xFFFF9C8F;

	private static final int STYLE_NORMAL = 0;
	private static final int STYLE_PRIMARY = 1;
	private static final int STYLE_GHOST = 2;

	/** A flat clickable control; {@code action} is a TradeMenu button id. */
	private record Chip(int x, int y, int w, int h, Supplier<String> label, int action,
			BooleanSupplier enabled, int style) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Chip> chips = new ArrayList<>();

	public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = W;
		imageHeight = H;
	}

	@Override
	protected void init() {
		super.init();
		chips.clear();
		int[] steps = TradeMenu.STEPS;
		for (int i = 0; i < steps.length; i++) {
			String amount = stepLabel(steps[i]);
			int x = OWN_LEFT + 6 + i * 39;
			chips.add(new Chip(x, 112, 36, 14, () -> "+" + amount, TradeMenu.PLUS_BASE + i,
					() -> menu.ownClams() > 0 && !menu.ownReady(), STYLE_NORMAL));
			chips.add(new Chip(x, 129, 36, 14, () -> "-" + amount, TradeMenu.MINUS_BASE + i,
					() -> menu.ownCoins() > 0 && !menu.ownReady(), STYLE_NORMAL));
		}
		chips.add(new Chip(OWN_LEFT + CARD_W - 32, 84, 26, 12, () -> "清零", TradeMenu.CLEAR,
				() -> menu.ownCoins() > 0 && !menu.ownReady(), STYLE_GHOST));
		chips.add(new Chip(W - 112, BAR_TOP + 7, 102, 22, this::confirmLabel, TradeMenu.READY,
				this::canConfirm, STYLE_PRIMARY));
		chips.add(new Chip(W - 24, 8, 16, 16, () -> "×", TradeMenu.CANCEL, () -> true, STYLE_GHOST));
	}

	private static String stepLabel(int step) {
		return step >= 10_000 ? step / 10_000 + "万" : step >= 1_000 ? step / 1_000 + "千" : String.valueOf(step);
	}

	/** My coin change if the trade went through now: + my ask, - their ask. */
	private long net() {
		return menu.ownCoins() - menu.peerCoins();
	}

	private boolean cannotAfford() {
		return net() < 0 && menu.balance() < -net();
	}

	private boolean canConfirm() {
		if (menu.ownReady()) return true;
		return menu.ownClams() + menu.peerClams() > 0 && !cannotAfford() && menu.cooldown() == 0;
	}

	private String confirmLabel() {
		if (menu.ownReady()) return "撤销确认";
		if (menu.ownClams() + menu.peerClams() == 0) return "等待放入河蚌";
		if (cannotAfford()) return "金币不足";
		if (menu.cooldown() > 0) return "请核对 (" + (menu.cooldown() + 19) / 20 + ")";
		return menu.peerReady() ? "同意成交" : "确认交易";
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			double mx = mouseX - leftPos;
			double my = mouseY - topPos;
			for (Chip chip : chips) {
				if (!chip.contains(mx, my)) continue;
				if (chip.enabled().getAsBoolean()) {
					if (minecraft != null) {
						minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					}
					send(chip.action());
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void send(int button) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
		}
	}

	@Override
	protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + W, y + H, FRAME);
		g.fill(x + 1, y + 1, x + W - 1, y + H - 1, BODY);
		g.fill(x + 1, y + 1, x + W - 1, y + 30, HEADER);
		g.fill(x + 1, y + 30, x + W - 1, y + 31, TIDE);

		card(g, x + OWN_LEFT, y + CARD_TOP, CARD_W, CARD_BOTTOM - CARD_TOP, menu.ownReady());
		card(g, x + PEER_LEFT, y + CARD_TOP, CARD_W, CARD_BOTTOM - CARD_TOP, menu.peerReady());
		card(g, x + OWN_LEFT, y + BAR_TOP, W - OWN_LEFT * 2, BAR_BOTTOM - BAR_TOP,
				menu.ownReady() && menu.peerReady());
		g.fill(x + OWN_LEFT, y + BAG_TOP, x + W - OWN_LEFT, y + H - 8, CARD);

		for (int i = 0; i < TradeSession.SLOTS; i++) {
			int ox = x + TradeMenu.OWN_X + i * 18;
			int px = x + TradeMenu.PEER_X + i * 18;
			int sy = y + TradeMenu.OFFER_Y;
			slot(g, ox, sy);
			slot(g, px, sy);
			if (!menu.slots.get(i).hasItem()) ghostClam(g, ox, sy);
		}
		for (int i = 0; i < 36; i++) {
			int sx = x + TradeMenu.INVENTORY_X + (i % 9) * 18;
			int sy = y + (i < 27 ? TradeMenu.INVENTORY_Y + i / 9 * 18 : TradeMenu.HOTBAR_Y);
			slot(g, sx, sy);
		}
	}

	private static void card(GuiGraphics g, int x, int y, int w, int h, boolean highlighted) {
		g.fill(x, y, x + w, y + h, highlighted ? TIDE : CARD_LINE);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD);
	}

	private static void slot(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
		g.fill(x, y, x + 16, y + 16, SLOT_FILL);
	}

	/** A dimmed clam in empty own slots tells the player what belongs there. */
	private static void ghostClam(GuiGraphics g, int x, int y) {
		g.renderItem(new ItemStack(ModItems.CLAM), x, y);
		g.pose().pushPose();
		g.pose().translate(0, 0, 250);
		g.fill(x, y, x + 16, y + 16, 0xC80C1A20);
		g.pose().popPose();
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		PlayerInfo peer = minecraft == null || minecraft.getConnection() == null
				? null : minecraft.getConnection().getPlayerInfo(menu.otherId);
		String otherName = peer == null ? "对方" : peer.getProfile().getName();

		g.drawString(font, "河蚌交易所", 14, 8, WHITE, false);
		g.drawString(font, font.plainSubstrByWidth("与 " + otherName + " 交易 · 只能交易河蚌", 200), 14, 19, MUTED, false);
		String balance = "余额 " + String.format("%,d", menu.balance()) + " 金";
		g.drawString(font, balance, W - 30 - font.width(balance), 12, GOLD, false);

		// My card
		g.drawString(font, "我出售", OWN_LEFT + 7, CARD_TOP + 6, TIDE, false);
		badge(g, OWN_LEFT + CARD_W - 6, CARD_TOP + 4, menu.ownReady());
		divider(g, OWN_LEFT, 80);
		g.drawString(font, "我的要价", OWN_LEFT + 7, 86, MUTED, false);
		bigAmount(g, OWN_LEFT + 7, 97, menu.ownCoins(), menu.ownClams() > 0);

		// Their card
		g.drawString(font, font.plainSubstrByWidth(otherName + " 出售", 76), PEER_LEFT + 7, CARD_TOP + 6, GOLD, false);
		badge(g, PEER_LEFT + CARD_W - 6, CARD_TOP + 4, menu.peerReady());
		divider(g, PEER_LEFT, 80);
		g.drawString(font, "对方要价", PEER_LEFT + 7, 86, MUTED, false);
		bigAmount(g, PEER_LEFT + 7, 97, menu.peerCoins(), menu.peerClams() > 0);
		g.drawString(font, menu.peerClams() > 0 ? "悬停河蚌查看鉴定结果" : "对方还没有放入河蚌",
				PEER_LEFT + 7, 118, MUTED, false);
		g.drawString(font, "要价 = 你需支付的金币", PEER_LEFT + 7, 131, MUTED, false);

		// Exchange arrows between the cards
		int mid = (OWN_LEFT + CARD_W + PEER_LEFT) / 2;
		g.drawString(font, "→", mid - font.width("→") / 2, 58, TIDE, false);
		g.drawString(font, "←", mid - font.width("←") / 2, 68, GOLD, false);

		// Settlement preview
		int get = menu.peerClams();
		int give = menu.ownClams();
		String clams = get == 0 && give == 0 ? "尚未放入河蚌"
				: (get > 0 ? "获得 " + get + " 个河蚌" : "") + (get > 0 && give > 0 ? " · " : "")
				+ (give > 0 ? "交出 " + give + " 个河蚌" : "");
		g.drawString(font, "结算预览", OWN_LEFT + 8, BAR_TOP + 6, MUTED, false);
		g.drawString(font, font.plainSubstrByWidth(clams, 180), OWN_LEFT + 8, BAR_TOP + 17, WHITE, false);
		long net = net();
		String coins = net == 0 ? "金币不变" : "金币 " + (net > 0 ? "+" : "-") + String.format("%,d", Math.abs(net));
		g.drawString(font, coins, OWN_LEFT + 8, BAR_TOP + 27 - 1, net > 0 ? GAIN : net < 0 ? LOSS : MUTED, false);
		if (!menu.ownReady() && !menu.peerReady()) {
			String hint = "改动后需重新确认";
			g.drawString(font, hint, W - 118 - font.width(hint), BAR_TOP + 27 - 1, MUTED, false);
		}

		g.drawString(font, "背包", OWN_LEFT + 8, TradeMenu.INVENTORY_Y + 4, MUTED, false);
		g.drawString(font, "快捷栏", OWN_LEFT + 8, TradeMenu.HOTBAR_Y + 4, MUTED, false);

		for (Chip chip : chips) drawChip(g, chip, mouseX - leftPos, mouseY - topPos);
	}

	private void divider(GuiGraphics g, int cardLeft, int y) {
		g.fill(cardLeft + 6, y, cardLeft + CARD_W - 6, y + 1, CARD_LINE);
	}

	private void bigAmount(GuiGraphics g, int x, int y, long amount, boolean selling) {
		String text = selling ? String.format("%,d", amount) : "—";
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(1.5F, 1.5F, 1.0F);
		g.drawString(font, text, 0, 0, selling ? GOLD : MUTED, false);
		g.pose().popPose();
		if (selling) g.drawString(font, "金", x + (int) (font.width(text) * 1.5F) + 3, y + 4, GOLD, false);
	}

	private void badge(GuiGraphics g, int right, int y, boolean confirmed) {
		String text = confirmed ? "✔ 已确认" : "待确认";
		int width = font.width(text) + 8;
		g.fill(right - width, y, right, y + 12, confirmed ? 0xFF1F5B4B : 0xFF1A2E35);
		g.drawString(font, text, right - width + 4, y + 2, confirmed ? TIDE : MUTED, false);
	}

	private void drawChip(GuiGraphics g, Chip chip, int mx, int my) {
		boolean enabled = chip.enabled().getAsBoolean();
		boolean hover = enabled && chip.contains(mx, my);
		int fill;
		int text;
		switch (chip.style()) {
			case STYLE_PRIMARY -> {
				fill = !enabled ? 0xFF22343A : hover ? 0xFF3AAE89 : 0xFF2C8E6F;
				text = enabled ? WHITE : MUTED;
			}
			case STYLE_GHOST -> {
				fill = hover ? 0xFF24505C : 0x00000000;
				text = enabled ? (hover ? WHITE : MUTED) : 0xFF4D6468;
			}
			default -> {
				fill = !enabled ? 0xFF132A31 : hover ? 0xFF2A6474 : 0xFF1C4552;
				text = enabled ? WHITE : 0xFF4D6468;
			}
		}
		int x = chip.x();
		int y = chip.y();
		if ((fill >>> 24) != 0) {
			g.fill(x, y, x + chip.w(), y + chip.h(), fill);
			if (enabled && chip.style() != STYLE_GHOST) {
				g.fill(x, y, x + chip.w(), y + 1, 0x30FFFFFF);
				g.fill(x, y + chip.h() - 1, x + chip.w(), y + chip.h(), 0x40000000);
			}
		}
		String label = chip.label().get();
		g.drawString(font, label, x + (chip.w() - font.width(label)) / 2, y + (chip.h() - 8) / 2 + 1, text, false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		renderTooltip(g, mouseX, mouseY);
	}
}
