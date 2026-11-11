package com.crazywaterworld.client.round;

import com.crazywaterworld.round.ResultsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ResultsScreen extends AbstractContainerScreen<ResultsMenu> {
	private static final int W = 238;
	private static final int H = 200;
	private static final int PAGE_SIZE = 6;
	private static final int GOLD = 0xFFFFD487;
	private static final int WHITE = 0xFFEAF5F3;
	private static final int TIDE = 0xFF85DDC6;
	/** Gold, silver and bronze accents for the podium rows. */
	private static final int[] MEDALS = {0xFFFFD487, 0xFFD9E2E6, 0xFFE0A47A};
	private final String[] lines;
	private int page;

	public ResultsScreen(ResultsMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = W;
		imageHeight = H;
		lines = menu.summary.split("\n");
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(Component.literal("上一页"), button -> page = Math.max(0, page - 1))
				.bounds(leftPos + 15, topPos + H - 45, 54, 18).build());
		addRenderableWidget(Button.builder(Component.literal("下一页"), button -> page = Math.min(pages() - 1, page + 1))
				.bounds(leftPos + W - 69, topPos + H - 45, 54, 18).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
				.bounds(leftPos + 81, topPos + H - 23, 76, 18).build());
	}

	private int pages() {
		return Math.max(1, (Math.max(0, lines.length - 2) + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + W, y + H, 0xFF091B22);
		graphics.fill(x + 2, y + 2, x + W - 2, y + H - 2, 0xFF15343F);
		graphics.fill(x + 8, y + 7, x + W - 8, y + 9, TIDE);
		graphics.fill(x + 10, y + 54, x + W - 10, y + H - 53, 0xFF0B222C);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, "疯狂水世界  /  本局结算", 12, 16, GOLD, false);
		graphics.drawString(font, "获胜者：" + (lines.length > 0 ? lines[0] : "未知"), 12, 31, WHITE, false);
		graphics.drawString(font, "用时：" + (lines.length > 1 ? lines[1] : "0:00"), 12, 43, TIDE, false);
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = 2 + page * PAGE_SIZE + i;
			if (index >= lines.length || lines[index].isEmpty()) break;
			String[] entry = lines[index].split("\\|", 3);
			if (entry.length != 3) continue;
			int place = page * PAGE_SIZE + i + 1;
			int row = 59 + i * 12;
			if (place <= MEDALS.length) graphics.fill(10, row - 2, 12, row + 9, MEDALS[place - 1]);
			graphics.drawString(font, place + ". " + font.plainSubstrByWidth(entry[0], 79), 15, row,
					place <= MEDALS.length ? MEDALS[place - 1] : WHITE, false);
			graphics.drawString(font, String.format("%,d", Long.parseLong(entry[1])) + " 金", 108, row, GOLD, false);
			graphics.drawString(font, "开蚌 " + entry[2], 173, 59 + i * 12, TIDE, false);
		}
		graphics.drawString(font, "第 " + (page + 1) + " / " + pages() + " 页", 94, H - 39, WHITE, false);
		graphics.drawString(font, "管理员输入 /cww restart 开始下一局", 12, H - 61, WHITE, false);
	}
}
