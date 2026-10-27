package dev.breezes.settlements.infrastructure.network.features.raid.packet;

import dev.breezes.settlements.infrastructure.network.core.ClientBoundPacket;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Updates a raid's next-wave marker in its dimension. A null position clears the marker.
 */
public record ClientBoundRaidWaveBeamPacket(ResourceKey<Level> dimension, int raidId, @Nullable BlockPos position)
        implements ClientBoundPacket {

    public static final Type<ClientBoundRaidWaveBeamPacket> ID = new Type<>(ResourceLocationUtil.mod("packet_raid_wave_beam_clientbound"));
    public static final StreamCodec<FriendlyByteBuf, ClientBoundRaidWaveBeamPacket> CODEC =
            CustomPacketPayload.codec(ClientBoundRaidWaveBeamPacket::write, ClientBoundRaidWaveBeamPacket::decode);

    /**
     * Retains an immutable snapshot of a present position.
     */
    public ClientBoundRaidWaveBeamPacket {
        if (position != null) {
            position = position.immutable();
        }
    }

    private static ClientBoundRaidWaveBeamPacket decode(@Nonnull FriendlyByteBuf buffer) {
        ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
        int raidId = buffer.readVarInt();
        BlockPos position = buffer.readNullable(input -> input.readBlockPos());
        return new ClientBoundRaidWaveBeamPacket(dimension, raidId, position);
    }

    private void write(@Nonnull FriendlyByteBuf buffer) {
        buffer.writeResourceKey(this.dimension);
        buffer.writeVarInt(this.raidId);
        buffer.writeNullable(this.position, (output, value) -> output.writeBlockPos(value));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

}
