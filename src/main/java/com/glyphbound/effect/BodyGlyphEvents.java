package com.glyphbound.effect;

import com.aozainkmc.api.AozaiInkApi;
import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffMetadata;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTarget;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.api.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphAttributes;
import com.glyphbound.core.GlyphboundWords;
import com.glyphbound.core.MarkQueries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class BodyGlyphEvents {
    private static final ResourceLocation HEART_MAX_HEALTH = id("heart_max_health");
    private static final ResourceLocation FIRM_TOUGHNESS = id("firm_armor_toughness");
    private static final ResourceLocation FIRM_KNOCKBACK = id("firm_knockback_resistance");
    private static final ResourceLocation FIRM_SPEED_COST = id("firm_speed_cost");
    private static final ResourceLocation RAGE_ATTACK_DAMAGE = id("rage_attack_damage");
    private static final ResourceLocation STEADY_KNOCKBACK = id("steady_knockback_resistance");
    private static final ResourceLocation STEADY_EXPLOSION = id("steady_explosion_resistance");
    private static final ResourceLocation STEADY_MOVEMENT = id("steady_movement_efficiency");
    private static final ResourceLocation STEADY_WATER = id("steady_water_efficiency");

    private static final double FIRM_TOUGHNESS_BONUS = 4.0D;
    private static final double FIRM_KNOCKBACK_BONUS = 0.25D;
    private static final double FIRM_SPEED_MULTIPLIER = -0.12D;
    private static final double STEADY_KNOCKBACK_BONUS = 0.35D;
    private static final double STEADY_EXPLOSION_BONUS = 0.45D;
    private static final double STEADY_MOVEMENT_BONUS = 0.45D;
    private static final double STEADY_WATER_BONUS = 0.35D;

    private static final int REST_STILL_TICKS = 60;
    private static final int REST_TICK_INTERVAL = 40;
    private static final int REST_COMBAT_LOCKOUT_TICKS = 100;
    private static final int SALVATION_FATIGUE_TICKS = 120;
    private static final int INK_WOUND_DELAY_TICKS = 40;
    private static final int INK_WOUND_SETTLE_INTERVAL = 20;
    private static final float INK_WOUND_SETTLE_AMOUNT = 1.5F;
    private static final int RAGE_HITS_PER_SHOCKWAVE = 10;
    private static final int HIDDEN_REFRESH_TICKS = 40;
    private static final int HIDDEN_MOB_FORGET_RADIUS = 5;
    private static final int BRIGHT_SENSE_INTERVAL = 40;
    private static final int BRIGHT_RADIUS = 8;
    private static final int PULSE_INTERVAL = 60;
    private static final int PULSE_RADIUS = 18;
    private static final int CLEANSE_WINDOW_TICKS = 200;
    private static final long SOUL_RECOVERY_WINDOW_TICKS = 20L * 60L * 10L;
    private static final float SOUL_XP_RESERVE_RATIO = 0.5F;

    private static final Map<UUID, HeartState> heartStates = new HashMap<>();
    private static final Map<UUID, PlayerRestState> restStates = new HashMap<>();
    private static final Map<UUID, Long> restBrokenUntil = new HashMap<>();
    private static final Map<UUID, Long> lastHurtAt = new HashMap<>();
    private static final Map<UUID, Long> salvationUsedUntil = new HashMap<>();
    private static final Map<UUID, Long> salvationGoldenBodyUntil = new HashMap<>();
    private static final Map<UUID, InkWound> inkWounds = new HashMap<>();
    private static final Set<UUID> settlingInkWounds = new HashSet<>();
    private static final Map<UUID, Long> hiddenBrokenUntil = new HashMap<>();
    private static final Map<UUID, CleansePressure> cleansePressure = new HashMap<>();
    private static final Map<UUID, RageState> rageStates = new HashMap<>();
    private static final Map<UUID, SoulRecovery> soulRecoveries = new HashMap<>();
    private static final Map<UUID, FirmPressure> firmPressures = new HashMap<>();
    private static final Map<UUID, Integer> steadyBigKnockbackCharges = new HashMap<>();

    private BodyGlyphEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkAttached(InkMarkAttachedEvent event) {
        InkMark mark = event.mark();
        if (!GlyphboundWords.BODY.contains(mark.word())) {
            return;
        }

        Glyphbound.LOGGER.debug("[{}] body glyph attached to {} ttl={}", mark.word(), mark.target().type(), mark.ttlTicks());
        if (mark.target().type() == InkTargetType.PLAYER && lifeSpec(mark.word(), InkStaffMetadata.tier(mark)) != null) {
            activateLife(mark);
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && "息".equals(mark.word())) {
            restBrokenUntil.remove(mark.target().entityUuid());
            restStates.remove(mark.target().entityUuid());
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && "隐".equals(mark.word())) {
            hiddenBrokenUntil.remove(mark.target().entityUuid());
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && "稳".equals(mark.word())) {
            steadyBigKnockbackCharges.put(mark.target().entityUuid(), steadySpec(InkStaffMetadata.tier(mark)).bigKnockbackCancels());
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && rageSpec(mark.word(), InkStaffMetadata.tier(mark)) != null) {
            activateRage(mark);
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && cleanseSpec(mark.word(), InkStaffMetadata.tier(mark)) != null) {
            ServerPlayer player = findPlayer(mark);
            if (player != null) {
                cleansePlayer(player, mark);
            }
            return;
        }
        if (mark.target().type() == InkTargetType.PLAYER && "魄".equals(mark.word())) {
            ServerPlayer player = findPlayer(mark);
            if (player != null) {
                recoverSoul(player, InkStaffMetadata.tier(mark));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % 100L == 0L) {
            AozaiInkApi.marks().pruneExpired(gameTime);
        }

        salvationUsedUntil.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        heartStates.entrySet().removeIf(entry -> entry.getValue().expiresAt <= gameTime);
        restBrokenUntil.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        salvationGoldenBodyUntil.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        cleansePressure.entrySet().removeIf(entry -> entry.getValue().expiresAt <= gameTime);
        soulRecoveries.entrySet().removeIf(entry -> entry.getValue().expiresAt <= gameTime || entry.getValue().consumed);
        firmPressures.entrySet().removeIf(entry -> entry.getValue().lastHitAt + 80L <= gameTime);

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                tickBodyAttributes(player, gameTime);
                tickRest(player, gameTime);
                tickInkWound(player, gameTime);
                tickHidden(player, level, gameTime);
                if (gameTime % BRIGHT_SENSE_INTERVAL == 0L) {
                    tickBrightSense(player, level, gameTime);
                }
                if (gameTime % PULSE_INTERVAL == 0L) {
                    tickPulse(player, level, gameTime);
                }
            }
        }
    }

    private static void tickBodyAttributes(ServerPlayer player, long gameTime) {
        HeartState heart = heartStates.get(player.getUUID());
        double heartBonus = heart != null && heart.expiresAt > gameTime ? heart.bonus : 0.0D;
        GlyphAttributes.setTransientAmount(player, Attributes.MAX_HEALTH, HEART_MAX_HEALTH, heartBonus, AttributeModifier.Operation.ADD_VALUE);
        if (heartBonus == 0.0D && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        boolean firm = hasActiveTimedMark(player, "坚", gameTime);
        float firmPower = glyphPower(player, "坚", gameTime);
        double firmPressureBonus = firmPressureBonus(player, gameTime);
        GlyphAttributes.setTransientAmount(player, Attributes.ARMOR_TOUGHNESS, FIRM_TOUGHNESS, firm ? FIRM_TOUGHNESS_BONUS * firmPower + firmPressureBonus : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        GlyphAttributes.setTransientAmount(player, Attributes.KNOCKBACK_RESISTANCE, FIRM_KNOCKBACK, firm ? FIRM_KNOCKBACK_BONUS * firmPower + firmPressureBonus * 0.03D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        GlyphAttributes.setTransientAmount(player, Attributes.MOVEMENT_SPEED, FIRM_SPEED_COST, firm ? FIRM_SPEED_MULTIPLIER * firmPower : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        RageState rage = rageStates.get(player.getUUID());
        double rageAttackBonus = rage != null && rage.activeUntil > gameTime ? rage.attackBonus() : 0.0D;
        GlyphAttributes.setTransientAmount(player, Attributes.ATTACK_DAMAGE, RAGE_ATTACK_DAMAGE, rageAttackBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        boolean steady = hasActiveTimedMark(player, "稳", gameTime);
        float steadyPower = glyphPower(player, "稳", gameTime);
        GlyphAttributes.setTransientAmount(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_KNOCKBACK, steady ? STEADY_KNOCKBACK_BONUS * steadyPower : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        GlyphAttributes.setTransientAmount(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY_EXPLOSION, steady ? STEADY_EXPLOSION_BONUS * steadyPower : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        GlyphAttributes.setTransientAmount(player, Attributes.MOVEMENT_EFFICIENCY, STEADY_MOVEMENT, steady ? STEADY_MOVEMENT_BONUS * steadyPower : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        GlyphAttributes.setTransientAmount(player, Attributes.WATER_MOVEMENT_EFFICIENCY, STEADY_WATER, steady ? STEADY_WATER_BONUS * steadyPower : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void tickRest(ServerPlayer player, long gameTime) {
        if (!hasActiveTimedMark(player, "息", gameTime)) {
            restStates.remove(player.getUUID());
            return;
        }
        if (restBrokenUntil.getOrDefault(player.getUUID(), 0L) > gameTime) {
            restStates.remove(player.getUUID());
            return;
        }
        if (lastHurtAt.getOrDefault(player.getUUID(), 0L) + REST_COMBAT_LOCKOUT_TICKS > gameTime) {
            breakRest(player, gameTime, "hurt lockout");
            return;
        }

        PlayerRestState state = restStates.computeIfAbsent(player.getUUID(), ignored -> PlayerRestState.moving(player.position(), gameTime));
        if (state.lastPosition.distanceToSqr(player.position()) > 0.0025D) {
            breakRest(player, gameTime, "movement");
            return;
        }

        RestSpec spec = restSpec(activeStaffTier(player, "息", gameTime));
        if (gameTime - state.stillSince < REST_STILL_TICKS || gameTime % spec.intervalTicks() != 0L) {
            return;
        }

        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < 20) {
            food.eat(spec.foodAmount(), spec.saturation());
            Glyphbound.LOGGER.debug("[息] restored food reserve for {}", player.getGameProfile().getName());
        } else if (player.getHealth() < player.getMaxHealth()) {
            player.heal(spec.healAmount());
            food.addExhaustion(spec.exhaustion());
            Glyphbound.LOGGER.debug("[息] converted quiet breath into natural healing for {}", player.getGameProfile().getName());
        }
    }

    private static void tickInkWound(ServerPlayer player, long gameTime) {
        if (salvationGoldenBodyUntil.getOrDefault(player.getUUID(), 0L) > gameTime) {
            return;
        }

        InkWound wound = inkWounds.get(player.getUUID());
        if (wound == null || gameTime < wound.startsAt || gameTime % INK_WOUND_SETTLE_INTERVAL != 0L) {
            return;
        }

        float amount = Math.min(INK_WOUND_SETTLE_AMOUNT, wound.amount);
        if (amount <= 0.0F) {
            inkWounds.remove(player.getUUID());
            return;
        }

        settlingInkWounds.add(player.getUUID());
        player.hurt(player.damageSources().magic(), amount);
        settlingInkWounds.remove(player.getUUID());

        wound.amount -= amount;
        if (wound.amount <= 0.05F || !player.isAlive()) {
            inkWounds.remove(player.getUUID());
        }
    }

    private static void tickHidden(ServerPlayer player, ServerLevel level, long gameTime) {
        boolean active = hasActiveTimedMark(player, "隐", gameTime)
            && hiddenBrokenUntil.getOrDefault(player.getUUID(), 0L) <= gameTime;
        if (!active) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, HIDDEN_REFRESH_TICKS, 0, true, false));
        HiddenSpec spec = hiddenSpec(activeStaffTier(player, "隐", gameTime));
        AABB area = player.getBoundingBox().inflate(spec.retargetRadius());
        for (Entity entity : level.getEntities(player, area)) {
            if (entity instanceof net.minecraft.world.entity.Mob mob
                && mob.getTarget() == player
                && mob.distanceTo(player) > spec.forgetRadius()) {
                mob.setTarget(null);
            }
        }
    }

    private static void tickBrightSense(ServerPlayer player, ServerLevel level, long gameTime) {
        if (!hasActiveTimedMark(player, "明", gameTime) || level.getBrightness(LightLayer.BLOCK, player.blockPosition()) > 7) {
            return;
        }

        int revealedMobs = 0;
        BrightSpec spec = brightSpec(activeStaffTier(player, "明", gameTime));
        int brightRadius = spec.radius();
        AABB area = player.getBoundingBox().inflate(brightRadius);
        if (spec.revealMobs()) {
            for (Entity entity : level.getEntities(player, area)) {
                if (entity instanceof LivingEntity living && entity instanceof Enemy) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, BRIGHT_SENSE_INTERVAL + 20, 0, true, false));
                    revealedMobs++;
                }
            }
        }

        int revealedBlocks = revealNearbyDarkClues(player, level, spec);
        if (revealedMobs > 0 || revealedBlocks > 0) {
            Glyphbound.LOGGER.debug("[明] revealed {} mobs and {} blocks near {}", revealedMobs, revealedBlocks, player.getGameProfile().getName());
        }
    }

    private static int revealNearbyDarkClues(ServerPlayer player, ServerLevel level, BrightSpec spec) {
        int revealed = 0;
        int radius = spec.radius();
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            if (revealed >= spec.maxBlocks() || center.distSqr(pos) > radius * radius) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isBrightClue(state, level.getBlockEntity(pos), spec)) {
                level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 1, 0.1D, 0.1D, 0.1D, 0.0D);
                revealed++;
            }
        }
        return revealed;
    }

    private static boolean isBrightClue(BlockState state, BlockEntity blockEntity, BrightSpec spec) {
        return spec.revealContainers() && blockEntity != null
            || spec.revealTraps() && (state.is(Blocks.TRIPWIRE) || state.is(Blocks.TRIPWIRE_HOOK))
            || spec.revealCommonOres() && (state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.IRON_ORES))
            || spec.revealRareOres() && (
                state.is(BlockTags.GOLD_ORES)
                    || state.is(BlockTags.REDSTONE_ORES)
                    || state.is(BlockTags.LAPIS_ORES)
                    || state.is(BlockTags.EMERALD_ORES)
                    || state.is(BlockTags.DIAMOND_ORES)
            );
    }

    private static void tickPulse(ServerPlayer player, ServerLevel level, long gameTime) {
        if (!hasActiveTimedMark(player, "脉", gameTime)) {
            return;
        }

        List<LivingEntity> entities = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(pulseSpec(activeStaffTier(player, "脉", gameTime)).radius()),
            entity -> entity != player && entity.isAlive()
        );
        if (entities.isEmpty()) {
            player.displayClientMessage(Component.literal("脉: 周围很静"), true);
            return;
        }

        PulseSummary summary = PulseSummary.from(player, entities);
        player.displayClientMessage(Component.literal("脉: " + summary.description(pulseSpec(activeStaffTier(player, "脉", gameTime)))), true);
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        long gameTime = target.level().getGameTime();
        if (target instanceof ServerPlayer player) {
            lastHurtAt.put(player.getUUID(), gameTime);
            breakRest(player, gameTime, "damage");
            if (handleGoldenBody(player, event, gameTime)) {
                return;
            }
            handleInkWound(player, event, gameTime);
            handleRageReduction(player, event, gameTime);
            handleSalvation(player, event, gameTime);
            recordFirmPressure(player, gameTime);
            recordRageDamage(player, event, gameTime);
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player) {
            breakRest(player, gameTime, "attack");
            breakHidden(player, gameTime);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerBodyState(player);
        }
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDroppedExperience() <= 0) {
            return;
        }

        int reserved = Math.max(1, Math.round(event.getDroppedExperience() * SOUL_XP_RESERVE_RATIO));
        event.setDroppedExperience(Math.max(0, event.getDroppedExperience() - reserved));
        SoulRecovery recovery = soulRecoveries.computeIfAbsent(
            player.getUUID(),
            ignored -> new SoulRecovery(player.level().getGameTime() + SOUL_RECOVERY_WINDOW_TICKS, player.level().dimension(), player.blockPosition())
        );
        recovery.experience += reserved;
        Glyphbound.LOGGER.debug("[魄] reserved {} xp from {}", reserved, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }

        SoulRecovery recovery = soulRecoveries.computeIfAbsent(
            player.getUUID(),
            ignored -> new SoulRecovery(player.level().getGameTime() + SOUL_RECOVERY_WINDOW_TICKS, player.level().dimension(), player.blockPosition())
        );
        recovery.expiresAt = player.level().getGameTime() + SOUL_RECOVERY_WINDOW_TICKS;
        recovery.deathDimension = player.level().dimension();
        recovery.deathPos = player.blockPosition();
        recovery.items.clear();
        for (ItemEntity item : event.getDrops()) {
            if (!item.getItem().isEmpty()) {
                recovery.items.add(new SoulItemRef(item.level().dimension(), item.getUUID()));
            }
        }
        Glyphbound.LOGGER.debug("[魄] tracked {} death drops for {}", recovery.items.size(), player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            clearPlayerBodyState(event.getOriginal().getUUID());
            clearPlayerBodyState(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearPlayerBodyState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerBodyState(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAllBodyState(event.getServer());
    }

    private static void handleSalvation(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        SalvationSpec spec = activeSalvationSpec(player, gameTime);
        if (spec == null || salvationUsedUntil.getOrDefault(player.getUUID(), 0L) > gameTime || event.getNewDamage() < player.getHealth()) {
            return;
        }

        long expiresAt = latestActiveMarkExpiry(player, spec.word(), gameTime);
        salvationUsedUntil.put(player.getUUID(), Math.max(expiresAt, gameTime + SALVATION_FATIGUE_TICKS));
        int goldenBodyTicks = spec.goldenBodyTicks();
        salvationGoldenBodyUntil.put(player.getUUID(), gameTime + goldenBodyTicks);
        if (spec.inkWoundClearRatio() > 0.0F) {
            cleanseInkWoundRatio(player, spec.inkWoundClearRatio());
        }
        event.setNewDamage(Math.max(0.0F, player.getHealth() - 1.0F));
        player.invulnerableTime = Math.max(player.invulnerableTime, goldenBodyTicks);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SALVATION_FATIGUE_TICKS, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SALVATION_FATIGUE_TICKS, 1, true, true));
        Glyphbound.LOGGER.debug("[{}] held {} at one heart and granted golden body", spec.word(), player.getGameProfile().getName());
    }

    private static boolean handleGoldenBody(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        if (salvationGoldenBodyUntil.getOrDefault(player.getUUID(), 0L) <= gameTime) {
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

    private static void handleInkWound(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        InkWoundSpec spec = activeInkWoundSpec(player, gameTime);
        if (settlingInkWounds.contains(player.getUUID()) || spec == null) {
            return;
        }

        float deferral = spec.deferral();
        float deferred = event.getNewDamage() * deferral;
        if (deferred < 0.5F) {
            return;
        }

        event.setNewDamage(event.getNewDamage() - deferred);
        InkWound wound = inkWounds.computeIfAbsent(player.getUUID(), ignored -> new InkWound(0.0F, gameTime + INK_WOUND_DELAY_TICKS));
        wound.amount = Math.min(spec.poolCap(), wound.amount + deferred);
        wound.startsAt = gameTime + INK_WOUND_DELAY_TICKS;
        Glyphbound.LOGGER.debug("[{}] deferred {} damage for {}", spec.word(), deferred, player.getGameProfile().getName());
    }

    private static void handleRageReduction(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        RageState rage = rageStates.get(player.getUUID());
        if (rage == null || rage.activeUntil <= gameTime) {
            return;
        }
        double reduction = rage.damageReduction();
        if (reduction <= 0.0D) {
            return;
        }

        float reducedDamage = (float) (event.getNewDamage() * (1.0D - reduction));
        event.setNewDamage(reducedDamage);
        Glyphbound.LOGGER.debug("[{}] reduced damage by {} for {}", rage.word, reduction, player.getGameProfile().getName());
    }

    private static void recordRageDamage(ServerPlayer player, LivingDamageEvent.Pre event, long gameTime) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        RageState rage = rageStates.get(player.getUUID());
        if (rage == null || rage.activeUntil <= gameTime) {
            return;
        }
        rage.addDamage(event.getNewDamage());
        if (event.getSource().getEntity() != null && event.getSource().getEntity() != player) {
            rage.hitCount++;
            while (rage.canShockwave() && rage.hitCount >= RAGE_HITS_PER_SHOCKWAVE) {
                rage.hitCount -= RAGE_HITS_PER_SHOCKWAVE;
                releaseRageShockwave(player, rage);
            }
        }
    }

    private static void recordFirmPressure(ServerPlayer player, long gameTime) {
        if (!hasActiveTimedMark(player, "坚", gameTime) || activeStaffRank(player, "坚", gameTime) < 4) {
            return;
        }

        FirmPressure pressure = firmPressures.computeIfAbsent(player.getUUID(), ignored -> new FirmPressure());
        pressure.hits = pressure.lastHitAt + 40L >= gameTime ? Math.min(5, pressure.hits + 1) : 1;
        pressure.lastHitAt = gameTime;
    }

    private static double firmPressureBonus(Player player, long gameTime) {
        if (activeStaffRank(player, "坚", gameTime) < 4) {
            return 0.0D;
        }
        FirmPressure pressure = firmPressures.get(player.getUUID());
        if (pressure == null || pressure.lastHitAt + 80L <= gameTime) {
            return 0.0D;
        }
        return pressure.hits * 0.35D;
    }

    private static void releaseRageShockwave(ServerPlayer player, RageState rage) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(rage.shockwaveRadius());
        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            area,
            entity -> entity != player && entity.isAlive() && player.distanceTo(entity) <= rage.shockwaveRadius()
        );

        for (LivingEntity target : targets) {
            Vec3 direction = target.position().subtract(player.position());
            if (direction.lengthSqr() < 0.001D) {
                direction = new Vec3(target.getRandom().nextDouble() - 0.5D, 0.0D, target.getRandom().nextDouble() - 0.5D);
            }
            Vec3 push = direction.normalize().scale(rage.shockwavePush());
            target.push(push.x, 0.35D, push.z);
            target.hurt(level.damageSources().magic(), rage.shockwaveDamage());
        }

        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0D, player.getZ(), 12, 1.6D, 0.35D, 1.6D, 0.0D);
        Glyphbound.LOGGER.debug("[{}] shockwave released by {} hitting {} entities", rage.word, player.getGameProfile().getName(), targets.size());
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        long gameTime = entity.level().getGameTime();
        if (entity instanceof Player player) {
            if (consumeSteadyBigKnockback(player, gameTime, event.getStrength())) {
                event.setStrength(0.0F);
                return;
            }
            if (hasActiveTimedMark(player, "稳", gameTime)) {
                event.setStrength(event.getStrength() * steadySpec(activeStaffTier(player, "稳", gameTime)).knockbackMultiplier());
            }
            if (hasActiveTimedMark(player, "坚", gameTime) && firmPressureBonus(player, gameTime) > 0.0D) {
                event.setStrength(event.getStrength() * 0.65F);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof Player player
            && hasActiveTimedMark(player, "稳", player.level().getGameTime())) {
            long gameTime = player.level().getGameTime();
            if (consumeSteadyBigKnockback(player, gameTime, (float) event.getKnockbackVelocity().length())) {
                event.setKnockbackVelocity(Vec3.ZERO);
                return;
            }
            event.setKnockbackVelocity(event.getKnockbackVelocity().scale(steadySpec(activeStaffTier(player, "稳", gameTime)).explosionMultiplier()));
        }
    }

    private static boolean consumeSteadyBigKnockback(Player player, long gameTime, float strength) {
        if (!hasActiveTimedMark(player, "稳", gameTime) || activeStaffRank(player, "稳", gameTime) < 4 || strength < 0.65F) {
            return false;
        }

        int charges = steadyBigKnockbackCharges.getOrDefault(player.getUUID(), 0);
        if (charges <= 0) {
            return false;
        }

        steadyBigKnockbackCharges.put(player.getUUID(), charges - 1);
        return true;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            breakHidden(event.getEntity(), event.getLevel().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            breakHidden(event.getEntity(), event.getLevel().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        breakHidden(event.getPlayer(), event.getLevel().getLevelData().getGameTime());
    }

    private static void breakHidden(Player player, long gameTime) {
        if (hasActiveTimedMark(player, "隐", gameTime)) {
            hiddenBrokenUntil.put(player.getUUID(), latestActiveMarkExpiry(player, "隐", gameTime));
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    private static void cleansePlayer(ServerPlayer player, InkMark mark) {
        CleanseSpec spec = cleanseSpec(mark.word(), InkStaffMetadata.tier(mark));
        if (spec == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        CleansePressure pressure = cleansePressure.compute(player.getUUID(), (ignored, old) -> {
            if (old == null || old.expiresAt <= gameTime) {
                return new CleansePressure(1, gameTime + CLEANSE_WINDOW_TICKS);
            }
            old.stacks++;
            old.expiresAt = gameTime + CLEANSE_WINDOW_TICKS;
            return old;
        });

        List<Holder<MobEffect>> removable = new ArrayList<>();
        Collection<MobEffectInstance> effects = player.getActiveEffects();
        for (MobEffectInstance instance : effects) {
            Holder<MobEffect> effect = instance.getEffect();
            if (!effect.value().isBeneficial()) {
                removable.add(effect);
            }
        }

        float inkWoundRemoved = cleanseInkWound(player, spec.inkWoundAmount());
        int removed = 0;
        for (Holder<MobEffect> effect : removable) {
            if (removed >= spec.maxEffects() + pressure.stacks) {
                break;
            }
            if (player.removeEffect(effect)) {
                removed++;
            }
        }

        if (removed > 0 || inkWoundRemoved > 0.0F) {
            player.getFoodData().addExhaustion(removed * (1.5F + pressure.stacks));
            player.getFoodData().addExhaustion((inkWoundRemoved / 2.0F) * (1.0F + pressure.stacks * 0.35F));
            Glyphbound.LOGGER.debug("[{}] removed {} harmful effects and {} ink wound damage from {}", mark.word(), removed, inkWoundRemoved, player.getGameProfile().getName());
        }
    }

    static float cleanseInkWound(ServerPlayer player, float amount) {
        InkWound wound = inkWounds.get(player.getUUID());
        if (wound == null || wound.amount <= 0.0F) {
            return 0.0F;
        }

        float removed = Math.min(wound.amount, amount);
        wound.amount -= removed;
        if (wound.amount <= 0.05F) {
            inkWounds.remove(player.getUUID());
        }
        return removed;
    }

    private static float cleanseInkWoundRatio(ServerPlayer player, float ratio) {
        InkWound wound = inkWounds.get(player.getUUID());
        if (wound == null || wound.amount <= 0.0F) {
            return 0.0F;
        }

        return cleanseInkWound(player, wound.amount * Math.min(1.0F, Math.max(0.0F, ratio)));
    }

    private static void recoverSoul(ServerPlayer player, InkStaffTier staffTier) {
        SoulRecovery recovery = soulRecoveries.get(player.getUUID());
        long gameTime = player.level().getGameTime();
        if (recovery == null || recovery.expiresAt <= gameTime || recovery.consumed) {
            player.displayClientMessage(Component.literal("魄: 没有可牵回的魄印"), true);
            return;
        }

        SoulSpec spec = soulSpec(staffTier);
        int xpRecovered = Math.round(recovery.experience * spec.experienceRatio());
        if (xpRecovered > 0) {
            player.giveExperiencePoints(xpRecovered);
        }

        int itemCount = recoverSoulItems(player, recovery, spec.itemRatio());
        recovery.consumed = true;
        soulRecoveries.remove(player.getUUID());

        if (itemCount > 0 || xpRecovered > 0) {
            player.displayClientMessage(Component.literal("魄: 牵回 " + itemCount + " 件物品与 " + xpRecovered + " 点经验"), true);
        } else {
            player.displayClientMessage(Component.literal("魄: 魄印已散，死亡掉落可能已消失"), true);
        }
    }

    private static int recoverSoulItems(ServerPlayer player, SoulRecovery recovery, float ratio) {
        if (recovery.items.isEmpty() || ratio <= 0.0F) {
            return 0;
        }

        List<SoulItemCategory> categories = new ArrayList<>();
        for (SoulItemRef itemRef : recovery.items) {
            ItemEntity item = findSoulItem(player.server, itemRef);
            if (item != null && item.isAlive() && !item.getItem().isEmpty()) {
                addSoulItemCategory(categories, new SoulItemEntry(item, item.getItem()));
            }
        }

        if (categories.isEmpty()) {
            return 0;
        }

        Collections.shuffle(categories);
        int targetCategories = Math.max(1, Math.min(categories.size(), (int) Math.ceil(categories.size() * ratio)));
        int recoveredCount = 0;
        for (int i = 0; i < targetCategories; i++) {
            SoulItemCategory category = categories.get(i);
            int categoryTarget = Math.max(1, Math.min(category.totalCount(), (int) Math.ceil(category.totalCount() * ratio)));
            int categoryRecovered = 0;
            Collections.shuffle(category.entries);
            for (SoulItemEntry entry : category.entries) {
                if (categoryRecovered >= categoryTarget) {
                    break;
                }

                ItemEntity item = entry.entity();
                if (!item.isAlive() || item.getItem().isEmpty()) {
                    continue;
                }

                ItemStack groundStack = item.getItem();
                int take = Math.min(groundStack.getCount(), categoryTarget - categoryRecovered);
                ItemStack recovered = groundStack.copyWithCount(take);
                giveOrDrop(player, recovered);
                groundStack.shrink(take);
                categoryRecovered += take;
                recoveredCount += take;
                if (groundStack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(groundStack);
                }
            }
        }

        return recoveredCount;
    }

    private static void addSoulItemCategory(List<SoulItemCategory> categories, SoulItemEntry entry) {
        for (SoulItemCategory category : categories) {
            if (ItemStack.isSameItemSameComponents(category.sample, entry.stack())) {
                category.entries.add(entry);
                return;
            }
        }

        SoulItemCategory category = new SoulItemCategory(entry.stack().copyWithCount(1));
        category.entries.add(entry);
        categories.add(category);
    }

    private static ItemEntity findSoulItem(MinecraftServer server, SoulItemRef itemRef) {
        ServerLevel level = server.getLevel(itemRef.dimension());
        if (level == null) {
            return null;
        }
        Entity entity = level.getEntity(itemRef.entityId());
        return entity instanceof ItemEntity item ? item : null;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static boolean hasNearbyEnemy(ServerPlayer player, int radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        return !player.serverLevel().getEntities(player, area, entity -> entity instanceof Enemy).isEmpty();
    }

    private static void clearPlayerBodyState(Player player) {
        clearPlayerBodyState(player.getUUID());
        clearPlayerBodyAttributes(player);
        clearPlayerInkMarks(player.getUUID());
    }

    private static void clearPlayerBodyState(UUID playerId) {
        heartStates.remove(playerId);
        restStates.remove(playerId);
        restBrokenUntil.remove(playerId);
        lastHurtAt.remove(playerId);
        salvationUsedUntil.remove(playerId);
        salvationGoldenBodyUntil.remove(playerId);
        inkWounds.remove(playerId);
        settlingInkWounds.remove(playerId);
        hiddenBrokenUntil.remove(playerId);
        cleansePressure.remove(playerId);
        rageStates.remove(playerId);
        firmPressures.remove(playerId);
        steadyBigKnockbackCharges.remove(playerId);
    }

    private static void clearAllBodyState(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearPlayerBodyAttributes(player);
        }
        heartStates.clear();
        restStates.clear();
        restBrokenUntil.clear();
        lastHurtAt.clear();
        salvationUsedUntil.clear();
        salvationGoldenBodyUntil.clear();
        inkWounds.clear();
        settlingInkWounds.clear();
        hiddenBrokenUntil.clear();
        cleansePressure.clear();
        rageStates.clear();
        soulRecoveries.clear();
        firmPressures.clear();
        steadyBigKnockbackCharges.clear();
        AozaiInkApi.marks().clearAll();
    }

    private static void clearPlayerBodyAttributes(Player player) {
        GlyphAttributes.remove(player, Attributes.MAX_HEALTH, HEART_MAX_HEALTH);
        GlyphAttributes.remove(player, Attributes.ARMOR_TOUGHNESS, FIRM_TOUGHNESS);
        GlyphAttributes.remove(player, Attributes.KNOCKBACK_RESISTANCE, FIRM_KNOCKBACK);
        GlyphAttributes.remove(player, Attributes.MOVEMENT_SPEED, FIRM_SPEED_COST);
        GlyphAttributes.remove(player, Attributes.ATTACK_DAMAGE, RAGE_ATTACK_DAMAGE);
        GlyphAttributes.remove(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_KNOCKBACK);
        GlyphAttributes.remove(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY_EXPLOSION);
        GlyphAttributes.remove(player, Attributes.MOVEMENT_EFFICIENCY, STEADY_MOVEMENT);
        GlyphAttributes.remove(player, Attributes.WATER_MOVEMENT_EFFICIENCY, STEADY_WATER);
    }

    private static void clearPlayerInkMarks(UUID playerId) {
        List<InkTarget> targets = new ArrayList<>();
        for (InkMark mark : AozaiInkApi.marks().allMarks()) {
            if (mark.target().type() == InkTargetType.PLAYER && playerId.equals(mark.target().entityUuid()) && !targets.contains(mark.target())) {
                targets.add(mark.target());
            }
        }
        for (InkTarget target : targets) {
            AozaiInkApi.marks().clear(target);
        }
    }

    private static void activateLife(InkMark mark) {
        if (mark.target().entityUuid() == null) {
            return;
        }
        LifeSpec spec = lifeSpec(mark.word(), InkStaffMetadata.tier(mark));
        ServerPlayer player = findPlayer(mark);
        if (spec == null || player == null) {
            if (player != null) {
                player.displayClientMessage(Component.literal(mark.word() + " 无法被当前魔杖稳定施放"), true);
            }
            return;
        }

        long expiresAt = mark.bornGameTime() + spec.durationTicks();
        HeartState state = heartStates.get(mark.target().entityUuid());
        if (state == null || state.expiresAt <= mark.bornGameTime() || spec.rank() > state.rank) {
            state = new HeartState(spec.rank(), 0.0D, expiresAt);
            heartStates.put(mark.target().entityUuid(), state);
        }
        if (spec.rank() == state.rank) {
            state.bonus = Math.min(Math.max(state.bonus, 0.0D) + spec.bonusPerCast(), spec.maxBonusHealth());
        }
        state.expiresAt = Math.max(state.expiresAt, expiresAt);
        if (spec.regenTicks() > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, spec.regenTicks(), 1, true, true));
        }
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(Math.min(6.0F, (float) spec.bonusPerCast() / 2.0F));
        }
        Glyphbound.LOGGER.debug("[{}] life rank {} bonus {} until {}", mark.word(), spec.rank(), state.bonus, state.expiresAt);
    }

    private static void activateRage(InkMark mark) {
        if (mark.target().entityUuid() == null) {
            return;
        }
        RageSpec spec = rageSpec(mark.word(), InkStaffMetadata.tier(mark));
        ServerPlayer player = findPlayer(mark);
        if (spec == null || player == null) {
            if (player != null) {
                player.displayClientMessage(Component.literal(mark.word() + " 无法被当前魔杖稳定施放"), true);
            }
            return;
        }

        long bornAt = mark.bornGameTime();
        long expiresAt = bornAt + spec.durationTicks();
        RageState state = rageStates.computeIfAbsent(mark.target().entityUuid(), ignored -> new RageState());
        if (state.activeUntil <= bornAt || state.spec.rank() != spec.rank()) {
            state.resetAll();
        }
        state.word = mark.word();
        state.spec = spec;
        state.activeUntil = Math.max(state.activeUntil, expiresAt);
        Glyphbound.LOGGER.debug("[{}] active until {}, damage={} attack={} hits={}", state.word, state.activeUntil, state.defenseDamage, state.attackDamage, state.hitCount);
    }

    private static void breakRest(ServerPlayer player, long gameTime, String reason) {
        if (!MarkQueries.hasActivePlayerMark(player, "息", gameTime)) {
            return;
        }
        long expiresAt = latestActiveMarkExpiry(player, "息", gameTime);
        restBrokenUntil.put(player.getUUID(), expiresAt);
        restStates.remove(player.getUUID());
        Glyphbound.LOGGER.debug("[息] broken by {} for {} until recast", reason, player.getGameProfile().getName());
    }

    private static LifeSpec lifeSpec(String word, InkStaffTier staffTier) {
        if (!"心".equals(word)) {
            return null;
        }
        return switch (staffTier) {
            case WOOD -> new LifeSpec("心", staffRank(staffTier), 6.0D, 10.0D, ticksSeconds(90), 0);
            case STONE -> new LifeSpec("心", staffRank(staffTier), 8.0D, 12.0D, ticksMinutes(2), 0);
            case COPPER -> new LifeSpec("心", staffRank(staffTier), 10.0D, 16.0D, ticksMinutes(3), 0);
            case IRON -> new LifeSpec("心", staffRank(staffTier), 10.0D, 20.0D, ticksMinutes(5), ticksSecondsInt(3));
            case GOLD -> new LifeSpec("心", staffRank(staffTier), 12.0D, 20.0D, ticksMinutes(3), ticksSecondsInt(5));
            case DIAMOND -> new LifeSpec("心", staffRank(staffTier), 12.0D, 20.0D, ticksMinutes(8), ticksSecondsInt(8));
            case NETHERITE -> new LifeSpec("心", staffRank(staffTier), 12.0D, 20.0D, ticksMinutes(10), ticksSecondsInt(10));
        };
    }

    private static SalvationSpec salvationSpec(String word, InkStaffTier staffTier) {
        if ("命".equals(word)) {
            return switch (staffTier) {
                case WOOD -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(5), ticksSecondsInt(1), 0.0F);
                case STONE -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(8), ticksSecondsInt(2), 0.0F);
                case COPPER -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(2), 0.0F);
                case IRON -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(3), 0.0F);
                case GOLD -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(6), ticksSecondsInt(3), 0.0F);
                case DIAMOND -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(4), 0.0F);
                case NETHERITE -> new SalvationSpec("命", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(4), 0.0F);
            };
        }
        if ("救".equals(word)) {
            return switch (staffTier) {
                case WOOD -> null;
                case STONE -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(5), ticksSecondsInt(3), 0.25F);
                case COPPER -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(8), ticksSecondsInt(4), 0.40F);
                case IRON -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(5), 0.60F);
                case GOLD -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(6), ticksSecondsInt(5), 0.60F);
                case DIAMOND -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(5), 0.80F);
                case NETHERITE -> new SalvationSpec("救", staffRank(staffTier), ticksMinutes(10), ticksSecondsInt(5), 1.0F);
            };
        }
        return null;
    }

    private static InkWoundSpec inkWoundSpec(String word, InkStaffTier staffTier) {
        if (!"忍".equals(word)) {
            return null;
        }
        return switch (staffTier) {
            case WOOD -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.25F, 6.0F);
            case STONE -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.35F, 8.0F);
            case COPPER -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.45F, 10.0F);
            case IRON -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.55F, 14.0F);
            case GOLD -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(6), 0.60F, 12.0F);
            case DIAMOND -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.65F, 18.0F);
            case NETHERITE -> new InkWoundSpec("忍", staffRank(staffTier), ticksMinutes(10), 0.70F, 20.0F);
        };
    }

    private static CleanseSpec cleanseSpec(String word, InkStaffTier staffTier) {
        if (!"净".equals(word)) {
            return null;
        }
        return switch (staffTier) {
            case WOOD -> new CleanseSpec("净", 4.0F, 1);
            case STONE -> new CleanseSpec("净", 6.0F, 1);
            case COPPER -> new CleanseSpec("净", 8.0F, 2);
            case IRON -> new CleanseSpec("净", 12.0F, 3);
            case GOLD -> new CleanseSpec("净", 14.0F, 3);
            case DIAMOND -> new CleanseSpec("净", 18.0F, 4);
            case NETHERITE -> new CleanseSpec("净", 22.0F, 5);
        };
    }

    private static RageSpec rageSpec(String word, InkStaffTier staffTier) {
        if ("力".equals(word)) {
            return switch (staffTier) {
                case WOOD -> new RageSpec("力", staffRank(staffTier), ticksMinutes(5), 0.0D, 0.20D, 0.0D, 1.0F, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case STONE -> new RageSpec("力", staffRank(staffTier), ticksMinutes(6), 0.0D, 0.30D, 0.0D, 1.0F, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case COPPER -> new RageSpec("力", staffRank(staffTier), ticksMinutes(8), 0.0D, 0.40D, 0.0D, 1.0F, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case IRON -> new RageSpec("力", staffRank(staffTier), ticksMinutes(10), 0.0D, 0.50D, 0.0D, 1.0F, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case GOLD -> new RageSpec("力", staffRank(staffTier), ticksMinutes(5), 0.0D, 0.60D, 0.0D, 1.0F, 0.005D, false, 0.0D, 0.0D, 0.0F);
                case DIAMOND -> new RageSpec("力", staffRank(staffTier), ticksMinutes(10), 0.0D, 0.60D, 0.0D, 1.0F, 0.005D, true, 3.0D, 1.0D, 0.0F);
                case NETHERITE -> new RageSpec("力", staffRank(staffTier), ticksMinutes(10), 0.0D, 0.60D, 0.0D, 1.0F, 0.005D, true, 3.0D, 1.2D, 0.0F);
            };
        }
        if ("怒".equals(word)) {
            return switch (staffTier) {
                case WOOD, STONE, COPPER, IRON, GOLD -> null;
                case DIAMOND -> new RageSpec("怒", staffRank(staffTier), ticksMinutes(10), 0.65D, 1.25D, 0.01D, 5.0F, 0.05D, true, 4.0D, 1.8D, 2.0F);
                case NETHERITE -> new RageSpec("怒", staffRank(staffTier), ticksMinutes(10), 0.70D, 1.50D, 0.01D, 5.0F, 0.05D, true, 4.0D, 2.0D, 2.0F);
            };
        }
        return null;
    }

    private static SoulSpec soulSpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new SoulSpec(0.20F, 0.20F);
            case STONE -> new SoulSpec(0.30F, 0.30F);
            case COPPER -> new SoulSpec(0.40F, 0.40F);
            case IRON -> new SoulSpec(0.50F, 0.50F);
            case GOLD -> new SoulSpec(0.60F, 0.60F);
            case DIAMOND -> new SoulSpec(0.70F, 0.70F);
            case NETHERITE -> new SoulSpec(0.80F, 0.80F);
        };
    }

    private static RestSpec restSpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new RestSpec(40, 1, 0.15F, 1.0F, 1.5F);
            case STONE -> new RestSpec(36, 1, 0.2F, 1.0F, 1.25F);
            case COPPER -> new RestSpec(34, 1, 0.35F, 1.0F, 1.1F);
            case IRON -> new RestSpec(30, 1, 0.35F, 1.5F, 1.0F);
            case GOLD -> new RestSpec(24, 1, 0.45F, 2.0F, 1.25F);
            case DIAMOND -> new RestSpec(28, 1, 0.45F, 1.5F, 0.8F);
            case NETHERITE -> new RestSpec(24, 1, 0.55F, 2.0F, 0.65F);
        };
    }

    private static SteadySpec steadySpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new SteadySpec(0.55F, 0.65D, 0);
            case STONE -> new SteadySpec(0.45F, 0.55D, 0);
            case COPPER -> new SteadySpec(0.35F, 0.45D, 0);
            case IRON -> new SteadySpec(0.25F, 0.35D, 0);
            case GOLD -> new SteadySpec(0.18F, 0.25D, 0);
            case DIAMOND -> new SteadySpec(0.20F, 0.25D, 1);
            case NETHERITE -> new SteadySpec(0.15F, 0.20D, 2);
        };
    }

    private static HiddenSpec hiddenSpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new HiddenSpec(12.0D, 12.0D);
            case STONE -> new HiddenSpec(14.0D, 10.0D);
            case COPPER -> new HiddenSpec(16.0D, 8.0D);
            case IRON -> new HiddenSpec(18.0D, 6.0D);
            case GOLD -> new HiddenSpec(20.0D, 5.0D);
            case DIAMOND -> new HiddenSpec(22.0D, 4.0D);
            case NETHERITE -> new HiddenSpec(24.0D, 3.0D);
        };
    }

    private static BrightSpec brightSpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new BrightSpec(6, 4, true, false, false, false, false);
            case STONE -> new BrightSpec(7, 6, true, true, false, false, false);
            case COPPER -> new BrightSpec(8, 8, true, true, true, false, false);
            case IRON -> new BrightSpec(10, 10, true, true, true, true, true);
            case GOLD -> new BrightSpec(10, 12, true, true, true, true, true);
            case DIAMOND -> new BrightSpec(12, 14, true, true, true, true, true);
            case NETHERITE -> new BrightSpec(14, 16, true, true, true, true, true);
        };
    }

    private static PulseSpec pulseSpec(InkStaffTier staffTier) {
        return switch (staffTier) {
            case WOOD -> new PulseSpec(8, 1);
            case STONE -> new PulseSpec(12, 2);
            case COPPER -> new PulseSpec(16, 3);
            case IRON -> new PulseSpec(18, 4);
            case GOLD -> new PulseSpec(18, 5);
            case DIAMOND -> new PulseSpec(24, 5);
            case NETHERITE -> new PulseSpec(28, 6);
        };
    }

    private static SalvationSpec activeSalvationSpec(ServerPlayer player, long gameTime) {
        SalvationSpec best = null;
        for (String word : List.of("救", "命")) {
            for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
                SalvationSpec spec = salvationSpec(word, InkStaffMetadata.tier(mark));
                if (spec != null && gameTime < mark.bornGameTime() + spec.durationTicks()
                    && (best == null || spec.rank() > best.rank())) {
                    best = spec;
                }
            }
            if (best != null) {
                break;
            }
        }
        return best;
    }

    private static InkWoundSpec activeInkWoundSpec(ServerPlayer player, long gameTime) {
        InkWoundSpec best = null;
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, "忍", gameTime)) {
            InkWoundSpec spec = inkWoundSpec("忍", InkStaffMetadata.tier(mark));
            if (spec != null && gameTime < mark.bornGameTime() + spec.durationTicks()
                && (best == null || spec.rank() > best.rank())) {
                best = spec;
            }
        }
        return best;
    }

    private static boolean staffAllows(InkMark mark, InkStaffTier minimumTier) {
        return staffRank(InkStaffMetadata.tier(mark)) >= staffRank(minimumTier);
    }

    private static boolean staffAllowsLatest(Player player, String word, InkStaffTier minimumTier, long gameTime) {
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
            if (staffAllows(mark, minimumTier)) {
                return true;
            }
        }
        return false;
    }

    private static int staffRank(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> 0;
            case STONE -> 1;
            case COPPER -> 2;
            case IRON, GOLD -> 3;
            case DIAMOND -> 4;
            case NETHERITE -> 5;
        };
    }

    private static long latestActiveMarkExpiry(Player player, String word, long gameTime) {
        long expiresAt = gameTime + 1L;
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
            expiresAt = Math.max(expiresAt, activeUntil(mark));
        }
        return expiresAt;
    }

    private static float glyphPower(Player player, String word, long gameTime) {
        float power = 1.0F;
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
            if (gameTime < activeUntil(mark)) {
                power = Math.max(power, InkStaffMetadata.powerMultiplier(mark));
            }
        }
        return power;
    }

    private static InkStaffTier activeStaffTier(Player player, String word, long gameTime) {
        InkStaffTier best = InkStaffTier.WOOD;
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
            InkStaffTier tier = InkStaffMetadata.tier(mark);
            if (gameTime < activeUntil(mark) && staffRank(tier) > staffRank(best)) {
                best = tier;
            }
        }
        return best;
    }

    private static int activeStaffRank(Player player, String word, long gameTime) {
        return staffRank(activeStaffTier(player, word, gameTime));
    }

    private static boolean hasActiveTimedMark(Player player, String word, long gameTime) {
        for (InkMark mark : MarkQueries.getActivePlayerMarks(player, word, gameTime)) {
            if (gameTime < activeUntil(mark)) {
                return true;
            }
        }
        return false;
    }

    private static long activeUntil(InkMark mark) {
        return mark.bornGameTime() + durationTicks(mark.word(), InkStaffMetadata.tier(mark), mark.ttlTicks());
    }

    private static long durationTicks(String word, InkStaffTier tier, long fallbackTicks) {
        LifeSpec life = lifeSpec(word, tier);
        if (life != null) {
            return life.durationTicks();
        }
        SalvationSpec salvation = salvationSpec(word, tier);
        if (salvation != null) {
            return salvation.durationTicks();
        }
        InkWoundSpec inkWound = inkWoundSpec(word, tier);
        if (inkWound != null) {
            return inkWound.durationTicks();
        }
        RageSpec rage = rageSpec(word, tier);
        if (rage != null) {
            return rage.durationTicks();
        }
        return switch (word) {
            case "息" -> switch (tier) {
                case WOOD -> ticksSeconds(30);
                case STONE -> ticksSeconds(45);
                case COPPER -> ticksMinutes(1);
                case IRON -> ticksSeconds(90);
                case GOLD -> ticksSeconds(45);
                case DIAMOND -> ticksMinutes(2);
                case NETHERITE -> ticksMinutes(3);
            };
            case "坚" -> switch (tier) {
                case WOOD -> ticksSeconds(30);
                case STONE -> ticksSeconds(45);
                case COPPER -> ticksMinutes(1);
                case IRON -> ticksSeconds(90);
                case GOLD -> ticksSeconds(45);
                case DIAMOND -> ticksMinutes(2);
                case NETHERITE -> ticksMinutes(3);
            };
            case "稳" -> switch (tier) {
                case WOOD -> ticksSeconds(45);
                case STONE -> ticksMinutes(1);
                case COPPER -> ticksSeconds(90);
                case IRON -> ticksMinutes(2);
                case GOLD -> ticksMinutes(1);
                case DIAMOND -> ticksMinutes(3);
                case NETHERITE -> ticksMinutes(4);
            };
            case "隐" -> switch (tier) {
                case WOOD -> ticksSeconds(30);
                case STONE -> ticksSeconds(45);
                case COPPER -> ticksMinutes(1);
                case IRON -> ticksSeconds(90);
                case GOLD -> ticksSeconds(45);
                case DIAMOND -> ticksMinutes(2);
                case NETHERITE -> ticksMinutes(3);
            };
            case "明" -> switch (tier) {
                case WOOD -> ticksSeconds(30);
                case STONE -> ticksSeconds(45);
                case COPPER -> ticksMinutes(1);
                case IRON -> ticksSeconds(90);
                case GOLD -> ticksSeconds(45);
                case DIAMOND -> ticksMinutes(2);
                case NETHERITE -> ticksMinutes(3);
            };
            case "脉" -> switch (tier) {
                case WOOD -> ticksSeconds(20);
                case STONE -> ticksSeconds(30);
                case COPPER -> ticksSeconds(45);
                case IRON -> ticksMinutes(1);
                case GOLD -> ticksSeconds(30);
                case DIAMOND -> ticksSeconds(90);
                case NETHERITE -> ticksMinutes(2);
            };
            default -> fallbackTicks;
        };
    }

    private static long ticksSeconds(int seconds) {
        return 20L * seconds;
    }

    private static int ticksSecondsInt(int seconds) {
        return 20 * seconds;
    }

    private static long ticksMinutes(int minutes) {
        return ticksSeconds(60 * minutes);
    }

    private static int scaledRadius(int baseRadius, float power) {
        return Math.max(baseRadius, Math.round(baseRadius * Math.min(1.5F, power)));
    }

    private static ServerPlayer findPlayer(InkMark mark) {
        if (mark.target().entityUuid() == null) {
            return null;
        }
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(mark.target().entityUuid());
            if (entity instanceof ServerPlayer player) {
                return player;
            }
        }
        return null;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, path);
    }

    private static final class PlayerRestState {
        private final Vec3 lastPosition;
        private final long stillSince;

        private PlayerRestState(Vec3 lastPosition, long stillSince) {
            this.lastPosition = lastPosition;
            this.stillSince = stillSince;
        }

        private static PlayerRestState moving(Vec3 position, long gameTime) {
            return new PlayerRestState(position, gameTime);
        }
    }

    private static final class HeartState {
        private int rank;
        private double bonus;
        private long expiresAt;

        private HeartState(int rank, double bonus, long expiresAt) {
            this.rank = rank;
            this.bonus = bonus;
            this.expiresAt = expiresAt;
        }
    }

    private static final class InkWound {
        private float amount;
        private long startsAt;

        private InkWound(float amount, long startsAt) {
            this.amount = amount;
            this.startsAt = startsAt;
        }
    }

    private static final class CleansePressure {
        private int stacks;
        private long expiresAt;

        private CleansePressure(int stacks, long expiresAt) {
            this.stacks = stacks;
            this.expiresAt = expiresAt;
        }
    }

    private static final class SoulRecovery {
        private long expiresAt;
        private ResourceKey<Level> deathDimension;
        private BlockPos deathPos;
        private int experience;
        private boolean consumed;
        private final List<SoulItemRef> items = new ArrayList<>();

        private SoulRecovery(long expiresAt, ResourceKey<Level> deathDimension, BlockPos deathPos) {
            this.expiresAt = expiresAt;
            this.deathDimension = deathDimension;
            this.deathPos = deathPos;
        }
    }

    private static final class SoulItemCategory {
        private final ItemStack sample;
        private final List<SoulItemEntry> entries = new ArrayList<>();

        private SoulItemCategory(ItemStack sample) {
            this.sample = sample;
        }

        private int totalCount() {
            int total = 0;
            for (SoulItemEntry entry : entries) {
                ItemEntity item = entry.entity();
                if (item.isAlive() && !item.getItem().isEmpty()) {
                    total += item.getItem().getCount();
                }
            }
            return total;
        }
    }

    private static final class FirmPressure {
        private int hits;
        private long lastHitAt;
    }

    private static final class RageState {
        private String word = "力";
        private RageSpec spec = rageSpec("力", InkStaffTier.WOOD);
        private long activeUntil;
        private float defenseDamage;
        private float attackDamage;
        private int hitCount;

        private void addDamage(float damage) {
            if (spec.damageReductionPerDamage() > 0.0D) {
                defenseDamage = Math.min((float) (spec.maxDamageReduction() / spec.damageReductionPerDamage()), defenseDamage + damage);
            }
            attackDamage = Math.min((float) ((spec.maxAttackBonus() / spec.attackBonusPerStep()) * spec.attackDamageStep()), attackDamage + damage);
        }

        private double damageReduction() {
            return Math.min(spec.maxDamageReduction(), Math.floor(defenseDamage) * spec.damageReductionPerDamage());
        }

        private double attackBonus() {
            return Math.min(spec.maxAttackBonus(), Math.floor(attackDamage / spec.attackDamageStep()) * spec.attackBonusPerStep());
        }

        private boolean canShockwave() {
            return spec.shockwave();
        }

        private double shockwaveRadius() {
            return spec.shockwaveRadius();
        }

        private double shockwavePush() {
            return spec.shockwavePush();
        }

        private float shockwaveDamage() {
            return spec.shockwaveDamage();
        }

        private void resetAll() {
            defenseDamage = 0.0F;
            attackDamage = 0.0F;
            hitCount = 0;
        }
    }

    private record LifeSpec(String word, int rank, double bonusPerCast, double maxBonusHealth, long durationTicks, int regenTicks) {
    }

    private record SalvationSpec(String word, int rank, long durationTicks, int goldenBodyTicks, float inkWoundClearRatio) {
    }

    private record InkWoundSpec(String word, int rank, long durationTicks, float deferral, float poolCap) {
    }

    private record CleanseSpec(String word, float inkWoundAmount, int maxEffects) {
    }

    private record SoulSpec(float itemRatio, float experienceRatio) {
    }

    private record SoulItemRef(ResourceKey<Level> dimension, UUID entityId) {
    }

    private record SoulItemEntry(ItemEntity entity, ItemStack stack) {
    }

    private record RestSpec(int intervalTicks, int foodAmount, float saturation, float healAmount, float exhaustion) {
    }

    private record SteadySpec(float knockbackMultiplier, double explosionMultiplier, int bigKnockbackCancels) {
    }

    private record HiddenSpec(double retargetRadius, double forgetRadius) {
    }

    private record BrightSpec(
        int radius,
        int maxBlocks,
        boolean revealContainers,
        boolean revealTraps,
        boolean revealCommonOres,
        boolean revealRareOres,
        boolean revealMobs
    ) {
    }

    private record PulseSpec(int radius, int detailLevel) {
    }

    private record RageSpec(
        String word,
        int rank,
        long durationTicks,
        double maxDamageReduction,
        double maxAttackBonus,
        double damageReductionPerDamage,
        float attackDamageStep,
        double attackBonusPerStep,
        boolean shockwave,
        double shockwaveRadius,
        double shockwavePush,
        float shockwaveDamage
    ) {
    }

    private record PulseSummary(int total, int hostile, String direction, int nearDistance) {
        private static PulseSummary from(ServerPlayer player, List<LivingEntity> entities) {
            LivingEntity nearest = entities.getFirst();
            double nearestDistance = player.distanceTo(nearest);
            int hostile = 0;
            for (LivingEntity entity : entities) {
                if (entity instanceof Enemy) {
                    hostile++;
                }
                double distance = player.distanceTo(entity);
                if (distance < nearestDistance) {
                    nearest = entity;
                    nearestDistance = distance;
                }
            }
            return new PulseSummary(entities.size(), hostile, directionFrom(player, nearest), (int) Math.round(nearestDistance));
        }

        private String description(PulseSpec spec) {
            return switch (spec.detailLevel()) {
                case 1 -> total > 0 ? "附近有生命脉动" : "周围很静";
                case 2 -> "生命在" + direction + "侧最强";
                case 3 -> (hostile > 0 ? "有敌意 / " : "") + total + " 生命，最近在" + direction;
                case 4 -> hostile + " 敌意 / " + total + " 生命，最近约 " + nearDistance + " 格";
                default -> {
                    String mood = hostile > 0 ? hostile + " 敌意 / " : "";
                    yield mood + total + " 生命，最近在" + direction + "约 " + nearDistance + " 格";
                }
            };
        }

        private static String directionFrom(ServerPlayer player, LivingEntity entity) {
            Vec3 delta = entity.position().subtract(player.position());
            if (Math.abs(delta.x) > Math.abs(delta.z)) {
                return delta.x > 0.0D ? "东" : "西";
            }
            return delta.z > 0.0D ? "南" : "北";
        }
    }
}
