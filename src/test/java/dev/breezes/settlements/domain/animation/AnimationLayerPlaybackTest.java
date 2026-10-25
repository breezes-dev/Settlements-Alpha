package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLayerPlaybackTest {

    private static final int BLEND_IN_TICKS = 4;
    private static final int DURATION_TICKS = 10;
    private static final int BLEND_OUT_TICKS = 4;

    // A partial entry weight distinguishes preserving it from restarting at zero or full weight
    private static final long PUSH_GAME_TIME = DURATION_TICKS + BLEND_OUT_TICKS / 2;

    @Test
    void reads_changeNothing_untilTheLayerIsUpdated() {
        // Arrange
        AnimationLayer layer = crossfading();
        long blendedIn = PUSH_GAME_TIME + BLEND_IN_TICKS;

        // Act
        float firstShare = layer.incomingShare(blendedIn, 0.0F);
        float firstWeight = layer.weight(blendedIn, 0.0F);
        KeyframeAnimation firstOutgoing = layer.getOutgoingAnimation();
        float secondShare = layer.incomingShare(blendedIn, 0.0F);
        float secondWeight = layer.weight(blendedIn, 0.0F);
        KeyframeAnimation secondOutgoing = layer.getOutgoingAnimation();

        // Assert
        assertNotNull(firstOutgoing);
        assertSame(firstOutgoing, secondOutgoing);
        assertEquals(firstShare, secondShare);
        assertEquals(firstWeight, secondWeight);
    }

    @Test
    void update_retiresTheOutgoingAnimation_onceTheIncomingHasBlendedIn() {
        // Arrange
        AnimationLayer layer = crossfading();

        // Act
        layer.update(PUSH_GAME_TIME + BLEND_IN_TICKS, 0.0F);

        // Assert
        assertNull(layer.getOutgoingAnimation());
        assertEquals(1.0F, layer.incomingShare(PUSH_GAME_TIME + BLEND_IN_TICKS, 0.0F));
    }

    @Test
    void update_keepsTheOutgoingAnimation_whileTheIncomingIsStillBlendingIn() {
        // Arrange
        AnimationLayer layer = crossfading();

        // Act
        layer.update(PUSH_GAME_TIME + BLEND_IN_TICKS - 1, 0.0F);

        // Assert
        assertNotNull(layer.getOutgoingAnimation());
    }

    @Test
    void incomingShare_risesWithTheIncomingBlendIn_onTheClockTheWeightRisesBy() {
        // Arrange
        AnimationLayer layer = AnimationTestClips.playingOnce(outgoingClip(), 0L);
        float entryWeight = layer.weight(PUSH_GAME_TIME, 0.0F);
        layer.replace(incomingClip(), PUSH_GAME_TIME, true);

        for (int ticksIn = 0; ticksIn <= BLEND_IN_TICKS; ticksIn++) {
            long gameTime = PUSH_GAME_TIME + ticksIn;

            // Act
            float share = layer.incomingShare(gameTime, 0.0F);
            float weight = layer.weight(gameTime, 0.0F);

            // Assert: replacement preserves the entry weight while the incoming share starts at zero
            assertEquals((float) ticksIn / BLEND_IN_TICKS, share, 1.0E-5F, "tick " + ticksIn);
            assertEquals(entryWeight + (1.0F - entryWeight) * share, weight, 1.0E-5F, "tick " + ticksIn);
        }
        assertTrue(entryWeight > 0.0F && entryWeight < 1.0F);
    }

    @Test
    void incomingShare_isFull_withNoOutgoingAnimation() {
        // Arrange: sample during blend-in to distinguish the animation's share from the slot's weight
        KeyframeAnimation animation = AnimationTestClips.timed("fresh", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
        AnimationLayer layer = AnimationTestClips.playingOnce(animation, 0L);

        // Act
        float share = layer.incomingShare(BLEND_IN_TICKS / 2, 0.0F);

        // Assert
        assertEquals(1.0F, share);
        assertTrue(layer.weight(BLEND_IN_TICKS / 2, 0.0F) < 1.0F);
    }

    @Test
    void elapsedTicks_measureEachAlternativeFromItsOwnStart() {
        // Arrange
        AnimationLayer layer = crossfading();
        long gameTime = PUSH_GAME_TIME + 1;
        float partialTicks = 0.5F;

        // Act
        float currentElapsed = layer.currentElapsedTicks(gameTime, partialTicks);
        float outgoingElapsed = layer.outgoingElapsedTicks(gameTime, partialTicks);

        // Assert
        assertEquals(1.5F, currentElapsed, 1.0E-5F);
        assertEquals(PUSH_GAME_TIME + 1.5F, outgoingElapsed, 1.0E-5F);
    }

    @Test
    void replace_dropsTheOlderOutgoingAnimation_whenPushedDuringACrossfade() {
        // Arrange
        AnimationLayer layer = crossfading();
        KeyframeAnimation interrupted = layer.getCurrentAnimation();
        KeyframeAnimation latest = AnimationTestClips.timed("latest", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);

        // Act
        layer.replace(latest, PUSH_GAME_TIME + 1, true);

        // Assert
        assertSame(latest, layer.getCurrentAnimation());
        assertSame(interrupted, layer.getOutgoingAnimation());
    }

    /**
     * Returns a layer at the start of a crossfade, with a partial entry weight.
     */
    private static AnimationLayer crossfading() {
        AnimationLayer layer = AnimationTestClips.playingOnce(outgoingClip(), 0L);
        layer.replace(incomingClip(), PUSH_GAME_TIME, true);
        return layer;
    }

    private static KeyframeAnimation outgoingClip() {
        return AnimationTestClips.timed("outgoing", 0, DURATION_TICKS, BLEND_OUT_TICKS);
    }

    private static KeyframeAnimation incomingClip() {
        return AnimationTestClips.timed("incoming", BLEND_IN_TICKS, DURATION_TICKS, BLEND_OUT_TICKS);
    }

}
