package com.glyphbound.effect;

import com.aozainkmc.api.AozaiInkApi;
import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffMetadata;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTarget;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.api.event.InkMarkBeforeAttachEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class SealGlyphEvents {
    private static final String SEAL_WORD = "印";
    private static final String CLEANSE_WORD = "净";
    private static final String SPRING_WORD = "泉";
    private static final String REST_WORD = "息";
    private static final String HEART_WORD = "心";
    private static final String LIFE_WARD_WORD = "命";
    private static final String RESCUE_WORD = "救";
    private static final String FORCE_WORD = "力";
    private static final String RAGE_WORD = "怒";
    private static final int MAX_DISTANCE = 10;
    private static final long TIMEOUT_TICKS = 1200L;
    private static final int MAX_ACTIVE_SEALS_PER_PLAYER_DIMENSION = 3;
    private static final long CLEANSE_REENTRY_COOLDOWN_TICKS = 1200L;
    private static final int REST_STILL_TICKS = 60;
    private static final ResourceLocation SEAL_HEART_MAX_HEALTH = id("seal_heart_max_health");
    private static final ResourceLocation SEAL_RAGE_ATTACK_DAMAGE = id("seal_rage_attack_damage");
    private static final Set<String> SEALABLE_WORDS = Set.of(
        "心", "命", "救", "息", "忍", "坚", "稳", "隐", "力", "怒", "净", "泉"
    );
    private static final Set<String> SYNTHETIC_BODY_MARK_SEALS = Set.of("忍", "坚", "稳", "隐");

    private static final Map<UUID, PendingSeal> pendingSeals = new HashMap<>();
    private static final Map<UUID, ActiveSeal> activeSeals = new HashMap<>();
    private static final Map<UUID, Map<UUID, SealHeartContribution>> sealHeartContributions = new HashMap<>();
    private static final Map<UUID, Long> sealGoldenBodyUntil = new HashMap<>();
    private static final Map<UUID, SealRageState> sealRageStates = new HashMap<>();

    private record PendingSeal(BlockPos selectedBlock, InkStaffTier staffTier, ResourceKey<Level> dimension, long createdGameTime) {
    }

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
        private final Map<UUID, Integer> salvationUses = new HashMap<>();
        private final Map<UUID, Long> cleanseCooldowns = new HashMap<>();
        private final Map<UUID, Long> springNextTriggers = new HashMap<>();
        private final Map<UUID, Long> heartNextTriggers = new HashMap<>();
        private final Map<UUID, Long> rescueCleanseNextTriggers = new HashMap<>();
        private final Map<UUID, Long> syntheticMarkNextRefresh = new HashMap<>();
        private final Map<UUID, SealRestState> restStates = new HashMap<>();

        private ActiveSeal(UUID owner, String word, BlockPos pos, InkStaffTier staffTier, ResourceKey<Level> dimension, long createdGameTime) {
            this.owner = owner;
            this.word = word;
            this.pos = pos.immutable();
            this.staffTier = staffTier;
            this.dimension = dimension;
            this.createdGameTime = createdGameTime;
            this.expiresAt = createdGameTime + sealDurationTicks(word, staffTier);
        }
    }

    private record SealRestState(double x, double y, double z, long stillSince) {
        private boolean samePosition(ServerPlayer player) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            return dx * dx + dy * dy + dz * dz <= 0.0025D;
        }
    }

    private static final class SealRageState {
        private String word = FORCE_WORD;
        private InkStaffTier tier = InkStaffTier.WOOD;
        private long activeUntil;
        private double defenseDamage;
        private double attackDamage;
        private int hitCount;
    }

    private record SealHeartContribution(double bonusHealth, long until) {
    }

    private SealGlyphEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkBeforeAttach(InkMarkBeforeAttachEvent event) {
        ServerPlayer player = event.player();
        InkMark mark = event.mark();
        PendingSeal pending = pendingSeals.get(player.getUUID());

        if (pending != null) {
            handleSecondGlyph(event, pending);
            return;
        }

        if (!SEAL_WORD.equals(mark.word())) {
            return;
        }
        if (mark.target().type() != InkTargetType.MARKER) {
            event.setCanceled(true);
            event.requestCloseInput("印: 请用铭刻阵写在方块上");
            return;
        }
        startSeal(event);
    }

    private static void startSeal(InkMarkBeforeAttachEvent event) {
        ServerPlayer player = event.player();
        long gameTime = player.serverLevel().getGameTime();
        BlockPos pos = resolveSealAnchor(player.serverLevel(), BlockPos.of(event.mark().target().packedBlockPos()));
        pendingSeals.put(player.getUUID(), new PendingSeal(pos, event.staffTier(), player.level().dimension(), gameTime));

        event.setCanceled(true);
        event.setConsumeOnCancel(true);
        event.requestOpenCastInput("印: 方碑已定，请书写入印之字");
    }

    private static void handleSecondGlyph(InkMarkBeforeAttachEvent event, PendingSeal seal) {
        ServerPlayer player = event.player();
        event.setCanceled(true);
        event.setConsumeOnCancel(true);

        if (!seal.dimension().equals(player.level().dimension())) {
            event.requestCloseInput("印: 异维度，封印中断");
            destroySeal(player);
            return;
        }
        if (player.distanceToSqr(seal.selectedBlock().getX() + 0.5D, seal.selectedBlock().getY() + 0.5D, seal.selectedBlock().getZ() + 0.5D)
            > (double) MAX_DISTANCE * MAX_DISTANCE) {
            event.requestCloseInput("印: 距封印方碑过远，封印中断");
            destroySeal(player);
            return;
        }

        String word = event.mark().word();
        if (SEALABLE_WORDS.contains(word) && sealSpecAvailable(word, seal.staffTier())) {
            createActiveSeal(player, seal, word);
            event.requestCloseInput(word + "印已成");
        } else if (SEALABLE_WORDS.contains(word)) {
            event.requestCloseInput(word + "印无法被当前魔杖稳定施放");
        } else {
            event.requestCloseInput("此字不可入印");
        }
        destroySeal(player);
    }

    private static void createActiveSeal(ServerPlayer player, PendingSeal pending, String word) {
        long gameTime = player.serverLevel().getGameTime();
        activeSeals.entrySet().removeIf(entry -> {
            ActiveSeal seal = entry.getValue();
            return seal.owner.equals(player.getUUID())
                && seal.dimension.equals(pending.dimension())
                && seal.pos.equals(pending.selectedBlock());
        });

        ActiveSeal seal = new ActiveSeal(player.getUUID(), word, pending.selectedBlock(), pending.staffTier(), pending.dimension(), gameTime);
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
            if (player.distanceToSqr(seal.selectedBlock().getX() + 0.5D, seal.selectedBlock().getY() + 0.5D, seal.selectedBlock().getZ() + 0.5D)
                > (double) MAX_DISTANCE * MAX_DISTANCE) {
                player.displayClientMessage(Component.literal("印: 距封印方碑过远，封印中断"), true);
                return true;
            }
            return false;
        });
        activeSeals.entrySet().removeIf(entry -> tickActiveSeal(event, entry.getValue(), gameTime));
        tickSealPlayerAttributes(event, gameTime);
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
        if (!sealAnchorStillExists(level, seal.pos)) {
            if (owner != null) {
                owner.displayClientMessage(Component.literal("封印方碑已毁"), true);
            }
            return true;
        }

        if (gameTime % 5L == 0L) {
            level.sendParticles(ParticleTypes.SQUID_INK, seal.pos.getX() + 0.5D, seal.pos.getY() + 1.1D, seal.pos.getZ() + 0.5D, 2, 0.25D, 0.25D, 0.25D, 0.0D);
        }

        if (CLEANSE_WORD.equals(seal.word) && gameTime % 10L == 0L) {
            tickCleanseSeal(level, seal, gameTime);
        } else if (SPRING_WORD.equals(seal.word) && gameTime % 20L == 0L) {
            tickSpringSeal(level, seal, gameTime);
        } else if (REST_WORD.equals(seal.word) && gameTime % 20L == 0L) {
            tickRestSeal(level, seal, gameTime);
        } else if (HEART_WORD.equals(seal.word) && gameTime % 10L == 0L) {
            tickHeartSeal(level, seal, gameTime);
        } else if (RESCUE_WORD.equals(seal.word) && gameTime % 20L == 0L) {
            tickRescueSeal(level, seal, gameTime);
        } else if (SYNTHETIC_BODY_MARK_SEALS.contains(seal.word) && gameTime % 10L == 0L) {
            tickSyntheticBodySeal(level, seal, gameTime);
        } else if ((FORCE_WORD.equals(seal.word) || RAGE_WORD.equals(seal.word)) && gameTime % 10L == 0L) {
            tickRageSeal(level, seal, gameTime);
        }
        return false;
    }

    private static void tickCleanseSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        double radius = sealRadius(seal.staffTier);
        Set<UUID> currentlyInside = new HashSet<>();
        for (ServerPlayer player : playersInside(level, seal, radius)) {
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

    private static void tickSpringSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        SpringSealSpec spec = springSealSpec(seal.staffTier);
        for (ServerPlayer player : playersInside(level, seal, spec.radius())) {
            long next = seal.springNextTriggers.getOrDefault(player.getUUID(), 0L);
            if (next > gameTime) {
                continue;
            }
            healFromSeal(player, spec.healAmount());
            seal.springNextTriggers.put(player.getUUID(), gameTime + spec.intervalTicks());
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.35D, 0.2D, 0.35D, 0.03D);
        }
        seal.springNextTriggers.entrySet().removeIf(entry -> gameTime - entry.getValue() > spec.intervalTicks() * 2L);
    }

    private static void tickRestSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        RestSealSpec spec = restSealSpec(seal.staffTier);
        Set<UUID> currentlyInside = new HashSet<>();
        for (ServerPlayer player : playersInside(level, seal, sealRadius(seal.staffTier))) {
            currentlyInside.add(player.getUUID());
            SealRestState state = seal.restStates.get(player.getUUID());
            if (state == null || !state.samePosition(player)) {
                seal.restStates.put(player.getUUID(), new SealRestState(player.getX(), player.getY(), player.getZ(), gameTime));
                continue;
            }
            if (gameTime - state.stillSince < REST_STILL_TICKS || gameTime % spec.intervalTicks() != 0L) {
                continue;
            }
            healFromSeal(player, spec.healAmount());
            if (player.getFoodData().getFoodLevel() < 20) {
                player.getFoodData().eat(spec.foodAmount(), spec.saturation());
            }
            level.sendParticles(ParticleTypes.COMPOSTER, player.getX(), player.getY() + 1.0D, player.getZ(), 4, 0.25D, 0.25D, 0.25D, 0.01D);
        }
        seal.restStates.keySet().removeIf(playerId -> !currentlyInside.contains(playerId));
    }

    private static void tickHeartSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        HeartSealSpec spec = heartSealSpec(seal.staffTier);
        for (ServerPlayer player : playersInside(level, seal, sealRadius(seal.staffTier))) {
            sealHeartContributions
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(seal.id, new SealHeartContribution(spec.maxBonusHealth(), gameTime + 40L));
            long next = seal.heartNextTriggers.getOrDefault(player.getUUID(), 0L);
            if (next <= gameTime && player.getHealth() < player.getMaxHealth()) {
                player.heal(spec.healAmount());
                seal.heartNextTriggers.put(player.getUUID(), gameTime + spec.healIntervalTicks());
            }
        }
        seal.heartNextTriggers.entrySet().removeIf(entry -> gameTime - entry.getValue() > heartSealSpec(seal.staffTier).healIntervalTicks() * 2L);
    }

    private static void tickRescueSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        SalvationSealSpec spec = salvationSealSpec(seal.word, seal.staffTier);
        if (spec == null || spec.inkWoundCleanseAmount() <= 0.0F) {
            return;
        }
        for (ServerPlayer player : playersInside(level, seal, sealRadius(seal.staffTier))) {
            long next = seal.rescueCleanseNextTriggers.getOrDefault(player.getUUID(), 0L);
            if (next > gameTime) {
                continue;
            }
            float removed = BodyGlyphEvents.cleanseInkWound(player, spec.inkWoundCleanseAmount());
            if (removed > 0.0F) {
                player.displayClientMessage(Component.literal("救印: 消去 " + formatAmount(removed) + " 墨伤"), true);
                level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.02D);
            }
            seal.rescueCleanseNextTriggers.put(player.getUUID(), gameTime + spec.inkWoundCleanseIntervalTicks());
        }
        seal.rescueCleanseNextTriggers.entrySet().removeIf(entry -> gameTime - entry.getValue() > spec.inkWoundCleanseIntervalTicks() * 2L);
    }

    private static void tickSyntheticBodySeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        for (ServerPlayer player : playersInside(level, seal, sealRadius(seal.staffTier))) {
            long next = seal.syntheticMarkNextRefresh.getOrDefault(player.getUUID(), 0L);
            if (next > gameTime) {
                continue;
            }
            AozaiInkApi.marks().attach(new InkMark(
                seal.word,
                "",
                List.of("glyphbound:seal"),
                1.0F,
                seal.owner,
                InkTarget.player(level.dimension().location().toString(), player.getUUID()),
                gameTime,
                40L,
                InkStaffMetadata.source("glyphbound:seal", seal.staffTier)
            ));
            seal.syntheticMarkNextRefresh.put(player.getUUID(), gameTime + 20L);
        }
        seal.syntheticMarkNextRefresh.entrySet().removeIf(entry -> gameTime - entry.getValue() > 80L);
    }

    private static void tickRageSeal(ServerLevel level, ActiveSeal seal, long gameTime) {
        SealRageSpec spec = sealRageSpec(seal.word, seal.staffTier);
        if (spec == null) {
            return;
        }
        for (ServerPlayer player : playersInside(level, seal, sealRadius(seal.staffTier))) {
            SealRageState state = sealRageStates.computeIfAbsent(player.getUUID(), ignored -> new SealRageState());
            if (!seal.word.equals(state.word) || state.tier != seal.staffTier || state.activeUntil <= gameTime) {
                state.word = seal.word;
                state.tier = seal.staffTier;
                state.defenseDamage = 0.0D;
                state.attackDamage = 0.0D;
                state.hitCount = 0;
            }
            state.activeUntil = gameTime + 200L;
        }
    }

    private static void tickSealPlayerAttributes(ServerTickEvent.Post event, long gameTime) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                UUID id = player.getUUID();
                Map<UUID, SealHeartContribution> heartContributions = sealHeartContributions.get(id);
                double heartBonus = 0.0D;
                if (heartContributions != null) {
                    heartContributions.entrySet().removeIf(entry -> entry.getValue().until() <= gameTime);
                    for (SealHeartContribution contribution : heartContributions.values()) {
                        heartBonus = Math.max(heartBonus, contribution.bonusHealth());
                    }
                    if (heartContributions.isEmpty()) {
                        sealHeartContributions.remove(id);
                    }
                }
                GlyphAttributes.setTransientAmount(player, Attributes.MAX_HEALTH, SEAL_HEART_MAX_HEALTH, heartBonus, AttributeModifier.Operation.ADD_VALUE);
                if (heartBonus == 0.0D) {
                    if (player.getHealth() > player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }

                SealRageState rage = sealRageStates.get(id);
                double attackBonus = 0.0D;
                if (rage != null && rage.activeUntil > gameTime) {
                    SealRageSpec spec = sealRageSpec(rage.word, rage.tier);
                    if (spec != null) {
                        attackBonus = Math.min(spec.attackCap(), rage.attackDamage * spec.attackPerDamage());
                    }
                } else {
                    sealRageStates.remove(id);
                }
                GlyphAttributes.setTransientAmount(player, Attributes.ATTACK_DAMAGE, SEAL_RAGE_ATTACK_DAMAGE, attackBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (handleSealGoldenBody(player, event, gameTime)) {
            return;
        }
        if (handleSealSalvation(player, event, gameTime)) {
            return;
        }

        SealRageState state = sealRageStates.get(player.getUUID());
        if (state == null || state.activeUntil <= gameTime) {
            return;
        }
        SealRageSpec spec = sealRageSpec(state.word, state.tier);
        if (spec == null || event.getNewDamage() <= 0.0F) {
            return;
        }
        double reduction = Math.min(spec.defenseCap(), state.defenseDamage * spec.defensePerDamage());
        if (reduction > 0.0D) {
            event.setNewDamage((float) (event.getNewDamage() * (1.0D - reduction)));
        }
        float finalDamage = event.getNewDamage();
        state.defenseDamage += finalDamage;
        state.attackDamage += finalDamage;
        state.hitCount++;
        if (spec.shockwave() && state.hitCount >= 10) {
            state.hitCount = 0;
            releaseSealShockwave(player, spec);
        }
    }

    private static boolean handleSealGoldenBody(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        if (sealGoldenBodyUntil.getOrDefault(player.getUUID(), 0L) <= gameTime) {
            return false;
        }
        if (event.getNewDamage() > 0.0F) {
            event.setNewDamage(0.0F);
            if (player.getHealth() < 1.0F) {
                player.setHealth(1.0F);
            }
        }
        return true;
    }

    private static boolean handleSealSalvation(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        if (event.getNewDamage() < player.getHealth()) {
            return false;
        }
        ActiveSeal seal = bestSalvationSeal(player, gameTime);
        if (seal == null) {
            return false;
        }
        SalvationSealSpec spec = salvationSealSpec(seal.word, seal.staffTier);
        if (spec == null) {
            return false;
        }
        int used = seal.salvationUses.getOrDefault(player.getUUID(), 0);
        if (used >= spec.maxTriggers()) {
            return false;
        }
        int remaining = Math.max(0, spec.maxTriggers() - used - 1);
        seal.salvationUses.put(player.getUUID(), used + 1);
        event.setNewDamage(Math.max(0.0F, player.getHealth() - 1.0F));
        if (RESCUE_WORD.equals(seal.word) && spec.goldenBodyTicks() > 0) {
            sealGoldenBodyUntil.put(player.getUUID(), Math.max(sealGoldenBodyUntil.getOrDefault(player.getUUID(), 0L), gameTime + spec.goldenBodyTicks()));
            player.invulnerableTime = Math.max(player.invulnerableTime, spec.goldenBodyTicks());
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, spec.goldenBodyTicks(), 4, true, true));
            player.displayClientMessage(Component.literal("救印: 金身护命，剩余 " + remaining + " 次"), true);
        } else {
            player.displayClientMessage(Component.literal("命印: 锁住一线生机，剩余 " + remaining + " 次"), true);
        }
        if (remaining == 0) {
            player.displayClientMessage(Component.literal(seal.word + "印护命次数已尽"), true);
        }
        return true;
    }

    private static ActiveSeal bestSalvationSeal(ServerPlayer player, long gameTime) {
        ActiveSeal best = null;
        for (ActiveSeal seal : activeSeals.values()) {
            if (!(LIFE_WARD_WORD.equals(seal.word) || RESCUE_WORD.equals(seal.word))
                || seal.expiresAt <= gameTime
                || !seal.dimension.equals(player.level().dimension())) {
                continue;
            }
            SalvationSealSpec spec = salvationSealSpec(seal.word, seal.staffTier);
            if (spec == null || seal.salvationUses.getOrDefault(player.getUUID(), 0) >= spec.maxTriggers()) {
                continue;
            }
            if (player.distanceToSqr(seal.pos.getX() + 0.5D, seal.pos.getY() + 0.5D, seal.pos.getZ() + 0.5D) > sealRadius(seal.staffTier) * sealRadius(seal.staffTier)) {
                continue;
            }
            if (best == null
                || RESCUE_WORD.equals(seal.word) && !RESCUE_WORD.equals(best.word)
                || seal.word.equals(best.word) && staffRank(seal.staffTier) > staffRank(best.staffTier)) {
                best = seal;
            }
        }
        return best;
    }

    private static void releaseSealShockwave(ServerPlayer player, SealRageSpec spec) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(spec.shockwaveRadius());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.push(dx / len * spec.shockwaveStrength(), 0.25D, dz / len * spec.shockwaveStrength());
            if (spec.shockwaveDamage() > 0.0F) {
                target.hurt(player.damageSources().magic(), spec.shockwaveDamage());
            }
        }
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.8D, player.getZ(), 32, spec.shockwaveRadius() * 0.4D, 0.25D, spec.shockwaveRadius() * 0.4D, 0.02D);
    }

    private static Set<ServerPlayer> playersInside(ServerLevel level, ActiveSeal seal, double radius) {
        Set<ServerPlayer> result = new HashSet<>();
        AABB bounds = new AABB(seal.pos).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds)) {
            if (player.distanceToSqr(seal.pos.getX() + 0.5D, seal.pos.getY() + 0.5D, seal.pos.getZ() + 0.5D) <= radius * radius) {
                result.add(player);
            }
        }
        return result;
    }

    private static void healFromSeal(ServerPlayer player, float amount) {
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(amount);
        }
    }

    private static BlockPos resolveSealAnchor(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return pos.immutable();
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isAir()) {
            return below.immutable();
        }
        return pos.immutable();
    }

    private static boolean sealAnchorStillExists(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isAir();
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (pendingSeals.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.literal("印: 封印中断"), true);
            }
            removeActiveSeals(player.getUUID());
            clearPlayerSealAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingSeals.remove(event.getEntity().getUUID());
        removeActiveSeals(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerSealAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pendingSeals.clear();
        activeSeals.clear();
        sealHeartContributions.clear();
        sealGoldenBodyUntil.clear();
        sealRageStates.clear();
    }

    private static void destroySeal(ServerPlayer player) {
        pendingSeals.remove(player.getUUID());
    }

    private static void removeActiveSeals(UUID owner) {
        activeSeals.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
    }

    private static void clearPlayerSealAttributes(ServerPlayer player) {
        UUID id = player.getUUID();
        sealHeartContributions.remove(id);
        sealGoldenBodyUntil.remove(id);
        sealRageStates.remove(id);
        GlyphAttributes.remove(player, Attributes.MAX_HEALTH, SEAL_HEART_MAX_HEALTH);
        GlyphAttributes.remove(player, Attributes.ATTACK_DAMAGE, SEAL_RAGE_ATTACK_DAMAGE);
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

    private static long sealDurationTicks(String word, InkStaffTier tier) {
        if (SPRING_WORD.equals(word)) {
            return springSealSpec(tier).durationTicks();
        }
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

    private static boolean sealSpecAvailable(String word, InkStaffTier tier) {
        if (RAGE_WORD.equals(word)) {
            return sealRageSpec(word, tier) != null;
        }
        if (RESCUE_WORD.equals(word)) {
            return tier != InkStaffTier.WOOD;
        }
        return SEALABLE_WORDS.contains(word);
    }

    private static HeartSealSpec heartSealSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new HeartSealSpec(10.0D, 1.0F, ticksSeconds(5));
            case STONE -> new HeartSealSpec(12.0D, 1.0F, ticksSeconds(5));
            case COPPER -> new HeartSealSpec(16.0D, 1.0F, ticksSeconds(4));
            case IRON -> new HeartSealSpec(20.0D, 1.0F, ticksSeconds(4));
            case GOLD -> new HeartSealSpec(20.0D, 1.0F, ticksSeconds(3));
            case DIAMOND -> new HeartSealSpec(20.0D, 2.0F, ticksSeconds(4));
            case NETHERITE -> new HeartSealSpec(20.0D, 2.0F, ticksSeconds(3));
        };
    }

    private static SalvationSealSpec salvationSealSpec(String word, InkStaffTier tier) {
        if (LIFE_WARD_WORD.equals(word)) {
            return switch (tier) {
                case WOOD -> new SalvationSealSpec(2, 0, 0.0F, 0L);
                case STONE -> new SalvationSealSpec(3, 0, 0.0F, 0L);
                case COPPER -> new SalvationSealSpec(3, 0, 0.0F, 0L);
                case IRON -> new SalvationSealSpec(4, 0, 0.0F, 0L);
                case GOLD -> new SalvationSealSpec(4, 0, 0.0F, 0L);
                case DIAMOND -> new SalvationSealSpec(5, 0, 0.0F, 0L);
                case NETHERITE -> new SalvationSealSpec(6, 0, 0.0F, 0L);
            };
        }
        if (RESCUE_WORD.equals(word)) {
            return switch (tier) {
                case WOOD -> null;
                case STONE -> new SalvationSealSpec(1, ticksSecondsInt(3), 2.0F, ticksSeconds(5));
                case COPPER -> new SalvationSealSpec(2, ticksSecondsInt(4), 3.0F, ticksSeconds(5));
                case IRON -> new SalvationSealSpec(2, ticksSecondsInt(5), 4.0F, ticksSeconds(4));
                case GOLD -> new SalvationSealSpec(2, ticksSecondsInt(5), 5.0F, ticksSeconds(3));
                case DIAMOND -> new SalvationSealSpec(3, ticksSecondsInt(5), 6.0F, ticksSeconds(4));
                case NETHERITE -> new SalvationSealSpec(4, ticksSecondsInt(5), 8.0F, ticksSeconds(3));
            };
        }
        return null;
    }

    private static SpringSealSpec springSealSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new SpringSealSpec(5, ticksSeconds(20), 5.0F, ticksMinutes(4));
            case STONE -> new SpringSealSpec(7, ticksSeconds(18), 7.0F, ticksMinutes(5));
            case COPPER -> new SpringSealSpec(9, ticksSeconds(16), 8.0F, ticksMinutes(6));
            case IRON -> new SpringSealSpec(12, ticksSeconds(14), 10.0F, ticksMinutes(7));
            case GOLD -> new SpringSealSpec(14, ticksSeconds(12), 12.0F, ticksMinutes(6));
            case DIAMOND -> new SpringSealSpec(18, ticksSeconds(10), 14.0F, ticksMinutes(9));
            case NETHERITE -> new SpringSealSpec(24, ticksSeconds(8), 16.0F, ticksMinutes(10));
        };
    }

    private static RestSealSpec restSealSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new RestSealSpec(ticksSeconds(4), 1.0F, 1, 0.15F);
            case STONE -> new RestSealSpec(ticksSeconds(4), 1.0F, 1, 0.20F);
            case COPPER -> new RestSealSpec(ticksSeconds(3), 1.25F, 1, 0.25F);
            case IRON -> new RestSealSpec(ticksSeconds(3), 1.5F, 1, 0.30F);
            case GOLD -> new RestSealSpec(ticksSeconds(2), 1.5F, 1, 0.35F);
            case DIAMOND -> new RestSealSpec(ticksSeconds(3), 2.0F, 2, 0.45F);
            case NETHERITE -> new RestSealSpec(ticksSeconds(2), 2.0F, 2, 0.55F);
        };
    }

    private static SealRageSpec sealRageSpec(String word, InkStaffTier tier) {
        if (FORCE_WORD.equals(word)) {
            return switch (tier) {
                case WOOD -> new SealRageSpec(0.0D, 0.20D, 0.0D, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case STONE -> new SealRageSpec(0.0D, 0.30D, 0.0D, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case COPPER -> new SealRageSpec(0.0D, 0.40D, 0.0D, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case IRON -> new SealRageSpec(0.0D, 0.50D, 0.0D, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case GOLD -> new SealRageSpec(0.0D, 0.60D, 0.0D, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case DIAMOND -> new SealRageSpec(0.0D, 0.60D, 0.0D, 0.005D, true, 3.0D, 1.0D, 0.0F);
                case NETHERITE -> new SealRageSpec(0.0D, 0.60D, 0.0D, 0.005D, true, 3.0D, 1.2D, 0.0F);
            };
        }
        if (RAGE_WORD.equals(word)) {
            return switch (tier) {
                case WOOD, STONE, COPPER, IRON, GOLD -> null;
                case DIAMOND -> new SealRageSpec(0.65D, 1.25D, 0.01D, 0.05D, true, 4.0D, 1.8D, 2.0F);
                case NETHERITE -> new SealRageSpec(0.70D, 1.50D, 0.01D, 0.05D, true, 4.0D, 2.0D, 2.0F);
            };
        }
        return null;
    }

    private static int staffRank(InkStaffTier tier) {
        return tier.ordinal();
    }

    private static long ticksSeconds(int seconds) {
        return seconds * 20L;
    }

    private static int ticksSecondsInt(int seconds) {
        return seconds * 20;
    }

    private static long ticksMinutes(int minutes) {
        return ticksSeconds(minutes * 60);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, path);
    }

    private static String formatAmount(float amount) {
        if (Math.abs(amount - Math.round(amount)) < 0.05F) {
            return Integer.toString(Math.round(amount));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", amount);
    }

    private record SpringSealSpec(double radius, long intervalTicks, float healAmount, long durationTicks) {
    }

    private record RestSealSpec(long intervalTicks, float healAmount, int foodAmount, float saturation) {
    }

    private record HeartSealSpec(double maxBonusHealth, float healAmount, long healIntervalTicks) {
    }

    private record SalvationSealSpec(int maxTriggers, int goldenBodyTicks, float inkWoundCleanseAmount, long inkWoundCleanseIntervalTicks) {
    }

    private record SealRageSpec(
        double defenseCap,
        double attackCap,
        double defensePerDamage,
        double attackPerDamage,
        boolean shockwave,
        double shockwaveRadius,
        double shockwaveStrength,
        float shockwaveDamage
    ) {
    }
}
