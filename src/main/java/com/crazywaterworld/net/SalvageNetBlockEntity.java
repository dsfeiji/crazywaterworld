package com.crazywaterworld.net;

import com.crazywaterworld.clam.ClamData;
import com.crazywaterworld.economy.Economy;
import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.loot.ModLoot;
import com.crazywaterworld.round.GameSwitch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class SalvageNetBlockEntity extends BlockEntity {
	private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private int remaining;
	/** Who placed the net; null for nets placed before ownership existed (anyone may use those). */
	private UUID owner;

	public SalvageNetBlockEntity(BlockPos pos, BlockState state) {
		super(ModItems.SALVAGE_NET_ENTITY, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SalvageNetBlockEntity net) {
		if (!GameSwitch.active(level.getServer()) || Economy.data(level.getServer()).finished()
				|| !SalvageNetBlock.nextToWater(level, pos)) return;
		if (net.remaining <= 0) {
			net.remaining = 400 + level.random.nextInt(401);
			net.setChanged();
		}
		if (--net.remaining > 0) {
			if (net.remaining % 20 == 0) net.setChanged();
			return;
		}
		int freeSlot = -1;
		for (int i = 0; i < net.items.size(); i++) {
			if (net.items.get(i).isEmpty()) { freeSlot = i; break; }
		}
		if (freeSlot < 0) return;
		ItemStack catchItem = ModLoot.rollNetCatch((ServerLevel) level);
		if (catchItem.is(ModItems.CLAM)) ClamData.seal((ServerLevel) level, catchItem);
		net.items.set(freeSlot, catchItem);
		net.setChanged();
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	// Clients only need the owner, so their mining prediction matches the server's ownership check.
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		if (owner != null) tag.putUUID("Owner", owner);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public boolean usableBy(Player player) {
		return owner == null || owner.equals(player.getUUID()) || player.getAbilities().instabuild;
	}

	public int collect(ServerPlayer player) {
		int count = 0;
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) continue;
			items.set(i, ItemStack.EMPTY);
			count += stack.getCount();
			player.getInventory().placeItemBackInInventory(stack);
		}
		if (count > 0) setChanged();
		return count;
	}

	public SimpleContainer inventory() {
		return new SimpleContainer(items.toArray(new ItemStack[0]));
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putInt("Remaining", remaining);
		if (owner != null) tag.putUUID("Owner", owner);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		ContainerHelper.loadAllItems(tag, items, registries);
		remaining = tag.getInt("Remaining");
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}
}
