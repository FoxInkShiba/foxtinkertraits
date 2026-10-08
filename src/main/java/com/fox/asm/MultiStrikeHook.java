package com.fox.asm;

import com.fox.handler.MultiStrikeHandler;
import slimeknights.tconstruct.library.entity.EntityProjectileBase;

/**
 * 多重打击的字节码注入钩子。
 *
 * 由 {@link WeaponMasterTransformer} 改写匠魂字节码时调用，负责：
 * <ul>
 *   <li>{@link #isLodging}：供 {@code EntityProjectileBase.onEntityHit} 判断是否跳过
 *       {@code setDead()}。命中以下任一情形即跳过：正处于多重打击滞留中，或当前命中
 *       属于冷却内的重复命中（不伤害、直接穿过）；</li>
 *   <li>{@link #recordRangedDamage}：记录本次远程最终伤害，供多重打击的额外段数复用。</li>
 * </ul>
 * 与 {@link WeaponMasterHook} 一样，把注入点隔离在此，避免字节码直接依赖易变的内部实现。
 */
public final class MultiStrikeHook {

    private MultiStrikeHook() {
    }

    /**
     * 该投射物本次命中是否应跳过 {@code setDead()}。
     *
     * <p>方法名沿用 {@code isLodging} 以保持字节码注入描述符稳定；语义已扩展为
     * "滞留中，或冷却内重复命中（应直接穿过）"。
     */
    public static boolean isLodging(EntityProjectileBase projectile) {
        return MultiStrikeHandler.shouldSkipRemoval(projectile);
    }

    /**
     * 记录一次远程攻击的最终伤害。
     */
    public static void recordRangedDamage(float damage) {
        MultiStrikeHandler.recordRangedDamage(damage);
    }

    /**
     * 供魔法飞弹的 {@code onUpdate} 超时逻辑改写查询：该投射物是否应因存在时间到限而销毁。
     *
     * <p>未参与多重打击时保持原版 32 tick 超时；参与后滞留中豁免、非滞留按 NBT 计时（默认 4 秒）。
     */
    public static boolean shouldTimeout(EntityProjectileBase projectile) {
        return MultiStrikeHandler.shouldTimeout(projectile);
    }
}
