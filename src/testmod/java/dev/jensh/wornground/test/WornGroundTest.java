package dev.jensh.wornground.test;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("worn_ground_test")
public final class WornGroundTest {
    public WornGroundTest() {
        if (System.getProperty("wornground.lab") != null) NeoForge.EVENT_BUS.register(new Lab());
        if (System.getProperty("wornground.showcase") != null && FMLEnvironment.dist.isClient()) Showcase.register();
    }
}
