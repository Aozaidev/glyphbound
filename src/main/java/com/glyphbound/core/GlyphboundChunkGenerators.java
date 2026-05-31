package com.glyphbound.core;

import com.glyphbound.Glyphbound;
import com.glyphbound.world.InkRealmChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GlyphboundChunkGenerators {
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, Glyphbound.MOD_ID);

    public static void register(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
    }

    static {
        CHUNK_GENERATORS.register(
            "ink_realm",
            () -> InkRealmChunkGenerator.CODEC
        );
    }

    private GlyphboundChunkGenerators() {
    }
}
