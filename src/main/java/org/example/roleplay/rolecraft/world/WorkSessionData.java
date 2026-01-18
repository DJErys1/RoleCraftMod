package org.example.roleplay.rolecraft.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkSessionData extends SavedData {
    private static final String KEY = "rolecraft_work_sessions";

    public static class WorkSession {
        public int ticksLeft;
        public double startX, startY, startZ;
        public int maxDistSq;
        public boolean cancelOnHit;
        public String label;

        public WorkSession(int ticksLeft, double x, double y, double z, int maxDistSq, boolean cancelOnHit, String label) {
            this.ticksLeft = ticksLeft;
            this.startX = x;
            this.startY = y;
            this.startZ = z;
            this.maxDistSq = maxDistSq;
            this.cancelOnHit = cancelOnHit;
            this.label = label;
        }
    }

    private final Map<UUID, WorkSession> sessions = new HashMap<>();

    public static WorkSessionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorkSessionData::load, WorkSessionData::new, KEY);
    }

    public Map<UUID, WorkSession> all() {
        return sessions;
    }

    public void start(UUID player, WorkSession s) {
        sessions.put(player, s);
        setDirty();
    }

    public void stop(UUID player) {
        if (sessions.remove(player) != null) setDirty();
    }

    public boolean has(UUID player) {
        return sessions.containsKey(player);
    }

    public WorkSession get(UUID player) {
        return sessions.get(player);
    }

    public static WorkSessionData load(CompoundTag tag) {
        WorkSessionData d = new WorkSessionData();
        CompoundTag map = tag.getCompound("map");

        for (String key : map.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag t = map.getCompound(key);

                WorkSession s = new WorkSession(
                        t.getInt("ticksLeft"),
                        t.getDouble("x"),
                        t.getDouble("y"),
                        t.getDouble("z"),
                        t.getInt("maxDistSq"),
                        t.getBoolean("cancelOnHit"),
                        t.getString("label")
                );
                d.sessions.put(uuid, s);
            } catch (Exception ignored) {}
        }

        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag map = new CompoundTag();
        for (Map.Entry<UUID, WorkSession> e : sessions.entrySet()) {
            CompoundTag t = new CompoundTag();
            WorkSession s = e.getValue();

            t.putInt("ticksLeft", s.ticksLeft);
            t.putDouble("x", s.startX);
            t.putDouble("y", s.startY);
            t.putDouble("z", s.startZ);
            t.putInt("maxDistSq", s.maxDistSq);
            t.putBoolean("cancelOnHit", s.cancelOnHit);
            t.putString("label", s.label == null ? "" : s.label);

            map.put(e.getKey().toString(), t);
        }
        tag.put("map", map);
        return tag;
    }
}
