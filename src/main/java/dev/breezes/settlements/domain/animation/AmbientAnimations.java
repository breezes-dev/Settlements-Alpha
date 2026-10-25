package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import dev.breezes.settlements.shared.util.RotationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Clips of the villager's ambient motion.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AmbientAnimations {

    // The final hold creates a natural pause between breathing cycles
    public static final int BREATHING_CYCLE_DURATION_TICKS = 80;
    public static final int BLINK_DURATION_TICKS = 6;
    public static final int BROW_MOTION_DURATION_TICKS = 36;

    // A brow motion eases out to its pose, holds it, and eases back to rest
    private static final int BROW_MOTION_REACHED_TICK = 8;
    private static final int BROW_MOTION_RELEASED_TICK = 24;

    private static final float BROW_RAISE_OFFSET = -0.8F;
    private static final float BROW_TILT_DEGREES = 4.0F;

    /**
     * The looping ambient breathing cycle, keyed as offsets onto the standing pose.
     * <p>
     * Every track ends on a hold of its starting value, so the loop pauses between breaths rather than snapping
     * straight back to the top.
     */
    public static KeyframeAnimation breathingCycle() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/ambient/breathing_cycle"))
                .durationTicks(BREATHING_CYCLE_DURATION_TICKS)
                .loopMode(LoopMode.LOOP)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.ARMS_CROSSED_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(-1.11f, 0.0f, 0.24f), Easing.LINEAR),
                                new Keyframe<>(2, RotationUtil.degrees(-0.1f, 0.0f, 0.32f), Easing.LINEAR),
                                new Keyframe<>(4, RotationUtil.degrees(0.75f, 0.0f, 0.4f), Easing.LINEAR),
                                new Keyframe<>(6, RotationUtil.degrees(1.42f, 0.0f, 0.45f), Easing.LINEAR),
                                new Keyframe<>(8, RotationUtil.degrees(1.85f, 0.0f, 0.49f), Easing.LINEAR),
                                new Keyframe<>(10, RotationUtil.degrees(2.0f, 0.0f, 0.5f), Easing.LINEAR),
                                new Keyframe<>(12, RotationUtil.degrees(1.85f, 0.0f, 0.49f), Easing.LINEAR),
                                new Keyframe<>(14, RotationUtil.degrees(1.42f, 0.0f, 0.45f), Easing.LINEAR),
                                new Keyframe<>(16, RotationUtil.degrees(0.75f, 0.0f, 0.4f), Easing.LINEAR),
                                new Keyframe<>(18, RotationUtil.degrees(-0.1f, 0.0f, 0.32f), Easing.LINEAR),
                                new Keyframe<>(22, RotationUtil.degrees(-2.22f, 0.0f, 0.15f), Easing.LINEAR),
                                new Keyframe<>(28, RotationUtil.degrees(-5.78f, 0.0f, -0.15f), Easing.LINEAR),
                                new Keyframe<>(32, RotationUtil.degrees(-7.9f, 0.0f, -0.32f), Easing.LINEAR),
                                new Keyframe<>(34, RotationUtil.degrees(-8.75f, 0.0f, -0.4f), Easing.LINEAR),
                                new Keyframe<>(36, RotationUtil.degrees(-9.42f, 0.0f, -0.45f), Easing.LINEAR),
                                new Keyframe<>(38, RotationUtil.degrees(-9.85f, 0.0f, -0.49f), Easing.LINEAR),
                                new Keyframe<>(40, RotationUtil.degrees(-10.0f, 0.0f, -0.5f), Easing.LINEAR),
                                new Keyframe<>(42, RotationUtil.degrees(-9.85f, 0.0f, -0.49f), Easing.LINEAR),
                                new Keyframe<>(44, RotationUtil.degrees(-9.42f, 0.0f, -0.45f), Easing.LINEAR),
                                new Keyframe<>(46, RotationUtil.degrees(-8.75f, 0.0f, -0.4f), Easing.LINEAR),
                                new Keyframe<>(48, RotationUtil.degrees(-7.9f, 0.0f, -0.32f), Easing.LINEAR),
                                new Keyframe<>(52, RotationUtil.degrees(-5.78f, 0.0f, -0.15f), Easing.LINEAR),
                                new Keyframe<>(58, RotationUtil.degrees(-2.22f, 0.0f, 0.15f), Easing.LINEAR),
                                new Keyframe<>(60, RotationUtil.degrees(-1.11f, 0.0f, 0.24f), Easing.LINEAR),
                                new Keyframe<>(80, RotationUtil.degrees(-1.11f, 0.0f, 0.24f), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.ARMS_CROSSED_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vec3(0.0, 0.5, -0.5), Easing.CUBIC),
                                new Keyframe<>(30, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(60, new Vec3(0.0, 0.5, -0.5), Easing.CUBIC),
                                new Keyframe<>(80, new Vec3(0.0, 0.5, -0.5), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.NOSE_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(3.38f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(4, RotationUtil.degrees(1.94f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(8, RotationUtil.degrees(0.78f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(10, RotationUtil.degrees(0.36f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(12, RotationUtil.degrees(0.1f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(14, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(16, RotationUtil.degrees(0.1f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(18, RotationUtil.degrees(0.36f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(20, RotationUtil.degrees(0.78f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(24, RotationUtil.degrees(1.94f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(34, RotationUtil.degrees(5.56f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(38, RotationUtil.degrees(6.72f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(40, RotationUtil.degrees(7.14f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(42, RotationUtil.degrees(7.4f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(44, RotationUtil.degrees(7.5f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(46, RotationUtil.degrees(7.4f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(48, RotationUtil.degrees(7.14f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(50, RotationUtil.degrees(6.72f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(54, RotationUtil.degrees(5.56f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(60, RotationUtil.degrees(3.38f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(80, RotationUtil.degrees(3.38f, 0.0f, 0.0f), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.PUPIL_LEFT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(24, new Vec3(0.0, 0.1, 0.0), Easing.CUBIC),
                                new Keyframe<>(60, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(80, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.PUPIL_LEFT_SCALE)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vector3f(1.0f, 1.0f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(24, new Vector3f(1.0f, 0.8f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(60, new Vector3f(1.0f, 1.0f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(80, new Vector3f(1.0f, 1.0f, 1.0f), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.PUPIL_RIGHT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(24, new Vec3(0.0, 0.1, 0.0), Easing.CUBIC),
                                new Keyframe<>(60, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(80, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.PUPIL_RIGHT_SCALE)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vector3f(1.0f, 1.0f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(24, new Vector3f(1.0f, 0.8f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(60, new Vector3f(1.0f, 1.0f, 1.0f), Easing.CUBIC),
                                new Keyframe<>(80, new Vector3f(1.0f, 1.0f, 1.0f), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.BODY_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(14, RotationUtil.degrees(-1.35f, -2.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(30, RotationUtil.degrees(-3.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(46, RotationUtil.degrees(-1.35f, 2.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(60, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(80, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.LINEAR)
                        ))
                        .build())
                .build();
    }

    /**
     * Single-shot eyelid blink that closes both eyelids before returning to neutral.
     */
    public static KeyframeAnimation blink() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/ambient/blink"))
                .durationTicks(BLINK_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.EYELID_LEFT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                                new Keyframe<>(2, new Vec3(0.0, 1.1, 0.0), Easing.LINEAR),
                                new Keyframe<>(4, new Vec3(0.0, 1.1, 0.0), Easing.LINEAR),
                                new Keyframe<>(6, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.EYELID_RIGHT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                                new Keyframe<>(2, new Vec3(0.0, 1.1, 0.0), Easing.LINEAR),
                                new Keyframe<>(4, new Vec3(0.0, 1.1, 0.0), Easing.LINEAR),
                                new Keyframe<>(6, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .build();
    }

    /**
     * Single-shot ambient brow raise that lifts the whole brow and lowers it back to rest.
     */
    public static KeyframeAnimation browRaise() {
        Vec3 raised = new Vec3(0.0, BROW_RAISE_OFFSET, 0.0);
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/ambient/brow_raise"))
                .durationTicks(BROW_MOTION_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.MONOBROW_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(BROW_MOTION_REACHED_TICK, raised, Easing.LINEAR),
                                new Keyframe<>(BROW_MOTION_RELEASED_TICK, raised, Easing.CUBIC),
                                new Keyframe<>(BROW_MOTION_DURATION_TICKS, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .build();
    }

    /**
     * Single-shot ambient brow tilt that rolls the brow's end on the villager's left up slightly and back to rest.
     */
    public static KeyframeAnimation browRaiseLeftEnd() {
        return browTilt("animation/ambient/brow_raise_left_end", -BROW_TILT_DEGREES);
    }

    /**
     * Single-shot ambient brow tilt that rolls the brow's end on the villager's right up slightly and back to rest.
     */
    public static KeyframeAnimation browRaiseRightEnd() {
        return browTilt("animation/ambient/brow_raise_right_end", BROW_TILT_DEGREES);
    }

    /**
     * Builds a brow tilt holding a roll in degrees, where a positive roll raises the brow's end on the villager's
     * right.
     */
    private static KeyframeAnimation browTilt(@Nonnull String id, float rollDegrees) {
        Vector3f level = RotationUtil.degrees(0.0f, 0.0f, 0.0f);
        Vector3f tilted = RotationUtil.degrees(0.0f, 0.0f, rollDegrees);
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod(id))
                .durationTicks(BROW_MOTION_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.MONOBROW_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, level, Easing.CUBIC),
                                new Keyframe<>(BROW_MOTION_REACHED_TICK, tilted, Easing.LINEAR),
                                new Keyframe<>(BROW_MOTION_RELEASED_TICK, tilted, Easing.CUBIC),
                                new Keyframe<>(BROW_MOTION_DURATION_TICKS, level, Easing.LINEAR)
                        ))
                        .build())
                .build();
    }

}
