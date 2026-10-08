package com.fox.traits;

import net.minecraft.nbt.NBTTagCompound;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.tools.modifiers.ToolModifier;

/**
 * 自动攻击——工具强化。按住左键时，按照工具攻击速度自动攻击准星目标。
 */
public class TraitAutoAttack extends ToolModifier {

    public TraitAutoAttack(int maxLevel, int freeModifiers) {
        super("autoattack", 0x8B4513);
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public void applyEffect(NBTTagCompound rootCompound, NBTTagCompound modifierTag) {
        // 自动攻击效果通过客户端 tick 驱动，无需修改工具 NBT。
    }
}
