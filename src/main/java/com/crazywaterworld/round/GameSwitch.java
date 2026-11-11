package com.crazywaterworld.round;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.clam.ClamOpeningAnimation;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.economy.MarketData;
import com.crazywaterworld.shop.ShopMenu;
import com.crazywaterworld.trade.TradeManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Saved per world; turning gameplay off never unregisters or deletes its items. */
public final class GameSwitch extends SavedData {
	private static final String NAME = "crazywaterworld_switch";
	private static final SavedData.Factory<GameSwitch> FACTORY = new SavedData.Factory<>(
			() -> new GameSwitch(CrazyWaterWorld.CONFIG.enabled), GameSwitch::load, null);
	private static final Set<MinecraftServer> RELOADING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<MinecraftServer> PENDING_RELOAD = Collections.newSetFromMap(new IdentityHashMap<>());
	private static volatile boolean lootEnabled;

	private boolean active;
	private long pausedAt = -1;

	private GameSwitch(boolean active) {
		this.active = active;
	}

	public static void register() {
		lootEnabled = CrazyWaterWorld.CONFIG.enabled;
		PayloadTypeRegistry.playS2C().register(GameSwitchPayload.TYPE, GameSwitchPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			boolean saved = active(server);
			if (saved != lootEnabled) {
				lootEnabled = saved;
				reloadLoot(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RELOADING.remove(server);
			PENDING_RELOAD.remove(server);
			lootEnabled = CrazyWaterWorld.CONFIG.enabled;
		});
	}

	private static GameSwitch get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
	}

	public static boolean active(MinecraftServer server) {
		return server != null && get(server).active;
	}

	public static boolean lootEnabled() {
		return lootEnabled;
	}

	public static void sync(ServerPlayer player) {
		ServerPlayNetworking.send(player, new GameSwitchPayload(active(player.getServer())));
	}

	public static boolean set(MinecraftServer server, boolean enabled) {
		GameSwitch state = get(server);
		if (state.active == enabled) return false;
		long now = server.overworld().getGameTime();
		state.active = enabled;
		lootEnabled = enabled;
		if (enabled) {
			long elapsed = state.pausedAt < 0 ? 0 : Math.max(0, now - state.pausedAt);
			Economy.data(server).skipTime(elapsed);
			MarketData.skipTime(server, elapsed);
			state.pausedAt = -1;
		} else {
			state.pausedAt = now;
			TradeManager.cancelAll();
			ClamOpeningAnimation.stop(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.containerMenu instanceof ShopMenu || player.containerMenu instanceof ResultsMenu) player.closeContainer();
			}
		}
		state.setDirty();
		if (enabled) RoundGame.startOnline(server);
		RoundGame.resendHud();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
		reloadLoot(server);
		return true;
	}

	private static void reloadLoot(MinecraftServer server) {
		if (!RELOADING.add(server)) {
			PENDING_RELOAD.add(server);
			return;
		}
		server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, error) ->
				server.execute(() -> {
					RELOADING.remove(server);
					if (error != null) CrazyWaterWorld.LOGGER.error("重新加载钓鱼战利品失败", error);
					if (PENDING_RELOAD.remove(server)) reloadLoot(server);
				}));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("active", active);
		tag.putLong("pausedAt", pausedAt);
		return tag;
	}

	private static GameSwitch load(CompoundTag tag, HolderLookup.Provider registries) {
		GameSwitch state = new GameSwitch(tag.getBoolean("active"));
		state.pausedAt = tag.getLong("pausedAt");
		return state;
	}
}
