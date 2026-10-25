package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builders for the throwaway clips the animation tests play. Every magnitude a test needs is passed in by that test, so
 * no assertion ends up restating an authored constant.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AnimationTestClips {

    /**
     * A trackless clip that plays once with the given timing.
     */
    static KeyframeAnimation timed(String name, int blendInTicks, int durationTicks, int blendOutTicks) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(durationTicks)
                .loopMode(LoopMode.ONCE)
                .blendInTicks(blendInTicks)
                .blendOutTicks(blendOutTicks)
                .build();
    }

    /**
     * A layer playing the animation once from the start tick, as an action trigger plays it.
     */
    static AnimationLayer playingOnce(KeyframeAnimation animation, long startTick) {
        AnimationLayer layer = AnimationLayer.persistent(animation, startTick);
        layer.restart(animation, startTick, true);
        return layer;
    }

}
