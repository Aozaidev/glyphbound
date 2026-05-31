package com.glyphbound.client;

import com.aozainkmc.api.AozaiInkApi;
import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkMarkStore;
import com.aozainkmc.api.InkTarget;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.api.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.effect.BodyGlyphEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = Glyphbound.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GlyphDurationHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "glyph_duration_hud");
    private static final Set<String> DISPLAYED_WORDS = Set.of(
        "心", "命", "救", "息", "忍", "坚", "稳", "隐", "明", "力", "怒", "脉"
    );
    private static final Set<String> SURVIVAL_WORDS = Set.of("命", "救", "心", "忍", "隐");
    private static final int MAX_ROWS = 5;
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 40;
    private static final int ROW_WIDTH = 74;
    private static final int ROW_HEIGHT = 10;
    private static final int ROW_GAP = 4;
    private static String focusedWord = "";

    private GlyphDurationHud() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, LAYER, GlyphDurationHud::render);
    }

    @EventBusSubscriber(modid = Glyphbound.MOD_ID, value = Dist.CLIENT)
    public static final class RuntimeEvents {
        private RuntimeEvents() {
        }

        @SubscribeEvent
        public static void onInkMarkAttached(InkMarkAttachedEvent event) {
            InkMark mark = event.mark();
            if (mark.target().type() == InkTargetType.PLAYER && DISPLAYED_WORDS.contains(mark.word())) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null && minecraft.player.getUUID().equals(mark.target().entityUuid())) {
                    focusedWord = mark.word();
                }
            }
        }
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        List<InkMark> marks = activeMarks(minecraft, gameTime);
        if (marks.isEmpty()) {
            focusedWord = "";
            return;
        }

        marks = marks.stream()
            .sorted(displayComparator(gameTime))
            .toList();

        int x = PANEL_X;
        int visibleRows = Math.min(MAX_ROWS, marks.size());

        for (int i = 0; i < visibleRows; i++) {
            renderRow(graphics, minecraft.font, marks.get(i), gameTime, x, PANEL_Y + i * (ROW_HEIGHT + ROW_GAP));
        }

        if (marks.size() > MAX_ROWS) {
            String more = "+" + (marks.size() - MAX_ROWS);
            graphics.drawString(minecraft.font, more, x + ROW_WIDTH - minecraft.font.width(more), PANEL_Y + visibleRows * (ROW_HEIGHT + ROW_GAP) - 1, 0x88D8C79C, true);
        }
    }

    private static List<InkMark> activeMarks(Minecraft minecraft, long gameTime) {
        InkMarkStore store;
        try {
            store = AozaiInkApi.marks();
        } catch (IllegalStateException exception) {
            return List.of();
        }

        InkTarget target = InkTarget.player(minecraft.level.dimension().location().toString(), minecraft.player.getUUID());
        Map<String, InkMark> latestByWord = new HashMap<>();
        for (InkMark mark : store.marksOn(target)) {
            if (!DISPLAYED_WORDS.contains(mark.word()) || BodyGlyphEvents.effectiveActiveUntil(minecraft.player, mark, gameTime) <= gameTime) {
                continue;
            }
            InkMark latest = latestByWord.get(mark.word());
            if (latest == null || mark.bornGameTime() > latest.bornGameTime()) {
                latestByWord.put(mark.word(), mark);
            }
        }
        return new ArrayList<>(latestByWord.values());
    }

    private static Comparator<InkMark> displayComparator(long gameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        return Comparator
            .comparingInt((InkMark mark) -> mark.word().equals(focusedWord) ? 0 : 1)
            .thenComparingInt(mark -> SURVIVAL_WORDS.contains(mark.word()) ? 0 : 1)
            .thenComparingLong(mark -> BodyGlyphEvents.effectiveActiveUntil(minecraft.player, mark, gameTime) - gameTime)
            .thenComparing(Comparator.comparingLong(InkMark::bornGameTime).reversed());
    }

    private static void renderRow(GuiGraphics graphics, Font font, InkMark mark, long gameTime, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        long expiresAt = BodyGlyphEvents.effectiveActiveUntil(minecraft.player, mark, gameTime);
        long totalTicks = Math.max(1L, expiresAt - mark.bornGameTime());
        long remainingTicks = Math.max(0L, expiresAt - gameTime);
        float progress = Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) totalTicks));

        int barX = x + 14;
        int barY = y + 5;
        int barWidth = 36;
        int fillWidth = Math.max(1, Math.round(barWidth * progress));
        int fillColor = progress < 0.18F ? 0xD9B65E47 : 0xD91A1714;

        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 4, 0x78000000);
        graphics.fill(barX, barY, barX + barWidth, barY + 3, 0x88E4DAC3);
        graphics.fill(barX, barY, barX + fillWidth, barY + 3, fillColor);

        int wordColor = mark.word().equals(focusedWord) ? 0xFFFFD978 : 0xFFD7BE72;
        graphics.drawString(font, mark.word(), x, y + 1, wordColor, true);
        String time = shortTime(remainingTicks);
        graphics.drawString(font, time, x + ROW_WIDTH - font.width(time), y + 1, progress < 0.18F ? 0xFFFFC2B5 : 0xDDEADFC5, true);
    }

    private static String formatTime(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        long minutes = seconds / 60L;
        long restSeconds = seconds % 60L;
        return minutes + ":" + (restSeconds < 10L ? "0" : "") + restSeconds;
    }

    private static String shortTime(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds >= 60L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }
}
