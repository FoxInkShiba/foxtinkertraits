package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import net.minecraft.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;

public class TraitTurtle extends ArmorModifierTrait {

    public TraitTurtle(int maxLevel, int freeModifiers) {
        // identifier 仍然是 turtle，注册后是 turtle_armor
        super("turtle", 0x2e8b57, maxLevel, 0); // 海绿色
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public boolean canApplyCustom(ItemStack stack) {
        if (!super.canApplyCustom(stack)) {
            return false;
        }
        return TinkerUtil.hasTrait(TagUtil.getTagSafe(stack), "demigod_armor")
                || TinkerUtil.hasModifier(TagUtil.getTagSafe(stack), "demigod_armor");
    }
}
