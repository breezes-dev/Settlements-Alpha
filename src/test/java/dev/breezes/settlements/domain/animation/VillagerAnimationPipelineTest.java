package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerAnimationPipelineTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180F;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final int NO_GATE_DELAY_TICKS = 0;

    // Far past the schedules' delay ranges, so a schedule counted from the age itself rather than from the first
    // observation would fire at once
    private static final long LONG_LIVED_AGE_TICKS = 5_000_000L;

    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float LOOK_PITCH_DEGREES = 10.0F;

    // Large enough against the tolerance that a proportional comparison is meaningful
    private static final float VISIBLE_SQUASH = 0.05F;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int NOSE = RIG.indexOf(AnimationTargets.NOSE_ROTATION);
    private static final int BROW_TRANSLATION = RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int BROW_ROTATION = RIG.indexOf(AnimationTargets.MONOBROW_ROTATION);
    private static final int LEFT_EYELID = RIG.indexOf(AnimationTargets.EYELID_LEFT_TRANSLATION);
    private static final int LEFT_PUPIL_SCALE = RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);
    private static final int LEFT_UMBRELLA_SLOT = RIG.indexOf(SlotTargets.translation(UmbrellaSlot.UMBRELLA_LEFT));

    private static final KeyframeAnimation PONDER = IdleAnimations.ponder();
    private static final KeyframeAnimation BLINK = AmbientAnimations.blink();
    private static final KeyframeAnimation WALK = LocomotionAnimations.walk();
    private static final KeyframeAnimation RUN = LocomotionAnimations.run();
    private static final KeyframeAnimation WAVE = WaveAnimations.wave();
    private static final KeyframeAnimation INTERACT = InteractAnimations.interact();
    private static final KeyframeAnimation FIGHT_FISH = FishingAnimations.fightFish();
    private static final KeyframeAnimation FISHING_LOOP = FishingAnimations.fishingLoop();
    private static final KeyframeAnimation CAST = FishingAnimations.cast();
    private static final KeyframeAnimation SLEEP = SleepingAnimations.sleeping();

    private static final AnimationSelectionContext GENERIC = AnimationSelectionContext.generic();
    private static final AnimationSelectionContext HOLDING_ROD = AnimationSelectionContext.of(ItemCategory.FISHING_ROD);
    // Selects a clip of its own for the wave, so a held-item change can resolve a playing action to another clip
    private static final AnimationSelectionContext HOLDING_AXE = AnimationSelectionContext.of(ItemCategory.AXE);

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), WAVE,
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.AXE), INTERACT,
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), INTERACT,
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.AXE), WAVE,
                    AnimationKey.of(AnimationArchetype.CAST, ItemCategory.FISHING_ROD), CAST,
                    AnimationKey.of(AnimationArchetype.REEL_OUT, ItemCategory.FISHING_ROD), FIGHT_FISH,
                    AnimationKey.of(AnimationArchetype.FISHING_WAIT, ItemCategory.FISHING_ROD), FISHING_LOOP,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SLEEP))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of(PONDER))
            .build();

    private static final AmbientAnimationLibrary AMBIENT_LIBRARY = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(AmbientAnimations.breathingCycle())
            .blink(BLINK)
            .browMotions(List.of())
            .build();

    private static final LocomotionAnimationLibrary LOCOMOTION = InMemoryLocomotionAnimationLibrary.builder()
            .standingPose(LocomotionAnimations.standingPose())
            .animations(Map.of(
                    NavigationType.STROLL, LocomotionAnimations.stroll(),
                    NavigationType.WALK, WALK,
                    NavigationType.RUN, RUN,
                    NavigationType.SPRINT, LocomotionAnimations.sprint()))
            .build();

    private static final BakedAnimationRegistry REGISTRY =
            BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION);

    // Bounds the first idle wait independently of the random draw
    private static final long LONGEST_FIRST_IDLE_WAIT = IdleAnimationScheduler.MIN_DELAY_TICKS
            + IdleAnimationScheduler.DELAY_RANGE_TICKS;
    private static final long LONGEST_FIRST_BLINK_WAIT = AmbientBlink.MIN_DELAY_TICKS + AmbientBlink.DELAY_RANGE_TICKS;

    private enum Interruption {
        MOVING,
        ACTING,
        SLEEPING
    }

    /**
     * Animation states whose continuity observational evaluation must preserve.
     */
    private enum LiveState {
        WALKING_IN_THE_RAIN,
        ACTION_CROSSFADE,
        IDLE_ANIMATION,
        BLINKING,
        ASLEEP
    }

    @Test
    void idleAnimation_startsOnlyAfterAFreshWait_countedFromTheFirstObservation() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long firstObservation = LONG_LIVED_AGE_TICKS;
        long firstTurnSeen = -1L;

        // Act
        for (long age = firstObservation; age <= firstObservation + LONGEST_FIRST_IDLE_WAIT
                + PONDER.getDurationTicks() && firstTurnSeen < 0L; age++) {
            step(pipeline, lookingAside(age).build(), snapshot);
            if (headLeftTheLook(snapshot)) {
                firstTurnSeen = age;
            }
        }

        // Assert
        assertTrue(firstTurnSeen >= 0L, "the idle animation never played");
        assertTrue(firstTurnSeen > firstObservation + IdleAnimationScheduler.MIN_DELAY_TICKS,
                "the idle animation played before a whole wait had passed: " + (firstTurnSeen - firstObservation));
    }

    @Test
    void idleAnimation_neverStarts_whileMoving() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        boolean headLeftTheLook = false;

        // Act: the stroll's head keys pitch only, so a turn away from the look yaw can only be the idle animation
        for (long age = LONG_LIVED_AGE_TICKS; age <= LONG_LIVED_AGE_TICKS + LONGEST_FIRST_IDLE_WAIT
                + PONDER.getDurationTicks(); age++) {
            step(pipeline, lookingAside(age)
                    .navigationType(NavigationType.STROLL)
                    .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                    .build(), snapshot);
            headLeftTheLook |= headLeftTheLook(snapshot);
        }

        // Assert
        assertFalse(headLeftTheLook);
    }

    @Test
    void idleAnimation_neverStarts_whileAnActionIsHeld() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        seenStanding(pipeline, snapshot, LONG_LIVED_AGE_TICKS - 1);
        boolean headLeftTheLook = false;

        // Act: the held action leaves the head alone, so a turn away from the look can only be the idle animation
        for (long age = LONG_LIVED_AGE_TICKS; age <= LONG_LIVED_AGE_TICKS + LONGEST_FIRST_IDLE_WAIT
                + PONDER.getDurationTicks(); age++) {
            step(pipeline, holdingInteract(lookingAside(age)).build(), snapshot);
            headLeftTheLook |= headLeftTheLook(snapshot);
        }

        // Assert
        assertFalse(headLeftTheLook);
    }

    @ParameterizedTest
    @EnumSource(Interruption.class)
    void idleAnimation_isCancelledByAnInterruption_andTheNextWaitsAFreshInterval(Interruption interruption) {
        // Arrange: stand still until the idle animation starts turning the head
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long age = LONG_LIVED_AGE_TICKS;
        step(pipeline, lookingAside(age).build(), snapshot);
        while (!headLeftTheLook(snapshot) && age < LONG_LIVED_AGE_TICKS + LONGEST_FIRST_IDLE_WAIT
                + PONDER.getDurationTicks()) {
            age++;
            step(pipeline, lookingAside(age).build(), snapshot);
        }
        assertTrue(headLeftTheLook(snapshot), "the idle animation never played");

        // Act: interrupt for longer than the idle animation's blend-out, then stand still again
        long interruptionEnd = age + PONDER.getBlendOutTicks() + 2L;
        for (age++; age < interruptionEnd; age++) {
            step(pipeline, interrupted(lookingAside(age), interruption).build(), snapshot);
        }
        boolean headLeftTheLookAfterInterruption = false;
        for (; age < interruptionEnd + IdleAnimationScheduler.MIN_DELAY_TICKS; age++) {
            step(pipeline, lookingAside(age).build(), snapshot);
            headLeftTheLookAfterInterruption |= headLeftTheLook(snapshot);
        }

        // Assert: an uncancelled idle animation would still be mid-turn, and a resumed wait would end too early
        assertFalse(headLeftTheLookAfterInterruption);
    }

    @Test
    void ponder_ownsTheHead_replacingTheLookWhateverItsYaw() {
        // Arrange: fixed draws align the ponders while the two villagers look aside by different amounts
        VillagerAnimationPipeline lookingSlightlyAside = pipeline();
        VillagerAnimationPipeline lookingFarAside = pipeline();
        VillagerAnimationSnapshot slightSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot farSnapshot = new VillagerAnimationSnapshot();
        float slightYaw = 10.0F;
        float farYaw = 40.0F;
        step(lookingSlightlyAside, lookingAside(LONG_LIVED_AGE_TICKS).netHeadYawDegrees(slightYaw).build(),
                slightSnapshot);
        step(lookingFarAside, lookingAside(LONG_LIVED_AGE_TICKS).netHeadYawDegrees(farYaw).build(), farSnapshot);
        boolean headsDifferedBeforePondering = !sameHead(slightSnapshot, farSnapshot);
        boolean pondered = false;
        boolean headsMatched = false;

        // Act
        for (long age = LONG_LIVED_AGE_TICKS + 1; age <= LONG_LIVED_AGE_TICKS + LONGEST_FIRST_IDLE_WAIT
                + PONDER.getDurationTicks() && !headsMatched; age++) {
            step(lookingSlightlyAside, lookingAside(age).netHeadYawDegrees(slightYaw).build(), slightSnapshot);
            step(lookingFarAside, lookingAside(age).netHeadYawDegrees(farYaw).build(), farSnapshot);
            pondered |= headLeftTheLook(slightSnapshot, slightYaw);
            headsMatched = sameHead(slightSnapshot, farSnapshot);
        }

        // Assert: only a head owner fully blended in leaves no trace of two different looks
        assertTrue(headsDifferedBeforePondering, "the look never reached the head");
        assertTrue(pondered, "the idle animation never played");
        assertTrue(headsMatched, "the idle animation added onto the look instead of replacing it");
    }

    @Test
    void breathing_isSuppressedInProportion_atAPartialGait() {
        // Arrange: find a sample where breathing visibly squashes the pupil at a standstill
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        float partialGaitAmount = VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT / 2.0F;
        long squashedSample = LONG_LIVED_AGE_TICKS;
        float standstillSquash = 0.0F;
        int cycleTicks = AmbientAnimations.breathingCycle().getDurationTicks();
        for (long age = LONG_LIVED_AGE_TICKS; age < LONG_LIVED_AGE_TICKS + cycleTicks
                && Math.abs(standstillSquash) < VISIBLE_SQUASH; age++) {
            squashedSample = age;
            pipeline.evaluateObservationally(lookingAside(age).build(), snapshot);
            standstillSquash = snapshot.getPose().get(LEFT_PUPIL_SCALE, Y) - 1.0F;
        }

        // Act: the stroll leaves the pupil's scale alone, so only breathing moves it
        pipeline.evaluateObservationally(lookingAside(squashedSample)
                .navigationType(NavigationType.STROLL)
                .limbSwingAmount(partialGaitAmount)
                .build(), snapshot);
        float partialGaitSquash = snapshot.getPose().get(LEFT_PUPIL_SCALE, Y) - 1.0F;

        // Assert: the gait's weight rises in proportion to the stride, so half the full-weight stride is half a gait
        assertTrue(Math.abs(standstillSquash) >= VISIBLE_SQUASH, "breathing never squashed the pupil");
        assertEquals(standstillSquash * 0.5F, partialGaitSquash, TOLERANCE);
    }

    @Test
    void breathing_keepsThePhaseItsRandomSourceDrew_cycleAfterCycle() {
        // Arrange: two villagers alike but for the breathing phase their random sources draw
        int drawnPhase = 7;
        VillagerAnimationPipeline drawsZero = pipeline();
        VillagerAnimationPipeline drawsPhase = new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY,
                AMBIENT_LIBRARY, LOCOMOTION, REGISTRY, new UmbrellaAnimator(NO_GATE_DELAY_TICKS),
                new FixedRandom(drawnPhase));
        VillagerAnimationSnapshot zeroSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot phaseSnapshot = new VillagerAnimationSnapshot();
        int cycleTicks = AMBIENT_LIBRARY.breathingCycle().getDurationTicks();
        boolean outOfStep = false;

        // Act, Assert: evaluated without updates, so only the standing pose, the look, and breathing move
        for (long age = LONG_LIVED_AGE_TICKS; age < LONG_LIVED_AGE_TICKS + 2L * cycleTicks; age++) {
            drawsPhase.evaluateObservationally(lookingAside(age).build(), phaseSnapshot);
            drawsZero.evaluateObservationally(lookingAside(age).build(), zeroSnapshot);
            outOfStep |= !samePose(zeroSnapshot.getPose(), phaseSnapshot.getPose());
            drawsZero.evaluateObservationally(lookingAside(age + drawnPhase).build(), zeroSnapshot);
            assertSameSnapshot(zeroSnapshot, phaseSnapshot);
        }
        assertTrue(outOfStep, "the two villagers breathed in step");
    }

    @Test
    void look_survivesAGait_withTheGaitsHeadKeysAddedOnTop() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        Vector3f gaitHead = clipVector(WALK, AnimationTargets.HEAD_ROTATION, 0.0F);

        // Act
        pipeline.evaluateObservationally(lookingAside(LONG_LIVED_AGE_TICKS)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .navigationType(NavigationType.WALK)
                .limbSwing(0.0F)
                .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                .build(), snapshot);

        // Assert
        AnimationPose pose = snapshot.getPose();
        assertEquals(LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS + gaitHead.x(), pose.get(HEAD, X), TOLERANCE);
        assertEquals(LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + gaitHead.y(), pose.get(HEAD, Y), TOLERANCE);
        assertEquals(gaitHead.z(), pose.get(HEAD, Z), TOLERANCE);
    }

    @Test
    void blink_atAStandstill_reproducesTheBlinkClip() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        int window = (int) (LONGEST_FIRST_BLINK_WAIT + BLINK.getDurationTicks() + 2);
        float[] eyelidHeights = new float[window];

        // Act
        for (int tick = 0; tick < window; tick++) {
            step(pipeline, lookingAside(LONG_LIVED_AGE_TICKS + tick).build(), snapshot);
            eyelidHeights[tick] = snapshot.getPose().get(LEFT_EYELID, Y);
        }

        // Assert: the blink starts one tick before its lids first leave rest, and then follows the clip throughout
        int firstClosingTick = 0;
        while (firstClosingTick < window && eyelidHeights[firstClosingTick] <= TOLERANCE) {
            firstClosingTick++;
        }
        assertTrue(firstClosingTick < window, "no blink happened");
        int blinkStart = firstClosingTick - 1;
        for (int elapsed = 0; elapsed <= BLINK.getDurationTicks(); elapsed++) {
            float authored = clipVector(BLINK, AnimationTargets.EYELID_LEFT_TRANSLATION, elapsed).y();
            assertEquals(authored, eyelidHeights[blinkStart + elapsed], TOLERANCE, "at blink tick " + elapsed);
        }
    }

    @Test
    void blink_closesTheEyesFully_overTheRunsRaisedLids() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        float closedHeight = Float.NEGATIVE_INFINITY;
        for (int elapsed = 0; elapsed <= BLINK.getDurationTicks(); elapsed++) {
            closedHeight = Math.max(closedHeight,
                    clipVector(BLINK, AnimationTargets.EYELID_LEFT_TRANSLATION, elapsed).y());
        }
        float raisedHeight = clipVector(RUN, AnimationTargets.EYELID_LEFT_TRANSLATION, 0.0F).y();
        float lowestHeight = Float.POSITIVE_INFINITY;
        float highestHeight = Float.NEGATIVE_INFINITY;

        // Act
        for (long tick = 0; tick <= LONGEST_FIRST_BLINK_WAIT + BLINK.getDurationTicks() + 1; tick++) {
            step(pipeline, lookingAside(LONG_LIVED_AGE_TICKS + tick)
                    .navigationType(NavigationType.RUN)
                    .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                    .build(), snapshot);
            float height = snapshot.getPose().get(LEFT_EYELID, Y);
            lowestHeight = Math.min(lowestHeight, height);
            highestHeight = Math.max(highestHeight, height);
        }

        // Assert
        assertEquals(raisedHeight, lowestHeight, TOLERANCE);
        assertEquals(closedHeight, highestHeight, TOLERANCE);
    }

    @Test
    void blink_isSuppressed_byAWideEyedAction() {
        // Arrange: fixed draws align the control's blinks with those suppressed by the fishing action
        VillagerAnimationPipeline fishing = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot fishingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long actionStart = LONG_LIVED_AGE_TICKS;
        boolean controlBlinked = false;
        boolean fishingFollowedItsClip = true;

        // Act
        for (long age = actionStart; age <= actionStart + 2L * (LONGEST_FIRST_BLINK_WAIT
                + BLINK.getDurationTicks() + 1); age++) {
            step(fishing, lookingAside(age)
                    .motionArchetype(AnimationArchetype.REEL_OUT)
                    .selectionContext(HOLDING_ROD)
                    .build(), fishingSnapshot);
            step(control, lookingAside(age).build(), controlSnapshot);
            long elapsed = age - actionStart;
            if (elapsed < FIGHT_FISH.getBlendInTicks()) {
                continue;
            }
            controlBlinked |= controlSnapshot.getPose().get(LEFT_EYELID, Y) > TOLERANCE;
            float authored = clipVector(FIGHT_FISH, AnimationTargets.EYELID_LEFT_TRANSLATION, elapsed).y();
            fishingFollowedItsClip &= Math.abs(fishingSnapshot.getPose().get(LEFT_EYELID, Y) - authored) <= TOLERANCE;
        }

        // Assert
        assertTrue(controlBlinked, "no blink fell inside the window");
        assertTrue(fishingFollowedItsClip, "a blink moved the wide-eyed action's lids");
    }

    @Test
    void sleep_snapsInAndOut_whileTheUmbrellaFinishesStowing() {
        // Arrange: deploy fully in the rain, holding an action that leaves the head alone so no idle animation starts
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long age = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, age - 1);
        long deployed = age + UmbrellaCarryAnimations.RAISE_DURATION_TICKS
                + UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK + UmbrellaAnimations.DEPLOY_DURATION_TICKS;
        for (; age <= deployed; age++) {
            step(pipeline, holdingInteract(lookingAside(age)).umbrellaRequested(true).build(), snapshot);
        }
        long stowTicks = Math.max(UmbrellaCarryAnimations.LOWER_DURATION_TICKS,
                UmbrellaCarryAnimations.LOWER_CANOPY_START_TICK + UmbrellaAnimations.UNDEPLOY_DURATION_TICKS);
        KeyframeAnimation lower = UmbrellaCarryAnimations.lower();

        // Act, Assert: asleep, the sleep pose holds the head from the first frame while the umbrella lowers
        long sleepStart = age;
        step(pipeline, asleepInTheRain(sleepStart), snapshot);
        Vector3f sleepHead = clipVector(SLEEP, AnimationTargets.HEAD_ROTATION, 0.0F);
        assertEquals(sleepHead.x(), snapshot.getPose().get(HEAD, X), TOLERANCE);
        assertEquals(sleepHead.y(), snapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertEquals(sleepHead.z(), snapshot.getPose().get(HEAD, Z), TOLERANCE);

        step(pipeline, asleepInTheRain(sleepStart + 1), snapshot);
        Vector3f lowering = clipVector(lower, SlotTargets.translation(UmbrellaSlot.UMBRELLA_LEFT), 1.0F);
        assertTrue(snapshot.isUmbrellaVisible());
        assertEquals(lowering.y(), snapshot.getPose().get(LEFT_UMBRELLA_SLOT, Y), TOLERANCE);

        for (age = sleepStart + 2; age <= sleepStart + stowTicks; age++) {
            step(pipeline, asleepInTheRain(age), snapshot);
        }
        assertFalse(snapshot.isUmbrellaVisible());

        // Act, Assert: awake, the look holds the head from the first frame
        step(pipeline, holdingInteract(lookingAside(age)).build(), snapshot);
        assertEquals(0.0F, snapshot.getPose().get(HEAD, X), TOLERANCE);
        assertEquals(LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, snapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertEquals(0.0F, snapshot.getPose().get(HEAD, Z), TOLERANCE);
    }

    @Test
    void waking_inTheRain_raisesTheUmbrellaAgain() {
        // Arrange: deploy in the rain, then sleep until the umbrella has stowed
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long age = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, age - 1);
        long deployed = age + UmbrellaCarryAnimations.RAISE_DURATION_TICKS;
        for (; age <= deployed; age++) {
            step(pipeline, holdingInteract(lookingAside(age)).umbrellaRequested(true).build(), snapshot);
        }
        long stowed = age + Math.max(UmbrellaCarryAnimations.LOWER_DURATION_TICKS,
                UmbrellaCarryAnimations.LOWER_CANOPY_START_TICK + UmbrellaAnimations.UNDEPLOY_DURATION_TICKS);
        for (; age <= stowed; age++) {
            step(pipeline, asleepInTheRain(age), snapshot);
        }
        assertFalse(snapshot.isUmbrellaVisible(), "the umbrella never stowed");

        // Act
        step(pipeline, holdingInteract(lookingAside(age)).umbrellaRequested(true).build(), snapshot);

        // Assert: a gate that sleep left shut would keep a villager waking in the rain without its umbrella
        assertTrue(snapshot.isUmbrellaVisible());
    }

    @Test
    void oneShotAction_doesNotReplay_afterWaking() {
        // Arrange: wave once, then fall asleep until the wave has long run out
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long waveStart = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, waveStart - 1);
        long wake = waveStart + WAVE.getDurationTicks() + WAVE.getBlendOutTicks() + 2L;
        for (long age = waveStart; age < wake; age++) {
            step(pipeline, waving(lookingAside(age)).sleeping(age > waveStart).build(), snapshot);
        }

        // Act
        step(pipeline, waving(lookingAside(wake)).build(), snapshot);

        // Assert: a replayed wave would hold the head and straighten an arm
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
        assertFalse(headLeftTheLook(snapshot));
    }

    @Test
    void triggeredAction_expiresAfterItsClip_whileASustainedActionHolds() {
        // Arrange: the same archetype, once on a new generation and once on the current one
        VillagerAnimationPipeline triggered = pipeline();
        VillagerAnimationPipeline sustained = pipeline();
        VillagerAnimationSnapshot triggeredSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot sustainedSnapshot = new VillagerAnimationSnapshot();
        long start = LONG_LIVED_AGE_TICKS;
        seenStanding(triggered, triggeredSnapshot, start - 1);
        seenStanding(sustained, sustainedSnapshot, start - 1);
        long pastTheClip = start + WAVE.getDurationTicks() + WAVE.getBlendOutTicks() + 2L;

        // Act
        for (long age = start; age <= pastTheClip; age++) {
            step(triggered, waving(lookingAside(age)).build(), triggeredSnapshot);
            step(sustained, lookingAside(age).motionArchetype(AnimationArchetype.WAVE).build(), sustainedSnapshot);
        }

        // Assert
        ArmConfiguration waveArms = ArmConfiguration.of(
                REGISTRY.baked(WAVE).leftArmClaimAt(0.0F), REGISTRY.baked(WAVE).rightArmClaimAt(0.0F));
        assertEquals(ArmConfiguration.BOTH_CROSSED, triggeredSnapshot.getArmConfiguration());
        assertEquals(waveArms, sustainedSnapshot.getArmConfiguration());
    }

    @Test
    void clearingAnAction_thatAlreadyExpired_leavesTheVillagerAsIfItNeverActed() {
        // Arrange: the wave plays once and expires while its archetype is still synced; the control never acts
        VillagerAnimationPipeline waved = pipeline();
        VillagerAnimationPipeline neverActs = pipeline();
        VillagerAnimationSnapshot wavedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot inactiveSnapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        long cleared = trigger + WAVE.getDurationTicks() + WAVE.getBlendOutTicks() + 2L;
        seenStanding(waved, wavedSnapshot, trigger - 1);
        seenStanding(neverActs, inactiveSnapshot, trigger - 1);
        boolean wavePlayed = false;
        for (long age = trigger; age < cleared; age++) {
            step(waved, waving(lookingAside(age)).build(), wavedSnapshot);
            step(neverActs, lookingAside(age).build(), inactiveSnapshot);
            wavePlayed |= !samePose(inactiveSnapshot.getPose(), wavedSnapshot.getPose());
        }
        assertTrue(wavePlayed, "the wave never played");

        // Act, Assert: a clear that reached for the expired action would throw, or replay the finished wave's fade-out
        for (long age = cleared; age <= cleared + WAVE.getBlendOutTicks() + 2L; age++) {
            step(waved, lookingAside(age).motionGeneration((byte) 1).build(), wavedSnapshot);
            step(neverActs, lookingAside(age).build(), inactiveSnapshot);
            assertSameSnapshotExceptFollowers(inactiveSnapshot, wavedSnapshot);
        }
    }

    @Test
    void actionReplacingAnotherMidPlay_crossfadesFromIt_overTheIncomingClipsBlendIn() {
        // Arrange: one villager interacts and then waves; the controls only interact, or only wave from the same tick
        VillagerAnimationPipeline replaced = pipeline();
        VillagerAnimationPipeline onlyInteracts = pipeline();
        VillagerAnimationPipeline onlyWaves = pipeline();
        VillagerAnimationSnapshot replacedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot interactSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot waveSnapshot = new VillagerAnimationSnapshot();
        long interactStart = LONG_LIVED_AGE_TICKS;
        long waveStart = interactStart + INTERACT.getBlendInTicks() + 1;
        long blendedIn = waveStart + WAVE.getBlendInTicks();
        assertTrue(waveStart < interactStart + INTERACT.getDurationTicks(), "the interact must still be playing");
        assertTrue(WAVE.getBlendInTicks() > 1, "the wave's blend-in must outlast a tick");
        seenStanding(replaced, replacedSnapshot, interactStart - 1);
        seenStanding(onlyInteracts, interactSnapshot, interactStart - 1);
        seenStanding(onlyWaves, waveSnapshot, interactStart - 1);
        for (long age = interactStart; age < waveStart; age++) {
            step(replaced, holdingInteract(lookingAside(age)).motionGeneration((byte) 1).build(), replacedSnapshot);
            step(onlyInteracts, holdingInteract(lookingAside(age)).motionGeneration((byte) 1).build(),
                    interactSnapshot);
            step(onlyWaves, lookingAside(age).build(), waveSnapshot);
        }
        boolean blendedBetweenThem = false;

        // Act, Assert: snapping to the incoming clip, or holding the outgoing one through the blend-in, would leave a
        // mid-crossfade sample on one of the two controls
        for (long age = waveStart; age <= blendedIn; age++) {
            step(replaced, waving(lookingAside(age)).motionGeneration((byte) 2).build(), replacedSnapshot);
            step(onlyInteracts, holdingInteract(lookingAside(age)).motionGeneration((byte) 1).build(),
                    interactSnapshot);
            step(onlyWaves, waving(lookingAside(age)).build(), waveSnapshot);
            if (age == waveStart) {
                assertSamePose(interactSnapshot.getPose(), replacedSnapshot.getPose());
            } else if (age < blendedIn) {
                blendedBetweenThem |= !samePose(interactSnapshot.getPose(), replacedSnapshot.getPose())
                        && !samePose(waveSnapshot.getPose(), replacedSnapshot.getPose());
            }
        }
        assertTrue(blendedBetweenThem, "no sample fell between the outgoing and the incoming clip");
        assertSameSnapshot(waveSnapshot, replacedSnapshot);
    }

    @Test
    void action_claimsItsArms_fromItsFirstTickUntilItExpires_whateverItsWeight() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, trigger - 1);
        assertTrue(WAVE.getBlendInTicks() > 0 && WAVE.getBlendOutTicks() > 1, "the wave must fade both ways");
        ArmConfiguration waveArms = ArmConfiguration.of(
                REGISTRY.baked(WAVE).leftArmClaimAt(0.0F), REGISTRY.baked(WAVE).rightArmClaimAt(0.0F));
        long fadingOut = trigger + WAVE.getDurationTicks() + WAVE.getBlendOutTicks() / 2;
        long expired = trigger + WAVE.getDurationTicks() + WAVE.getBlendOutTicks();

        // Act, Assert: claims that followed the action's weight would be missing at its first tick, where its weight is
        // still zero, and claims that ended with the clip would be gone while it fades out
        step(pipeline, waving(lookingAside(trigger)).build(), snapshot);
        assertEquals(waveArms, snapshot.getArmConfiguration(), "at the trigger");
        for (long age = trigger + 1; age <= fadingOut; age++) {
            step(pipeline, waving(lookingAside(age)).build(), snapshot);
        }
        assertEquals(waveArms, snapshot.getArmConfiguration(), "while fading out");
        for (long age = fadingOut + 1; age <= expired; age++) {
            step(pipeline, waving(lookingAside(age)).build(), snapshot);
        }
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration(), "once expired");
    }

    @Test
    void heldAction_whoseHeldItemArrivesAfterTheArchetype_playsTheItemsClipOnFromTheActionsStart() {
        // Arrange: the archetype arrives before the fishing rod does, so it first resolves to the empty clip
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long archetypeArrives = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, archetypeArrives - 1);
        step(pipeline, lookingAside(archetypeArrives)
                .motionArchetype(AnimationArchetype.FISHING_WAIT)
                .build(), snapshot);
        boolean lookedAsideBeforeTheRod = !headLeftTheLook(snapshot);

        // Act
        long rodArrives = archetypeArrives + 1;
        long settled = rodArrives + FISHING_LOOP.getBlendInTicks();
        for (long age = rodArrives; age <= settled; age++) {
            step(pipeline, lookingAside(age)
                    .motionArchetype(AnimationArchetype.FISHING_WAIT)
                    .selectionContext(HOLDING_ROD)
                    .build(), snapshot);
        }

        // Assert: once blended in, the fishing loop owns the head, played from when the archetype arrived
        Vector3f fishingHead = clipVector(FISHING_LOOP, AnimationTargets.HEAD_ROTATION, settled - archetypeArrives);
        assertTrue(lookedAsideBeforeTheRod);
        assertEquals(fishingHead.x(), snapshot.getPose().get(HEAD, X), TOLERANCE);
        assertEquals(fishingHead.y(), snapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertEquals(fishingHead.z(), snapshot.getPose().get(HEAD, Z), TOLERANCE);
    }

    @Test
    void oneShot_whoseHeldItemSelectsAnotherClipMidPlay_playsThatClipOnFromTheSameStart_andStillExpires() {
        // Arrange: the control holds the axe from the start, so it plays the axe's clip as a one-shot from there
        VillagerAnimationPipeline itemArrivesLate = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationPipeline neverActs = pipeline();
        VillagerAnimationSnapshot lateSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot inactiveSnapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        long axeArrives = trigger + 2;
        long pastTheClip = trigger + INTERACT.getDurationTicks() + INTERACT.getBlendOutTicks() + 2L;
        seenStanding(itemArrivesLate, lateSnapshot, trigger - 1);
        seenStanding(control, controlSnapshot, trigger - 1);
        seenStanding(neverActs, inactiveSnapshot, trigger - 1);
        boolean acted = false;

        // Act, Assert: a restart or a held clip would part the two once the axe has arrived
        for (long age = trigger; age <= pastTheClip; age++) {
            step(itemArrivesLate, waving(lookingAside(age))
                    .selectionContext(age >= axeArrives ? HOLDING_AXE : GENERIC)
                    .build(), lateSnapshot);
            step(control, waving(lookingAside(age)).selectionContext(HOLDING_AXE).build(), controlSnapshot);
            step(neverActs, lookingAside(age).selectionContext(HOLDING_AXE).build(), inactiveSnapshot);
            if (age >= axeArrives) {
                assertSameSnapshotExceptFollowers(controlSnapshot, lateSnapshot);
                acted |= !samePose(inactiveSnapshot.getPose(), lateSnapshot.getPose());
            }
        }
        assertTrue(acted, "the axe's clip never played");
        assertSameSnapshotExceptFollowers(inactiveSnapshot, lateSnapshot);
    }

    @Test
    void heldItemChange_thatResolvesTheSameClip_leavesThePlayingActionUntouched() {
        // Arrange: the wave has no fishing-rod clip of its own, so the rod resolves to the same generic wave
        VillagerAnimationPipeline itemChanges = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot changingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        long rodArrives = trigger + 2;
        seenStanding(itemChanges, changingSnapshot, trigger - 1);
        seenStanding(control, controlSnapshot, trigger - 1);

        // Act, Assert: re-pushing the same clip would restart it with a crossfade from itself
        for (long age = trigger; age <= trigger + WAVE.getDurationTicks() + WAVE.getBlendOutTicks() + 2L; age++) {
            step(itemChanges, waving(lookingAside(age))
                    .selectionContext(age >= rodArrives ? HOLDING_ROD : GENERIC)
                    .build(), changingSnapshot);
            step(control, waving(lookingAside(age)).build(), controlSnapshot);
            assertSameSnapshot(controlSnapshot, changingSnapshot);
        }
    }

    @Test
    void oneShot_whoseFirstClipExpiredBeforeItsHeldItem_playsTheItemsClipFromTheTriggersStart() {
        // Arrange: the cast has only a rod clip, so before the rod it resolves to the empty clip, which expires at once
        VillagerAnimationPipeline rodArrivesLate = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot lateSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        seenStanding(rodArrivesLate, lateSnapshot, trigger - 1);
        seenStanding(control, controlSnapshot, trigger - 1);
        step(rodArrivesLate, casting(lookingAside(trigger), GENERIC).build(), lateSnapshot);
        step(rodArrivesLate, casting(lookingAside(trigger).partialTicks(0.5F), GENERIC).build(), lateSnapshot);
        step(control, casting(lookingAside(trigger), HOLDING_ROD).build(), controlSnapshot);
        step(control, casting(lookingAside(trigger).partialTicks(0.5F), HOLDING_ROD).build(), controlSnapshot);
        boolean cast = false;

        // Act, Assert
        for (long age = trigger + 1; age <= trigger + CAST.getDurationTicks() + CAST.getBlendOutTicks() + 2L; age++) {
            step(rodArrivesLate, casting(lookingAside(age), HOLDING_ROD).build(), lateSnapshot);
            step(control, casting(lookingAside(age), HOLDING_ROD).build(), controlSnapshot);
            assertSameSnapshotExceptFollowers(controlSnapshot, lateSnapshot);
            cast |= headLeftTheLook(lateSnapshot);
        }
        assertTrue(cast, "the cast never played");
    }

    @Test
    void oneShot_thatPlayedItsOwnClipToTheEnd_isNotPlayedAgain_whenTheHeldItemChanges() {
        // Arrange: the interact plays its own clip to the end; the axe then selects the longer wave, whose duration
        // still covers the time since the trigger
        VillagerAnimationPipeline interacting = pipeline();
        VillagerAnimationPipeline neverActs = pipeline();
        VillagerAnimationSnapshot interactingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot inactiveSnapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        long axeArrives = trigger + INTERACT.getDurationTicks() + INTERACT.getBlendOutTicks() + 2L;
        assertTrue(axeArrives - trigger < WAVE.getDurationTicks(),
                "the wave must still cover the time since the trigger");
        seenStanding(interacting, interactingSnapshot, trigger - 1);
        seenStanding(neverActs, inactiveSnapshot, trigger - 1);

        // Act, Assert: playing the wave now would replay the tail of a clip that was never this action's
        for (long age = trigger; age <= axeArrives + WAVE.getBlendInTicks() + 2L; age++) {
            AnimationSelectionContext context = age >= axeArrives ? HOLDING_AXE : GENERIC;
            step(interacting, holdingInteract(lookingAside(age)).motionGeneration((byte) 1).selectionContext(context)
                    .build(), interactingSnapshot);
            step(neverActs, lookingAside(age).selectionContext(context).build(), inactiveSnapshot);
            if (age >= axeArrives) {
                assertSameSnapshot(inactiveSnapshot, interactingSnapshot);
            }
        }
    }

    @Test
    void oneShot_whoseHeldItemArrivesAfterItsClipWouldHaveEnded_playsNothing() {
        // Arrange
        VillagerAnimationPipeline rodArrivesTooLate = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        long trigger = LONG_LIVED_AGE_TICKS;
        long rodArrives = trigger + CAST.getDurationTicks() + 1;
        seenStanding(rodArrivesTooLate, snapshot, trigger - 1);
        boolean cast = false;

        // Act
        for (long age = trigger; age <= rodArrives + CAST.getBlendInTicks() + 2L; age++) {
            step(rodArrivesTooLate, casting(lookingAside(age), age >= rodArrives ? HOLDING_ROD : GENERIC)
                    .build(), snapshot);
            cast |= headLeftTheLook(snapshot);
        }

        // Assert: reviving it would replay a stale cast
        assertFalse(cast);
    }

    @Test
    void firstObservation_ofALoopingActionUnderWay_startsAtFullWeight() {
        // Arrange
        VillagerAnimationPipeline seenFishing = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        // Act
        step(seenFishing, fishingAfterACast(LONG_LIVED_AGE_TICKS, HOLDING_ROD), snapshot);

        // Assert: the loop owns the head, so at full weight the head is exactly its clip's, blend-in already past
        Vector3f fishingHead = clipVector(FISHING_LOOP, AnimationTargets.HEAD_ROTATION, FISHING_LOOP.getBlendInTicks());
        assertEquals(fishingHead.x(), snapshot.getPose().get(HEAD, X), TOLERANCE);
        assertEquals(fishingHead.y(), snapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertEquals(fishingHead.z(), snapshot.getPose().get(HEAD, Z), TOLERANCE);
    }

    @Test
    void firstObservation_ofALoopingActionUnderWay_holdsItAsIfHeldSinceItsBlendIn_untilItClears() {
        // Arrange: first seen fishing after a cast, the second action the generation counter has seen; the control
        // holds the same action from a blend-in earlier. Their blinks are scheduled from different first ticks.
        VillagerAnimationPipeline seenFishing = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot seenSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long firstSeen = LONG_LIVED_AGE_TICKS;
        long cleared = firstSeen + 3L * FISHING_LOOP.getDurationTicks();
        holdFishingFrom(control, controlSnapshot, firstSeen - FISHING_LOOP.getBlendInTicks(), firstSeen);
        boolean fished = false;

        // Act, Assert
        for (long age = firstSeen; age <= cleared + FISHING_LOOP.getBlendOutTicks() + 2L; age++) {
            AnimationArchetype archetype = age < cleared ? AnimationArchetype.FISHING_WAIT : AnimationArchetype.NONE;
            step(seenFishing, lookingAside(age).motionArchetype(archetype).motionGeneration((byte) 1)
                    .selectionContext(HOLDING_ROD).build(), seenSnapshot);
            step(control, lookingAside(age).motionArchetype(archetype).selectionContext(HOLDING_ROD).build(),
                    controlSnapshot);
            assertSameSnapshotExceptFollowers(controlSnapshot, seenSnapshot, VillagerAnimationRig.EYELIDS);
            fished |= headLeftTheLook(seenSnapshot);
        }
        assertTrue(fished, "the fishing loop never held");
        assertFalse(headLeftTheLook(seenSnapshot), "the fishing loop held past its clear");
    }

    @Test
    void firstObservation_ofALoopingActionBeforeItsHeldItem_holdsItOnceTheItemArrives() {
        // Arrange: first seen fishing before the rod has synced, so the action first resolves to the empty clip. The
        // control holds the action from a blend-in before the first observation.
        VillagerAnimationPipeline seenFishing = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot seenSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long firstSeen = LONG_LIVED_AGE_TICKS;
        holdFishingFrom(control, controlSnapshot, firstSeen - FISHING_LOOP.getBlendInTicks(), firstSeen + 1);
        step(seenFishing, fishingAfterACast(firstSeen, GENERIC), seenSnapshot);

        // Act, Assert
        for (long age = firstSeen + 1; age <= firstSeen + 2L * FISHING_LOOP.getDurationTicks(); age++) {
            step(seenFishing, fishingAfterACast(age, HOLDING_ROD), seenSnapshot);
            step(control, lookingAside(age).motionArchetype(AnimationArchetype.FISHING_WAIT)
                    .selectionContext(HOLDING_ROD).build(), controlSnapshot);
            assertSameSnapshotExceptFollowers(controlSnapshot, seenSnapshot, VillagerAnimationRig.EYELIDS);
        }
        assertTrue(headLeftTheLook(seenSnapshot), "the fishing loop never held");
    }

    @Test
    void firstObservation_ofAOneShotUnderWay_staysSkipped_whenItsHeldItemSelectsAnotherOneShot() {
        // Arrange: first seen mid-wave; the axe then selects the interact clip, which plays once as well
        VillagerAnimationPipeline seenWaving = pipeline();
        VillagerAnimationPipeline neverActs = pipeline();
        VillagerAnimationSnapshot seenSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot inactiveSnapshot = new VillagerAnimationSnapshot();
        long firstSeen = LONG_LIVED_AGE_TICKS;
        long axeArrives = firstSeen + 2;

        // Act, Assert
        for (long age = firstSeen; age <= axeArrives + INTERACT.getDurationTicks(); age++) {
            AnimationSelectionContext context = age >= axeArrives ? HOLDING_AXE : GENERIC;
            step(seenWaving, lookingAside(age).motionArchetype(AnimationArchetype.WAVE).motionGeneration((byte) 3)
                    .selectionContext(context).build(), seenSnapshot);
            step(neverActs, lookingAside(age).selectionContext(context).build(), inactiveSnapshot);
            assertSameSnapshot(inactiveSnapshot, seenSnapshot);
        }
    }

    @Test
    void firstObservation_ofAOneShotUnderWay_skipsIt_whileTheNextGenerationPlays() {
        // Arrange: first seen mid-wave; the control is first seen standing and never waves until the next trigger
        VillagerAnimationPipeline seenWaving = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot seenSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long firstSeen = LONG_LIVED_AGE_TICKS;
        long nextTrigger = firstSeen + WAVE.getDurationTicks();
        boolean wavedOnTheNextTrigger = false;

        // Act, Assert
        for (long age = firstSeen; age <= nextTrigger + WAVE.getBlendInTicks() + 2L; age++) {
            boolean triggeredAgain = age >= nextTrigger;
            step(seenWaving, lookingAside(age).motionArchetype(AnimationArchetype.WAVE)
                    .motionGeneration((byte) (triggeredAgain ? 4 : 3)).build(), seenSnapshot);
            step(control, lookingAside(age)
                    .motionArchetype(triggeredAgain ? AnimationArchetype.WAVE : AnimationArchetype.NONE)
                    .motionGeneration((byte) (triggeredAgain ? 1 : 0))
                    .build(), controlSnapshot);
            assertSameSnapshot(controlSnapshot, seenSnapshot);
            wavedOnTheNextTrigger |= triggeredAgain && headLeftTheLook(seenSnapshot);
        }
        assertTrue(wavedOnTheNextTrigger, "the next generation never played");
    }

    @Test
    void unhappyShake_addsOntoTheLook_ratherThanReplacingIt() {
        // Arrange
        VillagerAnimationPipeline lookingLevel = pipeline();
        VillagerAnimationPipeline lookingUp = pipeline();
        VillagerAnimationSnapshot levelSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot upSnapshot = new VillagerAnimationSnapshot();

        // Act
        step(lookingLevel, lookingAside(LONG_LIVED_AGE_TICKS).unhappy(true).build(), levelSnapshot);
        step(lookingUp, lookingAside(LONG_LIVED_AGE_TICKS).headPitchDegrees(LOOK_PITCH_DEGREES).unhappy(true).build(),
                upSnapshot);

        // Assert
        float levelPitch = levelSnapshot.getPose().get(HEAD, X);
        assertNotEquals(0.0F, levelPitch, TOLERANCE);
        float pitchDifference = upSnapshot.getPose().get(HEAD, X) - levelPitch;
        assertEquals(LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, pitchDifference, TOLERANCE);
        assertEquals(LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, upSnapshot.getPose().get(HEAD, Y), TOLERANCE);
    }

    @Test
    void unhappyShake_easesIn_ratherThanSnapping() {
        // Arrange: one villager turns unhappy while observed, the other is first seen already unhappy
        VillagerAnimationPipeline turningUnhappy = pipeline();
        VillagerAnimationPipeline alreadyUnhappy = pipeline();
        VillagerAnimationSnapshot turningSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot alreadySnapshot = new VillagerAnimationSnapshot();
        long unhappinessStarts = LONG_LIVED_AGE_TICKS + 1;
        long eased = unhappinessStarts + (long) Math.ceil(VillagerAnimationPipeline.UNHAPPY_EASE_TICKS);
        step(turningUnhappy, lookingAside(LONG_LIVED_AGE_TICKS).build(), turningSnapshot);

        // Act, Assert
        step(turningUnhappy, lookingAside(unhappinessStarts).unhappy(true).build(), turningSnapshot);
        assertFalse(headLeftTheLook(turningSnapshot), "the shake snapped in");
        assertEquals(0.0F, turningSnapshot.getPose().get(HEAD, X), TOLERANCE);

        for (long age = unhappinessStarts + 1; age <= eased; age++) {
            step(turningUnhappy, lookingAside(age).unhappy(true).build(), turningSnapshot);
        }
        step(alreadyUnhappy, lookingAside(eased).unhappy(true).build(), alreadySnapshot);
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(alreadySnapshot.getPose().get(HEAD, axis), turningSnapshot.getPose().get(HEAD, axis),
                    TOLERANCE);
        }
    }

    @Test
    void unhappyShake_stillEasesIn_afterANonFinitePartialTickAtTheFirstObservation() {
        // Arrange: the control's first observation is the same but for a finite partial tick
        VillagerAnimationPipeline firstSeenAtNaN = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot nanSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        step(firstSeenAtNaN, lookingAside(LONG_LIVED_AGE_TICKS).partialTicks(Float.NaN).build(), nanSnapshot);
        step(control, lookingAside(LONG_LIVED_AGE_TICKS).build(), controlSnapshot);
        long eased = LONG_LIVED_AGE_TICKS + 1 + (long) Math.ceil(VillagerAnimationPipeline.UNHAPPY_EASE_TICKS);

        // Act
        for (long age = LONG_LIVED_AGE_TICKS + 1; age <= eased; age++) {
            step(firstSeenAtNaN, lookingAside(age).unhappy(true).build(), nanSnapshot);
            step(control, lookingAside(age).unhappy(true).build(), controlSnapshot);
        }

        // Assert: an ease anchored on NaN would leave the shake out for the villager's whole life
        assertTrue(headLeftTheLook(controlSnapshot), "the control never shook");
        assertSameSnapshot(controlSnapshot, nanSnapshot);
    }

    @ParameterizedTest
    @EnumSource(LiveState.class)
    void evaluateObservationally_yieldsTheSameSnapshot_whenRepeatedAtOneSample(LiveState state) {
        // Arrange: an evaluation that retired a crossfade or moved a schedule would change the second result
        VillagerAnimationPipeline pipeline = pipeline();
        long age = driveInto(state, pipeline, new VillagerAnimationSnapshot());
        VillagerAnimationObservation next = continuing(state, age + 1);
        VillagerAnimationSnapshot first = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot repeated = new VillagerAnimationSnapshot();
        pipeline.advance(next, new VillagerAnimationSnapshot());

        // Act
        pipeline.evaluateObservationally(next, first);
        pipeline.evaluateObservationally(next, repeated);

        // Assert
        assertSameSnapshot(first, repeated);
    }

    @ParameterizedTest
    @EnumSource(LiveState.class)
    void evaluateObservationally_leavesNoTrace_onTheNextAdvance(LiveState state) {
        // Arrange: a future preview crosses lifecycle boundaries with conflicting inputs. Advancing both villagers
        // at the earlier live key then exposes state changes accidentally committed by the preview.
        VillagerAnimationPipeline observed = pipeline();
        VillagerAnimationPipeline untouched = pipeline();
        long age = driveInto(state, observed, new VillagerAnimationSnapshot());
        driveInto(state, untouched, new VillagerAnimationSnapshot());
        long pastTheLiveState = age + Math.max(WAVE.getBlendInTicks(), BLINK.getDurationTicks()) + 2L;
        VillagerAnimationObservation everythingChanged = lookingAside(pastTheLiveState)
                .netHeadYawDegrees(-LOOK_YAW_DEGREES)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .bodyHeadingDegrees(90.0F)
                .upsideDown(true)
                .navigationType(NavigationType.SPRINT)
                .limbSwing(7.0F)
                .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                .motionArchetype(AnimationArchetype.REEL_OUT)
                .motionGeneration((byte) 9)
                .selectionContext(HOLDING_ROD)
                .sleeping(state != LiveState.ASLEEP)
                .unhappy(true)
                .umbrellaRequested(state != LiveState.WALKING_IN_THE_RAIN)
                .build();
        VillagerAnimationObservation later = continuing(state, age + 1);
        VillagerAnimationSnapshot observedLater = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot untouchedLater = new VillagerAnimationSnapshot();

        // Act
        observed.evaluateObservationally(everythingChanged, new VillagerAnimationSnapshot());
        observed.advance(later, observedLater);
        untouched.advance(later, untouchedLater);

        // Assert
        assertSameSnapshot(untouchedLater, observedLater);
    }

    /**
     * Drives the pipeline into the state and returns the age of its last sample. The state is still live at the
     * next two ticks when they continue it.
     */
    private static long driveInto(LiveState state, VillagerAnimationPipeline pipeline,
                                  VillagerAnimationSnapshot snapshot) {
        long age = LONG_LIVED_AGE_TICKS;
        seenStanding(pipeline, snapshot, age);
        switch (state) {
            case WALKING_IN_THE_RAIN -> step(pipeline, walkingInTheRain(++age), snapshot);
            case ACTION_CROSSFADE -> {
                step(pipeline, holdingInteract(lookingAside(++age)).motionGeneration((byte) 1).build(), snapshot);
                // The wave's blend-in carries the crossfade from the interact past the next tick
                assertTrue(WAVE.getBlendInTicks() > 1, "the wave's blend-in must outlast a tick");
                step(pipeline, continuing(LiveState.ACTION_CROSSFADE, ++age), snapshot);
            }
            case IDLE_ANIMATION -> {
                while (!headLeftTheLook(snapshot)) {
                    assertTrue(age < LONG_LIVED_AGE_TICKS + LONGEST_FIRST_IDLE_WAIT + PONDER.getDurationTicks(),
                            "the idle animation never played");
                    step(pipeline, lookingAside(++age).build(), snapshot);
                }
            }
            case BLINKING -> {
                // A blink's lids leave rest on its second tick, so the next tick is still inside it
                while (snapshot.getPose().get(LEFT_EYELID, Y) <= TOLERANCE) {
                    assertTrue(age < LONG_LIVED_AGE_TICKS + LONGEST_FIRST_BLINK_WAIT + BLINK.getDurationTicks(),
                            "no blink happened");
                    step(pipeline, lookingAside(++age).build(), snapshot);
                }
            }
            case ASLEEP -> step(pipeline, asleepInTheRain(++age), snapshot);
        }
        return age;
    }

    /**
     * The observation at this age that keeps the state going.
     */
    private static VillagerAnimationObservation continuing(LiveState state, long age) {
        return switch (state) {
            case WALKING_IN_THE_RAIN -> walkingInTheRain(age);
            case ACTION_CROSSFADE -> waving(lookingAside(age)).motionGeneration((byte) 2).build();
            case IDLE_ANIMATION, BLINKING -> lookingAside(age).build();
            case ASLEEP -> asleepInTheRain(age);
        };
    }

    private static VillagerAnimationPipeline pipeline() {
        return new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION, REGISTRY,
                new UmbrellaAnimator(NO_GATE_DELAY_TICKS), new FixedRandom());
    }

    private static final class FixedRandom extends Random {

        private final int draw;

        private FixedRandom() {
            this(0);
        }

        /**
         * Creates a random source that repeats the given integer draw, capped below each requested bound.
         */
        private FixedRandom(int draw) {
            super(0L);
            this.draw = draw;
        }

        @Override
        public int nextInt(int bound) {
            // Different idle eligibility must not shift the cosmetic timing in paired comparisons
            return Math.min(this.draw, bound - 1);
        }
    }

    private static void step(VillagerAnimationPipeline pipeline,
                             VillagerAnimationObservation observation,
                             VillagerAnimationSnapshot snapshot) {
        pipeline.advance(observation, snapshot);
    }

    /**
     * Advances a standing observation at the given age, establishing a sample before any action starts.
     */
    private static void seenStanding(VillagerAnimationPipeline pipeline, VillagerAnimationSnapshot snapshot, long age) {
        step(pipeline, lookingAside(age).build(), snapshot);
    }

    /**
     * An awake, contented villager standing still with no action, looking level and to one side.
     */
    private static VillagerAnimationObservations.ObservationBuilder lookingAside(long age) {
        return VillagerAnimationObservations.observation()
                .ageTicks(age)
                .partialTicks(0.0F)
                .netHeadYawDegrees(LOOK_YAW_DEGREES)
                .headPitchDegrees(0.0F)
                .limbSwing(0.0F)
                .limbSwingAmount(0.0F)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .motionGeneration((byte) 0)
                .selectionContext(GENERIC)
                .sleeping(false)
                .unhappy(false)
                .umbrellaRequested(false);
    }

    /**
     * Asleep in the rain, still holding an action that leaves the head alone.
     */
    private static VillagerAnimationObservation asleepInTheRain(long age) {
        return holdingInteract(lookingAside(age)).umbrellaRequested(true).sleeping(true).build();
    }

    private static VillagerAnimationObservation walkingInTheRain(long age) {
        return lookingAside(age)
                .navigationType(NavigationType.WALK)
                .limbSwing(3.0F)
                .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT / 2.0F)
                .umbrellaRequested(true)
                .build();
    }

    /**
     * Returns a fishing-wait observation with a nonzero generation and the given item context.
     */
    private static VillagerAnimationObservation fishingAfterACast(long age, AnimationSelectionContext context) {
        return lookingAside(age)
                .motionArchetype(AnimationArchetype.FISHING_WAIT)
                .motionGeneration((byte) 1)
                .selectionContext(context)
                .build();
    }

    private static VillagerAnimationObservations.ObservationBuilder holdingInteract(
            VillagerAnimationObservations.ObservationBuilder builder) {
        return builder.motionArchetype(AnimationArchetype.INTERACT);
    }

    private static VillagerAnimationObservations.ObservationBuilder waving(
            VillagerAnimationObservations.ObservationBuilder builder) {
        return builder.motionArchetype(AnimationArchetype.WAVE).motionGeneration((byte) 1);
    }

    private static VillagerAnimationObservations.ObservationBuilder casting(
            VillagerAnimationObservations.ObservationBuilder builder,
            AnimationSelectionContext context) {
        return builder.motionArchetype(AnimationArchetype.CAST).motionGeneration((byte) 1).selectionContext(context);
    }

    private static VillagerAnimationObservations.ObservationBuilder interrupted(
            VillagerAnimationObservations.ObservationBuilder builder,
            Interruption interruption) {
        return switch (interruption) {
            case MOVING -> builder
                    .navigationType(NavigationType.STROLL)
                    .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT);
            case ACTING -> holdingInteract(builder);
            case SLEEPING -> builder.sleeping(true);
        };
    }

    /**
     * The clip's own value on the channel at this point in its playback.
     */
    private static Vector3f clipVector(KeyframeAnimation clip, AnimationTarget<?> channel, float elapsedTicks) {
        AnimationPose pose = new AnimationPose(RIG);
        BakedAnimation.bake(clip, RIG).sampleInto(pose, elapsedTicks);
        int index = RIG.indexOf(channel);
        return new Vector3f(pose.get(index, X), pose.get(index, Y), pose.get(index, Z));
    }

    private static boolean headLeftTheLook(VillagerAnimationSnapshot snapshot) {
        return headLeftTheLook(snapshot, LOOK_YAW_DEGREES);
    }

    /**
     * Returns whether head yaw differs from the supplied look yaw, or head roll differs from zero, beyond tolerance.
     */
    private static boolean headLeftTheLook(VillagerAnimationSnapshot snapshot, float lookYawDegrees) {
        AnimationPose pose = snapshot.getPose();
        return Math.abs(pose.get(HEAD, Y) - lookYawDegrees * DEGREES_TO_RADIANS) > TOLERANCE
                || Math.abs(pose.get(HEAD, Z)) > TOLERANCE;
    }

    private static boolean sameHead(VillagerAnimationSnapshot first, VillagerAnimationSnapshot second) {
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            if (Math.abs(first.getPose().get(HEAD, axis) - second.getPose().get(HEAD, axis)) > TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static boolean samePose(AnimationPose first, AnimationPose second) {
        for (int channel = 0; channel < first.getRig().getChannelCount(); channel++) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                if (first.get(channel, axis) != second.get(channel, axis)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Shows the pipeline the villager standing at the start age, then holding the fishing loop from that age until
     * just before the end age.
     */
    private static void holdFishingFrom(VillagerAnimationPipeline pipeline,
                                        VillagerAnimationSnapshot snapshot,
                                        long startAge,
                                        long endAge) {
        seenStanding(pipeline, snapshot, startAge);
        for (long age = startAge; age < endAge; age++) {
            step(pipeline, lookingAside(age).motionArchetype(AnimationArchetype.FISHING_WAIT)
                    .selectionContext(HOLDING_ROD).build(), snapshot);
        }
    }

    /**
     * Asserts snapshot equality except on the excluded channels, the villager's nose roll, and its brow's vertical
     * offset and roll.
     */
    private static void assertSameSnapshotExceptFollowers(VillagerAnimationSnapshot expected,
                                                          VillagerAnimationSnapshot actual,
                                                          AnimationMask... excluded) {
        assertEquals(expected.getArmConfiguration(), actual.getArmConfiguration());
        assertEquals(expected.isUmbrellaVisible(), actual.isUmbrellaVisible());
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            if (inAny(excluded, channel)) {
                continue;
            }
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                if ((channel == NOSE && axis == Z) || (channel == BROW_TRANSLATION && axis == Y)
                        || (channel == BROW_ROTATION && axis == Z)) {
                    // Matching current poses can retain different nose lags and brow reactions from earlier head
                    // motion
                    continue;
                }
                assertEquals(expected.getPose().get(channel, axis), actual.getPose().get(channel, axis), 0.0F,
                        RIG.channel(channel).getId() + " axis " + axis);
            }
        }
        assertSamePose(expected.getUmbrellaPose(), actual.getUmbrellaPose());
    }

    private static boolean inAny(AnimationMask[] masks, int channel) {
        for (AnimationMask mask : masks) {
            if (mask.contains(channel)) {
                return true;
            }
        }
        return false;
    }

    private static void assertSameSnapshot(VillagerAnimationSnapshot expected, VillagerAnimationSnapshot actual) {
        assertEquals(expected.getArmConfiguration(), actual.getArmConfiguration());
        assertEquals(expected.isUmbrellaVisible(), actual.isUmbrellaVisible());
        assertSamePose(expected.getPose(), actual.getPose());
        assertSamePose(expected.getUmbrellaPose(), actual.getUmbrellaPose());
    }

    private static void assertSamePose(AnimationPose expected, AnimationPose actual) {
        for (int channel = 0; channel < expected.getRig().getChannelCount(); channel++) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(expected.get(channel, axis), actual.get(channel, axis), 0.0F,
                        expected.getRig().channel(channel).getId() + " axis " + axis);
            }
        }
    }

}
