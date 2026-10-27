package dev.breezes.settlements.infrastructure.network.features.raid.handler;

import dev.breezes.settlements.infrastructure.network.core.ClientSidePacketHandler;
import dev.breezes.settlements.infrastructure.network.features.raid.packet.ClientBoundRaidWaveBeamPacket;
import dev.breezes.settlements.infrastructure.rendering.raid.RaidWaveBeamStore;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nonnull;

/**
 * Applies next-wave marker updates to this client's current level.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class ClientBoundRaidWaveBeamPacketHandler implements ClientSidePacketHandler<ClientBoundRaidWaveBeamPacket> {

    private final RaidWaveBeamStore beams;

    @Override
    public void runOnClient(@Nonnull IPayloadContext context, @Nonnull ClientBoundRaidWaveBeamPacket packet) {
        this.beams.update(packet.dimension(), packet.raidId(), packet.position());
    }

}
