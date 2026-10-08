package com.fox.traits;

import c4.conarm.lib.modifiers.ArmorModifierTrait;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;

public class TraitLifeContract extends ArmorModifierTrait {

    public TraitLifeContract(int maxLevel, int freeModifiers) {
        super("lifecontract", 0x8B0000, maxLevel, 0);
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }
}
