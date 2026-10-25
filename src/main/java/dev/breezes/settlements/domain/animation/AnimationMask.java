package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.BitSet;

/**
 * An immutable set of channels on one rig, addressed by the rig's channel indexes and valid only on that rig.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnimationMask {

    @Getter
    private final AnimationRig rig;

    private final long[] words;

    /**
     * Member channel indexes in ascending order.
     */
    private final int[] members;


    static AnimationMask of(@Nonnull AnimationRig rig, @Nonnull BitSet bits) {
        Validate.isTrue(bits.length() <= rig.getChannelCount(),
                "A mask on rig %s names channel %s, past its last channel", rig.getName(), bits.length() - 1);
        // Store member indexes once so traversal needs no temporary arrays or iterators
        return new AnimationMask(rig, bits.toLongArray(), bits.stream().toArray());
    }

    public boolean contains(int channelIndex) {
        int word = channelIndex >>> 6;
        return channelIndex >= 0 && word < this.words.length && (this.words[word] & (1L << channelIndex)) != 0;
    }

    public int size() {
        return this.members.length;
    }

    /**
     * The channel index of the member at this position, counting members in ascending index order.
     */
    public int channelAt(int position) {
        return this.members[position];
    }

    /**
     * Returns a mask containing every channel present in either mask.
     *
     * @throws IllegalArgumentException if the masks belong to different rigs, even rigs that share their channels
     * @see AnimationRig#resolve
     */
    public AnimationMask union(@Nonnull AnimationMask other) {
        Validate.isTrue(other.rig == this.rig, "Cannot unite masks on rigs %s and %s",
                this.rig.getName(), other.rig.getName());
        BitSet bits = BitSet.valueOf(this.words);
        bits.or(BitSet.valueOf(other.words));
        return of(this.rig, bits);
    }

}
