package com.crazywaterworld.item;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.clam.ClamOpenerBlock;
import com.crazywaterworld.clam.AppraisalItem;
import com.crazywaterworld.clam.ClamItem;
import com.crazywaterworld.net.SalvageNetBlock;
import com.crazywaterworld.net.SalvageNetBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 模组物品注册。只有 register() 被调用时才会注册，模组关闭时不会调用。
 */
public final class ModItems {
	/** 臭皮鞋（垃圾） */
	public static Item SMELLY_SHOE;
	/** 废水过滤器（垃圾） */
	public static Item WASTE_FILTER;
	/** 海水过滤器（稀有） */
	public static Item SEA_FILTER;
	/** 电鳗（稀有） */
	public static Item ELECTRIC_EEL;
	/** 河蚌（未开启，需到开蚌台付费开启） */
	public static Item CLAM;
	/** Hidden split models used only by the paid opening animation. */
	public static Item CLAM_BODY_VISUAL;
	public static Item CLAM_LID_VISUAL;
	public static Item RIVER_PEARL;
	public static Item EEL_GENERATOR;
	public static Item ANCIENT_PEARL;
	public static Item BASIC_APPRAISAL;
	public static Item ADVANCED_APPRAISAL;
	public static Item EXPERT_APPRAISAL;
	/** 开蚌台方块 */
	public static Block CLAM_OPENER;
	/** 开蚌台的物品形式 */
	public static Item CLAM_OPENER_ITEM;
	public static Block SALVAGE_NET;
	public static Item SALVAGE_NET_ITEM;
	public static BlockEntityType<SalvageNetBlockEntity> SALVAGE_NET_ENTITY;

	private ModItems() {
	}

	public static void register() {
		SMELLY_SHOE = register("smelly_shoe", new Item(new Item.Properties().stacksTo(16)));
		WASTE_FILTER = register("waste_filter", new Item(new Item.Properties().stacksTo(16)));
		SEA_FILTER = register("sea_filter", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		ELECTRIC_EEL = register("electric_eel", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
		CLAM = register("clam", new ClamItem(new Item.Properties().stacksTo(1)));
		CLAM_BODY_VISUAL = register("clam_body_visual", new Item(new Item.Properties().stacksTo(1)));
		CLAM_LID_VISUAL = register("clam_lid_visual", new Item(new Item.Properties().stacksTo(1)));
		RIVER_PEARL = register("river_pearl", new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
		EEL_GENERATOR = register("eel_generator", new Item(new Item.Properties().rarity(Rarity.RARE)));
		ANCIENT_PEARL = register("ancient_pearl", new Item(new Item.Properties().rarity(Rarity.EPIC)));
		BASIC_APPRAISAL = register("basic_appraisal", new AppraisalItem(1, new Item.Properties().stacksTo(16)));
		ADVANCED_APPRAISAL = register("advanced_appraisal", new AppraisalItem(2, new Item.Properties().stacksTo(16)));
		EXPERT_APPRAISAL = register("expert_appraisal", new AppraisalItem(3, new Item.Properties().stacksTo(16)));

		// The bench is map furniture: survival players cannot break it, creative builders still can.
		CLAM_OPENER = registerBlock("clam_opener", new ClamOpenerBlock(
				BlockBehaviour.Properties.of().strength(-1.0f, 3_600_000.0f).sound(SoundType.WOOD).noOcclusion()));
		CLAM_OPENER_ITEM = register("clam_opener", new BlockItem(CLAM_OPENER, new Item.Properties()));
		SALVAGE_NET = registerBlock("salvage_net", new SalvageNetBlock(
				BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
		SALVAGE_NET_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
				ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, "salvage_net"),
				BlockEntityType.Builder.of(SalvageNetBlockEntity::new, SALVAGE_NET).build(null));
		SALVAGE_NET_ITEM = register("salvage_net", new BlockItem(SALVAGE_NET, new Item.Properties().stacksTo(1)));

		// Own creative tab with every player-facing item (the two clam animation halves stay hidden).
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
				ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, "main"),
				FabricItemGroup.builder()
						.title(Component.translatable("itemGroup.crazywaterworld"))
						.icon(() -> new ItemStack(CLAM))
						.displayItems((parameters, entries) -> {
							entries.accept(CLAM_OPENER_ITEM);
							entries.accept(SALVAGE_NET_ITEM);
							entries.accept(BASIC_APPRAISAL);
							entries.accept(ADVANCED_APPRAISAL);
							entries.accept(EXPERT_APPRAISAL);
							entries.accept(CLAM);
							entries.accept(SMELLY_SHOE);
							entries.accept(WASTE_FILTER);
							entries.accept(ELECTRIC_EEL);
							entries.accept(SEA_FILTER);
							entries.accept(RIVER_PEARL);
							entries.accept(EEL_GENERATOR);
							entries.accept(ANCIENT_PEARL);
						})
						.build());

		CrazyWaterWorld.LOGGER.info("[疯狂水世界] 物品注册完成");
	}

	private static Block registerBlock(String path, Block block) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, path);
		return Registry.register(BuiltInRegistries.BLOCK, id, block);
	}

	private static Item register(String path, Item item) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CrazyWaterWorld.MOD_ID, path);
		return Registry.register(BuiltInRegistries.ITEM, id, item);
	}
}
