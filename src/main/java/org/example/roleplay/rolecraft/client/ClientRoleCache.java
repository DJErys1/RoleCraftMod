package org.example.roleplay.rolecraft.client;

import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

public class ClientRoleCache {
    public static volatile RoleType ROLE = RoleType.NONE;
    public static volatile JobType JOB = JobType.NONE;

    public static volatile int ROLE_XP = 0;
    public static volatile int ROLE_LEVEL = 0;

    public static volatile boolean WORK_LOCKED = false;
    public static volatile int WORK_TICKS_LEFT = 0;

    public static void set(RoleType role, JobType job, int xp, int level) {
        ROLE = (role == null ? RoleType.NONE : role);
        JOB = (job == null ? JobType.NONE : job);
        ROLE_XP = Math.max(0, xp);
        ROLE_LEVEL = Math.max(0, level);
    }
}
