package com.crazywaterworld.round;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-player round status for the always-visible HUD; sent only when it changes. */
public record HudPayload(long balance, long target, int rank, int players, String leader, long leaderBalance,
		boolean finished) implements CustomPacketPayload {
	public static final Type<HudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
			"crazywaterworld", "hud"));
	public static final StreamCodec<FriendlyByteBuf, HudPayload> CODEC =
			CustomPacketPayload.codec(HudPayload::write, HudPayload::new);

	private HudPayload(FriendlyByteBuf buf) {
		this(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64),
				buf.readVarLong(), buf.readBoolean());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarLong(balance);
		buf.writeVarLong(target);
		buf.writeVarInt(rank);
		buf.writeVarInt(players);
		buf.writeUtf(leader, 64);
		buf.writeVarLong(leaderBalance);
		buf.writeBoolean(finished);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
