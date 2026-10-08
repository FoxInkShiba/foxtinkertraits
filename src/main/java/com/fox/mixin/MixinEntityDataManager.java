package com.fox.mixin;

import com.fox.config.FoxConfig;
import com.fox.handler.BlossomHandler;
import com.fox.health.HealthCapHelper;
import com.fox.health.LifeContractHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDataManager.class)
public abstract class MixinEntityDataManager {

    @Shadow private Entity entity;

    @Inject(
            method = "set",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void fox$capHealthWrite(DataParameter<?> key, Object value, CallbackInfo ci) {
        if (key != EntityLivingBaseAccessor.getHealthKey()) {
            return;
        }

        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        if (player.world == null || player.getGameProfile() == null) {
            return;
        }

        if (!(value instanceof Float)) {
            return;
        }

        if (HealthCapHelper.isProcessing()) {
            return;
        }

        float target = (Float) value;
        Float rawTarget = HealthCapHelper.consumeRawHealthTarget(player);
        if (rawTarget != null) {
            target = rawTarget;
        }
        float current = player.getHealth();
        float adjusted = target;
        if (FoxConfig.blossomDamageTiming == 3) {
            adjusted = BlossomHandler.reduceHealthTarget(player, current, adjusted);
        }
        if (FoxConfig.lifeContractDamageTiming == 3) {
            adjusted = LifeContractHandler.reduceHealthTarget(player, current, adjusted);
        }

        // Preserve Turtle's priority; fall back to Demigod when it is inactive.
        float turtleRatio = HealthCapHelper.getTurtleRatio(player);
        if (turtleRatio > 0.0F) {
            if (FoxConfig.turtleStackWithDemigod) {
                float demigodRatio = HealthCapHelper.getDemigodRatio(player);
                if (demigodRatio > 0.0F) {
                    adjusted = HealthCapHelper.applyDemigod(adjusted, player, demigodRatio);
                }
            }
            adjusted = HealthCapHelper.applyTurtle(adjusted, player, turtleRatio);
        } else {
            float demigodRatio = HealthCapHelper.getDemigodRatio(player);
            if (demigodRatio > 0.0F) {
                adjusted = HealthCapHelper.applyDemigod(adjusted, player, demigodRatio);
            }
        }

        // Keep the stored value within the same bounds as vanilla. The raw
        // target is used only for calculating modifiers.
        float finalTarget = MathHelper.clamp(adjusted, 0.0F, player.getMaxHealth());
        finalTarget = LifeContractHandler.limitHealthTarget(player, finalTarget);
        if (finalTarget == (Float) value) {
            return;
        }

        ci.cancel();
        HealthCapHelper.setProcessing(true);
        try {
            ((EntityDataManager) (Object) this).set((DataParameter) key, (Object) finalTarget);
        } finally {
            HealthCapHelper.setProcessing(false);
        }
    }
}
