package com.fox.autoattack;

import com.fox.network.AutoAttackNetwork;
import com.fox.network.AutoAttackSyncMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 副手冷却同步的发送端工具。
 */
public final class AutoAttackSync {

    private AutoAttackSync() {
    }

    /**
     * 把副手冷却计时同步给拥有该玩家的客户端。
     *
     * <p>仅在服务端有意义：若传入的不是 {@link EntityPlayerMP}（如客户端本地玩家），
     * 说明本地就是权威端，无需同步。
     */
    public static void sendOffhandCooldown(EntityPlayer player, int ticksSinceLastSwing) {
        if (player instanceof EntityPlayerMP) {
            AutoAttackNetwork.CHANNEL.sendTo(
                    new AutoAttackSyncMessage(ticksSinceLastSwing),
                    (EntityPlayerMP) player
            );
        }
    }
}
