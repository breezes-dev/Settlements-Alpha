package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedAnimationTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;

    private static final int DURATION_TICKS = 20;
    private static final int ARM_SWITCH_TICK = 8;
    private static final float TOLERANCE = 1.0E-5F;

    @Test
    void contribute_carriesTheDeclaredCoverage_onOwnedChannelsOnly() {
        // Arrange
        float headCoverage = 0.5F;
        KeyframeAnimation clip = clip()
                .owns(VillagerAnimationRig.HEAD_ROTATION, headCoverage)
                .owns(VillagerAnimationRig.LEG_ROTATION)
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            float expected = VillagerAnimationRig.HEAD_ROTATION.contains(channel) ? headCoverage
                    : VillagerAnimationRig.LEG_ROTATION.contains(channel) ? 1.0F
                    : 0.0F;
            assertEquals(expected, contribution.coverage(channel), TOLERANCE, RIG.channel(channel).getId());
        }
    }

    @Test
    void contribute_takesTheLaterCoverage_whereTwoOwnershipDeclarationsOverlap() {
        // Arrange
        float headCoverage = 0.25F;
        KeyframeAnimation clip = clip()
                .owns(VillagerAnimationRig.FULL_BODY)
                .owns(VillagerAnimationRig.HEAD, headCoverage)
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        assertEquals(headCoverage, contribution.coverage(RIG.indexOf(AnimationTargets.HEAD_TRANSLATION)), TOLERANCE);
        assertEquals(1.0F, contribution.coverage(RIG.indexOf(AnimationTargets.BODY_TRANSLATION)), TOLERANCE);
    }

    @Test
    void contribute_suppressesExactlyTheDeclaredComponents_atFullStrength() {
        // Arrange
        KeyframeAnimation clip = clip()
                .suppresses(SuppressibleComponent.GAZE, SuppressibleComponent.BROW)
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            boolean declared = component == SuppressibleComponent.GAZE || component == SuppressibleComponent.BROW;
            assertEquals(declared ? 1.0F : 0.0F, contribution.suppression(component), TOLERANCE, component.name());
        }
    }

    @Test
    void contribute_ownsTheFullBodyAndSuppressesEveryComponent_forAnExclusiveClip() {
        // Arrange
        KeyframeAnimation clip = clip().playExclusively().build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            assertEquals(RIG.isSlotChannel(channel) ? 0.0F : 1.0F, contribution.coverage(channel), TOLERANCE,
                    RIG.channel(channel).getId());
        }
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            assertEquals(1.0F, contribution.suppression(component), TOLERANCE, component.name());
        }
    }

    @Test
    void contribute_marksOnlyTrackedChannelsKeyed() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(translationTrack(AnimationTargets.BODY_TRANSLATION, 0.0, 1.0, 0.0))
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            assertEquals(channel == RIG.indexOf(AnimationTargets.BODY_TRANSLATION), contribution.isKeyed(channel),
                    RIG.channel(channel).getId());
        }
    }

    @Test
    void sampleInto_leavesEveryUnkeyedChannelAsItWas() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(translationTrack(AnimationTargets.BODY_TRANSLATION, 0.0, 1.0, 0.0))
                .build();
        AnimationPose pose = new AnimationPose(RIG);
        int untouched = RIG.indexOf(AnimationTargets.NOSE_ROTATION);
        pose.set(untouched, 0.5F, 0.5F, 0.5F);

        // Act
        BakedAnimation.bake(clip, RIG).sampleInto(pose, 0.0F);

        // Assert
        assertEquals(1.0F, pose.get(RIG.indexOf(AnimationTargets.BODY_TRANSLATION), 1), TOLERANCE);
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(0.5F, pose.get(untouched, axis), TOLERANCE);
        }
    }

    @Test
    void sampleInto_holdsATracksFirstValue_beforeItsFirstKey_andItsLastValue_afterItsLastKey() {
        // Arrange
        BakedAnimation baked = bodyHeightClip(LoopMode.ONCE,
                new Keyframe<>(2, new Vec3(0.0, 3.0, 0.0), Easing.LINEAR),
                new Keyframe<>(8, new Vec3(0.0, 7.0, 0.0), Easing.LINEAR));

        // Act, Assert
        assertEquals(3.0F, bodyHeightAt(baked, 0.0F), TOLERANCE);
        assertEquals(7.0F, bodyHeightAt(baked, DURATION_TICKS - 1.0F), TOLERANCE);
    }

    @Test
    void sampleInto_givesEveryKeyItsOwnValue_onItsTick() {
        // Arrange: a distinct value per key, so reading a neighboring key's value shows
        BakedAnimation baked = bodyHeightClip(LoopMode.ONCE,
                new Keyframe<>(0, new Vec3(0.0, 1.0, 0.0), Easing.LINEAR),
                new Keyframe<>(5, new Vec3(0.0, 4.0, 0.0), Easing.LINEAR),
                new Keyframe<>(10, new Vec3(0.0, 9.0, 0.0), Easing.LINEAR));

        // Act, Assert
        assertEquals(1.0F, bodyHeightAt(baked, 0.0F), TOLERANCE);
        assertEquals(4.0F, bodyHeightAt(baked, 5.0F), TOLERANCE);
        assertEquals(9.0F, bodyHeightAt(baked, 10.0F), TOLERANCE);
    }

    @Test
    void sampleInto_interpolatesEveryAxisLinearly_betweenTwoKeys() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.BODY_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vec3(0.0, 10.0, -10.0), Easing.LINEAR),
                                new Keyframe<>(8, new Vec3(8.0, 18.0, 6.0), Easing.LINEAR)))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.HEAD_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, new Vector3f(0.4F, -0.4F, 0.0F), Easing.LINEAR),
                                new Keyframe<>(8, new Vector3f(0.0F, 0.4F, 0.8F), Easing.LINEAR)))
                        .build())
                .build();
        AnimationPose pose = new AnimationPose(RIG);

        // Act: a quarter of the way between the keys
        BakedAnimation.bake(clip, RIG).sampleInto(pose, 2.0F);

        // Assert
        int body = RIG.indexOf(AnimationTargets.BODY_TRANSLATION);
        assertEquals(2.0F, pose.get(body, 0), TOLERANCE);
        assertEquals(12.0F, pose.get(body, 1), TOLERANCE);
        assertEquals(-6.0F, pose.get(body, 2), TOLERANCE);
        int head = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
        assertEquals(0.3F, pose.get(head, 0), TOLERANCE);
        assertEquals(-0.2F, pose.get(head, 1), TOLERANCE);
        assertEquals(0.2F, pose.get(head, 2), TOLERANCE);
    }

    @Test
    void sampleInto_easesEachSegment_byItsEarlierKeysEasing() {
        // Arrange
        BakedAnimation baked = bodyHeightClip(LoopMode.ONCE,
                new Keyframe<>(0, Vec3.ZERO, Easing.EASE_IN),
                new Keyframe<>(10, new Vec3(0.0, 8.0, 0.0), Easing.LINEAR),
                new Keyframe<>(20, Vec3.ZERO, Easing.EASE_IN));

        // Act, Assert: halfway through an eased-in segment is a quarter of the way, and the linear one is halfway
        assertEquals(2.0F, bodyHeightAt(baked, 5.0F), TOLERANCE);
        assertEquals(4.0F, bodyHeightAt(baked, 15.0F), TOLERANCE);
    }

    @Test
    void sampleInto_findsEachSamplesSegment_inADenseTrackSampledOutOfOrder() {
        // Arrange
        BakedAnimation baked = bodyHeightClip(LoopMode.ONCE,
                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                new Keyframe<>(2, new Vec3(0.0, 2.0, 0.0), Easing.LINEAR),
                new Keyframe<>(4, new Vec3(0.0, 4.0, 0.0), Easing.LINEAR),
                new Keyframe<>(6, new Vec3(0.0, 6.0, 0.0), Easing.LINEAR),
                new Keyframe<>(8, new Vec3(0.0, 8.0, 0.0), Easing.LINEAR),
                new Keyframe<>(10, new Vec3(0.0, 10.0, 0.0), Easing.LINEAR));

        // Act, Assert: a sampler that kept a cursor from the last sample would land in the wrong segment going back
        assertEquals(9.0F, bodyHeightAt(baked, 9.0F), TOLERANCE);
        assertEquals(1.0F, bodyHeightAt(baked, 1.0F), TOLERANCE);
        assertEquals(7.0F, bodyHeightAt(baked, 7.0F), TOLERANCE);
    }

    @Test
    void sampleInto_holdsTheLastFrame_ofAClipPlayedOnce_pastItsDuration() {
        // Arrange
        BakedAnimation baked = rampingClip(LoopMode.ONCE);

        // Act, Assert
        assertEquals(DURATION_TICKS, bodyHeightAt(baked, DURATION_TICKS * 1.5F), TOLERANCE);
    }

    @Test
    void sampleInto_wrapsALoopingClip_pastItsDuration() {
        // Arrange
        BakedAnimation baked = rampingClip(LoopMode.LOOP);

        // Act, Assert
        assertEquals(DURATION_TICKS / 2.0F, bodyHeightAt(baked, DURATION_TICKS * 1.5F), TOLERANCE);
    }

    @Test
    void sampleInto_playsAPingPongClipBackward_pastItsDuration() {
        // Arrange
        BakedAnimation baked = rampingClip(LoopMode.PING_PONG);

        // Act, Assert
        assertEquals(DURATION_TICKS * 0.75F, bodyHeightAt(baked, DURATION_TICKS * 1.25F), TOLERANCE);
    }

    @Test
    void sampleInto_holdsTheLastValue_atATickThatIsNotANumber() {
        // Arrange
        BakedAnimation baked = rampingClip(LoopMode.ONCE);

        // Act, Assert: a NaN that fell through the comparisons would write NaN onto the rig
        assertEquals(DURATION_TICKS, bodyHeightAt(baked, Float.NaN), TOLERANCE);
    }

    @Test
    void everyProductionClip_bakesForTheProductionRigHoldingItsChannels() throws Exception {
        // Arrange
        List<KeyframeAnimation> clips = ProductionAnimationClips.all();
        assertFalse(clips.isEmpty(), "Discovery found no production clips");

        for (KeyframeAnimation clip : clips) {
            AnimationRig rig = ProductionAnimationClips.rigFor(clip);

            // Act
            BakedAnimation baked = BakedAnimation.bake(clip, rig);

            // Assert: a clip keying one channel twice, or owning a channel outside its rig, throws above
            assertEquals(clip.getTracks().size(), baked.getKeyedMask().size(), clip.getId().toString());
        }
    }

    @Test
    void contribute_switchesBothArmClaims_atTheDeclaredTick() {
        // Arrange
        KeyframeAnimation clip = clip()
                .arms(ArmConfiguration.BOTH_STRAIGHT)
                .armConfigurationAt(ARM_SWITCH_TICK, ArmConfiguration.BOTH_CROSSED)
                .build();
        BakedAnimation baked = BakedAnimation.bake(clip, RIG);
        AnimationContribution before = new AnimationContribution(RIG);
        AnimationContribution after = new AnimationContribution(RIG);

        // Act
        baked.contribute(before, ARM_SWITCH_TICK - 0.1F);
        baked.contribute(after, ARM_SWITCH_TICK);

        // Assert
        assertEquals(ArmPose.STRAIGHT, before.getLeftArmClaim());
        assertEquals(ArmPose.STRAIGHT, before.getRightArmClaim());
        assertEquals(ArmPose.CROSSED, after.getLeftArmClaim());
        assertEquals(ArmPose.CROSSED, after.getRightArmClaim());
    }

    @Test
    void contribute_claimsOnlyTheNamedArm_fromItsFirstClaim() {
        // Arrange
        KeyframeAnimation clip = clip()
                .leftArm(ArmPose.STRAIGHT)
                .rightArmAt(ARM_SWITCH_TICK, ArmPose.STRAIGHT)
                .build();
        BakedAnimation baked = BakedAnimation.bake(clip, RIG);
        AnimationContribution before = new AnimationContribution(RIG);
        AnimationContribution after = new AnimationContribution(RIG);

        // Act
        baked.contribute(before, 0.0F);
        baked.contribute(after, ARM_SWITCH_TICK);

        // Assert
        assertEquals(ArmPose.STRAIGHT, before.getLeftArmClaim());
        assertNull(before.getRightArmClaim());
        assertEquals(ArmPose.STRAIGHT, after.getLeftArmClaim());
        assertEquals(ArmPose.STRAIGHT, after.getRightArmClaim());
    }

    @Test
    void contribute_holdsTheLastArmClaims_pastTheEndOfAClipPlayedOnce() {
        // Arrange
        KeyframeAnimation clip = clip()
                .arms(ArmConfiguration.BOTH_STRAIGHT)
                .armConfigurationAt(ARM_SWITCH_TICK, ArmConfiguration.BOTH_CROSSED)
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, DURATION_TICKS * 2.0F);

        // Assert
        assertEquals(ArmPose.CROSSED, contribution.getLeftArmClaim());
        assertEquals(ArmPose.CROSSED, contribution.getRightArmClaim());
    }

    @Test
    void contribute_claimsNoArm_whenTheClipNamesNone() {
        // Arrange
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.claimArms(ArmPose.STRAIGHT, ArmPose.STRAIGHT);

        // Act: reusing a contribution must not preserve stale arm claims
        BakedAnimation.bake(clip().build(), RIG).contribute(contribution, 0.0F);

        // Assert
        assertNull(contribution.getLeftArmClaim());
        assertNull(contribution.getRightArmClaim());
    }

    @Test
    void bake_rejectsATrack_whoseTargetIsNotAChannelOfTheRig() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(translationTrack(UmbrellaAnimationTargets.CANOPY_TRANSLATION, 0.0, 1.0, 0.0))
                .build();

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> BakedAnimation.bake(clip, RIG));
    }

    @Test
    void bake_resolvesAnOwnedMaskFromAnotherRig_ontoTheSameChannelsOfThisRig() {
        // Arrange: different channel orders expose accidental reuse of the source rig's indexes
        float coverage = 0.5F;
        AnimationRig otherRig = AnimationRig.of("other", List.of(
                AnimationTargets.MOUTH_SCALE,
                AnimationTargets.HEAD_ROTATION));
        KeyframeAnimation clip = clip()
                .owns(otherRig.maskOf(List.of(AnimationTargets.HEAD_ROTATION)), coverage)
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);

        // Assert
        int headRotation = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            assertEquals(channel == headRotation ? coverage : 0.0F, contribution.coverage(channel), TOLERANCE,
                    RIG.channel(channel).getId());
        }
    }

    @Test
    void bake_rejectsAnOwnedMask_holdingAChannelTheRigLacks() {
        // Arrange
        AnimationRig otherRig = AnimationRig.of("other", List.of(
                AnimationTargets.HEAD_ROTATION,
                UmbrellaAnimationTargets.CANOPY_TRANSLATION));
        KeyframeAnimation clip = clip().owns(otherRig.getFullBody()).build();

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> BakedAnimation.bake(clip, RIG));
    }

    @Test
    void bake_rejectsTwoTracks_onOneChannel() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(translationTrack(AnimationTargets.BODY_TRANSLATION, 0.0, 1.0, 0.0))
                .track(translationTrack(AnimationTargets.BODY_TRANSLATION, 0.0, 2.0, 0.0))
                .build();

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> BakedAnimation.bake(clip, RIG));
    }

    @Test
    void contribute_rejectsAContribution_onAnotherRig() {
        // Arrange
        BakedAnimation baked = BakedAnimation.bake(clip().build(), RIG);
        AnimationRig otherRig = AnimationRig.of("other", List.of(AnimationTargets.HEAD_ROTATION));

        // Act, Assert
        assertThrows(IllegalArgumentException.class,
                () -> baked.contribute(new AnimationContribution(otherRig), 0.0F));
    }

    @Test
    void keyedMask_holdsExactlyTheTrackedChannels() {
        // Arrange
        KeyframeAnimation clip = clip()
                .track(translationTrack(AnimationTargets.BODY_TRANSLATION, 0.0, 1.0, 0.0))
                .build();

        // Act
        AnimationMask keyed = BakedAnimation.bake(clip, RIG).getKeyedMask();

        // Assert
        assertEquals(1, keyed.size());
        assertTrue(keyed.contains(RIG.indexOf(AnimationTargets.BODY_TRANSLATION)));
        assertFalse(keyed.contains(RIG.indexOf(AnimationTargets.BODY_ROTATION)));
    }

    private static TrackAnimationBuilder clip() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/baked"))
                .durationTicks(DURATION_TICKS);
    }

    /**
     * A clip of the given loop mode keying only the torso's translation.
     */
    @SafeVarargs
    private static BakedAnimation bodyHeightClip(LoopMode loopMode, Keyframe<Vec3>... keyframes) {
        return BakedAnimation.bake(clip()
                .loopMode(loopMode)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.BODY_TRANSLATION)
                        .keyframes(List.of(keyframes))
                        .build())
                .build(), RIG);
    }

    /**
     * A clip of the given loop mode whose torso height rises linearly from zero at its start to its duration at its
     * end, so a sample's height is the clip tick it played.
     */
    private static BakedAnimation rampingClip(LoopMode loopMode) {
        return bodyHeightClip(loopMode,
                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                new Keyframe<>(DURATION_TICKS, new Vec3(0.0, DURATION_TICKS, 0.0), Easing.LINEAR));
    }

    private static float bodyHeightAt(BakedAnimation baked, float elapsedTicks) {
        AnimationPose pose = new AnimationPose(RIG);
        baked.sampleInto(pose, elapsedTicks);
        return pose.get(RIG.indexOf(AnimationTargets.BODY_TRANSLATION), 1);
    }

    private static AnimationTrack<Vec3> translationTrack(AnimationTarget<Vec3> target, double x, double y, double z) {
        return AnimationTrack.<Vec3>builder()
                .target(target)
                .keyframes(List.of(new Keyframe<>(0, new Vec3(x, y, z), Easing.LINEAR)))
                .build();
    }

}
