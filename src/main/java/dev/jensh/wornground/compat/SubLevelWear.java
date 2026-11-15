package dev.jensh.wornground.compat;

import dev.jensh.wornground.Config;
import dev.jensh.wornground.WearHandler;
import dev.jensh.wornground.WornGround;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sable physics structures (Create Aeronautics / Offroad vehicles, landed airships, sleds) wear the ground they
 * move across: every world block newly touched by the underside of a sub-level, or rolled over by a wheel,
 * counts as one step. Only loaded when Sable is installed.
 */
public final class SubLevelWear {
    private static final int SAMPLE_INTERVAL = 2;
    private static final int UNDERSIDE_REFRESH_INTERVAL = 100;
    private static final int MAX_FOOTPRINT = 64 * 64;
    private static final int STALE_AFTER = 200;
    /** Depths below a block centre that are probed for ground: plain blocks must touch, wheels hang on suspension. */
    private static final double[] BLOCK_PROBES = {0.55};
    private static final double[] WHEEL_PROBES = {0.55, 1.3, 2.05, 2.8};

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    private SubLevelWear() {}

    private static final class Tracked {
        long[] underside = new long[0];
        boolean[] wheel = new boolean[0];
        long undersideAge = -UNDERSIDE_REFRESH_INTERVAL;
        long lastSeen;
        LongOpenHashSet touching;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Config.CONTRAPTIONS_WEAR_GROUND.get()) return;
        long time = level.getGameTime();
        if (time % SAMPLE_INTERVAL != 0) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        double multiplier = Config.CHANCE_MULTIPLIER.get() * Config.CONTRAPTION_MULTIPLIER.get();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) continue;
            sample(level, subLevel, time, multiplier);
        }
        if (time % STALE_AFTER == 0) TRACKED.values().removeIf(tracked -> time - tracked.lastSeen > STALE_AFTER);
    }

    private static void sample(ServerLevel level, ServerSubLevel subLevel, long time, double multiplier) {
        Tracked tracked = TRACKED.computeIfAbsent(subLevel.getUniqueId(), id -> new Tracked());
        tracked.lastSeen = time;
        if (time - tracked.undersideAge >= UNDERSIDE_REFRESH_INTERVAL) {
            scanUnderside(level, subLevel, tracked);
            tracked.undersideAge = time;
        }

        var pose = subLevel.logicalPose();
        var touching = new LongOpenHashSet();
        var point = new Vector3d();
        var probe = new BlockPos.MutableBlockPos();
        for (int i = 0; i < tracked.underside.length; i++) {
            long local = tracked.underside[i];
            point.set(BlockPos.getX(local) + 0.5, BlockPos.getY(local) + 0.5, BlockPos.getZ(local) + 0.5);
            pose.transformPosition(point);
            for (double depth : tracked.wheel[i] ? WHEEL_PROBES : BLOCK_PROBES) {
                probe.set(Math.floor(point.x), Math.floor(point.y - depth), Math.floor(point.z));
                if (!level.isLoaded(probe)) break;
                if (level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) continue;
                touching.add(probe.asLong());
                break;
            }
        }

        LongOpenHashSet before = tracked.touching;
        tracked.touching = touching;
        // A freshly assembled or loaded structure has not moved onto anything yet.
        if (before == null) return;
        for (long ground : touching) {
            if (!before.contains(ground)) WearHandler.tryWear(level, BlockPos.of(ground), multiplier, level.getRandom());
        }
    }

    /** Collects the lowest block of every column of the structure, in plot coordinates. */
    private static void scanUnderside(ServerLevel level, ServerSubLevel subLevel, Tracked tracked) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        var blocks = new LongArrayList();
        var wheels = new it.unimi.dsi.fastutil.booleans.BooleanArrayList();
        if (bounds != null && (long) bounds.width() * bounds.length() <= MAX_FOOTPRINT) {
            var pos = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir()) continue;
                        boolean wheel = state.is(WornGround.WHEELS);
                        if (!wheel && state.getCollisionShape(level, pos).isEmpty()) continue;
                        // A tire sits beside its mount, on the side the mount faces.
                        blocks.add(wheel && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).asLong() : pos.asLong());
                        wheels.add(wheel);
                        break;
                    }
                }
            }
        }
        tracked.underside = blocks.toLongArray();
        tracked.wheel = wheels.toBooleanArray();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKED.clear();
    }
}
