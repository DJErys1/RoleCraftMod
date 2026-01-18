package org.example.roleplay.rolecraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PlantTrackData extends SavedData {
    private static final String KEY = "rolecraft_plants";

    // Track: pos -> ownerUUID (string) i typ (sapling/crop)
    public enum PlantKind { SAPLING, CROP }

    public static class Entry {
        public final BlockPos pos;
        public final UUID owner;
        public final PlantKind kind;

        public Entry(BlockPos pos, UUID owner, PlantKind kind) {
            this.pos = pos;
            this.owner = owner;
            this.kind = kind;
        }
    }

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public Collection<Entry> all() {
        return entries.values();
    }

    public void put(BlockPos pos, UUID owner, PlantKind kind) {
        entries.put(pos.immutable(), new Entry(pos.immutable(), owner, kind));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos) != null) setDirty();
    }

    public static PlantTrackData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlantTrackData::load, PlantTrackData::new, KEY);
    }

    public static PlantTrackData load(CompoundTag tag) {
        PlantTrackData d = new PlantTrackData();
        ListTag list = tag.getList("entries", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int x = e.getInt("x");
            int y = e.getInt("y");
            int z = e.getInt("z");
            String u = e.getString("u");
            String k = e.getString("k");

            try {
                UUID uuid = UUID.fromString(u);
                PlantKind kind = PlantKind.valueOf(k);
                BlockPos pos = new BlockPos(x, y, z);
                d.entries.put(pos, new Entry(pos, uuid, kind));
            } catch (Exception ignored) {}
        }

        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", e.pos.getX());
            t.putInt("y", e.pos.getY());
            t.putInt("z", e.pos.getZ());
            t.putString("u", e.owner.toString());
            t.putString("k", e.kind.name());
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }
}
