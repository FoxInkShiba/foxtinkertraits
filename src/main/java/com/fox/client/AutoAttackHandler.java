package com.fox.client;

import com.fox.autoattack.CapabilityOffhandCooldown;
import com.fox.network.AutoAttackMessage;
import com.fox.network.AutoAttackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.utils.TinkerUtil;

/**
 * 自动攻击：按住左键时，按玩家当前攻击冷却自动攻击准星下的实体。
 *
 * <p>该处理器只在客户端注册。主手沿用 PlayerController 的原版攻击包（按其自身冷却与后摆），
 * 副手使用自动攻击专用包，并依据副手工具自己的攻击冷却（见 {@link CapabilityOffhandCooldown}）
 * 决定何时可以再次攻击，从而正确吃到各种攻击速度加成。
 */
@SideOnly(Side.CLIENT)
public final class AutoAttackHandler {

    private AutoAttackHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.player;
        if (player == null || minecraft.world == null || minecraft.playerController == null
                || minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            return;
        }
        if (!minecraft.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (player.isSpectator() || player.isHandActive()) {
            return;
        }

        RayTraceResult mouseOver = minecraft.objectMouseOver;
        if (mouseOver == null || mouseOver.typeOfHit != RayTraceResult.Type.ENTITY) {
            return;
        }

        Entity target = mouseOver.entityHit;
        if (target == null || target == player || !target.canBeAttackedWithItem()) {
            return;
        }

        // 主手：沿用原版攻击流程，其冷却由原版 getCooledAttackStrength 判断。
        ItemStack mainHand = player.getHeldItemMainhand();
        if (mainHand.getItem() instanceof ToolCore
                && hasAutoAttack(mainHand)
                && player.getCooledAttackStrength(0.0F) >= 1.0F) {
            minecraft.playerController.attackEntity(player, target);
            player.swingArm(EnumHand.MAIN_HAND);
        }

        // 副手：用副手自己的攻击冷却。
        ItemStack offHand = player.getHeldItemOffhand();
        if (!(offHand.getItem() instanceof ToolCore) || !hasAutoAttack(offHand)) {
            return;
        }

        CapabilityOffhandCooldown cap =
                player.getCapability(CapabilityOffhandCooldown.CAPABILITY, null);
        if (cap == null || cap.getCooledAttackStrength(0.0F) < 1.0F) {
            return;
        }

        AutoAttackNetwork.CHANNEL.sendToServer(
                new AutoAttackMessage(target.getEntityId(), EnumHand.OFF_HAND)
        );
        player.swingArm(EnumHand.OFF_HAND);
        // 客户端先行归零，避免下一个 tick 重复发包；服务端处理后会同步权威值回来。
        cap.resetTicksSinceLastSwing();
    }

    private static boolean hasAutoAttack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.nbt.NBTTagCompound tag = TinkerUtil.getModifierTag(stack, "autoattack");
        return tag != null && ModifierNBT.readTag(tag).level > 0;
    }
}
