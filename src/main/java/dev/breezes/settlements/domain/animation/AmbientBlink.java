package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import lombok.Getter;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * One villager's ambient blink schedule.
 * Updates and reads allocate no objects.
 */
final class AmbientBlink {

    /**
     * The shortest wait between blinks, and before the first blink after the first update.
     */
    static final int MIN_DELAY_TICKS = ClockTicks.seconds(2.5).getTicksAsInt();

    /**
     * How much longer than the shortest wait the next blink may wait, drawn uniformly.
     */
    static final int DELAY_RANGE_TICKS = ClockTicks.seconds(4.5).getTicksAsInt();

    /**
     * The longest a due blink may go unobserved and still start. A blink found later than this waits a fresh delay,
     * so villagers coming back into view do not blink together.
     */
    static final int MISSED_START_TOLERANCE_TICKS = ClockTicks.seconds(1).getTicksAsInt();

    private final BakedAnimation blink;
    private final RandomDelaySchedule schedule;

    @Getter
    private final AnimationContribution closedEyelids;

    // The most closed pose's offsets from rest and their squared length, which turn a sampled lid pose into closure
    private final float[] closedOffsets;
    private final float closedSquaredLength;

    // Scratch for sampling the blink clip during reads
    private final AnimationPose blinkSample;

    /**
     * Creates a blink that schedules its first blink on its first update.
     *
     * @throws IllegalArgumentException if the clip is not baked for the villager rig or never moves the eyelids
     */
    AmbientBlink(@Nonnull BakedAnimation blink, @Nonnull Random random) {
        Validate.isTrue(blink.getRig() == VillagerAnimationRig.RIG,
                "Blink clip %s must be baked for the villager rig", blink.getId());
        this.blink = blink;
        this.schedule = new RandomDelaySchedule(MIN_DELAY_TICKS, DELAY_RANGE_TICKS, MISSED_START_TOLERANCE_TICKS, random);
        this.blinkSample = new AnimationPose(VillagerAnimationRig.RIG);

        AnimationMask eyelids = VillagerAnimationRig.EYELIDS;
        this.closedOffsets = new float[eyelids.size() * AnimationPose.COMPONENTS];
        float closedSquaredLength = 0.0F;
        // Keys sit on whole ticks and a sample between two keys blends them, which cannot reach past the more closed
        // key, so the most closed whole tick is the most closed pose the clip reaches
        for (int tick = 0; tick <= blink.getDurationTicks(); tick++) {
            blink.sampleInto(this.blinkSample, tick);
            float squaredLength = this.squaredOffsetLength();
            if (squaredLength > closedSquaredLength) {
                closedSquaredLength = squaredLength;
                this.copySampleOffsets(this.closedOffsets);
            }
        }
        Validate.isTrue(closedSquaredLength > 0.0F, "Blink clip %s never moves the eyelids", blink.getId());
        this.closedSquaredLength = closedSquaredLength;

        this.closedEyelids = new AnimationContribution(VillagerAnimationRig.RIG);
        for (int member = 0; member < eyelids.size(); member++) {
            int channel = eyelids.channelAt(member);
            float rest = VillagerAnimationRig.RIG.kind(channel).getRest();
            int offset = member * AnimationPose.COMPONENTS;
            this.closedEyelids.key(channel,
                    rest + this.closedOffsets[offset],
                    rest + this.closedOffsets[offset + 1],
                    rest + this.closedOffsets[offset + 2]);
        }
        this.closedEyelids.own(eyelids, 1.0F);
    }

    /**
     * Advances blink timing to the given tick, one blink per window of a {@link RandomDelaySchedule} with this class's
     * delays and missed-start tolerance.
     */
    void update(long tick) {
        this.schedule.update(tick, this.blink.getDurationTicks());
    }

    /**
     * How far the current blink has closed the eyelids at this sample, in [0, 1].
     * 0 meaning the eye is fully open; 1 meaning it's fully closed.
     */
    float closure(long tick, float partialTicks) {
        if (!this.schedule.isWindowOpenAt(tick, partialTicks)) {
            return 0.0F;
        }
        this.blink.sampleInto(this.blinkSample, this.schedule.windowElapsedTicks(tick, partialTicks));

        // Project onto the closed pose so that a clip moving the eyelids along one direction is reproduced exactly
        AnimationMask eyelids = VillagerAnimationRig.EYELIDS;
        float projection = 0.0F;
        for (int member = 0; member < eyelids.size(); member++) {
            int channel = eyelids.channelAt(member);
            float rest = VillagerAnimationRig.RIG.kind(channel).getRest();
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                float sampledOffset = this.blinkSample.get(channel, axis) - rest;
                projection += sampledOffset * this.closedOffsets[member * AnimationPose.COMPONENTS + axis];
            }
        }
        return Math.clamp(projection / this.closedSquaredLength, 0.0F, 1.0F);
    }

    private float squaredOffsetLength() {
        AnimationMask eyelids = VillagerAnimationRig.EYELIDS;
        float squaredLength = 0.0F;
        for (int member = 0; member < eyelids.size(); member++) {
            int channel = eyelids.channelAt(member);
            float rest = VillagerAnimationRig.RIG.kind(channel).getRest();
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                float offset = this.blinkSample.get(channel, axis) - rest;
                squaredLength += offset * offset;
            }
        }
        return squaredLength;
    }

    private void copySampleOffsets(@Nonnull float[] offsets) {
        AnimationMask eyelids = VillagerAnimationRig.EYELIDS;
        for (int member = 0; member < eyelids.size(); member++) {
            int channel = eyelids.channelAt(member);
            float rest = VillagerAnimationRig.RIG.kind(channel).getRest();
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                offsets[member * AnimationPose.COMPONENTS + axis] = this.blinkSample.get(channel, axis) - rest;
            }
        }
    }

}
