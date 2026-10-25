package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomDelayScheduleTest {

    private static final float TOLERANCE = 1.0E-6F;

    // Test-owned timing, so the arithmetic below is this test's own
    private static final int MIN_DELAY_TICKS = 10;
    private static final int DELAY_RANGE_TICKS = 20;
    private static final int MISSED_START_TOLERANCE_TICKS = 5;
    private static final int WINDOW_DURATION_TICKS = 4;
    private static final int DRAW = 7;
    private static final int DELAY_TICKS = MIN_DELAY_TICKS + DRAW;

    private static final long FIRST_UPDATE_TICK = 100L;
    private static final long SCHEDULED_TICK = FIRST_UPDATE_TICK + DELAY_TICKS;

    @Test
    void window_opensOnTheTickTheShortestDelayPlusTheDrawReaches_countedFromTheFirstUpdate() {
        // Arrange
        RandomDelaySchedule schedule = schedule(new FixedDrawRandom(DRAW));
        schedule.update(FIRST_UPDATE_TICK, WINDOW_DURATION_TICKS);
        long openedTick = Long.MIN_VALUE;

        // Act
        for (long tick = FIRST_UPDATE_TICK + 1; tick <= SCHEDULED_TICK + 1 && openedTick == Long.MIN_VALUE; tick++) {
            assertFalse(schedule.isWindowOpenAt(tick, 0.5F), "a window covered tick " + tick + " before it opened");
            if (schedule.update(tick, WINDOW_DURATION_TICKS)) {
                openedTick = tick;
            }
        }

        // Assert: a delay counted from tick zero, or one that ignores the draw, opens elsewhere
        assertEquals(SCHEDULED_TICK, openedTick);
        assertEquals(0.5F, schedule.windowElapsedTicks(SCHEDULED_TICK, 0.5F), TOLERANCE);
    }

    @Test
    void window_opensOnItsScheduledTick_whenItsUpdateArrivesLateWithinTheTolerance() {
        // Arrange
        RandomDelaySchedule schedule = schedule(new FixedDrawRandom(DRAW));
        schedule.update(FIRST_UPDATE_TICK, WINDOW_DURATION_TICKS);
        long lateTick = SCHEDULED_TICK + MISSED_START_TOLERANCE_TICKS;

        // Act
        boolean opened = schedule.update(lateTick, WINDOW_DURATION_TICKS);

        // Assert: a window opened at the late update would read no elapsed time yet
        assertTrue(opened);
        assertEquals(MISSED_START_TOLERANCE_TICKS + 0.25F, schedule.windowElapsedTicks(lateTick, 0.25F), TOLERANCE);
    }

    @Test
    void window_overduePastTheTolerance_neverOpens_andAFreshDelayBeginsAtThatUpdate() {
        // Arrange
        RandomDelaySchedule schedule = schedule(new FixedDrawRandom(DRAW));
        schedule.update(FIRST_UPDATE_TICK, WINDOW_DURATION_TICKS);
        long staleTick = SCHEDULED_TICK + MISSED_START_TOLERANCE_TICKS + 1;

        // Act
        boolean openedStale = schedule.update(staleTick, WINDOW_DURATION_TICKS);
        long openedTick = firstOpening(schedule, staleTick + 1, staleTick + DELAY_TICKS + 1);

        // Assert: opening the stale window replays it late, and rescheduling from its tick opens the next too early
        assertFalse(openedStale);
        assertEquals(staleTick + DELAY_TICKS, openedTick);
    }

    @Test
    void nextDelay_countsFromTheWindowsEnd_notFromTheUpdateThatFindsItEnded() {
        // Arrange: the window opens on time, and the next update comes a few ticks after it ends
        RandomDelaySchedule schedule = schedule(new FixedDrawRandom(DRAW));
        schedule.update(FIRST_UPDATE_TICK, WINDOW_DURATION_TICKS);
        schedule.update(SCHEDULED_TICK, WINDOW_DURATION_TICKS);
        long windowEndTick = SCHEDULED_TICK + WINDOW_DURATION_TICKS;
        long noticedTick = windowEndTick + 3;

        // Act
        boolean openedWhenNoticed = schedule.update(noticedTick, WINDOW_DURATION_TICKS);
        long openedTick = firstOpening(schedule, noticedTick + 1, noticedTick + DELAY_TICKS + 1);

        // Assert
        assertFalse(openedWhenNoticed);
        assertEquals(windowEndTick + DELAY_TICKS, openedTick);
    }

    @Test
    void window_lastsTheDurationItsOpeningUpdateSupplied() {
        // Arrange: later updates supply a longer duration, as for a different window to come
        RandomDelaySchedule schedule = schedule(new FixedDrawRandom(DRAW));
        schedule.update(FIRST_UPDATE_TICK, WINDOW_DURATION_TICKS);
        schedule.update(SCHEDULED_TICK, WINDOW_DURATION_TICKS);
        long windowEndTick = SCHEDULED_TICK + WINDOW_DURATION_TICKS;

        // Act
        for (long tick = SCHEDULED_TICK + 1; tick <= windowEndTick; tick++) {
            schedule.update(tick, 10 * WINDOW_DURATION_TICKS);
        }

        // Assert: a window reading each update's duration would still cover the sample past its own end
        assertTrue(schedule.isWindowOpenAt(windowEndTick, 0.0F));
        assertFalse(schedule.isWindowOpenAt(windowEndTick, 0.5F));
    }

    @Test
    void repeatedUpdatesAtOneTick_openOneWindow_andDrawOneDelay() {
        // Arrange: frames within one tick update with the same whole tick
        CountingRandom random = new CountingRandom();
        RandomDelaySchedule schedule = schedule(random);
        int framesPerTick = 7;
        int openings = 0;

        // Act
        for (long tick = FIRST_UPDATE_TICK; tick <= FIRST_UPDATE_TICK + MIN_DELAY_TICKS; tick++) {
            for (int frame = 0; frame < framesPerTick; frame++) {
                openings += schedule.update(tick, WINDOW_DURATION_TICKS) ? 1 : 0;
            }
        }

        // Assert: the draw of zero opens the window on the shortest delay; a draw per frame moves it with frame rate
        assertEquals(1, openings);
        assertEquals(1, random.draws);
    }

    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, -1, 0", "0, 0, -1"})
    void constructor_rejectsANegativeTickCount(int minDelayTicks, int delayRangeTicks, int toleranceTicks) {
        // Act, Assert: unchecked, a negative delay schedules windows in the past, and a negative range throws only
        // at the first draw, far from its cause
        assertThrows(IllegalArgumentException.class,
                () -> new RandomDelaySchedule(minDelayTicks, delayRangeTicks, toleranceTicks, new Random(1L)));
    }

    private static RandomDelaySchedule schedule(Random random) {
        return new RandomDelaySchedule(MIN_DELAY_TICKS, DELAY_RANGE_TICKS, MISSED_START_TOLERANCE_TICKS, random);
    }

    /**
     * Updates every tick from the first to the last, inclusive, and returns the tick a window opened on, or
     * Long.MIN_VALUE if none did.
     */
    private static long firstOpening(RandomDelaySchedule schedule, long firstTick, long lastTick) {
        for (long tick = firstTick; tick <= lastTick; tick++) {
            if (schedule.update(tick, WINDOW_DURATION_TICKS)) {
                return tick;
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * A random source whose integer draws all return the given value, capped below each requested bound.
     */
    private static final class FixedDrawRandom extends Random {

        private final int draw;

        private FixedDrawRandom(int draw) {
            super(0L);
            this.draw = draw;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(this.draw, bound - 1);
        }

    }

    /**
     * A random source that draws zero and counts its integer draws.
     */
    private static final class CountingRandom extends Random {

        private int draws;

        private CountingRandom() {
            super(0L);
        }

        @Override
        public int nextInt(int bound) {
            this.draws++;
            return 0;
        }

    }

}
