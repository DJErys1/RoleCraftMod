package org.example.roleplay.rolecraft.capability;

import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

public interface IPlayerRoles {
    RoleType getRole();
    void setRole(RoleType role);

    JobType getJob();
    void setJob(JobType job);

    int getRoleXp();
    void setRoleXp(int xp);

    int getRoleLevel();
    void setRoleLevel(int level);
}
