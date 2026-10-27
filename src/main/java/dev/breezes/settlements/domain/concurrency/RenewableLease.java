package dev.breezes.settlements.domain.concurrency;

import dev.breezes.settlements.domain.time.ClockTicks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * An exclusive claim that expires unless its holder keeps renewing it.
 * All timestamps must come from the same simulation clock.
 */
public final class RenewableLease {

    /**
     * How often a holder renews its lease.
     */
    public static final ClockTicks RENEWAL_INTERVAL = ClockTicks.of(5);

    /**
     * How many renewal intervals a lease lasts, allowing a missed renewal without admitting another holder.
     */
    private static final int RENEWAL_INTERVALS_PER_LEASE = 4;

    private static final ClockTicks DURATION = ClockTicks.of(RENEWAL_INTERVAL.getTicks() * RENEWAL_INTERVALS_PER_LEASE);

    @Nullable
    private UUID holderId;
    private long expiresAtGameTime;

    /**
     * Whether the candidate may acquire the lease, including when it already holds it.
     */
    public boolean isFreeFor(@Nonnull UUID candidateId, long gameTime) {
        return !this.isLiveAt(gameTime) || candidateId.equals(this.holderId);
    }

    /**
     * Acquires the lease when available, renewing it when the candidate already holds it.
     *
     * @return whether the candidate holds the lease now
     */
    public boolean acquire(@Nonnull UUID candidateId, long gameTime) {
        if (!this.isFreeFor(candidateId, gameTime)) {
            return false;
        }

        this.holderId = candidateId;
        this.expiresAtGameTime = gameTime + DURATION.getTicks();
        return true;
    }

    /**
     * Extends the holder's live lease without acquiring an unheld or expired one.
     *
     * @return whether the candidate still holds the lease
     */
    public boolean renew(@Nonnull UUID candidateId, long gameTime) {
        if (!this.isHeldBy(candidateId, gameTime)) {
            return false;
        }

        this.expiresAtGameTime = gameTime + DURATION.getTicks();
        return true;
    }

    /**
     * Releases the candidate's claim, ignoring a release from anyone else.
     */
    public void release(@Nonnull UUID candidateId) {
        // A late release must not free a claim acquired by a different holder after the first claim expired
        if (candidateId.equals(this.holderId)) {
            this.holderId = null;
        }
    }

    /**
     * Whether any holder has an unexpired claim at the supplied simulation time.
     */
    public boolean isLiveAt(long gameTime) {
        return this.holderId != null && gameTime < this.expiresAtGameTime;
    }

    /**
     * Whether the candidate holds an unexpired claim at the supplied simulation time.
     */
    public boolean isHeldBy(@Nonnull UUID candidateId, long gameTime) {
        return this.isLiveAt(gameTime) && candidateId.equals(this.holderId);
    }

}
