package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationCompositorTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;

    private static final int HEAD_ROTATION = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int BODY_TRANSLATION = RIG.indexOf(AnimationTargets.BODY_TRANSLATION);
    private static final int MOUTH_SCALE = RIG.indexOf(AnimationTargets.MOUTH_SCALE);
    private static final int LEFT_EYELID = RIG.indexOf(AnimationTargets.EYELID_LEFT_TRANSLATION);
    private static final int LEFT_PUPIL_SCALE = RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);
    private static final int NOSE_ROTATION = RIG.indexOf(AnimationTargets.NOSE_ROTATION);
    private static final int UMBRELLA_LEFT_TRANSLATION = RIG.indexOf(SlotTargets.translation(UmbrellaSlot.UMBRELLA_LEFT));

    private static final float TOLERANCE = 1.0E-5F;

    // Include both endpoints to expose ownership changes when one alternative has no share
    private static final float[] CROSSFADE = {0.0F, 0.1F, 0.25F, 0.5F, 0.75F, 0.9F, 1.0F};

    @Test
    void applyContribution_replacesTheLowerValue_onAChannelTheContributionOwnsAndKeys() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(HEAD_ROTATION, 0.3F, -0.2F, 0.1F);
        contribution.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        assertChannel(pose, HEAD_ROTATION, 0.3F, -0.2F, 0.1F);
    }

    @Test
    void applyContribution_addsTheKeyedValue_onATranslationChannelTheContributionDoesNotOwn() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        float[] lower = components(pose, BODY_TRANSLATION);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(BODY_TRANSLATION, 0.5F, -1.0F, 0.25F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        assertChannel(pose, BODY_TRANSLATION, lower[0] + 0.5F, lower[1] - 1.0F, lower[2] + 0.25F);
    }

    @Test
    void applyContribution_multipliesByTheKeyedFactor_onAScaleChannelTheContributionDoesNotOwn() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        float[] lower = components(pose, MOUTH_SCALE);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(MOUTH_SCALE, 1.5F, 0.5F, 2.0F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        assertChannel(pose, MOUTH_SCALE, lower[0] * 1.5F, lower[1] * 0.5F, lower[2] * 2.0F);
    }

    @Test
    void applyContribution_pullsEveryOwnedChannelToRest_whenTheContributionDoesNotKeyIt() {
        // Arrange: the mouth mask exercises both zero rests and the unit rest for scale
        AnimationPose pose = lowerPose(1.0F);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.own(VillagerAnimationRig.MOUTH, 1.0F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        AnimationMask mouth = VillagerAnimationRig.MOUTH;
        for (int member = 0; member < mouth.size(); member++) {
            int channel = mouth.channelAt(member);
            float rest = RIG.kind(channel).getRest();
            assertChannel(pose, channel, rest, rest, rest);
        }
    }

    @Test
    void applyContribution_movesAnOwnedChannelPartway_byItsCoverage() {
        // Arrange
        float coverage = 0.25F;
        AnimationPose pose = lowerPose(1.0F);
        float[] lower = components(pose, HEAD_ROTATION);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(HEAD_ROTATION, 0.4F, 0.8F, -0.4F);
        contribution.own(VillagerAnimationRig.HEAD_ROTATION, coverage);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        assertChannel(pose, HEAD_ROTATION,
                lower[0] + coverage * (0.4F - lower[0]),
                lower[1] + coverage * (0.8F - lower[1]),
                lower[2] + coverage * (-0.4F - lower[2]));
    }

    @Test
    void applyContribution_passesEveryChannelThrough_whenTheContributionNeitherOwnsNorKeysIt() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(HEAD_ROTATION, 0.3F, 0.3F, 0.3F);
        contribution.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), 1.0F, contribution);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            if (channel != HEAD_ROTATION) {
                assertChannel(pose, channel, components(lower, channel));
            }
        }
    }

    @Test
    void applyContribution_scalesEveryEffect_bySlotWeight() {
        // Arrange
        float weight = 0.4F;
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(HEAD_ROTATION, 0.3F, 0.6F, -0.3F);
        contribution.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);
        contribution.own(VillagerAnimationRig.EYELIDS, 1.0F);
        contribution.key(BODY_TRANSLATION, 1.0F, -2.0F, 0.5F);
        contribution.key(MOUTH_SCALE, 1.5F, 0.5F, 2.0F);

        // Act
        AnimationCompositor.applyContribution(pose, new ComponentSuppression(), weight, contribution);

        // Assert
        float[] head = components(lower, HEAD_ROTATION);
        assertChannel(pose, HEAD_ROTATION,
                head[0] + weight * (0.3F - head[0]),
                head[1] + weight * (0.6F - head[1]),
                head[2] + weight * (-0.3F - head[2]));
        float[] eyelid = components(lower, LEFT_EYELID);
        assertChannel(pose, LEFT_EYELID,
                eyelid[0] * (1.0F - weight), eyelid[1] * (1.0F - weight), eyelid[2] * (1.0F - weight));
        float[] body = components(lower, BODY_TRANSLATION);
        assertChannel(pose, BODY_TRANSLATION,
                body[0] + weight * 1.0F, body[1] + weight * -2.0F, body[2] + weight * 0.5F);
        float[] mouth = components(lower, MOUTH_SCALE);
        assertChannel(pose, MOUTH_SCALE,
                mouth[0] * (1.0F + weight * 0.5F),
                mouth[1] * (1.0F + weight * -0.5F),
                mouth[2] * (1.0F + weight * 1.0F));
    }

    @Test
    void applyContribution_changesNothing_atZeroWeight() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(BODY_TRANSLATION, 1.0F, 1.0F, 1.0F);
        contribution.own(VillagerAnimationRig.FULL_BODY, 1.0F);
        contribution.suppress(SuppressibleComponent.BREATH, 1.0F);

        // Act
        AnimationCompositor.applyContribution(pose, suppression, 0.0F, contribution);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            assertChannel(pose, channel, components(lower, channel));
        }
        assertEquals(1.0F, suppression.remaining(SuppressibleComponent.BREATH), TOLERANCE);
    }

    @Test
    void applyContribution_changesNothing_atNaNWeight() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.key(BODY_TRANSLATION, 1.0F, 1.0F, 1.0F);
        contribution.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);
        contribution.suppress(SuppressibleComponent.BREATH, 1.0F);

        // Act
        AnimationCompositor.applyContribution(pose, suppression, Float.NaN, contribution);

        // Assert
        assertUnchanged(pose, lower, suppression);
    }

    @Test
    void applyCrossfade_changesNothing_atNaNWeight() {
        // Arrange
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution[] alternatives = {mixedOutgoing().toContribution(), mixedIncoming().toContribution()};

        // Act
        AnimationCompositor.applyCrossfade(pose, suppression, Float.NaN, alternatives, new float[]{0.5F, 0.5F}, 2);

        // Assert
        assertUnchanged(pose, lower, suppression);
    }

    @Test
    void applyCrossfade_leavesNoComponentBelowZero_whenSharesRoundPastOne() {
        // Arrange: model rounding error with shares whose sum is one float step above one
        AnimationContribution outgoing = new AnimationContribution(RIG);
        outgoing.suppress(SuppressibleComponent.BLINK, 1.0F);
        AnimationContribution incoming = new AnimationContribution(RIG);
        incoming.suppress(SuppressibleComponent.BLINK, 1.0F);
        float[] shares = {Math.nextUp(0.5F), Math.nextUp(0.5F)};
        ComponentSuppression suppression = new ComponentSuppression();

        // Act
        AnimationCompositor.applyCrossfade(lowerPose(1.0F), suppression, 1.0F,
                new AnimationContribution[]{outgoing, incoming}, shares, 2);

        // Assert
        float remaining = suppression.remaining(SuppressibleComponent.BLINK);
        assertTrue(remaining >= 0.0F, "remaining " + remaining);
    }

    @Test
    void applyContribution_suppressesNothing_whenAStrengthIsNaN() {
        // Arrange
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.suppress(SuppressibleComponent.BREATH, Float.NaN);

        // Act
        AnimationCompositor.applyContribution(lowerPose(1.0F), suppression, 1.0F, contribution);

        // Assert
        assertEquals(1.0F, suppression.remaining(SuppressibleComponent.BREATH), TOLERANCE);
    }

    @Test
    void applyContribution_combinesSuppressionAcrossSlots_asTheProductOfComplements() {
        // Arrange: vary slot weight and suppression strength independently to catch a missing factor
        float firstWeight = 0.5F;
        float secondStrength = 0.4F;
        float componentWeight = 0.8F;
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution first = new AnimationContribution(RIG);
        first.suppress(SuppressibleComponent.BREATH, 1.0F);
        AnimationContribution second = new AnimationContribution(RIG);
        second.suppress(SuppressibleComponent.BREATH, secondStrength);

        // Act
        AnimationCompositor.applyContribution(lowerPose(1.0F), suppression, firstWeight, first);
        AnimationCompositor.applyContribution(lowerPose(1.0F), suppression, 1.0F, second);

        // Assert
        float remaining = (1.0F - firstWeight) * (1.0F - secondStrength);
        assertEquals(remaining, suppression.remaining(SuppressibleComponent.BREATH), TOLERANCE);
        assertEquals(componentWeight * remaining,
                suppression.effectiveWeight(SuppressibleComponent.BREATH, componentWeight), TOLERANCE);
        assertEquals(1.0F, suppression.remaining(SuppressibleComponent.BLINK), TOLERANCE);
    }

    @Test
    void applyContribution_suppressesNothing_whenTheContributionOnlyOwnsChannels() {
        // Arrange
        ComponentSuppression suppression = new ComponentSuppression();
        AnimationContribution contribution = new AnimationContribution(RIG);
        contribution.own(VillagerAnimationRig.FULL_BODY, 1.0F);

        // Act
        AnimationCompositor.applyContribution(lowerPose(1.0F), suppression, 1.0F, contribution);

        // Assert
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            assertEquals(1.0F, suppression.remaining(component), TOLERANCE, component.name());
        }
    }

    @Test
    void applyContribution_ofAnExclusiveClip_restsUnkeyedPoseChannels_keepsSlotChannels_andSuppressesEveryComponent() {
        // Arrange
        KeyframeAnimation clip = KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/exclusive"))
                .durationTicks(10)
                .playExclusively()
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.HEAD_ROTATION)
                        .keyframes(List.of(new Keyframe<>(0, new Vector3f(0.2F, 0.0F, -0.1F), Easing.LINEAR)))
                        .build())
                .build();
        AnimationContribution contribution = new AnimationContribution(RIG);
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);
        AnimationPose pose = lowerPose(1.0F);
        AnimationPose lower = lowerPose(1.0F);
        ComponentSuppression suppression = new ComponentSuppression();

        // Act
        AnimationCompositor.applyContribution(pose, suppression, 1.0F, contribution);

        // Assert
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            float rest = RIG.kind(channel).getRest();
            if (channel == HEAD_ROTATION) {
                assertChannel(pose, channel, 0.2F, 0.0F, -0.1F);
            } else if (RIG.isSlotChannel(channel)) {
                assertChannel(pose, channel, components(lower, channel));
            } else {
                assertChannel(pose, channel, rest, rest, rest);
            }
        }
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            assertEquals(0.0F, suppression.remaining(component), TOLERANCE, component.name());
        }
    }

    @Test
    void applyCrossfade_letsNoLowerPoseThrough_whenBothAlternativesOwnTheChannel_atEveryShare() {
        // Arrange
        AnimationContribution outgoing = new AnimationContribution(RIG);
        outgoing.key(HEAD_ROTATION, 0.4F, -0.4F, 0.0F);
        outgoing.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);
        AnimationContribution incoming = new AnimationContribution(RIG);
        incoming.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);
        AnimationContribution[] alternatives = {outgoing, incoming};

        for (float incomingShare : CROSSFADE) {
            AnimationPose pose = lowerPose(1.0F);
            AnimationPose otherPose = lowerPose(3.0F);

            // Act
            float[] shares = {1.0F - incomingShare, incomingShare};
            AnimationCompositor.applyCrossfade(pose, new ComponentSuppression(), 1.0F, alternatives, shares, 2);
            AnimationCompositor.applyCrossfade(otherPose, new ComponentSuppression(), 1.0F, alternatives, shares, 2);

            // Assert: the incoming alternative contributes rest, so the lower pose cannot leak into the crossfade
            float outgoingShare = 1.0F - incomingShare;
            assertChannel(pose, HEAD_ROTATION, outgoingShare * 0.4F, outgoingShare * -0.4F, 0.0F);
            assertChannel(otherPose, HEAD_ROTATION, components(pose, HEAD_ROTATION));
        }
    }

    @Test
    void applyCrossfade_keepsAComponentFullySuppressed_whenBothAlternativesSuppressIt_atEveryShare() {
        // Arrange
        AnimationContribution outgoing = new AnimationContribution(RIG);
        outgoing.suppress(SuppressibleComponent.BLINK, 1.0F);
        AnimationContribution incoming = new AnimationContribution(RIG);
        incoming.suppress(SuppressibleComponent.BLINK, 1.0F);
        AnimationContribution[] alternatives = {outgoing, incoming};

        for (float incomingShare : CROSSFADE) {
            ComponentSuppression suppression = new ComponentSuppression();

            // Act
            AnimationCompositor.applyCrossfade(lowerPose(1.0F), suppression, 1.0F, alternatives,
                    new float[]{1.0F - incomingShare, incomingShare}, 2);

            // Assert
            assertEquals(0.0F, suppression.remaining(SuppressibleComponent.BLINK), TOLERANCE, "share " + incomingShare);
        }
    }

    @Test
    void applyCrossfade_givesTheSameResult_whicheverAlternativeIsListedFirst() {
        // Arrange
        AnimationContribution first = mixedOutgoing().toContribution();
        AnimationContribution second = mixedIncoming().toContribution();
        float weight = 0.7F;

        for (float incomingShare : CROSSFADE) {
            AnimationPose listedInOrder = lowerPose(1.0F);
            AnimationPose listedReversed = lowerPose(1.0F);
            ComponentSuppression inOrderSuppression = new ComponentSuppression();
            ComponentSuppression reversedSuppression = new ComponentSuppression();

            // Act
            AnimationCompositor.applyCrossfade(listedInOrder, inOrderSuppression, weight,
                    new AnimationContribution[]{first, second}, new float[]{1.0F - incomingShare, incomingShare}, 2);
            AnimationCompositor.applyCrossfade(listedReversed, reversedSuppression, weight,
                    new AnimationContribution[]{second, first}, new float[]{incomingShare, 1.0F - incomingShare}, 2);

            // Assert
            for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
                assertChannel(listedReversed, channel, components(listedInOrder, channel));
            }
            for (SuppressibleComponent component : SuppressibleComponent.values()) {
                assertEquals(inOrderSuppression.remaining(component), reversedSuppression.remaining(component),
                        TOLERANCE, component.name());
            }
        }
    }

    @Test
    void applyCrossfade_matchesTheLerpOfEachAlternativeAppliedAlone_forMixedOwnedAdditiveKeyedAndUnkeyedAlternatives() {
        // Arrange
        Alternative outgoing = mixedOutgoing();
        Alternative incoming = mixedIncoming();
        AnimationContribution[] alternatives = {outgoing.toContribution(), incoming.toContribution()};

        for (float weight : new float[]{0.3F, 1.0F}) {
            for (float incomingShare : CROSSFADE) {
                AnimationPose pose = lowerPose(1.0F);
                AnimationPose lower = lowerPose(1.0F);
                float outgoingShare = 1.0F - incomingShare;

                // Act
                AnimationCompositor.applyCrossfade(pose, new ComponentSuppression(), weight, alternatives,
                        new float[]{outgoingShare, incomingShare}, 2);

                // Assert: evaluate both alternatives from the same lower pose so neither gains priority
                for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
                    float[] expected = new float[AnimationPose.COMPONENTS];
                    for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                        float p = lower.get(channel, axis);
                        float blended = outgoingShare * outgoing.applyAlone(channel, axis, p)
                                + incomingShare * incoming.applyAlone(channel, axis, p);
                        expected[axis] = p + weight * (blended - p);
                    }
                    assertChannel(pose, channel, expected);
                }
            }
        }
    }

    @Test
    void applyCrossfade_blendsSuppressionByShare_withinOneSlot() {
        // Arrange
        float weight = 0.8F;
        float incomingShare = 0.25F;
        Alternative outgoing = mixedOutgoing();
        Alternative incoming = mixedIncoming();
        ComponentSuppression suppression = new ComponentSuppression();

        // Act
        AnimationCompositor.applyCrossfade(lowerPose(1.0F), suppression, weight,
                new AnimationContribution[]{outgoing.toContribution(), incoming.toContribution()},
                new float[]{1.0F - incomingShare, incomingShare}, 2);

        // Assert: averaging strengths distinguishes alternatives within one slot from independent suppressing slots
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            float slotSuppression = weight * ((1.0F - incomingShare) * outgoing.strength(component)
                    + incomingShare * incoming.strength(component));
            assertEquals(1.0F - slotSuppression, suppression.remaining(component), TOLERANCE, component.name());
        }
    }

    private static Alternative mixedOutgoing() {
        return new Alternative()
                .key(HEAD_ROTATION, 0.5F, -0.25F, 0.1F)
                .own(HEAD_ROTATION, 1.0F)
                // Leave this owned channel unkeyed to distinguish rest from the lower pose
                .own(LEFT_EYELID, 1.0F)
                .key(BODY_TRANSLATION, 1.0F, -0.5F, 0.0F)
                .key(MOUTH_SCALE, 1.5F, 0.75F, 1.0F)
                .key(LEFT_PUPIL_SCALE, 0.5F, 0.5F, 1.0F)
                .suppress(SuppressibleComponent.GAZE, 1.0F);
    }

    private static Alternative mixedIncoming() {
        return new Alternative()
                .key(HEAD_ROTATION, -0.2F, 0.2F, 0.0F)
                .key(BODY_TRANSLATION, 0.0F, 2.0F, -1.0F)
                .own(BODY_TRANSLATION, 0.5F)
                // Unkeyed scale ownership exercises rest at one rather than zero
                .own(MOUTH_SCALE, 1.0F)
                .key(LEFT_PUPIL_SCALE, 2.0F, 1.0F, 0.5F)
                .key(UMBRELLA_LEFT_TRANSLATION, 0.5F, 0.5F, 0.5F)
                .suppress(SuppressibleComponent.GAZE, 0.5F)
                .suppress(SuppressibleComponent.BLINK, 0.5F);
    }

    /**
     * Returns a pose with per-component offsets from rest scaled by spread.
     */
    private static AnimationPose lowerPose(float spread) {
        AnimationPose pose = new AnimationPose(RIG);
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            float rest = RIG.kind(channel).getRest();
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                pose.set(channel, axis, rest + spread * (0.05F * (channel + 1) + 0.01F * (axis + 1)));
            }
        }
        return pose;
    }

    private static void assertUnchanged(AnimationPose pose, AnimationPose lower, ComponentSuppression suppression) {
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            assertChannel(pose, channel, components(lower, channel));
        }
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            assertEquals(1.0F, suppression.remaining(component), TOLERANCE, component.name());
        }
    }

    private static float[] components(AnimationPose pose, int channel) {
        return new float[]{pose.get(channel, 0), pose.get(channel, 1), pose.get(channel, 2)};
    }

    private static void assertChannel(AnimationPose pose, int channel, float... expected) {
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(expected[axis], pose.get(channel, axis), TOLERANCE,
                    RIG.channel(channel).getId() + " axis " + axis);
        }
    }

    /**
     * Test-owned alternative description for building contributions and independent expected results.
     */
    private static final class Alternative {

        private final Map<Integer, float[]> keyed = new HashMap<>();
        private final Map<Integer, Float> coverage = new HashMap<>();
        private final Map<SuppressibleComponent, Float> strengths = new EnumMap<>(SuppressibleComponent.class);

        Alternative key(int channel, float x, float y, float z) {
            this.keyed.put(channel, new float[]{x, y, z});
            return this;
        }

        Alternative own(int channel, float channelCoverage) {
            this.coverage.put(channel, channelCoverage);
            return this;
        }

        Alternative suppress(SuppressibleComponent component, float strength) {
            this.strengths.put(component, strength);
            return this;
        }

        float strength(SuppressibleComponent component) {
            return this.strengths.getOrDefault(component, 0.0F);
        }

        AnimationContribution toContribution() {
            AnimationContribution contribution = new AnimationContribution(RIG);
            this.keyed.forEach((channel, value) -> contribution.key(channel, value[0], value[1], value[2]));
            this.coverage.forEach((channel, channelCoverage) ->
                    contribution.own(RIG.maskOf(List.of(RIG.channel(channel))), channelCoverage));
            this.strengths.forEach(contribution::suppress);
            return contribution;
        }

        /**
         * Returns the expected channel component after applying this alternative alone at full slot weight.
         */
        float applyAlone(int channel, int axis, float lower) {
            float[] value = this.keyed.get(channel);
            Float channelCoverage = this.coverage.get(channel);
            if (channelCoverage != null) {
                float target = value != null ? value[axis] : RIG.kind(channel).getRest();
                return lower + channelCoverage * (target - lower);
            }
            if (value == null) {
                return lower;
            }
            return RIG.kind(channel) == AnimationChannelKind.SCALE
                    ? lower * value[axis]
                    : lower + value[axis];
        }

    }

}
