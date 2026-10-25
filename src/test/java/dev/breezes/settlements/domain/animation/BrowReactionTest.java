package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.time.ClockTicks;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowReactionTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int BROW_TRANSLATION = RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int BROW_ROTATION = RIG.indexOf(AnimationTargets.MONOBROW_ROTATION);

    private static final long AGE_TICKS = 1_000L;

    // Slight looks, so the reaction stays well inside the brow bounds and a test reads it unclamped
    private static final float SIDE_LOOK_DEGREES = 2.0F;
    private static final float VERTICAL_LOOK_DEGREES = 20.0F;

    @Test
    void sidewaysResidual_rollsUpTheBrowsEndOnThatSide_andRaisesTheBrowAlike() {
        // Act: a positive look yaw lies to the villager's right, and a positive roll raises the brow's right end
        Reacting right = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, 0.0F);
        Reacting left = new Reacting().followLook(AGE_TICKS, 0.0F, -SIDE_LOOK_DEGREES, 0.0F);

        // Assert: a flipped roll lowers the end the villager looks toward; a signed raise drops the brow one way
        assertTrue(right.roll() > TOLERANCE, "a look to the right did not raise the brow's right end: " + right.roll());
        assertEquals(-right.roll(), left.roll(), TOLERANCE);
        assertTrue(right.offset() < -TOLERANCE, "a sideways look did not raise the brow: " + right.offset());
        assertEquals(right.offset(), left.offset(), TOLERANCE);
    }

    @Test
    void verticalResidual_liftsTheBrowForALookAbove_andDropsItForOneBelow_withoutRolling() {
        // Act: a negative pitch looks up
        Reacting above = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, -VERTICAL_LOOK_DEGREES);
        Reacting below = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, VERTICAL_LOOK_DEGREES);

        // Assert: reading the residual's sign the wrong way round drops the brow for a look up
        assertTrue(above.offset() < -TOLERANCE, "a look above did not lift the brow: " + above.offset());
        assertTrue(below.offset() > TOLERANCE, "a look below did not drop the brow: " + below.offset());
        assertEquals(0.0F, above.roll(), TOLERANCE);
        assertEquals(0.0F, below.roll(), TOLERANCE);
    }

    @Test
    void riseAndReturn_eachLandTheSame_howeverTheirTicksSplitIntoFrames() {
        // Arrange: two ticks of rising toward a held residual, then four of returning; each frame is under the step
        // cap, so every interval is followed exactly
        int[] framesPerTick = {1, 3, 7};
        float[][] landed = new float[framesPerTick.length][];

        for (int rate = 0; rate < framesPerTick.length; rate++) {
            Reacting reacting = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, 0.0F);

            // Act
            followFrames(reacting, AGE_TICKS, 2, framesPerTick[rate], SIDE_LOOK_DEGREES);
            float risenRoll = reacting.roll();
            float risenOffset = reacting.offset();
            followFrames(reacting, AGE_TICKS + 2, 4, framesPerTick[rate], 0.0F);
            landed[rate] = new float[]{risenRoll, risenOffset, reacting.roll(), reacting.offset()};
        }

        // Assert: a blend fixed per frame closes more of the gap the more frames a tick holds
        float settledRoll = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, 0.0F).roll();
        assertTrue(Math.abs(landed[0][0]) < Math.abs(settledRoll) - TOLERANCE,
                "the rise settled, so it cannot tell frame rates apart");
        assertTrue(Math.abs(landed[0][2]) > TOLERANCE, "the return settled, so it cannot tell frame rates apart");
        for (int rate = 1; rate < framesPerTick.length; rate++) {
            for (int value = 0; value < landed[0].length; value++) {
                assertEquals(landed[0][value], landed[rate][value], TOLERANCE * Math.abs(landed[0][value]),
                        framesPerTick[rate] + " frames per tick, value " + value);
            }
        }
    }

    @Test
    void reset_landsAtOnceOnTheReactionAHeldResidualSettlesOn() {
        // Arrange: a reaction that followed a held residual for two seconds
        Reacting followed = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, 0.0F);
        for (int tick = 1; tick <= 2 * ClockTicks.TICKS_PER_SECOND; tick++) {
            followed.followLook(AGE_TICKS + tick, 0.0F, SIDE_LOOK_DEGREES, -VERTICAL_LOOK_DEGREES);
        }

        // Act: a first sample resets, as an observational evaluation's always does
        Reacting reset = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, -VERTICAL_LOOK_DEGREES);

        // Assert: a reset that eased in from neutral would show a preview's brow at rest
        assertTrue(Math.abs(reset.roll()) > TOLERANCE, "the residual never rolled the brow");
        assertEquals(followed.roll(), reset.roll(), TOLERANCE);
        assertEquals(followed.offset(), reset.offset(), TOLERANCE);
    }

    @Test
    void apply_addsTheReactionByItsWeight_overTheAuthoredBrow() {
        // Arrange
        Reacting reacting = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, 0.0F);
        float authoredOffset = -0.2F;
        float authoredRoll = 0.01F;

        // Act
        AnimationPose suppressed = reacting.browOver(authoredOffset, authoredRoll, 0.0F);
        AnimationPose halfWeight = reacting.browOver(authoredOffset, authoredRoll, 0.5F);

        // Assert: a reaction ignoring its weight would tilt a sleeping villager's brow
        assertEquals(authoredOffset, suppressed.get(BROW_TRANSLATION, Y), 0.0F);
        assertEquals(authoredRoll, suppressed.get(BROW_ROTATION, Z), 0.0F);
        assertEquals(authoredOffset + 0.5F * reacting.offset(), halfWeight.get(BROW_TRANSLATION, Y), TOLERANCE);
        assertEquals(authoredRoll + 0.5F * reacting.roll(), halfWeight.get(BROW_ROTATION, Z), TOLERANCE);
    }

    @Test
    void finalBrow_staysWithinItsBounds_authoredValuesIncluded_atAnyWeight() {
        // Arrange: authored values past every bound under no reaction
        Reacting still = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, 0.0F);

        // Act
        AnimationPose pastTheRaise = still.browOver(2.0F * BrowReaction.RAISED_OFFSET_LIMIT, 0.0F, 0.0F);
        AnimationPose pastTheDrop = still.browOver(2.0F * BrowReaction.LOWERED_OFFSET_LIMIT, 0.0F, 0.0F);
        AnimationPose pastTheRoll = still.browOver(0.0F, 1.5F * BrowReaction.MAX_ROLL_RADIANS, 0.0F);
        AnimationPose pastTheOtherRoll = still.browOver(0.0F, -1.5F * BrowReaction.MAX_ROLL_RADIANS, 0.0F);

        // Assert: bounding only the reaction, or only a weighted one, would draw the authored extremes unbounded
        assertEquals(BrowReaction.RAISED_OFFSET_LIMIT, pastTheRaise.get(BROW_TRANSLATION, Y), 0.0F);
        assertEquals(BrowReaction.LOWERED_OFFSET_LIMIT, pastTheDrop.get(BROW_TRANSLATION, Y), 0.0F);
        assertEquals(BrowReaction.MAX_ROLL_RADIANS, pastTheRoll.get(BROW_ROTATION, Z), 0.0F);
        assertEquals(-BrowReaction.MAX_ROLL_RADIANS, pastTheOtherRoll.get(BROW_ROTATION, Z), 0.0F);
    }

    @Test
    void proceduralRaise_stopsAtTheProceduralLimit_overANeutralAuthoredBrow() {
        // Arrange: a neutral authored brow under an ambient raise past the limit, one under an ambient raise just short
        // of it that a sideways reaction carries past, and one whose procedural raise stays well inside it
        Reacting still = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, 0.0F);
        Reacting raising = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, 0.0F);
        float shortOfTheLimit = BrowReaction.PROCEDURAL_RAISE_LIMIT - raising.offset() / 2.0F;
        float wellInside = BrowReaction.PROCEDURAL_RAISE_LIMIT / 4.0F;

        // Act
        float ambientAlone = still.browOverAmbient(0.0F, 2.0F * BrowReaction.PROCEDURAL_RAISE_LIMIT, 1.0F);
        float together = raising.browOverAmbient(0.0F, shortOfTheLimit, 1.0F);
        float inside = raising.browOverAmbient(0.0F, wellInside, 1.0F);

        // Assert: a limit applied to the reaction alone would let the ambient raise, or the two together, pass it
        assertTrue(raising.offset() < -TOLERANCE, "the sideways look did not raise the brow");
        assertEquals(BrowReaction.PROCEDURAL_RAISE_LIMIT, ambientAlone, 0.0F);
        assertEquals(BrowReaction.PROCEDURAL_RAISE_LIMIT, together, 0.0F);
        assertEquals(wellInside + raising.offset(), inside, TOLERANCE);
    }

    @Test
    void authoredRaisePastTheProceduralLimit_isKept_notRaisedFurther_butCanStillBeLowered() {
        // Arrange: an authored raise between the procedural limit and the final bound, under an ambient raise and a
        // raising reaction, and under a reaction lowering the brow
        float authoredRaise = (BrowReaction.PROCEDURAL_RAISE_LIMIT + BrowReaction.RAISED_OFFSET_LIMIT) / 2.0F;
        Reacting raising = new Reacting().followLook(AGE_TICKS, 0.0F, SIDE_LOOK_DEGREES, 0.0F);
        Reacting lowering = new Reacting().followLook(AGE_TICKS, 0.0F, 0.0F, VERTICAL_LOOK_DEGREES);

        // Act
        float raised = raising.browOverAmbient(authoredRaise, raising.offset(), 1.0F);
        float lowered = lowering.browOverAmbient(authoredRaise, 0.0F, 1.0F);

        // Assert: limiting against the procedural limit alone would pull the authored raise down to it; limiting the
        // lowering too would hold the lowered brow at the authored raise
        assertEquals(authoredRaise, raised, 0.0F);
        assertTrue(lowering.offset() > TOLERANCE, "the look below did not lower the brow");
        assertEquals(authoredRaise + lowering.offset(), lowered, TOLERANCE);
    }

    @Test
    void everyAmbientBrowClip_raisesTheBrowNoHigherThanTheProceduralLimit() throws Exception {
        // Arrange: the ambient clips that key the brow, discovered from their factory
        AnimationPose sample = new AnimationPose(RIG);
        int browClips = 0;

        for (Method method : AmbientAnimations.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || method.getParameterCount() != 0
                    || method.getReturnType() != KeyframeAnimation.class) {
                continue;
            }
            KeyframeAnimation clip = (KeyframeAnimation) method.invoke(null);
            if (!keysTheBrow(clip)) {
                continue;
            }
            browClips++;
            BakedAnimation baked = BakedAnimation.bake(clip, RIG);
            for (int tick = 0; tick <= clip.getDurationTicks(); tick++) {
                // Act
                sample.resetToRest();
                baked.sampleInto(sample, tick);

                // Assert: an ambient raise past the limit would be cut short in game rather than drawn as authored
                float offset = sample.get(BROW_TRANSLATION, Y);
                assertTrue(offset >= BrowReaction.PROCEDURAL_RAISE_LIMIT,
                        clip.getId() + " raises the brow to " + offset + " at tick " + tick);
            }
        }
        assertTrue(browClips > 0, "Discovery found no ambient clip keying the brow");
    }

    @Test
    void everyProductionClipsBrowOffsetAndRoll_isInsideTheBounds() throws Exception {
        // Arrange: keys sit on whole ticks and no easing overshoots them, so whole ticks reach every authored extreme
        AnimationPose sample = new AnimationPose(RIG);
        int clipsMovingTheBrow = 0;

        for (KeyframeAnimation clip : ProductionAnimationClips.all()) {
            if (ProductionAnimationClips.rigFor(clip) != RIG) {
                continue;
            }
            BakedAnimation baked = BakedAnimation.bake(clip, RIG);
            boolean moves = false;
            for (int tick = 0; tick <= clip.getDurationTicks(); tick++) {
                // Act
                sample.resetToRest();
                baked.sampleInto(sample, tick);
                float offset = sample.get(BROW_TRANSLATION, Y);
                float roll = sample.get(BROW_ROTATION, Z);

                // Assert: an authored brow past a bound would be clamped in game rather than drawn as authored
                assertTrue(offset >= BrowReaction.RAISED_OFFSET_LIMIT && offset <= BrowReaction.LOWERED_OFFSET_LIMIT,
                        clip.getId() + " moves the brow to " + offset + " at tick " + tick);
                assertTrue(Math.abs(roll) <= BrowReaction.MAX_ROLL_RADIANS,
                        clip.getId() + " rolls the brow " + roll + " at tick " + tick);
                moves |= offset != 0.0F || roll != 0.0F;
            }
            clipsMovingTheBrow += moves ? 1 : 0;
        }
        assertTrue(clipsMovingTheBrow > 0, "Discovery found no clip moving the brow");
    }

    /**
     * Follows the look at evenly spaced frames over the given whole ticks after the start age.
     */
    private static void followFrames(Reacting reacting, long startAge, int ticks, int framesPerTick,
                                     float lookYawDegrees) {
        for (int frame = 1; frame <= ticks * framesPerTick; frame++) {
            float elapsedTicks = (float) frame / framesPerTick;
            long wholeTicks = (long) Math.floor(elapsedTicks);
            reacting.followLook(startAge + wholeTicks, elapsedTicks - wholeTicks, lookYawDegrees, 0.0F);
        }
    }

    private static boolean keysTheBrow(KeyframeAnimation clip) {
        for (AnimationTrack<?> track : clip.getTracks()) {
            AnimationTarget<?> target = track.getTarget();
            if (RIG.contains(target) && VillagerAnimationRig.BROW.contains(RIG.indexOf(target))) {
                return true;
            }
        }
        return false;
    }

    private static VillagerAnimationObservation observation(float netHeadYawDegrees, float headPitchDegrees) {
        return VillagerAnimationObservations.observation()
                .netHeadYawDegrees(netHeadYawDegrees)
                .headPitchDegrees(headPitchDegrees)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic())
                .build();
    }

    /**
     * A stateful brow reaction fixture whose head is held straight ahead, so its look residual is the look itself.
     */
    private static final class Reacting {

        private final BrowReaction reaction = new BrowReaction();
        private final HeadFrame headFrame = new HeadFrame();
        private final FollowerStep step = new FollowerStep();
        private final AnimationPose heldHead = new AnimationPose(RIG);

        Reacting followLook(long age, float partialTicks, float lookYawDegrees, float lookPitchDegrees) {
            this.step.measure(age, partialTicks, false);
            this.headFrame.compose(this.heldHead, observation(lookYawDegrees, lookPitchDegrees));
            this.reaction.follow(this.headFrame, this.step);
            return this;
        }

        /**
         * Returns a pose holding the authored brow with the weighted reaction applied.
         */
        AnimationPose browOver(float authoredOffset, float authoredRoll, float weight) {
            AnimationPose pose = new AnimationPose(RIG);
            pose.set(BROW_TRANSLATION, Y, authoredOffset);
            pose.set(BROW_ROTATION, Z, authoredRoll);
            this.reaction.apply(pose, weight, authoredOffset);
            return pose;
        }

        /**
         * Returns the final vertical offset of an authored brow the ambient brow has moved by the given offset, with
         * the weighted reaction applied.
         */
        float browOverAmbient(float authoredOffset, float ambientOffset, float weight) {
            AnimationPose pose = new AnimationPose(RIG);
            pose.set(BROW_TRANSLATION, Y, authoredOffset + ambientOffset);
            this.reaction.apply(pose, weight, authoredOffset);
            return pose.get(BROW_TRANSLATION, Y);
        }

        float offset() {
            return this.browOver(0.0F, 0.0F, 1.0F).get(BROW_TRANSLATION, Y);
        }

        float roll() {
            return this.browOver(0.0F, 0.0F, 1.0F).get(BROW_ROTATION, Z);
        }

    }

}
