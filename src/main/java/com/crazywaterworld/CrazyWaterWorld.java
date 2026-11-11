package com.crazywaterworld;

import com.crazywaterworld.command.ModCommands;
import com.crazywaterworld.clam.ClamData;
import com.crazywaterworld.clam.ClamOpeningAnimation;
import com.crazywaterworld.config.ModConfig;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.loot.ModLoot;
import com.crazywaterworld.shop.ModMenus;
import com.crazywaterworld.trade.TradeManager;
import com.crazywaterworld.round.RoundGame;
import com.crazywaterworld.round.GameSwitch;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrazyWaterWorld implements ModInitializer {
	public static final String MOD_ID = "crazywaterworld";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ModConfig CONFIG;

	@Override
	public void onInitialize() {
		CONFIG = ModConfig.load();

		ModItems.register();
		GameSwitch.register();
		ClamData.register();
		ClamOpeningAnimation.register();
		ModLoot.register();
		ModCommands.register();
		ModMenus.register();
		TradeManager.register();
		RoundGame.register();

		LOGGER.info("[疯狂水世界] 加载完成");
	}
}
