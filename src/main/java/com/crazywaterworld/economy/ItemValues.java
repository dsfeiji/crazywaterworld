package com.crazywaterworld.economy;

import com.crazywaterworld.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 物品售价表。河蚌不能卖给商店，只能开蚌或在玩家之间交易。
 * 价格在服务端固定，客户端只能显示，不能决定价格。
 */
public final class ItemValues {
	private ItemValues() {
	}

	/** 返回单个物品的售价，不可出售的物品（含河蚌）返回 0。必须在模组物品注册之后调用。 */
	public static long priceOf(Item item) {
		if (item == ModItems.SMELLY_SHOE) return 5L;
		if (item == ModItems.WASTE_FILTER) return 10L;
		if (item == ModItems.RIVER_PEARL) return 1_200L;
		if (item == ModItems.ELECTRIC_EEL) return 2_500L;
		if (item == ModItems.SEA_FILTER) return 5_000L;
		if (item == ModItems.EEL_GENERATOR) return 25_000L;
		if (item == ModItems.ANCIENT_PEARL) return 60_000L;
		if (item == Items.COD) return 80L;
		if (item == Items.SALMON) return 110L;
		if (item == Items.PUFFERFISH) return 180L;
		if (item == Items.TROPICAL_FISH) return 240L;
		return 0L;
	}

	public static List<Item> sellableItems() {
		return List.of(ModItems.SMELLY_SHOE, ModItems.WASTE_FILTER, ModItems.RIVER_PEARL,
				ModItems.ELECTRIC_EEL, ModItems.SEA_FILTER, ModItems.EEL_GENERATOR,
				ModItems.ANCIENT_PEARL, Items.COD, Items.SALMON, Items.PUFFERFISH, Items.TROPICAL_FISH);
	}
}
