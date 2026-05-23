package com.glyphbound.guide;

import com.glyphbound.Glyphbound;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vazkii.patchouli.api.PatchouliAPI;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class GlyphboundGuideBookEvents {
    private static final String RECEIVED_GUIDE_KEY = "glyphbound.received_guide_book.v6";
    private static final ResourceLocation GUIDE_ID = ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "guide");

    private GlyphboundGuideBookEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(RECEIVED_GUIDE_KEY)) {
            return;
        }

        data.putBoolean(RECEIVED_GUIDE_KEY, true);
        ItemStack book = createGuideBook();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private static ItemStack createGuideBook() {
        ItemStack book = PatchouliAPI.get().getBookStack(GUIDE_ID);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("执笔引").withStyle(ChatFormatting.GOLD));
        return book;
    }
}
