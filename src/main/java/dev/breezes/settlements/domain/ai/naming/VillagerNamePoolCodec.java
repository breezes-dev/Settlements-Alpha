package dev.breezes.settlements.domain.ai.naming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Datapack codec for a villager name pool with a required names array.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VillagerNamePoolCodec {

    public static final Codec<VillagerNamePool> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.listOf().fieldOf("names").forGetter(VillagerNamePool::names)
            ).apply(instance, VillagerNamePool::new));

}
