package org.example.roleplay.rolecraft.role;

import java.util.Locale;

public enum RoleType {
    NONE("none"),
    LUMBERJACK("drwal"),
    MINER("gornik"),
    BLACKSMITH("kowal"),
    FARMER("farmer");

    public final String plName;

    RoleType(String plName) {
        this.plName = plName;
    }

    public static RoleType fromString(String s) {
        if (s == null) return NONE;
        String x = s.trim().toLowerCase(Locale.ROOT);

        // PL
        if (x.equals("drwal")) return LUMBERJACK;
        if (x.equals("górnik") || x.equals("gornik")) return MINER;
        if (x.equals("kowal")) return BLACKSMITH;
        if (x.equals("farmer")) return FARMER;

        // EN / enum
        for (RoleType r : values()) {
            if (r.name().toLowerCase(Locale.ROOT).equals(x)) return r;
        }
        return NONE;
    }
}
