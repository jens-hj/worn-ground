package dev.jensh.wornground.test;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;

/** Sable structures for {@link Perf}; only loaded when Sable is present. */
final class PerfShips {
    private PerfShips() {}

    static String build(ServerLevel level, int surface) {
        int y = surface + 2;
        int count = 0;
        // Four small vehicles, two barges, one airship hull (keel + deck, mostly hollow), one oversized plate.
        for (int i = 0; i < 4; i++) count += hull(level, 60 + i * 8, y, 4, 4, 4, 1);
        for (int i = 0; i < 2; i++) count += hull(level, 60 + i * 20, y, 14, 16, 16, 3);
        count += hull(level, 60, y, 36, 60, 30, 14);
        count += hull(level, 130, y, 4, 70, 70, 1);
        return "8 sub-levels, " + count + " blocks";
    }

    /** A deck of {@code width x length} at the top, standing on a keel strip along the middle. */
    private static int hull(ServerLevel level, int x0, int y0, int z0, int width, int length, int height) {
        var blocks = new ArrayList<BlockPos>();
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                blocks.add(new BlockPos(x0 + dx, y0 + height - 1, z0 + dz));
                boolean keel = height > 1 && Math.abs(dz - length / 2) <= 1;
                for (int dy = 0; keel && dy < height - 1; dy++) blocks.add(new BlockPos(x0 + dx, y0 + dy, z0 + dz));
            }
        }
        for (BlockPos pos : blocks) level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        SubLevelAssemblyHelper.assembleBlocks(level, new BlockPos(x0, y0, z0), blocks,
            new BoundingBox3i(x0, y0, z0, x0 + width - 1, y0 + height - 1, z0 + length - 1));
        return blocks.size();
    }
}
