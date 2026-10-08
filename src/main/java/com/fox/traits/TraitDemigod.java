package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;

public class TraitDemigod extends ArmorModifierTrait {

    public TraitDemigod(int maxLevel, int freeModifiers) {
        // 等级由 LevelAspect 控制；每级消耗的强化槽由 FreeModifierAspect 控制。
        super("demigod", 0xffd700, maxLevel, 0); // 金色
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }
}
