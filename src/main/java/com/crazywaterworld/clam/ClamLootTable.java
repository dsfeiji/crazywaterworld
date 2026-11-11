package com.crazywaterworld.clam;

import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.CrazyWaterWorld;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/** Clam rewards: 60% junk, 30% ordinary, 9% valuable, 1% jackpot. */
public final class ClamLootTable {
	private ClamLootTable() {
	}

	/** Rewards from this index up (eel generator, ancient pearl) are announced to the whole server. */
	public static final int ANNOUNCE_FROM = 4;

	public static int roll(RandomSource random) {
		int draw = random.nextInt(100);
		if (draw < CrazyWaterWorld.CONFIG.clamJunkPercent) return random.nextInt(60) < 35 ? 0 : 1;
		draw -= CrazyWaterWorld.CONFIG.clamJunkPercent;
		if (draw < CrazyWaterWorld.CONFIG.clamOrdinaryPercent) return 2;
		draw -= CrazyWaterWorld.CONFIG.clamOrdinaryPercent;
		if (draw < CrazyWaterWorld.CONFIG.clamRarePercent) return random.nextInt(9) < 7 ? 3 : 4;
		return 5;
	}

	public static Item reward(int index) {
		return switch (index) {
			case 0 -> ModItems.SMELLY_SHOE;
			case 1 -> ModItems.WASTE_FILTER;
			case 2 -> ModItems.RIVER_PEARL;
			case 3 -> ModItems.SEA_FILTER;
			case 4 -> ModItems.EEL_GENERATOR;
			case 5 -> ModItems.ANCIENT_PEARL;
			default -> null;
		};
	}
}
