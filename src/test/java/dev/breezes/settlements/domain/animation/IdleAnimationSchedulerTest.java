package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import dev.breezes.settlements.shared.util.RotationUtil;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleAnimationSchedulerTest {

    private static final long SEED = 11L;
    private static final int PLAYS = 50;
    private static final int SEEDS = 30;

    // Partway into a tick, so a sample reads fractional playback
    private static final float MID_TICK = 0.5F;

    private static final int SHORT_DURATION_TICKS = 2;
    private static final int LONG_DURATION_TICKS = 30;
    private static final int LONG_BLEND_TICKS = 6;

    @Test
    void nextIdleAnimation_isNeverTheOneThatJustPlayed_whileAnotherIsAvailable() {
        // Arrange: each play starts within one longest wait of the previous one retiring, which bounds the run
        List<BakedAnimation> idleAnimations = List.of(
                idleAnimation("first"), idleAnimation("second"), idleAnimation("third"));
        IdleAnimationScheduler scheduler = new IdleAnimationScheduler(idleAnimations, new Random(SEED));
        long longestPlayCycle = longestPlayCycle(idleAnimations.getFirst());
        BakedAnimation previous = null;
        BakedAnimation playing = null;
        int plays = 0;

        // Act, Assert
        for (long tick = 0; tick <= PLAYS * longestPlayCycle && plays < PLAYS; tick++) {
            scheduler.update(tick, true);
            BakedAnimation current = scheduler.current();
            if (current != null && playing == null) {
                assertNotSame(previous, current, "play " + plays + " repeated the previous idle animation");
                previous = current;
                plays++;
            }
            playing = current;
        }
        assertEquals(PLAYS, plays, "idle animations stopped starting");
    }

    @Test
    void firstIdleAnimation_canBeAnyOfThem() {
        // Arrange
        List<BakedAnimation> idleAnimations = List.of(
                idleAnimation("first"), idleAnimation("second"), idleAnimation("third"));
        long longestPlayCycle = longestPlayCycle(idleAnimations.getFirst());
        Set<BakedAnimation> playedFirst = new HashSet<>();

        // Act: one villager per seed, each until its first idle animation starts
        for (long seed = 0; seed < SEEDS; seed++) {
            IdleAnimationScheduler scheduler = new IdleAnimationScheduler(idleAnimations, new Random(seed));
            for (long tick = 0; tick <= longestPlayCycle && scheduler.current() == null; tick++) {
                scheduler.update(tick, true);
            }
            assertNotNull(scheduler.current(), "no idle animation started on seed " + seed);
            playedFirst.add(scheduler.current());
        }

        // Assert
        assertEquals(Set.copyOf(idleAnimations), playedFirst);
    }

    @Test
    void idleAnimation_neverPlays_withoutAnyToChooseFrom() {
        // Arrange
        IdleAnimationScheduler scheduler = new IdleAnimationScheduler(List.of(), new Random(SEED));

        // Act
        for (long tick = 0; tick <= IdleAnimationScheduler.MIN_DELAY_TICKS
                + IdleAnimationScheduler.DELAY_RANGE_TICKS + 1; tick++) {
            scheduler.update(tick, true);
        }

        // Assert
        assertNull(scheduler.current());
    }

    @Test
    void idleAnimation_dueWhileUnobserved_waitsAFreshInterval() {
        // Arrange: schedule the first wait, then go unobserved until it is overdue by more than the tolerance
        IdleAnimationScheduler scheduler = new IdleAnimationScheduler(List.of(idleAnimation("only")), new Random(SEED));
        scheduler.update(0L, true);
        long returnTick = IdleAnimationScheduler.MIN_DELAY_TICKS + IdleAnimationScheduler.DELAY_RANGE_TICKS
                + IdleAnimationScheduler.MISSED_START_TOLERANCE_TICKS + 1;
        boolean started = false;

        // Act: observe every tick for a whole shortest wait after returning
        for (long tick = returnTick; tick < returnTick + IdleAnimationScheduler.MIN_DELAY_TICKS; tick++) {
            scheduler.update(tick, true);
            started |= scheduler.current() != null;
        }

        // Assert
        assertFalse(started);
    }

    @Test
    void idleAnimation_dueBetweenSparseObservations_startsAtTheNextOne() {
        // Arrange: observations spaced by the tolerance always find a due start within it
        IdleAnimationScheduler scheduler = new IdleAnimationScheduler(List.of(idleAnimation("only")), new Random(SEED));
        long lastObservation = IdleAnimationScheduler.MIN_DELAY_TICKS + IdleAnimationScheduler.DELAY_RANGE_TICKS
                + IdleAnimationScheduler.MISSED_START_TOLERANCE_TICKS;
        boolean started = false;

        // Act
        for (long tick = 0; tick <= lastObservation && !started;
             tick += IdleAnimationScheduler.MISSED_START_TOLERANCE_TICKS) {
            scheduler.update(tick, true);
            started = scheduler.current() != null;
        }

        // Assert
        assertTrue(started);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 7})
    void idleAnimations_startAndEndOnTheSameTicks_whateverTheGapBetweenUpdates(int sparseGapTicks) {
        // Arrange: one scheduler updated every tick, one only every few ticks, on the same seed. Every gap stays inside
        // the missed-start tolerance, so neither has a start to reschedule.
        assertTrue(sparseGapTicks <= IdleAnimationScheduler.MISSED_START_TOLERANCE_TICKS);
        List<BakedAnimation> idleAnimations = List.of(longIdleAnimation("first"), longIdleAnimation("second"));
        IdleAnimationScheduler everyTick = new IdleAnimationScheduler(idleAnimations, new Random(SEED));
        IdleAnimationScheduler sparse = new IdleAnimationScheduler(idleAnimations, new Random(SEED));
        long span = 10L * longestPlayCycle(idleAnimations.getFirst());
        boolean sharedSamplePlayed = false;

        // Act, Assert: compare wherever both are sampled
        for (long tick = 0; tick <= span; tick++) {
            everyTick.update(tick, true);
            if (tick % sparseGapTicks != 0) {
                continue;
            }
            sparse.update(tick, true);
            float weight = everyTick.weight(tick, MID_TICK);
            assertEquals(weight, sparse.weight(tick, MID_TICK), 0.0F, "weight at tick " + tick);
            if (weight > 0.0F) {
                sharedSamplePlayed = true;
                assertEquals(everyTick.currentElapsedTicks(tick, MID_TICK), sparse.currentElapsedTicks(tick, MID_TICK),
                        0.0F, "playback at tick " + tick);
            }
        }
        assertTrue(sharedSamplePlayed, "no idle animation fell on a shared sample");
    }

    private static long longestPlayCycle(BakedAnimation clip) {
        return IdleAnimationScheduler.MIN_DELAY_TICKS + IdleAnimationScheduler.DELAY_RANGE_TICKS
                + clip.getDurationTicks() + clip.getBlendOutTicks() + 1;
    }

    private static BakedAnimation idleAnimation(String name) {
        KeyframeAnimation clip = KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(SHORT_DURATION_TICKS)
                .track(noseTurn())
                .build();
        return BakedAnimation.bake(clip, VillagerAnimationRig.RIG);
    }

    private static BakedAnimation longIdleAnimation(String name) {
        KeyframeAnimation clip = KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/long_" + name))
                .durationTicks(LONG_DURATION_TICKS)
                .blendInTicks(LONG_BLEND_TICKS)
                .blendOutTicks(LONG_BLEND_TICKS)
                .track(noseTurn())
                .build();
        return BakedAnimation.bake(clip, VillagerAnimationRig.RIG);
    }

    private static AnimationTrack<Vector3f> noseTurn() {
        return AnimationTrack.<Vector3f>builder()
                .target(AnimationTargets.NOSE_ROTATION)
                .keyframes(List.of(new Keyframe<>(0, RotationUtil.degrees(1.0F, 0.0F, 0.0F), Easing.LINEAR)))
                .build();
    }

}
