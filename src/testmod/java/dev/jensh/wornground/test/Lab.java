package dev.jensh.wornground.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jensh.wornground.Config;
import dev.jensh.wornground.WearHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless measurement run on a superflat world: scripted foot traffic and real penned cows against real
 * vanilla grass regrowth. Enabled with -Dwornground.lab; writes JSON to -Dwornground.lab.out and stops.
 */
public final class Lab {
    static final int SURFACE = -61;
    static final int LENGTH = 32;
    static final int REPLICATES = 6;

    private final List<Scenario> scenarios = new ArrayList<>();
    private boolean started;
    private long ticks;

    interface Scenario {
        /** @return true while more work remains */
        boolean tick(ServerLevel level, long tick);
        JsonObject result();
    }

    static int[] census(ServerLevel level, int x0, int x1, int z0, int z1) {
        int[] counts = new int[5];
        var pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                Block block = level.getBlockState(pos.set(x, SURFACE, z)).getBlock();
                counts[block == Blocks.GRASS_BLOCK ? 0 : block == Blocks.DIRT ? 1 : block == Blocks.COARSE_DIRT ? 2
                    : block == Blocks.DIRT_PATH ? 3 : 4]++;
            }
        }
        return counts;
    }

    static JsonArray json(int[] values) {
        var array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }

    /** One route across a meadow, walked once every {@code interval} ticks. */
    static final class Lane implements Scenario {
        final String name;
        final int z;
        final int interval;
        final boolean sprint;
        /** Blocks between landings when the traveller jumps all the way; 0 for plain steps. */
        final double stride;
        final int maxTrips;
        final RandomSource random;
        final JsonArray centre = new JsonArray();
        final JsonArray band = new JsonArray();
        int trips;

        Lane(String name, int z, int interval, boolean sprint, int maxTrips, long seed) {
            this(name, z, interval, sprint, 0, maxTrips, seed);
        }

        Lane(String name, int z, int interval, boolean sprint, double stride, int maxTrips, long seed) {
            this.stride = stride;
            this.name = name;
            this.z = z;
            this.interval = interval;
            this.sprint = sprint;
            this.maxTrips = maxTrips;
            this.random = RandomSource.create(seed);
        }

        @Override
        public boolean tick(ServerLevel level, long tick) {
            if (trips >= maxTrips) return false;
            if (tick % interval != 0) return true;
            double multiplier = Config.CHANCE_MULTIPLIER.get() * (sprint ? Config.SPRINT_MULTIPLIER.get() : 1.0);
            // People do not walk on rails: each trip picks a line and drifts a little around it.
            int offset = pick();
            if (stride > 0) multiplier *= Config.LANDING_MULTIPLIER.get();
            // Jumpers only touch the block they land on, and never take off from exactly the same spot twice.
            double next = stride > 0 ? random.nextDouble() * stride : 0;
            for (int x = 0; x < LENGTH; x++) {
                if (random.nextFloat() < 0.1F) offset = Math.clamp(offset + (random.nextBoolean() ? 1 : -1), -1, 1);
                if (x < (int) next) continue;
                next += Math.max(stride, 1);
                WearHandler.tryWear(level, new BlockPos(x, SURFACE, z + offset), multiplier, random);
            }
            trips++;
            centre.add(json(census(level, 0, LENGTH - 1, z, z)));
            band.add(json(census(level, 0, LENGTH - 1, z - 1, z + 1)));
            return true;
        }

        private int pick() {
            float roll = random.nextFloat();
            return roll < 0.15F ? -1 : roll < 0.85F ? 0 : 1;
        }

        @Override
        public JsonObject result() {
            var object = new JsonObject();
            object.addProperty("type", "lane");
            object.addProperty("name", name);
            object.addProperty("interval", interval);
            object.addProperty("sprint", sprint);
            object.add("centre", centre);
            object.add("band", band);
            return object;
        }
    }

    /** Real cows with their normal AI in a fenced grass pen. */
    static final class Pen implements Scenario {
        final String name;
        final int x0;
        final int z0;
        final int size;
        final int cows;
        final int days;
        final JsonArray samples = new JsonArray();
        boolean built;

        Pen(String name, int x0, int z0, int size, int cows, int days) {
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.size = size;
            this.cows = cows;
            this.days = days;
        }

        @Override
        public boolean tick(ServerLevel level, long tick) {
            if (!built) {
                built = true;
                for (int i = -1; i <= size; i++) {
                    for (int j = -1; j <= size; j++) {
                        if (i == -1 || j == -1 || i == size || j == size) {
                            level.setBlockAndUpdate(new BlockPos(x0 + i, SURFACE + 1, z0 + j), Blocks.OAK_FENCE.defaultBlockState());
                        }
                    }
                }
                for (int i = 0; i < cows; i++) {
                    var cow = EntityType.COW.spawn(level, new BlockPos(x0 + 1 + i % size, SURFACE + 1, z0 + size / 2), MobSpawnType.COMMAND);
                    if (cow != null) cow.setPersistenceRequired();
                }
            }
            if (tick % 2400 == 0) samples.add(json(census(level, x0, x0 + size - 1, z0, z0 + size - 1)));
            return tick < days * 24000L;
        }

        @Override
        public JsonObject result() {
            var object = new JsonObject();
            object.addProperty("type", "pen");
            object.addProperty("name", name);
            object.addProperty("size", size);
            object.addProperty("cows", cows);
            object.addProperty("sampleTicks", 2400);
            object.add("samples", samples);
            return object;
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (!started) {
            started = true;
            setUp(server, level);
            return;
        }
        ticks++;
        boolean busy = false;
        for (Scenario scenario : scenarios) busy |= scenario.tick(level, ticks);
        if (ticks % 200_000 == 0) System.out.println("[lab] tick " + ticks);
        if (!busy) finish(server);
    }

    private void setUp(MinecraftServer server, ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(1000);
        String mode = System.getProperty("wornground.lab");
        if (mode.startsWith("vehicle")) {
            int passes = mode.endsWith("quick") ? 6 : 200;
            scenarios.add(new VehicleScenario("car/back-and-forth", 16, "offroad:tire", 32, passes, 0));
            scenarios.add(new VehicleScenario("car, large tires/back-and-forth", 40, "offroad:large_tire", 48, passes, 0));
            if (passes > 10) {
                scenarios.add(new VehicleScenario("car/every-minute", 64, "offroad:tire", 32, 200, 1200));
                scenarios.add(new VehicleScenario("car/every-5-minutes", 88, "offroad:tire", 32, 150, 6000));
            }
            for (int chunkX = -1; chunkX <= 7; chunkX++) {
                for (int chunkZ = -2; chunkZ <= 5; chunkZ++) level.setChunkForced(chunkX, chunkZ, true);
            }
            if (!mode.endsWith("quick")) server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
            return;
        }
        if (mode.equals("jumps")) {
            int lane = 8;
            for (int sprint = 0; sprint <= 1; sprint++) {
                for (int replicate = 0; replicate < REPLICATES; replicate++) {
                    // Air time of a jump is 12 ticks: about 2.6 blocks walking, 4.3 sprinting.
                    scenarios.add(new Lane("every-minute/" + (sprint == 1 ? "sprint-jump" : "walk-jump"), lane, 1200,
                        sprint == 1, sprint == 1 ? 4.3 : 2.6, 400, 1000L * lane + replicate));
                    lane += 10;
                }
            }
            for (int chunkX = -1; chunkX <= 3; chunkX++) {
                for (int chunkZ = -1; chunkZ <= (lane >> 4) + 1; chunkZ++) level.setChunkForced(chunkX, chunkZ, true);
            }
            server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
            return;
        }
        String[] names = {"back-and-forth", "every-minute", "every-5-minutes", "twice-a-day", "once-a-day"};
        int[] intervals = {160, 1200, 6000, 12000, 24000};
        boolean quick = "quick".equals(System.getProperty("wornground.lab"));
        int[] trips = quick ? new int[] {10, 10, 4, 2, 1} : new int[] {400, 400, 400, 200, 100};
        int days = quick ? 1 : 20;
        int z = 8;
        for (int i = 0; i < names.length && !mode.equals("pens"); i++) {
            for (int sprint = 0; sprint <= 1; sprint++) {
                for (int replicate = 0; replicate < REPLICATES; replicate++) {
                    scenarios.add(new Lane(names[i] + (sprint == 1 ? "/sprint" : "/walk"), z, intervals[i], sprint == 1,
                        trips[i], 1000L * z + replicate));
                    z += 10;
                }
            }
        }
        int penX = 48;
        for (int replicate = 0; replicate < 3; replicate++) {
            scenarios.add(new Pen("4 cows in 9x9", penX, 16 + replicate * 40, 9, 4, days));
            scenarios.add(new Pen("2 cows in 15x15", penX + 20, 16 + replicate * 40, 15, 2, days));
            scenarios.add(new Pen("8 cows in 7x7", penX + 44, 16 + replicate * 40, 7, 8, days));
        }
        // Force-loaded chunks keep random ticks (grass regrowth) and mob AI running without a player.
        for (int chunkX = -1; chunkX <= 7; chunkX++) {
            for (int chunkZ = -1; chunkZ <= Math.max(z >> 4, 9) + 1; chunkZ++) level.setChunkForced(chunkX, chunkZ, true);
        }
        server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        System.out.println("[lab] started " + scenarios.size() + " scenarios");
    }

    private void finish(MinecraftServer server) {
        var root = new JsonObject();
        root.addProperty("ticks", ticks);
        root.addProperty("length", LENGTH);
        var array = new JsonArray();
        for (Scenario scenario : scenarios) array.add(scenario.result());
        root.add("scenarios", array);
        try {
            Path out = Path.of(System.getProperty("wornground.lab.out"));
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().create().toJson(root));
            System.out.println("[lab] wrote " + out);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        scenarios.clear();
        server.halt(false);
    }
}
