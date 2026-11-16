package dev.jensh.wornground;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PLAYERS;
    public static final ModConfigSpec.BooleanValue MOBS;
    public static final ModConfigSpec.DoubleValue CHANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SPRINT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LANDING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MOB_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue SNEAKING_PREVENTS_WEAR;
    public static final ModConfigSpec.BooleanValue TRAMPLE_VEGETATION;
    public static final ModConfigSpec.BooleanValue TRAMPLE_FLOWERS;
    public static final ModConfigSpec.BooleanValue RESPECT_MOB_GRIEFING;
    public static final ModConfigSpec.BooleanValue WEAR_ON_CONTRAPTIONS;
    public static final ModConfigSpec.BooleanValue CONTRAPTIONS_WEAR_GROUND;
    public static final ModConfigSpec.DoubleValue CONTRAPTION_MULTIPLIER;

    static {
        var builder = new ModConfigSpec.Builder();
        PLAYERS = builder.comment("Players wear the ground they walk on.").define("players", true);
        MOBS = builder.comment("Mobs in the worn_ground:path_makers entity tag wear the ground they walk on.")
            .define("mobs", true);
        CHANCE_MULTIPLIER = builder.comment("Multiplies every wear chance. 0 disables wear entirely.")
            .defineInRange("chanceMultiplier", 1.0, 0.0, 1000.0);
        SPRINT_MULTIPLIER = builder.comment("Extra chance multiplier while sprinting.")
            .defineInRange("sprintMultiplier", 2.0, 0.0, 1000.0);
        LANDING_MULTIPLIER = builder.comment(
                "Extra chance multiplier for the block landed on after a jump or a drop.",
                "At 4, sprint-jumping (one landing every ~4 blocks) wears a route about as fast as sprinting along it.")
            .defineInRange("landingMultiplier", 4.0, 0.0, 1000.0);
        MOB_MULTIPLIER = builder.comment("Extra chance multiplier for mobs.")
            .defineInRange("mobMultiplier", 0.25, 0.0, 1000.0);
        SNEAKING_PREVENTS_WEAR = builder.comment("Sneaking never wears the ground.")
            .define("sneakingPreventsWear", true);
        TRAMPLE_VEGETATION = builder.comment(
                "Plants in the worn_ground:trampleable block tag are flattened before the ground below wears.",
                "When false, ground with such plants on it is left alone unless the plant can stay.")
            .define("trampleVegetation", true);
        TRAMPLE_FLOWERS = builder.comment(
                "Flowers (worn_ground:trampleable_flowers block tag) are flattened like tall grass. Needs trampleVegetation.",
                "When false, flowers stay and the ground under them never becomes a path block.")
            .define("trampleFlowers", true);
        RESPECT_MOB_GRIEFING = builder.comment("Mobs only wear the ground when the mobGriefing game rule allows it.")
            .define("respectMobGriefing", true);
        WEAR_ON_CONTRAPTIONS = builder.comment(
                "Walking on a moving physics structure (Sable sub-level, e.g. a Create Aeronautics airship) wears its blocks.",
                "Off by default so decks and gardens on ships stay as built.")
            .define("wearOnContraptions", false);
        CONTRAPTIONS_WEAR_GROUND = builder.comment(
                "Sable physics structures wear the ground they drive, roll or slide across (requires Sable).",
                "Wheels are the blocks in the worn_ground:wheels block tag.")
            .define("contraptionsWearGround", true);
        CONTRAPTION_MULTIPLIER = builder.comment("Extra chance multiplier for ground touched by physics structures.")
            .defineInRange("contraptionMultiplier", 2.0, 0.0, 1000.0);
        SPEC = builder.build();
    }

    private Config() {}
}
