package com.fox.asm;

import com.fox.handler.WeaponMasterHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * 诸武精通的字节码注入钩子。
 *
 * 本类是 {@link WeaponMasterTransformer} 改写匠魂字节码时调用的唯一入口，
 * 目的有二：
 * <ul>
 *   <li>把被改写方法里的运行时对象，转交给不依赖匠魂类的稳定逻辑
 *       （{@link WeaponMasterHandler}）；</li>
 *   <li>将注入点隔离在此，避免字节码直接调用可能随版本变化的匠魂方法。</li>
 * </ul>
 *
 * 之所以单独一层，是为了让字节码里出现的调用描述符保持极简，降低与
 * 具体匠魂版本耦合的风险。
 */
public final class WeaponMasterHook {

    private WeaponMasterHook() {
    }

    /**
     * 在匠魂投射物命中处理开始、移除射手双手攻击属性之前调用。
     *
     * @param shooter 射手的实体（可能为 null 或非玩家）
     */
    public static void capture(Entity shooter) {
        WeaponMasterHandler.capture(shooter);
    }

    /**
     * 在匠魂远程最终伤害写入实体之前调用，叠加诸武精通加成。
     *
     * @param damage           匠魂算出的原始远程伤害
     * @param projectileEntity 投射物实体
     * @param shooter          射手
     * @return 修正后的伤害
     */
    public static float modifyDamage(float damage, Entity projectileEntity, EntityLivingBase shooter) {
        return WeaponMasterHandler.modifyDamage(damage, projectileEntity, shooter);
    }
}
