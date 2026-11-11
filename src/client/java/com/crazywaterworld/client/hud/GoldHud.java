package com.crazywaterworld.client.hud;

import com.crazywaterworld.round.HudPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Top-left round status: balance, progress to the target, rank and the current leader. */
public final class GoldHud {
	private static final int W = 160;
	private static final int PANEL = 0xB0091B22;
	private static final int TRACK = 0xFF1A3A46;
	private static final int TIDE = 0xFF85DDC6;
	private static final int GOLD = 0xFFFFD487;
	private static final int MUTED = 0xFFC9DADC;
	private static final int GAIN = 0xFF8CF29B;
	private static final int LOSS = 0xFFFF9999;
	private static final int FLASH_TICKS = 40;

	private static HudPayload hud;
	private static boolean active;
	private static long delta;
	private static int deltaTick;
	/** Bar fill eased toward the real value so gains slide in rather than jump. */
	private static float shownProgress;

	private GoldHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(GoldHud::render);
	}

	public static void setActive(boolean enabled) {
		active = enabled;
		if (!enabled) {
			hud = null;
			delta = 0;
			deltaTick = 0;
		}
	}

	public static void update(HudPayload next) {
		if (hud != null && next.balance() != hud.balance()) {
			delta = next.balance() - hud.balance();
			deltaTick = FLASH_TICKS;
		}
		if (hud == null) shownProgress = progress(next);
		hud = next;
	}

	public static void tick() {
		if (deltaTick > 0) deltaTick--;
		if (hud != null) shownProgress += (progress(hud) - shownProgress) * 0.25F;
	}

	private static float progress(HudPayload payload) {
		return payload.target() <= 0 ? 0 : Mth.clamp((float) payload.balance() / payload.target(), 0, 1);
	}

	private static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft client = Minecraft.getInstance();
		if (!active || hud == null || client.options.hideGui || client.getDebugOverlay().showDebugScreen()) return;
		Font font = client.font;
		int x = 4;
		int y = 4;
		boolean leading = hud.rank() == 1;
		boolean showLeader = !leading && !hud.leader().isEmpty();
		int h = showLeader ? 66 : 54;
		g.fill(x, y, x + W, y + h, PANEL);
		g.fill(x, y, x + 2, y + h, TIDE);
		g.fill(x + 6, y + 17, x + W - 6, y + 18, TRACK);

		g.pose().pushPose();
		g.pose().translate(x + 5, y + 3, 0);
		g.pose().scale(0.75F, 0.75F, 1);
		g.renderItem(new ItemStack(Items.GOLD_NUGGET), 0, 0);
		g.pose().popPose();
		String balance = String.format("%,d", hud.balance());
		g.drawString(font, balance, x + 19, y + 5, GOLD, true);
		if (deltaTick > 0 && delta != 0) {
			int alpha = Mth.clamp(deltaTick * 255 / 20, 16, 255) << 24;
			String change = (delta > 0 ? "+" : "") + String.format("%,d", delta);
			g.drawString(font, change, x + W - 6 - font.width(change), y + 21,
					((delta > 0 ? GAIN : LOSS) & 0xFFFFFF) | alpha, true);
		}
		String rank = hud.finished() ? "已结束" : hud.rank() > 0 ? "第 " + hud.rank() + "/" + hud.players() + " 名" : "";
		g.drawString(font, rank, x + 6, y + 21, TIDE, false);

		int barY = y + 33;
		g.fill(x + 6, barY, x + W - 6, barY + 4, TRACK);
		g.fill(x + 6, barY, x + 6 + Math.round((W - 12) * shownProgress), barY + 4, leading ? GOLD : TIDE);
		String target = "目标 " + String.format("%,d", hud.target());
		String percent = Math.round(progress(hud) * 100) + "%";
		g.drawString(font, target, x + 6, y + 41, MUTED, false);
		g.drawString(font, percent, x + W - 6 - font.width(percent), y + 5, MUTED, false);
		if (showLeader) {
			String amount = String.format("%,d", hud.leaderBalance());
			String leader = "领先 " + font.plainSubstrByWidth(hud.leader(), Math.max(0, W - 42 - font.width(amount)));
			g.drawString(font, leader, x + 6, y + 54, GOLD, false);
			g.drawString(font, amount, x + W - 6 - font.width(amount), y + 54, GOLD, false);
		}
	}
}
