package com.glyphbound.core;

import com.glyphbound.Glyphbound;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GlyphboundItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Glyphbound.MOD_ID);

    public static final DeferredItem<Item> INK_CORE = ITEMS.register(
        "ink_core",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> INK_STONE = ITEMS.register(
        "ink_stone",
        () -> new BlockItem(GlyphboundBlocks.INK_STONE.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> POLISHED_INK_STONE = ITEMS.register(
        "polished_ink_stone",
        () -> new BlockItem(GlyphboundBlocks.POLISHED_INK_STONE.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> INK_BRICK = ITEMS.register(
        "ink_brick",
        () -> new BlockItem(GlyphboundBlocks.INK_BRICK.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> INK_DIRT = ITEMS.register(
        "ink_dirt",
        () -> new BlockItem(GlyphboundBlocks.INK_DIRT.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> INK_SAND = ITEMS.register(
        "ink_sand",
        () -> new BlockItem(GlyphboundBlocks.INK_SAND.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> PARCHMENT_BLOCK = ITEMS.register(
        "parchment_block",
        () -> new BlockItem(GlyphboundBlocks.PARCHMENT_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> PARCHMENT_LANTERN = ITEMS.register(
        "parchment_lantern",
        () -> new BlockItem(GlyphboundBlocks.PARCHMENT_LANTERN.get(), new Item.Properties())
    );

    private GlyphboundItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
