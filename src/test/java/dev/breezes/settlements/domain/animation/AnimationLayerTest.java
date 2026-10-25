package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLayerTest {

    private static final int BLEND_IN_TICKS = 4;
    private static final int DURATION_TICKS = 10;
    private static final int BLEND_OUT_TICKS = 4;
    private static final int LONG_BLEND_TICKS = 8;

    @Test
    void weight_risesAcrossBlendInForANewLayer() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("action", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);

        // Act
        float halfwayIn = layer.weight(BLEND_IN_TICKS / 2, 0.0F);
        float fullyIn = layer.weight(BLEND_IN_TICKS, 0.0F);

        // Assert
        assertEquals(0.5F, halfwayIn, 0.0001F);
        assertEquals(1.0F, fullyIn, 0.0001F);
    }

    @Test
    void weight_fallsFromItsCurrentWeightWhenClearedDuringBlendIn() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("sustained", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(animation, 0L);
        long clearGameTime = 1L;
        float weightAtClear = layer.weight(clearGameTime, 0.0F);

        // Act
        layer.beginClear(clearGameTime);
        float nextWeight = layer.weight(clearGameTime + 1L, 0.0F);
        float laterWeight = layer.weight(clearGameTime + 2L, 0.0F);

        // Assert
        assertEquals(0.25F, weightAtClear, 0.0001F);
        assertTrue(nextWeight < weightAtClear);
        assertTrue(laterWeight < nextWeight);
    }

    @Test
    void weight_holdsFullThroughDurationThenFadesOutForATransientLayer() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("action", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);

        // Act
        float atDuration = layer.weight(DURATION_TICKS, 0.0F);
        float halfwayOut = layer.weight(DURATION_TICKS + BLEND_OUT_TICKS / 2, 0.0F);
        float fullyOut = layer.weight(DURATION_TICKS + BLEND_OUT_TICKS, 0.0F);

        // Assert
        assertEquals(1.0F, atDuration, 0.0001F);
        assertEquals(0.5F, halfwayOut, 0.0001F);
        assertEquals(0.0F, fullyOut, 0.0001F);
    }

    @Test
    void weight_fadesAPersistentLayerOutOnlyOnceCleared() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("sustained", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(animation, 0L);
        long clearGameTime = DURATION_TICKS * 3L;

        // Act
        float wellPastDuration = layer.weight(clearGameTime, 0.0F);
        layer.beginClear(clearGameTime);
        float halfwayOut = layer.weight(clearGameTime + BLEND_OUT_TICKS / 2, 0.0F);
        float fullyOut = layer.weight(clearGameTime + BLEND_OUT_TICKS, 0.0F);

        // Assert
        assertEquals(1.0F, wellPastDuration, 0.0001F);
        assertEquals(0.5F, halfwayOut, 0.0001F);
        assertEquals(0.0F, fullyOut, 0.0001F);
    }

    @Test
    void weight_doesNotRecoverWhenClearedMidwayThroughItsOwnBlendOut() {
        // Arrange: a one-shot the behavior only returns to idle after the clip already ran out, so the
        // explicit clear lands on a layer that is halfway through fading on its own.
        KeyframeAnimation animation = AnimationTestClips.timed("action", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);
        long clearGameTime = DURATION_TICKS + BLEND_OUT_TICKS / 2;
        float weightBeforeClear = layer.weight(clearGameTime, 0.0F);

        // Act
        layer.beginClear(clearGameTime);
        float weightAfterClear = layer.weight(clearGameTime, 0.0F);

        // Assert: the clear must not restart the fade and hand the pose back at full strength.
        assertEquals(weightBeforeClear, weightAfterClear, 0.0001F);
        assertTrue(weightAfterClear < 1.0F);
    }

    @Test
    void beginClear_ignoresARepeatCallSoTheFadeKeepsRunningDown() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("sustained", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(animation, 0L);

        // Act
        layer.beginClear(0L);
        layer.beginClear(BLEND_OUT_TICKS / 2);

        // Assert: weight follows the first clear, so the fade is already half spent rather than restarted.
        assertEquals(0.5F, layer.weight(BLEND_OUT_TICKS / 2, 0.0F), 0.0001F);
    }

    @Test
    void replace_resumesFromTheWeightTheLayerAlreadyHeld() {
        // Arrange: a new action arrives while the outgoing one is halfway through its blend-out.
        KeyframeAnimation outgoing = AnimationTestClips.timed("outgoing", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(outgoing, 0L);
        long replaceGameTime = DURATION_TICKS + BLEND_OUT_TICKS / 2;
        float weightBeforeReplace = layer.weight(replaceGameTime, 0.0F);

        // Act
        KeyframeAnimation incoming = AnimationTestClips.timed("incoming", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        layer.replace(incoming, replaceGameTime, true);
        float weightAtReplace = layer.weight(replaceGameTime, 0.0F);
        float weightPartwayIn = layer.weight(replaceGameTime + BLEND_IN_TICKS / 2, 0.0F);
        float weightFullyIn = layer.weight(replaceGameTime + BLEND_IN_TICKS, 0.0F);

        // Assert: it picks up where the fade left off and climbs, rather than snapping back to full.
        assertEquals(weightBeforeReplace, weightAtReplace, 0.0001F);
        assertTrue(weightPartwayIn > weightAtReplace);
        assertEquals(1.0F, weightFullyIn, 0.0001F);
    }

    @Test
    void replace_cancelsAPendingClear() {
        // Arrange
        KeyframeAnimation outgoing = AnimationTestClips.timed("outgoing", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(outgoing, 0L);
        layer.beginClear(0L);

        // Act
        KeyframeAnimation incoming = AnimationTestClips.timed("incoming", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        layer.replace(incoming, 0L, false);

        // Assert: the layer is wanted again, so it neither fades nor retires.
        assertEquals(1.0F, layer.weight(BLEND_OUT_TICKS * 2L, 0.0F), 0.0001F);
        assertFalse(layer.isExpired(BLEND_OUT_TICKS * 2L, 0.0F));
    }

    @Test
    void replace_adoptsTheReplacementsLifetime_inEitherDirection() {
        // Arrange: a held layer replaced by a one-shot, and a one-shot layer replaced by a held clip
        KeyframeAnimation first = AnimationTestClips.timed("first", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        KeyframeAnimation replacement = AnimationTestClips.timed("replacement", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer heldThenOnce = AnimationLayer.persistent(first, 0L);
        AnimationLayer onceThenHeld = AnimationTestClips.playingOnce(first, 0L);

        // Act
        heldThenOnce.replace(replacement, 0L, true);
        onceThenHeld.replace(replacement, 0L, false);

        // Assert: a lifetime kept from before the replacement would hold the one-shot forever or retire the held clip
        // after one play
        long pastOnePlay = DURATION_TICKS + BLEND_OUT_TICKS;
        assertTrue(heldThenOnce.isExpired(pastOnePlay, 0.0F));
        assertFalse(onceThenHeld.isExpired(pastOnePlay, 0.0F));
    }

    @Test
    void isExpired_retiresATransientLayerOnceItsDurationAndBlendOutAreSpent() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("action", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);

        // Act, Assert
        assertFalse(layer.isExpired(DURATION_TICKS + BLEND_OUT_TICKS - 1, 0.0F));
        assertTrue(layer.isExpired(DURATION_TICKS + BLEND_OUT_TICKS, 0.0F));
    }

    @Test
    void isExpired_retiresAClipWithNoBlendOutOnlyOncePastItsDuration() {
        // Arrange: with nothing to fade across, the clip owns its final tick outright.
        KeyframeAnimation animation = AnimationTestClips.timed("action", 0, DURATION_TICKS, 0);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);

        // Act, Assert
        assertFalse(layer.isExpired(DURATION_TICKS, 0.0F));
        assertTrue(layer.isExpired(DURATION_TICKS, 0.1F));
    }

    @Test
    void isExpired_retiresAClearedLayerOnceItsBlendOutIsSpent() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("sustained", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(animation, 0L);

        // Act
        layer.beginClear(0L);

        // Assert
        assertFalse(layer.isExpired(BLEND_OUT_TICKS - 1, 0.0F));
        assertTrue(layer.isExpired(BLEND_OUT_TICKS, 0.0F));
    }

    @Test
    void isExpired_neverRetiresAPersistentLayerThatWasNotCleared() {
        // Arrange
        KeyframeAnimation animation = AnimationTestClips.timed("sustained", 0, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationLayer.persistent(animation, 0L);

        // Act, Assert
        assertFalse(layer.isExpired(DURATION_TICKS * 100L, 0.0F));
    }

    @Test
    void restart_leavesNothingOfThePreviousPlay_likeANewLayer() {
        // Arrange: a layer mid-crossfade and clearing, against a newly created layer of the same start
        KeyframeAnimation previous = AnimationTestClips.timed("previous", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        KeyframeAnimation next = AnimationTestClips.timed("next", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer reused = AnimationLayer.persistent(previous, 0L);
        reused.replace(next, BLEND_IN_TICKS, false);
        reused.beginClear(BLEND_IN_TICKS + 1L);
        long restartTick = 3L * DURATION_TICKS;
        AnimationLayer fresh = AnimationTestClips.playingOnce(next, restartTick);

        // Act
        reused.restart(next, restartTick, true);

        // Assert
        assertNull(reused.getOutgoingAnimation());
        for (long tick = restartTick; tick <= restartTick + DURATION_TICKS + BLEND_OUT_TICKS + 1; tick++) {
            assertEquals(fresh.weight(tick, 0.5F), reused.weight(tick, 0.5F), 0.0F, "weight at " + tick);
            assertEquals(fresh.isExpired(tick, 0.5F), reused.isExpired(tick, 0.5F), "expiry at " + tick);
        }
    }

    @Test
    void swapCurrentAnimation_midCrossfade_playsAsIfTheNewClipHadBeenCurrentFromItsStart() {
        // Arrange: the twin replaced the outgoing clip with the swapped-in clip directly
        KeyframeAnimation outgoing = AnimationTestClips.timed("outgoing", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        long replaceTick = 5L;
        AnimationLayer swapped = AnimationLayer.persistent(outgoing, 0L);
        swapped.replace(swappedOut(), replaceTick, false);
        AnimationLayer twin = AnimationLayer.persistent(outgoing, 0L);
        twin.replace(swappedIn(), replaceTick, false);

        // Act
        swapped.swapCurrentAnimation(swappedIn());

        // Assert
        assertTwins(twin, swapped, replaceTick + 1, replaceTick + 3L * DURATION_TICKS);
    }

    @Test
    void swapCurrentAnimation_midClear_fadesFromTheNewClipsWeightWhenTheClearBegan() {
        // Arrange: cleared partway through the blend-in, where the two clips' blend-ins give different weights
        long clearTick = 2L;
        AnimationLayer swapped = AnimationLayer.persistent(swappedOut(), 0L);
        swapped.beginClear(clearTick);
        AnimationLayer twin = AnimationLayer.persistent(swappedIn(), 0L);
        twin.beginClear(clearTick);

        // Act
        swapped.swapCurrentAnimation(swappedIn());

        // Assert
        assertTwins(twin, swapped, clearTick + 1, clearTick + 3L * DURATION_TICKS);
    }

    @Test
    void swapCurrentAnimation_ontoAClipShorterThanTheElapsedTime_expiresAsThatClip() {
        // Arrange: a transient layer already past the swapped-in clip's duration and blend-out
        long swapTick = 2L * (DURATION_TICKS + LONG_BLEND_TICKS);
        AnimationLayer swapped = AnimationTestClips.playingOnce(
                AnimationTestClips.timed("long", BLEND_IN_TICKS, 4 * DURATION_TICKS, BLEND_OUT_TICKS), 0L);
        AnimationLayer twin = AnimationTestClips.playingOnce(swappedIn(), 0L);

        // Act
        swapped.swapCurrentAnimation(swappedIn());

        // Assert
        assertTrue(swapped.isExpired(swapTick, 0.0F));
        assertTwins(twin, swapped, swapTick, swapTick + DURATION_TICKS);
    }

    /**
     * The clip swapped out: shorter fades than the clip swapped in, so a swap that kept the old timing would show.
     */
    private static KeyframeAnimation swappedOut() {
        return AnimationTestClips.timed("swapped_out", BLEND_IN_TICKS, 2 * DURATION_TICKS, BLEND_OUT_TICKS);
    }

    private static KeyframeAnimation swappedIn() {
        return AnimationTestClips.timed("swapped_in", LONG_BLEND_TICKS, DURATION_TICKS, LONG_BLEND_TICKS);
    }

    private static void assertTwins(AnimationLayer expected, AnimationLayer actual, long fromTick, long toTick) {
        assertEquals(expected.getOutgoingAnimation() == null, actual.getOutgoingAnimation() == null);
        for (long tick = fromTick; tick <= toTick; tick++) {
            String where = " at " + tick;
            assertEquals(expected.weight(tick, 0.5F), actual.weight(tick, 0.5F), 0.0F, "weight" + where);
            assertEquals(expected.incomingShare(tick, 0.5F), actual.incomingShare(tick, 0.5F), 0.0F, "share" + where);
            assertEquals(expected.currentElapsedTicks(tick, 0.5F), actual.currentElapsedTicks(tick, 0.5F), 0.0F,
                    "playback" + where);
            assertEquals(expected.isExpired(tick, 0.5F), actual.isExpired(tick, 0.5F), "expiry" + where);
        }
    }

}
