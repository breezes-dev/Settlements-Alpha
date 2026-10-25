package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/**
 * Mutable brow reaction that follows the look residual in the drawn head's axes.
 */
final class BrowReaction {

    private static final float RISE_TIME_CONSTANT_TICKS = 0.1F * ClockTicks.TICKS_PER_SECOND;
    private static final float RETURN_TIME_CONSTANT_TICKS = 0.5F * ClockTicks.TICKS_PER_SECOND;

    private static final float ROLL_RADIANS_PER_UNIT_RIGHTWARD = 16.0F * Mth.DEG_TO_RAD;
    private static final float RAISE_PER_UNIT_SIDEWAYS = 6.0F;
    private static final float OFFSET_PER_UNIT_DOWNWARD = 0.6F;

    // Vertical offsets of the brow, in model units, where a negative offset raises it.
    static final float RAISED_OFFSET_LIMIT = -1.5F;
    static final float LOWERED_OFFSET_LIMIT = 0.5F;

    // The highest offset procedural brow motion may raise the brow to; it never raises an authored brow already higher
    static final float PROCEDURAL_RAISE_LIMIT = -0.8F;

    // Widest final brow roll either way, in radians.
    static final float MAX_ROLL_RADIANS = 20.0F * Mth.DEG_TO_RAD;

    private static final int BROW_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int BROW_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.MONOBROW_ROTATION);
    private static final int VERTICAL_AXIS = 1;
    private static final int ROLL_AXIS = 2;

    private final ExponentialFollower offset = ExponentialFollower.linear();
    private final ExponentialFollower roll = ExponentialFollower.linear();

    /**
     * Advances toward the reaction the head frame's look residual calls for. A sideways residual rolls up the brow's
     * end on that side and raises the brow; a residual above the head lifts it, and one below drops it.
     * <p>
     * Each component moving away from neutral follows on a shorter time constant than one moving back toward it. A
     * reset lands on the reaction at once.
     */
    void follow(@Nonnull HeadFrame headFrame, @Nonnull FollowerStep step) {
        float rightward = headFrame.lookResidualRightward();
        // A positive roll raises the brow's end on the villager's right, the side a rightward residual looks toward
        float targetRoll = ROLL_RADIANS_PER_UNIT_RIGHTWARD * rightward;
        // A negative offset raises the brow, so a sideways residual raises it whichever side it lies on
        float targetOffset = OFFSET_PER_UNIT_DOWNWARD * headFrame.lookResidualDownward()
                - RAISE_PER_UNIT_SIDEWAYS * Math.abs(rightward);
        this.roll.follow(targetRoll, decayFactor(step, this.roll.getValue(), targetRoll));
        this.offset.follow(targetOffset, decayFactor(step, this.offset.getValue(), targetOffset));
    }

    /**
     * Adds the weight-scaled reaction to the pose's brow within limit. Then bounds the brow's final vertical
     * offset and roll regardless of weight. Lowering and roll are not limited against the authored brow.
     *
     * @param pose           pose on the villager rig; not validated
     * @param weight         fraction of the reaction to add, in [0, 1]; not validated
     * @param authoredOffset the brow's vertical offset before any procedural brow motion was added to the pose
     */
    void apply(@Nonnull AnimationPose pose, float weight, float authoredOffset) {
        float composedOffset = pose.get(BROW_TRANSLATION, VERTICAL_AXIS) + weight * this.offset.getValue();
        float finalOffset = Math.max(composedOffset, Math.min(authoredOffset, PROCEDURAL_RAISE_LIMIT));
        float finalRoll = pose.get(BROW_ROTATION, ROLL_AXIS) + weight * this.roll.getValue();
        pose.set(BROW_TRANSLATION, VERTICAL_AXIS, Math.clamp(finalOffset, RAISED_OFFSET_LIMIT, LOWERED_OFFSET_LIMIT));
        pose.set(BROW_ROTATION, ROLL_AXIS, Math.clamp(finalRoll, -MAX_ROLL_RADIANS, MAX_ROLL_RADIANS));
    }

    private static float decayFactor(@Nonnull FollowerStep step, float value, float target) {
        // Away from neutral the brow keeps pace with the residual; back toward it, it lingers after the head arrives
        boolean movesAwayFromNeutral = Math.abs(target) > Math.abs(value);
        return step.decayFactor(movesAwayFromNeutral ? RISE_TIME_CONSTANT_TICKS : RETURN_TIME_CONSTANT_TICKS);
    }

}
