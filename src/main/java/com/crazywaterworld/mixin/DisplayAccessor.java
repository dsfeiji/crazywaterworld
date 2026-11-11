package com.crazywaterworld.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void crazywaterworld$setTransformation(Transformation transform);

	@Invoker("setTransformationInterpolationDuration")
	void crazywaterworld$setTransformationInterpolationDuration(int ticks);
}
