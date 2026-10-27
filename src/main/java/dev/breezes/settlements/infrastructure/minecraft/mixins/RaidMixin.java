package dev.breezes.settlements.infrastructure.minecraft.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/**
 * Read access to a raid's current selection for its next wave.
 */
@Mixin(Raid.class)
public interface RaidMixin {

    /**
     * The current selection, whose position must be copied before retaining it.
     */
    @Accessor("waveSpawnPos")
    Optional<BlockPos> getRaidWaveSpawnPos();

}
