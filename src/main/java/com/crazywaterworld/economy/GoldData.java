package com.crazywaterworld.economy;

import com.crazywaterworld.CrazyWaterWorld;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 每个世界一份的金币数据，保存在存档的 data 目录中。
 */
public class GoldData extends SavedData {
	public static final String NAME = "crazywaterworld_gold";

	public static final SavedData.Factory<GoldData> FACTORY =
			new SavedData.Factory<>(GoldData::new, GoldData::load, null);

	private final Map<UUID, Long> balances = new HashMap<>();
	private final Map<UUID, String> names = new HashMap<>();
	private final Map<UUID, Integer> clamsOpened = new HashMap<>();
	private final Set<UUID> issuedRods = new HashSet<>();
	private final Map<UUID, Integer> milestones = new HashMap<>();
	private final Map<UUID, Integer> netsBought = new HashMap<>();
	private long startedAt = -1;
	private long finishedAt = -1;
	private UUID winner;
	private String winnerName = "";

	public long get(UUID player) {
		return balances.getOrDefault(player, 0L);
	}

	public void set(UUID player, long amount) {
		balances.put(player, amount);
		setDirty();
	}

	public boolean started() {
		return startedAt >= 0;
	}

	public boolean finished() {
		return winner != null;
	}

	public UUID winner() {
		return winner;
	}

	public String winnerName() {
		return winnerName;
	}

	public long elapsedTicks(long now) {
		return started() ? Math.max(0, (finished() ? finishedAt : now) - startedAt) : 0;
	}

	public void begin(long now) {
		if (started()) return;
		startedAt = now;
		setDirty();
	}

	public void skipTime(long ticks) {
		if (ticks <= 0 || !started() || finished()) return;
		startedAt += ticks;
		setDirty();
	}

	public void restart(long now) {
		balances.clear();
		clamsOpened.clear();
		issuedRods.clear();
		milestones.clear();
		netsBought.clear();
		winner = null;
		winnerName = "";
		startedAt = now;
		finishedAt = -1;
		setDirty();
	}

	public boolean issueRod(UUID player) {
		if (finished() || !issuedRods.add(player)) return false;
		balances.putIfAbsent(player, 0L);
		setDirty();
		return true;
	}

	public void updateName(UUID player, String name) {
		if (!name.equals(names.put(player, name))) setDirty();
	}

	public String name(UUID player) {
		return names.getOrDefault(player, player.toString().substring(0, 8));
	}

	public boolean declareWinner(UUID player, String name, long now) {
		if (finished()) return false;
		winner = player;
		winnerName = name;
		finishedAt = now;
		setDirty();
		return true;
	}

	public void openedClam(UUID player) {
		clamsOpened.merge(player, 1, Integer::sum);
		setDirty();
	}

	public int openedClams(UUID player) {
		return clamsOpened.getOrDefault(player, 0);
	}

	/** Records the highest progress milestone (percent) reached; returns true only the first time. */
	public boolean reachMilestone(UUID player, int percent) {
		if (milestones.getOrDefault(player, 0) >= percent) return false;
		milestones.put(player, percent);
		setDirty();
		return true;
	}

	public int netsBought(UUID player) {
		return netsBought.getOrDefault(player, 0);
	}

	public void boughtNet(UUID player) {
		netsBought.merge(player, 1, Integer::sum);
		setDirty();
	}

	public List<UUID> ranking() {
		return balances.keySet().stream().sorted((left, right) -> {
			int byBalance = Long.compare(get(right), get(left));
			return byBalance != 0 ? byBalance : left.compareTo(right);
		}).toList();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag list = new CompoundTag();
		for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
			list.putLong(entry.getKey().toString(), entry.getValue());
		}
		tag.put("balances", list);
		CompoundTag savedNames = new CompoundTag();
		names.forEach((player, name) -> savedNames.putString(player.toString(), name));
		tag.put("names", savedNames);
		CompoundTag savedClams = new CompoundTag();
		clamsOpened.forEach((player, count) -> savedClams.putInt(player.toString(), count));
		tag.put("clamsOpened", savedClams);
		CompoundTag savedRods = new CompoundTag();
		issuedRods.forEach(player -> savedRods.putBoolean(player.toString(), true));
		tag.put("issuedRods", savedRods);
		tag.put("milestones", saveCounts(milestones));
		tag.put("netsBought", saveCounts(netsBought));
		tag.putLong("startedAt", startedAt);
		tag.putLong("finishedAt", finishedAt);
		if (winner != null) tag.putUUID("winner", winner);
		tag.putString("winnerName", winnerName);
		return tag;
	}

	private static CompoundTag saveCounts(Map<UUID, Integer> counts) {
		CompoundTag saved = new CompoundTag();
		counts.forEach((player, count) -> saved.putInt(player.toString(), count));
		return saved;
	}

	private static void loadCounts(CompoundTag saved, Map<UUID, Integer> counts) {
		for (String key : saved.getAllKeys()) {
			try { counts.put(UUID.fromString(key), saved.getInt(key)); }
			catch (IllegalArgumentException e) { CrazyWaterWorld.LOGGER.warn("跳过无效的玩家编号: {}", key); }
		}
	}

	private static GoldData load(CompoundTag tag, HolderLookup.Provider registries) {
		GoldData data = new GoldData();
		CompoundTag list = tag.getCompound("balances");
		for (String key : list.getAllKeys()) {
			try {
				data.balances.put(UUID.fromString(key), list.getLong(key));
			} catch (IllegalArgumentException e) {
				CrazyWaterWorld.LOGGER.warn("[疯狂水世界] 跳过无效的玩家 UUID: {}", key);
			}
		}
		for (String key : tag.getCompound("names").getAllKeys()) {
			try { data.names.put(UUID.fromString(key), tag.getCompound("names").getString(key)); }
			catch (IllegalArgumentException e) { CrazyWaterWorld.LOGGER.warn("跳过无效的玩家名称编号: {}", key); }
		}
		for (String key : tag.getCompound("clamsOpened").getAllKeys()) {
			try { data.clamsOpened.put(UUID.fromString(key), tag.getCompound("clamsOpened").getInt(key)); }
			catch (IllegalArgumentException e) { CrazyWaterWorld.LOGGER.warn("跳过无效的开蚌统计编号: {}", key); }
		}
		for (String key : tag.getCompound("issuedRods").getAllKeys()) {
			try { data.issuedRods.add(UUID.fromString(key)); }
			catch (IllegalArgumentException e) { CrazyWaterWorld.LOGGER.warn("跳过无效的发竿编号: {}", key); }
		}
		loadCounts(tag.getCompound("milestones"), data.milestones);
		loadCounts(tag.getCompound("netsBought"), data.netsBought);
		if (tag.contains("startedAt")) data.startedAt = tag.getLong("startedAt");
		if (tag.contains("finishedAt")) data.finishedAt = tag.getLong("finishedAt");
		if (tag.hasUUID("winner")) data.winner = tag.getUUID("winner");
		data.winnerName = tag.getString("winnerName");
		return data;
	}
}
