package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import dev.breezes.settlements.shared.util.RotationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BindVexesAnimations {

    /**
     * One loop of the channel, in which the raised arms swing to each of their two extremes twice.
     */
    private static final int LOOP_TICKS = 20;

    /**
     * The swing from one arm extreme to the other.
     */
    private static final int SWING_TICKS = LOOP_TICKS / 4;

    /**
     * The whole channel: two loops.
     */
    public static final ClockTicks CHANNEL_DURATION = ClockTicks.of(LOOP_TICKS * 2);

    /**
     * The instant the bind lands: the channel's last arm extreme before it ends.
     */
    public static final ClockTicks COMMIT_TIME = ClockTicks.of(LOOP_TICKS * 2 - SWING_TICKS);

    // Long enough that raising the arms to 135 degrees does not snap
    private static final int BLEND_IN_TICKS = 8;
    private static final int BLEND_OUT_TICKS = 8;

    /**
     * A sustained channel with both arms raised high and swinging, the brow raised, and the body bobbing in place of
     * breathing.
     */
    public static KeyframeAnimation bindVexes() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/librarian/bind_vexes"))
                .durationTicks(LOOP_TICKS)
                .loopMode(LoopMode.LOOP)
                .blendInTicks(BLEND_IN_TICKS)
                .blendOutTicks(BLEND_OUT_TICKS)
                .owns(VillagerAnimationRig.HEAD_ROTATION)
                .owns(VillagerAnimationRig.LEG_ROTATION)
                .suppresses(SuppressibleComponent.BROW, SuppressibleComponent.BREATH)
                .arms(ArmConfiguration.BOTH_STRAIGHT)
                .track(rotation(AnimationTargets.ARM_STRAIGHT_RIGHT_ROTATION, alternating(
                        rotation(-15.0F, 0.0F, 135.0F), rotation(15.0F, 0.0F, 130.0F))))
                .track(rotation(AnimationTargets.ARM_STRAIGHT_LEFT_ROTATION, alternating(
                        rotation(-15.0F, 0.0F, -135.0F), rotation(15.0F, 0.0F, -130.0F))))
                .track(rotation(AnimationTargets.BODY_ROTATION, List.of(
                        key(0, rotation(4.0F, 0.0F, -1.5F)),
                        key(SWING_TICKS, rotation(2.0F, 0.0F, 0.0F)),
                        key(SWING_TICKS * 2, rotation(4.0F, 0.0F, 1.5F)),
                        key(SWING_TICKS * 3, rotation(2.0F, 0.0F, 0.0F)),
                        key(LOOP_TICKS, rotation(4.0F, 0.0F, -1.5F)))))
                .track(translation(AnimationTargets.BODY_TRANSLATION, alternating(
                        Vec3.ZERO, new Vec3(0.0D, 0.1D, 0.0D))))
                .track(rotation(AnimationTargets.HEAD_ROTATION, alternating(
                        rotation(3.0F, 0.0F, 0.0F), rotation(1.0F, 0.0F, 0.0F))))
                .track(rotation(AnimationTargets.MONOBROW_ROTATION, List.of(
                        key(0, rotation(0.0F, 0.0F, -1.5F)),
                        key(SWING_TICKS * 2, rotation(0.0F, 0.0F, 1.5F)),
                        key(LOOP_TICKS, rotation(0.0F, 0.0F, -1.5F)))))
                .track(translation(AnimationTargets.MONOBROW_TRANSLATION, alternating(
                        new Vec3(0.0D, -0.25D, 0.0D), new Vec3(0.0D, -0.45D, 0.0D))))
                .track(rotation(AnimationTargets.NOSE_ROTATION, alternating(
                        rotation(-3.0F, 0.0F, 0.0F), rotation(2.5F, 0.0F, 0.0F))))
                .track(rotation(AnimationTargets.LEG_LEFT_ROTATION, List.of(
                        key(0, rotation(0.0F, -7.5F, 0.0F)))))
                .track(rotation(AnimationTargets.LEG_RIGHT_ROTATION, List.of(
                        key(0, rotation(0.0F, 7.5F, 0.0F)))))
                .build();
    }

    /**
     * Keys a value that alternates between two extremes at every swing boundary, starting and ending the loop on the first.
     */
    private static <V> List<Keyframe<V>> alternating(@Nonnull V first, @Nonnull V second) {
        return List.of(
                key(0, first),
                key(SWING_TICKS, second),
                key(SWING_TICKS * 2, first),
                key(SWING_TICKS * 3, second),
                key(LOOP_TICKS, first));
    }

    private static AnimationTrack<Vector3f> rotation(@Nonnull AnimationTarget<Vector3f> target,
                                                     @Nonnull List<Keyframe<Vector3f>> keyframes) {
        return AnimationTrack.<Vector3f>builder().target(target).keyframes(keyframes).build();
    }

    private static AnimationTrack<Vec3> translation(@Nonnull AnimationTarget<Vec3> target,
                                                    @Nonnull List<Keyframe<Vec3>> keyframes) {
        return AnimationTrack.<Vec3>builder().target(target).keyframes(keyframes).build();
    }

    private static Vector3f rotation(float pitchDegrees, float yawDegrees, float rollDegrees) {
        return RotationUtil.degrees(pitchDegrees, yawDegrees, rollDegrees);
    }

    private static <V> Keyframe<V> key(int tick, @Nonnull V value) {
        return new Keyframe<>(tick, value, Easing.CUBIC);
    }

}
