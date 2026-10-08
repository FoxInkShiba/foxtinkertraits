package com.fox.traits;

import net.minecraft.nbt.NBTTagCompound;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.tools.modifiers.ToolModifier;

/**
 * 诸武精通——远程工具强化。
 * 发射的匠魂投射物命中时，每级额外获得玩家当前近战攻击力的一定比例作为远程伤害加成。
 */
public class TraitWeaponMaster extends ToolModifier {

    public TraitWeaponMaster(int maxLevel, int freeModifiers) {
        super("weaponmaster", 0x9b6b43);
        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public void applyEffect(NBTTagCompound rootCompound, NBTTagCompound modifierTag) {
        // 诸武精通效果在匠魂远程投射物命中时通过 Mixin 注入，无需修改工具 NBT。
    }
}
