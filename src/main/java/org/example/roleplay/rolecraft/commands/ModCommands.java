package org.example.roleplay.rolecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import org.example.roleplay.rolecraft.capability.IPlayerRoles;
import org.example.roleplay.rolecraft.capability.PlayerRoleProvider;
import org.example.roleplay.rolecraft.network.ModNetwork;
import org.example.roleplay.rolecraft.network.SyncRolePacket;
import org.example.roleplay.rolecraft.role.JobType;
import org.example.roleplay.rolecraft.role.RoleType;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(
                Commands.literal("rolecraft")

                        // /rolecraft
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.header"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.line1"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.line2"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.line3"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.line4"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("rolecraft.cmd.help.line5"), false);
                            return 1;
                        })

                        // /rolecraft adminui
                        .then(Commands.literal("adminui")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("rolecraft.cmd.adminui.todo"),
                                            false
                                    );
                                    return 1;
                                })
                        )

                        // =========================
                        // INFO / LIST
                        // =========================

                        // /rolecraft info
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    sendInfo(ctx.getSource(), self, self);
                                    return 1;
                                })
                                // /rolecraft info <player>
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                                            sendInfo(ctx.getSource(), self, target);
                                            return 1;
                                        })
                                )
                        )

                        // /rolecraft role list
                        .then(Commands.literal("role")
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(listRolesPretty()),
                                                    false
                                            );
                                            return 1;
                                        })
                                )

                                // /rolecraft role
                                .executes(ctx -> {
                                    sendOwnRole(ctx.getSource(), ctx.getSource().getPlayerOrException());
                                    return 1;
                                })

                                // /rolecraft role <player>
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> {
                                            sendRoleOf(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"));
                                            return 1;
                                        })
                                )

                                // /rolecraft role level ...
                                .then(Commands.literal("level")

                                        // /rolecraft role level
                                        .executes(ctx -> {
                                            sendOwnLevel(ctx.getSource(), ctx.getSource().getPlayerOrException());
                                            return 1;
                                        })

                                        // /rolecraft role level <player>
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .requires(src -> src.hasPermission(2))
                                                .executes(ctx -> {
                                                    sendLevelOf(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"));
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /rolecraft job list
                        .then(Commands.literal("job")
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(listJobsPretty()),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )

                        // =========================
                        // SET
                        // =========================

                        // /rolecraft set ...
                        .then(Commands.literal("set")

                                // /rolecraft set role <player> <role>
                                .then(Commands.literal("role")
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .suggests(ModCommands::suggestRoles)
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            RoleType role = RoleType.fromString(StringArgumentType.getString(ctx, "role"));

                                                            AtomicBoolean ok = new AtomicBoolean(false);
                                                            PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                                ok.set(true);
                                                                data.setRole(role);

                                                                // ✅ NATYCHMIASTOWY SYNC
                                                                syncToClient(target, data);

                                                                target.sendSystemMessage(
                                                                        Component.translatable("rolecraft.cmd.set.role.target", role.name())
                                                                );
                                                            });

                                                            if (!ok.get()) {
                                                                ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                                return 0;
                                                            }

                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.translatable(
                                                                            "rolecraft.cmd.set.role.source",
                                                                            target.getName(),
                                                                            role.name()
                                                                    ),
                                                                    true
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )

                                // /rolecraft set job <player> <job>
                                .then(Commands.literal("job")
                                        .requires(ModCommands::isOpOrMayor)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("job", StringArgumentType.word())
                                                        .suggests(ModCommands::suggestJobs)
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            JobType job = JobType.fromString(StringArgumentType.getString(ctx, "job"));

                                                            AtomicBoolean ok = new AtomicBoolean(false);
                                                            PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                                ok.set(true);
                                                                data.setJob(job);

                                                                // ✅ NATYCHMIASTOWY SYNC
                                                                syncToClient(target, data);

                                                                target.sendSystemMessage(
                                                                        Component.translatable("rolecraft.cmd.set.job.target", job.name())
                                                                );
                                                            });

                                                            if (!ok.get()) {
                                                                ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                                return 0;
                                                            }

                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.translatable(
                                                                            "rolecraft.cmd.set.job.source",
                                                                            target.getName(),
                                                                            job.name()
                                                                    ),
                                                                    true
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )

                                // /rolecraft set rolexp <player> <xp>
                                .then(Commands.literal("rolexp")
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("xp", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int xp = IntegerArgumentType.getInteger(ctx, "xp");

                                                            AtomicBoolean ok = new AtomicBoolean(false);
                                                            PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                                ok.set(true);
                                                                data.setRoleXp(xp);
                                                                syncToClient(target, data);
                                                                target.sendSystemMessage(Component.translatable("rolecraft.cmd.set.rolexp.target", xp));
                                                            });

                                                            if (!ok.get()) {
                                                                ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                                return 0;
                                                            }

                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.translatable("rolecraft.cmd.set.rolexp.source", target.getName(), xp),
                                                                    true
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )

                                // /rolecraft set rolelevel <player> <level>
                                .then(Commands.literal("rolelevel")
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int level = IntegerArgumentType.getInteger(ctx, "level");

                                                            AtomicBoolean ok = new AtomicBoolean(false);
                                                            PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                                ok.set(true);
                                                                data.setRoleLevel(level);
                                                                syncToClient(target, data);
                                                                target.sendSystemMessage(Component.translatable("rolecraft.cmd.set.rolelevel.target", level));
                                                            });

                                                            if (!ok.get()) {
                                                                ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                                return 0;
                                                            }

                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.translatable("rolecraft.cmd.set.rolelevel.source", target.getName(), level),
                                                                    true
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )

                        // =========================
                        // REMOVE / CLEAR
                        // =========================

                        // /rolecraft remove job <player>
                        .then(Commands.literal("remove")
                                .then(Commands.literal("job")
                                        .requires(ModCommands::isOpOrMayor)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                                    AtomicBoolean ok = new AtomicBoolean(false);
                                                    PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                        ok.set(true);
                                                        data.setJob(JobType.NONE);

                                                        // ✅ NATYCHMIASTOWY SYNC
                                                        syncToClient(target, data);

                                                        target.sendSystemMessage(
                                                                Component.translatable("rolecraft.cmd.remove.job.target")
                                                        );
                                                    });

                                                    if (!ok.get()) {
                                                        ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                        return 0;
                                                    }

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.translatable(
                                                                    "rolecraft.cmd.remove.job.source",
                                                                    target.getName()
                                                            ),
                                                            true
                                                    );
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /rolecraft clear <player>
                        .then(Commands.literal("clear")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                            AtomicBoolean ok = new AtomicBoolean(false);
                                            PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
                                                ok.set(true);
                                                data.setRole(RoleType.NONE);
                                                data.setJob(JobType.NONE);
                                                data.setRoleXp(0);
                                                data.setRoleLevel(0);

                                                syncToClient(target, data);

                                                target.sendSystemMessage(Component.translatable("rolecraft.cmd.clear.target"));
                                            });

                                            if (!ok.get()) {
                                                ctx.getSource().sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
                                                return 0;
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable("rolecraft.cmd.clear.source", target.getName()),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
        );
    }

    /* =========================
       SYNC (klucz do braku “lagu”)
    ========================= */
    private static void syncToClient(ServerPlayer target, IPlayerRoles data) {
        ModNetwork.CHANNEL.sendTo(
                new SyncRolePacket(data.getRole(), data.getJob(), data.getRoleXp(), data.getRoleLevel()),
                target.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }

    /* =========================
       PERMISSIONS
    ========================= */
    private static boolean isOpOrMayor(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        if (!(src.getEntity() instanceof ServerPlayer sp)) return false;

        AtomicBoolean allowed = new AtomicBoolean(false);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles data) ->
                allowed.set("MAYOR".equalsIgnoreCase(data.getJob().name()))
        );
        return allowed.get();
    }

    /* =========================
       INFO OUTPUT
    ========================= */
    private static void sendInfo(CommandSourceStack src, ServerPlayer viewer, ServerPlayer target) {
        AtomicBoolean found = new AtomicBoolean(false);
        PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
            found.set(true);

            src.sendSuccess(() -> Component.literal("§b[RoleCraft] §fInfo: §e" + target.getGameProfile().getName()), false);
            src.sendSuccess(() -> Component.literal("§7Role: §a" + data.getRole().name().toLowerCase(Locale.ROOT)), false);
            src.sendSuccess(() -> Component.literal("§7Job:  §a" + data.getJob().name().toLowerCase(Locale.ROOT)), false);
            src.sendSuccess(() -> Component.literal("§7Level: §e" + data.getRoleLevel() + " §8| §7XP: §e" + data.getRoleXp()), false);
        });

        if (!found.get()) {
            src.sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
        }
    }

    private static String listRolesPretty() {
        StringBuilder sb = new StringBuilder("§b[RoleCraft] §fRole list: §7");
        boolean first = true;
        for (RoleType r : RoleType.values()) {
            if (!first) sb.append("§8, §7");
            first = false;
            sb.append(r.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String listJobsPretty() {
        StringBuilder sb = new StringBuilder("§b[RoleCraft] §fJob list: §7");
        boolean first = true;
        for (JobType j : JobType.values()) {
            if (!first) sb.append("§8, §7");
            first = false;
            sb.append(j.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /* =========================
       HELPERS (Twoje)
    ========================= */
    private static void sendOwnRole(CommandSourceStack src, ServerPlayer sp) {
        AtomicBoolean found = new AtomicBoolean(false);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles data) -> {
            found.set(true);
            src.sendSuccess(() -> Component.translatable("rolecraft.cmd.role.self", data.getRole().name()), false);
        });
        if (!found.get()) src.sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
    }

    private static void sendRoleOf(CommandSourceStack src, ServerPlayer target) {
        AtomicBoolean found = new AtomicBoolean(false);
        PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
            found.set(true);
            src.sendSuccess(() -> Component.translatable("rolecraft.cmd.role.other", target.getName(), data.getRole().name()), false);
        });
        if (!found.get()) src.sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
    }

    private static void sendOwnLevel(CommandSourceStack src, ServerPlayer sp) {
        AtomicBoolean found = new AtomicBoolean(false);
        PlayerRoleProvider.get(sp).ifPresent((IPlayerRoles data) -> {
            found.set(true);
            src.sendSuccess(() -> Component.translatable("rolecraft.cmd.level.self", data.getRoleLevel(), data.getRoleXp()), false);
        });
        if (!found.get()) src.sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
    }

    private static void sendLevelOf(CommandSourceStack src, ServerPlayer target) {
        AtomicBoolean found = new AtomicBoolean(false);
        PlayerRoleProvider.get(target).ifPresent((IPlayerRoles data) -> {
            found.set(true);
            src.sendSuccess(() -> Component.translatable("rolecraft.cmd.level.other", target.getName(), data.getRoleLevel(), data.getRoleXp()), false);
        });
        if (!found.get()) src.sendFailure(Component.translatable("rolecraft.cmd.error.nodata"));
    }

    /* =========================
       TAB SUGGESTIONS (Twoje)
    ========================= */
    private static CompletableFuture<Suggestions> suggestRoles(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        for (RoleType r : RoleType.values()) {
            builder.suggest(r.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestJobs(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        for (JobType j : JobType.values()) {
            builder.suggest(j.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }
}
