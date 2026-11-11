package com.crazywaterworld.net;

import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SalvageNetBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = box(0, 0, 0, 16, 9, 16);

	public SalvageNetBlock(Properties properties) {
		super(properties);
	}

	public static boolean nextToWater(Level level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(pos.relative(direction)).is(Fluids.WATER)) return true;
		}
		return false;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return nextToWater(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide && placer instanceof Player player
				&& level.getBlockEntity(pos) instanceof SalvageNetBlockEntity net) net.setOwner(player.getUUID());
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		// Other players can neither empty nor dig up someone else's net.
		if (level.getBlockEntity(pos) instanceof SalvageNetBlockEntity net && !net.usableBy(player)) return 0;
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SalvageNetBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide || type != ModItems.SALVAGE_NET_ENTITY ? null
				: (world, at, current, entity) -> SalvageNetBlockEntity.tick(world, at, current, (SalvageNetBlockEntity) entity);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide && !GameSwitch.active(level.getServer())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide) return ItemInteractionResult.SUCCESS;
		collect(level, pos, player);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!level.isClientSide && !GameSwitch.active(level.getServer())) return InteractionResult.PASS;
		if (!level.isClientSide) collect(level, pos, player);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private void collect(Level level, BlockPos pos, Player player) {
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SalvageNetBlockEntity net) {
			if (!net.usableBy(player)) {
				serverPlayer.displayClientMessage(Component.literal("§c这是别人的打捞网"), true);
				return;
			}
			if (!nextToWater(level, pos)) {
				serverPlayer.displayClientMessage(Component.literal("§e打捞网离开了水，已暂停打捞"), true);
			}
			int count = net.collect(serverPlayer);
			if (count == 0) serverPlayer.displayClientMessage(Component.literal("§e打捞网里还没有渔获"), true);
			else serverPlayer.sendSystemMessage(Component.literal("§b[疯狂水世界] 从打捞网收取了 " + count + " 件物品"));
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
		if (!level.isClientSide && !state.is(newState.getBlock())
				&& level.getBlockEntity(pos) instanceof SalvageNetBlockEntity net) {
			Containers.dropContents(level, pos, net.inventory());
		}
		super.onRemove(state, level, pos, newState, moving);
	}
}
