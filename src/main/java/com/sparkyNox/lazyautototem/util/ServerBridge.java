// LazyAuto Totem — ServerBridge | Fabric 1.21.1
// Handles: POST /join, POST /leave, POST /coords, GET /poll (long-poll loop)
package com.sparkyNox.lazyautototem.util;

import com.google.gson.JsonObject;
import com.sparkyNox.lazyautototem.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerBridge {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LazyTotem-Bridge");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicBoolean polling = new AtomicBoolean(false);
    private static volatile String currentUsername = null;

    // ── called on server join ─────────────────────────────────────────────
    public static void onJoin(String username, String ip, int port) {
        if (ModConfig.get().safeMode || ModConfig.get().stealthMode) return; // safe mode: no off-platform reporting
        currentUsername = username;
        JsonObject body = new JsonObject();
        body.addProperty("username",   username);
        body.addProperty("serverIp",   ip);
        body.addProperty("serverPort", port);
        post("/join", body.toString());

        if (polling.compareAndSet(false, true)) {
            SCHEDULER.execute(() -> pollLoop(username));
        }
    }

    // ── called on server leave / disconnect ───────────────────────────────
    public static void onLeave(String username) {
        if (ModConfig.get().safeMode || ModConfig.get().stealthMode) return;
        polling.set(false);
        currentUsername = null;
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        post("/leave", body.toString());
    }

    // ── long-poll loop — runs on background thread ─────────────────────
    private static void pollLoop(String username) {
        while (polling.get() && username.equals(currentUsername)) {
            try {
                HttpRequest req = buildReq("/poll?username=" + username, "GET", null);
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    // parse command
                    String body = resp.body();
                    // body: {"command":"coords"}
                    if (body.contains("\"coords\"")) {
                        handleCoordsCommand();
                    }
                }
                // 204 = no command yet, loop immediately
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // network blip — wait 3s before retry
                try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    // ── read coords from game thread, POST back ───────────────────────────
    private static void handleCoordsCommand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // schedule on game thread to safely read player state
        mc.execute(() -> {
            ClientPlayerEntity player = mc.player;
            if (player == null) return;

            double x        = player.getX();
            double y        = player.getY();
            double z        = player.getZ();
            long   gameTime = mc.world != null ? mc.world.getTime() : -1;
            String dim      = "unknown";
            if (mc.world != null) {
                RegistryKey<World> key = mc.world.getRegistryKey();
                dim = key.getValue().toString();
            }

            // local chat echo
            String dimShort  = dim.replace("minecraft:", "");
            String extra = "";
            if (dim.equals("minecraft:overworld"))
                extra = String.format(" §7(Nether: %.1f/%.1f/%.1f)", x/8, y, z/8);
            else if (dim.equals("minecraft:the_nether"))
                extra = String.format(" §7(OW: %.1f/%.1f/%.1f)", x*8, y, z*8);

            player.sendMessage(Text.literal(String.format(
                "§b[Coords] §fX:§a%.2f §fY:§a%.2f §fZ:§a%.2f §f| §e%s%s",
                x, y, z, dimShort, extra
            )), false);

            // POST to Render
            JsonObject body = new JsonObject();
            body.addProperty("username",  player.getName().getString());
            body.addProperty("x",         x);
            body.addProperty("y",         y);
            body.addProperty("z",         z);
            body.addProperty("dimension", dim);
            body.addProperty("gameTime",  gameTime);
            post("/coords", body.toString());
        });
    }

    // ── fire async POST ───────────────────────────────────────────────────
    private static void post(String endpoint, String json) {
        try {
            HttpRequest req = buildReq(endpoint, "POST", json);
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((r, e) -> {
                    if (e != null)
                        System.err.println("[LazyTotem] POST " + endpoint + " failed: " + e.getMessage());
                });
        } catch (Exception e) {
            System.err.println("[LazyTotem] build request failed: " + e.getMessage());
        }
    }

    private static HttpRequest buildReq(String endpoint, String method, String json) {
        ModConfig cfg = ModConfig.get();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(cfg.renderServerUrl.replaceAll("/+$", "") + endpoint))
                .timeout(Duration.ofSeconds(28)); // just under Render's 30s request limit

        if (!cfg.serverAuthToken.isBlank())
            b.header("Authorization", "Bearer " + cfg.serverAuthToken);

        if ("POST".equals(method)) {
            b.header("Content-Type", "application/json");
            b.POST(HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.GET();
        }
        return b.build();
    }
}
