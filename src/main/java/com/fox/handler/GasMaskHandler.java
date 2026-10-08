package com.fox.handler;

import com.fox.config.FoxConfig;
import com.fox.mixin.PotionAccessor;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.utils.TinkerUtil;

import java.util.Random;

/**
 * 防毒面具 / 超级防毒面具：穿戴在头上时，有概率取消负面药水效果的施加。
 */
public final class GasMaskHandler {

    private static final Random RANDOM = new Random();

    private GasMaskHandler() {
    }

    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        EntityLivingBase living = event.getEntityLiving();
        if (!(living instanceof EntityPlayer)) {
            return;
        }

        Potion potion = event.getPotionEffect().getPotion();
        // 只针对负面效果
        if (!((PotionAccessor) potion).fox$isBadEffect()) {
            return;
        }

        EntityPlayer player = (EntityPlayer) living;

        // 检查头盔
        ItemStack helmet = player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD);
        if (helmet.isEmpty()) {
            return;
        }

        // 超级防毒面具：100% 取消
        int superLevel = getLevel(helmet, "supergasmask_armor");
        if (superLevel > 0) {
            event.setResult(Event.Result.DENY);
            return;
        }

        // 普通防毒面具：等级 * perLevel 概率取消
        int level = getLevel(helmet, "gasmask_armor");
        if (level > 0) {
            float chance = level * FoxConfig.gasMaskPerLevel;
            if (chance >= 1.0f || RANDOM.nextFloat() < chance) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    private static int getLevel(ItemStack stack, String identifier) {
        NBTTagCompound tag = TinkerUtil.getModifierTag(stack, identifier);
        if (tag == null) {
            return 0;
        }
        return ModifierNBT.readTag(tag).level;
    }
}
