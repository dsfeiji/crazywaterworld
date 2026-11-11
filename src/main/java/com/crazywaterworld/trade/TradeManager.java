package com.crazywaterworld.trade;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.round.GameSwitch;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Local multiplayer invitations and open sessions; only server-side callbacks mutate state. */
public final class TradeManager {
	private record Invitation(UUID sender, int tick) {
	}

	private static final Map<UUID, Invitation> INVITES = new HashMap<>();
	private static final Map<UUID, TradeSession> SESSIONS = new HashMap<>();

	private TradeManager() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!GameSwitch.active(world.getServer())) return InteractionResult.PASS;
			if (world.isClientSide || hand != InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sender) || !(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}
			request(sender, target);
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			TradeSession session = SESSIONS.get(id);
			if (session != null) session.cancel();
			INVITES.remove(id);
			INVITES.values().removeIf(invite -> invite.sender().equals(id));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (TradeSession session : new java.util.HashSet<>(SESSIONS.values())) {
				if (!session.valid()) session.cancel();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (TradeSession session : java.util.List.copyOf(SESSIONS.values())) session.cancel();
			INVITES.clear();
			SESSIONS.clear();
		});
	}

	public static void cancelAll() {
		for (TradeSession session : new java.util.HashSet<>(SESSIONS.values())) session.cancel();
		INVITES.clear();
	}

	/** Called before PlayerList.remove persists the departing player's inventory. */
	public static void beforePlayerSaved(ServerPlayer player) {
		UUID id = player.getUUID();
		TradeSession session = SESSIONS.get(id);
		if (session != null) session.cancel();
		INVITES.remove(id);
		INVITES.values().removeIf(invite -> invite.sender().equals(id));
	}

	public static void request(ServerPlayer sender, ServerPlayer target) {
		if (!GameSwitch.active(sender.getServer())) return;
		if (Economy.data(sender.getServer()).finished()) {
			sender.sendSystemMessage(Component.literal("§e本局已结束，无法发起交易"));
			return;
		}
		if (sender == target || SESSIONS.containsKey(sender.getUUID()) || SESSIONS.containsKey(target.getUUID())
				|| sender.level() != target.level() || sender.distanceToSqr(target) > 64) {
			sender.sendSystemMessage(Component.literal("§c交易需要双方都在线、空闲且相距不超过 8 格"));
			return;
		}
		if (!hasClam(sender) && !hasClam(target)) {
			sender.sendSystemMessage(Component.literal("§e玩家之间只能交易河蚌，双方背包里都没有河蚌"));
			return;
		}
		Invitation existing = INVITES.get(target.getUUID());
		if (existing != null && existing.sender().equals(sender.getUUID())
				&& sender.getServer().getTickCount() - existing.tick() < 1200) return;
		INVITES.put(target.getUUID(), new Invitation(sender.getUUID(), sender.getServer().getTickCount()));
		sender.sendSystemMessage(Component.literal("§b已向 " + target.getName().getString() + " 发出河蚌交易邀请"));
		target.sendSystemMessage(Component.literal("§b" + sender.getName().getString()
				+ " 邀请你交易河蚌（60 秒内有效） ").append(action("[接受]", "/cww accept", ChatFormatting.GREEN))
				.append(" ").append(action("[拒绝]", "/cww deny", ChatFormatting.RED)));
	}

	private static MutableComponent action(String label, String command, ChatFormatting color) {
		return Component.literal(label).withStyle(style -> style.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}

	private static boolean hasClam(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (TradeSession.tradable(inventory.getItem(i))) return true;
		}
		return false;
	}

	public static void deny(ServerPlayer target) {
		Invitation invitation = INVITES.remove(target.getUUID());
		if (invitation == null) {
			target.sendSystemMessage(Component.literal("§7没有待处理的交易邀请"));
			return;
		}
		target.sendSystemMessage(Component.literal("§7已拒绝交易邀请"));
		ServerPlayer sender = target.getServer().getPlayerList().getPlayer(invitation.sender());
		if (sender != null) {
			sender.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " 拒绝了你的交易邀请"));
		}
	}

	public static void accept(ServerPlayer target) {
		if (!GameSwitch.active(target.getServer())) return;
		if (Economy.data(target.getServer()).finished()) {
			target.sendSystemMessage(Component.literal("§e本局已结束，无法接受交易"));
			return;
		}
		Invitation invitation = INVITES.remove(target.getUUID());
		if (invitation == null || target.getServer().getTickCount() - invitation.tick() > 1200) {
			target.sendSystemMessage(Component.literal("§c没有有效的交易邀请"));
			return;
		}
		ServerPlayer sender = target.getServer().getPlayerList().getPlayer(invitation.sender());
		if (sender == null || SESSIONS.containsKey(sender.getUUID()) || SESSIONS.containsKey(target.getUUID())
				|| sender.level() != target.level() || sender.distanceToSqr(target) > 64) {
			target.sendSystemMessage(Component.literal("§c对方已离线、正在交易或离得太远"));
			return;
		}
		if (!hasClam(sender) && !hasClam(target)) {
			target.sendSystemMessage(Component.literal("§e玩家之间只能交易河蚌，双方背包里都没有河蚌"));
			return;
		}
		TradeSession session = new TradeSession(sender, target);
		SESSIONS.put(sender.getUUID(), session);
		SESSIONS.put(target.getUUID(), session);
		open(sender, target, session);
		if (!session.active()) return;
		if (!(sender.containerMenu instanceof TradeMenu)) {
			session.cancel();
			return;
		}
		open(target, sender, session);
		if (session.active() && !(target.containerMenu instanceof TradeMenu)) session.cancel();
	}

	private static void open(ServerPlayer player, ServerPlayer other, TradeSession session) {
		player.openMenu(new ExtendedScreenHandlerFactory<UUID>() {
			@Override
			public TradeMenu createMenu(int id, Inventory inventory, Player ignored) {
				return new TradeMenu(id, inventory, other.getUUID(), session);
			}

			@Override
			public Component getDisplayName() {
				return Component.literal("河蚌交易");
			}

			@Override
			public UUID getScreenOpeningData(ServerPlayer recipient) {
				return other.getUUID();
			}
		});
	}

	static void forget(TradeSession session, ServerPlayer first, ServerPlayer second) {
		SESSIONS.remove(first.getUUID(), session);
		SESSIONS.remove(second.getUUID(), session);
		CrazyWaterWorld.LOGGER.info("[疯狂水世界] 玩家交易结束: {} / {}", first.getName().getString(), second.getName().getString());
	}
}
