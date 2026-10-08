package com.fox.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.datasync.DataParameter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseAccessor {
    @Accessor("HEALTH")
    static DataParameter<Float> getHealthKey() {
        throw new AssertionError();
    }

    @Accessor("ticksSinceLastSwing")
    int fox$getTicksSinceLastSwing();

    @Accessor("ticksSinceLastSwing")
    void fox$setTicksSinceLastSwing(int ticks);
}
