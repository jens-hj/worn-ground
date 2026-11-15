package dev.jensh.wornground;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** One possible outcome of stepping on a block. */
public record WearRule(Block result, float chance, Weather weather) {
    public static final Codec<WearRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("result").forGetter(WearRule::result),
        Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(WearRule::chance),
        Weather.CODEC.optionalFieldOf("weather", Weather.ANY).forGetter(WearRule::weather)
    ).apply(instance, WearRule::new));

    /** Either a single rule or a list of rules; the first rule that rolls successfully wins. */
    public static final Codec<List<WearRule>> LIST_CODEC = Codec.withAlternative(CODEC.listOf(), CODEC, List::of);

    public enum Weather implements StringRepresentable {
        ANY, RAIN, CLEAR;

        public static final Codec<Weather> CODEC = StringRepresentable.fromEnum(Weather::values);

        public boolean matches(ServerLevel level, BlockPos surface) {
            return this == ANY || (this == RAIN) == level.isRainingAt(surface);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
