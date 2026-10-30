package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.domain.time.ClockTicks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Composes one villager's animation in a fixed slot order, retaining live animation state separately from evaluation
 * scratch. All animation timing uses the observation's sample key. Steady-state advances and evaluations allocate no
 * objects.
 */
public final class VillagerAnimationPipeline {

    /**
     * How long the vanilla unhappy shake head animation takes to fade fully in or out.
     */
    static final float UNHAPPY_EASE_TICKS = ClockTicks.seconds(0.25).getTicksAsInt();

    /**
     * The limb-swing amount at and above which the gait has full weight.
     */
    static final float FULL_WEIGHT_LIMB_SWING_AMOUNT = 0.35F;

    private static final float STANDSTILL_LIMB_SWING_AMOUNT = 0.01F;

    // The limb-swing distance of one full limb cycle in vanilla's walk animation
    private static final float VANILLA_LIMB_SWING_CYCLE = (float) ((Math.PI * 2.0D) / 0.6662D);

    private static final int BROW_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int VERTICAL_AXIS = 1;

    /**
     * What the synced motion asks of the Action slot, kept apart from the layer playing it, which can expire or change
     * clip while the request stands.
     */
    private enum ActionIntent {
        NONE,
        /**
         * A new generation asked for its archetype's clip once.
         */
        ONE_SHOT,
        /**
         * The archetype is held until the synced motion changes.
         */
        HELD,
        /**
         * Already under way at the first observation with a clip that does not loop, so nothing plays unless a later
         * held item resolves the archetype to a clip that loops.
         */
        SEEN_UNDER_WAY
    }

    private final AnimationLibrary animationLibrary;
    private final LocomotionAnimationLibrary locomotionLibrary;
    private final BakedAnimationRegistry registry;
    private final UmbrellaAnimator umbrellaAnimator;
    private final LanternAnimator lanternAnimator;

    private final BakedAnimation breathingCycle;
    private final BakedAnimation sleep;
    private final int breathingPhaseOffsetTicks;
    private final IdleAnimationScheduler idleAnimationScheduler;
    private final AmbientBlink blink;
    private final AmbientBrow ambientBrow;

    private boolean observed;
    private AnimationArchetype lastSeenArchetype = AnimationArchetype.NONE;
    private byte lastSeenGeneration;
    private AnimationSelectionContext lastResolvedContext = AnimationSelectionContext.generic();
    private ActionIntent actionIntent = ActionIntent.NONE;
    private long actionStartTick;
    /**
     * Whether the most recently expired action layer had an empty current clip.
     */
    private boolean actionExpiredUnplayed;
    // The one layer every action plays through, restarted for each so starting an action allocates nothing
    private final AnimationLayer actionLayerStorage;
    // The storage layer while an action plays, otherwise null
    @Nullable
    private AnimationLayer actionLayer;
    private boolean sleeping;
    private long sleepStartTick;
    private boolean unhappy;
    private long unhappyEaseStartTick;
    private float unhappyEaseStartPartialTicks;
    private float unhappyEaseStartWeight;
    private final FollowerStep liveStep = new FollowerStep();
    private final HeadTrail headTrail = new HeadTrail();
    private final NoseLag noseLag = new NoseLag();
    private final BrowReaction browReaction = new BrowReaction();

    // Evaluation scratch, preallocated so evaluation allocates nothing.
    // TODO: Share this scratch across pipelines evaluated on the render thread, and the constant standing pose and
    //  closed eyelids across villagers, so each villager keeps only state that outlives one evaluation.
    private final ComponentSuppression suppression = new ComponentSuppression();
    private final LookAndUnhappySlots lookAndUnhappy = new LookAndUnhappySlots();
    private final AnimationContribution standingPose = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution gait = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution idleAnimation = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution currentAction = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution outgoingAction = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution posture = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution carry = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution lanternCarry = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution breathing = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution[] alternatives = new AnimationContribution[2];
    private final float[] shares = new float[2];
    private final HeadFrame headFrame = new HeadFrame();
    // Separate scratch keeps observational angles out of the live followers
    private final HeadTrail settledHeadTrail = new HeadTrail();
    private final NoseLag settledNoseLag = new NoseLag();
    private final BrowReaction settledBrowReaction = new BrowReaction();
    // Leave this unmeasured so observational evaluations always reset their followers
    private final FollowerStep settledStep = new FollowerStep();

    /**
     * Creates the pipeline for one villager with the supplied source of cosmetic variation.
     *
     * @param registry         holds the baked form of every clip the libraries supply
     * @param umbrellaAnimator this villager's own umbrella coordinator, advanced only by this pipeline
     * @param lanternAnimator  this villager's own lantern coordinator, advanced only by this pipeline
     * @param random           random source for scheduling various animations
     * @throws IllegalArgumentException if the registry lacks the sleep clip, the standing pose, an idle animation, or a
     *                                  clip of the ambient library, the blink clip never moves the eyelids, or a brow
     *                                  motion keys a channel outside the brow or declares ownership or suppression
     */
    public VillagerAnimationPipeline(@Nonnull AnimationLibrary animationLibrary,
                                     @Nonnull IdleAnimationLibrary idleAnimationLibrary,
                                     @Nonnull AmbientAnimationLibrary ambientAnimationLibrary,
                                     @Nonnull LocomotionAnimationLibrary locomotionLibrary,
                                     @Nonnull BakedAnimationRegistry registry,
                                     @Nonnull UmbrellaAnimator umbrellaAnimator,
                                     @Nonnull LanternAnimator lanternAnimator,
                                     @Nonnull Random random) {
        this.animationLibrary = animationLibrary;
        this.locomotionLibrary = locomotionLibrary;
        this.registry = registry;
        this.umbrellaAnimator = umbrellaAnimator;
        this.lanternAnimator = lanternAnimator;
        this.actionLayerStorage = AnimationLayer.persistent(animationLibrary.resolve(AnimationArchetype.NONE, AnimationSelectionContext.generic()), 0L);

        this.breathingCycle = registry.baked(ambientAnimationLibrary.breathingCycle());
        this.sleep = registry.baked(animationLibrary.resolve(AnimationArchetype.SLEEP, AnimationSelectionContext.generic()));
        this.breathingPhaseOffsetTicks = random.nextInt(Math.max(1, this.breathingCycle.getDurationTicks()));

        List<BakedAnimation> idleAnimations = new ArrayList<>();
        for (KeyframeAnimation idleAnimation : idleAnimationLibrary.idleAnimations()) {
            idleAnimations.add(registry.baked(idleAnimation));
        }
        this.idleAnimationScheduler = new IdleAnimationScheduler(idleAnimations, random);
        this.blink = new AmbientBlink(registry.baked(ambientAnimationLibrary.blink()), random);
        List<BakedAnimation> browMotions = new ArrayList<>();
        for (KeyframeAnimation browMotion : ambientAnimationLibrary.browMotions()) {
            browMotions.add(registry.baked(browMotion));
        }
        this.ambientBrow = new AmbientBrow(browMotions, random);

        // The standing pose is constant, so its contribution is filled once and only read afterward
        registry.baked(locomotionLibrary.standingPose()).contribute(this.standingPose, 0.0F);
    }

    /**
     * Applies the observation to live animation state and fills the snapshot with the resulting pose.
     *
     * @throws IllegalArgumentException if the registry lacks the gait or action clip this sample plays
     */
    public void advance(@Nonnull VillagerAnimationObservation observation,
                        @Nonnull VillagerAnimationSnapshot snapshot) {
        this.update(observation);
        this.evaluate(observation, snapshot, this.headTrail, this.noseLag, this.browReaction, this.liveStep);
    }

    /**
     * Fills the snapshot from committed animation state at the observation's sample key, taking look and movement from
     * the observation. The look has no smoothing but retains its yaw limit, the nose has no procedural lag, and the
     * brow reacts as fully as its look residual calls for. Leaves live animation state unchanged.
     *
     * @throws IllegalArgumentException if the registry lacks the gait or action clip this sample plays
     */
    public void evaluateObservationally(@Nonnull VillagerAnimationObservation observation,
                                        @Nonnull VillagerAnimationSnapshot snapshot) {
        this.settledHeadTrail.follow(observation, this.settledStep);
        this.evaluate(observation, snapshot, this.settledHeadTrail, this.settledNoseLag, this.settledBrowReaction,
                this.settledStep);
    }

    private void update(@Nonnull VillagerAnimationObservation observation) {
        long tick = observation.getAgeTicks();
        float partialTicks = observation.getPartialTicks();
        if (!this.observed) {
            this.observed = true;
            this.applyFirstObservation(observation);
        }

        boolean wakes = this.sleeping && !observation.isSleeping();
        if (observation.isSleeping() && !this.sleeping) {
            this.sleepStartTick = tick;
        }
        this.sleeping = observation.isSleeping();

        this.updateAction(observation);
        AnimationLayer layer = this.actionLayer;
        if (layer != null) {
            layer.update(tick, partialTicks);
            if (layer.isExpired(tick, partialTicks)) {
                this.actionExpiredUnplayed = layer.getCurrentAnimation().getDurationTicks() <= 0;
                this.actionLayer = null;
            }
        }

        boolean idleAnimationEligible = !this.sleeping
                && this.actionLayer == null
                && locomotionWeight(observation.getLimbSwingAmount()) <= 0.0F;
        this.idleAnimationScheduler.update(tick, idleAnimationEligible);
        this.blink.update(tick);
        this.ambientBrow.update(tick);
        this.updateUnhappy(observation.isUnhappy(), tick, partialTicks);
        // A villager going to bed in the rain stows its umbrella rather than sleeping under it
        this.umbrellaAnimator.update(tick, !this.sleeping && observation.isUmbrellaRequested());
        ArmConfiguration armConfiguration = this.resolveArmClaims(observation);
        boolean lanternYields = armConfiguration.left() == ArmPose.STRAIGHT
                || armConfiguration.right() == ArmPose.STRAIGHT
                || observation.isHandItemShown()
                || this.sleeping
                || this.umbrellaAnimator.isVisible(tick, partialTicks);
        this.lanternAnimator.update(tick, partialTicks, observation.isLanternCheckDecided(), observation.isLanternRequested(),
                lanternYields);

        // Sleep draws the villager in a bed pose the followers never tracked, so they start over on waking
        this.liveStep.measure(tick, partialTicks, wakes);
        this.headTrail.follow(observation, this.liveStep);
    }

    /**
     * Fills the snapshot and advances the supplied nose and brow followers; animation schedules are left unchanged.
     */
    private void evaluate(@Nonnull VillagerAnimationObservation observation,
                          @Nonnull VillagerAnimationSnapshot snapshot,
                          @Nonnull HeadTrail headTrail,
                          @Nonnull NoseLag noseLag,
                          @Nonnull BrowReaction browReaction,
                          @Nonnull FollowerStep step) {
        long tick = observation.getAgeTicks();
        float partialTicks = observation.getPartialTicks();
        AnimationPose pose = snapshot.getPose();
        pose.resetToRest();
        this.suppression.reset();

        this.lookAndUnhappy.apply(pose, this.suppression, observation, headTrail,
                this.unhappyWeight(tick, partialTicks));
        this.applyLocomotion(pose, observation);
        this.applyIdleAnimation(pose, tick, partialTicks);
        this.applyAction(pose, tick, partialTicks);
        if (this.sleeping) {
            float sleepElapsedTicks = Math.max(0.0F, (tick - this.sleepStartTick) + partialTicks);
            this.sleep.contribute(this.posture, sleepElapsedTicks);
            AnimationCompositor.applyContribution(pose, this.suppression, 1.0F, this.posture);
        }

        this.applyCarry(pose, snapshot, tick, partialTicks);

        // Ambient applies above every authored slot so breathing shows over an action's torso and a blink closes the
        // eyes over any authored lid pose
        float breathingWeight = this.suppression.remaining(SuppressibleComponent.BREATH);
        if (breathingWeight > 0.0F) {
            this.breathingCycle.contribute(this.breathing, this.breathingPlaybackTicks(tick, partialTicks));
            AnimationCompositor.applyContribution(pose, this.suppression, breathingWeight, this.breathing);
        }
        float blinkWeight = this.blink.closure(tick, partialTicks)
                * this.suppression.remaining(SuppressibleComponent.BLINK);
        AnimationCompositor.applyContribution(pose, this.suppression, blinkWeight, this.blink.getClosedEyelids());
        float browWeight = this.suppression.remaining(SuppressibleComponent.BROW);
        // Read before the ambient brow adds to it, so the procedural raise is limited against the authored brow alone
        float authoredBrowOffset = pose.get(BROW_TRANSLATION, VERTICAL_AXIS);
        this.ambientBrow.apply(pose, this.suppression, browWeight, tick, partialTicks);

        // Measure the fully composed head so torso motion also drives the followers
        this.headFrame.compose(pose, observation);
        noseLag.follow(this.headFrame, observation, step);
        browReaction.follow(this.headFrame, step);

        // The bounds and the pupil containment are the last writes to their channels, so they bound every source
        noseLag.apply(pose, this.suppression.remaining(SuppressibleComponent.NOSE_LAG), observation);
        browReaction.apply(pose, browWeight, authoredBrowOffset);
        Gaze.apply(pose, this.suppression.remaining(SuppressibleComponent.GAZE), this.headFrame);
        PupilContainment.apply(pose);

        snapshot.setArmConfiguration(this.resolveArmClaims(observation));
    }

    private void applyFirstObservation(@Nonnull VillagerAnimationObservation observation) {
        long tick = observation.getAgeTicks();
        // A villager first seen unhappy is already mid-shake rather than starting one
        this.unhappy = observation.isUnhappy();
        this.unhappyEaseStartWeight = observation.isUnhappy() ? 1.0F : 0.0F;
        this.unhappyEaseStartTick = tick;
        this.unhappyEaseStartPartialTicks = observation.getPartialTicks();

        // A one-shot first seen under way is over or already partly missed, and a late replay is worse than skipping
        AnimationArchetype archetype = observation.getMotionArchetype();
        this.lastSeenArchetype = archetype;
        this.lastSeenGeneration = observation.getMotionGeneration();
        this.lastResolvedContext = observation.getSelectionContext();
        if (archetype == AnimationArchetype.NONE) {
            return;
        }
        this.actionStartTick = tick;
        KeyframeAnimation clip = this.animationLibrary.resolve(archetype, observation.getSelectionContext());
        if (loops(clip)) {
            this.holdActionSeenUnderWay(clip);
        } else {
            this.actionIntent = ActionIntent.SEEN_UNDER_WAY;
        }
    }

    /**
     * Holds an already-running looping action at full weight, with no blend-in.
     */
    private void holdActionSeenUnderWay(@Nonnull KeyframeAnimation clip) {
        this.actionIntent = ActionIntent.HELD;
        this.playActionLayer(clip, this.actionStartTick - clip.getBlendInTicks(), false);
    }

    private void updateAction(@Nonnull VillagerAnimationObservation observation) {
        long tick = observation.getAgeTicks();
        AnimationArchetype archetype = observation.getMotionArchetype();
        byte generation = observation.getMotionGeneration();
        AnimationSelectionContext context = observation.getSelectionContext();

        if (archetype != this.lastSeenArchetype || generation != this.lastSeenGeneration) {
            boolean newGeneration = generation != this.lastSeenGeneration;
            this.lastSeenArchetype = archetype;
            this.lastSeenGeneration = generation;
            this.lastResolvedContext = context;
            if (archetype == AnimationArchetype.NONE) {
                this.actionIntent = ActionIntent.NONE;
                if (this.actionLayer != null) {
                    this.actionLayer.beginClear(tick);
                }
            } else {
                this.startAction(newGeneration ? ActionIntent.ONE_SHOT : ActionIntent.HELD,
                        this.animationLibrary.resolve(archetype, context), tick);
            }
            return;
        }

        if (context.equals(this.lastResolvedContext)) {
            return;
        }
        this.lastResolvedContext = context;
        if (this.actionIntent != ActionIntent.NONE) {
            // The held item syncs after the archetype, so the first resolution may have used the wrong item category
            this.resolveActionAgain(this.animationLibrary.resolve(archetype, context), tick);
        }
    }

    private void startAction(@Nonnull ActionIntent intent, @Nonnull KeyframeAnimation clip, long tick) {
        this.actionIntent = intent;
        this.actionStartTick = tick;
        boolean playsOnce = intent == ActionIntent.ONE_SHOT;
        if (this.actionLayer == null) {
            this.playActionLayer(clip, tick, playsOnce);
            return;
        }
        this.actionLayer.replace(clip, tick, playsOnce);
    }

    /**
     * Plays the current action's clip as resolved for a new held item, keeping the action's lifetime and start, so a
     * one-shot plays on from where it is rather than restarting.
     */
    private void resolveActionAgain(@Nonnull KeyframeAnimation clip, long tick) {
        AnimationLayer layer = this.actionLayer;
        if (layer != null) {
            // Swapped in place rather than pushed, so the action neither restarts nor crossfades from itself
            layer.swapCurrentAnimation(clip);
            return;
        }
        switch (this.actionIntent) {
            // Only a one-shot that expired on the empty clip, before its held item arrived, is played again; one that
            // played its own clip to the end is over, whatever clip the new item would select
            case ONE_SHOT -> {
                if (this.actionExpiredUnplayed && tick - this.actionStartTick < clip.getDurationTicks()) {
                    this.playActionLayer(clip, this.actionStartTick, true);
                }
            }
            case SEEN_UNDER_WAY -> {
                if (loops(clip)) {
                    this.holdActionSeenUnderWay(clip);
                }
            }
            case HELD, NONE -> {
            }
        }
    }

    private void playActionLayer(@Nonnull KeyframeAnimation clip, long startTick, boolean playsOnce) {
        this.actionLayerStorage.restart(clip, startTick, playsOnce);
        this.actionLayer = this.actionLayerStorage;
    }

    private void updateUnhappy(boolean unhappy, long tick, float partialTicks) {
        if (unhappy == this.unhappy) {
            return;
        }
        // Start from the weight at this sample so a reversal mid-ease continues rather than jumping
        this.unhappyEaseStartWeight = this.unhappyWeight(tick, partialTicks);
        this.unhappy = unhappy;
        this.unhappyEaseStartTick = tick;
        this.unhappyEaseStartPartialTicks = partialTicks;
    }

    private float unhappyWeight(long tick, float partialTicks) {
        float elapsedTicks = Math.max(0.0F,
                (tick - this.unhappyEaseStartTick) + (partialTicks - this.unhappyEaseStartPartialTicks));
        float progress = Math.min(elapsedTicks / UNHAPPY_EASE_TICKS, 1.0F);
        float target = this.unhappy ? 1.0F : 0.0F;
        return this.unhappyEaseStartWeight + (target - this.unhappyEaseStartWeight) * progress;
    }

    /**
     * Crossfades the standing pose into the gait by the locomotion weight.
     */
    private void applyLocomotion(@Nonnull AnimationPose pose, @Nonnull VillagerAnimationObservation observation) {
        float locomotionWeight = locomotionWeight(observation.getLimbSwingAmount());
        // At a standstill or a full stride one alternative holds the whole share, so it applies alone
        if (locomotionWeight <= 0.0F) {
            AnimationCompositor.applyContribution(pose, this.suppression, 1.0F, this.standingPose);
            return;
        }
        BakedAnimation gaitClip = this.registry.baked(this.locomotionLibrary.resolve(observation.getNavigationType()));
        gaitClip.contribute(this.gait, gaitPlaybackTicks(observation.getLimbSwing(), gaitClip.getDurationTicks()));
        if (locomotionWeight >= 1.0F) {
            AnimationCompositor.applyContribution(pose, this.suppression, 1.0F, this.gait);
            return;
        }

        // Both alternatives own the legs, so they crossfade evenly, and the gait's breath suppression scales with it
        this.alternatives[0] = this.standingPose;
        this.shares[0] = 1.0F - locomotionWeight;
        this.alternatives[1] = this.gait;
        this.shares[1] = locomotionWeight;
        AnimationCompositor.applyCrossfade(pose, this.suppression, 1.0F, this.alternatives, this.shares, 2);
    }

    /**
     * Applies the scheduled idle animation, if one is playing.
     */
    private void applyIdleAnimation(@Nonnull AnimationPose pose, long tick, float partialTicks) {
        BakedAnimation current = this.idleAnimationScheduler.current();
        if (current == null) {
            return;
        }
        current.contribute(this.idleAnimation, this.idleAnimationScheduler.currentElapsedTicks(tick, partialTicks));
        AnimationCompositor.applyContribution(pose, this.suppression,
                this.idleAnimationScheduler.weight(tick, partialTicks), this.idleAnimation);
    }

    private void applyAction(@Nonnull AnimationPose pose, long tick, float partialTicks) {
        AnimationLayer layer = this.actionLayer;
        if (layer == null) {
            return;
        }
        this.registry.baked(layer.getCurrentAnimation())
                .contribute(this.currentAction, layer.currentElapsedTicks(tick, partialTicks));
        float weight = layer.weight(tick, partialTicks);
        KeyframeAnimation outgoing = layer.getOutgoingAnimation();
        if (outgoing == null) {
            AnimationCompositor.applyContribution(pose, this.suppression, weight, this.currentAction);
            return;
        }

        this.registry.baked(outgoing)
                .contribute(this.outgoingAction, layer.outgoingElapsedTicks(tick, partialTicks));
        float incomingShare = layer.incomingShare(tick, partialTicks);
        this.alternatives[0] = this.currentAction;
        this.shares[0] = incomingShare;
        this.alternatives[1] = this.outgoingAction;
        this.shares[1] = 1.0F - incomingShare;
        AnimationCompositor.applyCrossfade(pose, this.suppression, weight, this.alternatives, this.shares, 2);
    }

    private void applyCarry(@Nonnull AnimationPose pose,
                            @Nonnull VillagerAnimationSnapshot snapshot,
                            long tick,
                            float partialTicks) {
        boolean umbrellaVisible = this.umbrellaAnimator.isVisible(tick, partialTicks);
        snapshot.setUmbrellaVisible(umbrellaVisible);
        AnimationPose umbrellaPose = snapshot.getUmbrellaPose();
        umbrellaPose.resetToRest();
        if (umbrellaVisible) {
            this.umbrellaAnimator.contributeCarry(this.carry, tick, partialTicks);
            AnimationCompositor.applyContribution(pose, this.suppression, 1.0F, this.carry);
            this.umbrellaAnimator.sampleCanopyInto(umbrellaPose, tick, partialTicks);
        }
        this.lanternAnimator.contributeCarry(this.lanternCarry, tick, partialTicks);
        AnimationCompositor.applyContribution(pose, this.suppression, 1.0F, this.lanternCarry);
        snapshot.setLanternVisible(this.lanternAnimator.isVisible(tick, partialTicks));
    }

    /**
     * Takes each arm's pose from the highest claimant that claims it, defaulting to crossed.
     */
    private ArmConfiguration resolveArmClaims(@Nonnull VillagerAnimationObservation observation) {
        return ArmConfiguration.of(this.resolveArmClaim(observation, true), this.resolveArmClaim(observation, false));
    }

    private ArmPose resolveArmClaim(@Nonnull VillagerAnimationObservation observation, boolean leftArm) {
        long tick = observation.getAgeTicks();
        float partialTicks = observation.getPartialTicks();
        ArmPose claim = this.sleeping
                ? armClaimAt(this.sleep, Math.max(0.0F, (tick - this.sleepStartTick) + partialTicks), leftArm)
                : null;
        if (claim != null) {
            return claim;
        }

        // Claims outlive blend weights, so only the current clip and the layer's lifetime choose arm geometry.
        AnimationLayer layer = this.actionLayer;
        if (layer != null) {
            claim = armClaimAt(this.registry.baked(layer.getCurrentAnimation()),
                    layer.currentElapsedTicks(tick, partialTicks), leftArm);
            if (claim != null) {
                return claim;
            }
        }
        BakedAnimation idleClip = this.idleAnimationScheduler.current();
        if (idleClip != null) {
            claim = armClaimAt(idleClip, this.idleAnimationScheduler.currentElapsedTicks(tick, partialTicks), leftArm);
            if (claim != null) {
                return claim;
            }
        }
        boolean moving = locomotionWeight(observation.getLimbSwingAmount()) > 0.0F;
        BakedAnimation locomotionClip = this.registry.baked(moving
                ? this.locomotionLibrary.resolve(observation.getNavigationType())
                : this.locomotionLibrary.standingPose());
        claim = armClaimAt(locomotionClip, moving
                ? gaitPlaybackTicks(observation.getLimbSwing(), locomotionClip.getDurationTicks()) : 0.0F, leftArm);
        return claim == null ? ArmPose.CROSSED : claim;
    }

    @Nullable
    private static ArmPose armClaimAt(@Nonnull BakedAnimation clip, float elapsedTicks, boolean leftArm) {
        return leftArm ? clip.leftArmClaimAt(elapsedTicks) : clip.rightArmClaimAt(elapsedTicks);
    }

    /**
     * The gait's weight for this limb-swing amount, in [0, 1]: zero at a standstill and one at full stride.
     */
    private static float locomotionWeight(float limbSwingAmount) {
        if (limbSwingAmount <= STANDSTILL_LIMB_SWING_AMOUNT) {
            return 0.0F;
        }
        return Math.clamp(limbSwingAmount / FULL_WEIGHT_LIMB_SWING_AMOUNT, 0.0F, 1.0F);
    }

    /**
     * The playback position in a gait clip of this duration for this limb-swing distance, mapping one vanilla limb
     * cycle onto one clip cycle. Returns zero for a clip without duration.
     */
    private static float gaitPlaybackTicks(float limbSwing, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }
        // Limb swing is phased by distance walked rather than by time, so planted feet hold their cadence at any speed
        return limbSwing * durationTicks / VANILLA_LIMB_SWING_CYCLE;
    }

    private float breathingPlaybackTicks(long tick, float partialTicks) {
        int cycleTicks = this.breathingCycle.getDurationTicks();
        if (cycleTicks <= 0) {
            return 0.0F;
        }
        // Reduce by the cycle in long arithmetic first; one float sum of the age and partial tick loses the partial
        // tick on a long-lived villager
        return Math.floorMod(tick + this.breathingPhaseOffsetTicks, cycleTicks) + partialTicks;
    }

    private static boolean loops(@Nonnull KeyframeAnimation clip) {
        return clip.getLoopMode() != LoopMode.ONCE;
    }

}
