package com.fox.handler;

import com.fox.config.FoxConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import slimeknights.tconstruct.library.capability.projectile.TinkerProjectileHandler;
import slimeknights.tconstruct.library.entity.EntityProjectileBase;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.utils.TinkerUtil;

/**
 * 诸武精通（Weapon Mastery）——远程工具强化的核心逻辑。
 *
 * 匠魂投射物命中时会先移除射手主副手的攻击属性，再走 {@code ToolHelper.attackEntity}，
 * 因此命中瞬间射手的 ATTACK_DAMAGE 已不含近战武器攻击力。这里在 unequip 之前捕获
 * 射手的完整近战攻击力（含主副手武器、力量、饰品等全部来源），供最终伤害阶段叠加。
 *
 * 本类不依赖 Mixin / ASM，只提供纯运行时逻辑，由 {@code com.fox.asm.WeaponMasterHook}
 * 在被改写的匠魂字节码中调用。
 */
public final class WeaponMasterHandler {

    private static final String MODIFIER_ID = "weaponmaster";

    // 命中处理开始前捕获的射手完整近战攻击力。
    private static final ThreadLocal<Float> CAPTURED_ATTACK = new ThreadLocal<>();

    private WeaponMasterHandler() {
    }

    /**
     * 在匠魂 {@code EntityProjectileBase.onHitEntity} 移除双手属性之前调用，
     * 记录射手的完整近战攻击力；传入非玩家时清空。
     */
    public static void capture(Entity shooter) {
        if (!(shooter instanceof EntityPlayer)) {
            CAPTURED_ATTACK.remove();
            return;
        }
        double attack = ((EntityPlayer) shooter).getEntityAttribute(
                SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        CAPTURED_ATTACK.set((float) attack);
    }

    /**
     * 取回并清空捕获的近战攻击力；无捕获时返回 0。
     */
    public static float consumeAttack() {
        Float value = CAPTURED_ATTACK.get();
        CAPTURED_ATTACK.remove();
        return value == null ? 0.0F : value;
    }

    /**
     * 诸武精通的最终伤害修正：在原远程伤害上叠加
     * 「词条等级 × perLevel × 玩家近战攻击力」。
     *
     * @param damage           匠魂算出的原始远程伤害
     * @param projectileEntity 投射物实体（用于反查发射器栈）
     * @param shooter          射手
     * @return 修正后的伤害
     */
    public static float modifyDamage(float damage, Entity projectileEntity, EntityLivingBase shooter) {
        float attack = consumeAttack();
        if (!(shooter instanceof EntityPlayer)) {
            return damage;
        }
        int level = getLauncherLevel(projectileEntity);
        if (level <= 0) {
            return damage;
        }
        return damage + bonusDamage(level, attack);
    }

    /**
     * 从投射物实体反查发射器（弓/弩）栈上的诸武精通等级；无法判定返回 0。
     */
    public static int getLauncherLevel(Entity projectileEntity) {
        if (!(projectileEntity instanceof EntityProjectileBase)) {
            return 0;
        }
        TinkerProjectileHandler handler = ((EntityProjectileBase) projectileEntity).tinkerProjectile;
        if (handler == null) {
            return 0;
        }
        return getLevel(handler.getLaunchingStack());
    }

    /**
     * 读取发射器（弓/弩）栈上的诸武精通等级；未带词条返回 0。
     */
    public static int getLevel(ItemStack launcher) {
        if (launcher == null || launcher.isEmpty()) {
            return 0;
        }
        NBTTagCompound tag = TinkerUtil.getModifierTag(launcher, MODIFIER_ID);
        if (tag == null) {
            return 0;
        }
        return ModifierNBT.readTag(tag).level;
    }

    /**
     * 计算诸武精通叠加到远程伤害上的额外值：等级 × perLevel × 玩家近战攻击力。
     */
    public static float bonusDamage(int level, float attack) {
        if (level <= 0 || attack <= 0.0F) {
            return 0.0F;
        }
        float ratio = Math.max(0.0F, Math.min(1.0F, FoxConfig.weaponMasterPerLevel));
        return attack * ratio * level;
    }
}
