package dev.breezes.settlements.domain.animation;

import lombok.Getter;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable, ordered set of channels for one model. Each channel's position is its dense index within the rig.
 */
public final class AnimationRig {

    /**
     * Names this rig in diagnostics.
     */
    @Getter
    private final String name;

    private final List<AnimationTarget<?>> channels;
    private final Map<AnimationTarget<?>, Integer> indexByChannel;
    private final AnimationChannelKind[] kinds;
    private final boolean[] slotChannels;

    /**
     * The mask of all channels whose bone is a model part.
     */
    @Getter
    private final AnimationMask fullBody;

    private AnimationRig(@Nonnull String name, @Nonnull List<AnimationTarget<?>> channels) {
        Validate.notBlank(name, "Rig name must not be blank");
        Validate.notEmpty(channels, "Rig %s must have at least one channel", name);

        int channelCount = channels.size();
        Map<AnimationTarget<?>, Integer> indexByChannel = HashMap.newHashMap(channelCount);
        AnimationChannelKind[] kinds = new AnimationChannelKind[channelCount];
        boolean[] slotChannels = new boolean[channelCount];
        BitSet poseChannels = new BitSet(channelCount);
        for (int index = 0; index < channelCount; index++) {
            AnimationTarget<?> channel = channels.get(index);
            Integer earlierIndex = indexByChannel.putIfAbsent(channel, index);
            Validate.isTrue(earlierIndex == null, "Rig %s lists channel %s more than once", name, channel.getId());
            kinds[index] = channel.getKind();
            slotChannels[index] = channel.getBone() instanceof AnimationBone.Slot;
            if (!slotChannels[index]) {
                poseChannels.set(index);
            }
        }

        this.name = name;
        this.channels = channels;
        this.indexByChannel = Map.copyOf(indexByChannel);
        this.kinds = kinds;
        this.slotChannels = slotChannels;
        // The mask factory receives this rig before its constructor returns, so the fields above must already be set
        this.fullBody = AnimationMask.of(this, poseChannels);
    }

    /**
     * Builds a rig whose channels take their indexes from their order in the list.
     *
     * @throws IllegalArgumentException if the name is blank, the list is empty, or it lists a channel twice
     */
    public static AnimationRig of(@Nonnull String name, @Nonnull List<? extends AnimationTarget<?>> channels) {
        return new AnimationRig(name, List.copyOf(channels));
    }

    public int getChannelCount() {
        return this.channels.size();
    }

    public boolean contains(@Nonnull AnimationTarget<?> channel) {
        return this.indexByChannel.containsKey(channel);
    }

    /**
     * @throws IllegalArgumentException if the target is not a channel of this rig
     */
    public int indexOf(@Nonnull AnimationTarget<?> channel) {
        Integer index = this.indexByChannel.get(channel);
        // Keep successful channel lookups free of varargs allocation
        if (index == null) {
            throw new IllegalArgumentException("Rig " + this.name + " has no channel " + channel.getId());
        }
        return index;
    }

    public AnimationTarget<?> channel(int index) {
        return this.channels.get(index);
    }

    public AnimationChannelKind kind(int index) {
        return this.kinds[index];
    }

    /**
     * Whether the channel at this index targets an attachment slot.
     */
    public boolean isSlotChannel(int index) {
        return this.slotChannels[index];
    }

    /**
     * Builds the mask of exactly these channels.
     *
     * @throws IllegalArgumentException if the collection is empty or holds a target that is not a channel of this rig
     */
    public AnimationMask maskOf(@Nonnull Collection<? extends AnimationTarget<?>> members) {
        Validate.notEmpty(members, "A mask on rig %s must name at least one channel", this.name);
        BitSet bits = new BitSet(this.getChannelCount());
        for (AnimationTarget<?> member : members) {
            bits.set(this.indexOf(member));
        }
        return AnimationMask.of(this, bits);
    }

    /**
     * Returns a mask on this rig containing the input mask's channels, matched by target identity rather than index.
     * Returns the input mask unchanged if it already belongs to this rig.
     *
     * @throws IllegalArgumentException if the mask holds a channel this rig lacks
     */
    public AnimationMask resolve(@Nonnull AnimationMask mask) {
        if (mask.getRig() == this) {
            return mask;
        }
        AnimationRig source = mask.getRig();
        BitSet bits = new BitSet(this.getChannelCount());
        for (int member = 0; member < mask.size(); member++) {
            AnimationTarget<?> channel = source.channel(mask.channelAt(member));
            Integer index = this.indexByChannel.get(channel);
            Validate.isTrue(index != null, "Rig %s has no channel %s, which a mask on rig %s holds",
                    this.name, channel.getId(), source.getName());
            bits.set(index);
        }
        return AnimationMask.of(this, bits);
    }

}
