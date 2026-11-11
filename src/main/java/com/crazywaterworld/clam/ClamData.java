package com.crazywaterworld.clam;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.GameSwitch;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rewards live only in the world save; the item carries an opaque UUID, never the exact prize. */
public final class ClamData extends SavedData {
	private static final String NAME = "crazywaterworld_clams";
	private static final String TOKEN = "cww_clam_token";
	private static final SavedData.Factory<ClamData> FACTORY =
			new SavedData.Factory<>(ClamData::new, ClamData::load, null);

	private final Map<UUID, Integer> sealed = new HashMap<>();

	private static ClamData forLevel(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (GameSwitch.active(level.getServer()) && entity instanceof ItemEntity dropped && dropped.getItem().is(ModItems.CLAM)
					&& dropped.getItem().getCount() == 1) {
				ItemStack stack = dropped.getItem();
				if (token(stack) == null && seal(level, stack) >= 0) dropped.setItem(stack);
			}
		});
	}

	public static UUID token(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		return tag.hasUUID(TOKEN) ? tag.getUUID(TOKEN) : null;
	}

	/** Returns the reward index, or -1 for a consumed/invalid token. */
	public static int seal(ServerLevel level, ItemStack stack) {
		if (!GameSwitch.active(level.getServer())) return -1;
		if (!stack.is(ModItems.CLAM) || stack.getCount() != 1) return -1;
		ClamData data = forLevel(level);
		UUID id = token(stack);
		if (id != null) return data.sealed.getOrDefault(id, -1);
		id = UUID.randomUUID();
		int reward = ClamLootTable.roll(level.random);
		data.sealed.put(id, reward);
		data.setDirty();
		UUID finalId = id;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TOKEN, finalId));
		return reward;
	}

	public static int consume(ServerLevel level, ItemStack stack) {
		UUID id = token(stack);
		if (id == null) return -1;
		ClamData data = forLevel(level);
		Integer reward = data.sealed.remove(id);
		if (reward == null) return -1;
		data.setDirty();
		return reward;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag entries = new CompoundTag();
		sealed.forEach((id, reward) -> entries.putInt(id.toString(), reward));
		tag.put("sealed", entries);
		return tag;
	}

	private static ClamData load(CompoundTag tag, HolderLookup.Provider registries) {
		ClamData data = new ClamData();
		CompoundTag entries = tag.getCompound("sealed");
		for (String key : entries.getAllKeys()) {
			try {
				int reward = entries.getInt(key);
				if (ClamLootTable.reward(reward) != null) data.sealed.put(UUID.fromString(key), reward);
			} catch (IllegalArgumentException e) {
				CrazyWaterWorld.LOGGER.warn("跳过无效的河蚌编号: {}", key);
			}
		}
		return data;
	}
}
