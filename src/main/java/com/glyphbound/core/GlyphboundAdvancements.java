package com.glyphbound.core;

import com.aozainkmc.api.InkStaffs;
import com.glyphbound.Glyphbound;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class GlyphboundAdvancements {
    public static final String ROOT = "root";
    public static final String FIRST_GLYPH = "first_glyph";
    public static final String ALL_BODY_GLYPHS = "all_body_glyphs";
    public static final String FIRST_SEAL = "first_seal";
    public static final String INK_FIELD_ACTIVE = "ink_field_active";
    public static final String MOUNTAIN_WALL = "mountain_wall";
    public static final String RIFT_TRAP = "rift_trap";
    public static final String FIRST_CALAMITY = "first_calamity";
    public static final String CALAMITY_VICTORY = "calamity_victory";
    public static final String FIRST_TRIBULATION = "first_tribulation";
    public static final String TRIBULATION_VICTORY = "tribulation_victory";
    public static final String NETHERITE_FULL = "netherite_full";
    public static final String INK_REALM_ENTRY = "ink_realm_entry";
    public static final String INK_REALM_RETURN = "ink_realm_return";
    public static final String INK_CORE_DROP = "ink_core_drop";

    private static final Map<String, String> BODY_CRITERIA = Map.ofEntries(
        Map.entry("心", "heart"),
        Map.entry("命", "life_ward"),
        Map.entry("救", "rescue"),
        Map.entry("息", "rest"),
        Map.entry("忍", "endurance"),
        Map.entry("坚", "firm"),
        Map.entry("稳", "steady"),
        Map.entry("隐", "hidden"),
        Map.entry("明", "bright"),
        Map.entry("净", "cleanse"),
        Map.entry("力", "force"),
        Map.entry("怒", "rage"),
        Map.entry("脉", "pulse"),
        Map.entry("魄", "soul")
    );

    private GlyphboundAdvancements() {
    }

    public static void award(ServerPlayer player, String path) {
        award(player, path, "done");
    }

    public static void award(ServerPlayer player, String path, String criterion) {
        AdvancementHolder advancement = player.server.getAdvancements().get(id(path));
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void awardGlyphWritten(ServerPlayer player, String word) {
        award(player, ROOT);
        award(player, FIRST_GLYPH);
        String bodyCriterion = BODY_CRITERIA.get(word);
        if (bodyCriterion != null) {
            award(player, ALL_BODY_GLYPHS, bodyCriterion);
        }
    }

    public static boolean hasInkStaff(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (InkStaffs.isStaff(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasInkCore(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(GlyphboundItems.INK_CORE.get())) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> bodyWords() {
        return BODY_CRITERIA.keySet();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, path);
    }
}
