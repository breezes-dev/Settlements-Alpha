package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmPose;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A reusable contribution from one animation source to one slot, sized for a single rig.
 * <p>
 * A channel is owned exactly when its coverage is above zero, regardless if it is keyed.
 */
public final class AnimationContribution {

    @Getter
    private final AnimationRig rig;

    private final AnimationPose values;
    private final boolean[] keyed;
    private final float[] coverage;
    private final float[] suppression = new float[SuppressibleComponent.COUNT];

    /**
     * The pose this contribution requires for the left arm, or null when it leaves the arm unclaimed.
     */
    @Getter
    @Nullable
    private ArmPose leftArmClaim;

    /**
     * The pose this contribution requires for the right arm, or null when it leaves the arm unclaimed.
     */
    @Getter
    @Nullable
    private ArmPose rightArmClaim;

    /**
     * Creates an empty contribution: nothing keyed, owned, suppressed, or claimed.
     */
    public AnimationContribution(@Nonnull AnimationRig rig) {
        this.rig = rig;
        this.values = new AnimationPose(rig);
        this.keyed = new boolean[rig.getChannelCount()];
        this.coverage = new float[rig.getChannelCount()];
    }

    /**
     * Empties this contribution: every value back to rest, and nothing keyed, owned, suppressed, or claimed.
     */
    public void clear() {
        this.values.resetToRest();
        Arrays.fill(this.keyed, false);
        Arrays.fill(this.coverage, 0.0F);
        Arrays.fill(this.suppression, 0.0F);
        this.leftArmClaim = null;
        this.rightArmClaim = null;
    }

    /**
     * Keys the channel at this index with a rest-relative value.
     */
    public void key(int channelIndex, float x, float y, float z) {
        this.values.set(channelIndex, x, y, z);
        this.keyed[channelIndex] = true;
    }

    public boolean isKeyed(int channelIndex) {
        return this.keyed[channelIndex];
    }

    /**
     * One component of the value keyed on the channel at this index; rest where the channel is not keyed.
     */
    public float value(int channelIndex, int axis) {
        return this.values.get(channelIndex, axis);
    }

    /**
     * Sets ownership coverage for every channel in the mask.
     *
     * @param coverage how far an owned channel moves at full weight, clamped to [0, 1], where zero releases ownership
     * @throws IllegalArgumentException if the mask is on another rig, even one that shares its channels
     * @see AnimationRig#resolve
     */
    public void own(@Nonnull AnimationMask mask, float coverage) {
        // Keep the successful rig check free of varargs allocation
        if (mask.getRig() != this.rig) {
            throw new IllegalArgumentException("Mask on rig " + mask.getRig().getName()
                    + " does not apply to a contribution on rig " + this.rig.getName());
        }
        float clamped = Math.clamp(coverage, 0.0F, 1.0F);
        for (int member = 0; member < mask.size(); member++) {
            this.coverage[mask.channelAt(member)] = clamped;
        }
    }

    /**
     * How far this contribution moves the channel at this index at full weight, or zero where it does not own it.
     */
    public float coverage(int channelIndex) {
        return this.coverage[channelIndex];
    }

    /**
     * Sets how strongly this contribution suppresses the component.
     *
     * @param strength clamped to [0, 1], where zero leaves the component untouched
     */
    public void suppress(@Nonnull SuppressibleComponent component, float strength) {
        this.suppression[component.ordinal()] = Math.clamp(strength, 0.0F, 1.0F);
    }

    public float suppression(@Nonnull SuppressibleComponent component) {
        return this.suppression[component.ordinal()];
    }

    /**
     * Sets the pose this contribution requires for each arm, null leaving an arm unclaimed.
     */
    public void claimArms(@Nullable ArmPose left, @Nullable ArmPose right) {
        this.leftArmClaim = left;
        this.rightArmClaim = right;
    }

    // Share storage to avoid allocating copies during evaluation

    AnimationPose values() {
        return this.values;
    }

    boolean[] keyedFlags() {
        return this.keyed;
    }

    float[] coverages() {
        return this.coverage;
    }

    float[] suppressions() {
        return this.suppression;
    }

}
