package dev.jensh.wornground;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

public final class WearHandler {
    /**
     * Physics mods such as Sable keep a contraption's blocks in a far-away plot of the same level and report
     * that plot position as the block an entity stands on. Real ground is always right below the entity.
     */
    private static final int MAX_GROUND_DISTANCE_SQUARED = 4 * 4;
    private static final long NO_BLOCK = Long.MIN_VALUE;

    /** Last supporting block per entity id, so wear is rolled once per block stepped onto. Server thread only. */
    private static final Int2LongOpenHashMap LAST_BLOCK = new Int2LongOpenHashMap();

    static {
        LAST_BLOCK.defaultReturnValue(NO_BLOCK);
    }

    private WearHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !entity.onGround()
            || !(entity.level() instanceof ServerLevel level)) return;
        boolean player = entity instanceof Player;
        if (player ? !Config.PLAYERS.get() : !Config.MOBS.get() || !entity.getType().is(WornGround.PATH_MAKERS)) return;

        BlockPos ground = entity.getOnPos();
        long key = ground.asLong();
        long previous = LAST_BLOCK.put(entity.getId(), key);
        // The first sighting (spawn, login, landing from a ride) is not a step.
        if (previous == key || previous == NO_BLOCK) return;
        if (!canWear(level, entity, ground)) return;

        double multiplier = Config.CHANCE_MULTIPLIER.get();
        if (entity.isSprinting()) multiplier *= Config.SPRINT_MULTIPLIER.get();
        if (!player) multiplier *= Config.MOB_MULTIPLIER.get();
        tryWear(level, ground, multiplier, entity.getRandom());
    }

    private static boolean canWear(ServerLevel level, LivingEntity entity, BlockPos ground) {
        if (entity.isSpectator()) return false;
        if (Config.SNEAKING_PREVENTS_WEAR.get() && entity.isShiftKeyDown()) return false;
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(WornGround.SOFT_SOLES)) return false;
        if (!Config.WEAR_ON_CONTRAPTIONS.get() && isOnContraption(entity.blockPosition(), ground)) return false;
        return entity instanceof Player || !Config.RESPECT_MOB_GRIEFING.get() || EventHooks.canEntityGrief(level, entity);
    }

    public static boolean isOnContraption(BlockPos entityPosition, BlockPos ground) {
        return entityPosition.distSqr(ground) > MAX_GROUND_DISTANCE_SQUARED;
    }

    /**
     * Rolls the wear rules of the block at {@code pos} once.
     *
     * @return true when the ground or the vegetation on it changed
     */
    public static boolean tryWear(ServerLevel level, BlockPos pos, double multiplier, RandomSource random) {
        if (multiplier <= 0 || !level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        List<WearRule> rules = state.getBlockHolder().getData(WornGround.WEAR);
        if (rules == null) return false;

        BlockPos abovePos = pos.above();
        for (WearRule rule : rules) {
            if (!rule.weather().matches(level, abovePos) || random.nextDouble() >= rule.chance() * multiplier) continue;
            return apply(level, pos, state, rule.result());
        }
        return false;
    }

    private static boolean apply(ServerLevel level, BlockPos pos, BlockState state, Block result) {
        if (state.is(result)) return false;
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);
        BlockState worn = result.defaultBlockState();
        if (!above.isAir()) {
            if (Config.TRAMPLE_VEGETATION.get() && above.is(WornGround.TRAMPLEABLE)) {
                // Flattening the plant is this step's wear; the ground follows on a later step.
                return level.destroyBlock(abovePos, false);
            }
            // Never pull the support out from under rails, tracks, machines, crops, flowers...
            boolean keepsSupport = worn.isFaceSturdy(level, pos, Direction.UP)
                && (!state.is(BlockTags.DIRT) || worn.is(BlockTags.DIRT));
            if (!keepsSupport) return false;
        }
        return level.setBlockAndUpdate(pos, worn);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) LAST_BLOCK.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_BLOCK.clear();
    }
}
