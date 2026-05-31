package com.glyphbound.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.concurrent.CompletableFuture;

public class InkRealmChunkGenerator extends ChunkGenerator {
    public static final MapCodec<InkRealmChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, instance.stable(InkRealmChunkGenerator::new))
    );

    private static final int SEA_LEVEL = 62;

    public InkRealmChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return InkRealmGenerator.MIN_Y;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return InkRealmGenerator.isMainIslandColumn(x, z)
            ? InkRealmGenerator.surfaceYAt(x, z) + 1
            : SEA_LEVEL + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];

        for (int y = height.getMinBuildHeight(); y < height.getMinBuildHeight() + states.length; y++) {
            int index = y - height.getMinBuildHeight();
            states[index] = stateFor(x, z, y);
        }

        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int localX = 0; localX < 16; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = baseZ + localZ;
                for (int y = minY; y < maxY; y++) {
                    BlockState state = stateFor(worldX, worldZ, y);
                    if (!state.isAir()) {
                        chunk.setBlockState(pos.set(localX, y, localZ), state, false);
                        oceanFloor.update(localX, y, localZ, state);
                        worldSurface.update(localX, y, localZ, state);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving carving) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor height) {
        return InkRealmGenerator.spawnPos().getY();
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, net.minecraft.core.BlockPos pos) {
    }

    private static BlockState stateFor(int x, int z, int y) {
        if (y == InkRealmGenerator.MIN_Y) {
            return Blocks.BEDROCK.defaultBlockState();
        }

        if (InkRealmGenerator.isMainIslandColumn(x, z)) {
            int surfaceY = InkRealmGenerator.surfaceYAt(x, z);
            if (y > surfaceY) {
                return y <= SEA_LEVEL ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            if (y == surfaceY) {
                return InkRealmGenerator.surfaceStateAt(x, z);
            }
            return InkRealmGenerator.underSurfaceStateAt(x, z, surfaceY - y);
        }

        return y <= SEA_LEVEL ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
}
