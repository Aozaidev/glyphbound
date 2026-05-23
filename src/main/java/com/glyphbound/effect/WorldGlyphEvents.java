package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffMetadata;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.core.event.InkMarkBeforeAttachEvent;
import com.aozainkmc.core.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphAttributes;
import com.glyphbound.core.GlyphboundItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class WorldGlyphEvents {
    private static final String SPRING_WORD = "泉";
    private static final String INK_FIELD_WORD = "墨";
    private static final ResourceLocation INK_MAX_HEALTH = id("ink_field_max_health");
    private static final ResourceLocation INK_ATTACK_DAMAGE = id("ink_field_attack_damage");
    private static final ResourceLocation INK_MOVEMENT_SPEED = id("ink_field_movement_speed");

    private static final Map<UUID, Long> springCooldowns = new HashMap<>();
    private static final Map<UUID, InkFieldState> inkFields = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> boostedMobs = new HashMap<>();
    private static final Map<UUID, Float> inkDurabilityDebt = new HashMap<>();
    private static final Map<UUID, Long> inkPlayerHitTargets = new HashMap<>();

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
            event.setCanceled(true);
            event.requestCloseInput("墨: 场内不可复写");
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
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        springCooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        inkPlayerHitTargets.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        inkFields.entrySet().removeIf(entry -> tickInkField(event, entry.getKey(), entry.getValue(), gameTime));
        if (gameTime % 20L == 0L) {
            cleanupMobBoosts(event, gameTime);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && isInInkField(player, player.level().getGameTime())) {
            event.setNewDamage(event.getNewDamage() * 3.0F);
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
            event.setAmount(event.getAmount() * 3.0F);
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
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            inkFields.remove(player.getUUID());
            springCooldowns.remove(player.getUUID());
            inkDurabilityDebt.remove(player.getUUID());
        }
        clearMobBoost(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        inkFields.remove(event.getEntity().getUUID());
        springCooldowns.remove(event.getEntity().getUUID());
        inkDurabilityDebt.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        inkFields.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        inkFields.clear();
        springCooldowns.clear();
        boostedMobs.clear();
        inkDurabilityDebt.clear();
        inkPlayerHitTargets.clear();
    }

    public static float healingMultiplier(ServerPlayer player) {
        return 1.0F;
    }

    public static boolean isInkFieldOwnerActive(UUID owner, String dimension, long gameTime) {
        InkFieldState field = inkFields.get(owner);
        return field != null
            && field.expiresAt > gameTime
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

        InkFieldSpec spec = inkFieldSpec(InkStaffMetadata.tier(mark));
        inkFields.put(owner.getUUID(), new InkFieldState(owner.getUUID(), owner.level().dimension(), InkStaffMetadata.tier(mark), owner.level().getGameTime() + spec.durationTicks()));
        owner.displayClientMessage(Component.literal("墨: 墨染场展开"), true);
    }

    private static boolean tickInkField(ServerTickEvent.Post event, UUID ownerId, InkFieldState field, long gameTime) {
        ServerLevel level = event.getServer().getLevel(field.dimension);
        if (level == null) {
            return true;
        }
        ServerPlayer owner = event.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || !owner.level().dimension().equals(field.dimension) || gameTime >= field.expiresAt) {
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
        if (gameTime % 80L == 0L && level.random.nextFloat() < 0.50F) {
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
            GlyphAttributes.setTransientAmount(mob, Attributes.MAX_HEALTH, INK_MAX_HEALTH, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            GlyphAttributes.setTransientAmount(mob, Attributes.ATTACK_DAMAGE, INK_ATTACK_DAMAGE, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            GlyphAttributes.setTransientAmount(mob, Attributes.MOVEMENT_SPEED, INK_MOVEMENT_SPEED, 0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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
        if (nearbyEnemies >= 24) {
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
            clearMobBoost(living);
            return true;
        });
    }

    private static void clearMobBoost(Entity entity) {
        if (entity instanceof LivingEntity living) {
            GlyphAttributes.remove(living, Attributes.MAX_HEALTH, INK_MAX_HEALTH);
            GlyphAttributes.remove(living, Attributes.ATTACK_DAMAGE, INK_ATTACK_DAMAGE);
            GlyphAttributes.remove(living, Attributes.MOVEMENT_SPEED, INK_MOVEMENT_SPEED);
            if (living.getHealth() > living.getMaxHealth()) {
                living.setHealth(living.getMaxHealth());
            }
        }
        boostedMobs.remove(entity.getUUID());
    }

    private static boolean isInInkField(ServerPlayer player, long gameTime) {
        return isInsideAnyInkField(player, gameTime);
    }

    private static boolean isInsideAnyInkField(LivingEntity entity, long gameTime) {
        for (InkFieldState field : inkFields.values()) {
            if (field.expiresAt <= gameTime || !field.dimension.equals(entity.level().dimension())) {
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
            case COPPER -> new SpringSpec(8.0F, ticksSeconds(20));
            case IRON -> new SpringSpec(10.0F, ticksSeconds(15));
            case GOLD -> new SpringSpec(12.0F, ticksSeconds(5));
            case DIAMOND -> new SpringSpec(14.0F, ticksSeconds(10));
            case NETHERITE -> new SpringSpec(16.0F, ticksSeconds(5));
        };
    }

    private static InkFieldSpec inkFieldSpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new InkFieldSpec(8.0D, ticksMinutes(2));
            case STONE -> new InkFieldSpec(10.0D, ticksMinutes(3));
            case COPPER -> new InkFieldSpec(12.0D, ticksMinutes(4));
            case IRON -> new InkFieldSpec(14.0D, ticksMinutes(5));
            case GOLD -> new InkFieldSpec(16.0D, ticksMinutes(4));
            case DIAMOND -> new InkFieldSpec(18.0D, ticksMinutes(6));
            case NETHERITE -> new InkFieldSpec(22.0D, ticksMinutes(8));
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

    private record InkFieldSpec(double radius, long durationTicks) {
    }

    private record InkFieldState(UUID owner, ResourceKey<Level> dimension, InkStaffTier staffTier, long expiresAt) {
    }
}
