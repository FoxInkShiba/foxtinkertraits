package com.fox.autoattack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 副手冷却能力的事件处理器：挂载能力 + 每 tick 递增计时。
 */
public final class OffhandCooldownHandler {

    private static final ResourceLocation KEY =
            new ResourceLocation("foxtinkertraits", "offhand_cooldown");

    private OffhandCooldownHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(KEY, new CapabilityOffhandCooldown((EntityPlayer) event.getObject()));
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            CapabilityOffhandCooldown cap = event.getEntityLiving()
                    .getCapability(CapabilityOffhandCooldown.CAPABILITY, null);
            if (cap != null) {
                cap.tick();
            }
        }
    }
}
