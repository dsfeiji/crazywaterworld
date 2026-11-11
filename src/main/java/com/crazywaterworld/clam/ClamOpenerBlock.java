package com.crazywaterworld.clam;

import com.crazywaterworld.CrazyWaterWorld;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.RoundGame;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 开蚌台。手持河蚌右键即可付费开启：扣金币、消耗一颗河蚌、按权重抽取物品。
 * 所有结算都在服务端完成。
 */
public class ClamOpenerBlock extends Block {
	/** 每次开启的费用 */
	public static final long OPEN_COST = 500L;
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	// Bounds of the model as exported (facing north); other facings rotate them about the block centre.
	private static final Map<Direction, VoxelShape> OUTLINE = shapes(22.3);
	private static final Map<Direction, VoxelShape> COLLISION = shapes(9);

	public ClamOpenerBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	private static Map<Direction, VoxelShape> shapes(double height) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		shapes.put(Direction.NORTH, box(-4.3, 0, -0.2, 20.4, height, 16.4));
		shapes.put(Direction.SOUTH, box(-4.4, 0, -0.4, 20.3, height, 16.2));
		shapes.put(Direction.EAST, box(-0.4, 0, -4.3, 16.2, height, 20.4));
		shapes.put(Direction.WEST, box(-0.2, 0, -4.4, 16.4, height, 20.3));
		return shapes;
	}

	/** The blockstate's model Y rotation in degrees (north = 0, east = 90 ...). */
	public static float yaw(BlockState state) {
		return state.hasProperty(FACING) ? state.getValue(FACING).get2DDataValue() * 90F - 180F : 0F;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLLISION.get(state.getValue(FACING));
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide && !GameSwitch.active(level.getServer())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!stack.is(ModItems.CLAM)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return ItemInteractionResult.FAIL;
		}
		if (Economy.data(serverPlayer.getServer()).finished()) {
			serverPlayer.sendSystemMessage(Component.literal("§e本局已结束，无法继续开蚌"));
			return ItemInteractionResult.FAIL;
		}

		if (Economy.balance(serverPlayer.getServer(), serverPlayer.getUUID()) < OPEN_COST) {
			serverPlayer.sendSystemMessage(Component.literal("§c金币不足，开启需要 " + OPEN_COST + " 金币"));
			return ItemInteractionResult.FAIL;
		}
		ItemStack single = stack.getCount() == 1 ? stack : stack.copyWithCount(1);
		int reward = ClamData.seal((net.minecraft.server.level.ServerLevel) level, single);
		if (reward < 0 || ClamLootTable.reward(reward) == null) {
			serverPlayer.sendSystemMessage(Component.literal("§c河蚌编号已失效，无法重复开启"));
			return ItemInteractionResult.FAIL;
		}
		if (!Economy.spend(serverPlayer, OPEN_COST)) return ItemInteractionResult.FAIL;
		ClamData.consume((net.minecraft.server.level.ServerLevel) level, single);
		stack.shrink(1);
		ItemStack result = new ItemStack(ClamLootTable.reward(reward));
		serverPlayer.getInventory().placeItemBackInInventory(result);
		RoundGame.openedClam(serverPlayer);
		ClamOpeningAnimation.play((net.minecraft.server.level.ServerLevel) level, pos, yaw(state));
		((net.minecraft.server.level.ServerLevel) level).sendParticles(ParticleTypes.BUBBLE,
				pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
				24, 0.3, 0.2, 0.3, 0.06);
		level.playSound(null, pos, SoundEvents.PLAYER_SPLASH, SoundSource.BLOCKS, 0.8F, 1.2F);

		serverPlayer.sendSystemMessage(Component.literal("§b开启河蚌，获得：")
				.append(result.getHoverName()));
		if (reward >= ClamLootTable.ANNOUNCE_FROM) {
			// Rare pulls are the big moments of a round, so everyone hears about them.
			serverPlayer.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6[疯狂水世界] "
					+ serverPlayer.getName().getString() + " 开出了 ").append(result.getHoverName())
					.append(Component.literal("§6！")), false);
		}
		CrazyWaterWorld.LOGGER.info("[疯狂水世界] {} 开启河蚌，获得 {}", serverPlayer.getName().getString(), result.getItem());
		return ItemInteractionResult.CONSUME;
	}
}
