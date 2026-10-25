package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmPose;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.List;

/**
 * An immutable clip baked for one rig. Successful sampling and contribution fills allocate no objects.
 * <p>
 * Playback follows the clip's loop mode. Each track holds its first and last values outside its keyed range. Between
 * keys, values interpolate linearly in float using the earlier key's easing, clamped to [0, 1].
 */
public final class BakedAnimation {

    @Getter
    private final ResourceLocation id;

    @Getter
    private final AnimationRig rig;

    @Getter
    private final int durationTicks;

    @Getter
    private final LoopMode loopMode;

    @Getter
    private final int blendInTicks;

    @Getter
    private final int blendOutTicks;

    /**
     * Every channel a track keys.
     */
    @Getter
    private final AnimationMask keyedMask;

    // Primitive arrays avoid temporary values and iterators during sampling.
    // Track t keys channel trackChannels[t] with keys from [t, t + 1).
    // Key k is at keyTicks[k]; its AnimationPose.COMPONENTS values begin at keyValues[k * AnimationPose.COMPONENTS],
    // and keyEasings[k] controls interpolation toward the next key.
    private final int[] trackChannels;
    private final int[] trackKeyStarts;
    private final int[] keyTicks;
    private final float[] keyValues;
    private final Easing[] keyEasings;

    private final float[] coverage;
    private final float[] suppression;
    private final ArmConfigurationTimeline armClaims;

    private BakedAnimation(@Nonnull KeyframeAnimation clip, @Nonnull AnimationRig rig) {
        List<AnimationTrack<?>> tracks = clip.getTracks();
        int keyCount = 0;
        for (AnimationTrack<?> track : tracks) {
            keyCount += track.getKeyframes().size();
        }

        this.id = clip.getId();
        this.rig = rig;
        this.durationTicks = clip.getDurationTicks();
        this.loopMode = clip.getLoopMode();
        this.blendInTicks = clip.getBlendInTicks();
        this.blendOutTicks = clip.getBlendOutTicks();
        this.trackChannels = new int[tracks.size()];
        this.trackKeyStarts = new int[tracks.size() + 1];
        this.keyTicks = new int[keyCount];
        this.keyValues = new float[keyCount * AnimationPose.COMPONENTS];
        this.keyEasings = new Easing[keyCount];

        BitSet keyed = new BitSet(rig.getChannelCount());
        int nextKey = 0;
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            AnimationTrack<?> track = tracks.get(trackIndex);
            int channel = rig.indexOf(track.getTarget());
            // A second track on one channel would silently overwrite the first on every sample
            Validate.isTrue(!keyed.get(channel), "Clip %s keys channel %s more than once", this.id, track.getTarget().getId());
            keyed.set(channel);
            this.trackChannels[trackIndex] = channel;
            this.trackKeyStarts[trackIndex] = nextKey;
            nextKey = this.bakeKeys(track, nextKey);
        }
        this.trackKeyStarts[tracks.size()] = nextKey;
        this.keyedMask = AnimationMask.of(rig, keyed);

        this.coverage = bakeCoverage(clip, rig);
        this.suppression = bakeSuppression(clip);
        this.armClaims = clip.getArmConfigurationTimeline();
    }

    /**
     * Bakes the clip for the rig.
     *
     * @throws IllegalArgumentException if a track keys a target that is not a channel of the rig, two tracks key one
     *                                  channel, or a declared ownership names a channel the rig lacks
     */
    public static BakedAnimation bake(@Nonnull KeyframeAnimation clip, @Nonnull AnimationRig rig) {
        return new BakedAnimation(clip, rig);
    }

    /**
     * Writes this clip's value for every keyed channel at this point in its playback into the pose, leaving every
     * other channel as it was.
     *
     * @throws IllegalArgumentException if the pose uses a different rig instance
     */
    public void sampleInto(@Nonnull AnimationPose pose, float elapsedTicks) {
        this.requireRig(pose.getRig());
        this.sampleResolved(pose.values(), this.loopMode.resolveTick(elapsedTicks, this.durationTicks));
    }

    /**
     * Replaces the contribution's content with this clip at this point in its playback: its keyed values and keyed
     * channels, its ownership coverage, its suppression strengths, and its arm claims.
     *
     * @throws IllegalArgumentException if the contribution uses a different rig instance
     */
    public void contribute(@Nonnull AnimationContribution contribution, float elapsedTicks) {
        this.requireRig(contribution.getRig());
        float animationTick = this.loopMode.resolveTick(elapsedTicks, this.durationTicks);
        contribution.clear();
        this.sampleResolved(contribution.values().values(), animationTick);
        boolean[] keyed = contribution.keyedFlags();
        for (int track = 0; track < this.trackChannels.length; track++) {
            keyed[this.trackChannels[track]] = true;
        }
        System.arraycopy(this.coverage, 0, contribution.coverages(), 0, this.coverage.length);
        System.arraycopy(this.suppression, 0, contribution.suppressions(), 0, this.suppression.length);
        contribution.claimArms(this.armClaims.leftAt(animationTick), this.armClaims.rightAt(animationTick));
    }

    /**
     * The channel's ownership coverage at full slot weight, or zero if the clip does not own it.
     */
    public float coverage(int channelIndex) {
        return this.coverage[channelIndex];
    }

    /**
     * How strongly this clip suppresses the component, in [0, 1].
     */
    public float suppression(@Nonnull SuppressibleComponent component) {
        return this.suppression[component.ordinal()];
    }

    /**
     * The pose this clip claims for the left arm at this point in its playback, or null when it leaves the arm
     * unclaimed.
     */
    @Nullable
    public ArmPose leftArmClaimAt(float elapsedTicks) {
        return this.armClaims.leftAt(this.loopMode.resolveTick(elapsedTicks, this.durationTicks));
    }

    /**
     * The pose this clip claims for the right arm at this point in its playback, or null when it leaves the arm
     * unclaimed.
     */
    @Nullable
    public ArmPose rightArmClaimAt(float elapsedTicks) {
        return this.armClaims.rightAt(this.loopMode.resolveTick(elapsedTicks, this.durationTicks));
    }

    private void sampleResolved(@Nonnull float[] pose, float animationTick) {
        for (int track = 0; track < this.trackChannels.length; track++) {
            int destination = this.trackChannels[track] * AnimationPose.COMPONENTS;
            int firstKey = this.trackKeyStarts[track];
            int lastKey = this.trackKeyStarts[track + 1] - 1;

            if (animationTick <= this.keyTicks[firstKey]) {
                System.arraycopy(this.keyValues, firstKey * AnimationPose.COMPONENTS, pose, destination, AnimationPose.COMPONENTS);
                continue;
            }
            // Negating the comparison makes a NaN tick select the held final value
            if (!(animationTick <= this.keyTicks[lastKey])) {
                System.arraycopy(this.keyValues, lastKey * AnimationPose.COMPONENTS, pose, destination, AnimationPose.COMPONENTS);
                continue;
            }

            // The sample is past the first key; searching from the next key guarantees a predecessor for interpolation
            int lower = firstKey + 1;
            int upper = lastKey;
            while (lower < upper) {
                int middle = (lower + upper) >>> 1;
                if (animationTick <= this.keyTicks[middle]) {
                    upper = middle;
                } else {
                    lower = middle + 1;
                }
            }

            // Key ticks are unique and increasing, so the interpolation span cannot be zero
            int fromKey = lower - 1;
            float normalized = (animationTick - this.keyTicks[fromKey]) / (this.keyTicks[lower] - this.keyTicks[fromKey]);
            float eased = Math.clamp(this.keyEasings[fromKey].apply(normalized), 0.0F, 1.0F);
            int from = fromKey * AnimationPose.COMPONENTS;
            int to = lower * AnimationPose.COMPONENTS;
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                float fromValue = this.keyValues[from + axis];
                pose[destination + axis] = fromValue + ((this.keyValues[to + axis] - fromValue) * eased);
            }
        }
    }

    private <V> int bakeKeys(@Nonnull AnimationTrack<V> track, int firstKey) {
        AnimationTarget<V> target = track.getTarget();
        int key = firstKey;
        for (Keyframe<V> keyframe : track.getKeyframes()) {
            this.keyTicks[key] = keyframe.tick();
            this.keyEasings[key] = keyframe.easingToNext();
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                this.keyValues[key * AnimationPose.COMPONENTS + axis] = target.component(keyframe.value(), axis);
            }
            key++;
        }
        return key;
    }

    private static float[] bakeCoverage(@Nonnull KeyframeAnimation clip, @Nonnull AnimationRig rig) {
        float[] coverage = new float[rig.getChannelCount()];
        if (clip.isExclusive()) {
            AnimationMask fullBody = rig.getFullBody();
            for (int member = 0; member < fullBody.size(); member++) {
                coverage[fullBody.channelAt(member)] = 1.0F;
            }
        }
        // Explicit declarations override exclusive defaults, including when they reduce a channel's coverage
        for (AnimationOwnership ownership : clip.getOwnership()) {
            AnimationMask mask = rig.resolve(ownership.mask());
            for (int member = 0; member < mask.size(); member++) {
                coverage[mask.channelAt(member)] = ownership.coverage();
            }
        }
        return coverage;
    }

    private static float[] bakeSuppression(@Nonnull KeyframeAnimation clip) {
        float[] suppression = new float[SuppressibleComponent.COUNT];
        for (SuppressibleComponent component : SuppressibleComponent.values()) {
            if (clip.isExclusive() || clip.getSuppressedComponents().contains(component)) {
                suppression[component.ordinal()] = 1.0F;
            }
        }
        return suppression;
    }

    private void requireRig(@Nonnull AnimationRig other) {
        // A direct branch avoids allocating diagnostic arguments for successful validation on the sampling path
        if (other != this.rig) {
            throw new IllegalArgumentException("Clip " + this.id + " is baked for rig " + this.rig.getName()
                    + ", not rig " + other.getName());
        }
    }

}
