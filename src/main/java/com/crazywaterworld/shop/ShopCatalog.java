package com.crazywaterworld.shop;

import com.crazywaterworld.item.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * 商店商品目录。价格只在服务端定义，客户端只发送商品序号。
 * 分类：推荐 / 工具 / 鉴定 / 出售（出售分类不展示商品，只显示出售区）。
 */
public final class ShopCatalog {
	public enum Tab {
		RECOMMEND("推荐", Items.GOLD_INGOT),
		TOOLS("工具", Items.FISHING_ROD),
		APPRAISE("鉴定", Items.SPYGLASS),
		SELL("出售", Items.CHEST);

		public final String label;
		public final Item icon;

		Tab(String label, Item icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	/** 一件商品：名称、说明、价格、图标、所属分类 */
	public record Product(String name, String desc, long price, Item icon, Tab tab,
			ResourceKey<Enchantment> enchantment, int level) {
		public Product(String name, String desc, long price, Item icon, Tab tab) {
			this(name, desc, price, icon, tab, null, 0);
		}
	}

	/** 每位玩家每局最多购买的打捞网数量，防止堆网滚雪球 */
	public static final int NET_LIMIT = 2;

	/** 全部商品。顺序即全局序号，购买按钮用的就是这个序号，不能随意调整。 */
	public static final List<Product> PRODUCTS = List.of(
			new Product("初级鉴定", "显示河蚌档次，判断是否值得开蚌", 200, ModItems.BASIC_APPRAISAL, Tab.APPRAISE),
			new Product("中级鉴定", "按当前市价估值约 ±30%，适合交易定价", 500, ModItems.ADVANCED_APPRAISAL, Tab.APPRAISE),
			new Product("高级鉴定", "按当前市价估值约 ±10%，适合交易定价", 1200, ModItems.EXPERT_APPRAISAL, Tab.APPRAISE),
			new Product("原版钓鱼竿", "普通钓鱼竿，任何水域都可以钓疯狂水世界战利品", 150, Items.FISHING_ROD, Tab.TOOLS),
			new Product("打捞网", "靠水放置，约30秒一件，仅放置者可收取；每局限购 " + NET_LIMIT + " 张", 10_000, ModItems.SALVAGE_NET_ITEM, Tab.TOOLS),
			new Product("饵钓 I", "主手或副手鱼竿直接附魔", 1_500, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LURE, 1),
			new Product("饵钓 II", "主手或副手鱼竿直接升级", 3_000, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LURE, 2),
			new Product("饵钓 III", "主手或副手鱼竿直接升级", 5_000, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LURE, 3),
			new Product("海之眷顾 I", "鱼竿直接附魔，提高珍稀渔获概率", 1_800, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LUCK_OF_THE_SEA, 1),
			new Product("海之眷顾 II", "鱼竿直接升级，提高珍稀渔获概率", 3_600, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LUCK_OF_THE_SEA, 2),
			new Product("海之眷顾 III", "鱼竿直接升级，提高珍稀渔获概率", 6_000, Items.FISHING_ROD, Tab.TOOLS, Enchantments.LUCK_OF_THE_SEA, 3),
			new Product("耐久 III", "主手或副手鱼竿直接附魔", 3_000, Items.FISHING_ROD, Tab.TOOLS, Enchantments.UNBREAKING, 3),
			new Product("经验修补", "主手或副手鱼竿直接附魔", 8_000, Items.FISHING_ROD, Tab.TOOLS, Enchantments.MENDING, 1)
	);

	private ShopCatalog() {
	}

	/** 推荐页：开局最常用的几件，按入手顺序排列 */
	private static final List<Integer> RECOMMENDED = List.of(5, 0, 8, 4, 6, 3);

	/** 按分类取商品：推荐显示精选，出售不显示商品 */
	public static List<Product> ofTab(Tab tab) {
		if (tab == Tab.RECOMMEND) {
			return RECOMMENDED.stream().map(PRODUCTS::get).toList();
		}
		if (tab == Tab.SELL) {
			return List.of();
		}
		return PRODUCTS.stream().filter(p -> p.tab() == tab).toList();
	}

	/** 按全局序号取商品，越界返回 null */
	public static Product get(int index) {
		return index >= 0 && index < PRODUCTS.size() ? PRODUCTS.get(index) : null;
	}
}
