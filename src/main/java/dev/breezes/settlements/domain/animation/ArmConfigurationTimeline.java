package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmPose;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable timeline of arm claims at unique animation ticks. Each keyframe's claims hold until the next keyframe;
 * the first and last keyframes also hold outside the keyed range.
 */
public final class ArmConfigurationTimeline {

    public static final ArmConfigurationTimeline EMPTY = new ArmConfigurationTimeline(List.of());

    private final List<ArmConfigurationKeyframe> keyframes;

    private ArmConfigurationTimeline(@Nonnull List<ArmConfigurationKeyframe> keyframes) {
        List<ArmConfigurationKeyframe> sortedKeyframes = keyframes.stream()
                .sorted(Comparator.comparingInt(ArmConfigurationKeyframe::tick))
                .toList();
        validateUniqueTicks(sortedKeyframes);
        this.keyframes = sortedKeyframes;
    }

    /**
     * Creates a timeline from keyframes in any order, or returns EMPTY when none are supplied.
     *
     * @throws IllegalArgumentException if two keyframes have the same tick
     */
    public static ArmConfigurationTimeline of(@Nonnull List<ArmConfigurationKeyframe> keyframes) {
        if (keyframes.isEmpty()) {
            return EMPTY;
        }
        return new ArmConfigurationTimeline(keyframes);
    }

    /**
     * The pose the clip claims for its left arm at this tick, or null when it leaves that arm unclaimed.
     */
    @Nullable
    public ArmPose leftAt(float animationTick) {
        int index = this.activeIndex(animationTick);
        return index < 0 ? null : this.keyframes.get(index).left();
    }

    /**
     * The pose the clip claims for its right arm at this tick, or null when it leaves that arm unclaimed.
     */
    @Nullable
    public ArmPose rightAt(float animationTick) {
        int index = this.activeIndex(animationTick);
        return index < 0 ? null : this.keyframes.get(index).right();
    }

    /**
     * The index of the keyframe in effect at this tick, or -1 for an empty timeline.
     */
    private int activeIndex(float animationTick) {
        if (this.keyframes.isEmpty()) {
            return -1;
        }
        // Indexing avoids allocating an iterator during sampling. Keep the strict comparison so a NaN tick selects
        // the final keyframe.
        int active = 0;
        for (int index = 1; index < this.keyframes.size(); index++) {
            if (animationTick < this.keyframes.get(index).tick()) {
                break;
            }
            active = index;
        }
        return active;
    }

    private static void validateUniqueTicks(@Nonnull List<ArmConfigurationKeyframe> keyframes) {
        int previousTick = -1;
        for (ArmConfigurationKeyframe keyframe : keyframes) {
            if (keyframe.tick() == previousTick) {
                throw new IllegalArgumentException("Arm configuration keyframe ticks must be unique");
            }
            previousTick = keyframe.tick();
        }
    }

}
