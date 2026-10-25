package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientBrowTest {

    private static final float TOLERANCE = 1.0E-6F;
    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int BROW_TRANSLATION = RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int BROW_ROTATION = RIG.indexOf(AnimationTargets.MONOBROW_ROTATION);
    private static final int Y = 1;
    private static final int Z = 2;

    private static final long SEED = 11L;
    private static final long FIRST_TICK = 1_000L;

    // Partway into a tick, where a motion that started on it has already left rest
    private static final float MID_TICK = 0.5F;

    private static final List<BakedAnimation> MOTIONS = List.of(
            BakedAnimation.bake(AmbientAnimations.browRaise(), RIG),
            BakedAnimation.bake(AmbientAnimations.browRaiseLeftEnd(), RIG),
            BakedAnimation.bake(AmbientAnimations.browRaiseRightEnd(), RIG));

    // The longest first wait and the longest motion it can start
    private static final long FIRST_MOTION_SPAN = AmbientBrow.MIN_DELAY_TICKS + AmbientBrow.DELAY_RANGE_TICKS
            + MOTIONS.stream().mapToInt(BakedAnimation::getDurationTicks).max().orElseThrow() + 1;

    @Test
    void motions_ofVillagersSharingOneRandomSource_startOnDifferentTicks() {
        // Arrange: villagers built together and first seen on one tick, drawing from one source as in production
        Random shared = new Random(SEED);
        int villagers = 8;
        List<AmbientBrow> brows = new ArrayList<>();
        for (int villager = 0; villager < villagers; villager++) {
            brows.add(new AmbientBrow(MOTIONS, shared));
        }
        long[] firstMovingTicks = new long[villagers];
        Arrays.fill(firstMovingTicks, Long.MIN_VALUE);
        Set<Long> distinctStarts = new HashSet<>();

        // Act
        for (long tick = FIRST_TICK; tick <= FIRST_TICK + FIRST_MOTION_SPAN; tick++) {
            for (int villager = 0; villager < villagers; villager++) {
                AmbientBrow brow = brows.get(villager);
                brow.update(tick);
                if (firstMovingTicks[villager] == Long.MIN_VALUE && moves(sample(brow, tick, MID_TICK))) {
                    firstMovingTicks[villager] = tick;
                }
            }
        }

        // Assert: a fixed delay, or one schedule shared by every villager, would start them all together
        for (long firstMovingTick : firstMovingTicks) {
            assertTrue(firstMovingTick != Long.MIN_VALUE, "a villager never moved its brow");
            distinctStarts.add(firstMovingTick);
        }
        assertTrue(distinctStarts.size() > villagers / 2, "brows started together: " + distinctStarts);
    }

    @ParameterizedTest
    @MethodSource("gapsThatCannotMissAStart")
    void motions_playTheSame_whateverTheGapBetweenUpdates(int sparseGapTicks) {
        // Arrange: one villager updated every tick, one only every few ticks, on the same seed
        AmbientBrow everyTick = new AmbientBrow(MOTIONS, new Random(SEED));
        AmbientBrow sparse = new AmbientBrow(MOTIONS, new Random(SEED));
        boolean sharedSampleMoved = false;

        // Act, Assert: compare wherever both are sampled
        for (long tick = FIRST_TICK; tick <= FIRST_TICK + 4L * FIRST_MOTION_SPAN; tick++) {
            everyTick.update(tick);
            if ((tick - FIRST_TICK) % sparseGapTicks != 0) {
                continue;
            }
            sparse.update(tick);
            float[] expected = sample(everyTick, tick, MID_TICK);
            float[] actual = sample(sparse, tick, MID_TICK);
            assertEquals(expected[0], actual[0], 0.0F, "offset at tick " + tick);
            assertEquals(expected[1], actual[1], 0.0F, "roll at tick " + tick);
            sharedSampleMoved |= moves(expected);
        }
        assertTrue(sharedSampleMoved, "no brow motion fell on a shared sample");
    }

    @Test
    void motions_sampleTheSame_atAnyFrameRate() {
        // Arrange: one villager updated and sampled at seven frames per tick, one updated once per tick
        int framesPerTick = 7;
        AmbientBrow highFrameRate = new AmbientBrow(MOTIONS, new Random(SEED));
        AmbientBrow oneFramePerTick = new AmbientBrow(MOTIONS, new Random(SEED));
        boolean moved = false;

        // Act, Assert: a motion timed by frames would part the two between whole ticks
        for (long tick = FIRST_TICK; tick <= FIRST_TICK + 2L * FIRST_MOTION_SPAN; tick++) {
            oneFramePerTick.update(tick);
            for (int frame = 0; frame < framesPerTick; frame++) {
                float partialTicks = (float) frame / framesPerTick;
                highFrameRate.update(tick);
                float[] expected = sample(oneFramePerTick, tick, partialTicks);
                float[] actual = sample(highFrameRate, tick, partialTicks);
                assertEquals(expected[0], actual[0], 0.0F, "offset at tick " + tick + " frame " + frame);
                assertEquals(expected[1], actual[1], 0.0F, "roll at tick " + tick + " frame " + frame);
                moved |= moves(expected);
            }
        }
        assertTrue(moved, "no brow motion played");
    }

    @Test
    void apply_addsThePlayingMotionOntoTheAuthoredBrow_scaledByWeight() {
        // Arrange: a brow mid-motion, applied over an authored raise and roll
        AmbientBrow brow = new AmbientBrow(MOTIONS, new Random(SEED));
        long tick = firstMovingTick(brow);
        float[] motion = sample(brow, tick, MID_TICK);
        float authoredOffset = -1.0F;
        float authoredRoll = 0.02F;
        AnimationPose suppressed = authoredBrow(authoredOffset, authoredRoll);
        AnimationPose halfWeight = authoredBrow(authoredOffset, authoredRoll);

        // Act
        brow.apply(suppressed, new ComponentSuppression(), 0.0F, tick, MID_TICK);
        brow.apply(halfWeight, new ComponentSuppression(), 0.5F, tick, MID_TICK);

        // Assert: a motion that ignored its weight would move a suppressed brow; one that owned the brow would drop
        // the authored raise
        assertEquals(authoredOffset, suppressed.get(BROW_TRANSLATION, Y), 0.0F);
        assertEquals(authoredRoll, suppressed.get(BROW_ROTATION, Z), 0.0F);
        assertEquals(authoredOffset + 0.5F * motion[0], halfWeight.get(BROW_TRANSLATION, Y), TOLERANCE);
        assertEquals(authoredRoll + 0.5F * motion[1], halfWeight.get(BROW_ROTATION, Z), TOLERANCE);
    }

    @ParameterizedTest
    @MethodSource("strengthDraws")
    void motion_playsAtItsDrawnStrength_fromTheFloorUpToItsAuthoredExtent(float strengthDraw, float expectedStrength) {
        // Arrange: one motion whose delay draws are all zero, sampled midway through it
        BakedAnimation raise = MOTIONS.getFirst();
        AmbientBrow brow = new AmbientBrow(List.of(raise), new FixedDrawRandom(strengthDraw));
        long start = FIRST_TICK + AmbientBrow.MIN_DELAY_TICKS;
        long sampleTick = start + raise.getDurationTicks() / 2;
        for (long tick = FIRST_TICK; tick <= sampleTick; tick++) {
            brow.update(tick);
        }
        AnimationPose authored = new AnimationPose(RIG);
        raise.sampleInto(authored, (sampleTick - start) + MID_TICK);

        // Act
        float[] played = sample(brow, sampleTick, MID_TICK);

        // Assert: a motion ignoring its draw always plays in full; one drawn from zero can play no visible motion
        float authoredOffset = authored.get(BROW_TRANSLATION, Y);
        assertTrue(authoredOffset < 0.0F, "the raise is not raised at the sample: " + authoredOffset);
        assertEquals(expectedStrength * authoredOffset, played[0], TOLERANCE);
    }

    @ParameterizedTest
    @MethodSource("motionsThatDoMoreThanAddToTheBrow")
    void constructor_rejectsAMotionThatDoesMoreThanAddToTheBrow(KeyframeAnimation clip) {
        // Arrange
        List<BakedAnimation> motions = List.of(BakedAnimation.bake(clip, RIG));

        // Act, Assert: weighted by the brow's suppression, such a motion would move or hide another channel or
        // component
        assertThrows(IllegalArgumentException.class, () -> new AmbientBrow(motions, new Random(SEED)));
    }

    /**
     * Gaps from a couple of ticks up to one past the tolerance, the widest after which no update is ever later than it.
     */
    static IntStream gapsThatCannotMissAStart() {
        return IntStream.of(2, 7, AmbientBrow.MISSED_START_TOLERANCE_TICKS,
                AmbientBrow.MISSED_START_TOLERANCE_TICKS + 1);
    }

    /**
     * The lowest, middle, and highest strength draws, each with the share of the authored extent it plays at.
     */
    static Stream<Arguments> strengthDraws() {
        return Stream.of(
                Arguments.of(0.0F, AmbientBrow.MIN_MOTION_STRENGTH),
                Arguments.of(0.5F, (AmbientBrow.MIN_MOTION_STRENGTH + 1.0F) / 2.0F),
                Arguments.of(1.0F, 1.0F));
    }

    static Stream<KeyframeAnimation> motionsThatDoMoreThanAddToTheBrow() {
        return Stream.of(
                AmbientAnimations.blink(),
                browRaise("owns_the_brow").owns(VillagerAnimationRig.BROW).build(),
                browRaise("suppresses_the_blink").suppresses(SuppressibleComponent.BLINK).build());
    }

    private static TrackAnimationBuilder browRaise(String name) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(10)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.MONOBROW_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                                new Keyframe<>(10, new Vec3(0.0, -0.3, 0.0), Easing.LINEAR)))
                        .build());
    }

    /**
     * Updates the brow every tick from the first tick until a motion moves it, and returns that tick.
     */
    private static long firstMovingTick(AmbientBrow brow) {
        for (long tick = FIRST_TICK; tick <= FIRST_TICK + FIRST_MOTION_SPAN; tick++) {
            brow.update(tick);
            if (moves(sample(brow, tick, MID_TICK))) {
                return tick;
            }
        }
        throw new AssertionError("no brow motion played");
    }

    /**
     * Returns the brow's vertical offset and roll the ambient brow adds onto a brow at rest at full weight.
     */
    private static float[] sample(AmbientBrow brow, long tick, float partialTicks) {
        AnimationPose pose = new AnimationPose(RIG);
        brow.apply(pose, new ComponentSuppression(), 1.0F, tick, partialTicks);
        return new float[]{pose.get(BROW_TRANSLATION, Y), pose.get(BROW_ROTATION, Z)};
    }

    private static boolean moves(float[] offsetAndRoll) {
        return offsetAndRoll[0] != 0.0F || offsetAndRoll[1] != 0.0F;
    }

    private static AnimationPose authoredBrow(float offset, float roll) {
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(BROW_TRANSLATION, Y, offset);
        pose.set(BROW_ROTATION, Z, roll);
        return pose;
    }

    /**
     * A random source whose integer draws all return zero and whose float draws all return the given value.
     */
    private static final class FixedDrawRandom extends Random {

        private final float floatDraw;

        private FixedDrawRandom(float floatDraw) {
            super(0L);
            this.floatDraw = floatDraw;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return this.floatDraw;
        }

    }

}
