package com.fox.autoattack;

import com.fox.mixin.EntityLivingBaseAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.ForgeHooks;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * 自动攻击服务端逻辑。客户端请求必须在这里重新校验目标、手持工具和攻击间隔。
 *
 * <p>主手沿用原版 {@code ticksSinceLastSwing}/{@code getCooldownPeriod}；
 * 副手用 {@link CapabilityOffhandCooldown} 独立计时，以支持副手工具各自的攻速。
 * 副手攻击期间临时让副手工具的属性生效，使伤害也以其为准。
 */
public final class AutoAttackServerHandler {

    private AutoAttackServerHandler() {
    }

    public static void attack(EntityPlayerMP player, int targetEntityId, EnumHand hand) {
        if (player == null || player.world == null || player.world.isRemote
                || player.isSpectator() || player.isHandActive()) {
            return;
        }

        Entity target = player.world.getEntityByID(targetEntityId);
        ItemStack tool = player.getHeldItem(hand);
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1.0D;
        if (target == null || target == player || !target.canBeAttackedWithItem()
                || player.getDistanceSq(target) > reach * reach
                || !(tool.getItem() instanceof ToolCore)
                || !hasAutoAttack(tool)) {
            return;
        }
        if (!ForgeHooks.onPlayerAttackTarget(player, target)) {
            return;
        }

        boolean offhand = hand == EnumHand.OFF_HAND;
        CapabilityOffhandCooldown cooldownCap = offhand
                ? player.getCapability(CapabilityOffhandCooldown.CAPABILITY, null)
                : null;
        if (offhand && (cooldownCap == null || cooldownCap.getCooledAttackStrength(0.0F) < 1.0F)) {
            return;
        }

        EntityLivingBaseAccessor swingAccessor = (EntityLivingBaseAccessor) (Object) player;
        int previousSwingTicks = swingAccessor.fox$getTicksSinceLastSwing();

        Runnable doAttack = () -> {
            if (!offhand) {
                // 主手：沿用原版冷却周期
                swingAccessor.fox$setTicksSinceLastSwing(
                        Math.max(previousSwingTicks, MathHelper.ceil(player.getCooldownPeriod()))
                );
            }
            boolean hit = ToolHelper.attackEntity(
                    tool,
                    (ToolCore) tool.getItem(),
                    player,
                    target
            );
            if (hit) {
                player.swingArm(hand);
                if (offhand && cooldownCap != null) {
                    cooldownCap.resetTicksSinceLastSwing();
                    cooldownCap.sync();
                }
            }
        };

        try {
            if (offhand) {
                // 副手：让副手工具的属性在攻击期间生效（伤害也以其为准）
                AutoAttackHelper.withToolAttributes(player, tool, doAttack);
            } else {
                doAttack.run();
            }
        } finally {
            if (!offhand) {
                swingAccessor.fox$setTicksSinceLastSwing(previousSwingTicks);
            }
        }
    }

    private static boolean hasAutoAttack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.nbt.NBTTagCompound tag = TinkerUtil.getModifierTag(stack, "autoattack");
        return tag != null && ModifierNBT.readTag(tag).level > 0;
    }
}
