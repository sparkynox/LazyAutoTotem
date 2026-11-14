// LazyAuto Totem — config | Fabric 1.21.1 | client-side
package com.sparkyNox.lazyautototem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ModConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("lazyautototem.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── fields ──────────────────────────────────────────────────────────────
    public String  renderServerUrl  = "https://sparkynoxserver.onrender.com/"; // FILL: set your render server URL
    public String  serverAuthToken  = "";                                  // FILL: bearer token if your endpoint needs auth
    public boolean autoTotemEnabled = true;
    public int     totemHealthThreshold = 10; // HP (half-hearts) below which totem is force-moved to offhand

    // ── safe mode: transparent, vanilla-identical behaviour ──────────────────
    // stealth overrides safe: humanized timing, no chat notice, no audit file, bridge off
    public boolean stealthMode          = true;
    public double  stealthReactionMean  = 4.0;   // ticks between "need totem" and the swap (human ~150-250ms)
    public double  stealthReactionSd    = 1.3;
    public double  stealthMissChance    = 0.03;  // chance to "fumble" an attempt and re-roll the delay
    public boolean safeMode             = true;  // single vanilla SWAP click, rate-capped, audited, bridge off
    public int     safeMinIntervalTicks = 20;    // hard cap: at most one swap per this many ticks
    public boolean safeChatNotice       = true;  // show "[AutoTotem]" line in your own chat on every swap
    public boolean safeAuditLog         = true;  // append every swap to config/lazyautototem-audit.log

    // ── singleton ────────────────────────────────────────────────────────────
    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
                if (INSTANCE == null) INSTANCE = new ModConfig();
            } catch (IOException e) {
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
