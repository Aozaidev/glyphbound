package com.glyphbound;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Glyphbound.MOD_ID)
public final class Glyphbound {
    public static final String MOD_ID = "glyphbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Glyphbound() {
        LOGGER.info("Glyphbound loaded as an empty gameplay addon shell");
    }
}
