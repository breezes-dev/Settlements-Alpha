package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds animation clips from tracks and composition declarations. An undeclared arm remains unclaimed.
 * Claim methods throw IllegalArgumentException for a second declaration of the same arm at the same tick.
 */
public final class TrackAnimationBuilder {

    private ResourceLocation id;
    private int durationTicks;
    private LoopMode loopMode = LoopMode.ONCE;
    private int blendInTicks;
    private int blendOutTicks;
    private final Map<Integer, ArmPose> leftArmClaims = new TreeMap<>();
    private final Map<Integer, ArmPose> rightArmClaims = new TreeMap<>();
    private final List<AnimationTrack<?>> tracks = new ArrayList<>();
    private final List<AnimationOwnership> ownership = new ArrayList<>();
    private final Set<SuppressibleComponent> suppressedComponents = EnumSet.noneOf(SuppressibleComponent.class);
    private boolean exclusive;

    public TrackAnimationBuilder id(@Nonnull ResourceLocation id) {
        this.id = id;
        return this;
    }

    public TrackAnimationBuilder durationTicks(int durationTicks) {
        this.durationTicks = durationTicks;
        return this;
    }

    public TrackAnimationBuilder loopMode(@Nonnull LoopMode loopMode) {
        this.loopMode = loopMode;
        return this;
    }

    public TrackAnimationBuilder blendInTicks(int blendInTicks) {
        this.blendInTicks = blendInTicks;
        return this;
    }

    public TrackAnimationBuilder blendOutTicks(int blendOutTicks) {
        this.blendOutTicks = blendOutTicks;
        return this;
    }

    /**
     * Claims both arms from the clip's first tick.
     */
    public TrackAnimationBuilder arms(@Nonnull ArmConfiguration armConfiguration) {
        return this.armConfigurationAt(0, armConfiguration);
    }

    /**
     * Claims both arms from this tick until each arm's next claim.
     */
    public TrackAnimationBuilder armConfigurationAt(int tick, @Nonnull ArmConfiguration armConfiguration) {
        this.claim(this.leftArmClaims, "left", tick, armConfiguration.left());
        this.claim(this.rightArmClaims, "right", tick, armConfiguration.right());
        return this;
    }

    /**
     * Claims the left arm from the clip's first tick, leaving the right arm as the clip otherwise claims it.
     */
    public TrackAnimationBuilder leftArm(@Nonnull ArmPose pose) {
        return this.leftArmAt(0, pose);
    }

    /**
     * Claims the right arm from the clip's first tick, leaving the left arm as the clip otherwise claims it.
     */
    public TrackAnimationBuilder rightArm(@Nonnull ArmPose pose) {
        return this.rightArmAt(0, pose);
    }

    /**
     * Claims the left arm from this tick until its next claim. Before its first claim the arm is unclaimed.
     */
    public TrackAnimationBuilder leftArmAt(int tick, @Nonnull ArmPose pose) {
        this.claim(this.leftArmClaims, "left", tick, pose);
        return this;
    }

    /**
     * Claims the right arm from this tick until its next claim. Before its first claim the arm is unclaimed.
     */
    public TrackAnimationBuilder rightArmAt(int tick, @Nonnull ArmPose pose) {
        this.claim(this.rightArmClaims, "right", tick, pose);
        return this;
    }

    /**
     * Owns every channel of the mask at full coverage.
     */
    public TrackAnimationBuilder owns(@Nonnull AnimationMask mask) {
        return this.owns(mask, 1.0F);
    }

    /**
     * Owns every channel of the mask at this coverage, replacing the coverage of any earlier declaration that names
     * the same channel.
     *
     * @param coverage how far an owned channel moves at full weight, in (0, 1]
     */
    public TrackAnimationBuilder owns(@Nonnull AnimationMask mask, float coverage) {
        this.ownership.add(new AnimationOwnership(mask, coverage));
        return this;
    }

    /**
     * Declares full-strength suppression for each named component.
     */
    public TrackAnimationBuilder suppresses(@Nonnull SuppressibleComponent... components) {
        this.suppressedComponents.addAll(List.of(components));
        return this;
    }

    /**
     * Declares full-body ownership and suppression of every component. Explicit ownership declarations override
     * the default full coverage.
     */
    public TrackAnimationBuilder playExclusively() {
        this.exclusive = true;
        return this;
    }

    public TrackAnimationBuilder track(@Nonnull AnimationTrack<?> track) {
        this.tracks.add(track);
        return this;
    }

    public KeyframeAnimation build() {
        return KeyframeAnimation.builder()
                .id(this.id)
                .durationTicks(this.durationTicks)
                .loopMode(this.loopMode)
                .blendInTicks(this.blendInTicks)
                .blendOutTicks(this.blendOutTicks)
                .tracks(this.tracks)
                .armConfigurationKeyframes(this.armConfigurationTimeline())
                .ownership(this.ownership)
                .suppressedComponents(this.suppressedComponents)
                .exclusive(this.exclusive)
                .build();
    }

    private void claim(@Nonnull Map<Integer, ArmPose> claims, @Nonnull String arm, int tick, @Nonnull ArmPose pose) {
        ArmPose earlier = claims.putIfAbsent(tick, pose);
        // Conflicting declarations are ambiguous even when they happen to specify the same pose
        Validate.isTrue(earlier == null, "Clip %s claims its %s arm twice at tick %s", this.id, arm, tick);
    }

    /**
     * Returns ordered keyframes carrying both arms' active claims at every declared tick.
     */
    private List<ArmConfigurationKeyframe> armConfigurationTimeline() {
        TreeSet<Integer> ticks = new TreeSet<>(this.leftArmClaims.keySet());
        ticks.addAll(this.rightArmClaims.keySet());
        if (ticks.isEmpty()) {
            return List.of();
        }

        List<ArmConfigurationKeyframe> timeline = new ArrayList<>();
        // An unclaimed starting keyframe prevents the timeline's first-value hold from claiming an arm too early
        if (ticks.first() > 0) {
            timeline.add(new ArmConfigurationKeyframe(0, null, null));
        }
        ArmPose left = null;
        ArmPose right = null;
        for (int tick : ticks) {
            left = this.leftArmClaims.getOrDefault(tick, left);
            right = this.rightArmClaims.getOrDefault(tick, right);
            timeline.add(new ArmConfigurationKeyframe(tick, left, right));
        }
        return timeline;
    }

}
