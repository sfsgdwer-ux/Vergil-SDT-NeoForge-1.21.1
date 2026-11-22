package com.sfsgdwer.vergilsdt;

import net.neoforged.fml.common.Mod;

/**
 * Core mod entry point. Cosmetic logic is client-side only.
 */
@Mod(VergilSdtMod.MODID)
public final class VergilSdtMod {
    public static final String MODID = "vergilsdt";

    public VergilSdtMod() {
        // All cosmetic handlers are registered via EventBusSubscriber.
    }
}
