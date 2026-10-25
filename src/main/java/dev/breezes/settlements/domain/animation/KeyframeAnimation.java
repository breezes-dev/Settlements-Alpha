package dev.breezes.settlements.domain.animation;

import lombok.Builder;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * A keyframed clip with playback timing and composition declarations, sampled through a {@link BakedAnimation} made
 * from it for one rig.
 */
@Getter
public final class KeyframeAnimation {

    private final ResourceLocation id;
    private final int durationTicks;
    private final LoopMode loopMode;
    private final int blendInTicks;
    private final int blendOutTicks;
    private final List<AnimationTrack<?>> tracks;
    private final ArmConfigurationTimeline armConfigurationTimeline;

    /**
     * Ownership declarations in order. A channel named more than once takes the coverage of the last declaration.
     */
    private final List<AnimationOwnership> ownership;

    /**
     * The components this clip suppresses at full strength.
     */
    private final Set<SuppressibleComponent> suppressedComponents;

    /**
     * Whether this clip declares full-body ownership and suppression of every component. Explicit ownership
     * declarations override the default full coverage.
     */
    private final boolean exclusive;

    public static TrackAnimationBuilder fromTracks() {
        return new TrackAnimationBuilder();
    }

    @Builder
    private KeyframeAnimation(@Nonnull ResourceLocation id,
                              int durationTicks,
                              @Nonnull LoopMode loopMode,
                              int blendInTicks,
                              int blendOutTicks,
                              @Nonnull List<AnimationTrack<?>> tracks,
                              @Nullable List<ArmConfigurationKeyframe> armConfigurationKeyframes,
                              @Nullable List<AnimationOwnership> ownership,
                              @Nullable Set<SuppressibleComponent> suppressedComponents,
                              boolean exclusive) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Animation duration must be non-negative");
        }
        if (blendInTicks < 0 || blendOutTicks < 0) {
            throw new IllegalArgumentException("Blend durations must be non-negative");
        }

        this.id = id;
        this.durationTicks = durationTicks;
        this.loopMode = loopMode;
        this.blendInTicks = blendInTicks;
        this.blendOutTicks = blendOutTicks;
        this.tracks = List.copyOf(tracks);
        this.armConfigurationTimeline = armConfigurationKeyframes == null
                ? ArmConfigurationTimeline.EMPTY
                : ArmConfigurationTimeline.of(armConfigurationKeyframes);
        this.ownership = ownership == null ? List.of() : List.copyOf(ownership);
        this.suppressedComponents = suppressedComponents == null ? Set.of() : Set.copyOf(suppressedComponents);
        this.exclusive = exclusive;
    }

}
