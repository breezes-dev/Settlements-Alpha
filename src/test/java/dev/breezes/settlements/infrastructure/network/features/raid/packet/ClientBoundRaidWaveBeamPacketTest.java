package dev.breezes.settlements.infrastructure.network.features.raid.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClientBoundRaidWaveBeamPacketTest {

    @Test
    void codec_roundTripsMarkedPositionInCustomDimension() {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("test", "raid_level"));
        ClientBoundRaidWaveBeamPacket input = new ClientBoundRaidWaveBeamPacket(dimension, 42, new BlockPos(-128, 90, 231));

        ClientBoundRaidWaveBeamPacket decoded = roundTrip(input);

        Assertions.assertEquals(input, decoded);
    }

    @Test
    void codec_roundTripsClearInItsDimension() {
        ClientBoundRaidWaveBeamPacket input = new ClientBoundRaidWaveBeamPacket(Level.NETHER, 7, null);

        ClientBoundRaidWaveBeamPacket decoded = roundTrip(input);

        Assertions.assertEquals(input, decoded);
        Assertions.assertNull(decoded.position());
    }

    @Test
    void construction_retainsSnapshotWhenSourcePositionMoves() {
        BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos(12, 64, -8);
        ClientBoundRaidWaveBeamPacket packet = new ClientBoundRaidWaveBeamPacket(Level.OVERWORLD, 3, source);

        source.set(30, 80, 40);

        Assertions.assertEquals(new BlockPos(12, 64, -8), packet.position());
    }

    private static ClientBoundRaidWaveBeamPacket roundTrip(ClientBoundRaidWaveBeamPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientBoundRaidWaveBeamPacket.CODEC.encode(buffer, packet);
            return ClientBoundRaidWaveBeamPacket.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

}
