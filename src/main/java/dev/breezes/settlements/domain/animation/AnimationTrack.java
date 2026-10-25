package dev.breezes.settlements.domain.animation;

import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;

/**
 * One channel's keyframes, held in tick order.
 */
@Getter
public final class AnimationTrack<V> {

    private final AnimationTarget<V> target;
    private final List<Keyframe<V>> keyframes;

    /**
     * Takes the keyframes in any order.
     *
     * @throws IllegalArgumentException if there are no keyframes or two share a tick
     */
    @Builder
    private AnimationTrack(@Nonnull AnimationTarget<V> target,
                           @Nonnull List<Keyframe<V>> keyframes) {
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("AnimationTrack requires at least one keyframe");
        }

        List<Keyframe<V>> sortedKeyframes = keyframes.stream()
                .sorted(Comparator.comparingInt(Keyframe::tick))
                .toList();
        validateUniqueTicks(sortedKeyframes);

        this.target = target;
        this.keyframes = sortedKeyframes;
    }

    private static <V> void validateUniqueTicks(@Nonnull List<Keyframe<V>> keyframes) {
        int previousTick = -1;
        for (Keyframe<V> keyframe : keyframes) {
            if (keyframe.tick() == previousTick) {
                throw new IllegalArgumentException("AnimationTrack keyframe ticks must be unique");
            }
            previousTick = keyframe.tick();
        }
    }

}
