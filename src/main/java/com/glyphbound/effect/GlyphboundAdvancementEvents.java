package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphboundAdvancements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class GlyphboundAdvancementEvents {
    private GlyphboundAdvancementEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkAttached(InkMarkAttachedEvent event) {
        InkMark mark = event.mark();
        if (mark.owner() == null) {
            return;
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(mark.owner());
        if (player != null) {
            GlyphboundAdvancements.awardGlyphWritten(player, mark.word());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % 100L != 0L) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (GlyphboundAdvancements.hasInkStaff(player)) {
                GlyphboundAdvancements.award(player, GlyphboundAdvancements.ROOT);
            }
            if (GlyphboundAdvancements.hasInkCore(player)) {
                GlyphboundAdvancements.award(player, GlyphboundAdvancements.INK_CORE_DROP);
            }
        }
    }
}
