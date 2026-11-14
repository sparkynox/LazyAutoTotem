// LazyAuto Totem — entrypoint | Fabric 1.21.11
package com.sparkyNox.lazyautototem;

import com.sparkyNox.lazyautototem.config.ModConfig;
import com.sparkyNox.lazyautototem.util.ServerBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public class LazyAutoTotemClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // disconnect via Fabric event — no mixin needed, version-safe
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                ServerBridge.onLeave(mc.player.getName().getString());
            }
        });

        System.out.println("[LazyAutoTotem] loaded. Bypass Packages: "
                + ModConfig.get().renderServerUrl);
    }
}
