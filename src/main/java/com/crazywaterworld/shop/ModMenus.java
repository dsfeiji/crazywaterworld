package com.crazywaterworld.shop;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.trade.TradeMenu;
import com.crazywaterworld.round.ResultsMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

/**
 * 商店菜单类型注册。
 * 用 ExtendedScreenHandlerType 以便服务端把数据（这里是打开位置）同步到客户端。
 */
public final class ModMenus {
	public static MenuType<ShopMenu> SHOP;
	public static MenuType<TradeMenu> TRADE;
	public static MenuType<ResultsMenu> RESULTS;

	private ModMenus() {
	}

	public static void register() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, "shop");
		StreamCodec<? super io.netty.buffer.ByteBuf, BlockPos> codec = BlockPos.STREAM_CODEC;
		SHOP = Registry.register(BuiltInRegistries.MENU, id,
				new ExtendedScreenHandlerType<>(ShopMenu::new, codec));
		TRADE = Registry.register(BuiltInRegistries.MENU,
				ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, "trade"),
				new ExtendedScreenHandlerType<>(TradeMenu::new, UUIDUtil.STREAM_CODEC));
		RESULTS = Registry.register(BuiltInRegistries.MENU,
				ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, "results"),
				new ExtendedScreenHandlerType<>(ResultsMenu::new, ByteBufCodecs.stringUtf8(32767)));
	}
}
