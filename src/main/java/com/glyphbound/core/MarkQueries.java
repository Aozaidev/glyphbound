package com.glyphbound.core;

import com.aozainkmc.api.AozaiInkApi;
import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkMarkStore;
import com.aozainkmc.api.InkTarget;
import com.aozainkmc.api.InkTargetType;
import com.glyphbound.Glyphbound;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class MarkQueries {

    private MarkQueries() {}

    private static InkMarkStore store() {
        try {
            return AozaiInkApi.marks();
        } catch (IllegalStateException e) {
            Glyphbound.LOGGER.warn("Aozai Ink API not installed — skipping mark query");
            return null;
        }
    }

    public static boolean hasActivePlayerMark(Player player, String word, long gameTime) {
        return !getActivePlayerMarks(player, word, gameTime).isEmpty();
    }

    public static List<InkMark> getActivePlayerMarks(Player player, String word, long gameTime) {
        InkMarkStore store = store();
        if (store == null) return List.of();
        InkTarget target = InkTarget.player(
            player.level().dimension().location().toString(),
            player.getUUID()
        );
        List<InkMark> result = new ArrayList<>();
        for (InkMark mark : store.marksOn(target)) {
            if (mark.word().equals(word) && !mark.expired(gameTime)) {
                result.add(mark);
            }
        }
        return result;
    }

    public static List<InkMark> getActiveMarkerMarks(ServerLevel level, String word, long gameTime) {
        InkMarkStore store = store();
        if (store == null) return List.of();
        String dim = level.dimension().location().toString();
        List<InkMark> result = new ArrayList<>();
        for (InkMark mark : store.allMarks()) {
            if (mark.target().type() == InkTargetType.MARKER
                && mark.word().equals(word)
                && mark.target().dimension().equals(dim)
                && !mark.expired(gameTime)) {
                result.add(mark);
            }
        }
        return result;
    }

    public static BlockPos blockPosFromPacked(long packed) {
        return BlockPos.of(packed);
    }

    public static boolean isEntityNearAnyMarker(ServerLevel level, Player player, String word, double radius, long gameTime) {
        List<InkMark> markers = getActiveMarkerMarks(level, word, gameTime);
        for (InkMark marker : markers) {
            BlockPos pos = blockPosFromPacked(marker.target().packedBlockPos());
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            if (player.distanceToSqr(cx, cy, cz) <= radius * radius) {
                return true;
            }
        }
        return false;
    }
}
