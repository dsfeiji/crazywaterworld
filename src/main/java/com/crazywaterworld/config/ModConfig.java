package com.crazywaterworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置，保存在 config/crazywaterworld.json。
 * enabled 为总开关，修改后需要重启游戏生效。
 */
public class ModConfig {
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("crazywaterworld.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 模组总开关。false 时模组不注册任何内容 */
	public boolean enabled = true;
	/** 获胜所需金币 */
	public long winTarget = 100_000L;
	/** 钓到模组物品的概率（0~1） */
	public float catchChance = 0.4f;
	/** 河蚌四档概率，必须合计 100。档内物品权重由代码确定。 */
	public int clamJunkPercent = 60;
	public int clamOrdinaryPercent = 30;
	public int clamRarePercent = 9;
	public int clamJackpotPercent = 1;

	public static ModConfig load() {
		ModConfig config = new ModConfig();
		try {
			if (Files.exists(PATH)) {
				ModConfig loaded = GSON.fromJson(Files.readString(PATH), ModConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} else {
				Files.createDirectories(PATH.getParent());
				Files.writeString(PATH, GSON.toJson(config));
			}
		} catch (IOException | JsonParseException e) {
			com.crazywaterworld.CrazyWaterWorld.LOGGER.error("读取配置失败，使用默认配置", e);
		}
		if (config.winTarget <= 0) config.winTarget = 100_000L;
		if (Float.isNaN(config.catchChance)) config.catchChance = 0.4f;
		config.catchChance = Math.max(0, Math.min(1, config.catchChance));
		if (config.clamJunkPercent < 0 || config.clamOrdinaryPercent < 0 || config.clamRarePercent < 0
				|| config.clamJackpotPercent < 0 || (long) config.clamJunkPercent + config.clamOrdinaryPercent
				+ config.clamRarePercent + config.clamJackpotPercent != 100) {
			com.crazywaterworld.CrazyWaterWorld.LOGGER.warn("河蚌概率之和必须为 100%，已使用 60/30/9/1 默认配置");
			config.clamJunkPercent = 60;
			config.clamOrdinaryPercent = 30;
			config.clamRarePercent = 9;
			config.clamJackpotPercent = 1;
		}
		return config;
	}
}
