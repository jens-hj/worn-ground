package dev.jensh.wornground.test;

import dev.jensh.wornground.Config;
import dev.jensh.wornground.WearHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worn_ground")
@PrefixGameTestTemplate(false)
public final class WearTests {
    private static final BlockPos GROUND = new BlockPos(3, 1, 3);
    private static final double ALWAYS = 1000.0;

    private static boolean step(GameTestHelper test, double multiplier) {
        return WearHandler.tryWear(test.getLevel(), test.absolutePos(GROUND), multiplier, test.getLevel().getRandom());
    }

    private static void stepExpecting(GameTestHelper test, Block expected) {
        test.assertTrue(step(test, ALWAYS), "Step should wear the ground into " + expected);
        test.assertBlockPresent(expected, GROUND);
    }

    @GameTest(template = "empty")
    public static void grassWearsDownToPath(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.GRASS_BLOCK);
        stepExpecting(test, Blocks.DIRT);
        stepExpecting(test, Blocks.COARSE_DIRT);
        stepExpecting(test, Blocks.DIRT_PATH);
        test.assertFalse(step(test, ALWAYS), "A finished path has nothing left to wear");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void podzolAndMyceliumWearToDirt(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.PODZOL);
        stepExpecting(test, Blocks.DIRT);
        test.setBlock(GROUND, Blocks.MYCELIUM);
        stepExpecting(test, Blocks.DIRT);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroChanceAndUnmappedBlocksStay(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.GRASS_BLOCK);
        test.assertFalse(step(test, 0.0), "Zero multiplier must never wear");
        test.assertBlockPresent(Blocks.GRASS_BLOCK, GROUND);
        test.setBlock(GROUND, Blocks.STONE);
        test.assertFalse(step(test, ALWAYS), "Blocks without a wear rule stay");
        test.assertBlockPresent(Blocks.STONE, GROUND);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void vegetationIsTrampledBeforeGround(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.GRASS_BLOCK);
        test.setBlock(GROUND.above(), Blocks.SHORT_GRASS);
        test.assertTrue(step(test, ALWAYS), "First step flattens the plant");
        test.assertBlockPresent(Blocks.AIR, GROUND.above());
        test.assertBlockPresent(Blocks.GRASS_BLOCK, GROUND);
        stepExpecting(test, Blocks.DIRT);
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void flowersSurviveAndBlockPathStage(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.GRASS_BLOCK);
        test.setBlock(GROUND.above(), Blocks.POPPY);
        stepExpecting(test, Blocks.DIRT);
        stepExpecting(test, Blocks.COARSE_DIRT);
        test.assertFalse(step(test, ALWAYS), "A path block would uproot the flower");
        test.assertBlockPresent(Blocks.COARSE_DIRT, GROUND);
        test.assertBlockPresent(Blocks.POPPY, GROUND.above());
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void railsKeepTheirSupport(GameTestHelper test) {
        test.setBlock(GROUND, Blocks.COARSE_DIRT);
        test.setBlock(GROUND.above(), Blocks.RAIL);
        test.assertFalse(step(test, ALWAYS), "Ground under a rail must stay a full block");
        test.assertBlockPresent(Blocks.COARSE_DIRT, GROUND);
        test.assertBlockPresent(Blocks.RAIL, GROUND.above());
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void contraptionPlotPositionsAreDetected(GameTestHelper test) {
        BlockPos feet = new BlockPos(100, 64, 100);
        test.assertFalse(WearHandler.isOnContraption(feet, feet.below()), "Block below the feet is real ground");
        test.assertFalse(WearHandler.isOnContraption(feet, feet.below(2)), "Fences report the block further down");
        test.assertTrue(WearHandler.isOnContraption(feet, new BlockPos(20_000_000, 64, 20_000_000)),
            "A remapped plot position is a contraption");
        test.succeed();
    }

    /** End to end: a real mob walking across grass leaves a trail. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void walkingCowWearsATrail(GameTestHelper test) {
        Config.CHANCE_MULTIPLIER.set(ALWAYS);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) test.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        }
        var cow = test.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        test.runAfterDelay(20, () -> test.walkTo(cow, new BlockPos(6, 2, 6), 1.0F));
        test.succeedWhen(() -> {
            int worn = 0;
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    if (!test.getBlockState(new BlockPos(x, 1, z)).is(Blocks.GRASS_BLOCK)) worn++;
                }
            }
            test.assertTrue(worn >= 3, "Expected a worn trail, got " + worn + " worn blocks");
            test.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(1, 1, 1));
        });
    }
}
