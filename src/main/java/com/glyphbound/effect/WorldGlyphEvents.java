package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffMetadata;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.api.event.InkMarkBeforeAttachEvent;
import com.aozainkmc.api.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphAttributes;
import com.glyphbound.core.GlyphboundAdvancements;
import com.glyphbound.core.GlyphboundItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class WorldGlyphEvents {
    private static final String SPRING_WORD = "泉";
    private static final String INK_FIELD_WORD = "墨";
    private static final String MOUNTAIN_WORD = "山";
    private static final String RIFT_WORD = "裂";
    private static final String BOUNDARY_WORD = "界";
    private static final ResourceLocation INK_MAX_HEALTH = id("ink_field_max_health");
    private static final ResourceLocation INK_ATTACK_DAMAGE = id("ink_field_attack_damage");
    private static final ResourceLocation INK_MOVEMENT_SPEED = id("ink_field_movement_speed");

    private static final Map<UUID, Long> springCooldowns = new HashMap<>();
    private static final Map<UUID, InkFieldState> inkFields = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> boostedMobs = new HashMap<>();
    private static final Map<UUID, Float> inkDurabilityDebt = new HashMap<>();
    private static final Map<UUID, Long> inkPlayerHitTargets = new HashMap<>();
    private static final Map<UUID, TemporaryTerrain> terrains = new LinkedHashMap<>();
    private static final Map<TerrainBlockKey, UUID> terrainBlocks = new HashMap<>();

    private WorldGlyphEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkBeforeAttach(InkMarkBeforeAttachEvent event) {
        ServerPlayer player = event.player();
        long gameTime = player.level().getGameTime();
        if (!isInInkField(player, gameTime)) {
            return;
        }
        if (INK_FIELD_WORD.equals(event.mark().word())) {
            return;
        }

        float debt = inkDurabilityDebt.getOrDefault(player.getUUID(), 0.0F) + 0.2F;
        int extra = 0;
        if (debt >= 1.0F) {
            extra = (int) debt;
            debt -= extra;
        }
        inkDurabilityDebt.put(player.getUUID(), debt);
        if (extra > 0) {
            event.addExtraDurabilityCost(extra);
        }
    }

    @SubscribeEvent
    public static void onInkMarkAttached(InkMarkAttachedEvent event) {
        InkMark mark = event.mark();
        if (SPRING_WORD.equals(mark.word())) {
            castSpring(mark);
            return;
        }
        if (INK_FIELD_WORD.equals(mark.word())) {
            castInkField(mark);
            return;
        }
        if (MOUNTAIN_WORD.equals(mark.word())) {
            castMountain(mark);
            return;
        }
        if (RIFT_WORD.equals(mark.word())) {
            castRift(mark);
            return;
        }
        if (BOUNDARY_WORD.equals(mark.word())) {
            ServerPlayer player = findPlayerForWorldGlyph(mark);
            if (player != null) {
                player.displayClientMessage(Component.literal("界: 此界未稳"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        springCooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        inkPlayerHitTargets.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        inkFields.entrySet().removeIf(entry -> tickInkField(event, entry.getKey(), entry.getValue(), gameTime));
        tickTerrains(event, gameTime);
        if (gameTime % 20L == 0L) {
            cleanupMobBoosts(event, gameTime);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && isInInkField(player, player.level().getGameTime())) {
            event.setNewDamage(event.getNewDamage() * inkFieldSpec(activeInkFieldTier(player)).playerMultiplier());
            inkPlayerHitTargets.put(event.getEntity().getUUID(), player.level().getGameTime() + 2L);
        }
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        Long expiresAt = inkPlayerHitTargets.get(event.getEntity().getUUID());
        long gameTime = event.getEntity().level().getGameTime();
        if (expiresAt == null || expiresAt < gameTime) {
            return;
        }
        event.setStrength(event.getStrength() * 2.0F);
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInInkField(player, player.level().getGameTime())) {
            event.setAmount(event.getAmount() * inkFieldSpec(activeInkFieldTier(player)).playerMultiplier());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        boolean rewarded = attacker instanceof ServerPlayer player && isInInkField(player, player.level().getGameTime())
            || isInsideAnyInkField(event.getEntity(), event.getEntity().level().getGameTime());
        if (!rewarded || level.random.nextFloat() >= 0.30F) {
            return;
        }

        ItemStack core = new ItemStack(GlyphboundItems.INK_CORE.get());
        ItemEntity drop = new ItemEntity(level, event.getEntity().getX(), event.getEntity().getY() + 0.25D, event.getEntity().getZ(), core);
        event.getDrops().add(drop);
        if (attacker instanceof ServerPlayer player) {
            GlyphboundAdvancements.award(player, GlyphboundAdvancements.INK_CORE_DROP);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            inkFields.remove(player.getUUID());
            springCooldowns.remove(player.getUUID());
            inkDurabilityDebt.remove(player.getUUID());
            removeOwnerTerrains(player.getUUID(), event.getEntity().level().getServer());
        }
        clearMobBoost(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        inkFields.remove(event.getEntity().getUUID());
        springCooldowns.remove(event.getEntity().getUUID());
        inkDurabilityDebt.remove(event.getEntity().getUUID());
        removeOwnerTerrains(event.getEntity().getUUID(), event.getEntity().level().getServer());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        inkFields.remove(event.getEntity().getUUID());
        inkDurabilityDebt.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (TemporaryTerrain terrain : new ArrayList<>(terrains.values())) {
            rollbackTerrain(event.getServer().getLevel(terrain.dimension), terrain);
        }
        inkFields.clear();
        springCooldowns.clear();
        boostedMobs.clear();
        inkDurabilityDebt.clear();
        inkPlayerHitTargets.clear();
        terrains.clear();
        terrainBlocks.clear();
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID terrainId = terrainBlocks.get(new TerrainBlockKey(level.dimension(), event.getPos()));
        if (terrainId == null) {
            return;
        }
        TemporaryTerrain terrain = terrains.get(terrainId);
        if (terrain == null) {
            return;
        }
        event.setCanceled(true);
        rollbackTerrain(level, terrain);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(terrain.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.literal(terrain.word + ": 临时地形已回卷"), true);
        }
    }

    public static float healingMultiplier(ServerPlayer player) {
        if (!isInInkField(player, player.level().getGameTime())) {
            return 1.0F;
        }
        return inkFieldSpec(activeInkFieldTier(player)).playerMultiplier();
    }

    public static boolean isInkFieldOwnerActive(UUID owner, String dimension, long gameTime) {
        InkFieldState field = inkFields.get(owner);
        return field != null
            && field.dimension.location().toString().equals(dimension);
    }

    private static void castSpring(InkMark mark) {
        ServerPlayer owner = findOwner(mark);
        if (owner == null) {
            return;
        }

        long gameTime = owner.level().getGameTime();
        SpringSpec spec = springSpec(InkStaffMetadata.tier(mark));
        long cooldownUntil = springCooldowns.getOrDefault(owner.getUUID(), 0L);
        if (cooldownUntil > gameTime) {
            owner.displayClientMessage(Component.literal("泉: 灵泉未涌"), true);
            return;
        }
        springCooldowns.put(owner.getUUID(), gameTime + spec.cooldownTicks());

        if (mark.target().type() == InkTargetType.MARKER) {
            ServerLevel level = owner.serverLevel();
            BlockPos pos = BlockPos.of(mark.target().packedBlockPos());
            int healed = 0;
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(3.0D))) {
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 9.0D) {
                    healSpring(player, spec.healAmount());
                    healed++;
                }
            }
            level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 24, 1.0D, 0.25D, 1.0D, 0.04D);
            owner.displayClientMessage(Component.literal(healed > 0 ? "泉: 灵泉涌起" : "泉: 此处无人承泉"), true);
            return;
        }

        healSpring(owner, spec.healAmount());
        owner.serverLevel().sendParticles(ParticleTypes.SPLASH, owner.getX(), owner.getY() + 1.0D, owner.getZ(), 16, 0.55D, 0.35D, 0.55D, 0.04D);
        owner.displayClientMessage(Component.literal("泉: 灵泉入身"), true);
    }

    private static void castInkField(InkMark mark) {
        ServerPlayer owner = findOwner(mark);
        if (owner == null) {
            return;
        }
        if (mark.target().type() != InkTargetType.PLAYER) {
            owner.displayClientMessage(Component.literal("墨: 需施写于自身"), true);
            return;
        }

        InkFieldState active = inkFields.get(owner.getUUID());
        if (active != null && active.dimension().equals(owner.level().dimension())) {
            inkFields.remove(owner.getUUID());
            inkDurabilityDebt.remove(owner.getUUID());
            owner.displayClientMessage(Component.literal("墨: 墨染场收束"), true);
            return;
        }

        inkFields.put(owner.getUUID(), new InkFieldState(owner.getUUID(), owner.level().dimension(), InkStaffMetadata.tier(mark)));
        GlyphboundAdvancements.award(owner, GlyphboundAdvancements.INK_FIELD_ACTIVE);
        owner.displayClientMessage(Component.literal("墨: 墨染场展开，再写墨可收束"), true);
    }

    private static void castMountain(InkMark mark) {
        ServerPlayer owner = findOwner(mark);
        if (owner == null) {
            return;
        }
        if (mark.target().type() != InkTargetType.MARKER) {
            owner.displayClientMessage(Component.literal("山: 需用铭刻阵指定方块"), true);
            return;
        }
        ServerLevel level = owner.serverLevel();
        MountainSpec spec = mountainSpec(InkStaffMetadata.tier(mark));
        BlockPos target = BlockPos.of(mark.target().packedBlockPos());
        BlockPos base = level.getBlockState(target).isAir() ? target : target.above();
        Direction facing = owner.getDirection();
        Direction.Axis axis = facing.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;

        TemporaryTerrain terrain = new TemporaryTerrain(
            MOUNTAIN_WORD,
            owner.getUUID(),
            level.dimension(),
            level.getGameTime(),
            level.getGameTime() + spec.durationTicks(),
            base,
            InkStaffMetadata.tier(mark)
        );
        terrain.thornDamage = spec.thornDamage();

        for (int offset = -2; offset <= 2; offset++) {
            for (int y = 0; y < spec.height(); y++) {
                BlockPos pos = offset(base, axis, offset).above(y);
                BlockState original = level.getBlockState(pos);
                if (!canReplaceWithTemporary(level, pos, original)) {
                    owner.displayClientMessage(Component.literal("山: 此处地脉太重，不能立山"), true);
                    return;
                }
                terrain.addChange(pos, original, mountainState(spec, offset, y));
            }
        }

        if (applyTerrain(level, terrain)) {
            terrains.put(terrain.id, terrain);
            GlyphboundAdvancements.award(owner, GlyphboundAdvancements.MOUNTAIN_WALL);
            owner.displayClientMessage(Component.literal("山: 石脊立起"), true);
            level.sendParticles(ParticleTypes.POOF, base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D, 28, 2.8D, 0.9D, 2.8D, 0.03D);
        }
    }

    private static void castRift(InkMark mark) {
        ServerPlayer owner = findOwner(mark);
        if (owner == null) {
            return;
        }
        if (mark.target().type() != InkTargetType.MARKER) {
            owner.displayClientMessage(Component.literal("裂: 需用铭刻阵指定方块"), true);
            return;
        }

        ServerLevel level = owner.serverLevel();
        RiftSpec spec = riftSpec(InkStaffMetadata.tier(mark));
        BlockPos target = BlockPos.of(mark.target().packedBlockPos());
        BlockPos surface = level.getBlockState(target).isAir() ? target.below() : target;
        TemporaryTerrain terrain = new TemporaryTerrain(
            RIFT_WORD,
            owner.getUUID(),
            level.dimension(),
            level.getGameTime() + 20L,
            level.getGameTime() + spec.durationTicks(),
            surface,
            InkStaffMetadata.tier(mark)
        );
        terrain.riftSpec = spec;
        terrains.put(terrain.id, terrain);
        GlyphboundAdvancements.award(owner, GlyphboundAdvancements.RIFT_TRAP);
        owner.displayClientMessage(Component.literal("裂: 地鸣将开"), true);
        level.sendParticles(ParticleTypes.SMOKE, surface.getX() + 0.5D, surface.getY() + 1.0D, surface.getZ() + 0.5D, 20, spec.radius(), 0.2D, spec.radius(), 0.02D);
    }

    private static boolean tickInkField(ServerTickEvent.Post event, UUID ownerId, InkFieldState field, long gameTime) {
        ServerLevel level = event.getServer().getLevel(field.dimension);
        if (level == null) {
            return true;
        }
        ServerPlayer owner = event.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || !owner.level().dimension().equals(field.dimension)) {
            return true;
        }

        InkFieldSpec spec = inkFieldSpec(field.staffTier);
        if (gameTime % 10L == 0L) {
            drawInkFieldRing(level, owner, spec.radius());
            level.sendParticles(ParticleTypes.SQUID_INK, owner.getX(), owner.getY() + 0.8D, owner.getZ(), 5, spec.radius() * 0.25D, 0.25D, spec.radius() * 0.25D, 0.0D);
        }
        if (gameTime % 20L == 0L) {
            boostMobsInField(level, owner, spec, gameTime);
        }
        if (gameTime % spec.spawnIntervalTicks() == 0L) {
            spawnInkPressureMob(level, owner, spec);
        }
        return false;
    }

    private static void boostMobsInField(ServerLevel level, ServerPlayer owner, InkFieldSpec spec, long gameTime) {
        AABB area = owner.getBoundingBox().inflate(spec.radius());
        List<LivingEntity> mobs = level.getEntitiesOfClass(
            LivingEntity.class,
            area,
            entity -> entity instanceof Enemy && entity.isAlive() && entity.distanceTo(owner) <= spec.radius()
        );
        for (LivingEntity mob : mobs) {
            GlyphAttributes.setTransientAmount(mob, Attributes.MAX_HEALTH, INK_MAX_HEALTH, spec.mobHealthBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            GlyphAttributes.setTransientAmount(mob, Attributes.ATTACK_DAMAGE, INK_ATTACK_DAMAGE, spec.mobAttackBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            GlyphAttributes.setTransientAmount(mob, Attributes.MOVEMENT_SPEED, INK_MOVEMENT_SPEED, spec.mobSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if (mob.getHealth() < mob.getMaxHealth() * 0.55F) {
                mob.setHealth(Math.min(mob.getMaxHealth(), mob.getHealth() + mob.getMaxHealth() * 0.05F));
            }
            boostedMobs.put(mob.getUUID(), level.dimension());
            if (mob instanceof net.minecraft.world.entity.Mob aiMob && aiMob.getTarget() == null) {
                aiMob.setTarget(owner);
            }
        }
    }

    private static void spawnInkPressureMob(ServerLevel level, ServerPlayer owner, InkFieldSpec spec) {
        AABB area = owner.getBoundingBox().inflate(spec.radius());
        int nearbyEnemies = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity instanceof Enemy && entity.isAlive()).size();
        if (nearbyEnemies >= spec.maxPressureMobs()) {
            return;
        }

        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.max(4.0D, spec.radius() - 1.5D);
        BlockPos start = BlockPos.containing(owner.getX() + Math.cos(angle) * distance, owner.getY(), owner.getZ() + Math.sin(angle) * distance);
        BlockPos spawnPos = findSpawnPos(level, start);
        if (spawnPos == null) {
            return;
        }

        EntityType<? extends Mob> type = level.random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SPIDER;
        Mob mob = type.create(level);
        if (mob == null) {
            return;
        }
        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        mob.setTarget(owner);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.6D, mob.getZ(), 12, 0.4D, 0.35D, 0.4D, 0.02D);
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos start) {
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos pos = start.offset(0, dy, 0);
            if (level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static void cleanupMobBoosts(ServerTickEvent.Post event, long gameTime) {
        boostedMobs.entrySet().removeIf(entry -> {
            ServerLevel level = event.getServer().getLevel(entry.getValue());
            if (level == null) {
                return true;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                return true;
            }
            if (isInsideAnyInkField(living, gameTime)) {
                return false;
            }
            removeInkBoostAttributes(living);
            return true;
        });
    }

    private static void clearMobBoost(Entity entity) {
        removeInkBoostAttributes(entity);
        boostedMobs.remove(entity.getUUID());
    }

    private static void removeInkBoostAttributes(Entity entity) {
        if (entity instanceof LivingEntity living) {
            GlyphAttributes.remove(living, Attributes.MAX_HEALTH, INK_MAX_HEALTH);
            GlyphAttributes.remove(living, Attributes.ATTACK_DAMAGE, INK_ATTACK_DAMAGE);
            GlyphAttributes.remove(living, Attributes.MOVEMENT_SPEED, INK_MOVEMENT_SPEED);
            if (living.getHealth() > living.getMaxHealth()) {
                living.setHealth(living.getMaxHealth());
            }
        }
    }

    private static boolean isInInkField(ServerPlayer player, long gameTime) {
        return isInsideAnyInkField(player, gameTime);
    }

    private static InkStaffTier activeInkFieldTier(ServerPlayer player) {
        InkFieldState field = inkFields.get(player.getUUID());
        if (field != null && field.dimension().equals(player.level().dimension())) {
            return field.staffTier();
        }
        return InkStaffTier.WOOD;
    }

    private static boolean isInsideAnyInkField(LivingEntity entity, long gameTime) {
        for (InkFieldState field : inkFields.values()) {
            if (!field.dimension.equals(entity.level().dimension())) {
                continue;
            }
            Player owner = entity.level().getPlayerByUUID(field.owner);
            if (owner == null) {
                continue;
            }
            if (entity.distanceToSqr(owner) <= inkFieldSpec(field.staffTier).radius() * inkFieldSpec(field.staffTier).radius()) {
                return true;
            }
        }
        return false;
    }

    private static void healSpring(ServerPlayer player, float amount) {
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(amount);
        }
    }

    private static void drawInkFieldRing(ServerLevel level, ServerPlayer owner, double radius) {
        int points = 48;
        double y = owner.getY() + 0.12D;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = owner.getX() + Math.cos(angle) * radius;
            double z = owner.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.04D, 0.02D, 0.04D, 0.0D);
        }
    }

    private static void tickTerrains(ServerTickEvent.Post event, long gameTime) {
        for (TemporaryTerrain terrain : new ArrayList<>(terrains.values())) {
            ServerLevel level = event.getServer().getLevel(terrain.dimension);
            if (level == null) {
                terrains.remove(terrain.id);
                continue;
            }
            if (!terrain.applied && gameTime >= terrain.applyAt) {
                if (RIFT_WORD.equals(terrain.word)) {
                    if (!prepareRift(level, terrain)) {
                        terrains.remove(terrain.id);
                        notifyOwner(event, terrain, "裂: 此处地脉太硬，不能开裂");
                        continue;
                    }
                    applyTerrain(level, terrain);
                }
            }
            if (terrain.applied && gameTime >= terrain.expiresAt) {
                rollbackTerrain(level, terrain);
                continue;
            }
            if (terrain.applied && gameTime % 20L == 0L) {
                tickTerrainEffects(level, terrain, gameTime);
            }
        }
    }

    private static void notifyOwner(ServerTickEvent.Post event, TemporaryTerrain terrain, String message) {
        ServerPlayer owner = event.getServer().getPlayerList().getPlayer(terrain.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.literal(message), true);
        }
    }

    private static boolean prepareRift(ServerLevel level, TemporaryTerrain terrain) {
        RiftSpec spec = terrain.riftSpec;
        if (spec == null) {
            return false;
        }
        Set<BlockPos> planned = new HashSet<>();
        for (int dx = -spec.radius(); dx <= spec.radius(); dx++) {
            for (int dz = -spec.radius(); dz <= spec.radius(); dz++) {
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                int depth = spec.edgeShallow() && edge == spec.radius() ? 1 : 2;
                for (int dy = 0; dy < depth; dy++) {
                    BlockPos pos = terrain.origin.offset(dx, -dy, dz);
                    BlockState original = level.getBlockState(pos);
                    if (!canRemoveForRift(level, pos, original)) {
                        return false;
                    }
                    planned.add(pos);
                    terrain.addChange(pos, original, Blocks.AIR.defaultBlockState());
                }
            }
        }
        terrain.riftArea.addAll(planned);
        return !terrain.changes.isEmpty();
    }

    private static boolean applyTerrain(ServerLevel level, TemporaryTerrain terrain) {
        for (TerrainChange change : terrain.changes.values()) {
            level.setBlock(change.pos, change.temporary, 3);
            terrainBlocks.put(new TerrainBlockKey(terrain.dimension, change.pos), terrain.id);
        }
        terrain.applied = true;
        return true;
    }

    private static void rollbackTerrain(ServerLevel level, TemporaryTerrain terrain) {
        if (level != null) {
            for (TerrainChange change : new ArrayList<>(terrain.changes.values())) {
                BlockState current = level.getBlockState(change.pos);
                if (current == change.temporary || current.is(change.temporary.getBlock()) || current.isAir()) {
                    level.setBlock(change.pos, change.original, 3);
                }
                terrainBlocks.remove(new TerrainBlockKey(terrain.dimension, change.pos));
            }
        } else {
            for (TerrainChange change : terrain.changes.values()) {
                terrainBlocks.remove(new TerrainBlockKey(terrain.dimension, change.pos));
            }
        }
        terrains.remove(terrain.id);
    }

    private static void tickTerrainEffects(ServerLevel level, TemporaryTerrain terrain, long gameTime) {
        if (MOUNTAIN_WORD.equals(terrain.word)) {
            if (terrain.thornDamage > 0.0F) {
                AABB area = terrain.bounds().inflate(1.0D);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity instanceof Enemy && entity.isAlive())) {
                    if (isNearAny(terrain.changes.keySet(), entity.position(), 1.8D)) {
                        entity.hurt(level.damageSources().magic(), terrain.thornDamage);
                    }
                }
                level.sendParticles(ParticleTypes.CRIT, terrain.origin.getX() + 0.5D, terrain.origin.getY() + 1.5D, terrain.origin.getZ() + 0.5D, 10, 2.5D, 1.2D, 2.5D, 0.02D);
            }
            return;
        }

        if (!RIFT_WORD.equals(terrain.word) || terrain.riftSpec == null) {
            return;
        }
        RiftSpec spec = terrain.riftSpec;
        AABB area = new AABB(terrain.origin).inflate(spec.radius() + 0.8D, 2.2D, spec.radius() + 0.8D);
        if (spec.inkParticles()) {
            level.sendParticles(ParticleTypes.SQUID_INK, terrain.origin.getX() + 0.5D, terrain.origin.getY() - 1.2D, terrain.origin.getZ() + 0.5D, 10, spec.radius(), 0.25D, spec.radius(), 0.0D);
        }
        if (spec.smokeParticles()) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, terrain.origin.getX() + 0.5D, terrain.origin.getY() - 0.6D, terrain.origin.getZ() + 0.5D, 14, spec.radius(), 0.4D, spec.radius(), 0.02D);
        }

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            double localX = entity.getX() - (terrain.origin.getX() + 0.5D);
            double localZ = entity.getZ() - (terrain.origin.getZ() + 0.5D);
            boolean inHorizontal = Math.abs(localX) <= spec.radius() + 0.55D && Math.abs(localZ) <= spec.radius() + 0.55D;
            if (!inHorizontal) {
                continue;
            }
            if (entity instanceof ServerPlayer player && entity.getY() <= terrain.origin.getY() - 0.65D) {
                Long next = terrain.riftDamageCooldown.getOrDefault(player.getUUID(), 0L);
                if (next <= gameTime) {
                    player.hurt(level.damageSources().fall(), 2.0F);
                    terrain.riftDamageCooldown.put(player.getUUID(), gameTime + 40L);
                }
            }
            if (entity instanceof Enemy) {
                Vec3 away = entity.position().subtract(Vec3.atCenterOf(terrain.origin));
                if (away.horizontalDistanceSqr() > 0.001D && entity.getY() >= terrain.origin.getY() - 0.25D) {
                    Vec3 push = away.normalize().scale(0.08D);
                    entity.push(push.x, 0.0D, push.z);
                }
            }
            if (spec.slowEdges() && Math.max(Math.abs(localX), Math.abs(localZ)) >= spec.radius() - 0.6D) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false));
            }
        }
    }

    private static boolean isNearAny(Set<BlockPos> positions, Vec3 point, double radius) {
        double radiusSqr = radius * radius;
        for (BlockPos pos : positions) {
            if (Vec3.atCenterOf(pos).distanceToSqr(point) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static void removeOwnerTerrains(UUID owner, net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (TemporaryTerrain terrain : new ArrayList<>(terrains.values())) {
            if (terrain.owner.equals(owner)) {
                rollbackTerrain(server.getLevel(terrain.dimension), terrain);
            }
        }
    }

    private static boolean canReplaceWithTemporary(ServerLevel level, BlockPos pos, BlockState state) {
        return isSafeTerrainTarget(level, pos, state)
            && (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty());
    }

    private static boolean canRemoveForRift(ServerLevel level, BlockPos pos, BlockState state) {
        return isSafeTerrainTarget(level, pos, state) && !state.isAir();
    }

    private static boolean isSafeTerrainTarget(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null
            && !state.is(Blocks.BEDROCK)
            && !state.is(Blocks.OBSIDIAN)
            && !state.is(Blocks.CRYING_OBSIDIAN)
            && !state.is(Blocks.NETHER_PORTAL)
            && !state.is(Blocks.END_PORTAL)
            && !state.is(Blocks.END_PORTAL_FRAME)
            && !state.is(Blocks.COMMAND_BLOCK)
            && !state.is(Blocks.CHAIN_COMMAND_BLOCK)
            && !state.is(Blocks.REPEATING_COMMAND_BLOCK)
            && !state.is(Blocks.STRUCTURE_BLOCK)
            && !state.is(Blocks.JIGSAW)
            && !state.is(Blocks.BARRIER)
            && !state.is(Blocks.REDSTONE_WIRE)
            && !state.is(Blocks.REPEATER)
            && !state.is(Blocks.COMPARATOR)
            && !state.is(Blocks.REDSTONE_TORCH)
            && !state.is(Blocks.REDSTONE_WALL_TORCH)
            && !state.is(Blocks.LEVER);
    }

    private static BlockPos offset(BlockPos pos, Direction.Axis axis, int amount) {
        return axis == Direction.Axis.X ? pos.offset(amount, 0, 0) : pos.offset(0, 0, amount);
    }

    private static BlockState mountainState(MountainSpec spec, int offset, int y) {
        if (spec.glowing() && y == spec.height() - 1 && Math.abs(offset) <= 1) {
            return Blocks.GLOWSTONE.defaultBlockState();
        }
        return spec.wallState();
    }

    private static ServerPlayer findOwner(InkMark mark) {
        if (mark.owner() == null) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(mark.owner());
    }

    private static SpringSpec springSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new SpringSpec(5.0F, ticksSeconds(30));
            case STONE -> new SpringSpec(7.0F, ticksSeconds(25));
            case COPPER, IRON -> new SpringSpec(10.0F, ticksSeconds(15));
            case GOLD, DIAMOND -> new SpringSpec(14.0F, ticksSeconds(10));
            case NETHERITE -> new SpringSpec(16.0F, ticksSeconds(5));
        };
    }

    private static InkFieldSpec inkFieldSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new InkFieldSpec(8.0D, 2.0F, 1.00D, 0.50D, 0.20D, ticksSeconds(20), 18);
            case STONE -> new InkFieldSpec(10.0D, 2.2F, 0.85D, 0.42D, 0.18D, ticksSeconds(18), 18);
            case COPPER, IRON -> new InkFieldSpec(14.0D, 2.6F, 0.55D, 0.28D, 0.12D, ticksSeconds(14), 20);
            case GOLD, DIAMOND -> new InkFieldSpec(18.0D, 3.0F, 0.35D, 0.18D, 0.08D, ticksSeconds(10), 22);
            case NETHERITE -> new InkFieldSpec(22.0D, 4.0F, 0.20D, 0.10D, 0.05D, ticksSeconds(8), 24);
        };
    }

    private static MountainSpec mountainSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new MountainSpec(2, Blocks.STONE.defaultBlockState(), false, 0.0F, ticksSeconds(45));
            case STONE -> new MountainSpec(3, Blocks.COBBLESTONE.defaultBlockState(), false, 0.0F, ticksMinutes(1));
            case COPPER, IRON -> new MountainSpec(4, Blocks.STONE_BRICKS.defaultBlockState(), false, 0.0F, ticksMinutes(2));
            case GOLD, DIAMOND -> new MountainSpec(5, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), false, 1.0F, ticksMinutes(3));
            case NETHERITE -> new MountainSpec(5, Blocks.OBSIDIAN.defaultBlockState(), false, 1.5F, ticksMinutes(4));
        };
    }

    private static RiftSpec riftSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new RiftSpec(1, false, false, false, false, ticksSeconds(30));
            case STONE -> new RiftSpec(1, false, false, false, false, ticksSeconds(45));
            case COPPER, IRON -> new RiftSpec(1, false, true, true, false, ticksSeconds(90));
            case GOLD, DIAMOND -> new RiftSpec(2, false, true, true, false, ticksMinutes(2));
            case NETHERITE -> new RiftSpec(2, false, true, true, true, ticksMinutes(3));
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, path);
    }

    private static long ticksSeconds(int seconds) {
        return seconds * 20L;
    }

    private static long ticksMinutes(int minutes) {
        return ticksSeconds(minutes * 60);
    }

    private record SpringSpec(float healAmount, long cooldownTicks) {
    }

    private record InkFieldSpec(
        double radius,
        float playerMultiplier,
        double mobHealthBonus,
        double mobAttackBonus,
        double mobSpeedBonus,
        long spawnIntervalTicks,
        int maxPressureMobs
    ) {
    }

    private record InkFieldState(UUID owner, ResourceKey<Level> dimension, InkStaffTier staffTier) {
    }

    private record MountainSpec(int height, BlockState wallState, boolean glowing, float thornDamage, long durationTicks) {
    }

    private record RiftSpec(int radius, boolean edgeShallow, boolean inkParticles, boolean smokeParticles, boolean slowEdges, long durationTicks) {
    }

    private record TerrainBlockKey(ResourceKey<Level> dimension, BlockPos pos) {
        private TerrainBlockKey {
            pos = pos.immutable();
        }
    }

    private record TerrainChange(BlockPos pos, BlockState original, BlockState temporary) {
        private TerrainChange {
            pos = pos.immutable();
        }
    }

    private static final class TemporaryTerrain {
        private final UUID id = UUID.randomUUID();
        private final String word;
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final long applyAt;
        private final long expiresAt;
        private final BlockPos origin;
        private final InkStaffTier staffTier;
        private final Map<BlockPos, TerrainChange> changes = new LinkedHashMap<>();
        private final Set<BlockPos> riftArea = new HashSet<>();
        private final Map<UUID, Long> riftDamageCooldown = new HashMap<>();
        private boolean applied;
        private float thornDamage;
        private RiftSpec riftSpec;

        private TemporaryTerrain(String word, UUID owner, ResourceKey<Level> dimension, long applyAt, long expiresAt, BlockPos origin, InkStaffTier staffTier) {
            this.word = word;
            this.owner = owner;
            this.dimension = dimension;
            this.applyAt = applyAt;
            this.expiresAt = expiresAt;
            this.origin = origin.immutable();
            this.staffTier = staffTier;
        }

        private void addChange(BlockPos pos, BlockState original, BlockState temporary) {
            changes.put(pos.immutable(), new TerrainChange(pos, original, temporary));
        }

        private AABB bounds() {
            if (changes.isEmpty()) {
                return new AABB(origin);
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : changes.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
        }
    }

    private static ServerPlayer findPlayerForWorldGlyph(InkMark mark) {
        if (mark.owner() == null || mark.target().entityUuid() == null) {
            return null;
        }
        for (ServerLevel level : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            Entity entity = level.getEntity(mark.target().entityUuid());
            if (entity instanceof ServerPlayer player && player.getUUID().equals(mark.owner())) {
                return player;
            }
        }
        return null;
    }
}
