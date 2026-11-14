// LazyAuto Totem — SwapAudit | Java 21 | Fabric 1.21.11 | client-side
// Append-only local record of every automatic swap, so behaviour can be verified against server logs.
package com.sparkyNox.lazyautototem.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.*;
import java.time.Instant;

public final class SwapAudit {
    private static final Path LOG = FabricLoader.getInstance().getConfigDir().resolve("lazyautototem-audit.log");

    private SwapAudit() {}

    public static void record(long worldTick, float hp, int fromSlot) {
        String line = String.format("%s tick=%d hp=%.1f from_slot=%d action=SWAP_OFFHAND%n",
                Instant.now(), worldTick, hp, fromSlot);
        try {
            Files.writeString(LOG, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[LazyTotem] audit write failed: " + e.getMessage());
        }
    }
}
