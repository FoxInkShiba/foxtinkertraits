package com.fox.health;

import com.fox.config.FoxConfig;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class HealthCapHelper {

    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Deque<RawHealthWrite>> RAW_HEALTH_WRITES = new ThreadLocal<>();

    private static boolean checked = false;
    private static Method getArmorAbilityLevel = null;

    public static boolean isProcessing() {
        return PROCESSING.get();
    }

    public static void setProcessing(boolean value) {
        PROCESSING.set(value);
    }

    public static void beginRawHealthWrite(EntityLivingBase entity, float target) {
        Deque<RawHealthWrite> writes = RAW_HEALTH_WRITES.get();
        if (writes == null) {
            writes = new ArrayDeque<>();
            RAW_HEALTH_WRITES.set(writes);
        }
        writes.push(new RawHealthWrite(entity, target));
    }

    public static Float consumeRawHealthTarget(EntityLivingBase entity) {
        Deque<RawHealthWrite> writes = RAW_HEALTH_WRITES.get();
        if (writes == null) {
            return null;
        }
        for (RawHealthWrite write : writes) {
            if (write.entity == entity) {
                if (write.consumed) {
                    return null;
                }
                write.consumed = true;
                return write.target;
            }
        }
        return null;
    }

    public static void endRawHealthWrite(EntityLivingBase entity) {
        Deque<RawHealthWrite> writes = RAW_HEALTH_WRITES.get();
        if (writes == null) {
            return;
        }
        Iterator<RawHealthWrite> iterator = writes.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().entity == entity) {
                iterator.remove();
                break;
            }
        }
        if (writes.isEmpty()) {
            RAW_HEALTH_WRITES.remove();
        }
    }

    /**
     * 返回半神的总比例。半神减少的是本次生命值修改量，
     * 例如当前 20、目标 0、比例 0.5，实际只减少 10 点。
     */
    public static float getDemigodRatio(EntityPlayer player) {
        if (player instanceof FakePlayer) {
            return 0f;
        }
        int demigodLevel = getArmorAbilityLevel(player, "demigod_armor");
        return demigodLevel > 0 ? demigodLevel * FoxConfig.demigodPerLevel : 0f;
    }

    /**
     * 返回乌龟的总比例。乌龟需要当前生命值不低于配置阈值；
     * 低于阈值时返回 0，表示本次不生效。
     */
    public static float getTurtleRatio(EntityPlayer player) {
        if (player instanceof FakePlayer) {
            return 0f;
        }
        if (player.getHealth() < FoxConfig.turtleMinHealth) {
            return 0f;
        }
        int turtleLevel = getArmorAbilityLevel(player, "turtle_armor");
        return turtleLevel > 0 ? turtleLevel * FoxConfig.turtlePerLevel : 0f;
    }

    public static int getArmorAbilityLevel(EntityPlayer player, String identifier) {
        if (player == null || player instanceof FakePlayer) {
            return 0;
        }
        Method m = getMethod();
        if (m == null) {
            return 0;
        }
        try {
            return ((Number) m.invoke(null, player, identifier)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 半神规则：按比例削减本次生命值修改量。
     *
     * <p>默认只削减"损失"（掉血）；开启 {@link FoxConfig#demigodHalveHealing} 后，
     * "增加"（回血）也按同样比例削减。
     */
    public static float applyDemigod(float target, EntityLivingBase self, float ratio) {
        float current = self.getHealth();
        float delta = target - current;
        float reductionRatio = clampRatio(ratio);

        if (delta >= 0) {
            // 生命值增加（回血）：默认不削减，除非显式开启。
            if (!FoxConfig.demigodHalveHealing) {
                return target;
            }
            return current + delta * (1.0F - reductionRatio);
        }

        float actualLoss = -delta;
        float remainingLoss = actualLoss * (1.0F - reductionRatio);
        return current - remainingLoss;
    }

    /**
     * 乌龟规则：单次最多扣除当前生命值乘以配置比例。
     * 目标值再低，也不会因为本次修改扣掉超过这个上限的生命值。
     */
    public static float applyTurtle(float target, EntityLivingBase self, float ratio) {
        float current = self.getHealth();
        float delta = target - current;

        if (delta >= 0) {
            return target;
        }

        float maxLoss = current * clampRatio(ratio);
        float actualLoss = Math.min(-delta, maxLoss);
        return current - actualLoss;
    }

    private static float clampRatio(float ratio) {
        return Math.max(0.0F, Math.min(1.0F, ratio));
    }

    private static Method getMethod() {
        if (!checked) {
            checked = true;
            try {
                Class<?> helper = Class.forName("c4.conarm.common.armor.utils.ArmorHelper");
                getArmorAbilityLevel = helper.getMethod(
                        "getArmorAbilityLevel",
                        EntityPlayer.class,
                        String.class
                );
            } catch (Throwable t) {
                getArmorAbilityLevel = null;
            }
        }
        return getArmorAbilityLevel;
    }

    private static final class RawHealthWrite {
        private final EntityLivingBase entity;
        private final float target;
        private boolean consumed;

        private RawHealthWrite(EntityLivingBase entity, float target) {
            this.entity = entity;
            this.target = target;
        }
    }
}
