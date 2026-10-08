package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;

/**
 * 防毒面具——头盔专属，每级 x% 取消负面效果施加。
 */
public class TraitGasMask extends ArmorModifierTrait {

    public TraitGasMask(int maxLevel, int freeModifiers) {
        super("gasmask", 0x4B5320, maxLevel, 0); // 军绿
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public boolean canApplyCustom(ItemStack stack) {
        if (EntityLiving.getSlotForItemStack(stack) != EntityEquipmentSlot.HEAD) {
            return false;
        }
        return super.canApplyCustom(stack);
    }
}
