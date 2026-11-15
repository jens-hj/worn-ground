package dev.jensh.wornground.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A real Create Aeronautics (Offroad) car: casing chassis, four wheel mounts with tires, creative motors.
 * It drives back and forth along a grass strip under Sable physics; the wheel tracks are censused per pass.
 */
final class VehicleScenario implements Lab.Scenario {
    static final int TRACK_START = 12;
    static final int TRACK_END = 44;

    final String name;
    final int x0;
    final String tire;
    final int rpm;
    final int maxPasses;
    final long maxTicks;
    final JsonArray passes = new JsonArray();
    final JsonArray log = new JsonArray();
    final List<BlockPos> motors = new ArrayList<>();
    ServerSubLevel car;
    int direction = 1;
    int passCount;
    long builtAt = -1;
    long lastTurn;

    final int pause;
    long resumeAt = -1;

    int surface = Lab.SURFACE;
    int zBase;

    VehicleScenario(String name, int x0, String tire, int rpm, int maxPasses, int pause) {
        this.pause = pause;
        this.name = name;
        this.x0 = x0;
        this.tire = tire;
        this.rpm = rpm;
        this.maxPasses = maxPasses;
        this.maxTicks = maxPasses <= 10 ? 1500 : 3_000_000;
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private void build(ServerLevel level) {
        int y = surface + 3;
        int z0 = zBase + 4;
        var blocks = new ArrayList<BlockPos>();
        BlockState casing = block("create:andesite_casing").defaultBlockState();
        BlockState mount = block("offroad:wheel_mount").defaultBlockState();
        BlockState motor = block("create:creative_motor").defaultBlockState();
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                BlockPos pos = new BlockPos(x0 + dx, y, z0 + dz);
                boolean axle = dz == 0 || dz == 3;
                BlockState state = casing;
                if (axle && (dx == 0 || dx == 3)) {
                    state = mount.setValue(BlockStateProperties.HORIZONTAL_FACING, dx == 0 ? Direction.WEST : Direction.EAST);
                } else if (axle) {
                    state = motor.setValue(BlockStateProperties.FACING, dx == 1 ? Direction.WEST : Direction.EAST);
                    motors.add(pos);
                }
                level.setBlockAndUpdate(pos, state);
                blocks.add(pos);
                if (state.is(mount.getBlock())) {
                    var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(tire)));
                    var entity = level.getBlockEntity(pos);
                    CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
                    tag.put("CurrentStack", stack.save(level.registryAccess()));
                    entity.loadWithComponents(tag, level.registryAccess());
                    entity.setChanged();
                }
            }
        }
        setSpeed(level, motors);
        car = SubLevelAssemblyHelper.assembleBlocks(level, new BlockPos(x0, y, z0), blocks,
            new BoundingBox3i(x0, y, z0, x0 + 3, y, z0 + 3));
        // Motors now live in the plot; find them again there.
        motors.clear();
        var bounds = car.getPlot().getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (level.getBlockState(pos).is(motor.getBlock())) motors.add(pos.immutable());
        }
    }

    private void setSpeed(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            // Motors on opposite sides face opposite ways, so they need opposite signs to roll the same way.
            boolean west = state.getValue(BlockStateProperties.FACING) == Direction.WEST;
            // Re-placing the motor makes its kinetic network pick up the new speed.
            level.removeBlock(pos, false);
            level.setBlockAndUpdate(pos, state);
            var entity = level.getBlockEntity(pos);
            CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
            tag.putInt("ScrollValue", (west ? -1 : 1) * direction * (resumeAt >= 0 ? 0 : rpm));
            entity.loadWithComponents(tag, level.registryAccess());
            entity.setChanged();
        }
    }

    @Override
    public boolean tick(ServerLevel level, long tick) {
        if (builtAt < 0) {
            if (tick < 40) return true;
            build(level);
            builtAt = tick;
            lastTurn = tick;
            return true;
        }
        if (car == null || car.isRemoved()) return false;
        if (resumeAt >= 0) {
            if (tick < resumeAt) return true;
            resumeAt = -1;
            lastTurn = tick;
            setSpeed(level, motors);
            return true;
        }
        var position = car.logicalPose().position();
        if (tick % 100 == 0 && maxPasses <= 10) {
            System.out.println("[lab] " + name + " t=" + (tick - builtAt) + " pos=" + position + " passes=" + passCount);
        }
        if (log.size() < 400 && tick % 20 == 0) {
            var entry = new JsonArray();
            entry.add(tick - builtAt);
            entry.add(Math.round(position.x() * 100) / 100.0);
            entry.add(Math.round(position.y() * 100) / 100.0);
            entry.add(Math.round(position.z() * 100) / 100.0);
            log.add(entry);
        }
        boolean turn = direction > 0 ? position.z() > zBase + TRACK_END + 6 : position.z() < zBase + TRACK_START - 6;
        // A stuck car gives up on this pass after a minute.
        if (turn || tick - lastTurn > 1200) {
            if (turn) {
                passCount++;
                var sample = new JsonObject();
                sample.add("left", Lab.json(Lab.census(level, x0 - 2, x0 - 1, TRACK_START, TRACK_END)));
                sample.add("right", Lab.json(Lab.census(level, x0 + 4, x0 + 5, TRACK_START, TRACK_END)));
                sample.add("under", Lab.json(Lab.census(level, x0, x0 + 3, TRACK_START, TRACK_END)));
                sample.addProperty("x", position.x());
                sample.addProperty("ticks", tick - lastTurn);
                passes.add(sample);
            }
            direction = -direction;
            lastTurn = tick;
            if (pause > 0) resumeAt = tick + pause;
            setSpeed(level, motors);
        }
        return passCount < maxPasses && tick - builtAt < maxTicks;
    }

    @Override
    public JsonObject result() {
        var object = new JsonObject();
        object.addProperty("type", "vehicle");
        object.addProperty("name", name);
        object.add("passes", passes);
        object.add("log", log);
        return object;
    }
}
