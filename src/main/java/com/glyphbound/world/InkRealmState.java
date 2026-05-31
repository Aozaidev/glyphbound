package com.glyphbound.world;

import com.glyphbound.Glyphbound;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InkRealmState extends SavedData {
    private static final String DATA_NAME = Glyphbound.MOD_ID + "_ink_realm";
    private static final SavedData.Factory<InkRealmState> FACTORY =
        new SavedData.Factory<>(InkRealmState::new, InkRealmState::load);

    private boolean mainIslandGenerated = false;
    private final Map<UUID, ReturnPoint> playerReturnPoints = new HashMap<>();
    private final Set<PortalRecord> temporaryPortals = new HashSet<>();

    public record ReturnPoint(ResourceKey<Level> dimension, BlockPos position) {
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension.location().toString());
            tag.put("position", NbtUtils.writeBlockPos(position));
            return tag;
        }

        public static ReturnPoint fromNbt(CompoundTag tag) {
            ResourceKey<Level> dim = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension"))
            );
            BlockPos pos = NbtUtils.readBlockPos(tag, "position").orElse(BlockPos.ZERO);
            return new ReturnPoint(dim, pos);
        }
    }

    public record PortalRecord(BlockPos position, BlockPos sidePosition, UUID owner, ResourceKey<Level> returnDimension,
                               BlockPos returnPosition, long createdTime) {
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.put("position", NbtUtils.writeBlockPos(position));
            tag.put("sidePosition", NbtUtils.writeBlockPos(sidePosition));
            tag.putUUID("owner", owner);
            tag.putString("returnDimension", returnDimension.location().toString());
            tag.put("returnPosition", NbtUtils.writeBlockPos(returnPosition));
            tag.putLong("createdTime", createdTime);
            return tag;
        }

        public static PortalRecord fromNbt(CompoundTag tag) {
            return new PortalRecord(
                NbtUtils.readBlockPos(tag, "position").orElse(BlockPos.ZERO),
                NbtUtils.readBlockPos(tag, "sidePosition").orElse(BlockPos.ZERO),
                tag.getUUID("owner"),
                ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString("returnDimension"))
                ),
                NbtUtils.readBlockPos(tag, "returnPosition").orElse(BlockPos.ZERO),
                tag.getLong("createdTime")
            );
        }
    }

    public static InkRealmState get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static InkRealmState load(CompoundTag tag, HolderLookup.Provider registries) {
        InkRealmState state = new InkRealmState();
        state.mainIslandGenerated = tag.getBoolean("mainIslandGenerated");

        if (tag.contains("playerReturnPoints")) {
            ListTag list = tag.getList("playerReturnPoints", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                UUID uuid = entry.getUUID("player");
                ReturnPoint point = ReturnPoint.fromNbt(entry.getCompound("point"));
                state.playerReturnPoints.put(uuid, point);
            }
        }

        if (tag.contains("temporaryPortals")) {
            ListTag list = tag.getList("temporaryPortals", 10);
            for (int i = 0; i < list.size(); i++) {
                state.temporaryPortals.add(PortalRecord.fromNbt(list.getCompound(i)));
            }
        }

        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("mainIslandGenerated", mainIslandGenerated);

        ListTag returnPointsList = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> entry : playerReturnPoints.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("player", entry.getKey());
            entryTag.put("point", entry.getValue().toNbt());
            returnPointsList.add(entryTag);
        }
        tag.put("playerReturnPoints", returnPointsList);

        ListTag portalsList = new ListTag();
        for (PortalRecord portal : temporaryPortals) {
            portalsList.add(portal.toNbt());
        }
        tag.put("temporaryPortals", portalsList);

        return tag;
    }

    public boolean isMainIslandGenerated() {
        return mainIslandGenerated;
    }

    public void setMainIslandGenerated() {
        mainIslandGenerated = true;
        setDirty();
    }

    public void setReturnPoint(UUID player, ReturnPoint point) {
        playerReturnPoints.put(player, point);
        setDirty();
    }

    public ReturnPoint getReturnPoint(UUID player) {
        return playerReturnPoints.get(player);
    }

    public void removeReturnPoint(UUID player) {
        playerReturnPoints.remove(player);
        setDirty();
    }

    public void addPortal(PortalRecord portal) {
        temporaryPortals.add(portal);
        setDirty();
    }

    public void removePortal(PortalRecord portal) {
        temporaryPortals.remove(portal);
        setDirty();
    }

    public Set<PortalRecord> getPortals() {
        return Set.copyOf(temporaryPortals);
    }

    public void clearPlayerState(UUID player) {
        playerReturnPoints.remove(player);
        temporaryPortals.removeIf(p -> p.owner().equals(player));
        setDirty();
    }
}
