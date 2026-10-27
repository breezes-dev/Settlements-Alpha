package dev.breezes.settlements.application.ai.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Publishes a raid's next-wave marker to an individual player.
 */
public interface RaidWaveMarkPublisher {

    /**
     * Shows a marker belonging to the supplied dimension, or clears it when the position is null.
     * A shown marker replaces the raid's previous one and expires on its own unless cleared first.
     */
    void send(@Nonnull ServerPlayer recipient, @Nonnull ResourceKey<Level> dimension, int raidId, @Nullable BlockPos position);

}
