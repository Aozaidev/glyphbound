package com.glyphbound.effect;

import com.aozainkmc.api.InkMark;
import com.aozainkmc.api.InkStaffProgress;
import com.aozainkmc.api.InkStaffMetadata;
import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkTargetType;
import com.aozainkmc.api.InkStaffs;
import com.aozainkmc.api.event.InkMarkBeforeAttachEvent;
import com.aozainkmc.api.event.InkMarkAttachedEvent;
import com.glyphbound.Glyphbound;
import com.glyphbound.core.GlyphboundAdvancements;
import com.glyphbound.core.GlyphboundItems;
import com.glyphbound.core.StaffTierUtils;
import com.glyphbound.effect.InkRealmEvents;
import com.glyphbound.world.InkRealmState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class CalamityGlyphEvents {
    private static final String ENTRY_WORD = "入";
    private static final String TRIBULATION_WORD = "劫";
    private static final String EXIT_WORD = "出";
    private static final int OBSIDIAN_RADIUS = 8;
    private static final int LIGHT_GRID_STEP = 8;
    private static final int BUILD_INTERVAL_TICKS = 5;
    private static final int EXIT_DELAY_TICKS = 60;
    private static final long COOLDOWN_TICKS = 20L * 60L * 2L;
    private static final int MIN_ARENA_CLEARANCE = 20;
    private static final int ARENA_SEARCH_STEP = 4;
    private static final double ELITE_SCALE_MULTIPLIER = 1.25D;
    private static final double BOSS_SCALE_MULTIPLIER = 1.50D;
    private static final String ARENA_MOB_TAG = "glyphbound_calamity_mob";

    private static final Map<UUID, CalamityArena> arenas = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    private CalamityGlyphEvents() {
    }

    @SubscribeEvent
    public static void onInkMarkBeforeAttach(InkMarkBeforeAttachEvent event) {
        InkMark mark = event.mark();
        if (mark.target().type() != InkTargetType.PLAYER) {
            return;
        }

        ServerPlayer player = event.player();
        String word = mark.word();
        if (ENTRY_WORD.equals(word)) {
            String failure = precheckEntryFailure(player, event.staffTier());
            if (failure != null) {
                event.setCanceled(true);
                event.requestCloseInput(failure);
            }
            return;
        }
        if (TRIBULATION_WORD.equals(word)) {
            String failure = precheckTribulationFailure(player);
            if (failure != null) {
                event.setCanceled(true);
                event.requestCloseInput(failure);
            }
        }
    }

    @SubscribeEvent
    public static void onInkMarkAttached(InkMarkAttachedEvent event) {
        InkMark mark = event.mark();
        if (mark.target().type() != InkTargetType.PLAYER) {
            return;
        }

        ServerPlayer player = findPlayer(mark);
        if (player == null) {
            return;
        }
        if (EXIT_WORD.equals(mark.word())) {
            if (InkRealmEvents.isInkRealm(player.level())) {
                InkRealmEvents.exitInkRealm(player);
                return;
            }
            CalamityArena arena = arenas.get(player.getUUID());
            if (arena != null && arena.awaitingExit()) {
                arena.startExit(player);
            }
            return;
        }
        if (ENTRY_WORD.equals(mark.word())) {
            if (InkRealmEvents.isInkRealm(player.level())) {
                player.displayClientMessage(Component.literal("入: 已在墨界"), true);
                return;
            }
            startEntry(player, InkStaffMetadata.tier(mark));
            return;
        }
        if (TRIBULATION_WORD.equals(mark.word())) {
            startTribulation(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        for (CalamityArena arena : List.copyOf(arenas.values())) {
            arena.tick(event.getServer().getLevel(arena.arenaDimension));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CalamityArena arena = arenas.get(player.getUUID());
        if (arena == null || !arena.combatStarted() || event.getNewDamage() < player.getHealth()) {
            return;
        }

        event.setNewDamage(0.0F);
        arena.fail(player, arena.fatalReason());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CalamityArena arena = arenas.remove(event.getEntity().getUUID());
        if (arena != null) {
            if (event.getEntity() instanceof ServerPlayer player) {
                arena.cleanup(player.serverLevel());
            } else {
                arena.cleanup(null);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (activeArenaAt(event.getLevel(), event.getPos()) != null) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("墨斗场内不可破坏方块"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (activeArenaAt(event.getLevel(), event.getPos()) != null) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("墨斗场内不可放置方块"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        if (monster.getTags().contains(ARENA_MOB_TAG)) {
            return;
        }
        if (activeArenaAt(event.getLevel(), monster.blockPosition()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityMobGriefing(EntityMobGriefingEvent event) {
        if (activeArenaAt(event.getEntity().level(), event.getEntity().blockPosition()) != null) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> activeArenaAt(event.getLevel(), pos) != null);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (CalamityArena arena : List.copyOf(arenas.values())) {
            arena.cleanup(event.getServer().getLevel(arena.arenaDimension));
        }
        arenas.clear();
        cooldowns.clear();
    }

    private static CalamityArena activeArenaAt(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!(level instanceof Level actualLevel)) {
            return null;
        }
        for (CalamityArena arena : arenas.values()) {
            if (arena.contains(actualLevel, pos)) {
                return arena;
            }
        }
        return null;
    }

    private static void startEntry(ServerPlayer player, InkStaffTier tier) {
        String failure = precheckEntryFailure(player, tier);
        if (failure != null) {
            player.displayClientMessage(Component.literal(failure), true);
            return;
        }

        if (tier == InkStaffTier.NETHERITE) {
            ItemStack staff = heldStaff(player);
            if (InkStaffProgress.isFull(staff, tier)) {
                GlyphboundAdvancements.award(player, GlyphboundAdvancements.NETHERITE_FULL);
                InkRealmEvents.enterInkRealm(player);
                return;
            }
        }
        ServerLevel level = player.serverLevel();
        CalamitySpec spec = entrySpec(tier);
        BlockPos arenaCenter = findArenaCenter(level, player.blockPosition(), spec);
        if (arenaCenter == null) {
            player.displayClientMessage(Component.literal("入: 上方空间拥挤"), true);
            return;
        }

        UUID staffId = matchingHeldStaffId(player, tier);
        long gameTime = player.serverLevel().getGameTime();
        CalamityArena arena = new CalamityArena(player, level, arenaCenter, spec, staffId, gameTime);
        arenas.put(player.getUUID(), arena);
        cooldowns.put(player.getUUID(), gameTime + COOLDOWN_TICKS);
        arena.begin(player);
        GlyphboundAdvancements.award(player, GlyphboundAdvancements.FIRST_CALAMITY);
    }

    private static void startTribulation(ServerPlayer player) {
        String failure = precheckTribulationFailure(player);
        if (failure != null) {
            player.displayClientMessage(Component.literal(failure), true);
            return;
        }

        ItemStack staff = heldStaff(player);
        InkStaffTier tier = InkStaffs.tier(staff).orElse(null);
        UUID staffId = InkStaffProgress.ensureInstanceId(staff);

        ServerLevel level = player.serverLevel();
        CalamitySpec spec = tribulationSpec(tier);
        BlockPos arenaCenter = findArenaCenter(level, player.blockPosition(), spec);
        if (arenaCenter == null) {
            player.displayClientMessage(Component.literal("劫: 上方空间拥挤"), true);
            return;
        }

        CalamityArena arena = new CalamityArena(player, level, arenaCenter, spec, staffId, level.getGameTime());
        arenas.put(player.getUUID(), arena);
        arena.begin(player);
        GlyphboundAdvancements.award(player, GlyphboundAdvancements.FIRST_TRIBULATION);
    }

    private static ItemStack heldStaff(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return InkStaffs.isStaff(stack) ? stack : player.getOffhandItem();
    }

    private static UUID matchingHeldStaffId(ServerPlayer player, InkStaffTier tier) {
        ItemStack staff = heldStaff(player);
        if (InkStaffs.tier(staff).orElse(null) != tier) {
            return null;
        }
        return InkStaffProgress.ensureInstanceId(staff);
    }

    private static ItemStack findStaffById(ServerPlayer player, UUID staffId) {
        if (staffId == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (InkStaffProgress.instanceId(stack).filter(staffId::equals).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ServerPlayer findPlayer(InkMark mark) {
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

    private static String precheckEntryFailure(ServerPlayer player, InkStaffTier tier) {
        long gameTime = player.serverLevel().getGameTime();
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return "入: 和平难度无法触发";
        }
        if (tier == InkStaffTier.NETHERITE) {
            ItemStack staff = heldStaff(player);
            if (InkStaffProgress.isFull(staff, tier)) {
                return null;
            }
        }
        if (arenas.containsKey(player.getUUID()) || cooldowns.getOrDefault(player.getUUID(), 0L) > gameTime) {
            return "入: 墨斗尚未平息";
        }
        ServerLevel level = player.serverLevel();
        CalamitySpec spec = entrySpec(tier);
        BlockPos arenaCenter = findArenaCenter(level, player.blockPosition(), spec);
        if (arenaCenter == null) {
            return "入: 上方空间拥挤";
        }
        return null;
    }

    private static String precheckTribulationFailure(ServerPlayer player) {
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return "劫: 和平难度无法触发";
        }
        if (arenas.containsKey(player.getUUID())) {
            return "劫: 当前已有挑战";
        }
        ItemStack staff = heldStaff(player);
        InkStaffTier tier = InkStaffs.tier(staff).orElse(null);
        if (tier == null) {
            return "劫: 需要手持魔杖";
        }
        if (!tier.canUpgrade()) {
            return "劫: 此杖已至极境";
        }
        if (!InkStaffProgress.isFull(staff, tier)) {
            return "劫: 魔杖境界未满";
        }
        if (InkStaffProgress.isBreakthroughReady(staff)) {
            return "劫: 此杖已渡劫，可升级";
        }
        ServerLevel level = player.serverLevel();
        CalamitySpec spec = tribulationSpec(tier);
        BlockPos arenaCenter = findArenaCenter(level, player.blockPosition(), spec);
        if (arenaCenter == null) {
            return "劫: 上方空间拥挤";
        }
        return null;
    }

    private static BlockPos findArenaCenter(ServerLevel level, BlockPos source, CalamitySpec spec) {
        int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, source).getY();
        int minY = Math.max(surfaceY + MIN_ARENA_CLEARANCE, level.getMinBuildHeight() + 5);
        int maxY = level.getMaxBuildHeight() - spec.wallHeight() - 4;
        for (int y = minY; y <= maxY; y += ARENA_SEARCH_STEP) {
            BlockPos center = new BlockPos(source.getX(), y, source.getZ());
            if (hasBuildSpace(level, center, spec)) {
                return center;
            }
        }
        for (int y = Math.max(minY, maxY - ARENA_SEARCH_STEP + 1); y <= maxY; y++) {
            BlockPos center = new BlockPos(source.getX(), y, source.getZ());
            if (hasBuildSpace(level, center, spec)) {
                return center;
            }
        }
        return null;
    }

    private static boolean hasBuildSpace(ServerLevel level, BlockPos center, CalamitySpec spec) {
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-spec.arenaRadius() - 1, -1, -spec.arenaRadius() - 1),
            center.offset(spec.arenaRadius() + 1, spec.wallHeight() + 1, spec.arenaRadius() + 1)
        )) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > (spec.arenaRadius() + 1) * (spec.arenaRadius() + 1)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static CalamitySpec entrySpec(InkStaffTier tier) {
        return switch (tier) {
            case WOOD -> new CalamitySpec(tier, 2, List.of(
                new WaveSpec(3, 0, false),
                new WaveSpec(4, 0, false)
            ), 1.0D, 1.0D, 1.0D, 0.0D, 0.0D, ChallengeKind.ENTRY, 24, 12, "入", "墨斗", 5);
            case STONE -> new CalamitySpec(tier, 3, List.of(
                new WaveSpec(3, 0, false),
                new WaveSpec(4, 0, false),
                new WaveSpec(5, 0, false)
            ), 1.25D, 1.08D, 1.04D, 1.6D, 1.18D, ChallengeKind.ENTRY, 24, 12, "入", "墨斗", 8);
            case COPPER, IRON -> new CalamitySpec(tier, 4, List.of(
                new WaveSpec(4, 0, false),
                new WaveSpec(5, 0, false),
                new WaveSpec(3, 2, false),
                new WaveSpec(5, 1, false)
            ), 1.5D, 1.18D, 1.08D, 1.95D, 1.30D, ChallengeKind.ENTRY, 24, 12, "入", "墨斗", 12);
            case GOLD, DIAMOND -> new CalamitySpec(tier, 6, List.of(
                new WaveSpec(5, 0, false),
                new WaveSpec(6, 0, false),
                new WaveSpec(4, 2, false),
                new WaveSpec(4, 3, false),
                new WaveSpec(2, 4, false),
                new WaveSpec(0, 2, true)
            ), 2.0D, 1.35D, 1.15D, 2.5D, 1.45D, ChallengeKind.ENTRY, 24, 12, "入", "墨斗", 15);
            case NETHERITE -> new CalamitySpec(tier, 6, List.of(
                new WaveSpec(6, 0, false),
                new WaveSpec(6, 1, false),
                new WaveSpec(4, 3, false),
                new WaveSpec(4, 3, false),
                new WaveSpec(2, 5, false),
                new WaveSpec(0, 3, true)
            ), 2.15D, 1.40D, 1.16D, 2.75D, 1.50D, ChallengeKind.ENTRY, 24, 12, "入", "墨斗", 15);
        };
    }

    private static CalamitySpec tribulationSpec(InkStaffTier tier) {
        CalamitySpec base = entrySpec(tier);
        List<WaveSpec> waves = new ArrayList<>(base.waves());
        waves.add(new WaveSpec(2 + StaffTierUtils.tierRank(tier), Math.max(1, StaffTierUtils.tierRank(tier) / 2 + 1), true));
        return new CalamitySpec(
            tier,
            waves.size(),
            waves,
            base.normalHealthMultiplier() * 1.20D,
            base.normalAttackMultiplier() * 1.15D,
            base.normalSpeedMultiplier(),
            Math.max(2.0D, base.eliteHealthMultiplier()) * 1.20D,
            Math.max(1.3D, base.eliteAttackMultiplier()) * 1.15D,
            ChallengeKind.TRIBULATION,
            32,
            16,
            "劫",
            "墨劫",
            0
        );
    }

    private record CalamitySpec(
        InkStaffTier tier,
        int waveCount,
        List<WaveSpec> waves,
        double normalHealthMultiplier,
        double normalAttackMultiplier,
        double normalSpeedMultiplier,
        double eliteHealthMultiplier,
        double eliteAttackMultiplier,
        ChallengeKind kind,
        int arenaRadius,
        int wallHeight,
        String wordLabel,
        String eventName,
        int entryProgressReward
    ) {
    }

    private enum ChallengeKind {
        ENTRY,
        TRIBULATION
    }

    private record WaveSpec(int normalCount, int eliteCount, boolean boss) {
    }

    private enum Phase {
        BUILDING,
        COMBAT,
        VICTORY_WAITING,
        EXITING,
        DONE
    }

    private static final class CalamityArena {
        private final UUID playerId;
        private final ResourceKey<Level> arenaDimension;
        private final BlockPos center;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final float originYRot;
        private final float originXRot;
        private final float originHealth;
        private final int originFood;
        private final float originSaturation;
        private final CalamitySpec spec;
        private final UUID staffId;
        private final ServerBossEvent bossBar;
        private final Set<BlockPos> placedBlocks = new HashSet<>();
        private final Set<UUID> waveMobs = new HashSet<>();
        private final Random random = new Random();
        private Phase phase = Phase.BUILDING;
        private int buildRadius = OBSIDIAN_RADIUS;
        private int buildTicks;
        private int exitTicks;
        private int waveIndex = -1;

        private CalamityArena(ServerPlayer player, ServerLevel level, BlockPos center, CalamitySpec spec, UUID staffId, long gameTime) {
            this.playerId = player.getUUID();
            this.arenaDimension = level.dimension();
            this.center = center;
            this.originX = player.getX();
            this.originY = player.getY();
            this.originZ = player.getZ();
            this.originYRot = player.getYRot();
            this.originXRot = player.getXRot();
            this.originHealth = player.getHealth();
            this.originFood = player.getFoodData().getFoodLevel();
            this.originSaturation = player.getFoodData().getSaturationLevel();
            this.spec = spec;
            this.staffId = staffId;
            this.bossBar = new ServerBossEvent(
                Component.literal(spec.eventName() + ": 筑场"),
                BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS
            );
            this.bossBar.setProgress(0.0F);
        }

        private void begin(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            buildInitialPlatform(level);
            player.teleportTo(level, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, player.getYRot(), player.getXRot());
            bossBar.addPlayer(player);
            player.displayClientMessage(Component.literal(spec.wordLabel() + ": " + spec.eventName() + "筑场"), true);
        }

        private boolean combatStarted() {
            return phase == Phase.COMBAT;
        }

        private boolean awaitingExit() {
            return phase == Phase.VICTORY_WAITING;
        }

        private String fatalReason() {
            return spec.eventName() + "破阵";
        }

        private void tick(ServerLevel level) {
            if (level == null || phase == Phase.DONE) {
                cleanup(level);
                return;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                cleanup(level);
                return;
            }

            if (phase == Phase.BUILDING) {
                tickBuilding(level, player);
                return;
            }
            if (phase == Phase.VICTORY_WAITING) {
                tickVictoryWaiting(level, player);
                return;
            }
            if (phase == Phase.EXITING) {
                tickExit(level, player);
                return;
            }

            tickCombat(level, player);
        }

        private void tickBuilding(ServerLevel level, ServerPlayer player) {
            buildTicks++;
            level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.8D, 0.6D, 0.8D, 0.01D);
            bossBar.setProgress(Mth.clamp((float) (buildRadius - OBSIDIAN_RADIUS) / (float) (spec.arenaRadius() - OBSIDIAN_RADIUS), 0.0F, 1.0F));
            if (buildTicks % BUILD_INTERVAL_TICKS != 0) {
                return;
            }

            buildRadius++;
            buildFloorRing(level, buildRadius);
            if (buildRadius >= spec.arenaRadius()) {
                buildWallAndRoof(level);
                phase = Phase.COMBAT;
                spawnNextWave(level, player);
            }
        }

        private void tickCombat(ServerLevel level, ServerPlayer player) {
            if (player.serverLevel() != level) {
                fail(player, spec.eventName() + "离场");
                return;
            }
            if (player.distanceToSqr(center.getX() + 0.5D, player.getY(), center.getZ() + 0.5D) > (spec.arenaRadius() - 1) * (spec.arenaRadius() - 1)) {
                player.teleportTo(level, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, player.getYRot(), player.getXRot());
            }

            waveMobs.removeIf(uuid -> {
                Entity entity = level.getEntity(uuid);
                return !(entity instanceof LivingEntity living) || !living.isAlive();
            });
            for (UUID uuid : waveMobs) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof Mob mob && mob.getTarget() == null && distanceInArena(mob) <= spec.arenaRadius() + 2) {
                    mob.setTarget(player);
                }
            }

            updateBossBar();
            if (waveMobs.isEmpty()) {
                if (waveIndex + 1 >= spec.waves().size()) {
                    completeWaves(level, player);
                } else {
                    spawnNextWave(level, player);
                }
            }
        }

        private void tickVictoryWaiting(ServerLevel level, ServerPlayer player) {
            keepPlayerInArena(level, player);
            bossBar.setName(Component.literal(spec.eventName() + "已清: 写 出 离开"));
            bossBar.setProgress(1.0F);
            if (level.getGameTime() % 40L == 0L) {
                player.displayClientMessage(Component.literal(spec.wordLabel() + ": 写「出」离开，未拾取掉落物会带回脚下"), true);
            }
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5D, center.getY() + 1.2D, center.getZ() + 0.5D, 10, 4.0D, 0.2D, 4.0D, 0.02D);
        }

        private void tickExit(ServerLevel level, ServerPlayer player) {
            keepPlayerInArena(level, player);
            exitTicks--;
            double progress = 1.0D - Math.max(0, exitTicks) / (double) EXIT_DELAY_TICKS;
            bossBar.setProgress((float) progress);
            bossBar.setName(Component.literal(spec.eventName() + ": 归途"));
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.7D, 0.9D, 0.7D, 0.08D);
            level.sendParticles(ParticleTypes.SQUID_INK, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, 10, 6.0D, 0.4D, 6.0D, 0.01D);
            if (exitTicks <= 0) {
                win(level, player);
            }
        }

        private void spawnNextWave(ServerLevel level, ServerPlayer player) {
            waveIndex++;
            WaveSpec wave = spec.waves().get(waveIndex);
            waveMobs.clear();
            for (int i = 0; i < wave.normalCount(); i++) {
                spawnMob(level, player, false, false, i);
            }
            for (int i = 0; i < wave.eliteCount(); i++) {
                spawnMob(level, player, true, false, i);
            }
            if (wave.boss()) {
                spawnMob(level, player, true, true, 99);
            }
            updateBossBar();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.getX() + 0.5D, center.getY() + 1.5D, center.getZ() + 0.5D, 80, 6.0D, 0.5D, 6.0D, 0.05D);
            player.displayClientMessage(Component.literal(spec.wordLabel() + ": 第 " + (waveIndex + 1) + "/" + spec.waveCount() + " 波"), true);
        }

        private void spawnMob(ServerLevel level, ServerPlayer player, boolean elite, boolean boss, int index) {
            EntityType<? extends Monster> type = pickMobType(elite, boss);
            Monster mob = type.create(level);
            if (mob == null) {
                return;
            }

            BlockPos spawnPos = spawnPos(index);
            mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            mob.setPersistenceRequired();
            mob.setTarget(player);
            if (elite || boss) {
                mob.setCustomName(Component.literal(boss ? spec.eventName() + "首领" : spec.eventName() + "精英"));
                mob.setCustomNameVisible(true);
                mob.setGlowingTag(true);
            } else {
                mob.setCustomName(Component.literal(spec.eventName() + "爪牙"));
                mob.setCustomNameVisible(true);
            }
            mob.addTag(ARENA_MOB_TAG);
            applyMobStats(mob, elite, boss);
            level.addFreshEntity(mob);
            waveMobs.add(mob.getUUID());
        }

        @SuppressWarnings("unchecked")
        private EntityType<? extends Monster> pickMobType(boolean elite, boolean boss) {
            if (boss) {
                return (EntityType<? extends Monster>) EntityType.ZOMBIE;
            }
            int pick = random.nextInt(elite ? 3 : 4);
            if (pick == 0) {
                return (EntityType<? extends Monster>) EntityType.ZOMBIE;
            }
            if (pick == 1) {
                return (EntityType<? extends Monster>) EntityType.SKELETON;
            }
            if (pick == 2) {
                return (EntityType<? extends Monster>) EntityType.SPIDER;
            }
            return (EntityType<? extends Monster>) EntityType.HUSK;
        }

        private void applyMobStats(Monster mob, boolean elite, boolean boss) {
            double healthMultiplier = elite ? spec.eliteHealthMultiplier() : spec.normalHealthMultiplier();
            double attackMultiplier = elite ? spec.eliteAttackMultiplier() : spec.normalAttackMultiplier();
            double speedMultiplier = spec.normalSpeedMultiplier();
            if (boss) {
                healthMultiplier = bossHealthMultiplier(spec);
                attackMultiplier = bossAttackMultiplier(spec);
                speedMultiplier = Math.max(spec.normalSpeedMultiplier(), 1.05D);
            }

            multiplyAttribute(mob, Attributes.MAX_HEALTH, healthMultiplier);
            mob.setHealth(mob.getMaxHealth());
            multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, attackMultiplier);
            multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, speedMultiplier);
            if (boss) {
                multiplyAttribute(mob, Attributes.SCALE, BOSS_SCALE_MULTIPLIER);
            } else if (elite) {
                multiplyAttribute(mob, Attributes.SCALE, ELITE_SCALE_MULTIPLIER);
            }
            if (mob instanceof Zombie zombie && boss) {
                zombie.setBaby(false);
            }
            if (mob instanceof Zombie || mob instanceof Skeleton) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
            if (mob instanceof Skeleton skeleton) {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
            if (mob instanceof Skeleton skeleton && elite) {
                skeleton.setCanPickUpLoot(false);
            }
            if (mob instanceof Spider spider && elite) {
                spider.setCanPickUpLoot(false);
            }
        }

        private void multiplyAttribute(LivingEntity entity, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double multiplier) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(instance.getBaseValue() * multiplier);
            }
        }

        private double bossHealthMultiplier(CalamitySpec spec) {
            double[] entry = {0.0D, 0.0D, 0.0D, 3.2D, 4.0D};
            double[] tribulation = {2.0D, 2.6D, 3.2D, 4.0D, 4.8D};
            double[] values = spec.kind() == ChallengeKind.TRIBULATION ? tribulation : entry;
            return values[Math.min(values.length - 1, StaffTierUtils.tierRank(spec.tier()))];
        }

        private double bossAttackMultiplier(CalamitySpec spec) {
            double[] entry = {0.0D, 0.0D, 0.0D, 1.45D, 1.6D};
            double[] tribulation = {1.2D, 1.35D, 1.5D, 1.7D, 1.9D};
            double[] values = spec.kind() == ChallengeKind.TRIBULATION ? tribulation : entry;
            return values[Math.min(values.length - 1, StaffTierUtils.tierRank(spec.tier()))];
        }

        private BlockPos spawnPos(int index) {
            double angle = (Math.PI * 2.0D / 9.0D) * index + random.nextDouble() * 0.8D;
            int radius = 11 + random.nextInt(8);
            int x = center.getX() + Mth.floor(Math.cos(angle) * radius);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * radius);
            return new BlockPos(x, center.getY() + 1, z);
        }

        private double distanceInArena(Entity entity) {
            double dx = entity.getX() - (center.getX() + 0.5D);
            double dz = entity.getZ() - (center.getZ() + 0.5D);
            return Math.sqrt(dx * dx + dz * dz);
        }

        private void updateBossBar() {
            int remaining = waveMobs.size();
            bossBar.setName(Component.literal(spec.eventName() + ": " + (waveIndex + 1) + "/" + spec.waveCount() + " 波  剩余 " + remaining));
            float waveBase = (float) Math.max(0, waveIndex) / (float) spec.waveCount();
            float waveProgress = remaining == 0 ? 1.0F : 0.15F;
            bossBar.setProgress(Mth.clamp(waveBase + waveProgress / (float) spec.waveCount(), 0.0F, 1.0F));
        }

        private void completeWaves(ServerLevel level, ServerPlayer player) {
            phase = Phase.VICTORY_WAITING;
            completeChallenge(player);
            restorePlayer(player, true);
            waveMobs.clear();
            bossBar.setName(Component.literal(spec.eventName() + "已清: 写 出 离开"));
            bossBar.setProgress(1.0F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 40, 1.0D, 1.0D, 1.0D, 0.05D);
            player.displayClientMessage(Component.literal(spec.wordLabel() + ": " + spec.eventName() + "已清，写「出」离开"), true);
        }

        private void startExit(ServerPlayer player) {
            if (phase != Phase.VICTORY_WAITING) {
                return;
            }
            phase = Phase.EXITING;
            exitTicks = EXIT_DELAY_TICKS;
            bossBar.setName(Component.literal(spec.eventName() + ": 归途"));
            bossBar.setProgress(0.0F);
            player.displayClientMessage(Component.literal("出: 墨门开启"), true);
        }

        private void win(ServerLevel level, ServerPlayer player) {
            phase = Phase.DONE;
            List<ItemEntity> drops = arenaDrops(level);
            player.teleportTo(level, originX, originY, originZ, originYRot, originXRot);
            moveDropsToOrigin(drops);
            player.displayClientMessage(Component.literal("出: 已离开" + spec.eventName()), true);
            cleanup(level);
            arenas.remove(playerId);
        }

        private void fail(ServerPlayer player, String reason) {
            phase = Phase.DONE;
            ServerLevel level = player.serverLevel();
            player.teleportTo(level, originX, originY, originZ, originYRot, originXRot);
            restorePlayer(player, false);
            if (spec.kind() == ChallengeKind.TRIBULATION) {
                GlyphboundAdvancements.award(player, GlyphboundAdvancements.TRIBULATION_VICTORY);
                ItemStack staff = findStaffById(player, staffId);
                if (!staff.isEmpty() && InkStaffs.tier(staff).orElse(null) == spec.tier()) {
                    InkStaffProgress.halveProgressRoundUp(staff);
                }
            }
            player.displayClientMessage(Component.literal(spec.wordLabel() + ": " + reason), true);
            cleanup(level);
            arenas.remove(playerId);
        }

        private void restorePlayer(ServerPlayer player, boolean victory) {
            FoodData food = player.getFoodData();
            if (victory) {
                player.setHealth(player.getMaxHealth());
                food.setFoodLevel(20);
                food.setSaturation(20.0F);
                return;
            }
            player.setHealth(Math.max(2.0F, originHealth * 0.5F));
            food.setFoodLevel(originFood);
            food.setSaturation(originSaturation);
        }

        private void completeChallenge(ServerPlayer player) {
            if (spec.kind() == ChallengeKind.TRIBULATION) {
                ItemStack staff = findStaffById(player, staffId);
                if (!staff.isEmpty() && InkStaffs.tier(staff).orElse(null) == spec.tier()) {
                    InkStaffProgress.setBreakthroughReady(staff, true);
                    player.displayClientMessage(Component.literal("劫: 渡劫成功，此杖可升级"), true);
                } else {
                    player.displayClientMessage(Component.literal("劫: 原杖不在身上，无法标记升级"), true);
                }
                giveRewards(player);
                return;
            }

            GlyphboundAdvancements.award(player, GlyphboundAdvancements.CALAMITY_VICTORY);
            giveRewards(player);
            ItemStack staff = findStaffById(player, staffId);
            if (!staff.isEmpty()
                && InkStaffs.tier(staff).orElse(null) == spec.tier()) {
                boolean filled = InkStaffProgress.addProgress(staff, spec.tier(), spec.entryProgressReward());
                if (filled) {
                    if (spec.tier() == InkStaffTier.NETHERITE) {
                        GlyphboundAdvancements.award(player, GlyphboundAdvancements.NETHERITE_FULL);
                        player.displayClientMessage(Component.literal("入: 笔势 +" + spec.entryProgressReward() + "，笔势已满，可写「入」前往墨界"), true);
                    } else {
                        player.displayClientMessage(Component.literal("入: 笔势 +" + spec.entryProgressReward() + "，境界已满，可写「劫」"), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal("入: 笔势 +" + spec.entryProgressReward()), true);
                }
            }
        }

        private void giveRewards(ServerPlayer player) {
            List<ItemStack> rewards = new ArrayList<>();
            net.minecraft.util.RandomSource random = player.getRandom();
            switch (spec.tier()) {
                case WOOD -> {
                    rewards.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 1, 2)));
                    rewards.add(new ItemStack(Items.COAL, randomBetween(random, 6, 10)));
                    rewards.add(new ItemStack(Items.BREAD, 2));
                }
                case STONE -> {
                    rewards.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 3, 4)));
                    rewards.add(new ItemStack(Items.COAL, randomBetween(random, 8, 12)));
                    rewards.add(new ItemStack(Items.BREAD, randomBetween(random, 2, 3)));
                }
                case COPPER, IRON -> {
                    rewards.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 5, 6)));
                    rewards.add(new ItemStack(Items.COAL, randomBetween(random, 10, 14)));
                    rewards.add(new ItemStack(Items.BREAD, 3));
                    if (random.nextFloat() < 0.60F) {
                        rewards.add(new ItemStack(Items.GOLD_INGOT, 1));
                    }
                    if (random.nextFloat() < 0.25F) {
                        rewards.add(new ItemStack(Items.DIAMOND, 1));
                    }
                }
                case GOLD, DIAMOND -> {
                    rewards.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 7, 8)));
                    rewards.add(new ItemStack(Items.COAL, randomBetween(random, 12, 16)));
                    rewards.add(new ItemStack(Items.BREAD, randomBetween(random, 3, 4)));
                    rewards.add(new ItemStack(Items.GOLD_INGOT, randomBetween(random, 1, 2)));
                    if (random.nextFloat() < 0.75F) {
                        rewards.add(new ItemStack(Items.DIAMOND, randomBetween(random, 1, 2)));
                    }
                    if (random.nextFloat() < 0.35F) {
                        rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
                    }
                    rewards.add(new ItemStack(Items.LAPIS_LAZULI, 16));
                    if (spec.tier() == InkStaffTier.GOLD && random.nextFloat() < 0.03F) {
                        rewards.add(new ItemStack(Items.NETHERITE_SCRAP, 1));
                    }
                }
                case NETHERITE -> {
                    rewards.add(new ItemStack(Items.IRON_INGOT, randomBetween(random, 9, 10)));
                    rewards.add(new ItemStack(Items.COAL, randomBetween(random, 14, 18)));
                    rewards.add(new ItemStack(Items.BREAD, randomBetween(random, 4, 5)));
                    rewards.add(new ItemStack(Items.GOLD_INGOT, randomBetween(random, 2, 3)));
                    rewards.add(new ItemStack(Items.DIAMOND, randomBetween(random, 2, 3)));
                    if (random.nextFloat() < 0.50F) {
                        rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
                    }
                    rewards.add(new ItemStack(Items.LAPIS_LAZULI, 24));
                    rewards.add(new ItemStack(Items.NETHERITE_SCRAP, 1));
                }
            }
            if (spec.kind() == ChallengeKind.ENTRY) {
                rewards.add(new ItemStack(GlyphboundItems.INK_CORE.get(), inkCoreReward(player)));
                GlyphboundAdvancements.award(player, GlyphboundAdvancements.INK_CORE_DROP);
            }
            for (ItemStack stack : rewards) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }

        private int inkCoreReward(ServerPlayer player) {
            int min = StaffTierUtils.tierRank(spec.tier());
            return min + player.getRandom().nextInt(2);
        }

        private static int randomBetween(net.minecraft.util.RandomSource random, int min, int max) {
            if (max <= min) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }

        private void cleanup(ServerLevel level) {
            phase = Phase.DONE;
            bossBar.removeAllPlayers();
            if (level == null) {
                return;
            }
            for (UUID uuid : waveMobs) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
            waveMobs.clear();
            discardArenaDrops(level);
            for (BlockPos pos : placedBlocks) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            placedBlocks.clear();
        }

        private void keepPlayerInArena(ServerLevel level, ServerPlayer player) {
            if (player.serverLevel() != level) {
                fail(player, spec.eventName() + "离场");
                return;
            }
            if (player.distanceToSqr(center.getX() + 0.5D, player.getY(), center.getZ() + 0.5D) > (spec.arenaRadius() - 1) * (spec.arenaRadius() - 1)) {
                player.teleportTo(level, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, player.getYRot(), player.getXRot());
            }
        }

        private List<ItemEntity> arenaDrops(ServerLevel level) {
            return level.getEntitiesOfClass(ItemEntity.class, arenaBox(), item -> item.isAlive());
        }

        private void discardArenaDrops(ServerLevel level) {
            for (ItemEntity item : arenaDrops(level)) {
                item.discard();
            }
        }

        private void moveDropsToOrigin(List<ItemEntity> drops) {
            for (ItemEntity item : drops) {
                if (item.isAlive()) {
                    item.moveTo(originX, originY + 0.1D, originZ, item.getYRot(), item.getXRot());
                    item.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    item.setPickUpDelay(20);
                }
            }
        }

        private AABB arenaBox() {
            return new AABB(
                center.getX() - spec.arenaRadius() - 1,
                center.getY() - 3,
                center.getZ() - spec.arenaRadius() - 1,
                center.getX() + spec.arenaRadius() + 1,
                center.getY() + spec.wallHeight() + 3,
                center.getZ() + spec.arenaRadius() + 1
            );
        }

        private boolean contains(Level level, BlockPos pos) {
            return level.dimension() == arenaDimension && arenaBox().contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }

        private void buildInitialPlatform(ServerLevel level) {
            for (int dx = -OBSIDIAN_RADIUS; dx <= OBSIDIAN_RADIUS; dx++) {
                for (int dz = -OBSIDIAN_RADIUS; dz <= OBSIDIAN_RADIUS; dz++) {
                    if (dx * dx + dz * dz <= OBSIDIAN_RADIUS * OBSIDIAN_RADIUS) {
                        place(level, center.offset(dx, 0, dz), Blocks.OBSIDIAN.defaultBlockState());
                    }
                }
            }
            lightAreaBelow(level);
        }

        private void buildFloorRing(ServerLevel level, int radius) {
            int outer = radius * radius;
            int inner = (radius - 1) * (radius - 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int dist = dx * dx + dz * dz;
                    if (dist <= outer && dist > inner) {
                        place(level, center.offset(dx, 0, dz), Blocks.DEEPSLATE.defaultBlockState());
                    }
                }
            }
        }

        private void buildWallAndRoof(ServerLevel level) {
            for (int dx = -spec.arenaRadius(); dx <= spec.arenaRadius(); dx++) {
                for (int dz = -spec.arenaRadius(); dz <= spec.arenaRadius(); dz++) {
                    int dist = dx * dx + dz * dz;
                    if (dist <= spec.arenaRadius() * spec.arenaRadius()) {
                        place(level, center.offset(dx, spec.wallHeight(), dz), Blocks.BARRIER.defaultBlockState());
                    }
                    if (dist <= spec.arenaRadius() * spec.arenaRadius() && dist >= (spec.arenaRadius() - 1) * (spec.arenaRadius() - 1)) {
                        for (int dy = 1; dy <= spec.wallHeight(); dy++) {
                            place(level, center.offset(dx, dy, dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                        }
                    }
                }
            }
        }

        private void lightAreaBelow(ServerLevel level) {
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
            for (int yOffset : List.of(-2, -10, -18)) {
                int y = center.getY() + yOffset;
                if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                for (int dx = -spec.arenaRadius(); dx <= spec.arenaRadius(); dx += LIGHT_GRID_STEP) {
                    for (int dz = -spec.arenaRadius(); dz <= spec.arenaRadius(); dz += LIGHT_GRID_STEP) {
                        BlockPos pos = center.offset(dx, yOffset, dz);
                        if (level.getBlockState(pos).isAir()) {
                            place(level, pos, light);
                        }
                    }
                }
            }
        }

        private void place(ServerLevel level, BlockPos pos, BlockState state) {
            level.setBlock(pos, state, 3);
            placedBlocks.add(pos.immutable());
        }
    }
}
