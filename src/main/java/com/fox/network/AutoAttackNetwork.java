package com.fox.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 自动攻击的客户端到服务端请求通道。
 */
public final class AutoAttackNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("foxtinkertraits_autoattack");

    private static boolean initialized;

    private AutoAttackNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        CHANNEL.registerMessage(
                AutoAttackMessage.Handler.class,
                AutoAttackMessage.class,
                0,
                Side.SERVER
        );
        CHANNEL.registerMessage(
                AutoAttackSyncMessage.ClientHandler.class,
                AutoAttackSyncMessage.class,
                1,
                Side.CLIENT
        );
        initialized = true;
    }
}
