package dev.breezes.settlements.domain.animation;

import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * One villager's schedule of windows separated by random delays, on whole ticks of the caller's clock.
 * At most one window is open at a time.
 */
final class RandomDelaySchedule {

    private static final long UNSCHEDULED = Long.MIN_VALUE;
    private static final long CLOSED = Long.MIN_VALUE;

    private final int minDelayTicks;
    private final int delayRangeTicks;
    private final int missedStartToleranceTicks;
    private final Random random;

    private long nextStartTick = UNSCHEDULED;
    private long windowStartTick = CLOSED;
    private int windowDurationTicks;

    /**
     * Creates a schedule that draws its first delay on its first update.
     *
     * @param minDelayTicks             the shortest delay before a window
     * @param delayRangeTicks           how much longer than the shortest delay a delay may be, drawn uniformly
     * @param missedStartToleranceTicks the longest a due window may go unobserved and still open
     * @param random                    supplies the delays; may be shared with other schedules
     * @throws IllegalArgumentException if a tick count is negative
     */
    RandomDelaySchedule(int minDelayTicks,
                        int delayRangeTicks,
                        int missedStartToleranceTicks,
                        @Nonnull Random random) {
        Validate.isTrue(minDelayTicks >= 0, "Minimum delay must be nonnegative: %s", minDelayTicks);
        Validate.isTrue(delayRangeTicks >= 0, "Delay range must be nonnegative: %s", delayRangeTicks);
        Validate.isTrue(missedStartToleranceTicks >= 0,
                "Missed-start tolerance must be nonnegative: %s", missedStartToleranceTicks);
        this.minDelayTicks = minDelayTicks;
        this.delayRangeTicks = delayRangeTicks;
        this.missedStartToleranceTicks = missedStartToleranceTicks;
        this.random = random;
    }

    /**
     * Advances to the given whole tick and returns whether a window opened at this update.
     * <p>
     * A delay begins at the first update and at each window's end. A window opens on its scheduled tick even when the
     * update reaches it late, and lasts the duration this update supplies. A window overdue by more than the
     * missed-start tolerance never opens; a fresh delay begins at the given tick instead.
     *
     * @param windowDurationTicks duration of a window opening at this update, nonnegative; not validated
     */
    boolean update(long tick, int windowDurationTicks) {
        if (this.windowStartTick != CLOSED) {
            long windowEndTick = this.windowStartTick + this.windowDurationTicks;
            if (tick <= windowEndTick) {
                return false;
            }

            this.windowStartTick = CLOSED;
            // Counted from the window's own end, so the frame that noticed it ending never moves the next one
            this.nextStartTick = windowEndTick + this.randomDelay();
        }

        if (this.nextStartTick == UNSCHEDULED || tick - this.nextStartTick > this.missedStartToleranceTicks) {
            this.nextStartTick = tick + this.randomDelay();
            return false;
        }

        if (tick < this.nextStartTick) {
            return false;
        }

        // Opened on its scheduled tick, so an update arriving late shows the window where it belongs
        this.windowStartTick = this.nextStartTick;
        this.windowDurationTicks = windowDurationTicks;
        return true;
    }

    /**
     * Whether the last window opened covers this sample: one has opened, and the sample is no later than its end. A
     * sample before its start counts as its start.
     */
    boolean isWindowOpenAt(long tick, float partialTicks) {
        return this.windowStartTick != CLOSED
                && this.windowElapsedTicks(tick, partialTicks) <= this.windowDurationTicks;
    }

    /**
     * Ticks from the open window's start to this sample, clamped at zero.
     * Meaningful only while isWindowOpenAt returns true.
     */
    float windowElapsedTicks(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.windowStartTick) + partialTicks);
    }

    private long randomDelay() {
        return this.minDelayTicks + this.random.nextInt(this.delayRangeTicks + 1);
    }

}
