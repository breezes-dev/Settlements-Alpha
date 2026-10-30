package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedGateTest {

    private static final int DELAY_TICKS = 8;

    @Test
    void holdsChangedRequest_untilTheDelayElapses() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, false);

        // Act, Assert
        for (long tick = 0; tick < DELAY_TICKS; tick++) {
            assertFalse(gate.update(tick, true));
        }
        assertTrue(gate.update(DELAY_TICKS, true));
    }

    @Test
    void delaysClosing_asWellAsOpening() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, false);
        updateThrough(gate, 0L, DELAY_TICKS, true);
        long closeRequestTick = DELAY_TICKS + 1L;
        long closeCommitTick = closeRequestTick + DELAY_TICKS;

        // Act, Assert
        for (long tick = closeRequestTick; tick < closeCommitTick; tick++) {
            assertTrue(gate.update(tick, false));
        }
        assertFalse(gate.update(closeCommitTick, false));
    }

    @Test
    void abandonsPendingChange_whenTheRequestReturnsBeforeTheDelayElapses() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, false);
        long requestReturnTick = DELAY_TICKS / 2;
        updateThrough(gate, 0L, requestReturnTick - 1L, true);

        // Act
        updateThrough(gate, requestReturnTick, DELAY_TICKS, false);

        // Assert
        assertFalse(gate.isOpen());
    }

    @Test
    void restartsTheFullDelay_afterAnAbandonedChange() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, false);
        long abandonTick = DELAY_TICKS - 1L;
        updateThrough(gate, 0L, abandonTick - 1L, true);
        gate.update(abandonTick, false);
        long reopenTick = abandonTick + 1L;
        long recommitTick = reopenTick + DELAY_TICKS;

        // Act
        updateThrough(gate, reopenTick, recommitTick - 1L, true);

        // Assert
        assertFalse(gate.isOpen());
        assertTrue(gate.update(recommitTick, true));
    }

    @Test
    void startsCommitted_withoutWaitingForTheDelay() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, true);

        // Act
        boolean committedValue = gate.update(0L, true);

        // Assert
        assertTrue(committedValue);
        assertTrue(gate.isOpen());
    }

    @Test
    void commitsImmediately_whenThereIsNoDelay() {
        // Arrange
        DelayedGate gate = new DelayedGate(0, false);

        // Act
        boolean committedValue = gate.update(0L, true);

        // Assert
        assertTrue(committedValue);
    }

    @Test
    void repeatedObservation_doesNotAdvanceTheDelay() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, false);

        // Act
        for (int observation = 0; observation <= DELAY_TICKS; observation++) {
            gate.update(0L, true);
        }

        // Assert
        assertFalse(gate.isOpen());
        updateThrough(gate, 1L, DELAY_TICKS, true);
        assertTrue(gate.isOpen());
        assertTrue(gate.update(DELAY_TICKS, true));
    }

    @Test
    void resetCommitsImmediately_andDiscardsThePendingDelay() {
        // Arrange
        DelayedGate gate = new DelayedGate(DELAY_TICKS, true);
        gate.update(0L, false);

        // Act
        gate.reset(false);
        gate.update(1L, true);

        // Assert
        assertFalse(gate.isOpen());
        updateThrough(gate, 2L, DELAY_TICKS, true);
        assertFalse(gate.isOpen());
        assertTrue(gate.update(DELAY_TICKS + 1L, true));
    }

    private static void updateThrough(DelayedGate gate, long fromTick, long toTick, boolean requestedValue) {
        for (long tick = fromTick; tick <= toTick; tick++) {
            gate.update(tick, requestedValue);
        }
    }

}
