package com.fox.network;

import com.fox.autoattack.AutoAttackServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求用指定手持工具攻击准星目标。
 */
public class AutoAttackMessage implements IMessage {

    private int targetEntityId;
    private byte hand;

    public AutoAttackMessage() {
    }

    public AutoAttackMessage(int targetEntityId, EnumHand hand) {
        this.targetEntityId = targetEntityId;
        this.hand = (byte) (hand == EnumHand.OFF_HAND ? 1 : 0);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetEntityId = buf.readInt();
        hand = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetEntityId);
        buf.writeByte(hand);
    }

    private EnumHand getHand() {
        return hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
    }

    public static class Handler implements IMessageHandler<AutoAttackMessage, IMessage> {

        @Override
        public IMessage onMessage(AutoAttackMessage message, MessageContext context) {
            final net.minecraft.entity.player.EntityPlayerMP player =
                    context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() ->
                    AutoAttackServerHandler.attack(player, message.targetEntityId, message.getHand()));
            return null;
        }
    }
}
