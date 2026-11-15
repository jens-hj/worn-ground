package dev.jensh.wornground.test;

import dev.jensh.wornground.Config;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;

import java.util.List;

/** Only touched when Sable is installed. */
final class SableChecks {
    private SableChecks() {}

    /** A plain block structure dragged across grass (a sled, a landed hull) wears what it touches. */
    static void sliding(GameTestHelper test) {
        Config.CHANCE_MULTIPLIER.set(1000.0);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) test.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        }
        BlockPos start = test.absolutePos(new BlockPos(1, 2, 3));
        test.getLevel().setBlockAndUpdate(start, Blocks.STONE.defaultBlockState());
        var sled = SubLevelAssemblyHelper.assembleBlocks(test.getLevel(), start, List.of(start),
            new BoundingBox3i(start, start));
        var handle = RigidBodyHandle.of(sled);
        test.onEachTick(() -> {
            if (sled.isRemoved() || !handle.isValid()) return;
            var pose = sled.logicalPose();
            if (pose.position().x() < start.getX() + 5) {
                handle.teleport(new Vector3d(pose.position()).add(0.25, 0, 0), pose.orientation());
            }
        });
        test.succeedWhen(() -> {
            int worn = 0;
            for (int x = 2; x <= 6; x++) {
                if (!test.getBlockState(new BlockPos(x, 1, 3)).is(Blocks.GRASS_BLOCK)) worn++;
            }
            test.assertTrue(worn >= 3, "Expected the sled to wear its trail, got " + worn + " worn blocks");
            test.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(1, 1, 6));
        });
    }
}
