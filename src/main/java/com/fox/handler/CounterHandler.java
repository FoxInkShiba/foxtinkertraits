package com.fox.handler;

import com.fox.config.FoxConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 反击——受到攻击时，手持反击工具自动攻击攻击者，每 tick 有次数上限。
 */
public final class CounterHandler {

    private static final Random RANDOM = new Random();

    // 每个玩家当前 tick 已反击的次数，key 为玩家 UUID
    private static final Map<UUID, Long> lastTick = new HashMap<>();
    private static final Map<UUID, Integer> tickCount = new HashMap<>();

    private CounterHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (!(victim instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) victim;

        // 找到攻击来源实体
        Entity attacker = event.getSource().getTrueSource();
        if (attacker == null) {
            attacker = event.getSource().getImmediateSource();
        }
        if (attacker == null || attacker == player) {
            return;
        }
        if (!(attacker instanceof EntityLivingBase)) {
            return;
        }

        // 概率判定
        if (FoxConfig.counterChance < 1.0f && RANDOM.nextFloat() >= FoxConfig.counterChance) {
            return;
        }

        // 每 tick 次数上限
        long tick = player.world.getTotalWorldTime();
        UUID uuid = player.getUniqueID();
        Long prev = lastTick.get(uuid);
        int count = (prev != null && prev == tick) ? tickCount.getOrDefault(uuid, 0) : 0;
        if (count >= FoxConfig.counterMaxPerTick) {
            return;
        }

        // 找带反击强化的手持工具：优先主手，其次副手
        ItemStack tool = player.getHeldItemMainhand();
        if (!hasCounter(tool)) {
            ItemStack off = player.getHeldItemOffhand();
            tool = hasCounter(off) ? off : ItemStack.EMPTY;
        }
        if (tool.isEmpty() || !(tool.getItem() instanceof ToolCore)) {
            return;
        }

        // 执行反击
        boolean hit = ToolHelper.attackEntity(
                tool,
                (ToolCore) tool.getItem(),
                player,
                attacker
        );
        if (hit) {
            lastTick.put(uuid, tick);
            tickCount.put(uuid, count + 1);
        }
    }

    private static boolean hasCounter(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        NBTTagCompound tag = TinkerUtil.getModifierTag(stack, "counter");
        if (tag == null) {
            return false;
        }
        return ModifierNBT.readTag(tag).level > 0;
    }
}
