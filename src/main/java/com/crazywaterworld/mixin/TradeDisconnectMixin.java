package com.crazywaterworld.mixin;

import com.crazywaterworld.trade.TradeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class TradeDisconnectMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void crazywaterworld$refundBeforeSave(ServerPlayer player, CallbackInfo info) {
		TradeManager.beforePlayerSaved(player);
	}
}
