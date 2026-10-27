package dev.breezes.settlements.application.ai.behavior.usecases.villager.cartographer;

import dev.breezes.settlements.application.ai.behavior.runtime.BehaviorSupport;
import dev.breezes.settlements.application.ai.behavior.runtime.VillagerStateMachineBehavior;
import dev.breezes.settlements.application.ai.behavior.workflow.staged.StagedStep;
import dev.breezes.settlements.application.ai.behavior.workflow.state.BehaviorContext;
import dev.breezes.settlements.application.ai.behavior.workflow.state.registry.BehaviorStateType;
import dev.breezes.settlements.application.ai.behavior.workflow.state.registry.look.LookState;
import dev.breezes.settlements.application.ai.behavior.workflow.state.registry.outcomes.BehaviorOutcome;
import dev.breezes.settlements.application.ai.behavior.workflow.steps.StageKey;
import dev.breezes.settlements.application.ai.behavior.workflow.steps.StepResult;
import dev.breezes.settlements.application.ai.behavior.workflow.steps.TimeBasedStep;
import dev.breezes.settlements.application.ai.brain.ActivityArbiter;
import dev.breezes.settlements.application.ai.raid.RaidWaveMarkRegistry;
import dev.breezes.settlements.domain.ai.worldevent.WorldEventType;
import dev.breezes.settlements.domain.animation.AnimationArchetype;
import dev.breezes.settlements.domain.animation.CartographerAnimations;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.domain.world.location.Location;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.mixins.RaidMixin;
import lombok.CustomLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Sights a raid's selected spawn point and marks its pre-wave window once the spyglass is raised.
 * A committed mark survives the end or interruption of this behavior.
 */
@CustomLog(topic = "behavior")
public final class MarkRaidWaveBehavior extends VillagerStateMachineBehavior {

    /**
     * The sighting and its terminal state.
     */
    private enum SightingStage implements StageKey {
        SIGHT,
        END
    }

    private final RaidWaveMarkRegistry marks;

    @Nullable
    private Raid raid;
    @Nullable
    private BlockPos sightingPosition;
    private int window;
    private boolean marked;

    public MarkRaidWaveBehavior(@Nonnull MarkRaidWaveConfig config,
                                @Nonnull RaidWaveMarkRegistry marks,
                                @Nonnull BehaviorSupport support) {
        super(log, ClockTicks.ZERO.asTickable(), ClockTicks.ZERO.asTickable(), support, config.experienceReward());
        this.marks = marks;
        this.initializeStateMachine(StagedStep.<BaseVillager>builder()
                .name("MarkRaidWaveBehavior")
                .initialStage(SightingStage.SIGHT)
                .stageStepMap(Map.of(SightingStage.SIGHT, this.sightingStep()))
                .nextStage(SightingStage.END)
                .build(), SightingStage.END);
    }

    @Override
    protected void onBehaviorStart(@Nonnull Level world,
                                   @Nonnull BaseVillager villager,
                                   @Nonnull BehaviorContext<BaseVillager> context) {
        this.raid = null;
        this.sightingPosition = null;
        this.marked = false;

        ServerLevel level = context.getLevel();
        Raid candidate = level.getRaidAt(villager.blockPosition());
        if (!ActivityArbiter.isRaidAwaitingWave(candidate)) {
            this.requestStop("The raid no longer awaits a wave");
            return;
        }

        Optional<BlockPos> selection = ((RaidMixin) candidate).getRaidWaveSpawnPos();
        int candidateWindow = candidate.getGroupsSpawned();
        if (selection.isEmpty() || !this.marks.acquire(candidate, candidateWindow, villager.getUUID())) {
            this.requestStop("The pre-wave window has no available selection");
            return;
        }

        this.raid = candidate;
        this.window = candidateWindow;
        this.sightingPosition = selection.get().immutable();
        context.setState(BehaviorStateType.LOOK_TARGET, LookState.ofLocation(Location.of(this.sightingPosition, level)));
    }

    @Override
    protected void onBehaviorStop(@Nonnull Level world, @Nonnull BaseVillager villager) {
        if (this.raid != null && !this.marked) {
            this.marks.release(this.raid, this.window, villager.getUUID());
        }

        this.raid = null;
        this.sightingPosition = null;
        villager.clearHeldItem();
        villager.setMotion(AnimationArchetype.NONE);
    }

    private TimeBasedStep<BaseVillager> sightingStep() {
        return TimeBasedStep.<BaseVillager>builder()
                .withTickable(CartographerAnimations.SIGHT_DURATION.asTickable())
                .onStart(context -> {
                    BaseVillager villager = context.getInitiator();
                    villager.getNavigationManager().stop();
                    villager.setHeldItem(Items.SPYGLASS.getDefaultInstance());
                    villager.triggerMotion(AnimationArchetype.SIGHT_WITH_SPYGLASS);
                    return StepResult.noOp();
                })
                .everyTick(context -> {
                    BaseVillager villager = context.getInitiator();
                    if (!this.marked && (this.raid == null
                            || !this.marks.renew(this.raid, this.window, villager.getUUID()))) {
                        return StepResult.transition(SightingStage.END);
                    }
                    this.refreshSelection(context);
                    return StepResult.noOp();
                })
                .addKeyFrame(CartographerAnimations.SIGHT_RAISE_TIME, this::markWindow)
                .onEnd(context -> StepResult.transition(SightingStage.END))
                .build();
    }

    private StepResult markWindow(@Nonnull BehaviorContext<BaseVillager> context) {
        BaseVillager villager = context.getInitiator();
        Raid activeRaid = this.raid;
        if (activeRaid == null || context.getLevel().getRaidAt(villager.blockPosition()) != activeRaid
                || !ActivityArbiter.isRaidAwaitingWave(activeRaid)
                || activeRaid.getGroupsSpawned() != this.window
                || !this.marks.mark(activeRaid, this.window, villager.getUUID())) {
            return StepResult.transition(SightingStage.END);
        }

        // Commitment is independent of the remaining hold, so interruption cannot revoke or duplicate the reward.
        this.marked = true;
        this.rewardExperience(villager);
        context.declarePrimaryDeed(BehaviorOutcome.forDeed(WorldEventType.RAID_WAVE_MARKED, null)).markSucceeded();
        return StepResult.noOp();
    }

    private void refreshSelection(@Nonnull BehaviorContext<BaseVillager> context) {
        Raid activeRaid = this.raid;
        if (activeRaid == null) {
            return;
        }

        Optional<BlockPos> selection = ((RaidMixin) activeRaid).getRaidWaveSpawnPos();
        // An empty selection can be temporary; retain the last known aim until vanilla selects again.
        if (selection.isPresent() && !selection.get().equals(this.sightingPosition)) {
            this.sightingPosition = selection.get().immutable();
            context.setState(BehaviorStateType.LOOK_TARGET,
                    LookState.ofLocation(Location.of(this.sightingPosition, context.getLevel())));
        }
    }

}
