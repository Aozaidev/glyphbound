package com.glyphbound.effect;

import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.event.InkMarkBeforeAttachEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.StaffTierUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class GlyphUnlockCheckEvents {
    private static final Map<String, Integer> GLYPH_MIN_RANK = Map.ofEntries(
        Map.entry("心", 0), Map.entry("命", 0), Map.entry("净", 0), Map.entry("忍", 0),
        Map.entry("明", 0), Map.entry("稳", 1), Map.entry("息", 1), Map.entry("力", 1), Map.entry("坚", 1), Map.entry("魄", 1),
        Map.entry("救", 2), Map.entry("泉", 2), Map.entry("脉", 2), Map.entry("印", 2), Map.entry("山", 2), Map.entry("裂", 2),
        Map.entry("怒", 3), Map.entry("墨", 1), Map.entry("隐", 3)
    );

    private GlyphUnlockCheckEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInkMarkBeforeAttach(InkMarkBeforeAttachEvent event) {
        String word = event.mark().word();
        Integer minRank = GLYPH_MIN_RANK.get(word);
        if (minRank == null) {
            return;
        }

        InkStaffTier tier = event.staffTier();
        int currentRank = StaffTierUtils.tierRank(tier);
        if (currentRank >= minRank) {
            return;
        }

        ServerPlayer player = event.player();
        String minTierName = getMinTierName(minRank);
        player.displayClientMessage(
            Component.literal(word + ": 需要" + minTierName + "以上魔杖"),
            true
        );
        event.setCanceled(true);
    }

    private static String getMinTierName(int rank) {
        return switch (rank) {
            case 0 -> "木";
            case 1 -> "石";
            case 2 -> "铁";
            case 3 -> "钻石";
            case 4 -> "下界合金";
            default -> "?";
        };
    }
}
