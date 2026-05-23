package com.glyphbound.core;

import com.glyphbound.Glyphbound;
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

    private GlyphboundItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
