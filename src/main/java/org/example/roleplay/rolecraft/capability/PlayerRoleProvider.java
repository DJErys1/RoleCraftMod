package org.example.roleplay.rolecraft.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.example.roleplay.rolecraft.Rolecraft;
import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlayerRoleProvider implements ICapabilitySerializable<CompoundTag> {

    public static final ResourceLocation KEY =
            new ResourceLocation(Rolecraft.MODID, "player_roles");

    public static final Capability<IPlayerRoles> CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerRoles backend = new PlayerRoles();
    private final LazyOptional<IPlayerRoles> opt = LazyOptional.of(() -> backend);

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new PlayerRoleProvider());
        }
    }

    public static LazyOptional<IPlayerRoles> get(Player player) {
        return player.getCapability(CAP);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == CAP ? opt.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("role", backend.getRole().name());
        tag.putString("job", backend.getJob().name());
        tag.putInt("roleXp", backend.getRoleXp());
        tag.putInt("roleLevel", backend.getRoleLevel());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.setRole(RoleType.fromString(nbt.getString("role")));
        backend.setJob(JobType.fromString(nbt.getString("job")));
        backend.setRoleXp(nbt.getInt("roleXp"));
        backend.setRoleLevel(nbt.getInt("roleLevel"));
    }
}
