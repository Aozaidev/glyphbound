package com.glyphbound;

import com.aozainkmc.api.InkGlyphRegistry;
import com.aozainkmc.api.InkGlyphClientBehaviorRegistry;
import com.glyphbound.core.GlyphboundBlocks;
import com.glyphbound.core.GlyphboundChunkGenerators;
import com.glyphbound.core.GlyphboundItems;
import com.glyphbound.core.GlyphboundWords;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Glyphbound.MOD_ID)
public final class Glyphbound {
    public static final String MOD_ID = "glyphbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Glyphbound(IEventBus modBus) {
        GlyphboundBlocks.register(modBus);
        GlyphboundChunkGenerators.register(modBus);
        GlyphboundItems.register(modBus);
        InkGlyphRegistry.registerAll(GlyphboundWords.IMPLEMENTED);
        InkGlyphClientBehaviorRegistry.registerCloseAfterRecognize(GlyphboundWords.CLOSE_AFTER_RECOGNIZE);
        LOGGER.info("Glyphbound loaded");
    }
}
