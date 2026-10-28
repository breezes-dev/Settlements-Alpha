package dev.breezes.settlements.application.ai.behavior.usecases.villager.librarian;

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
import dev.breezes.settlements.bootstrap.registry.effects.MobEffectRegistry;
import dev.breezes.settlements.domain.ai.memory.MemoryTypeRegistry;
import dev.breezes.settlements.domain.ai.perception.PerceivedEntities;
import dev.breezes.settlements.domain.ai.worldevent.WorldEventType;
import dev.breezes.settlements.domain.animation.AnimationArchetype;
import dev.breezes.settlements.domain.animation.BindVexesAnimations;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.domain.world.location.Location;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.shared.util.RandomUtil;
import lombok.CustomLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Channels a spell at nearby free vexes to bind and weaken them.
 */
@CustomLog(topic = "behavior")
public final class BindVexesBehavior extends VillagerStateMachineBehavior {

    /**
     * The wait after a cast that bound nothing.
     * Keeps a librarian from channeling back to back while other librarians have every vex bound.
     */
    private static final ClockTicks EMPTY_CAST_RETRY = ClockTicks.seconds(10);

    private static final int DEFAULT_VEXES_PER_CAST = 2;

    private static final double GLYPH_SOURCE_ABOVE_EYES = 0.5D;
    private static final double GLYPH_SCATTER = 0.3D;

    private enum BindStage implements StageKey {
        CHANNEL,
        END
    }

    private final BindVexesConfig config;

    // Runtime state
    private final List<LivingEntity> targets;

    public BindVexesBehavior(@Nonnull BindVexesConfig config,
                             @Nonnull BehaviorSupport support) {
        // The cadence is held in a brain memory, so the behavior's own cooldowns stay empty
        super(log, ClockTicks.ZERO.asTickable(), ClockTicks.ZERO.asTickable(), support, config.experienceReward());
        this.config = config;
        this.targets = new ArrayList<>();
        this.initializeStateMachine(StagedStep.<BaseVillager>builder()
                .name("BindVexesBehavior")
                .initialStage(BindStage.CHANNEL)
                .stageStepMap(Map.of(BindStage.CHANNEL, this.channelStep()))
                .nextStage(BindStage.END)
                .build(), BindStage.END);
    }

    @Override
    protected void onBehaviorStart(@Nonnull Level world,
                                   @Nonnull BaseVillager villager,
                                   @Nonnull BehaviorContext<BaseVillager> context) {
        this.refreshTargets(villager, context.getLevel());
        if (this.targets.isEmpty()) {
            this.requestStop("No bindable entity is near");
            return;
        }

        // The memory lists hostiles nearest first
        context.setState(BehaviorStateType.LOOK_TARGET, LookState.ofEntity(this.targets.getFirst()));
    }

    @Override
    protected void onBehaviorStop(@Nonnull Level world, @Nonnull BaseVillager villager) {
        this.targets.clear();
        villager.setMotion(AnimationArchetype.NONE);
    }

    private void refreshTargets(@Nonnull BaseVillager villager, @Nonnull ServerLevel level) {
        this.targets.clear();
        int vexesPerCast = this.config.expertiseVexesPerCast()
                .getOrDefault(villager.getExpertise().getConfigName(), DEFAULT_VEXES_PER_CAST);
        villager.getSettlementsBrain().getMemory(MemoryTypeRegistry.NEARBY_HOSTILES)
                .orElse(PerceivedEntities.empty())
                .ofType(LivingEntity.class, hostile -> VexBinding.isBindable(hostile, level))
                .limit(vexesPerCast)
                .forEach(this.targets::add);
    }

    private TimeBasedStep<BaseVillager> channelStep() {
        return TimeBasedStep.<BaseVillager>builder()
                .withTickable(BindVexesAnimations.CHANNEL_DURATION.asTickable())
                .onStart(context -> {
                    BaseVillager villager = context.getInitiator();
                    villager.getNavigationManager().stop();
                    villager.setMotion(AnimationArchetype.BIND_VEXES);

                    Location.fromEntity(villager, false)
                            .playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F, SoundSource.NEUTRAL);
                    return StepResult.noOp();
                })
                .addKeyFrame(BindVexesAnimations.COMMIT_TIME, this::bindTargets)
                .onEnd(context -> StepResult.transition(BindStage.END))
                .build();
    }

    private StepResult bindTargets(@Nonnull BehaviorContext<BaseVillager> context) {
        BaseVillager villager = context.getInitiator();
        ServerLevel level = context.getLevel();

        // The wave can end during the channel. No cooldown then, so the next wave is bound as soon as it spawns
        if (!ActivityArbiter.isRaidWaveLive(level.getRaidAt(villager.blockPosition()))) {
            return StepResult.transition(BindStage.END);
        }

        // Other librarians may have bound the initial targets during the channel; use the remaining sensed candidates
        this.refreshTargets(villager, level);
        int duration = this.config.bindDuration().getTicksAsInt();
        int boundCount = 0;
        for (LivingEntity target : this.targets) {
            if (!VexBinding.isBindable(target, level)) {
                continue;
            }
            if (target.addEffect(new MobEffectInstance(MobEffectRegistry.BOUND, duration, 0), villager)) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 3), villager);
                boundCount++;
            }
        }

        ClockTicks cadence = this.config.cadence();
        if (boundCount == 0) {
            ClockTicks retry = ClockTicks.of(Math.min(EMPTY_CAST_RETRY.getTicks(), cadence.getTicks()));
            villager.getSettlementsBrain().setMemory(MemoryTypeRegistry.BIND_VEXES_COOLDOWN, true, retry);
            return StepResult.transition(BindStage.END);
        }

        villager.getSettlementsBrain().setMemory(MemoryTypeRegistry.BIND_VEXES_COOLDOWN, true, cadence);
        Location.fromEntity(villager, false)
                .playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.0F, SoundSource.NEUTRAL);

        // Commitment is independent of the remaining channel, so interruption cannot revoke or duplicate the reward
        this.rewardExperience(villager);
        context.declarePrimaryDeed(BehaviorOutcome.forDeed(WorldEventType.VEXES_BOUND, "vexes"))
                .recordYield(boundCount);
        return StepResult.noOp();
    }

    private static double scatter() {
        return (RandomUtil.RANDOM.nextDouble() * 2.0D - 1.0D) * GLYPH_SCATTER;
    }

}
