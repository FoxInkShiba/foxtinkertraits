package com.fox.network;

import com.fox.autoattack.CapabilityOffhandCooldown;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 服务端 → 客户端：同步副手攻击冷却的 ticksSinceLastSwing。
 *
 * <p>自动攻击由客户端发起、服务端校验。服务端在副手攻击后归零该计时，
 * 需要把权威值同步给客户端，避免两端漂移导致"客户端以为可以打、服务端拒绝"。
 */
public class AutoAttackSyncMessage implements IMessage {

    private int ticksSinceLastSwing;

    public AutoAttackSyncMessage() {
    }

    public AutoAttackSyncMessage(int ticksSinceLastSwing) {
        this.ticksSinceLastSwing = ticksSinceLastSwing;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ticksSinceLastSwing = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ticksSinceLastSwing);
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<AutoAttackSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(AutoAttackSyncMessage message, MessageContext context) {
            final int ticks = message.ticksSinceLastSwing;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null) {
                    CapabilityOffhandCooldown cap =
                            player.getCapability(CapabilityOffhandCooldown.CAPABILITY, null);
                    if (cap != null) {
                        cap.setTicksSinceLastSwing(ticks);
                    }
                }
            });
            return null;
        }
    }
}
