package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Mutable input for one villager render, reused and updated in place.
 * Values remain unchanged until explicitly replaced. Sample times are measured by the villager's age.
 */
@Getter
public final class VillagerAnimationObservation {

    /**
     * The sample key's whole ticks of the villager's age, kept apart from the partial tick so the key stays exact on a
     * long-lived villager.
     */
    private long ageTicks;

    /**
     * The sample key's fraction of a tick past the whole age ticks, in [0, 1].
     */
    private float partialTicks;

    /**
     * Head yaw relative to the body, in degrees, already negated for an upside-down entity.
     */
    private float netHeadYawDegrees;

    /**
     * Head pitch, in degrees, already negated for an upside-down entity.
     */
    private float headPitchDegrees;

    /**
     * The body's world heading used for the render, including any riding adjustment, in vanilla yaw degrees.
     * Positive angles turn toward the villager's right; whole turns are permitted.
     */
    @Setter
    private float bodyHeadingDegrees;

    /**
     * Whether the model is drawn upside down.
     */
    @Setter
    private boolean upsideDown;

    /**
     * Accumulated limb-swing distance.
     */
    private float limbSwing;

    private float limbSwingAmount;

    /**
     * The synced navigation type.
     */
    private NavigationType navigationType = NavigationType.STROLL;

    /**
     * The synced action archetype.
     */
    private AnimationArchetype motionArchetype = AnimationArchetype.NONE;

    /**
     * The synced action generation, which changes each time an action is triggered.
     */
    private byte motionGeneration;

    /**
     * The held-item context that selects among an archetype's clips.
     */
    private AnimationSelectionContext selectionContext = AnimationSelectionContext.generic();

    /**
     * Whether vanilla reports the villager asleep.
     */
    @Setter
    private boolean sleeping;

    /**
     * Whether vanilla's unhappy counter is running.
     */
    @Setter
    private boolean unhappy;

    /**
     * Whether the rain gate asks for the umbrella, regardless of sleep.
     */
    @Setter
    private boolean umbrellaRequested;

    /**
     * Sets the sample key, clamping partial ticks to [0, 1] and treating NaN as zero.
     */
    public void setSampleKey(long ageTicks, float partialTicks) {
        this.ageTicks = ageTicks;
        // Use the comparison so NaN maps to zero instead of surviving a min/max clamp
        this.partialTicks = partialTicks >= 0.0F ? Math.min(partialTicks, 1.0F) : 0.0F;
    }

    /**
     * Returns the body's world heading in radians, within half a turn either side of zero.
     */
    float bodyHeadingRadians() {
        return Mth.wrapDegrees(this.bodyHeadingDegrees) * Mth.DEG_TO_RAD;
    }

    public void setLook(float netHeadYawDegrees, float headPitchDegrees) {
        this.netHeadYawDegrees = netHeadYawDegrees;
        this.headPitchDegrees = headPitchDegrees;
    }

    public void setLimbSwing(float limbSwing, float limbSwingAmount) {
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
    }

    /**
     * @throws NullPointerException if the navigation type is null
     */
    public void setNavigationType(@Nonnull NavigationType navigationType) {
        // Messages without format arguments keep writes free of varargs arrays on the render path
        this.navigationType = Objects.requireNonNull(navigationType, "Observation navigation type must not be null");
    }

    /**
     * @throws NullPointerException if the archetype is null
     */
    public void setMotion(@Nonnull AnimationArchetype motionArchetype, byte motionGeneration) {
        this.motionArchetype = Objects.requireNonNull(motionArchetype, "Observation motion archetype must not be null");
        this.motionGeneration = motionGeneration;
    }

    /**
     * @throws NullPointerException if the selection context is null
     */
    public void setSelectionContext(@Nonnull AnimationSelectionContext selectionContext) {
        this.selectionContext = Objects.requireNonNull(selectionContext, "Observation selection context must not be null");
    }

}
