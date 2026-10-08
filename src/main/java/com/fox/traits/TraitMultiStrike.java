package com.fox.traits;

import com.fox.handler.MultiStrikeHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import slimeknights.tconstruct.library.entity.EntityProjectileBase;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.library.modifiers.ProjectileModifierTrait;

/**
 * 多重打击（Multi Strike）——投射物强化。
 *
 * 命中生物后会在目标身上滞留一段时间，期间反复造成伤害，然后继续飞向下一个目标。
 * 行为由 {@link MultiStrikeHandler} 实现，并由 {@code WeaponMasterTransformer} 改写匠魂
 * {@code EntityProjectileBase.onEntityHit}，在滞留期间跳过投射物的移除。
 *
 * 本词条附加在投射物（箭矢/手里剑等）上，因此弓、弩、回旋镖发射的匠魂投射物均可生效。
 */
public class TraitMultiStrike extends ProjectileModifierTrait {

    public TraitMultiStrike(int maxLevel, int freeModifiers) {
        // 必须把 maxLevel 传给父类：ModifierTrait 的 maxLevel 是 final 字段，
        // 且 canApplyCustom 会在 maxLevel == 0 时禁止继续强化（导致只能到 1 级）。
        super("multistrike", 0x00BFFF, maxLevel, 0);

        aspects.clear();
        addAspects(
                new ModifierAspect.LevelAspect(this, maxLevel),
                new ModifierAspect.FreeModifierAspect(freeModifiers),
                new ModifierAspect.DataAspect(this)
        );
    }

    @Override
    public void afterHit(EntityProjectileBase projectile, World world, ItemStack ammoStack,
                         EntityLivingBase attacker, Entity target, double impactSpeed) {
        float lastDamage = MultiStrikeHandler.consumeRangedDamage();
        MultiStrikeHandler.onHit(projectile, ammoStack, attacker, target, lastDamage);
    }

    @Override
    public void onProjectileUpdate(EntityProjectileBase projectile, World world, ItemStack toolStack) {
        MultiStrikeHandler.onUpdate(projectile, world, toolStack);
    }
}
