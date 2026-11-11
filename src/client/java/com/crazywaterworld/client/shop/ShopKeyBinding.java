package com.crazywaterworld.client.shop;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 商店快捷键：默认 B 键，按下后向服务端发送 /cww shop。
 * 只有客户端 Minecraft 实例存在且已进入世界时才会触发。
 */
public final class ShopKeyBinding {
	private static final String CATEGORY = "key.categories.crazywaterworld";

	private static KeyMapping openShopKey;
	private static boolean enabled;

	private ShopKeyBinding() {
	}

	public static void register() {
		openShopKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.crazywaterworld.open_shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openShopKey.consumeClick()) {
				sendOpenShop(client);
			}
		});
	}

	public static void setEnabled(boolean active) {
		enabled = active;
	}

	private static void sendOpenShop(Minecraft client) {
		if (!enabled || client.player == null || client.getConnection() == null) {
			return;
		}
		client.getConnection().sendCommand("cww shop");
	}
}
