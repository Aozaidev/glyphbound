package com.glyphbound.world;

import com.glyphbound.core.GlyphboundBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class InkRealmGenerator {
    public static final int SEA_LEVEL = 62;
    public static final int MIN_Y = -64;
    private static final int ISLAND_CENTER_X = 0;
    private static final int ISLAND_CENTER_Z = 0;
    private static final int ISLAND_RADIUS = 24;
    private static final double SHORELINE_START = 0.74D;
    private static final int INNER_BASE_HEIGHT = 1;
    private static final int INNER_RISE = 3;
    private static final int PAVILION_RADIUS = 4;
    private static final int PAVILION_OPENING_RADIUS = 1;

    private InkRealmGenerator() {
    }

    public static BlockPos spawnPos() {
        return new BlockPos(ISLAND_CENTER_X, pavilionFloorY() + 1, ISLAND_CENTER_Z + 1);
    }

    public static boolean isMainIslandColumn(int x, int z) {
        return distanceFromCenter(x, z) <= ISLAND_RADIUS;
    }

    public static boolean isShoreline(int x, int z) {
        return distanceFromCenter(x, z) / ISLAND_RADIUS >= SHORELINE_START;
    }

    public static BlockState surfaceStateAt(int x, int z) {
        double dist = distanceFromCenter(x, z);
        if (isShoreline(x, z)) {
            return GlyphboundBlocks.INK_SAND.get().defaultBlockState();
        }
        if (isParchmentField(x, z)) {
            return GlyphboundBlocks.PARCHMENT_BLOCK.get().defaultBlockState();
        }
        return GlyphboundBlocks.INK_DIRT.get().defaultBlockState();
    }

    public static BlockState underSurfaceStateAt(int x, int z, int depth) {
        if (depth <= 2 && !isShoreline(x, z)) {
            return GlyphboundBlocks.INK_DIRT.get().defaultBlockState();
        }
        return GlyphboundBlocks.INK_STONE.get().defaultBlockState();
    }

    public static int surfaceYAt(int x, int z) {
        double dist = distanceFromCenter(x, z);
        double normalizedDistance = Math.min(1.0D, dist / ISLAND_RADIUS);
        if (normalizedDistance >= SHORELINE_START) {
            double shoreT = (1.0D - normalizedDistance) / (1.0D - SHORELINE_START);
            return SEA_LEVEL + (shoreT > 0.45D ? 1 : 0);
        }

        double innerT = 1.0D - normalizedDistance / SHORELINE_START;
        double falloff = innerT * innerT;
        double noise = simplex2(x * 0.08D, z * 0.08D) * 1.2D * Math.min(1.0D, innerT * 2.0D);
        int height = (int)Math.round(INNER_BASE_HEIGHT + falloff * INNER_RISE + noise);
        return SEA_LEVEL + Math.max(1, height);
    }

    public static void generateMainIsland(ServerLevel level) {
        for (int x = ISLAND_CENTER_X - ISLAND_RADIUS; x <= ISLAND_CENTER_X + ISLAND_RADIUS; x++) {
            for (int z = ISLAND_CENTER_Z - ISLAND_RADIUS; z <= ISLAND_CENTER_Z + ISLAND_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ISLAND_RADIUS) {
                    fillSeaColumn(level, x, z);
                    continue;
                }

                int surfaceY = surfaceYAt(x, z);

                fillIslandColumn(level, x, z, surfaceY, dist);
            }
        }

        int pavilionFloorY = pavilionFloorY();
        clearForecourt(level);
        preparePavilionTerrace(level, pavilionFloorY);
        placeParchmentPath(level);
        placePathLanterns(level);
        placeBoundaryPavilion(level, pavilionFloorY);
        placeInkBamboo(level);
    }

    private static void fillIslandColumn(ServerLevel level, int x, int z, int surfaceY, double distFromCenter) {
        BlockState seaState = Blocks.WATER.defaultBlockState();

        for (int y = level.getMinBuildHeight(); y <= surfaceY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (y == level.getMinBuildHeight()) {
                level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
            } else if (y == surfaceY) {
                level.setBlock(pos, surfaceStateAt(x, z), 3);
            } else {
                level.setBlock(pos, underSurfaceStateAt(x, z, surfaceY - y), 3);
            }
        }

        for (int y = surfaceY + 1; y <= level.getMaxBuildHeight(); y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }

        for (int y = level.getMinBuildHeight(); y <= SEA_LEVEL; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, seaState, 3);
            }
        }
    }

    private static void fillSeaColumn(ServerLevel level, int x, int z) {
        BlockState seaState = Blocks.WATER.defaultBlockState();
        for (int y = level.getMinBuildHeight(); y <= SEA_LEVEL; y++) {
            BlockState state = y == level.getMinBuildHeight() ? Blocks.BEDROCK.defaultBlockState() : seaState;
            level.setBlock(new BlockPos(x, y, z), state, 3);
        }
        for (int y = SEA_LEVEL + 1; y <= level.getMaxBuildHeight(); y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void clearForecourt(ServerLevel level) {
        int clearFromY = pavilionFloorY();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = clearFromY; y <= clearFromY + 22; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void preparePavilionTerrace(ServerLevel level, int floorY) {
        int groundY = floorY - 1;
        BlockState stoneState = GlyphboundBlocks.INK_STONE.get().defaultBlockState();
        BlockState parchmentState = GlyphboundBlocks.PARCHMENT_BLOCK.get().defaultBlockState();

        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (Math.sqrt(x * x + z * z) > 6.4D) {
                    continue;
                }
                for (int y = level.getMinBuildHeight(); y <= groundY; y++) {
                    BlockState state = y == level.getMinBuildHeight()
                        ? Blocks.BEDROCK.defaultBlockState()
                        : y == groundY ? parchmentState : stoneState;
                    level.setBlock(new BlockPos(ISLAND_CENTER_X + x, y, ISLAND_CENTER_Z + z), state, 3);
                }
            }
        }
    }

    private static void placeBoundaryPavilion(ServerLevel level, int floorY) {
        BlockState floorState = GlyphboundBlocks.POLISHED_INK_STONE.get().defaultBlockState();
        BlockState pillarState = GlyphboundBlocks.INK_BRICK.get().defaultBlockState();
        BlockState roofState = GlyphboundBlocks.INK_STONE.get().defaultBlockState();
        BlockState roofTrimState = GlyphboundBlocks.INK_BRICK.get().defaultBlockState();
        BlockState parchmentState = GlyphboundBlocks.PARCHMENT_BLOCK.get().defaultBlockState();
        BlockState lanternState = GlyphboundBlocks.PARCHMENT_LANTERN.get().defaultBlockState();

        for (int x = -PAVILION_RADIUS; x <= PAVILION_RADIUS; x++) {
            for (int z = -PAVILION_RADIUS; z <= PAVILION_RADIUS; z++) {
                BlockPos floorPos = new BlockPos(ISLAND_CENTER_X + x, floorY, ISLAND_CENTER_Z + z);
                level.setBlock(floorPos, Math.max(Math.abs(x), Math.abs(z)) == PAVILION_RADIUS ? floorState : parchmentState, 3);
            }
        }

        int[][] pillarOffsets = {
            {-3, -3}, {3, -3}, {-3, 3}, {3, 3}
        };
        for (int[] offset : pillarOffsets) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                level.setBlock(new BlockPos(ISLAND_CENTER_X + offset[0], y, ISLAND_CENTER_Z + offset[1]), pillarState, 3);
            }
            level.setBlock(new BlockPos(ISLAND_CENTER_X + offset[0], floorY + 5, ISLAND_CENTER_Z + offset[1]), lanternState, 3);
        }

        placeHollowRoofLayer(level, floorY + 5, 5, 2, roofTrimState);
        placeHollowRoofLayer(level, floorY + 6, 4, 2, roofState);
        placeHollowRoofLayer(level, floorY + 7, 3, 2, roofTrimState);

        int[][] liftedCorners = {
            {-5, -5}, {5, -5}, {-5, 5}, {5, 5}
        };
        for (int[] offset : liftedCorners) {
            level.setBlock(new BlockPos(ISLAND_CENTER_X + offset[0], floorY + 6, ISLAND_CENTER_Z + offset[1]), roofTrimState, 3);
            level.setBlock(new BlockPos(ISLAND_CENTER_X + offset[0], floorY + 7, ISLAND_CENTER_Z + offset[1]), roofTrimState, 3);
        }

        placeBoundaryStele(level, new BlockPos(ISLAND_CENTER_X, floorY + 1, ISLAND_CENTER_Z - 2));

        for (int x = -PAVILION_OPENING_RADIUS; x <= PAVILION_OPENING_RADIUS; x++) {
            for (int z = -PAVILION_OPENING_RADIUS; z <= PAVILION_OPENING_RADIUS; z++) {
                for (int y = floorY + 1; y <= floorY + 10; y++) {
                    BlockPos pos = new BlockPos(ISLAND_CENTER_X + x, y, ISLAND_CENTER_Z + z);
                    if (!level.getBlockState(pos).is(GlyphboundBlocks.BOUNDARY_STELE.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void placeHollowRoofLayer(ServerLevel level, int y, int radius, int openingRadius, BlockState state) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int max = Math.max(Math.abs(x), Math.abs(z));
                if (max != radius && max < radius - 1) {
                    continue;
                }
                if (Math.abs(x) <= openingRadius && Math.abs(z) <= openingRadius) {
                    continue;
                }
                level.setBlock(new BlockPos(ISLAND_CENTER_X + x, y, ISLAND_CENTER_Z + z), state, 3);
            }
        }
    }

    private static void placeBoundaryStele(ServerLevel level, BlockPos pos) {
        BlockState steleState = GlyphboundBlocks.BOUNDARY_STELE.get().defaultBlockState();
        level.setBlock(pos, steleState, 3);
        level.setBlock(pos.above(), steleState, 3);

        BlockPos base = pos.below();
        BlockState polishedState = GlyphboundBlocks.POLISHED_INK_STONE.get().defaultBlockState();
        level.setBlock(base, polishedState, 3);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos platformPos = base.offset(dx, 0, dz);
                level.setBlock(platformPos, polishedState, 3);
            }
        }
    }

    private static void placeParchmentPath(ServerLevel level) {
        BlockState pathState = GlyphboundBlocks.PARCHMENT_BLOCK.get().defaultBlockState();
        for (int z = ISLAND_CENTER_Z + 3; z <= ISLAND_CENTER_Z + 12; z++) {
            BlockPos pos = getSurfacePos(level, ISLAND_CENTER_X, z);
            if (pos != null) {
                level.setBlock(pos, pathState, 3);
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = ISLAND_CENTER_Z + 4; z <= ISLAND_CENTER_Z + 11; z++) {
                if (x == 0) continue;
                BlockPos pos = getSurfacePos(level, ISLAND_CENTER_X + x, z);
                if (pos != null) {
                    level.setBlock(pos, pathState, 3);
                }
            }
        }
    }

    private static void placePathLanterns(ServerLevel level) {
        BlockState lanternState = GlyphboundBlocks.PARCHMENT_LANTERN.get().defaultBlockState();

        int[][] lanternPositions = {
            {ISLAND_CENTER_X - 2, ISLAND_CENTER_Z + 7},
            {ISLAND_CENTER_X + 2, ISLAND_CENTER_Z + 7},
            {ISLAND_CENTER_X - 2, ISLAND_CENTER_Z + 11},
            {ISLAND_CENTER_X + 2, ISLAND_CENTER_Z + 11}
        };

        for (int[] offset : lanternPositions) {
            BlockPos pos = getSurfacePos(level, offset[0], offset[1]);
            if (pos != null) {
                level.setBlock(pos.above(), lanternState, 3);
            }
        }
    }

    private static void placeInkBamboo(ServerLevel level) {
        BlockState bambooState = Blocks.BAMBOO_PLANKS.defaultBlockState();

        int[][] bambooClusters = {
            {ISLAND_CENTER_X - 8, ISLAND_CENTER_Z - 6},
            {ISLAND_CENTER_X - 7, ISLAND_CENTER_Z - 7},
            {ISLAND_CENTER_X - 9, ISLAND_CENTER_Z - 5},
            {ISLAND_CENTER_X + 10, ISLAND_CENTER_Z - 8},
            {ISLAND_CENTER_X + 11, ISLAND_CENTER_Z - 7},
        };

        for (int[] offset : bambooClusters) {
            BlockPos pos = getSurfacePos(level, offset[0], offset[1]);
            if (pos != null) {
                level.setBlock(pos.above(), bambooState, 3);
                if (Math.random() > 0.5D) {
                    level.setBlock(pos.above(2), bambooState, 3);
                }
            }
        }
    }

    private static BlockPos getSurfacePos(ServerLevel level, int x, int z) {
        for (int y = level.getMaxBuildHeight(); y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static double distanceFromCenter(int x, int z) {
        double dx = x - ISLAND_CENTER_X;
        double dz = z - ISLAND_CENTER_Z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isParchmentField(int x, int z) {
        double dist = distanceFromCenter(x, z);
        if (dist <= 9.5D) {
            return true;
        }
        if (Math.abs(x - ISLAND_CENTER_X) <= 2 && z >= ISLAND_CENTER_Z - 3 && z <= ISLAND_CENTER_Z + 15) {
            return true;
        }
        double brush = simplex2(x * 0.055D + 40.0D, z * 0.055D - 17.0D);
        return dist < ISLAND_RADIUS * 0.78D && brush > 0.45D;
    }

    private static double simplex2(double x, double z) {
        double n = Math.sin(x * 1.7 + z * 2.3) * 0.5;
        n += Math.sin(x * 3.1 - z * 1.1) * 0.25;
        n += Math.cos(x * 0.7 + z * 3.7) * 0.125;
        return Mth.clamp(n, -1.0, 1.0);
    }

    private static int pavilionFloorY() {
        int min = Integer.MAX_VALUE;
        for (int x = -PAVILION_RADIUS - 1; x <= PAVILION_RADIUS + 1; x++) {
            for (int z = -PAVILION_RADIUS - 1; z <= PAVILION_RADIUS + 1; z++) {
                min = Math.min(min, surfaceYAt(ISLAND_CENTER_X + x, ISLAND_CENTER_Z + z));
            }
        }
        return min + 1;
    }
}
