package dev.jensh.wornground.test;

import dev.jensh.wornground.Config;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Headless A/B benchmark: whole-server tick time with the mod's wear switched on and off in alternating blocks,
 * first with a herd of path-making mobs, then with Sable structures resting on the ground. Run with a JFR
 * recording for the method-level picture. Enabled with -Dwornground.lab=perf.
 */
public final class Perf {
    private static final int BLOCK = 400;
    private static final int WARMUP = 40;
    private static final int BLOCKS = 16;
    private static final int SETTLE = 400;
    private static final int COWS = Integer.getInteger("wornground.perf.cows", 300);

    private final LongArrayList[][] samples = {{new LongArrayList(), new LongArrayList()}, {new LongArrayList(), new LongArrayList()}};
    private boolean started;
    private int stage;
    private long stageStart;
    private long ticks;
    private long begin;

    @SubscribeEvent
    public void onTickStart(ServerTickEvent.Pre event) {
        begin = System.nanoTime();
    }

    @SubscribeEvent
    public void onTickEnd(ServerTickEvent.Post event) {
        long elapsed = System.nanoTime() - begin;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (!started) {
            started = true;
            setUp(server, level);
            stageStart = SETTLE;
            return;
        }
        ticks++;
        long local = ticks - stageStart;
        if (local < 0) return;
        int block = (int) (local / BLOCK);
        if (block >= BLOCKS) {
            report(stage);
            if (stage == 0 && ModList.get().isLoaded("sable")) {
                stage = 1;
                Config.MOBS.set(true);
                System.out.println("[perf] structures: " + PerfShips.build(level, Lab.SURFACE));
                stageStart = ticks + SETTLE;
                return;
            }
            server.halt(false);
            return;
        }
        boolean on = block % 2 == 0;
        if (local % BLOCK == 0) (stage == 0 ? Config.MOBS : Config.CONTRAPTIONS_WEAR_GROUND).set(on);
        else if (local % BLOCK >= WARMUP) samples[stage][on ? 0 : 1].add(elapsed);
    }

    private void setUp(MinecraftServer server, ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        for (int chunkX = -1; chunkX <= 12; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 8; chunkZ++) level.setChunkForced(chunkX, chunkZ, true);
        }
        // One big fenced meadow, cows free to wander.
        int size = 48;
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= size; j++) {
                if (i == -1 || j == -1 || i == size || j == size) {
                    level.setBlockAndUpdate(new BlockPos(i, Lab.SURFACE + 1, j), Blocks.OAK_FENCE.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < COWS; i++) {
            var cow = EntityType.COW.spawn(level, new BlockPos(1 + i % (size - 2), Lab.SURFACE + 1, 1 + (i * 7) % (size - 2)), MobSpawnType.COMMAND);
            if (cow != null) cow.setPersistenceRequired();
        }
        server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        System.out.println("[perf] started with " + COWS + " cows");
    }

    private void report(int stage) {
        String name = stage == 0 ? COWS + " cows, mob wear" : "sable structures, contraption wear";
        for (int i = 0; i < 2; i++) {
            long[] values = samples[stage][i].toLongArray();
            java.util.Arrays.sort(values);
            double mean = java.util.Arrays.stream(values).average().orElse(0) / 1000.0;
            System.out.printf("[perf] %-36s %-3s n=%d mean=%.1fus p50=%.1fus p99=%.1fus max=%.1fus%n", name, i == 0 ? "ON" : "OFF",
                values.length, mean, values[values.length / 2] / 1000.0, values[(int) (values.length * 0.99)] / 1000.0,
                values[values.length - 1] / 1000.0);
        }
    }
}
