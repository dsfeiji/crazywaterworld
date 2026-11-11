package com.crazywaterworld.round;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.economy.GoldData;
import com.crazywaterworld.economy.MarketData;
import com.crazywaterworld.trade.TradeManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One persistent LAN round. Settlement is deferred to the end of the tick to finish pending trades. */
public final class RoundGame {
	private static final Set<MinecraftServer> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<UUID, HudPayload> LAST_HUD = new HashMap<>();

	private RoundGame() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(HudPayload.TYPE, HudPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 == 0 && GameSwitch.active(server)) syncHud(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_HUD.remove(handler.player.getUUID()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!GameSwitch.active(server)) return;
			ServerPlayer player = handler.player;
			GoldData state = Economy.data(server);
			state.updateName(player.getUUID(), player.getName().getString());
			if (!state.started()) state.begin(server.overworld().getGameTime());
			if (state.finished()) {
				ResultsOpener.open(player, summary(server));
			} else {
				giveRod(player, state, false);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!GameSwitch.active(server)) return;
			if (!PENDING.remove(server)) return;
			GoldData state = Economy.data(server);
			if (!state.finished()) return;
			TradeManager.cancelAll();
			server.getPlayerList().broadcastSystemMessage(Component.literal("§6[疯狂水世界] "
					+ state.winnerName() + " 率先达到 " + CrazyWaterWorld.CONFIG.winTarget + " 金币，赢得本局！"), false);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.serverLevel().sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 2,
						player.getZ(), 30, 0.6, 0.6, 0.6, 0.1);
				player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
						SoundSource.PLAYERS, 1.0F, 1.0F);
				ResultsOpener.open(player, summary(server));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.remove(server);
			LAST_HUD.clear();
		});
	}

	/** Sends each player's HUD numbers when they differ from what that client last received. */
	private static void syncHud(MinecraftServer server) {
		GoldData state = Economy.data(server);
		List<UUID> ranks = state.ranking();
		UUID leader = ranks.isEmpty() ? null : ranks.get(0);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, HudPayload.TYPE)) continue;
			int rank = ranks.indexOf(player.getUUID()) + 1;
			HudPayload hud = new HudPayload(state.get(player.getUUID()), CrazyWaterWorld.CONFIG.winTarget, rank,
					ranks.size(), leader == null ? "" : state.name(leader), leader == null ? 0 : state.get(leader),
					state.finished());
			if (hud.equals(LAST_HUD.get(player.getUUID()))) continue;
			LAST_HUD.put(player.getUUID(), hud);
			ServerPlayNetworking.send(player, hud);
		}
	}

	/** Forget what clients were sent, e.g. after the game is switched back on and the HUD was hidden. */
	public static void resendHud() {
		LAST_HUD.clear();
	}

	public static void scheduleResults(MinecraftServer server) {
		if (!GameSwitch.active(server)) return;
		PENDING.add(server);
	}

	public static void startOnline(MinecraftServer server) {
		if (!GameSwitch.active(server)) return;
		GoldData state = Economy.data(server);
		if (state.finished()) return;
		state.begin(server.overworld().getGameTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			state.updateName(player.getUUID(), player.getName().getString());
			giveRod(player, state, true);
		}
	}

	public static void restart(MinecraftServer server) {
		if (!GameSwitch.active(server)) return;
		TradeManager.cancelAll();
		PENDING.remove(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.containerMenu instanceof ResultsMenu) player.closeContainer();
		}
		Economy.data(server).restart(server.overworld().getGameTime());
		MarketData.restart(server);
		startOnline(server);
		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"§b[疯狂水世界] 新一局开始！金币与市场已重置，背包物品未清空。目标 "
						+ CrazyWaterWorld.CONFIG.winTarget + " 金币。"), false);
	}

	/**
	 * Gives a fishing rod only when the player has none. On join this happens once per round;
	 * when a round is started or restarted every online player is checked again.
	 */
	private static void giveRod(ServerPlayer player, GoldData state, boolean recheck) {
		boolean firstThisRound = state.issueRod(player.getUUID());
		if (state.finished() || !firstThisRound && !recheck || hasRod(player)) return;
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.FISHING_ROD));
		player.sendSystemMessage(Component.literal("§b[疯狂水世界] 获得一根原版钓鱼竿！先赚到 "
				+ CrazyWaterWorld.CONFIG.winTarget + " 金币者获胜。"));
	}

	private static boolean hasRod(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(Items.FISHING_ROD)) return true;
		}
		return player.containerMenu.getCarried().is(Items.FISHING_ROD);
	}

	public static void openedClam(ServerPlayer player) {
		if (!GameSwitch.active(player.getServer())) return;
		Economy.data(player.getServer()).openedClam(player.getUUID());
	}

	public static int rank(MinecraftServer server, UUID player) {
		int index = Economy.data(server).ranking().indexOf(player);
		return index < 0 ? 0 : index + 1;
	}

	public static String summary(MinecraftServer server) {
		GoldData state = Economy.data(server);
		long ticks = state.elapsedTicks(server.overworld().getGameTime());
		StringBuilder result = new StringBuilder(state.winnerName()).append('\n')
				.append(ticks / 1200).append(':').append(String.format("%02d", (ticks / 20) % 60)).append('\n');
		List<UUID> ranks = state.ranking();
		for (UUID id : ranks) {
			result.append(state.name(id)).append('|').append(state.get(id)).append('|')
					.append(state.openedClams(id)).append('\n');
		}
		return result.toString();
	}

	public static void sendRanking(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		GoldData state = Economy.data(server);
		player.sendSystemMessage(Component.literal("§b[疯狂水世界] 当前排行（目标 "
				+ CrazyWaterWorld.CONFIG.winTarget + " 金币，已进行 "
				+ state.elapsedTicks(server.overworld().getGameTime()) / 1200 + " 分钟）"));
		List<UUID> ranks = state.ranking();
		for (int i = 0; i < ranks.size(); i++) {
			UUID id = ranks.get(i);
			player.sendSystemMessage(Component.literal("§e" + (i + 1) + ". " + state.name(id)
					+ "  " + state.get(id) + " 金币  开蚌 " + state.openedClams(id) + " 次"));
		}
	}
}
