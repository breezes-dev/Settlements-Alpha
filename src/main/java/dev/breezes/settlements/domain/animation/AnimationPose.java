package dev.breezes.settlements.domain.animation;

import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Mutable values for every channel of one rig.
 * Axis indexes 0, 1, and 2 address X, Y, and Z.
 * Values use the rest-relative convention of each channel's {@link AnimationChannelKind}.
 */
public final class AnimationPose {

    /**
     * Components per channel.
     */
    public static final int COMPONENTS = 3;

    @Getter
    private final AnimationRig rig;

    private final float[] values;

    /**
     * Creates a pose at rest.
     */
    public AnimationPose(@Nonnull AnimationRig rig) {
        this.rig = rig;
        this.values = new float[rig.getChannelCount() * COMPONENTS];
        this.resetToRest();
    }

    public float get(int channelIndex, int axis) {
        return this.values[offset(channelIndex, axis)];
    }

    public void set(int channelIndex, int axis, float value) {
        this.values[offset(channelIndex, axis)] = value;
    }

    public void set(int channelIndex, float x, float y, float z) {
        int base = channelIndex * COMPONENTS;
        this.values[base] = x;
        this.values[base + 1] = y;
        this.values[base + 2] = z;
    }

    public void resetToRest() {
        for (int channel = 0; channel < this.rig.getChannelCount(); channel++) {
            float rest = this.rig.kind(channel).getRest();
            int base = channel * COMPONENTS;
            this.values[base] = rest;
            this.values[base + 1] = rest;
            this.values[base + 2] = rest;
        }
    }

    /**
     * The backing array, laid out as COMPONENTS consecutive values per channel in index order. Writes through it
     * change this pose.
     */
    float[] values() {
        return this.values;
    }

    private static int offset(int channelIndex, int axis) {
        // An axis outside the channel would otherwise silently address a neighboring channel's value
        return channelIndex * COMPONENTS + Objects.checkIndex(axis, COMPONENTS);
    }

}
