package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

/**
 * One villager's occasional brow motions, played one at a time on a {@link RandomDelaySchedule}, each at a random
 * strength, and added onto the brow.
 */
final class AmbientBrow {

    /**
     * The shortest wait between brow motions, and before the first one after the first update.
     */
    static final int MIN_DELAY_TICKS = ClockTicks.seconds(6).getTicksAsInt();

    /**
     * How much longer than the shortest wait the next brow motion may wait, drawn uniformly.
     */
    static final int DELAY_RANGE_TICKS = ClockTicks.seconds(14).getTicksAsInt();

    /**
     * The longest a due brow motion may go unobserved and still start. One found later than this waits a fresh delay,
     * so villagers coming back into view do not move their brows together.
     */
    static final int MISSED_START_TOLERANCE_TICKS = ClockTicks.seconds(1).getTicksAsInt();

    /**
     * The weakest strength a brow motion plays at, as a share of its authored extent. Each motion draws its strength
     * uniformly from this up to its full extent when it starts.
     */
    static final float MIN_MOTION_STRENGTH = 0.25F;

    private static final int NO_MOTION = -1;

    private final List<BakedAnimation> motions;
    private final Random random;
    private final RandomDelaySchedule schedule;

    // Scratch for sampling the playing motion during reads
    private final AnimationContribution motionSample = new AnimationContribution(VillagerAnimationRig.RIG);

    // Drawn when the previous motion starts, because the window the schedule opens next takes its length from it
    private int nextMotion;
    private int currentMotion = NO_MOTION;
    private float currentMotionStrength;

    /**
     * Creates an ambient brow that schedules its first motion on its first update. With no motions it never moves the
     * brow.
     *
     * @param motions brow motions baked for the villager rig, each keying only brow channels and declaring no
     *                ownership or suppression
     * @param random  supplies the delays and each motion's uniform choice and strength; may be shared with other
     *                villagers
     * @throws IllegalArgumentException if a motion is baked for another rig, keys a channel outside the brow, or
     *                                  declares ownership or suppression
     */
    AmbientBrow(@Nonnull List<BakedAnimation> motions, @Nonnull Random random) {
        for (BakedAnimation motion : motions) {
            requireAdditiveBrowMotion(motion);
        }
        this.motions = List.copyOf(motions);
        this.random = random;
        this.schedule = new RandomDelaySchedule(MIN_DELAY_TICKS, DELAY_RANGE_TICKS, MISSED_START_TOLERANCE_TICKS,
                random);
        this.nextMotion = this.motions.isEmpty() ? NO_MOTION : this.randomMotion();
    }

    /**
     * Advances brow timing to the given tick, one motion per window of the schedule with this class's delays and
     * missed-start tolerance.
     */
    void update(long tick) {
        if (this.nextMotion == NO_MOTION) {
            return;
        }
        if (this.schedule.update(tick, this.motions.get(this.nextMotion).getDurationTicks())) {
            this.currentMotion = this.nextMotion;
            this.currentMotionStrength = MIN_MOTION_STRENGTH + (1.0F - MIN_MOTION_STRENGTH) * this.random.nextFloat();
            this.nextMotion = this.randomMotion();
        }
    }

    /**
     * Adds the brow motion playing at this sample onto the pose, at its drawn strength scaled by the given weight.
     * Leaves the pose unchanged while none plays or at a weight of zero or less.
     *
     * @param pose   pose on the villager rig
     * @param weight fraction of the drawn strength to add, clamped to [0, 1]
     */
    void apply(@Nonnull AnimationPose pose,
               @Nonnull ComponentSuppression suppression,
               float weight,
               long tick,
               float partialTicks) {
        // Negated so a NaN weight skips the sample
        if (this.currentMotion == NO_MOTION || !(weight > 0.0F) || !this.schedule.isWindowOpenAt(tick, partialTicks)) {
            return;
        }
        this.motions.get(this.currentMotion)
                .contribute(this.motionSample, this.schedule.windowElapsedTicks(tick, partialTicks));
        // Scaling the weight scales the motion, because a brow motion only ever adds
        AnimationCompositor.applyContribution(pose, suppression, weight * this.currentMotionStrength, this.motionSample);
    }

    private int randomMotion() {
        return this.random.nextInt(this.motions.size());
    }

    private static void requireAdditiveBrowMotion(@Nonnull BakedAnimation motion) {
        AnimationRig rig = VillagerAnimationRig.RIG;
        Validate.isTrue(motion.getRig() == rig, "Brow motion %s must be baked for the villager rig", motion.getId());
        for (int channel = 0; channel < rig.getChannelCount(); channel++) {
            Validate.isTrue(!motion.getKeyedMask().contains(channel) || VillagerAnimationRig.BROW.contains(channel),
                    "Brow motion %s keys %s, outside the brow", motion.getId(), rig.channel(channel).getId());
            Validate.isTrue(motion.coverage(channel) == 0.0F,
                    "Brow motion %s owns %s instead of adding to it", motion.getId(), rig.channel(channel).getId());
        }
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            Validate.isTrue(motion.suppression(component) == 0.0F,
                    "Brow motion %s suppresses %s", motion.getId(), component);
        }
    }

}
