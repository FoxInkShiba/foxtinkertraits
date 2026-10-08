package com.fox.mixin;

import com.fox.config.FoxConfig;
import com.fox.handler.BlossomHandler;
import com.fox.health.HealthCapHelper;
import com.fox.health.LifeContractHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    @ModifyVariable(
            method = "attackEntityFrom",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private float fox$reduceRawDamage(float amount) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (!(self instanceof EntityPlayer)) {
            return amount;
        }
        EntityPlayer player = (EntityPlayer) self;
        if (FoxConfig.blossomDamageTiming == 1) {
            amount = BlossomHandler.reduceDamage(player, amount);
        }
        if (FoxConfig.lifeContractDamageTiming == 1) {
            amount = LifeContractHandler.reduceDamage(player, amount);
        }
        return amount;
    }

    @ModifyVariable(
            method = "setHealth",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private float fox$captureRawHealthTarget(float health) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self instanceof EntityPlayer) {
            HealthCapHelper.beginRawHealthWrite(self, health);
        }
        return health;
    }

    @Inject(
            method = "setHealth",
            at = @At("RETURN")
    )
    private void fox$clearRawHealthTarget(float health, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self instanceof EntityPlayer) {
            HealthCapHelper.endRawHealthWrite(self);
        }
    }
}
