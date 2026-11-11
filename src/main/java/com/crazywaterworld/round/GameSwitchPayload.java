package com.crazywaterworld.round;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameSwitchPayload(boolean active) implements CustomPacketPayload {
	public static final Type<GameSwitchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
			"crazywaterworld", "game_switch"));
	public static final StreamCodec<FriendlyByteBuf, GameSwitchPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, GameSwitchPayload::active, GameSwitchPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
