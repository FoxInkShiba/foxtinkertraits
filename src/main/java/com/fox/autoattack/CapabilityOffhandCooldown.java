package com.fox.autoattack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.Capability.IStorage;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

/**
 * 副手武器攻击冷却的能力实现（仿 RLCombat）。
 *
 * <p>原版 {@code getCooledAttackStrength}/{@code getCooldownPeriod} 只反映主手工具的攻速。
 * 副手工具要正确吃到攻速加成（含工具自身、急迫、饰品等所有作用于 ATTACK_SPEED 的来源），
 * 需要一次独立的 {@code ticksSinceLastSwing} 计时：副手攻击后归零，每 tick 自增，
 * 冷却是否就绪由 {@code ticksSinceLastSwing >= getCooldownPeriod()} 判断。
 *
 * <p>求 {@code getCooldownPeriod} 时，需临时让“副手工具”的属性修饰符在玩家身上生效，
 * 这样 {@code ATTACK_SPEED} 才反映副手工具 + 玩家全部来源的真实值。
 */
public class CapabilityOffhandCooldown implements ICapabilityProvider {

    @CapabilityInject(CapabilityOffhandCooldown.class)
    public static Capability<CapabilityOffhandCooldown> CAPABILITY = null;

    private int ticksSinceLastSwing;
    private final EntityPlayer player;

    public CapabilityOffhandCooldown(@Nonnull EntityPlayer player) {
        this.ticksSinceLastSwing = 0;
        this.player = player;
    }

    /** 每 tick 自增（玩家存在时持续调用）。 */
    public void tick() {
        if (ticksSinceLastSwing < Integer.MAX_VALUE) {
            ticksSinceLastSwing++;
        }
    }

    public void resetTicksSinceLastSwing() {
        this.ticksSinceLastSwing = 0;
    }

    public int getTicksSinceLastSwing() {
        return ticksSinceLastSwing;
    }

    public void setTicksSinceLastSwing(int ticks) {
        this.ticksSinceLastSwing = ticks;
    }

    /** 把服务端权威值同步给客户端。 */
    public void sync() {
        AutoAttackSync.sendOffhandCooldown(this.player, this.ticksSinceLastSwing);
    }

    /**
     * 副手工具当前的有效冷却时长（tick）。等价于原版 {@code getCooldownPeriod}
     * 但以副手工具的属性为准。
     */
    public float getCooldownPeriod() {
        double attackSpeed = AutoAttackHelper.getEffectiveAttackSpeed(player, player.getHeldItemOffhand());
        if (attackSpeed <= 0.0D) {
            return Float.MAX_VALUE;
        }
        return (float) (1.0D / attackSpeed * 20.0D);
    }

    /**
     * 副手当前攻击冷却进度（0~1，1 为就绪）。对应原版的 {@code getCooledAttackStrength}。
     */
    public float getCooledAttackStrength(float adjustTicks) {
        float period = getCooldownPeriod();
        if (period <= 0.0F) {
            return 1.0F;
        }
        return MathHelper.clamp((this.ticksSinceLastSwing + adjustTicks) / period, 0.0F, 1.0F);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == CAPABILITY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        return capability == CAPABILITY ? (T) this : null;
    }

    /** 注册能力。 */
    public static void register() {
        CapabilityManager.INSTANCE.register(
                CapabilityOffhandCooldown.class,
                new Storage(),
                new Factory()
        );
    }

    public static class Storage implements IStorage<CapabilityOffhandCooldown> {

        @Override
        public NBTBase writeNBT(Capability<CapabilityOffhandCooldown> capability,
                                CapabilityOffhandCooldown instance, EnumFacing side) {
            return null;
        }

        @Override
        public void readNBT(Capability<CapabilityOffhandCooldown> capability,
                            CapabilityOffhandCooldown instance, EnumFacing side, NBTBase nbt) {
        }
    }

    public static class Factory implements Callable<CapabilityOffhandCooldown> {

        @Override
        public CapabilityOffhandCooldown call() {
            return null;
        }
    }
}
