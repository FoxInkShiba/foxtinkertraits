package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;

public class TraitBlossom extends ArmorModifierTrait {

    public TraitBlossom(int maxLevel, int freeModifiers) {
        super("blossom", 0xFFA1C9, maxLevel, 0); // 浅粉
        aspects.clear();
        addAspects(
            new ModifierAspect.LevelAspect(this, maxLevel),
            new ModifierAspect.FreeModifierAspect(freeModifiers),
            new ModifierAspect.DataAspect(this)
        );
    }
}
