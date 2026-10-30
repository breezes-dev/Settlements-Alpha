package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import dev.breezes.settlements.shared.util.RotationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.joml.Vector3f;

import java.util.List;

/**
 * Additive crossed-arm poses for carrying a lantern, with no channel ownership or arm claims.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LanternCarryAnimations {

    public static final float HOLD_PITCH_SHARE = 0.5F;
    public static final float HOLD_PITCH_DEGREES = InteractAnimations.INTERACT_PEAK_PITCH_DEGREES * HOLD_PITCH_SHARE;

    public static KeyframeAnimation hold() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/lantern/carry_hold"))
                .durationTicks(0)
                .loopMode(LoopMode.ONCE)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.ARMS_CROSSED_ROTATION)
                        .keyframes(List.of(new Keyframe<>(0,
                                RotationUtil.degrees(HOLD_PITCH_DEGREES, 0.0F, 0.0F), Easing.LINEAR)))
                        .build())
                .build();
    }

    public static KeyframeAnimation show() {
        return gesture("show", 0.0F, HOLD_PITCH_DEGREES);
    }

    public static KeyframeAnimation stow() {
        return gesture("stow", HOLD_PITCH_DEGREES, 0.0F);
    }

    private static KeyframeAnimation gesture(String name, float startPitchDegrees, float endPitchDegrees) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/lantern/carry_" + name))
                .durationTicks(InteractAnimations.INTERACT_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.ARMS_CROSSED_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(startPitchDegrees, 0.0F, 0.0F), Easing.EASE_IN),
                                new Keyframe<>(InteractAnimations.INTERACT_PEAK_TICK,
                                        RotationUtil.degrees(InteractAnimations.INTERACT_PEAK_PITCH_DEGREES, 0.0F, 0.0F),
                                        Easing.EASE_OUT),
                                new Keyframe<>(InteractAnimations.INTERACT_DURATION_TICKS,
                                        RotationUtil.degrees(endPitchDegrees, 0.0F, 0.0F), Easing.LINEAR)))
                        .build())
                .build();
    }

}
