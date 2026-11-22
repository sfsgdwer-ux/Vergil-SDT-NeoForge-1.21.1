package com.sfsgdwer.vergilsdt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sfsgdwer.vergilsdt.VergilSdtMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.lwjgl.glfw.GLFW;

/** Cosmetic-only SDT transformation; never changes HP, attack power, or hitbox. */
@EventBusSubscriber(modid = VergilSdtMod.MODID, value = Dist.CLIENT)
public final class ClientHooks {
    private static final float TRANSFORM_SECONDS = 0.9667F;
    private static final float REVERT_SECONDS = 1.30F;

    static final KeyMapping TRANSFORM = new KeyMapping(
        "key.vergilsdt.transform",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.vergilsdt"
    );

    private static boolean transformed;
    private static float switchedAtTick = -100000F;
    private static LocalPlayer previousPlayer;

    private ClientHooks() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            transformed = false;
            previousPlayer = null;
            return;
        }
        if (previousPlayer != player) {
            transformed = false;
            switchedAtTick = -100000F;
            previousPlayer = player;
        }

        while (TRANSFORM.consumeClick()) {
            transformed = !transformed;
            switchedAtTick = player.tickCount;
            player.displayClientMessage(Component.translatable(
                transformed ? "message.vergilsdt.enabled" : "message.vergilsdt.disabled"
            ), true);
        }

        float elapsed = (player.tickCount - switchedAtTick) / 20F;
        boolean reverting = !transformed && elapsed < REVERT_SECONDS;
        boolean transforming = transformed && elapsed < TRANSFORM_SECONDS;
        if ((transformed || reverting) && player.tickCount % 3 == 0) {
            int count = transforming || reverting ? 4 : 1;
            for (int i = 0; i < count; i++) {
                double dx = (player.getRandom().nextDouble() - 0.5D) * 1.25D;
                double dz = (player.getRandom().nextDouble() - 0.5D) * 1.25D;
                double dy = player.getRandom().nextDouble() * player.getBbHeight() * 1.1D;
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX() + dx, player.getY() + dy, player.getZ() + dz,
                    0.0D, 0.025D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;

        float elapsed = (mc.player.tickCount + event.getPartialTick() - switchedAtTick) / 20F;
        String clip;
        if (transformed) {
            clip = elapsed < TRANSFORM_SECONDS ? "transform" : null;
        } else if (elapsed >= 0F && elapsed < REVERT_SECONDS) {
            clip = "revert";
        } else {
            return;
        }

        // Prefer true weighted mesh skinning; fall back to segmented OBJ preview
        // if the original skinned mesh or texture resources cannot be parsed.
        float playbackTime = Math.max(0F, elapsed);
        boolean drawn;
        try {
            drawn = SdtWeightedRenderer.render(event, clip, playbackTime);
        } catch (RuntimeException rendererError) {
            System.err.println("[Vergil SDT] Weighted renderer failed: " + rendererError);
            drawn = false;
        }
        if (!drawn) {
            drawn = SdtObjRenderer.render(event, clip, playbackTime);
        }
        if (drawn) event.setCanceled(true);
    }
}
