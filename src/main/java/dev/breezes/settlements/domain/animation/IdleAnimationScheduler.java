package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Schedules one villager's idle animations.
 * <p>
 * An idle animation plays only while the villager stays eligible, and the same one never plays twice
 * in a row while another is available. Idle animations start and end on whole ticks of the caller's clock.
 * <p>
 * Updates and reads allocate no objects.
 */
final class IdleAnimationScheduler {

    /**
     * The shortest wait before the next idle animation, counted from the end of the last one, or from the first
     * eligible update when none has just ended.
     */
    static final int MIN_DELAY_TICKS = ClockTicks.seconds(30).getTicksAsInt();

    /**
     * How much longer than the shortest wait the next idle animation may wait, drawn uniformly.
     */
    static final int DELAY_RANGE_TICKS = ClockTicks.seconds(45).getTicksAsInt();

    /**
     * The longest a due idle animation may go unobserved and still start. One found later than this waits a fresh
     * interval, so villagers coming back into view do not start together.
     */
    static final int MISSED_START_TOLERANCE_TICKS = ClockTicks.seconds(1).getTicksAsInt();

    private static final long UNSCHEDULED = Long.MIN_VALUE;
    private static final long NOT_CANCELLING = Long.MIN_VALUE;
    private static final int NONE_PLAYED = -1;

    private final List<BakedAnimation> idleAnimations;
    private final Random random;

    private long nextStartTick = UNSCHEDULED;
    @Nullable
    private BakedAnimation current;
    private long currentStartTick;
    private long cancelStartTick = NOT_CANCELLING;
    private int lastIndex = NONE_PLAYED;

    /**
     * Creates a scheduler that waits for its first eligible update. With no idle animations it never plays one.
     */
    IdleAnimationScheduler(@Nonnull List<BakedAnimation> idleAnimations, @Nonnull Random random) {
        this.idleAnimations = List.copyOf(idleAnimations);
        this.random = random;
    }

    /**
     * Applies one observation at a whole tick. While eligible with none playing, the next idle animation starts on its
     * scheduled tick once that tick has passed. Losing eligibility fades the current one out over its blend-out and
     * cancels it entirely, and clears any pending wait, so the next waits a fresh interval once eligibility returns.
     * One due longer ago than the missed-start tolerance waits a fresh interval from this tick instead of starting.
     */
    void update(long tick, boolean eligible) {
        if (this.idleAnimations.isEmpty()) {
            return;
        }
        BakedAnimation playing = this.current;
        if (playing != null) {
            if (!eligible && this.cancelStartTick == NOT_CANCELLING) {
                this.cancelStartTick = tick;
            }
            long endTick = this.currentEndTick(playing);
            if (tick <= endTick) {
                return;
            }
            this.current = null;
            this.cancelStartTick = NOT_CANCELLING;
            // Counted from the idle animation's own end, so the frame that noticed it ending never moves the next one
            this.nextStartTick = eligible ? endTick + this.randomDelay() : UNSCHEDULED;
        }

        if (!eligible) {
            this.nextStartTick = UNSCHEDULED;
        } else if (this.nextStartTick == UNSCHEDULED || tick - this.nextStartTick > MISSED_START_TOLERANCE_TICKS) {
            this.nextStartTick = tick + this.randomDelay();
        } else if (tick >= this.nextStartTick) {
            // Started on its scheduled tick, so an update arriving late shows the idle animation where it belongs
            this.startNext(this.nextStartTick);
        }
    }

    /**
     * The idle animation playing or fading out, or null when none is.
     */
    @Nullable
    BakedAnimation current() {
        return this.current;
    }

    /**
     * Playback ticks since the current idle animation started, clamped at zero. Meaningful only while one plays.
     */
    float currentElapsedTicks(long tick, float partialTicks) {
        return elapsedSince(this.currentStartTick, tick, partialTicks);
    }

    /**
     * The current idle animation's weight in [0, 1]: its blend in and out multiplied by its cancel fade, or zero
     * when none plays.
     */
    float weight(long tick, float partialTicks) {
        BakedAnimation animation = this.current;
        if (animation == null) {
            return 0.0F;
        }
        return playbackWeight(animation, this.currentElapsedTicks(tick, partialTicks))
                * this.cancelWeight(animation, tick, partialTicks);
    }

    private void startNext(long startTick) {
        int index = this.nextIndex();
        this.current = this.idleAnimations.get(index);
        this.lastIndex = index;
        this.currentStartTick = startTick;
        this.cancelStartTick = NOT_CANCELLING;
    }

    private int nextIndex() {
        int size = this.idleAnimations.size();
        if (size == 1) {
            return 0;
        }
        if (this.lastIndex == NONE_PLAYED) {
            return this.random.nextInt(size);
        }
        // Draw from every index but the last one played, so no idle animation repeats back to back
        int index = this.random.nextInt(size - 1);
        if (index >= this.lastIndex) {
            index++;
        }
        return index;
    }

    /**
     * The last whole tick at which the current idle animation can still show: where its blend-out ends, or where a
     * cancel's fade ends if that comes first.
     */
    private long currentEndTick(@Nonnull BakedAnimation animation) {
        long naturalEndTick = this.currentStartTick + animation.getDurationTicks() + animation.getBlendOutTicks();
        if (this.cancelStartTick == NOT_CANCELLING) {
            return naturalEndTick;
        }
        return Math.min(naturalEndTick, this.cancelStartTick + Math.max(0, animation.getBlendOutTicks()));
    }

    private long randomDelay() {
        return MIN_DELAY_TICKS + this.random.nextInt(DELAY_RANGE_TICKS + 1);
    }

    private static float playbackWeight(@Nonnull BakedAnimation animation, float elapsedTicks) {
        int blendInTicks = animation.getBlendInTicks();
        float blendInWeight = blendInTicks <= 0
                ? 1.0F
                : Math.clamp(elapsedTicks / blendInTicks, 0.0F, 1.0F);
        if (elapsedTicks <= animation.getDurationTicks()) {
            return blendInWeight;
        }

        int blendOutTicks = animation.getBlendOutTicks();
        if (blendOutTicks <= 0) {
            return 0.0F;
        }
        float blendOutProgress = (elapsedTicks - animation.getDurationTicks()) / blendOutTicks;
        return blendInWeight * (1.0F - Math.clamp(blendOutProgress, 0.0F, 1.0F));
    }

    private float cancelWeight(@Nonnull BakedAnimation animation, long tick, float partialTicks) {
        if (this.cancelStartTick == NOT_CANCELLING) {
            return 1.0F;
        }
        int blendOutTicks = animation.getBlendOutTicks();
        if (blendOutTicks <= 0) {
            return 0.0F;
        }
        float cancelProgress = elapsedSince(this.cancelStartTick, tick, partialTicks) / blendOutTicks;
        return 1.0F - Math.clamp(cancelProgress, 0.0F, 1.0F);
    }

    private static float elapsedSince(long startTick, long tick, float partialTicks) {
        return Math.max(0.0F, (tick - startTick) + partialTicks);
    }

}
