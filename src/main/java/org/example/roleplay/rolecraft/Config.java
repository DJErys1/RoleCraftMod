package org.example.roleplay.rolecraft;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class Config {
    public static final ForgeConfigSpec SPEC;

    // ===== Role leveling =====
    public static ForgeConfigSpec.IntValue XP_PER_LEVEL;
    public static ForgeConfigSpec.DoubleValue BONUS_PER_LEVEL;
    public static ForgeConfigSpec.DoubleValue MAX_BONUS_MULT;

    // XP per action
    public static ForgeConfigSpec.IntValue XP_LUMBERJACK_LOG;
    public static ForgeConfigSpec.IntValue XP_MINER_BLOCK;

    // ===== Non-role penalties =====
    public static ForgeConfigSpec.DoubleValue LUMBERJACK_NONROLE_PENALTY;
    public static ForgeConfigSpec.DoubleValue MINER_NONROLE_PENALTY;

    // drops
    public static ForgeConfigSpec.DoubleValue MINER_STONE_ORE_DROP_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("leveling");
        XP_PER_LEVEL = b
                .comment("Ile XP potrzeba na 1 level roli (level = xp / XP_PER_LEVEL)")
                .defineInRange("xp_per_level", 200, 10, 100000);

        BONUS_PER_LEVEL = b
                .comment("Ile mnożnika szybkości dodaje każdy level na blokach roli. (np 0.02 = +2% per level)")
                .defineInRange("bonus_per_level", 0.02, 0.0, 1.0);

        MAX_BONUS_MULT = b
                .comment("Maksymalny mnożnik szybkości kopania na blokach roli (np 2.5 = max 250% vanilla)")
                .defineInRange("max_bonus_mult", 2.5, 1.0, 50.0);
        b.pop();

        b.push("xp");
        XP_LUMBERJACK_LOG = b
                .comment("XP za zniszczenie 1 log (Drwal)")
                .defineInRange("xp_lumberjack_log", 1, 0, 1000);

        XP_MINER_BLOCK = b
                .comment("XP za zniszczenie 1 bloku górniczego (Górnik)")
                .defineInRange("xp_miner_block", 1, 0, 1000);
        b.pop();

        b.push("penalties");
        LUMBERJACK_NONROLE_PENALTY = b
                .comment("Drwal: kara (mnożnik) na bloki górnicze (STONE/ORE itp). 0.6 = 40% wolniej.")
                .defineInRange("lumberjack_nonrole_penalty", 0.6, 0.05, 1.0);

        MINER_NONROLE_PENALTY = b
                .comment("Górnik: kara (mnożnik) na drewno (LOGS). 0.6 = 40% wolniej.")
                .defineInRange("miner_nonrole_penalty", 0.6, 0.05, 1.0);
        b.pop();

        b.push("drops");
        MINER_STONE_ORE_DROP_CHANCE = b
                .comment("Górnik: szansa że ze STONE wypadnie losowy surowiec")
                .defineInRange("miner_stone_ore_drop_chance", 0.10, 0.0, 1.0);
        b.pop();

        SPEC = b.build();
    }

    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
