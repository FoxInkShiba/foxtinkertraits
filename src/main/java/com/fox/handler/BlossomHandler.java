package com.fox.handler;

import com.fox.config.FoxConfig;
import com.fox.health.LifeContractHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * 落花之情：盔甲耐久上限带来定值减伤，多件叠加。
 * 只要盔甲带有该词条且尚未损坏即可生效，不会额外消耗耐久。
 */
public final class BlossomHandler {

    private BlossomHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (FoxConfig.blossomDamageTiming != 2
                && FoxConfig.lifeContractDamageTiming != 2) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        float amount = event.getAmount();
        if (FoxConfig.blossomDamageTiming == 2) {
            amount = reduceDamage(player, amount);
        }
        if (FoxConfig.lifeContractDamageTiming == 2) {
            amount = LifeContractHandler.reduceDamage(player, amount);
        }
        event.setAmount(amount);
    }

    /**
     * Applies only the Falling Blossom flat reduction to an incoming damage
     * amount. The caller chooses the damage pipeline stage.
     */
    public static float reduceDamage(EntityPlayer player, float amount) {
        if (amount <= 0.0F) {
            return amount;
        }

        double perDamage = FoxConfig.blossomDurabilityPerDamage;
        if (perDamage <= 0.0) {
            return amount;
        }

        double totalDurability = 0.0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack.isEmpty() || ToolHelper.isBroken(stack)) {
                continue;
            }
            int level = getLevel(stack);
            if (level <= 0) {
                continue;
            }
            double maxDurability = stack.getMaxDamage();
            if (maxDurability <= 0.0 || maxDurability - stack.getItemDamage() <= 0) {
                continue;
            }
            // 有效耐久池按每件盔甲的“耐久上限 * 等级”计入。
            totalDurability += maxDurability * level;
        }
        if (totalDurability <= 0.0) {
            return amount;
        }

        // 定值减伤：每 perDamage 点耐久上限可抵扣 1 点伤害。
        double maxReduction = totalDurability / perDamage;
        double reduction = Math.min((double) amount, maxReduction);
        return (float) (amount - reduction);
    }

    /**
     * Reduces a raw setHealth target before EntityLivingBase clamps it to
     * [0, maxHealth]. This preserves overkill information such as -10.
     */
    public static float reduceHealthTarget(EntityPlayer player, float current, float target) {
        if (target >= current) {
            return target;
        }
        float actualLoss = current - target;
        float reducedLoss = reduceDamage(player, actualLoss);
        return current - reducedLoss;
    }

    private static int getLevel(ItemStack stack) {
        NBTTagCompound tag = TinkerUtil.getModifierTag(stack, "blossom_armor");
        if (tag == null) {
            return 0;
        }
        return ModifierNBT.readTag(tag).level;
    }
}
