package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;

/**
 * Composes animation slots into a pose and component suppression state in place.
 * <p>
 * Slot weights are clamped to [0, 1].
 * Nonpositive and NaN weights leave both outputs unchanged without validating rigs.
 * Successful calls allocate no objects.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnimationCompositor {

    /**
     * Applies one contribution, blending from the input pose to its full-weight result by the slot weight.
     * <p>
     * At full weight, owned channels move towards their keyed value, or towards rest if unkeyed, by their coverage.
     * Unowned keyed channels add rotation and translation offsets or multiply scale factors. Channels that are
     * neither owned nor keyed retain the input pose.
     * <p>
     * Each component's suppression strength is scaled by the slot weight. Ownership does not imply suppression.
     *
     * @throws IllegalArgumentException if the weight is positive and the contribution uses a different rig instance
     */
    public static void applyContribution(@Nonnull AnimationPose pose,
                                         @Nonnull ComponentSuppression suppression,
                                         float weight,
                                         @Nonnull AnimationContribution contribution) {
        float slotWeight = Math.clamp(weight, 0.0F, 1.0F);
        // A positive-weight test rejects NaN before it can propagate into the outputs
        if (!(slotWeight > 0.0F)) {
            return;
        }
        requireRig(pose, contribution);

        AnimationRig rig = pose.getRig();
        float[] values = pose.values();
        for (int channel = 0; channel < rig.getChannelCount(); channel++) {
            AnimationChannelKind kind = rig.kind(channel);
            int base = channel * AnimationPose.COMPONENTS;
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                float lower = values[base + axis];
                values[base + axis] = lower + slotWeight * delta(contribution, channel, base + axis, kind, lower);
            }
        }

        float[] strengths = contribution.suppressions();
        for (int component = 0; component < SuppressibleComponent.COUNT; component++) {
            suppression.suppress(component, slotWeight * strengths[component]);
        }
    }

    /**
     * Blends alternatives evaluated against the same input pose, then blends that result into the pose by the slot weight.
     * Each alternative uses the single-contribution {@link #applyContribution} contract at full weight.
     * <p>
     * When every alternative fully covers a channel and the slot has full weight, the crossfade excludes the
     * lower pose, subject to floating-point rounding. Component suppression uses the slot weight times the
     * share-weighted suppression strengths.
     * <p>
     * All counted alternatives must use the pose's rig, including those with zero share. A rig mismatch leaves both
     * outputs unchanged.
     *
     * @param alternatives     the slot's alternatives, in any order; only the first alternativeCount are read
     * @param shares           finite, nonnegative shares paired with alternatives; the shares read must sum to one
     *                         within rounding error. Shares are used without validation or normalization.
     * @param alternativeCount number of entries to read from each array, positive and no greater than either length
     * @throws IllegalArgumentException if the weight is positive and any counted alternative uses a different rig
     *                                  instance
     */
    public static void applyCrossfade(@Nonnull AnimationPose pose,
                                      @Nonnull ComponentSuppression suppression,
                                      float weight,
                                      @Nonnull AnimationContribution[] alternatives,
                                      @Nonnull float[] shares,
                                      int alternativeCount) {
        float slotWeight = Math.clamp(weight, 0.0F, 1.0F);
        // A positive-weight test rejects NaN before it can propagate into the outputs
        if (!(slotWeight > 0.0F)) {
            return;
        }
        // Validate every rig before modifying either output so a mismatch cannot leave a partially applied slot
        for (int alternative = 0; alternative < alternativeCount; alternative++) {
            requireRig(pose, alternatives[alternative]);
        }

        AnimationRig rig = pose.getRig();
        float[] values = pose.values();
        for (int channel = 0; channel < rig.getChannelCount(); channel++) {
            AnimationChannelKind kind = rig.kind(channel);
            int base = channel * AnimationPose.COMPONENTS;
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                // Resolve against the same input; applying them in sequence would give later alternatives priority
                float lower = values[base + axis];
                float blendedDelta = 0.0F;
                for (int alternative = 0; alternative < alternativeCount; alternative++) {
                    blendedDelta += shares[alternative]
                            * delta(alternatives[alternative], channel, base + axis, kind, lower);
                }
                values[base + axis] = lower + slotWeight * blendedDelta;
            }
        }

        // Combine strengths before applying suppression; applying alternatives separately would weaken suppression
        // during a crossfade even when every alternative fully suppresses the component
        for (int component = 0; component < SuppressibleComponent.COUNT; component++) {
            float blendedStrength = 0.0F;
            for (int alternative = 0; alternative < alternativeCount; alternative++) {
                blendedStrength += shares[alternative] * alternatives[alternative].suppressions()[component];
            }
            suppression.suppress(component, slotWeight * blendedStrength);
        }
    }

    /**
     * Returns the signed change to one component when applying a contribution at full slot weight.
     */
    private static float delta(@Nonnull AnimationContribution contribution,
                               int channel,
                               int component,
                               @Nonnull AnimationChannelKind kind,
                               float lower) {
        boolean keyed = contribution.keyedFlags()[channel];
        float coverage = contribution.coverages()[channel];
        if (coverage > 0.0F) {
            // Missing keys must not release ownership, or a crossfade to rest would expose the lower pose
            float target = keyed ? contribution.values().values()[component] : kind.getRest();
            return coverage * (target - lower);
        }
        if (!keyed) {
            return 0.0F;
        }
        float value = contribution.values().values()[component];
        return kind == AnimationChannelKind.SCALE
                ? lower * (value - 1.0F)
                : value;
    }

    private static void requireRig(@Nonnull AnimationPose pose, @Nonnull AnimationContribution contribution) {
        // Avoid varargs validation helpers so successful calls do not allocate an argument array
        if (contribution.getRig() != pose.getRig()) {
            throw new IllegalArgumentException("A contribution on rig " + contribution.getRig().getName()
                    + " cannot apply to a pose on rig " + pose.getRig().getName());
        }
    }

}
