// LazyAuto Totem — ClientPlayerEntityMixin | Java 21 | Fabric 1.21.11 | client-side
// modes: stealth (humanized) > safe (transparent) > legacy (3-click pickup)
package com.sparkyNox.lazyautototem.mixin;

import com.sparkyNox.lazyautototem.config.ModConfig;
import com.sparkyNox.lazyautototem.util.SwapAudit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    private int lat_cooldown = 0;
    private int lat_pending  = -1; // stealth: ticks left until the swap fires, -1 = not armed
    private int lat_stage    = 0;  // stealth hotbar path: 0 idle, 1 = send swap, 2 = restore slot
    private int lat_prevSlot = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void lat_onTick(CallbackInfo ci) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.autoTotemEnabled) return;
        if (lat_cooldown-- > 0) return;

        ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null) return;

        if (lat_stage == 1) {
            self.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            lat_stage = 2;
            return;
        }
        if (lat_stage == 2) {
            self.getInventory().setSelectedSlot(lat_prevSlot);
            self.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(lat_prevSlot));
            lat_stage = 0;
            lat_cooldown = 12 + ThreadLocalRandom.current().nextInt(24);
            return;
        }

        ItemStack offhand = self.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) { lat_pending = -1; return; }

        float hp = self.getHealth();
        boolean criticalHp = hp <= cfg.totemHealthThreshold;
        if (!offhand.isEmpty() && !criticalHp) { lat_pending = -1; return; }

        int totemSlot = findTotem(self.getInventory());
        if (totemSlot < 0) { lat_pending = -1; return; }
        int handlerSlot = totemSlot < 9 ? 36 + totemSlot : totemSlot;
        int syncId = self.playerScreenHandler.syncId;

        if (cfg.stealthMode) {
            if (totemSlot >= 9 && !(mc.currentScreen instanceof InventoryScreen)) return; // no screen-less inventory clicks
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            // human reaction: arm a gaussian delay instead of reacting on the first eligible tick
            // defeats: "swap latency == constant / 1 tick after pop" checks. caught by: large-sample timing histograms
            if (lat_pending < 0) {
                lat_pending = (int) Math.round(Math.max(2, Math.min(9,
                        cfg.stealthReactionMean + rnd.nextGaussian() * cfg.stealthReactionSd)));
            }
            if (lat_pending-- > 0) return;
            lat_pending = -1;

            // humans fumble and are busy: skip while eating/blocking, and occasionally miss the attempt
            // defeats: "perfect success rate" heuristics. caught by: nothing cheap, costs you the occasional totem
            if (self.isUsingItem() || rnd.nextDouble() < cfg.stealthMissChance) {
                lat_cooldown = 1 + rnd.nextInt(3);
                return;
            }

            if (totemSlot >= 9) {
                // totem is in the main inventory and the player has the inventory open: same click a hand swap sends
                mc.interactionManager.clickSlot(syncId, handlerSlot, 40, SlotActionType.SWAP, self);
            } else {
                // hotbar totem: replay the manual F-swap flow with real packets, one per tick
                // select totem slot -> offhand-swap action -> restore slot. no container click, so no screen-less-click tell
                // caught by: hotbar-flick pattern analysis (very rare)
                lat_prevSlot = self.getInventory().getSelectedSlot();
                self.getInventory().setSelectedSlot(totemSlot);
                self.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(totemSlot));
                lat_stage = 1;
                lat_cooldown = 0;
                return;
            }
            lat_cooldown = 12 + rnd.nextInt(24);
            return;
        }

        if (cfg.safeMode) {
            lat_cooldown = Math.max(cfg.safeMinIntervalTicks, 1);
            mc.interactionManager.clickSlot(syncId, handlerSlot, 40, SlotActionType.SWAP, self);
            if (cfg.safeAuditLog) SwapAudit.record(mc.world != null ? mc.world.getTime() : -1, hp, handlerSlot);
            if (cfg.safeChatNotice)
                self.sendMessage(Text.literal("\u00a76[AutoTotem] \u00a7fswapped totem to offhand (hp " + (int) hp + ")"), false);
            return;
        }

        lat_cooldown = 5;
        mc.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.PICKUP, self);
        mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, self);
        if (!self.playerScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.PICKUP, self);
        }
    }

    private int findTotem(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) return i;
        for (int i = 9; i < 36; i++) if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) return i;
        return -1;
    }
}
