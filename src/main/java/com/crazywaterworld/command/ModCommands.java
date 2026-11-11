package com.crazywaterworld.command;

import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.shop.ShopOpener;
import com.crazywaterworld.round.ResultsOpener;
import com.crazywaterworld.round.RoundGame;
import com.crazywaterworld.round.GameSwitch;
import com.crazywaterworld.trade.TradeManager;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * 命令：
 * /cww start  管理员，开始游戏，给所有在线玩家发放疯狂钓鱼竿
 * /cww sell   把背包里可出售的物品换成金币
 * /cww gold   查看自己的金币
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(literal("cww")
						.then(literal("on").requires(src -> src.hasPermission(2)).executes(ctx -> {
							boolean changed = GameSwitch.set(ctx.getSource().getServer(), true);
							ctx.getSource().sendSuccess(() -> Component.literal(changed
									? "疯狂水世界已开启，正在重载钓鱼奖池" : "疯狂水世界已经开启"), true);
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("off").requires(src -> src.hasPermission(2)).executes(ctx -> {
							boolean changed = GameSwitch.set(ctx.getSource().getServer(), false);
							ctx.getSource().sendSuccess(() -> Component.literal(changed
									? "疯狂水世界已关闭，正在恢复原版钓鱼奖池；物品已保留" : "疯狂水世界已经关闭"), true);
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("status").executes(ctx -> {
							ctx.getSource().sendSuccess(() -> Component.literal("疯狂水世界当前"
									+ (GameSwitch.active(ctx.getSource().getServer()) ? "开启" : "关闭")), false);
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("start")
								.requires(src -> src.hasPermission(2))
								.executes(ctx -> {
									if (!active(ctx.getSource())) return 0;
									if (Economy.data(ctx.getSource().getServer()).finished()) {
										ctx.getSource().sendFailure(Component.literal("本局已结束，用 /cww restart 开始新局"));
										return 0;
									}
									RoundGame.startOnline(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.literal("游戏进行中，已给没有钓鱼竿的玩家补发"), true);
									return Command.SINGLE_SUCCESS;
								}))
						.then(literal("restart")
								.requires(src -> src.hasPermission(2))
								.executes(ctx -> {
									if (!active(ctx.getSource())) return 0;
									RoundGame.restart(ctx.getSource().getServer());
									return Command.SINGLE_SUCCESS;
								}))
						.then(literal("sell").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							long earned = Economy.sellAll(ctx.getSource().getServer(), player);
							long total = Economy.balance(ctx.getSource().getServer(), player.getUUID());
							player.sendSystemMessage(Component.literal(earned > 0
									? "§a出售获得 " + earned + " 金币，当前金币 " + total
									: "§7背包里没有可出售的物品"));
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("gold").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							long total = Economy.balance(ctx.getSource().getServer(), player.getUUID());
							player.sendSystemMessage(Component.literal("§e当前金币：" + total));
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("rank").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							RoundGame.sendRanking(ctx.getSource().getPlayerOrException());
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("results").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!Economy.data(player.getServer()).finished()) return 0;
							ResultsOpener.open(player, RoundGame.summary(player.getServer()));
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("shop").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							ShopOpener.open(player, player.blockPosition());
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("trade").then(argument("player", EntityArgument.player()).executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							TradeManager.request(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
							return Command.SINGLE_SUCCESS;
						})))
						.then(literal("accept").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							TradeManager.accept(ctx.getSource().getPlayerOrException());
							return Command.SINGLE_SUCCESS;
						}))
						.then(literal("deny").executes(ctx -> {
							if (!active(ctx.getSource())) return 0;
							TradeManager.deny(ctx.getSource().getPlayerOrException());
							return Command.SINGLE_SUCCESS;
						}))
				)
		);
	}

	private static boolean active(CommandSourceStack source) {
		if (GameSwitch.active(source.getServer())) return true;
		source.sendFailure(Component.literal("疯狂水世界已关闭，用 /cww on 开启"));
		return false;
	}
}
