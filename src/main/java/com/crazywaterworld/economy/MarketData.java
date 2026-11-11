package com.crazywaterworld.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** A bounded 60%-100% market price. Unsold goods recover one demand step per minute (full recovery in 20 minutes). */
public final class MarketData extends SavedData {
	private static final SavedData.Factory<MarketData> FACTORY =
			new SavedData.Factory<>(MarketData::new, MarketData::load, null);
	private static final long RECOVERY_TICKS = 1200;
	private final Map<String, Integer> demand = new HashMap<>();
	private final Map<String, Long> updatedAt = new HashMap<>();

	private static MarketData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "crazywaterworld_market");
	}

	private static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private int currentDemand(String key, long now) {
		long elapsed = Math.max(0, now - updatedAt.getOrDefault(key, now));
		return (int) Math.max(0, demand.getOrDefault(key, 0) - elapsed / RECOVERY_TICKS);
	}

	public static long price(MinecraftServer server, Item item) {
		long base = ItemValues.priceOf(item);
		if (base <= 0) return 0;
		int discount = Math.min(40, get(server).currentDemand(key(item), server.overworld().getGameTime()) * 2);
		return Math.max(1, base * (100 - discount) / 100);
	}

	public static void sold(MinecraftServer server, Item item, int count) {
		if (count <= 0 || ItemValues.priceOf(item) <= 0) return;
		MarketData data = get(server);
		String key = key(item);
		long now = server.overworld().getGameTime();
		data.demand.put(key, Math.min(20, data.currentDemand(key, now) + count));
		data.updatedAt.put(key, now);
		data.setDirty();
	}

	public static void restart(MinecraftServer server) {
		MarketData data = get(server);
		data.demand.clear();
		data.updatedAt.clear();
		data.setDirty();
	}

	public static void skipTime(MinecraftServer server, long ticks) {
		if (ticks <= 0) return;
		MarketData data = get(server);
		data.updatedAt.replaceAll((key, time) -> time + ticks);
		data.setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag entries = new CompoundTag();
		demand.forEach((key, value) -> {
			CompoundTag entry = new CompoundTag();
			entry.putInt("demand", value);
			entry.putLong("updatedAt", updatedAt.getOrDefault(key, 0L));
			entries.put(key, entry);
		});
		tag.put("entries", entries);
		return tag;
	}

	private static MarketData load(CompoundTag tag, HolderLookup.Provider registries) {
		MarketData data = new MarketData();
		CompoundTag entries = tag.getCompound("entries");
		for (String key : entries.getAllKeys()) {
			CompoundTag entry = entries.getCompound(key);
			data.demand.put(key, Math.max(0, Math.min(20, entry.getInt("demand"))));
			data.updatedAt.put(key, entry.getLong("updatedAt"));
		}
		return data;
	}
}
