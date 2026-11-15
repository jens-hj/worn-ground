package dev.jensh.wornground.test;

import dev.jensh.wornground.WearHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Renders the gallery screenshots: builds small scenes on real plains terrain, lets the mod's own wear logic
 * and vanilla regrowth run for the stated number of trips / days, and photographs fixed camera angles.
 * Enabled with -Dwornground.showcase=<output dir>.
 */
public final class Showcase {
    private static final int TRIP_INTERVAL = Integer.getInteger("wornground.showcase.interval", 1200);
    private final Path out = Path.of(System.getProperty("wornground.showcase"));
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<Route> routes = new ArrayList<>();
    private boolean started, planned, done;
    private int ticks, wait;
    private long sprintUntil = -1;
    private BlockPos origin;
    private int trips;

    private interface Step {
        /** @return true when finished */
        boolean run(Minecraft mc, MinecraftServer server) throws Exception;
    }

    private record Route(List<BlockPos> waypoints, double multiplier, RandomSource random) {}

    public static void register() {
        var showcase = new Showcase();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> showcase.tick());
    }

    private void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 20 * 60 * 40) throw new IllegalStateException("Showcase timed out");
            if (!started) {
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("showcase",
                    new LevelSettings("Showcase", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT),
                    new WorldOptions(Long.getLong("wornground.showcase.seed", 20261115L), false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            var server = mc.getSingleplayerServer();
            if (mc.level == null || mc.player == null || server == null) return;
            if (!planned) {
                planned = true;
                mc.options.hideGui = true;
                mc.setScreen(null);
                plan();
            }
            if (mc.screen != null) mc.setScreen(null);
            if (wait > 0) {
                wait--;
                return;
            }
            while (!steps.isEmpty() && steps.peek().run(mc, server)) {
                steps.poll();
                if (wait > 0) return;
            }
            if (steps.isEmpty()) finish(mc, "ok");
        } catch (Throwable error) {
            error.printStackTrace();
            finish(mc, error.toString());
        }
    }

    private void finish(Minecraft mc, String message) {
        done = true;
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("result.txt"), message);
        } catch (Exception ignored) {
        }
        mc.stop();
    }

    // ---- plan ---------------------------------------------------------------------------------------------

    private void plan() {
        steps.add(onServer(this::findSite));
        if ("vehicle".equals(System.getProperty("wornground.showcase.mode"))) {
            planVehicle();
            return;
        }
        if (Boolean.getBoolean("wornground.showcase.siteOnly")) return;
        steps.add(onServer(this::buildScenes));
        shot("timelapse-0", 15, 15, 24, 180, 36);
        shot("pen-0", 55, 12, 16, 180, 38);
        traffic(20);
        shot("timelapse-20", 15, 15, 24, 180, 36);
        traffic(60);
        shot("pen-4-days", 55, 12, 16, 180, 38);
        shot("timelapse-80", 15, 15, 24, 180, 36);
        traffic(220);
        shot("timelapse-300", 15, 15, 24, 180, 36);
        shot("network", -6, 9, 12, 235, 24);
        shot("door", 15.5, 1.8, 0.5, 90, 8);
        shot("gaits", 13, 15, 57, 180, 50);
        shot("safe", 53, 10, 55, 180, 40);
        shot("pen-15-days", 55, 12, 16, 180, 38);
        steps.add(onServer(this::buildStages));
        shot("stages", 88, 5.5, 7.5, 180, 48);
    }

    /** A real Offroad car drives the same line a dozen times; photographed from behind while it rolls away. */
    private void planVehicle() {
        var holder = new VehicleScenario[1];
        steps.add(onServer(level -> {
            var car = new VehicleScenario("showcase car", origin.getX() + 40, "offroad:tire", 24, 1000, 0);
            car.surface = origin.getY();
            car.zBase = origin.getZ() - 34;
            holder[0] = car;
            level.setDayTime(3500);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            var player = level.getServer().getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, origin.getX() + 45.5, origin.getY() + 5.5, origin.getZ() + 22, 172, 20);
        }));
        int[] shots = {0};
        steps.add((mc, server) -> {
            VehicleScenario car = holder[0];
            CompletableFuture.runAsync(() -> car.tick(server.overworld(), server.overworld().getGameTime()), server).join();
            if (car.car == null) return false;
            double z = car.car.logicalPose().position().z() - origin.getZ();
            int wanted = shots[0] == 0 ? 2 : shots[0] == 1 ? 12 : 40;
            if (car.passCount >= wanted && car.direction < 0 && z < 6 && z > 2) {
                Files.createDirectories(out);
                Screenshot.grab(out.toFile(), "car-" + car.passCount + ".png", mc.getMainRenderTarget(), message -> {});
                System.out.println("[showcase] shot car-" + car.passCount);
                shots[0]++;
            }
            return shots[0] >= 3;
        });
        wait = 0;
    }

    private Step onServer(java.util.function.Consumer<ServerLevel> action) {
        return (mc, server) -> {
            CompletableFuture.runAsync(() -> action.accept(server.overworld()), server).join();
            return true;
        };
    }

    /** Runs {@code count} more round trips on every route, one per TRIP_INTERVAL ticks of real game time. */
    private void traffic(int count) {
        int target = trips + count;
        steps.add((mc, server) -> {
            long now = server.overworld().getGameTime();
            if (sprintUntil < 0) {
                sprintUntil = now + TRIP_INTERVAL;
                CompletableFuture.runAsync(() -> {
                    server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                    server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
                }, server).join();
            }
            if (now < sprintUntil) return false;
            CompletableFuture.runAsync(() -> routes.forEach(route -> walk(server.overworld(), route)), server).join();
            trips++;
            sprintUntil = trips < target ? now + TRIP_INTERVAL : -1;
            if (trips % 20 == 0) System.out.println("[showcase] trip " + trips);
            if (trips < target) return false;
            CompletableFuture.runAsync(() -> server.tickRateManager().stopSprinting(), server).join();
            return true;
        });
    }

    private void shot(String name, double x, double y, double z, float yaw, float pitch) {
        steps.add((mc, server) -> {
            CompletableFuture.runAsync(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(level.getDayTime() / 24000 * 24000 + 24000 + 3500);
                level.setWeatherParameters(100000, 0, false, false);
                var player = server.getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, origin.getX() + x, origin.getY() + 1 + y, origin.getZ() + z, yaw, pitch);
            }, server).join();
            wait = 160;
            return true;
        });
        steps.add((mc, server) -> {
            Files.createDirectories(out);
            Screenshot.grab(out.toFile(), name + ".png", mc.getMainRenderTarget(), message -> {});
            System.out.println("[showcase] shot " + name);
            wait = 10;
            return true;
        });
    }

    // ---- terrain ------------------------------------------------------------------------------------------

    private static boolean isGround(BlockState state) {
        return !state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
            && !state.is(BlockTags.FLOWERS) && !state.is(BlockTags.SAPLINGS) && state.getFluidState().isEmpty()
            && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Level#getHeight answers the minimum height for chunks that are not loaded yet; this generates them. */
    private static int height(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private static int groundY(ServerLevel level, int x, int z) {
        int y = height(level, x, z);
        var pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight() && !isGround(level.getBlockState(pos.setY(y))) ) y--;
        return y;
    }

    private static final int X0 = -30, X1 = 118, Z0 = -46, Z1 = 86, MARGIN = 12;

    private void findSite(ServerLevel level) {
        var found = level.findClosestBiome3d(holder -> holder.is(Biomes.PLAINS), level.getSharedSpawnPos(), 3000, 32, 64);
        BlockPos centre = found == null ? level.getSharedSpawnPos() : found.getFirst();
        int best = Integer.MAX_VALUE;
        for (int dx = -160; dx <= 160; dx += 16) {
            for (int dz = -160; dz <= 160; dz += 16) {
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, bad = 0;
                for (int x = X0; x <= X1; x += 8) {
                    for (int z = Z0; z <= Z1; z += 8) {
                        int wx = centre.getX() + dx + x, wz = centre.getZ() + dz + z;
                        int y = height(level, wx, wz) - 1;
                        var pos = new BlockPos(wx, y, wz);
                        if (!level.getBiome(pos).is(Biomes.PLAINS)) bad += 10;
                        if (!level.getFluidState(pos).isEmpty()) bad += 3;
                        min = Math.min(min, y);
                        max = Math.max(max, y);
                    }
                }
                int score = (max - min) + bad;
                if (score < best) {
                    best = score;
                    origin = new BlockPos(centre.getX() + dx, 0, centre.getZ() + dz);
                }
            }
        }
        // Level a meadow: median height inside, blended into the natural terrain over the margin.
        var heights = new ArrayList<Integer>();
        for (int x = X0; x <= X1; x += 4) {
            for (int z = Z0; z <= Z1; z += 4) heights.add(groundY(level, origin.getX() + x, origin.getZ() + z));
        }
        heights.sort(null);
        int target = heights.get(heights.size() / 2);
        origin = new BlockPos(origin.getX(), target, origin.getZ());
        System.out.println("[showcase] site " + origin + " score " + best);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        for (int cx = (origin.getX() + X0) >> 4; cx <= (origin.getX() + X1) >> 4; cx++) {
            for (int cz = (origin.getZ() + Z0) >> 4; cz <= (origin.getZ() + Z1) >> 4; cz++) level.setChunkForced(cx, cz, true);
        }
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getGameRules().getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT).set(10_000_000, level.getServer());
        for (int x = X0 - 48; x <= X1 + 48; x += 32) {
            for (int z = Z0 - 48; z <= Z1 + 48; z += 32) {
                commands.performPrefixedCommand(source, "fillbiome " + (origin.getX() + x) + " " + (target - 24) + " " + (origin.getZ() + z)
                    + " " + (origin.getX() + x + 31) + " " + (target + 60) + " " + (origin.getZ() + z + 31) + " minecraft:plains");
            }
        }
        RandomSource random = RandomSource.create(42);
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                int edge = Math.min(Math.min(x - X0, X1 - x), Math.min(z - Z0, Z1 - z));
                int natural = groundY(level, wx, wz);
                float blend = Math.min(1.0F, edge / (float) MARGIN);
                int y = Math.round(natural + (target - natural) * blend);
                var pos = new BlockPos.MutableBlockPos(wx, 0, wz);
                for (int clear = Math.max(natural, y) + 30; clear > y; clear--) level.setBlock(pos.setY(clear), Blocks.AIR.defaultBlockState(), 2);
                for (int fill = Math.min(natural, y) - 1; fill < y; fill++) level.setBlock(pos.setY(fill), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(pos.setY(y), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                float roll = random.nextFloat();
                BlockState plant = roll < 0.22F ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 0.235F ? Blocks.DANDELION.defaultBlockState()
                    : roll < 0.245F ? Blocks.POPPY.defaultBlockState() : roll < 0.252F ? Blocks.OXEYE_DAISY.defaultBlockState() : null;
                if (plant != null) level.setBlock(pos.setY(y + 1), plant, 2);
            }
        }
    }

    private BlockPos at(ServerLevel level, int x, int z) {
        int wx = origin.getX() + x, wz = origin.getZ() + z;
        return new BlockPos(wx, groundY(level, wx, wz), wz);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }

    // ---- traffic ------------------------------------------------------------------------------------------

    private void route(double multiplier, long seed, BlockPos... waypoints) {
        routes.add(new Route(List.of(waypoints), multiplier, RandomSource.create(seed)));
    }

    private void walk(ServerLevel level, Route route) {
        RandomSource random = route.random();
        float roll = random.nextFloat();
        int offset = roll < 0.15F ? -1 : roll < 0.85F ? 0 : 1;
        for (int i = 0; i + 1 < route.waypoints().size(); i++) {
            BlockPos from = route.waypoints().get(i), to = route.waypoints().get(i + 1);
            int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
            int length = Math.max(Math.abs(dx), Math.abs(dz));
            boolean alongX = Math.abs(dx) >= Math.abs(dz);
            for (int step = 0; step <= length; step++) {
                if (random.nextFloat() < 0.1F) offset = Math.clamp(offset + (random.nextBoolean() ? 1 : -1), -1, 1);
                int x = from.getX() + Math.round(dx * step / (float) Math.max(length, 1)) + (alongX ? 0 : offset);
                int z = from.getZ() + Math.round(dz * step / (float) Math.max(length, 1)) + (alongX ? offset : 0);
                WearHandler.tryWear(level, new BlockPos(x, groundY(level, x, z), z), route.multiplier(), random);
            }
        }
    }

    // ---- scenes -------------------------------------------------------------------------------------------

    private void buildScenes(ServerLevel level) {
        // Homestead: cabin, well and a fenced crop field, connected only by where people walk.
        cabin(level, 0, -2);
        well(level, 30, 4);
        field(level, 12, -22);
        BlockPos door = at(level, 6, 0), fork = at(level, 15, 0), wellSide = at(level, 28, 5), gate = at(level, 16, -15);
        route(1.0, 1, door, fork, wellSide);
        route(1.0, 2, door, fork, gate);

        // Pasture: four cows in a pen next to an identical empty one.
        pen(level, 46, -6, 4);
        pen(level, 60, -6, 0);

        // Gaits: the same number of trips sprinting, walking, and sneaking / in leather boots.
        route(2.0, 3, at(level, 0, 36), at(level, 26, 36));
        route(1.0, 4, at(level, 0, 42), at(level, 26, 42));
        route(0.0, 5, at(level, 0, 48), at(level, 26, 48));

        // Builds stay safe: a route crossing a railway and a flower bed.
        for (int z = 30; z <= 54; z++) {
            BlockPos ground = at(level, 50, z);
            set(level, ground, Blocks.GRAVEL.defaultBlockState());
            set(level, ground.above(), Blocks.RAIL.defaultBlockState());
        }
        for (int x = 56; x <= 59; x++) {
            for (int z = 39; z <= 45; z++) {
                BlockPos ground = at(level, x, z);
                set(level, ground, Blocks.GRASS_BLOCK.defaultBlockState());
                set(level, ground.above(), ((x + z) % 3 == 0 ? Blocks.POPPY : (x + z) % 3 == 1 ? Blocks.DANDELION : Blocks.CORNFLOWER).defaultBlockState());
            }
        }
        route(1.0, 6, at(level, 40, 42), at(level, 66, 42));
        // The rails sit on gravel; show that wearable ground under rails is kept too.
        for (int z = 40; z <= 44; z++) set(level, at(level, 50, z).above(0), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int z = 40; z <= 44; z++) set(level, at(level, 50, z).above(), Blocks.RAIL.defaultBlockState());
    }

    private void cabin(ServerLevel level, int x0, int z0) {
        int floor = at(level, x0 + 2, z0 + 2).getY();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), log = Blocks.OAK_LOG.defaultBlockState();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                BlockPos base = new BlockPos(origin.getX() + x0 + x, floor, origin.getZ() + z0 + z);
                for (int y = -3; y <= 0; y++) set(level, base.above(y), Blocks.COBBLESTONE.defaultBlockState());
                boolean edgeX = x == 0 || x == 5, edgeZ = z == 0 || z == 4;
                for (int y = 1; y <= 3; y++) {
                    BlockState state = edgeX && edgeZ ? log : edgeX || edgeZ ? planks : Blocks.AIR.defaultBlockState();
                    if (y == 2 && (edgeX ^ edgeZ) && (z == 2 && x == 0 || x == 2 && edgeZ || x == 3 && edgeZ)) {
                        state = Blocks.GLASS_PANE.defaultBlockState();
                    }
                    set(level, base.above(y), state);
                }
            }
        }
        // Gable roof with the ridge along x.
        for (int x = -1; x <= 6; x++) {
            for (int step = 0; step < 3; step++) {
                BlockPos north = new BlockPos(origin.getX() + x0 + x, floor + 4 + step, origin.getZ() + z0 - 1 + step);
                BlockPos south = new BlockPos(origin.getX() + x0 + x, floor + 4 + step, origin.getZ() + z0 + 5 - step);
                set(level, north, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                set(level, south, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                if (x >= 0 && x <= 5) {
                    for (int z = step; z <= 4 - step; z++) {
                        if (x == 0 || x == 5) set(level, new BlockPos(origin.getX() + x0 + x, floor + 4 + step, origin.getZ() + z0 + z), planks);
                    }
                }
            }
        }
        // Door in the east wall.
        BlockPos door = new BlockPos(origin.getX() + x0 + 5, floor + 1, origin.getZ() + z0 + 2);
        BlockState doorState = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        level.setBlock(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        set(level, door.offset(1, 1, -1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
        set(level, door.offset(1, 0, -1), Blocks.SPRUCE_FENCE.defaultBlockState());
    }

    private void well(ServerLevel level, int x0, int z0) {
        int y = at(level, x0 + 1, z0 + 1).getY();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos pos = new BlockPos(origin.getX() + x0 + x, y, origin.getZ() + z0 + z);
                boolean middle = x == 1 && z == 1;
                set(level, pos.below(), Blocks.COBBLESTONE.defaultBlockState());
                set(level, pos, middle ? Blocks.WATER.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                set(level, pos.above(), middle ? Blocks.AIR.defaultBlockState() : (x + z) % 2 == 0
                    ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.COBBLESTONE_SLAB.defaultBlockState());
                if ((x + z) % 2 == 0 && !middle) {
                    set(level, pos.above(2), Blocks.SPRUCE_FENCE.defaultBlockState());
                    set(level, pos.above(3), Blocks.SPRUCE_FENCE.defaultBlockState());
                }
                set(level, pos.above(4), Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        }
    }

    private void field(ServerLevel level, int x0, int z0) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 7; z++) {
                BlockPos ground = at(level, x0 + x, z0 + z);
                boolean edge = x == 0 || x == 8 || z == 0 || z == 6;
                if (edge) {
                    boolean gate = x == 4 && z == 6;
                    set(level, ground.above(), gate ? Blocks.SPRUCE_FENCE_GATE.defaultBlockState() : Blocks.SPRUCE_FENCE.defaultBlockState());
                } else if (x == 4) {
                    set(level, ground, Blocks.WATER.defaultBlockState());
                } else {
                    set(level, ground, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                    set(level, ground.above(), Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
                }
            }
        }
    }

    private void pen(ServerLevel level, int x0, int z0, int cows) {
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 9; z++) {
                BlockPos ground = at(level, x0 + x, z0 + z);
                if (level.getBlockState(ground.above()).canBeReplaced()) set(level, ground.above(), Blocks.AIR.defaultBlockState());
                set(level, ground, Blocks.GRASS_BLOCK.defaultBlockState());
                if (x == -1 || z == -1 || x == 9 || z == 9) set(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        for (int i = 0; i < cows; i++) {
            var cow = EntityType.COW.spawn(level, at(level, x0 + 2 + 2 * (i % 2), z0 + 2 + 2 * (i / 2)).above(), MobSpawnType.COMMAND);
            if (cow != null) cow.setPersistenceRequired();
        }
    }

    /** Explainer strip: the same ground after 0, 1, 2 and 3 successful wear steps. */
    private void buildStages(ServerLevel level) {
        for (int stage = 0; stage < 4; stage++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos ground = at(level, 80 + stage * 4 + x, z);
                    set(level, ground.above(), Blocks.AIR.defaultBlockState());
                    set(level, ground, Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int i = 0; i < stage; i++) WearHandler.tryWear(level, ground, 1000, level.getRandom());
                }
            }
        }
    }
}
