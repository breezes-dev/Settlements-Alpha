package dev.breezes.settlements.infrastructure.network.features.ui.toss.packet;

import dev.breezes.settlements.infrastructure.network.core.ClientBoundPacket;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;

/**
 * Client-bound request for a visual item toss between two villagers.
 *
 * @param stack       a non-empty stack copied on construction; callers must not modify the stored copy
 * @param flightTicks duration from launch to catch in client ticks; must be at least 1
 */
public record ClientBoundItemTossPacket(int giverEntityId, int receiverEntityId, ItemStack stack, int flightTicks)
        implements ClientBoundPacket {

    public static final Type<ClientBoundItemTossPacket> ID = new Type<>(ResourceLocationUtil.mod("packet_item_toss_clientbound"));
    public static final StreamCodec<FriendlyByteBuf, ClientBoundItemTossPacket> CODEC =
            CustomPacketPayload.codec(ClientBoundItemTossPacket::write, ClientBoundItemTossPacket::decode);

    /**
     * @throws IllegalArgumentException if stack is empty or flightTicks is less than 1
     */
    public ClientBoundItemTossPacket {
        Validate.isTrue(flightTicks >= 1, "flightTicks must be at least 1: %s", flightTicks);
        Validate.isTrue(!stack.isEmpty(), "stack must not be empty");
        stack = stack.copy();
    }

    private static ClientBoundItemTossPacket decode(@Nonnull FriendlyByteBuf buffer) {
        int giverEntityId = buffer.readVarInt();
        int receiverEntityId = buffer.readVarInt();
        // Play payloads always arrive in a registry-aware buffer, which an item's components need to decode
        ItemStack stack = ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer);
        int flightTicks = buffer.readVarInt();
        return new ClientBoundItemTossPacket(giverEntityId, receiverEntityId, stack, flightTicks);
    }

    private void write(@Nonnull FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.giverEntityId);
        buffer.writeVarInt(this.receiverEntityId);
        ItemStack.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, this.stack);
        buffer.writeVarInt(this.flightTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

}
