package org.example.roleplay.rolecraft.role;

import java.util.Locale;

public enum JobType {
    NONE,
    MEDIC,
    POLICEMAN,
    CLERK,
    MAYOR;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static JobType fromString(String s) {
        if (s == null) return NONE;
        try {
            return JobType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return NONE;
        }
    }
}
