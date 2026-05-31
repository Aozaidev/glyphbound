package com.glyphbound.core;

import com.aozainkmc.api.InkStaffTier;

import java.util.Map;
import java.util.Set;

public final class StaffTierUtils {
    private static final Map<String, Integer> GLYPH_MIN_RANK = Map.ofEntries(
        Map.entry("心", 0), Map.entry("命", 0), Map.entry("净", 0), Map.entry("忍", 0),
        Map.entry("明", 0), Map.entry("稳", 1), Map.entry("息", 1), Map.entry("力", 1), Map.entry("坚", 1), Map.entry("魄", 1),
        Map.entry("救", 2), Map.entry("泉", 2), Map.entry("脉", 2), Map.entry("印", 2), Map.entry("山", 2), Map.entry("裂", 2),
        Map.entry("怒", 3), Map.entry("墨", 3), Map.entry("隐", 3),
        Map.entry("界", 4)
    );

    private StaffTierUtils() {}

    public static int tierRank(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> 0;
            case STONE -> 1;
            case COPPER, IRON -> 2;
            case GOLD, DIAMOND -> 3;
            case NETHERITE -> 4;
        };
    }

    public static boolean canUseGlyph(String word, InkStaffTier tier) {
        Integer minRank = GLYPH_MIN_RANK.get(word);
        if (minRank == null) {
            return true;
        }
        return tierRank(tier) >= minRank;
    }

    public static String tierName(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> "木";
            case STONE -> "石";
            case COPPER -> "铜";
            case IRON -> "铁";
            case GOLD -> "金";
            case DIAMOND -> "钻石";
            case NETHERITE -> "下界合金";
        };
    }
}
