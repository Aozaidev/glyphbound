package com.glyphbound.effect;

import com.glyphbound.Glyphbound;
import com.glyphbound.block.InkRealmPortalBlock;
import com.glyphbound.core.GlyphboundAdvancements;
import com.glyphbound.core.GlyphboundBlocks;
import com.glyphbound.world.InkRealmGenerator;
import com.glyphbound.world.InkRealmState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class InkRealmEvents {
    public static final ResourceKey<Level> INK_REALM = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "ink_realm")
    );

    private static final int SEA_LEVEL = InkRealmGenerator.SEA_LEVEL;
    private static final int INK_SEA_DAMAGE_PER_SECOND = 4;
    private static final int DEPTH_TELEPORT_THRESHOLD = 3;
    private static final int PORTAL_TTL_TICKS = 20 * 30;
    private static final BlockPos INK_REALM_SPAWN = InkRealmGenerator.spawnPos();

    private static final Map<UUID, Long> inkSeaDamageTimers = new HashMap<>();

    private InkRealmEvents() {
    }

    public static boolean isInkRealm(Level level) {
        return level.dimension().equals(INK_REALM);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            expirePortals(level, gameTime);
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (isInkRealm(level)) {
                    handleInkSeaDanger(player, gameTime);
                } else {
                    handlePortalContact(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isInkRealm(player.level())) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(GlyphboundBlocks.INK_REALM_PORTAL.get())) {
            event.setCanceled(true);
            tryUsePortal(player, event.getPos());
        }

        if (state.is(GlyphboundBlocks.BOUNDARY_STELE.get())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal(""), false);
            player.displayClientMessage(Component.literal("墨界前庭"), false);
            player.displayClientMessage(Component.literal(""), false);
            player.displayClientMessage(Component.literal("此处是墨界中最稳定的一片纸面。"), false);
            player.displayClientMessage(Component.literal("当前版本仅开放前庭、墨海与归途。"), false);
            player.displayClientMessage(Component.literal("后续版本将开放界锚扩展、墨海岛屿与界域副本。"), false);
            player.displayClientMessage(Component.literal(""), false);
            player.displayClientMessage(Component.literal("写「出」可返回来处。"), false);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockState state = event.getState();
        if (state.is(GlyphboundBlocks.INK_REALM_PORTAL.get())) {
            event.setCanceled(true);
            return;
        }

        if (!isInkRealm(player.level())) return;

        if (state.is(GlyphboundBlocks.BOUNDARY_STELE.get()) ||
            state.is(GlyphboundBlocks.INK_STONE.get()) ||
            state.is(GlyphboundBlocks.POLISHED_INK_STONE.get()) ||
            state.is(GlyphboundBlocks.INK_BRICK.get()) ||
            state.is(GlyphboundBlocks.INK_DIRT.get()) ||
            state.is(GlyphboundBlocks.INK_SAND.get()) ||
            state.is(GlyphboundBlocks.PARCHMENT_BLOCK.get()) ||
            state.is(GlyphboundBlocks.PARCHMENT_LANTERN.get()) ||
            state.is(Blocks.BAMBOO_PLANKS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (event.getTo().equals(INK_REALM)) {
            ensureMainIslandReady(player.serverLevel());
            player.displayClientMessage(Component.literal("墨界前庭已展开。写「出」离开。"), true);
        }

        if (event.getFrom().equals(INK_REALM)) {
            ServerLevel inkRealm = player.server.getLevel(INK_REALM);
            if (inkRealm != null && !isInkRealm(player.level())) {
                InkRealmState.get(inkRealm).removeReturnPoint(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (isInkRealm(player.level())) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            InkRealmState state = InkRealmState.get(overworld);
            state.clearPlayerState(player.getUUID());
        } else {
            ServerLevel inkRealm = player.server.getLevel(INK_REALM);
            if (inkRealm != null) {
                InkRealmState.get(inkRealm).removeReturnPoint(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isInkRealm(player.level())) return;

        ensureMainIslandReady(player.serverLevel());

        InkRealmState state = InkRealmState.get(player.serverLevel());
        if (state.getReturnPoint(player.getUUID()) == null) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.literal("归途已散，已返回主世界。"), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearPlayerPortals(player);
        ServerLevel inkRealm = player.server.getLevel(INK_REALM);
        if (inkRealm != null) {
            InkRealmState.get(inkRealm).removeReturnPoint(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        inkSeaDamageTimers.clear();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            InkRealmState state = InkRealmState.get(level);
            for (InkRealmState.PortalRecord portal : state.getPortals()) {
                removePortalBlocks(level, portal);
                state.removePortal(portal);
            }
        }
    }

    public static void enterInkRealm(ServerPlayer player) {
        ServerLevel inkRealmLevel = player.server.getLevel(INK_REALM);
        if (inkRealmLevel == null) {
            player.displayClientMessage(Component.literal("入: 墨界未就绪"), true);
            return;
        }

        openInkRealmPortal(player);
    }

    private static void openInkRealmPortal(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        BlockPos corner = player.blockPosition().relative(facing, 2).above();
        BlockPos sidePosition = corner.relative(side);

        if (!canPlacePortal(level, corner, sidePosition)) {
            player.displayClientMessage(Component.literal("入: 墨界门无法成形，找个空旷处"), true);
            return;
        }

        clearPlayerPortals(player);

        BlockState portalState = GlyphboundBlocks.INK_REALM_PORTAL.get()
            .defaultBlockState()
            .setValue(InkRealmPortalBlock.AXIS, side.getAxis());
        for (BlockPos pos : portalBlocks(corner, sidePosition)) {
            level.setBlock(pos, portalState, 3);
        }
        for (BlockPos pos : portalFrameBlocks(corner, sidePosition)) {
            level.setBlock(pos, GlyphboundBlocks.INK_BRICK.get().defaultBlockState(), 3);
        }

        InkRealmState state = InkRealmState.get(level);
        state.addPortal(new InkRealmState.PortalRecord(
            corner.immutable(),
            sidePosition.immutable(),
            player.getUUID(),
            level.dimension(),
            player.blockPosition().immutable(),
            gameTime
        ));

        player.displayClientMessage(Component.literal("入: 墨界门已成形，步入门中。"), true);
    }

    private static void teleportThroughPortal(ServerPlayer player, ServerLevel originLevel, InkRealmState.PortalRecord portal) {
        ServerLevel inkRealmLevel = player.server.getLevel(INK_REALM);
        if (inkRealmLevel == null) {
            player.displayClientMessage(Component.literal("入: 墨界未就绪"), true);
            return;
        }

        InkRealmState state = InkRealmState.get(inkRealmLevel);
        ensureMainIslandReady(inkRealmLevel);

        state.setReturnPoint(player.getUUID(),
            new InkRealmState.ReturnPoint(portal.returnDimension(), portal.returnPosition()));

        InkRealmState originState = InkRealmState.get(originLevel);
        removePortalBlocks(originLevel, portal);
        originState.removePortal(portal);

        player.teleportTo(inkRealmLevel, INK_REALM_SPAWN.getX() + 0.5, INK_REALM_SPAWN.getY(), INK_REALM_SPAWN.getZ() + 0.5, player.getYRot(), player.getXRot());
        GlyphboundAdvancements.award(player, GlyphboundAdvancements.INK_REALM_ENTRY);
        player.displayClientMessage(Component.literal("墨界前庭已展开。写「出」离开。"), true);
    }

    private static void ensureMainIslandReady(ServerLevel inkRealmLevel) {
        InkRealmState state = InkRealmState.get(inkRealmLevel);
        if (state.isMainIslandGenerated()) {
            return;
        }

        InkRealmGenerator.generateMainIsland(inkRealmLevel);
        state.setMainIslandGenerated();
    }

    public static void exitInkRealm(ServerPlayer player) {
        InkRealmState state = InkRealmState.get(player.serverLevel());
        InkRealmState.ReturnPoint returnPoint = state.getReturnPoint(player.getUUID());

        if (returnPoint == null) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.literal("归途已散，已返回主世界。"), true);
        } else {
            ServerLevel returnLevel = player.server.getLevel(returnPoint.dimension());
            if (returnLevel == null) {
                returnLevel = player.server.overworld();
            }
            BlockPos pos = returnPoint.position();
            player.teleportTo(returnLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        }

        state.removeReturnPoint(player.getUUID());
        GlyphboundAdvancements.award(player, GlyphboundAdvancements.INK_REALM_RETURN);
    }

    private static void handleInkSeaDanger(ServerPlayer player, long gameTime) {
        BlockPos pos = player.blockPosition();
        if (pos.getY() > SEA_LEVEL) {
            inkSeaDamageTimers.remove(player.getUUID());
            return;
        }

        Long lastDamage = inkSeaDamageTimers.get(player.getUUID());
        if (lastDamage == null || gameTime - lastDamage >= 20) {
            inkSeaDamageTimers.put(player.getUUID(), gameTime);
            player.hurt(player.damageSources().generic(), INK_SEA_DAMAGE_PER_SECOND);
        }

        double depth = SEA_LEVEL - pos.getY();
        if (depth >= DEPTH_TELEPORT_THRESHOLD) {
            player.teleportTo(player.serverLevel(), INK_REALM_SPAWN.getX() + 0.5, INK_REALM_SPAWN.getY(), INK_REALM_SPAWN.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.literal("墨海深邃，被推回岸前。"), true);
            inkSeaDamageTimers.remove(player.getUUID());
        }
    }

    private static void handlePortalContact(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        if (tryUsePortal(player, feet)) {
            return;
        }
        tryUsePortal(player, feet.above());
    }

    private static boolean tryUsePortal(ServerPlayer player, BlockPos pos) {
        if (isInkRealm(player.level())) {
            return false;
        }
        if (!player.level().getBlockState(pos).is(GlyphboundBlocks.INK_REALM_PORTAL.get())) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        InkRealmState state = InkRealmState.get(level);
        for (InkRealmState.PortalRecord portal : state.getPortals()) {
            if (portal.owner().equals(player.getUUID()) && containsPortalBlock(portal, pos)) {
                teleportThroughPortal(player, level, portal);
                return true;
            }
        }
        return false;
    }

    private static boolean canPlacePortal(ServerLevel level, BlockPos corner, BlockPos sidePosition) {
        for (BlockPos pos : portalShapeBlocks(corner, sidePosition)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()
                && !state.canBeReplaced()
                && !state.is(GlyphboundBlocks.INK_REALM_PORTAL.get())
                && !state.is(GlyphboundBlocks.INK_BRICK.get())) {
                return false;
            }
        }
        return true;
    }

    private static Set<BlockPos> portalBlocks(BlockPos corner, BlockPos sidePosition) {
        Set<BlockPos> blocks = new HashSet<>();
        for (int dy = 0; dy < 3; dy++) {
            blocks.add(corner.above(dy));
            blocks.add(sidePosition.above(dy));
        }
        return blocks;
    }

    private static Set<BlockPos> portalFrameBlocks(BlockPos corner, BlockPos sidePosition) {
        Direction side = portalSide(corner, sidePosition);
        Set<BlockPos> blocks = new HashSet<>();
        for (int offset = -1; offset <= 2; offset++) {
            for (int dy = -1; dy <= 3; dy++) {
                if (offset == -1 || offset == 2 || dy == -1 || dy == 3) {
                    blocks.add(corner.relative(side, offset).relative(Direction.UP, dy));
                }
            }
        }
        return blocks;
    }

    private static Set<BlockPos> portalShapeBlocks(BlockPos corner, BlockPos sidePosition) {
        Set<BlockPos> blocks = new HashSet<>(portalBlocks(corner, sidePosition));
        blocks.addAll(portalFrameBlocks(corner, sidePosition));
        return blocks;
    }

    private static Direction portalSide(BlockPos corner, BlockPos sidePosition) {
        int dx = sidePosition.getX() - corner.getX();
        int dz = sidePosition.getZ() - corner.getZ();
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static boolean containsPortalBlock(InkRealmState.PortalRecord portal, BlockPos pos) {
        return portalBlocks(portal.position(), portal.sidePosition()).contains(pos);
    }

    private static void expirePortals(ServerLevel level, long gameTime) {
        InkRealmState state = InkRealmState.get(level);
        for (InkRealmState.PortalRecord portal : state.getPortals()) {
            if (gameTime - portal.createdTime() >= PORTAL_TTL_TICKS) {
                removePortalBlocks(level, portal);
                state.removePortal(portal);
            }
        }
    }

    private static void clearPlayerPortals(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            InkRealmState state = InkRealmState.get(level);
            for (InkRealmState.PortalRecord portal : state.getPortals()) {
                if (portal.owner().equals(player.getUUID())) {
                    removePortalBlocks(level, portal);
                    state.removePortal(portal);
                }
            }
        }
    }

    private static void removePortalBlocks(ServerLevel level, InkRealmState.PortalRecord portal) {
        for (BlockPos pos : portalBlocks(portal.position(), portal.sidePosition())) {
            if (level.getBlockState(pos).is(GlyphboundBlocks.INK_REALM_PORTAL.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (BlockPos pos : portalFrameBlocks(portal.position(), portal.sidePosition())) {
            if (level.getBlockState(pos).is(GlyphboundBlocks.INK_BRICK.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
