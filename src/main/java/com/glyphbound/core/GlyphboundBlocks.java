package com.glyphbound.core;

import com.glyphbound.Glyphbound;
import com.glyphbound.block.InkRealmPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GlyphboundBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Glyphbound.MOD_ID);

    public static final DeferredBlock<Block> INK_STONE = BLOCKS.register(
        "ink_stone",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F)
            .sound(SoundType.STONE))
    );

    public static final DeferredBlock<Block> POLISHED_INK_STONE = BLOCKS.register(
        "polished_ink_stone",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F)
            .sound(SoundType.STONE))
    );

    public static final DeferredBlock<Block> INK_BRICK = BLOCKS.register(
        "ink_brick",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 6.0F)
            .sound(SoundType.STONE))
    );

    public static final DeferredBlock<Block> INK_DIRT = BLOCKS.register(
        "ink_dirt",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(0.5F)
            .sound(SoundType.GRAVEL))
    );

    public static final DeferredBlock<Block> INK_SAND = BLOCKS.register(
        "ink_sand",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(0.5F)
            .sound(SoundType.SAND))
    );

    public static final DeferredBlock<Block> PARCHMENT_BLOCK = BLOCKS.register(
        "parchment_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND)
            .strength(0.5F)
            .sound(SoundType.WOOL))
    );

    public static final DeferredBlock<Block> PARCHMENT_LANTERN = BLOCKS.register(
        "parchment_lantern",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND)
            .strength(0.3F)
            .sound(SoundType.WOOL)
            .lightLevel(s -> 15)
            .noOcclusion())
    );

    public static final DeferredBlock<Block> BOUNDARY_STELE = BLOCKS.register(
        "boundary_stele",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.STONE)
            .lightLevel(s -> 7)
            .noOcclusion())
    );

    public static final DeferredBlock<Block> INK_REALM_PORTAL = BLOCKS.register(
        "ink_realm_portal",
        () -> new InkRealmPortalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.STONE)
            .lightLevel(s -> 11)
            .noCollission()
            .noOcclusion()
            .noLootTable())
    );

    private GlyphboundBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
