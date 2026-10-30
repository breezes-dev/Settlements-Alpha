package dev.breezes.settlements.domain.animation;

/**
 * A boolean gate that commits a changed request after a fixed whole-tick delay.
 * Returning to the committed value cancels the pending change.
 */
final class DelayedGate {

    private final int delayTicks;

    private boolean committedValue;
    private boolean changePending;
    private long changeCommitTick;

    /**
     * Starts committed to the initial value, without waiting for the delay.
     */
    DelayedGate(int delayTicks, boolean initialValue) {
        this.delayTicks = delayTicks;
        this.committedValue = initialValue;
    }

    /**
     * Returns the committed value after observing the request at this tick. Repeating the same tick and request
     * has no additional effect; a zero delay commits immediately.
     */
    boolean update(long tick, boolean requestedValue) {
        if (requestedValue == this.committedValue) {
            this.changePending = false;
            return this.committedValue;
        }

        if (!this.changePending) {
            this.changePending = true;
            this.changeCommitTick = tick + this.delayTicks;
        }

        if (tick >= this.changeCommitTick) {
            this.changePending = false;
            this.committedValue = requestedValue;
        }

        return this.committedValue;
    }

    boolean isOpen() {
        return this.committedValue;
    }

    /**
     * Commits the supplied value immediately and discards any pending change.
     */
    void reset(boolean committedValue) {
        this.committedValue = committedValue;
        this.changePending = false;
    }

}
