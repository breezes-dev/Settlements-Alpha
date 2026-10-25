package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import dev.breezes.settlements.shared.util.RotationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The idle animations: occasional motions a villager plays while it stands idle.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdleAnimations {

    public static final int PONDER_DURATION_TICKS = 80;

    /**
     * The villager tips its head up and to its left, raises its brow, and glances up and to its right as if turning a
     * thought over, then nods twice while its weight shifts from side to side.
     */
    public static KeyframeAnimation ponder() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/idle/ponder"))
                .durationTicks(PONDER_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .blendInTicks(4)
                .blendOutTicks(8)
                .owns(VillagerAnimationRig.HEAD_ROTATION)
                .suppresses(SuppressibleComponent.GAZE, SuppressibleComponent.BROW)
                .arms(ArmConfiguration.BOTH_CROSSED)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.ARMS_CROSSED_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(-2.11f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(4, RotationUtil.degrees(-1.17f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(8, RotationUtil.degrees(-0.46f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(12, RotationUtil.degrees(-0.05f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(14, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(16, RotationUtil.degrees(-0.05f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(20, RotationUtil.degrees(-0.46f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(24, RotationUtil.degrees(-1.17f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(32, RotationUtil.degrees(-3.19f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(40, RotationUtil.degrees(-5.39f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(44, RotationUtil.degrees(-6.33f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(48, RotationUtil.degrees(-7.04f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(52, RotationUtil.degrees(-7.45f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(56, RotationUtil.degrees(-7.45f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(60, RotationUtil.degrees(-7.04f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(64, RotationUtil.degrees(-6.33f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(72, RotationUtil.degrees(-4.31f, 0.0f, 0.0f), Easing.LINEAR),
                                new Keyframe<>(80, RotationUtil.degrees(-2.11f, 0.0f, 0.0f), Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.ARMS_CROSSED_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vec3(0.0, 0.5, -0.5), Easing.CUBIC),
                                new Keyframe<>(40, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(80, new Vec3(0.0, 0.5, -0.5), Easing.CUBIC)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.HEAD_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(20, RotationUtil.degrees(-10.0f, -18.0f, -4.0f), Easing.CUBIC),
                                new Keyframe<>(40, RotationUtil.degrees(-10.0f, -18.0f, -4.0f), Easing.CUBIC),
                                new Keyframe<>(50, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(56, RotationUtil.degrees(8.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(62, RotationUtil.degrees(-2.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(68, RotationUtil.degrees(6.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(80, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.MONOBROW_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                                new Keyframe<>(20, new Vec3(0.0, -0.15, 0.0), Easing.LINEAR),
                                new Keyframe<>(40, new Vec3(0.0, -0.15, 0.0), Easing.LINEAR),
                                new Keyframe<>(50, Vec3.ZERO, Easing.LINEAR)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.PUPIL_LEFT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(16, new Vec3(0.0, -0.3, 0.0), Easing.CUBIC),
                                new Keyframe<>(40, new Vec3(0.0, -0.3, 0.0), Easing.CUBIC),
                                new Keyframe<>(48, Vec3.ZERO, Easing.CUBIC)
                        ))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.PUPIL_RIGHT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.CUBIC),
                                new Keyframe<>(16, new Vec3(-0.3, -0.3, 0.0), Easing.CUBIC),
                                new Keyframe<>(40, new Vec3(-0.3, -0.3, 0.0), Easing.CUBIC),
                                new Keyframe<>(48, Vec3.ZERO, Easing.CUBIC)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.BODY_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(20, RotationUtil.degrees(-1.35f, -2.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(40, RotationUtil.degrees(-3.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(60, RotationUtil.degrees(-1.35f, 2.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(80, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC)
                        ))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.NOSE_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC),
                                new Keyframe<>(24, RotationUtil.degrees(5.0f, 0.0f, 5.0f), Easing.CUBIC),
                                new Keyframe<>(40, RotationUtil.degrees(5.0f, 0.0f, 5.0f), Easing.CUBIC),
                                new Keyframe<>(54, RotationUtil.degrees(0.0f, 0.0f, 0.0f), Easing.CUBIC)
                        ))
                        .build())
                .build();
    }

}
