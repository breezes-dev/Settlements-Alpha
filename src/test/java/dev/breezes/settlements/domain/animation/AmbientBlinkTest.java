package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientBlinkTest {

    private static final long SEED = 3L;

    // Partway into the tick a blink starts on, where its lids have already begun closing
    private static final float MID_TICK = 0.5F;

    private static final int BLINKS_TO_SPAN = 20;
    // Enough blinks that one of them falls where the widest gap finds it overdue
    private static final int BLINKS_TO_PART = 400;

    private static final BakedAnimation BLINK =
            BakedAnimation.bake(AmbientAnimations.blink(), VillagerAnimationRig.RIG);

    @Test
    void constructor_rejectsABlinkThatNeverMovesTheEyelids() {
        // Arrange: lids keyed, but held at rest throughout, so there is no closed pose to blink toward
        KeyframeAnimation clip = KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/still_blink"))
                .durationTicks(4)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.EYELID_LEFT_TRANSLATION)
                        .keyframes(List.of(new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR)))
                        .build())
                .build();
        BakedAnimation baked = BakedAnimation.bake(clip, VillagerAnimationRig.RIG);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> new AmbientBlink(baked, new Random(1L)));
    }

    @Test
    void blink_dueWhileUnobserved_waitsAFreshDelay() {
        // Arrange: schedule the first blink, then go unobserved until it is overdue by more than the tolerance
        AmbientBlink blink = new AmbientBlink(BLINK, new Random(SEED));
        blink.update(0L);
        long returnTick = AmbientBlink.MIN_DELAY_TICKS + AmbientBlink.DELAY_RANGE_TICKS
                + AmbientBlink.MISSED_START_TOLERANCE_TICKS + 1;
        boolean closed = false;

        // Act: observe every tick for a whole shortest wait after returning
        for (long tick = returnTick; tick < returnTick + AmbientBlink.MIN_DELAY_TICKS; tick++) {
            blink.update(tick);
            closed |= blink.closure(tick, MID_TICK) > 0.0F;
        }

        // Assert
        assertFalse(closed);
    }

    @ParameterizedTest
    @MethodSource("gapsThatCannotMissAStart")
    void blinks_startAndEndOnTheSameTicks_whateverTheGapBetweenUpdates(int sparseGapTicks) {
        // Arrange: one blink updated every tick, one only every few ticks, on the same seed
        AmbientBlink everyTick = new AmbientBlink(BLINK, new Random(SEED));
        AmbientBlink sparse = new AmbientBlink(BLINK, new Random(SEED));
        boolean sharedSampleClosed = false;

        // Act, Assert: compare wherever both are sampled
        for (long tick = 0; tick <= span(BLINKS_TO_SPAN); tick++) {
            everyTick.update(tick);
            if (tick % sparseGapTicks != 0) {
                continue;
            }
            sparse.update(tick);
            float closure = everyTick.closure(tick, MID_TICK);
            assertEquals(closure, sparse.closure(tick, MID_TICK), 0.0F, "at tick " + tick);
            sharedSampleClosed |= closure > 0.0F;
        }
        assertTrue(sharedSampleClosed, "no blink fell on a shared sample");
    }

    @Test
    void blinks_partWays_onceAGapCanFindABlinkOverduePastTheTolerance() {
        // Arrange: an update is at most a gap less one tick late, so this is the first gap that can be too late
        int sparseGapTicks = AmbientBlink.MISSED_START_TOLERANCE_TICKS + 2;
        AmbientBlink everyTick = new AmbientBlink(BLINK, new Random(SEED));
        AmbientBlink sparse = new AmbientBlink(BLINK, new Random(SEED));
        boolean parted = false;

        // Act
        for (long tick = 0; tick <= span(BLINKS_TO_PART) && !parted; tick++) {
            everyTick.update(tick);
            if (tick % sparseGapTicks != 0) {
                continue;
            }
            sparse.update(tick);
            parted = everyTick.closure(tick, MID_TICK) != sparse.closure(tick, MID_TICK);
        }

        // Assert: the sparse blink found one overdue and waited a fresh delay, which moves every later blink
        assertTrue(parted);
    }

    /**
     * Gaps from a couple of ticks up to one past the tolerance, the widest after which no update is ever later than it.
     */
    static IntStream gapsThatCannotMissAStart() {
        return IntStream.of(2, 7, AmbientBlink.MISSED_START_TOLERANCE_TICKS,
                AmbientBlink.MISSED_START_TOLERANCE_TICKS + 1);
    }

    private static long span(int blinks) {
        return blinks * (long) (AmbientBlink.MIN_DELAY_TICKS + AmbientBlink.DELAY_RANGE_TICKS
                + BLINK.getDurationTicks() + 1);
    }

}
