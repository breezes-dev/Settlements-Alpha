package dev.breezes.settlements.infrastructure.network.features.raid;

import dev.breezes.settlements.application.ai.raid.RaidWaveMarkPublisher;
import dev.breezes.settlements.infrastructure.network.features.raid.packet.ClientBoundRaidWaveBeamPacket;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sends next-wave marker updates through the player's client connection.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RaidWaveMarkPacketSender implements RaidWaveMarkPublisher {

    @Override
    public void send(@Nonnull ServerPlayer recipient,
                     @Nonnull ResourceKey<Level> dimension,
                     int raidId,
                     @Nullable BlockPos position) {
        PacketDistributor.sendToPlayer(recipient, new ClientBoundRaidWaveBeamPacket(dimension, raidId, position));
    }

}
