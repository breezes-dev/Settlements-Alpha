package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationTrackTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final AnimationTarget<Vec3> TARGET = AnimationTargets.BODY_TRANSLATION;
    private static final float TOLERANCE = 1.0E-5F;

    @Test
    void build_ordersKeyframesByTick_whateverOrderTheyAreGivenIn() {
        // Arrange
        AnimationTrack<Vec3> track = track(
                new Keyframe<>(10, new Vec3(0.0, 10.0, 0.0), Easing.LINEAR),
                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR));
        AnimationPose pose = new AnimationPose(RIG);

        // Act
        bake(track).sampleInto(pose, 5.0F);

        // Assert: keys left in authored order would hold the first value instead of interpolating
        assertEquals(5.0F, pose.get(RIG.indexOf(TARGET), 1), TOLERANCE);
    }

    @Test
    void build_rejectsTwoKeyframes_atOneTick() {
        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> track(
                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                new Keyframe<>(0, new Vec3(0.0, 10.0, 0.0), Easing.LINEAR)));
    }

    @Test
    void build_rejectsATrack_withNoKeyframes() {
        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> AnimationTrack.<Vec3>builder()
                .target(TARGET)
                .keyframes(List.of())
                .build());
    }

    @SafeVarargs
    private static AnimationTrack<Vec3> track(Keyframe<Vec3>... keyframes) {
        return AnimationTrack.<Vec3>builder()
                .target(TARGET)
                .keyframes(List.of(keyframes))
                .build();
    }

    private static BakedAnimation bake(AnimationTrack<Vec3> track) {
        return BakedAnimation.bake(KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/track"))
                .durationTicks(10)
                .track(track)
                .build(), RIG);
    }

}
