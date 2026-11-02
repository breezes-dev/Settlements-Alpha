package dev.breezes.settlements.infrastructure.network.features.ui.toss.handler;

import dev.breezes.settlements.infrastructure.network.core.ClientSidePacketHandler;
import dev.breezes.settlements.infrastructure.network.features.ui.toss.packet.ClientBoundItemTossPacket;
import dev.breezes.settlements.infrastructure.rendering.toss.ItemTossFlightStore;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nonnull;

/**
 * Starts item tosses in this client's current level.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class ClientBoundItemTossPacketHandler implements ClientSidePacketHandler<ClientBoundItemTossPacket> {

    private final ItemTossFlightStore flights;

    @Override
    public void runOnClient(@Nonnull IPayloadContext context, @Nonnull ClientBoundItemTossPacket packet) {
        this.flights.start(packet.giverEntityId(), packet.receiverEntityId(), packet.stack(), packet.flightTicks());
    }

}
