package com.crazywaterworld.clam;

import com.crazywaterworld.economy.MarketData;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** One-use appraisal tool: the actual reward remains in server-side ClamData. */
public final class AppraisalItem extends Item {
	private final int level;

	public AppraisalItem(int level, Properties properties) {
		super(properties);
		this.level = level;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack tool = player.getItemInHand(hand);
		if (!world.isClientSide && !GameSwitch.active(world.getServer())) return InteractionResultHolder.pass(tool);
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack clam = player.getItemInHand(otherHand);
		if (!(clam.getItem() instanceof ClamItem)) {
			if (!world.isClientSide) player.sendSystemMessage(Component.literal("§e另一只手需要拿着河蚌"));
			return InteractionResultHolder.sidedSuccess(tool, world.isClientSide);
		}
		if (!(world instanceof ServerLevel serverWorld)) {
			return InteractionResultHolder.sidedSuccess(tool, world.isClientSide);
		}
		if (Economy.data(serverWorld.getServer()).finished()) {
			player.sendSystemMessage(Component.literal("§e本局已结束，无法继续鉴定"));
			return InteractionResultHolder.success(tool);
		}
		if (clam.getCount() > 1) {
			if (ClamData.token(clam) != null) {
				player.sendSystemMessage(Component.literal("§c已标记的河蚌不能堆叠，请分开后再鉴定"));
				return InteractionResultHolder.success(tool);
			}
			ItemStack single = clam.split(1);
			player.setItemInHand(otherHand, single);
			if (!clam.isEmpty() && !player.getInventory().add(clam) && !clam.isEmpty()) player.drop(clam, false);
			clam = single;
		}
		int reward = ClamData.seal(serverWorld, clam);
		if (reward < 0) {
			player.sendSystemMessage(Component.literal("§c这颗河蚌的编号已失效，无法鉴定"));
			return InteractionResultHolder.success(tool);
		}
		int previous = clam.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("cww_hint_level");
		if (previous >= level) {
			player.sendSystemMessage(Component.literal("§e这颗河蚌已经接受过同级或更高级的鉴定"));
			return InteractionResultHolder.success(tool);
		}
		int kind = reward;
		if (level == 1) {
			String label = kind < 2 ? "废品" : kind == 2 ? "普通" : kind < 5 ? "珍稀" : "顶级";
			CustomData.update(DataComponents.CUSTOM_DATA, clam, tag -> {
				tag.putInt("cww_hint_level", level);
				tag.putString("cww_hint_label", label);
			});
			player.sendSystemMessage(Component.literal("§b鉴定结果：" + label));
		} else {
			long value = MarketData.price(serverWorld.getServer(), ClamLootTable.reward(reward));
			int margin = level == 2 ? 30 : 10;
			long lower = value * (100 - margin) / 100;
			long upper = (value * (100 + margin) + 99) / 100;
			CustomData.update(DataComponents.CUSTOM_DATA, clam, tag -> {
				tag.putInt("cww_hint_level", level);
				tag.putLong("cww_hint_low", lower);
				tag.putLong("cww_hint_high", upper);
			});
			player.sendSystemMessage(Component.literal("§b鉴定结果：" + lower + " ~ " + upper + " 金币"));
		}
		if (!player.getAbilities().instabuild) tool.shrink(1);
		return InteractionResultHolder.success(tool);
	}
}
