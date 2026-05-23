package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.core.event.InkBlockTargetSelectedEvent;
import com.aozainkmc.core.event.InkMarkBeforeAttachEvent;
import com.glyphbound.Glyphbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class SealGlyphEvents {
    private static final String SEAL_WORD = "印";
    private static final String TOKEN_PREFIX = "glyphbound:seal:";
    private static final int MAX_DISTANCE = 10;
    private static final long TIMEOUT_TICKS = 1200L;

    private static final Map<UUID, PendingSeal> pendingSeals = new HashMap<>();

    private enum SealPhase {
        SELECT_BLOCK,
        WAIT_SECOND_GLYPH
    }

    private record PendingSeal(
        SealPhase phase,
        String token,
        BlockPos selectedBlock,
        InkStaffTier staffTier,
        ResourceKey<Level> dimension,
        long createdGameTime
    ) {}

    private SealGlyphEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkBeforeAttach(InkMarkBeforeAttachEvent event) {
        ServerPlayer player = event.player();
        InkMark mark = event.mark();

        PendingSeal seal = pendingSeals.get(player.getUUID());

        if (seal != null && seal.phase() == SealPhase.WAIT_SECOND_GLYPH) {
            handleSecondGlyph(event, seal);
            return;
        }

        if (SEAL_WORD.equals(mark.word()) && mark.target().type() == InkTargetType.PLAYER) {
            handleSealStart(event, seal);
        }
    }

    private static void handleSealStart(InkMarkBeforeAttachEvent event, PendingSeal existingSeal) {
        ServerPlayer player = event.player();

        if (existingSeal != null) {
            destroySeal(player);
        }

        String token = TOKEN_PREFIX + player.getUUID();
        long gameTime = player.serverLevel().getGameTime();

        PendingSeal seal = new PendingSeal(
            SealPhase.SELECT_BLOCK,
            token,
            null,
            event.staffTier(),
            player.level().dimension(),
            gameTime
        );
        pendingSeals.put(player.getUUID(), seal);

        event.setCanceled(true);
        event.requestBlockTarget(token, "印: 选择封印目标方块", MAX_DISTANCE);
    }

    private static void handleSecondGlyph(InkMarkBeforeAttachEvent event, PendingSeal seal) {
        ServerPlayer player = event.player();

        if (!seal.dimension().equals(player.level().dimension())) {
            event.setCanceled(true);
            event.requestCloseInput("印: 异维度，封印中断");
            destroySeal(player);
            return;
        }

        BlockPos target = seal.selectedBlock();
        double distSqr = player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (distSqr > (double) MAX_DISTANCE * MAX_DISTANCE) {
            event.setCanceled(true);
            event.requestCloseInput("印: 距封印方碑过远，封印中断");
            destroySeal(player);
            return;
        }

        event.setCanceled(true);
        event.requestCloseInput("此字不可入印");
        destroySeal(player);
    }

    @SubscribeEvent
    public static void onBlockTargetSelected(InkBlockTargetSelectedEvent event) {
        String token = event.token();
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return;
        }

        ServerPlayer player = event.player();
        PendingSeal seal = pendingSeals.get(player.getUUID());
        if (seal == null || seal.phase() != SealPhase.SELECT_BLOCK) {
            return;
        }

        if (!token.equals(seal.token())) {
            return;
        }

        if (!seal.dimension().equals(player.level().dimension())) {
            destroySeal(player);
            return;
        }

        PendingSeal updated = new PendingSeal(
            SealPhase.WAIT_SECOND_GLYPH,
            seal.token(),
            event.pos(),
            seal.staffTier(),
            seal.dimension(),
            seal.createdGameTime()
        );
        pendingSeals.put(player.getUUID(), updated);

        event.requestOpenCastInput("印: 方块已选，请书写入印之字");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        pendingSeals.entrySet().removeIf(entry -> {
            PendingSeal seal = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            if (!seal.dimension().equals(player.level().dimension())) {
                player.displayClientMessage(Component.literal("印: 异维度，封印中断"), true);
                return true;
            }
            if (gameTime - seal.createdGameTime() > TIMEOUT_TICKS) {
                player.displayClientMessage(Component.literal("印: 封印超时"), true);
                return true;
            }
            if (seal.selectedBlock() != null && player.distanceToSqr(
                seal.selectedBlock().getX() + 0.5D,
                seal.selectedBlock().getY() + 0.5D,
                seal.selectedBlock().getZ() + 0.5D
            ) > (double) MAX_DISTANCE * MAX_DISTANCE) {
                player.displayClientMessage(Component.literal("印: 距封印方碑过远，封印中断"), true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (pendingSeals.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.literal("印: 封印中断"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingSeals.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pendingSeals.clear();
    }

    private static void destroySeal(ServerPlayer player) {
        pendingSeals.remove(player.getUUID());
    }
}
