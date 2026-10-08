package com.fox.traits;

import net.minecraft.nbt.NBTTagCompound;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.tools.modifiers.ToolModifier;

/**
 * 反击——工具强化。受到攻击时，手持该工具有几率自动反击攻击者。
 */
public class TraitCounter extends ToolModifier {

    public TraitCounter(int maxLevel, int freeModifiers) {
        super("counter", 0xFF4500); // 橙红
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public void applyEffect(NBTTagCompound rootCompound, NBTTagCompound modifierTag) {
        // 反击效果通过事件驱动实现，无需修改 NBT
    }
}
