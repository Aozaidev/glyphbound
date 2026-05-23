package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.core.event.InkBlockTargetSelectedEvent;
import com.aozainkmc.core.event.InkMarkBeforeAttachEvent;
import com.glyphbound.Glyphbound;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class SealGlyphEvents {
    private static final String SEAL_WORD = "印";
    private static final String CLEANSE_WORD = "净";
    private static final String TOKEN_PREFIX = "glyphbound:seal:";
    private static final int MAX_DISTANCE = 10;
    private static final long TIMEOUT_TICKS = 1200L;
    private static final int MAX_ACTIVE_SEALS_PER_PLAYER_DIMENSION = 3;
    private static final long CLEANSE_REENTRY_COOLDOWN_TICKS = 1200L;
    private static final Set<String> SEALABLE_WORDS = Set.of(
        "心", "忍", "坚", "稳", "隐", "明", "脉", "力", "怒", "净"
    );

    private static final Map<UUID, PendingSeal> pendingSeals = new HashMap<>();
    private static final Map<UUID, ActiveSeal> activeSeals = new HashMap<>();

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

    private static final class ActiveSeal {
        private final UUID id = UUID.randomUUID();
        private final UUID owner;
        private final String word;
        private final BlockPos pos;
        private final InkStaffTier staffTier;
        private final ResourceKey<Level> dimension;
        private final long createdGameTime;
        private final long expiresAt;
        private final Set<UUID> occupants = new HashSet<>();
        private final Map<UUID, Long> cleanseCooldowns = new HashMap<>();

        private ActiveSeal(UUID owner, String word, BlockPos pos, InkStaffTier staffTier, ResourceKey<Level> dimension, long createdGameTime) {
            this.owner = owner;
            this.word = word;
            this.pos = pos.immutable();
            this.staffTier = staffTier;
            this.dimension = dimension;
            this.createdGameTime = createdGameTime;
            this.expiresAt = createdGameTime + sealDurationTicks(staffTier);
        }
    }

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
        event.setConsumeOnCancel(true);
        event.requestBlockTarget(token, "印: 选择封印目标方块", MAX_DISTANCE);
    }

    private static void handleSecondGlyph(InkMarkBeforeAttachEvent event, PendingSeal seal) {
        ServerPlayer player = event.player();

        if (!seal.dimension().equals(player.level().dimension())) {
            event.setCanceled(true);
            event.setConsumeOnCancel(true);
            event.requestCloseInput("印: 异维度，封印中断");
            destroySeal(player);
            return;
        }

        BlockPos target = seal.selectedBlock();
        double distSqr = player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (distSqr > (double) MAX_DISTANCE * MAX_DISTANCE) {
            event.setCanceled(true);
            event.setConsumeOnCancel(true);
            event.requestCloseInput("印: 距封印方碑过远，封印中断");
            destroySeal(player);
            return;
        }

        event.setCanceled(true);
        event.setConsumeOnCancel(true);
        if (CLEANSE_WORD.equals(event.mark().word())) {
            createCleanseSeal(player, seal);
            event.requestCloseInput("净印已成");
        } else if (SEALABLE_WORDS.contains(event.mark().word())) {
            event.requestCloseInput(event.mark().word() + "印尚未接入");
        } else {
            event.requestCloseInput("此字不可入印");
        }
        destroySeal(player);
    }

    private static void createCleanseSeal(ServerPlayer player, PendingSeal pending) {
        long gameTime = player.serverLevel().getGameTime();
        activeSeals.entrySet().removeIf(entry -> {
            ActiveSeal seal = entry.getValue();
            return seal.owner.equals(player.getUUID())
                && seal.dimension.equals(pending.dimension())
                && seal.pos.equals(pending.selectedBlock());
        });

        ActiveSeal seal = new ActiveSeal(
            player.getUUID(),
            CLEANSE_WORD,
            pending.selectedBlock(),
            pending.staffTier(),
            pending.dimension(),
            gameTime
        );
        activeSeals.put(seal.id, seal);
        enforceSealLimit(player, seal.dimension);
    }

    private static void enforceSealLimit(ServerPlayer owner, ResourceKey<Level> dimension) {
        while (activeSeals.values().stream().filter(seal -> seal.owner.equals(owner.getUUID()) && seal.dimension.equals(dimension)).count()
            > MAX_ACTIVE_SEALS_PER_PLAYER_DIMENSION) {
            ActiveSeal oldest = activeSeals.values().stream()
                .filter(seal -> seal.owner.equals(owner.getUUID()) && seal.dimension.equals(dimension))
                .min((left, right) -> Long.compare(left.createdGameTime, right.createdGameTime))
                .orElse(null);
            if (oldest == null) {
                return;
            }
            activeSeals.remove(oldest.id);
            owner.displayClientMessage(Component.literal("印: 最旧方碑消散"), true);
        }
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
        activeSeals.entrySet().removeIf(entry -> tickActiveSeal(event, entry.getValue(), gameTime));
    }

    private static boolean tickActiveSeal(ServerTickEvent.Post event, ActiveSeal seal, long gameTime) {
        ServerLevel level = event.getServer().getLevel(seal.dimension);
        if (level == null) {
            return true;
        }
        ServerPlayer owner = event.getServer().getPlayerList().getPlayer(seal.owner);
        if (owner != null && !owner.level().dimension().equals(seal.dimension)) {
            owner.displayClientMessage(Component.literal("印: 封印随你离界消散"), true);
            return true;
        }
        if (gameTime >= seal.expiresAt) {
            if (owner != null) {
                owner.displayClientMessage(Component.literal(seal.word + "印已散"), true);
            }
            return true;
        }
        if (level.getBlockState(seal.pos).isAir()) {
            if (owner != null) {
                owner.displayClientMessage(Component.literal("封印方碑已毁"), true);
            }
            return true;
        }

        if (gameTime % 5L == 0L) {
            level.sendParticles(
                ParticleTypes.SQUID_INK,
                seal.pos.getX() + 0.5D,
                seal.pos.getY() + 1.1D,
                seal.pos.getZ() + 0.5D,
                2,
                0.25D,
                0.25D,
                0.25D,
                0.0D
            );
        }

        if (CLEANSE_WORD.equals(seal.word) && gameTime % 10L == 0L) {
            tickCleanseSeal(level, seal, gameTime);
        }
        return false;
    }

    private static void tickCleanseSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        double radius = sealRadius(seal.staffTier);
        Set<UUID> currentlyInside = new HashSet<>();
        AABB bounds = new AABB(seal.pos).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds)) {
            if (player.distanceToSqr(seal.pos.getX() + 0.5D, seal.pos.getY() + 0.5D, seal.pos.getZ() + 0.5D) > radius * radius) {
                continue;
            }
            currentlyInside.add(player.getUUID());
            if (!seal.occupants.contains(player.getUUID())) {
                tryCleanseFromSeal(player, seal, gameTime);
            }
        }
        seal.occupants.clear();
        seal.occupants.addAll(currentlyInside);
        seal.cleanseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static void tryCleanseFromSeal(ServerPlayer player, ActiveSeal seal, long gameTime) {
        if (seal.cleanseCooldowns.getOrDefault(player.getUUID(), 0L) > gameTime) {
            return;
        }
        Holder<MobEffect> removable = null;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            if (!effect.value().isBeneficial()) {
                removable = effect;
                break;
            }
        }
        seal.cleanseCooldowns.put(player.getUUID(), gameTime + CLEANSE_REENTRY_COOLDOWN_TICKS);
        if (removable != null && player.removeEffect(removable)) {
            player.getFoodData().addExhaustion(0.8F);
            player.displayClientMessage(Component.literal("净印: 清去一缕污秽"), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (pendingSeals.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.literal("印: 封印中断"), true);
            }
            removeActiveSeals(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingSeals.remove(event.getEntity().getUUID());
        removeActiveSeals(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pendingSeals.clear();
        activeSeals.clear();
    }

    private static void destroySeal(ServerPlayer player) {
        pendingSeals.remove(player.getUUID());
    }

    private static void removeActiveSeals(UUID owner) {
        activeSeals.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
    }

    private static int sealRadius(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> 5;
            case STONE -> 7;
            case COPPER -> 9;
            case IRON -> 12;
            case GOLD -> 14;
            case DIAMOND -> 18;
            case NETHERITE -> 24;
        };
    }

    private static long sealDurationTicks(InkStaffTier tier) {
        int seconds = switch (tier) {
            case WOOD -> 180;
            case STONE -> 240;
            case COPPER -> 324;
            case IRON -> 540;
            case GOLD -> 270;
            case DIAMOND, NETHERITE -> 720;
        };
        return seconds * 20L;
    }
}
