package com.crazywaterworld.clam;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class ClamItem extends Item {
	public ClamItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
		int level = tag.getInt("cww_hint_level");
		if (level == 1) {
			lines.add(Component.literal("§b鉴定结果：" + tag.getString("cww_hint_label")));
		} else if (level > 1) {
			lines.add(Component.literal("§b鉴定时市价区间：" + tag.getLong("cww_hint_low")
					+ " ~ " + tag.getLong("cww_hint_high") + " 金币"));
		} else {
			lines.add(Component.literal("§7未鉴定：副手拿河蚌，主手持鉴定道具右键"));
		}
		lines.add(Component.literal("§7手持河蚌右键开蚌台，需 " + ClamOpenerBlock.OPEN_COST + " 金币"));
		lines.add(Component.literal("§8商店不收购河蚌，可与其他玩家交易"));
	}
}
