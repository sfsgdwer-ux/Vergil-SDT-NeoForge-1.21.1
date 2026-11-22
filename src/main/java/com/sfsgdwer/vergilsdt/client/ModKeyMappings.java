package com.sfsgdwer.vergilsdt.client;

import com.sfsgdwer.vergilsdt.VergilSdtMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Key mappings must be registered on the MOD event bus, not the GAME event bus. */
@EventBusSubscriber(modid = VergilSdtMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyMappings {
    private ModKeyMappings() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientHooks.TRANSFORM);
        event.register(ClientHooks.WEIGHTED_PREVIEW);
    }
}
