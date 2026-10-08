package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;

/**
 * 超级防毒面具——头盔专属，100% 取消负面效果施加。
 */
public class TraitSuperGasMask extends ArmorModifierTrait {

    public TraitSuperGasMask(int freeModifiers) {
        super("supergasmask", 0x2F4F4F, 1, 0); // 深青
        aspects.clear();
        addAspects(
                new ModifierAspect.SingleAspect(this),
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
