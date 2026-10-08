package com.fox.autoattack;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * 自动攻击的攻速查询工具（仿 RLCombat 的 Helpers）。
 *
 * <p>原版 {@code ATTACK_SPEED} 属性只包含“当前主手工具 + 玩家来源（药水、饰品等）”。
 * 查询副手工具的有效攻速时，需临时把主手工具的 ATTACK_SPEED 修饰符换成副手工具的，
 * 读完再还原。这样副手也能正确吃到：
 * <ul>
 *   <li>副手工具自身的攻速修饰符（材料、强化、词条等）；</li>
 *   <li>玩家身上所有作用于 ATTACK_SPEED 的来源（急迫药水、饰品、其它 mod）；</li>
 * </ul>
 */
public final class AutoAttackHelper {

    private AutoAttackHelper() {
    }

    /**
     * 求“以指定工具为当前手”时玩家的有效攻击速度。
     *
     * <p>若指定工具就是当前主手，直接读取属性；否则临时替换主手工具的
     * ATTACK_SPEED 修饰符为指定工具的，读完还原。
     */
    public static double getEffectiveAttackSpeed(EntityPlayer player, ItemStack candidate) {
        IAttributeInstance instance = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (instance == null) {
            return 0.0D;
        }

        ItemStack mainHand = player.getHeldItemMainhand();
        if (candidate == mainHand || ItemStack.areItemStacksEqual(candidate, mainHand)) {
            return instance.getAttributeValue();
        }

        Multimap<String, AttributeModifier> mainModifiers =
                collectAttackSpeed(mainHand, EntityEquipmentSlot.MAINHAND);
        Multimap<String, AttributeModifier> candidateModifiers =
                collectAttackSpeed(candidate, EntityEquipmentSlot.MAINHAND);

        if (mainModifiers.isEmpty() && candidateModifiers.isEmpty()) {
            return instance.getAttributeValue();
        }

        try {
            removeModifiers(player, mainModifiers);
            applyModifiers(player, candidateModifiers);
            return instance.getAttributeValue();
        } finally {
            removeModifiers(player, candidateModifiers);
            applyModifiers(player, mainModifiers);
        }
    }

    /** 收集某物品在指定槽位下、归属于 ATTACK_SPEED 的属性修饰符。 */
    public static Multimap<String, AttributeModifier> collectAttackSpeed(ItemStack stack, EntityEquipmentSlot slot) {
        Multimap<String, AttributeModifier> result = HashMultimap.create();
        if (stack == null || stack.isEmpty()) {
            return result;
        }
        Multimap<String, AttributeModifier> all = stack.getAttributeModifiers(slot);
        for (Map.Entry<String, AttributeModifier> entry : all.entries()) {
            if (SharedMonsterAttributes.ATTACK_SPEED.getName().equals(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static void removeModifiers(EntityPlayer player, Multimap<String, AttributeModifier> modifiers) {
        if (!modifiers.isEmpty()) {
            player.getAttributeMap().removeAttributeModifiers(modifiers);
        }
    }

    private static void applyModifiers(EntityPlayer player, Multimap<String, AttributeModifier> modifiers) {
        if (modifiers.isEmpty()) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        for (AttributeModifier modifier : modifiers.values()) {
            if (!instance.hasModifier(modifier)) {
                instance.applyModifier(modifier);
            }
        }
    }

    /**
     * 按有效攻速换算攻击冷却时长（tick）。等价于原版 {@code getCooldownPeriod}。
     */
    public static int getCooldownTicks(EntityPlayer player, ItemStack candidate) {
        double attackSpeed = getEffectiveAttackSpeed(player, candidate);
        if (attackSpeed <= 0.0D) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(20.0D / attackSpeed));
    }

    /**
     * 临时让指定工具的属性修饰符在玩家身上生效，执行 {@code action}，然后还原。
     *
     * <p>用于副手攻击：让副手工具的 ATTACK_DAMAGE / ATTACK_SPEED 等属性在
     * {@code ToolHelper.attackEntity} 期间生效（否则会沿用主手工具的属性）。
     */
    public static void withToolAttributes(EntityPlayer player, ItemStack tool, Runnable action) {
        ItemStack mainHand = player.getHeldItemMainhand();
        Multimap<String, AttributeModifier> mainModifiers =
                mainHand.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        Multimap<String, AttributeModifier> toolModifiers =
                tool.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);

        player.getAttributeMap().removeAttributeModifiers(mainModifiers);
        player.getAttributeMap().applyAttributeModifiers(toolModifiers);
        try {
            action.run();
        } finally {
            player.getAttributeMap().removeAttributeModifiers(toolModifiers);
            player.getAttributeMap().applyAttributeModifiers(mainModifiers);
        }
    }
}
