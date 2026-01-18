package org.example.roleplay.rolecraft.capability;

import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

public class PlayerRoles implements IPlayerRoles {
    private RoleType role = RoleType.NONE;
    private JobType job = JobType.NONE;

    private int roleXp = 0;
    private int roleLevel = 0;

    @Override
    public RoleType getRole() { return role; }

    @Override
    public void setRole(RoleType role) {
        this.role = (role == null ? RoleType.NONE : role);
    }

    @Override
    public JobType getJob() { return job; }

    @Override
    public void setJob(JobType job) {
        this.job = (job == null ? JobType.NONE : job);
    }

    @Override
    public int getRoleXp() { return roleXp; }

    @Override
    public void setRoleXp(int xp) { this.roleXp = Math.max(0, xp); }

    @Override
    public int getRoleLevel() { return roleLevel; }

    @Override
    public void setRoleLevel(int level) { this.roleLevel = Math.max(0, level); }
}
