package dev.jensh.wornground;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.List;

@Mod(WornGround.MOD_ID)
public final class WornGround {
    public static final String MOD_ID = "worn_ground";

    /** Block data map: what a block wears into. Server-only, so clients do not need the mod. */
    public static final DataMapType<Block, List<WearRule>> WEAR =
        DataMapType.builder(id("wear"), Registries.BLOCK, WearRule.LIST_CODEC).build();

    public static final TagKey<EntityType<?>> PATH_MAKERS = TagKey.create(Registries.ENTITY_TYPE, id("path_makers"));
    public static final TagKey<Block> TRAMPLEABLE = TagKey.create(Registries.BLOCK, id("trampleable"));
    public static final TagKey<Item> SOFT_SOLES = TagKey.create(Registries.ITEM, id("soft_soles"));

    public WornGround(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener((RegisterDataMapTypesEvent event) -> event.register(WEAR));
        NeoForge.EVENT_BUS.register(WearHandler.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
