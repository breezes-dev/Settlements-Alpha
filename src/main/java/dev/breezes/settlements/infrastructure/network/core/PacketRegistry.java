package dev.breezes.settlements.infrastructure.network.core;

import dev.breezes.settlements.di.SettlementsDagger;
import dev.breezes.settlements.infrastructure.network.features.arrival.packet.ClientBoundSettlementArrivalPacket;
import dev.breezes.settlements.infrastructure.network.features.ballista.packet.ServerBoundBallistaAimPacket;
import dev.breezes.settlements.infrastructure.network.features.debug.packet.ClientBoundSettlementDebugPacket;
import dev.breezes.settlements.infrastructure.network.features.farming.packet.ClientBoundCultivationSeedSetPacket;
import dev.breezes.settlements.infrastructure.network.features.flare.packet.ClientBoundFlarePacket;
import dev.breezes.settlements.infrastructure.network.features.raid.packet.ClientBoundRaidWaveBeamPacket;
import dev.breezes.settlements.infrastructure.network.features.schedule.packet.ClientBoundVillageDayTypePacket;
import dev.breezes.settlements.infrastructure.network.features.ui.bubble.packet.ClientBoundBubbleSnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.dayplan.packet.ClientBoundDayPlanSnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.stats.packet.ClientBoundVillagerDemandSnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.stats.packet.ClientBoundVillagerInventorySnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.stats.packet.ClientBoundVillagerStatsSnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.stats.packet.ClientBoundVillagerTradeCatalogSnapshotPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ClientBoundHeartbeatAckUiPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ClientBoundOpenUiPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ClientBoundUiUnavailablePacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ServerBoundCloseUiPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ServerBoundHeartbeatUiPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.sync.packet.ServerBoundOpenUiPacket;
import dev.breezes.settlements.infrastructure.network.features.ui.toss.packet.ClientBoundItemTossPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.Nonnull;

public class PacketRegistry {

    /**
     * Wire-format compatibility version for Settlements payloads, synced attachments, and synced entity data.
     * Shared payload channels require matching values on the client and server.
     */
    private static final String NETWORK_VERSION = "6";

    public static void bindPacketHandlers(@Nonnull RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();

        registerClient(registrar, ClientBoundSettlementDebugPacket.ID, ClientBoundSettlementDebugPacket.CODEC);
        registerClient(registrar, ClientBoundBubbleSnapshotPacket.ID, ClientBoundBubbleSnapshotPacket.CODEC);
        registerClient(registrar, ClientBoundCultivationSeedSetPacket.ID, ClientBoundCultivationSeedSetPacket.CODEC);
        registerClient(registrar, ClientBoundVillageDayTypePacket.ID, ClientBoundVillageDayTypePacket.CODEC);
        registerClient(registrar, ClientBoundFlarePacket.ID, ClientBoundFlarePacket.CODEC);
        registerClient(registrar, ClientBoundRaidWaveBeamPacket.ID, ClientBoundRaidWaveBeamPacket.CODEC);
        registerClient(registrar, ClientBoundSettlementArrivalPacket.ID, ClientBoundSettlementArrivalPacket.CODEC);
        registerClient(registrar, ClientBoundItemTossPacket.ID, ClientBoundItemTossPacket.CODEC);

        registerServer(registrar, ServerBoundBallistaAimPacket.ID, ServerBoundBallistaAimPacket.CODEC);

        registerServer(registrar, ServerBoundOpenUiPacket.ID, ServerBoundOpenUiPacket.CODEC);
        registerServer(registrar, ServerBoundCloseUiPacket.ID, ServerBoundCloseUiPacket.CODEC);
        registerServer(registrar, ServerBoundHeartbeatUiPacket.ID, ServerBoundHeartbeatUiPacket.CODEC);

        registerClient(registrar, ClientBoundOpenUiPacket.ID, ClientBoundOpenUiPacket.CODEC);
        registerClient(registrar, ClientBoundHeartbeatAckUiPacket.ID, ClientBoundHeartbeatAckUiPacket.CODEC);
        registerClient(registrar, ClientBoundUiUnavailablePacket.ID, ClientBoundUiUnavailablePacket.CODEC);
        registerClient(registrar, ClientBoundDayPlanSnapshotPacket.ID, ClientBoundDayPlanSnapshotPacket.CODEC);

        registerClient(registrar, ClientBoundVillagerStatsSnapshotPacket.ID, ClientBoundVillagerStatsSnapshotPacket.CODEC);
        registerClient(registrar, ClientBoundVillagerInventorySnapshotPacket.ID, ClientBoundVillagerInventorySnapshotPacket.CODEC);
        registerClient(registrar, ClientBoundVillagerTradeCatalogSnapshotPacket.ID, ClientBoundVillagerTradeCatalogSnapshotPacket.CODEC);
        registerClient(registrar, ClientBoundVillagerDemandSnapshotPacket.ID, ClientBoundVillagerDemandSnapshotPacket.CODEC);
    }

    private static <T extends ClientBoundPacket> void registerClient(@Nonnull PayloadRegistrar registrar,
                                                                     @Nonnull CustomPacketPayload.Type<T> packetId,
                                                                     @Nonnull StreamCodec<FriendlyByteBuf, T> codec) {
        // The client component may not exist at registration, so resolve the receiver when a packet arrives
        registrar.playToClient(packetId, codec,
                (packet, context) -> SettlementsDagger.client().clientSidePacketReceiver().onReceivePacket(packet, context));
    }

    private static <T extends ServerBoundPacket> void registerServer(@Nonnull PayloadRegistrar registrar,
                                                                     @Nonnull CustomPacketPayload.Type<T> packetId,
                                                                     @Nonnull StreamCodec<FriendlyByteBuf, T> codec) {
        // The server component belongs to a world session, so resolve the current receiver when a packet arrives
        registrar.playToServer(packetId, codec,
                (packet, context) -> SettlementsDagger.serverOrThrow().serverSidePacketReceiver().onReceivePacket(packet, context));
    }

}
