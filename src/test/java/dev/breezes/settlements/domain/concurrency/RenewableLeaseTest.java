package dev.breezes.settlements.domain.concurrency;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenewableLeaseTest {

    private static final UUID HOLDER = new UUID(0L, 1L);
    private static final UUID OTHER = new UUID(0L, 2L);
    private static final UUID THIRD = new UUID(0L, 3L);

    private static final long ACQUIRED_AT = 1_000L;

    // Far longer than any lease lasts unrenewed, so each test reads a lease left alone this long as lapsed
    private static final long LONG_AFTER = ACQUIRED_AT + 100_000L;

    private static final long RENEWAL_INTERVAL_TICKS = RenewableLease.RENEWAL_INTERVAL.getTicks();

    @Test
    void acquire_refused_whenAnotherHoldsALiveLease() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean acquired = lease.acquire(OTHER, ACQUIRED_AT + 1);

        // Assert: taken, two claimants would converge on one resource
        assertFalse(acquired);
    }

    @Test
    void acquire_succeeds_whenTheHoldersLeaseHasLapsed() {
        // Arrange: a holder that stopped without releasing
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean acquired = lease.acquire(OTHER, LONG_AFTER);

        // Assert: refused, an abandoned lease would keep the resource from every other claimant for good
        assertTrue(acquired);
    }

    @Test
    void isFreeFor_holder_whileItsLeaseIsLive() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean free = lease.isFreeFor(HOLDER, ACQUIRED_AT + 1);

        // Assert: read as taken, the resource would look unavailable to the very claimant operating it
        assertTrue(free);
    }

    @Test
    void isFreeFor_another_isFalse_afterTheHolderMissesOneRenewal() {
        // Arrange: the holder acquires and then misses its next renewal
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act: just as the renewal after the missed one comes due
        boolean free = lease.isFreeFor(OTHER, ACQUIRED_AT + 2 * RENEWAL_INTERVAL_TICKS);

        // Assert: a lease lasting no more than two renewal intervals hands the resource to a second claimant the moment
        // one renewal slips
        assertFalse(free);
    }

    @Test
    void renew_keepsTheLease_whileTheHolderRenewsOnItsInterval() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        for (long gameTime = ACQUIRED_AT + RENEWAL_INTERVAL_TICKS; gameTime <= LONG_AFTER;
             gameTime += RENEWAL_INTERVAL_TICKS) {
            lease.renew(HOLDER, gameTime);
        }

        // Assert: a renewal that did not extend the lease would let it lapse under a claimant still operating
        assertFalse(lease.isFreeFor(OTHER, LONG_AFTER));
    }

    @Test
    void renew_refused_forAVillagerThatDoesNotHoldIt() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean renewed = lease.renew(OTHER, ACQUIRED_AT + 1);

        // Assert: the other claimant would be told it holds a resource the holder is operating
        assertFalse(renewed);
        assertFalse(lease.isFreeFor(OTHER, ACQUIRED_AT + 1));
    }

    @Test
    void renew_refused_onceTheLeaseHasLapsed() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean renewed = lease.renew(HOLDER, LONG_AFTER);

        // Assert: revived, the holder would carry on as if it had never lost a resource every other claimant has meanwhile
        // read as free
        assertFalse(renewed);
    }

    @Test
    void release_freesTheLease_forAnotherAtOnce() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        lease.release(HOLDER);

        // Assert: a release that did nothing would hold the resource until the lease lapsed
        assertTrue(lease.isFreeFor(OTHER, ACQUIRED_AT + 1));
    }

    @Test
    void release_ignored_whenAnotherTookTheLeaseAfterTheReleasersLapsed() {
        // Arrange: the first holder's lease lapses and another claimant takes the resource
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);
        lease.acquire(OTHER, LONG_AFTER);

        // Act: the first holder's release arrives late
        lease.release(HOLDER);

        // Assert: freed, a third claimant would converge on the resource the second is operating
        assertFalse(lease.isFreeFor(THIRD, LONG_AFTER + 1));
    }


    @Test
    void isLiveAt_false_withoutAClaim() {
        // Arrange
        RenewableLease lease = new RenewableLease();

        // Act
        boolean live = lease.isLiveAt(ACQUIRED_AT);

        // Assert
        assertFalse(live);
    }

    @Test
    void isLiveAt_true_whileAClaimIsHeld() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean live = lease.isLiveAt(ACQUIRED_AT + 1);

        // Assert
        assertTrue(live);
    }

    @Test
    void isLiveAt_false_afterRelease() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);
        lease.release(HOLDER);

        // Act
        boolean live = lease.isLiveAt(ACQUIRED_AT + 1);

        // Assert
        assertFalse(live);
    }

    @Test
    void isLiveAt_false_afterExpiry() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean live = lease.isLiveAt(LONG_AFTER);

        // Assert
        assertFalse(live);
    }

    @Test
    void isHeldBy_true_forTheLiveHolder() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean held = lease.isHeldBy(HOLDER, ACQUIRED_AT + 1);

        // Assert
        assertTrue(held);
    }

    @Test
    void isHeldBy_false_forAnotherCandidate() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean held = lease.isHeldBy(OTHER, ACQUIRED_AT + 1);

        // Assert
        assertFalse(held);
    }

    @Test
    void isHeldBy_false_forAnExpiredHolder() {
        // Arrange
        RenewableLease lease = new RenewableLease();
        lease.acquire(HOLDER, ACQUIRED_AT);

        // Act
        boolean held = lease.isHeldBy(HOLDER, LONG_AFTER);

        // Assert
        assertFalse(held);
    }

    @Test
    void isHeldBy_false_withoutAClaim() {
        // Arrange
        RenewableLease lease = new RenewableLease();

        // Act
        boolean held = lease.isHeldBy(HOLDER, ACQUIRED_AT);

        // Assert
        assertFalse(held);
    }

}
