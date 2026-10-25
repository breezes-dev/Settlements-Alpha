package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.time.ClockTicks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoseLagTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int NOSE = RIG.indexOf(AnimationTargets.NOSE_ROTATION);

    private static final long AGE_TICKS = 1_000L;

    // Keeps the lag below the roll bound so clamping cannot hide differences in follower behavior
    private static final float SMALL_TURN_RADIANS = 0.05F;

    @Test
    void roll_isTheAuthoredRoll_onceSettled() {
        // Arrange: the head already turned aside, with an authored roll on the nose
        Lagging nose = new Lagging();
        float authoredRoll = 0.1F;

        // Act
        float roll = nose.followHeadYaw(AGE_TICKS, 0.6F).rollOver(authoredRoll, 1.0F);

        // Assert: a lag measured from a heading of zero would roll the nose at the first sight of a turned head
        assertEquals(authoredRoll, roll, TOLERANCE);
    }

    @Test
    void roll_trailsATurnToTheRight_byRollingNegative() {
        // Arrange: a turn to the right raises the heading; a negative roll swings the nose tip to the left
        Lagging nose = new Lagging().followHeadYaw(AGE_TICKS, 0.0F);

        // Act
        float roll = nose.followHeadYaw(AGE_TICKS + 1, SMALL_TURN_RADIANS).rollOver(0.0F, 1.0F);

        // Assert: the other sign would swing the nose ahead of the turn
        assertTrue(roll < -TOLERANCE, "the nose did not trail the turn: " + roll);
    }

    @Test
    void roll_settlesBackToTheAuthoredRoll_withoutOvershoot_onceTheHeadHoldsStill() {
        // Arrange: a steady turn, then the head holding still for a second
        Lagging nose = new Lagging().followHeadYaw(AGE_TICKS, 0.0F);
        long age = AGE_TICKS;
        for (int tick = 1; tick <= 5; tick++) {
            nose.followHeadYaw(++age, tick * SMALL_TURN_RADIANS);
        }
        float heldHeading = 5 * SMALL_TURN_RADIANS;
        float rollAtTheStop = nose.rollOver(0.0F, 1.0F);
        float previousRoll = rollAtTheStop;

        for (int tick = 1; tick <= ClockTicks.TICKS_PER_SECOND; tick++) {
            // Act
            float roll = nose.followHeadYaw(++age, heldHeading).rollOver(0.0F, 1.0F);

            // Assert: detects overshoot, increasing lag, and a roll that fails to decay
            assertTrue(roll <= 0.0F, "the nose overshot neutral at tick " + tick);
            assertTrue(Math.abs(roll) <= Math.abs(previousRoll), "the roll grew back at tick " + tick);
            previousRoll = roll;
        }
        assertTrue(rollAtTheStop < -TOLERANCE, "the turn never rolled the nose");
        assertTrue(Math.abs(previousRoll) < Math.abs(rollAtTheStop) / 10.0F, "the nose never settled");
    }

    @Test
    void roll_snapsToTheAuthoredRoll_whenTheHeadJumpsPastATeleportSizedTurn() {
        // Arrange
        Lagging jumping = new Lagging().followHeadYaw(AGE_TICKS, 0.0F);
        Lagging turning = new Lagging().followHeadYaw(AGE_TICKS, 0.0F);

        // Act
        float jumpedRoll = jumping.followHeadYaw(AGE_TICKS + 1, ExponentialFollower.TELEPORT_TURN_RADIANS + 0.05F)
                .rollOver(0.0F, 1.0F);
        float turnedRoll = turning.followHeadYaw(AGE_TICKS + 1, ExponentialFollower.TELEPORT_TURN_RADIANS - 0.05F)
                .rollOver(0.0F, 1.0F);

        // Assert: without the snap, a jump rolls the nose to its bound
        assertEquals(0.0F, jumpedRoll, TOLERANCE);
        assertTrue(turnedRoll < -TOLERANCE, "a turn short of a teleport snapped");
    }

    @Test
    void roll_snapsToTheAuthoredRoll_whileTheHeadingIsDegenerate() {
        // Arrange: a vertical look has no reliable horizontal heading to follow
        Lagging nose = new Lagging().followHeadYaw(AGE_TICKS, 0.0F);
        AnimationPose straightDown = new AnimationPose(RIG);
        straightDown.set(HEAD, X, 90.0F * DEGREES_TO_RADIANS);
        straightDown.set(HEAD, Y, 0.5F);

        // Act
        float roll = nose.followHead(AGE_TICKS + 1, straightDown, false).rollOver(0.0F, 1.0F);

        // Assert
        assertEquals(0.0F, roll, TOLERANCE);
    }

    @Test
    void lagRoll_scalesWithTheNoseLagWeight_overTheAuthoredRoll() {
        // Arrange: three noses with one turn, applied fully, at half weight, and fully suppressed
        float authoredRoll = 0.05F;
        float full = turnedOneTick().rollOver(authoredRoll, 1.0F);
        float half = turnedOneTick().rollOver(authoredRoll, 0.5F);

        // Act
        float suppressed = turnedOneTick().rollOver(authoredRoll, 0.0F);

        // Assert: a lag ignoring suppression would roll a sleeping villager's nose
        assertEquals(authoredRoll, suppressed, TOLERANCE);
        assertEquals((full - authoredRoll) / 2.0F, half - authoredRoll, TOLERANCE);
        assertTrue(Math.abs(full - authoredRoll) > TOLERANCE, "the turn never rolled the nose");
    }

    @Test
    void finalRoll_staysWithinTheBound_authoredRollIncluded() {
        // Arrange: an authored roll near the bound that the lag rolls further, and one past the bound under no lag
        float nearTheBound = -0.9F * NoseLag.MAX_ROLL_RADIANS;
        float pastTheBound = 1.5F * NoseLag.MAX_ROLL_RADIANS;

        // Act
        float lagged = turnedPastTheBound().rollOver(nearTheBound, 1.0F);
        float settled = new Lagging().followHeadYaw(AGE_TICKS, 0.0F).rollOver(pastTheBound, 1.0F);

        // Assert: bounding only the lag's own roll would let the sum through
        assertEquals(-NoseLag.MAX_ROLL_RADIANS, lagged, TOLERANCE);
        assertEquals(NoseLag.MAX_ROLL_RADIANS, settled, TOLERANCE);
    }

    @Test
    void lagRoll_staysWithinTheBound_onItsOwn() {
        // Arrange: a lag far past the bound, over an authored roll leaning the other way
        float authoredRoll = 0.5F * NoseLag.MAX_ROLL_RADIANS;

        // Act
        float roll = turnedPastTheBound().rollOver(authoredRoll, 1.0F);

        // Assert: an unbounded lag would cancel the authored lean and pass the bound the other way
        assertEquals(authoredRoll - NoseLag.MAX_ROLL_RADIANS, roll, TOLERANCE);
    }

    @Test
    void roll_ofASteadyTurn_agreesAcrossFrameRates_withinHalf() {
        // Arrange: half a second of a steady turn. A blend factor fixed per frame lags several times less at the
        // highest frame rate than at the lowest.
        float turnRadiansPerTick = 0.05F;
        int durationTicks = ClockTicks.TICKS_PER_SECOND / 2;
        int[] framesPerSecond = {20, 60, 144};
        float[] rolls = new float[framesPerSecond.length];

        for (int rate = 0; rate < framesPerSecond.length; rate++) {
            Lagging nose = new Lagging();
            int frames = framesPerSecond[rate] * durationTicks / ClockTicks.TICKS_PER_SECOND;

            // Act
            for (int frame = 0; frame <= frames; frame++) {
                float ticks = (float) frame * durationTicks / frames;
                long wholeTicks = (long) Math.floor(ticks);
                nose.followHeadYawAt(AGE_TICKS + wholeTicks, ticks - wholeTicks, turnRadiansPerTick * ticks);
            }
            rolls[rate] = nose.rollOver(0.0F, 1.0F);
        }

        // Assert
        assertTrue(Math.abs(rolls[2]) < NoseLag.MAX_ROLL_RADIANS, "the turn saturated the roll");
        assertTrue(Math.abs(rolls[2]) > TOLERANCE, "the turn never rolled the nose");
        assertEquals(rolls[2], rolls[0], Math.abs(rolls[2]) / 2.0F, "20 fps");
        assertEquals(rolls[2], rolls[1], Math.abs(rolls[2]) / 2.0F, "60 fps");
    }

    @Test
    void roll_acrossTheHeadingWrap_matchesTheSameTurnAwayFromIt() {
        // Arrange: one turn of the head, with the body facing just short of the wrap or facing zero
        float shortOfTheWrapDegrees = 179.0F;
        float turn = 2.0F * DEGREES_TO_RADIANS;
        Lagging atTheWrap = new Lagging().followHeadYawUnderBody(AGE_TICKS, shortOfTheWrapDegrees, 0.0F);
        Lagging awayFromIt = new Lagging().followHeadYawUnderBody(AGE_TICKS, 0.0F, 0.0F);

        // Act
        float wrappedRoll = atTheWrap.followHeadYawUnderBody(AGE_TICKS + 1, shortOfTheWrapDegrees, turn)
                .rollOver(0.0F, 1.0F);
        float roll = awayFromIt.followHeadYawUnderBody(AGE_TICKS + 1, 0.0F, turn).rollOver(0.0F, 1.0F);

        // Assert: an unwrapped difference would read the step across the wrap as nearly a full turn
        assertTrue(Math.abs(roll) > TOLERANCE, "the turn never rolled the nose");
        assertEquals(roll, wrappedRoll, TOLERANCE);
    }

    @Test
    void roll_followsTheModelsOwnTurn_whetherOrNotTheVillagerIsUpsideDown() {
        // Arrange: the same model-space head turn, upright and upside down, where the world sees it mirrored
        Lagging upright = new Lagging().followHead(AGE_TICKS, headTurnedTo(0.0F), false);
        Lagging upsideDown = new Lagging().followHead(AGE_TICKS, headTurnedTo(0.0F), true);

        // Act
        float uprightRoll = upright.followHead(AGE_TICKS + 1, headTurnedTo(SMALL_TURN_RADIANS), false)
                .rollOver(0.0F, 1.0F);
        float upsideDownRoll = upsideDown.followHead(AGE_TICKS + 1, headTurnedTo(SMALL_TURN_RADIANS), true)
                .rollOver(0.0F, 1.0F);

        // Assert: leaving out the mirror once, in the heading or in the roll, swings the nose ahead of the turn
        assertTrue(Math.abs(uprightRoll) > TOLERANCE, "the turn never rolled the nose");
        assertEquals(uprightRoll, upsideDownRoll, TOLERANCE);
    }

    @Test
    void everyProductionClipsNoseRoll_isInsideTheBound() throws Exception {
        // Arrange: keys sit on whole ticks and no easing overshoots them, so whole ticks reach every authored extreme
        AnimationPose sample = new AnimationPose(RIG);
        int clipsRollingTheNose = 0;

        for (KeyframeAnimation clip : ProductionAnimationClips.all()) {
            if (ProductionAnimationClips.rigFor(clip) != RIG || !keysTheNoseRotation(clip)) {
                continue;
            }
            BakedAnimation baked = BakedAnimation.bake(clip, RIG);
            boolean rolls = false;
            for (int tick = 0; tick <= clip.getDurationTicks(); tick++) {
                // Act
                baked.sampleInto(sample, tick);
                float roll = sample.get(NOSE, Z);

                // Assert: a roll past the bound would be clamped in game rather than drawn as authored
                assertTrue(Math.abs(roll) <= NoseLag.MAX_ROLL_RADIANS,
                        clip.getId() + " rolls the nose " + roll + " at tick " + tick);
                rolls |= roll != 0.0F;
            }
            clipsRollingTheNose += rolls ? 1 : 0;
        }
        assertTrue(clipsRollingTheNose > 0, "Discovery found no clip rolling the nose");
    }

    private static boolean keysTheNoseRotation(KeyframeAnimation clip) {
        for (AnimationTrack<?> track : clip.getTracks()) {
            if (track.getTarget() == AnimationTargets.NOSE_ROTATION) {
                return true;
            }
        }
        return false;
    }

    private static Lagging turnedOneTick() {
        return new Lagging().followHeadYaw(AGE_TICKS, 0.0F).followHeadYaw(AGE_TICKS + 1, 4.0F * SMALL_TURN_RADIANS);
    }

    /**
     * Returns a nose whose turn saturates the lag's negative roll bound.
     */
    private static Lagging turnedPastTheBound() {
        Lagging nose = new Lagging().followHeadYaw(AGE_TICKS, 0.0F)
                .followHeadYaw(AGE_TICKS + 1, ExponentialFollower.TELEPORT_TURN_RADIANS - 0.05F);
        assertTrue(nose.rollOver(0.0F, 1.0F) <= -NoseLag.MAX_ROLL_RADIANS + TOLERANCE,
                "the turn must saturate the lag's roll");
        return nose;
    }

    private static AnimationPose headTurnedTo(float headYaw) {
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, Y, headYaw);
        return pose;
    }

    /**
     * A stateful nose-lag fixture that accepts head poses at explicit sample keys.
     */
    private static final class Lagging {

        private final NoseLag noseLag = new NoseLag();
        private final HeadFrame headFrame = new HeadFrame();
        private final FollowerStep step = new FollowerStep();
        private VillagerAnimationObservation observation = observation(AGE_TICKS, 0.0F, 0.0F, false);

        /**
         * Follows an upright head at the given yaw in radians, with zero body heading and partial tick.
         */
        Lagging followHeadYaw(long age, float headYaw) {
            return this.follow(age, 0.0F, 0.0F, headTurnedTo(headYaw), false);
        }

        Lagging followHeadYawAt(long age, float partialTicks, float headYaw) {
            return this.follow(age, partialTicks, 0.0F, headTurnedTo(headYaw), false);
        }

        Lagging followHeadYawUnderBody(long age, float bodyHeadingDegrees, float headYaw) {
            return this.follow(age, 0.0F, bodyHeadingDegrees, headTurnedTo(headYaw), false);
        }

        Lagging followHead(long age, AnimationPose head, boolean upsideDown) {
            return this.follow(age, 0.0F, 0.0F, head, upsideDown);
        }

        /**
         * Returns the final nose roll in radians after applying the weighted lag to the authored roll.
         */
        float rollOver(float authoredRoll, float weight) {
            AnimationPose pose = new AnimationPose(RIG);
            pose.set(NOSE, Z, authoredRoll);
            this.noseLag.apply(pose, weight, this.observation);
            return pose.get(NOSE, Z);
        }

        private Lagging follow(long age,
                               float partialTicks,
                               float bodyHeadingDegrees,
                               AnimationPose head,
                               boolean upsideDown) {
            this.observation = observation(age, partialTicks, bodyHeadingDegrees, upsideDown);
            this.step.measure(age, partialTicks, false);
            this.headFrame.compose(head, this.observation);
            this.noseLag.follow(this.headFrame, this.observation, this.step);
            return this;
        }

        private static VillagerAnimationObservation observation(long age,
                                                                float partialTicks,
                                                                float bodyHeadingDegrees,
                                                                boolean upsideDown) {
            return VillagerAnimationObservations.observation()
                    .ageTicks(age)
                    .partialTicks(partialTicks)
                    .bodyHeadingDegrees(bodyHeadingDegrees)
                    .upsideDown(upsideDown)
                    .navigationType(NavigationType.STROLL)
                    .motionArchetype(AnimationArchetype.NONE)
                    .selectionContext(AnimationSelectionContext.generic())
                    .build();
        }

    }

}
