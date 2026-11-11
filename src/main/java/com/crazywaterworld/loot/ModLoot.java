package com.crazywaterworld.loot;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.GameSwitch;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/** One weighted catch with any vanilla fishing rod: sellable fish or mod catches only. */
public final class ModLoot {
	private ModLoot() {
	}

	public static void register() {
		LootTableEvents.REPLACE.register((key, original, source, registries) -> {
			if (!GameSwitch.lootEnabled()) return null;
			if (key.equals(BuiltInLootTables.FISHING)) return fishingWithModItems();
			return null;
		});
		CrazyWaterWorld.LOGGER.info("[疯狂水世界] 四种可售鱼与模组渔获奖池已注册");
	}

	/** The unattended net follows the fishing pool's configured mod/vanilla proportions. */
	public static ItemStack rollNetCatch(ServerLevel level) {
		int roll = level.random.nextInt(1000);
		int mod = Math.round(CrazyWaterWorld.CONFIG.catchChance * 1000);
		if (roll < mod) {
			int catchRoll = level.random.nextInt(100);
			if (catchRoll < 2) return new ItemStack(ModItems.SMELLY_SHOE);
			if (catchRoll < 3) return new ItemStack(ModItems.WASTE_FILTER);
			if (catchRoll < 4) return new ItemStack(ModItems.SEA_FILTER);
			if (catchRoll < 5) return new ItemStack(ModItems.ELECTRIC_EEL);
			return new ItemStack(ModItems.CLAM);
		}
		int fish = level.random.nextInt(100);
		return new ItemStack(fish < 60 ? Items.COD : fish < 85 ? Items.SALMON
				: fish < 98 ? Items.PUFFERFISH : Items.TROPICAL_FISH);
	}

	private static LootTable fishingWithModItems() {
		int mod = Math.round(CrazyWaterWorld.CONFIG.catchChance * 1000);
		int vanilla = 1000 - mod;
		LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));

		// Scale by 100 so even a 0.1% configured share retains every possible catch.
		int salmon = vanilla * 25;
		int pufferfish = vanilla * 13;
		int tropical = vanilla * 2;
		int cod = vanilla * 60;
		if (cod > 0) pool.add(LootItem.lootTableItem(Items.COD).setWeight(cod));
		if (salmon > 0) pool.add(LootItem.lootTableItem(Items.SALMON).setWeight(salmon));
		if (pufferfish > 0) pool.add(LootItem.lootTableItem(Items.PUFFERFISH).setWeight(pufferfish).setQuality(100));
		if (tropical > 0) pool.add(LootItem.lootTableItem(Items.TROPICAL_FISH).setWeight(tropical).setQuality(200));

		if (mod > 0) {
			int shoe = mod * 2;
			int waste = mod;
			int filter = mod;
			int eel = mod;
			int clam = mod * 95;
			if (shoe > 0) pool.add(LootItem.lootTableItem(ModItems.SMELLY_SHOE).setWeight(shoe));
			if (waste > 0) pool.add(LootItem.lootTableItem(ModItems.WASTE_FILTER).setWeight(waste));
			if (filter > 0) pool.add(LootItem.lootTableItem(ModItems.SEA_FILTER).setWeight(filter).setQuality(100));
			if (eel > 0) pool.add(LootItem.lootTableItem(ModItems.ELECTRIC_EEL).setWeight(eel).setQuality(100));
			if (clam > 0) pool.add(LootItem.lootTableItem(ModItems.CLAM).setWeight(clam).setQuality(200));
		}
		return LootTable.lootTable().setParamSet(LootContextParamSets.FISHING)
				.setRandomSequence(BuiltInLootTables.FISHING.location()).withPool(pool).build();
	}
}
