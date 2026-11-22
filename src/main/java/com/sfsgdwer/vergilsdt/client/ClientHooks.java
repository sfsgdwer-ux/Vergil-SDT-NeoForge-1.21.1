package com.sfsgdwer.vergilsdt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sfsgdwer.vergilsdt.VergilSdtMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary client-only transformation toggle and particles.
 *
 * IMPORTANT: this prototype does not yet render the original SDT model.
 * It never changes player attributes or combat abilities.
 */
@EventBusSubscriber(modid = VergilSdtMod.MODID, value = Dist.CLIENT)
public final class ClientHooks {
    private static final KeyMapping TRANSFORM = new KeyMapping(
        "key.vergilsdt.transform",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.vergilsdt"
    );

    private static boolean transformed = false;

    private ClientHooks() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TRANSFORM);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            transformed = false;
            return;
        }

        while (TRANSFORM.consumeClick()) {
            transformed = !transformed;
            mc.player.displayClientMessage(
                Component.translatable(
                    transformed ? "message.vergilsdt.enabled" : "message.vergilsdt.disabled"
                ),
                true
            );
        }

        // Only visual feedback during the early prototype phase.
        if (transformed && mc.player.tickCount % 4 == 0) {
            double dx = (mc.player.getRandom().nextDouble() - 0.5D) * 0.9D;
            double dz = (mc.player.getRandom().nextDouble() - 0.5D) * 0.9D;
            double dy = mc.player.getRandom().nextDouble() * mc.player.getBbHeight();

            mc.level.addParticle(
                ParticleTypes.SOUL_FIRE_FLAME,
                mc.player.getX() + dx,
                mc.player.getY() + dy,
                mc.player.getZ() + dz,
                0.0D, 0.02D, 0.0D
            );
        }
    }
}
