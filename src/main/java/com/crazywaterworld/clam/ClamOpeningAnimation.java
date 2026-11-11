package com.crazywaterworld.clam;

import com.crazywaterworld.item.ModItems;
import com.crazywaterworld.round.GameSwitch;
import com.crazywaterworld.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Plays the supplied Blockbench open_burst hinge poses only after the server completes a paid opening. */
public final class ClamOpeningAnimation {
	private static final String TAG = "crazywaterworld_clam_opening";
	private static final List<Playing> PLAYING = new ArrayList<>();
	private static final Set<UUID> LIVE_DISPLAYS = new HashSet<>();

	// The 1.5 s hinge X rotation keyframes from treasure_clam.open_burst.
	private static final float[] TIMES = {0F, .08F, .17F, .25F, .35F, .45F, .65F, .85F, 1.5F};
	private static final float[] ANGLES = {0F, 4F, 0F, 6F, 72F, 85F, 78F, 82F, 82F};

	private ClamOpeningAnimation() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = PLAYING.size() - 1; i >= 0; i--) {
				Playing active = PLAYING.get(i);
				if (active.level.getServer() != server) continue;
				if (active.tick()) {
					active.clear();
					PLAYING.remove(i);
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Display.ItemDisplay && entity.getTags().contains(TAG)
					&& !LIVE_DISPLAYS.contains(entity.getUUID())) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Playing active : List.copyOf(PLAYING)) {
				if (active.level.getServer() == server) active.clear();
			}
			PLAYING.removeIf(active -> active.level.getServer() == server);
		});
	}

	public static void play(ServerLevel level, BlockPos pos, float yaw) {
		if (!GameSwitch.active(level.getServer())) return;
		Display.ItemDisplay body = EntityType.ITEM_DISPLAY.create(level);
		Display.ItemDisplay lid = EntityType.ITEM_DISPLAY.create(level);
		if (body == null || lid == null) return;
		body.getSlot(0).set(new ItemStack(ModItems.CLAM_BODY_VISUAL));
		lid.getSlot(0).set(new ItemStack(ModItems.CLAM_LID_VISUAL));
		body.addTag(TAG);
		lid.addTag(TAG);
		body.setPos(pos.getX() + .5, pos.getY() + 1.025, pos.getZ() + .5);
		// The lid model was exported relative to Blockbench's hinge (0, 2, 7); turn that offset with the bench.
		double radians = Math.toRadians(yaw);
		lid.setPos(pos.getX() + .5 - Math.sin(radians) * 7.0 / 16, pos.getY() + 1.025 + 2.0 / 16,
				pos.getZ() + .5 + Math.cos(radians) * 7.0 / 16);
		body.setYRot(yaw);
		lid.setYRot(yaw);
		((DisplayAccessor) lid).crazywaterworld$setTransformationInterpolationDuration(2);
		LIVE_DISPLAYS.add(body.getUUID());
		LIVE_DISPLAYS.add(lid.getUUID());
		if (!level.addFreshEntity(body) || !level.addFreshEntity(lid)) {
			body.discard();
			lid.discard();
			LIVE_DISPLAYS.remove(body.getUUID());
			LIVE_DISPLAYS.remove(lid.getUUID());
			return;
		}
		PLAYING.add(new Playing(level, pos, body, lid));
	}

	public static void stop(net.minecraft.server.MinecraftServer server) {
		for (Playing active : List.copyOf(PLAYING)) {
			if (active.level.getServer() == server) active.clear();
		}
		PLAYING.removeIf(active -> active.level.getServer() == server);
	}

	private static float angle(float seconds) {
		for (int i = 1; i < TIMES.length; i++) {
			if (seconds <= TIMES[i]) {
				float fraction = (seconds - TIMES[i - 1]) / (TIMES[i] - TIMES[i - 1]);
				return ANGLES[i - 1] + fraction * (ANGLES[i] - ANGLES[i - 1]);
			}
		}
		return ANGLES[ANGLES.length - 1];
	}

	private static final class Playing {
		private final ServerLevel level;
		private final BlockPos pos;
		private final Display.ItemDisplay body;
		private final Display.ItemDisplay lid;
		private int ticks;

		private Playing(ServerLevel level, BlockPos pos, Display.ItemDisplay body, Display.ItemDisplay lid) {
			this.level = level;
			this.pos = pos;
			this.body = body;
			this.lid = lid;
		}

		private boolean tick() {
			ticks++;
			float degrees = angle(ticks / 20F);
			((DisplayAccessor) lid).crazywaterworld$setTransformation(new Transformation(
					new Vector3f(), new Quaternionf().rotationX((float) Math.toRadians(degrees)),
					new Vector3f(1, 1, 1), new Quaternionf()));
			if (ticks == 9) {
				level.sendParticles(ParticleTypes.END_ROD, pos.getX() + .5, pos.getY() + 1.5,
						pos.getZ() + .3, 16, .3, .15, .3, .03);
				level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .8F, 1.1F);
			}
			return ticks >= 30 || body.isRemoved() || lid.isRemoved();
		}

		private void clear() {
			LIVE_DISPLAYS.remove(body.getUUID());
			LIVE_DISPLAYS.remove(lid.getUUID());
			body.discard();
			lid.discard();
		}
	}
}
