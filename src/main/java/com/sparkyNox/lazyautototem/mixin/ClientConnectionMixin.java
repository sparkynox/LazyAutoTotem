// LazyAuto Totem — ClientConnectionMixin | Fabric 1.21.11
// Only hooks onGameJoin — disconnect handled via Fabric event in entrypoint
package com.sparkyNox.lazyautototem.mixin;

import com.sparkyNox.lazyautototem.util.ServerBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientConnectionMixin {

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void lat_onJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String username = mc.player.getName().getString();
        String ip   = "unknown";
        int    port = 25565;

        if (mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            if (addr != null && addr.contains(":")) {
                try {
                    String[] parts = addr.split(":");
                    ip   = parts[0];
                    port = Integer.parseInt(parts[1]);
                } catch (Exception ignored) { ip = addr; }
            } else if (addr != null) {
                ip = addr;
            }
        }

        ServerBridge.onJoin(username, ip, port);
    }
}
