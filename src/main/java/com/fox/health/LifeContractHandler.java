package com.fox.health;

import com.fox.config.FoxConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 残命之契：按词条等级限制玩家当前生命值，并减少指定阶段的伤害。
 *
 * 普通等级 n 的生命值上限为 maxHealth * (1 - n / maxLevel)，
 * 满级时固定为 1 点；伤害减免与等级比例相同，满级为
 * 1 - (1 / maxHealth)。
 */
public final class LifeContractHandler {

    private static final String MODIFIER_ID = "lifecontract_armor";

    private LifeContractHandler() {
    }

    /**
     * 只处理伤害，不处理回血。调用者决定当前伤害处理所处的阶段。
     *
     * <p>用于模式 1/2（改的是伤害数值）：减免比例上限夹到 1（避免伤害变负），下限不设，
     * 因此负系数会放大伤害。
     */
    public static float reduceDamage(EntityPlayer player, float amount) {
        if (amount <= 0.0F) {
            return amount;
        }

        float reduction = Math.min(1.0F, getDamageReduction(player));
        return amount * (1.0F - reduction);
    }

    /**
     * 在最终生命值写入阶段减少本次掉血（模式 3）。
     *
     * <p>用血量差计算，因此减免比例必须夹到 [0,1]（不能为负：负值会让结果超过当前生命值，
     * 语义错乱；也不能超过 1）。
     */
    public static float reduceHealthTarget(EntityPlayer player, float current, float target) {
        float cap = getHealthCap(player);
        float effectiveCurrent = Math.min(current, cap);
        float effectiveTarget = Math.min(target, cap);

        if (effectiveTarget >= effectiveCurrent) {
            return effectiveTarget;
        }

        float actualLoss = effectiveCurrent - effectiveTarget;
        float reduction = getDamageReductionClamped(player);
        return effectiveCurrent - actualLoss * (1.0F - reduction);
    }

    /**
     * 将生命值写入限制在残命之契当前等级的上限内。
     */
    public static float limitHealthTarget(EntityPlayer player, float target) {
        return Math.min(target, getHealthCap(player));
    }

    /**
     * 玩家装备变化后，下一次 tick 立即压低超过词条上限的当前生命值。
     * 脱下词条后不会自动补回生命值。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }

        float cap = getHealthCap(player);
        if (player.getHealth() > cap) {
            player.setHealth(cap);
        }
    }

    public static float getHealthCap(EntityPlayer player) {
        int level = getLevel(player);
        if (level <= 0) {
            return player.getMaxHealth();
        }

        float maxHealth = player.getMaxHealth();
        int maxLevel = Math.max(1, FoxConfig.lifeContractMaxLevel);
        if (level >= maxLevel) {
            return Math.min(maxHealth, 1.0F);
        }

        float healthRatio = 1.0F - ((float) level / (float) maxLevel);
        return Math.min(maxHealth, maxHealth * healthRatio);
    }

    /**
     * 残命之契按等级算出的伤害减免比例（已乘配置系数），<b>不夹取</b>。
     *
     * <p>可为负（表示增伤，仅模式 1/2 有意义）或大于 1（过度减伤）。各调用点按语义自行夹取：
     * 模式 1/2 改的是伤害数值，上限夹到 1（避免伤害变负成回血），下限不限（允许增伤）；
     * 模式 3 用血量差计算，必须夹到 [0,1]。
     */
    private static float getDamageReduction(EntityPlayer player) {
        int level = getLevel(player);
        if (level <= 0) {
            return 0.0F;
        }

        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return 0.0F;
        }

        int maxLevel = Math.max(1, FoxConfig.lifeContractMaxLevel);
        float base;
        if (level >= maxLevel) {
            base = 1.0F - (1.0F / maxHealth);
        } else {
            base = (float) level / (float) maxLevel;
        }
        return base * FoxConfig.lifeContractDamageReductionScale;
    }

    /** 模式 3 专用：把减免夹取到 [0,1]（不能为负，也不能超过 1）。 */
    private static float getDamageReductionClamped(EntityPlayer player) {
        return clampRatio(getDamageReduction(player));
    }

    private static int getLevel(EntityPlayer player) {
        return Math.min(
                Math.max(0, HealthCapHelper.getArmorAbilityLevel(player, MODIFIER_ID)),
                Math.max(1, FoxConfig.lifeContractMaxLevel)
        );
    }

    private static float clampRatio(float ratio) {
        return Math.max(0.0F, Math.min(1.0F, ratio));
    }
}
