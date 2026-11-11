package com.crazywaterworld.client;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.client.hud.GoldHud;
import com.crazywaterworld.client.shop.ShopKeyBinding;
import com.crazywaterworld.client.shop.ShopScreen;
import com.crazywaterworld.client.trade.TradeScreen;
import com.crazywaterworld.client.round.ResultsScreen;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.shop.ModMenus;
import com.crazywaterworld.round.GameSwitchPayload;
import com.crazywaterworld.round.HudPayload;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端初始化：界面、HUD 与网络接收。交易与结算界面只用原版控件，不依赖 Modern UI。
 */
public class CrazyWaterWorldClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.TRADE, TradeScreen::new);
		MenuScreens.register(ModMenus.RESULTS, ResultsScreen::new);
		if (FabricLoader.getInstance().isModLoaded("modernui")) {
			MenuScreens.register(ModMenus.SHOP, ShopScreen::new);
			ShopKeyBinding.register();
		} else {
			CrazyWaterWorld.LOGGER.warn("未安装 Modern UI：可保留物品并游玩其他小游戏，开启疯狂水世界前需要安装客户端界面依赖");
		}
		BlockRenderLayerMap.INSTANCE.putBlock(ModItems.SALVAGE_NET, RenderType.cutout());
		GoldHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> GoldHud.tick());
		ClientPlayNetworking.registerGlobalReceiver(GameSwitchPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			ShopKeyBinding.setEnabled(payload.active());
			GoldHud.setActive(payload.active());
		}));
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE,
				(payload, context) -> context.client().execute(() -> GoldHud.update(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ShopKeyBinding.setEnabled(false);
			GoldHud.setActive(false);
		});
	}
}
