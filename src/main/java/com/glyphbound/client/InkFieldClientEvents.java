package com.glyphbound.client;

import com.glyphbound.Glyphbound;
import com.glyphbound.effect.WorldGlyphEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID, value = Dist.CLIENT)
public final class InkFieldClientEvents {
    private static final ResourceLocation INK_GRAY_SHADER = ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "shaders/post/ink_gray.json");
    private static boolean shaderActive;

    private InkFieldClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean shouldRender = minecraft.player != null
            && minecraft.level != null
            && WorldGlyphEvents.isInkFieldOwnerActive(
                minecraft.player.getUUID(),
                minecraft.level.dimension().location().toString(),
                minecraft.level.getGameTime()
            );

        if (shouldRender && !shaderActive) {
            minecraft.gameRenderer.loadEffect(INK_GRAY_SHADER);
            shaderActive = true;
        } else if (!shouldRender && shaderActive) {
            minecraft.gameRenderer.shutdownEffect();
            shaderActive = false;
        }
    }
}
