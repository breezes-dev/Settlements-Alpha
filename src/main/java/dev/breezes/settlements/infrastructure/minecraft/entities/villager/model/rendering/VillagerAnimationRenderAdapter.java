package dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.rendering;

import dev.breezes.settlements.di.ClientSessionComponent;
import dev.breezes.settlements.di.SettlementsDagger;
import dev.breezes.settlements.domain.animation.AnimationSelectionContext;
import dev.breezes.settlements.domain.animation.StatelessVillagerAnimator;
import dev.breezes.settlements.domain.animation.VillagerAnimationObservation;
import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.domain.animation.VillagerAnimator;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.infrastructure.minecraft.attachments.EquipmentLookup;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.SettlementsVillagerModel;
import dev.breezes.settlements.infrastructure.rendering.animation.ObservationalVillagerRenderFlag;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Adapts vanilla's resolved render inputs to one animation snapshot shared by the model and its layers.
 * <p>
 * Live renders in a client session advance the villager's animation. Observational renders leave its animation state
 * unchanged. Without a client session, snapshots depend only on the current observation. Steady-state work allocates
 * nothing except when reading a custom name to detect an upside-down villager.
 */
@ClientSide
public final class VillagerAnimationRenderAdapter implements SettlementsVillagerModel.SnapshotSource {

    private final ObservationalVillagerRenderFlag observationalRenderFlag;

    // Separate buffers prevent observational renders from overwriting the villager's live observation and snapshot
    private final VillagerAnimationObservation uncommittedObservation = new VillagerAnimationObservation();
    private final VillagerAnimationSnapshot uncommittedSnapshot = new VillagerAnimationSnapshot();
    private final StatelessVillagerAnimator statelessAnimator = new StatelessVillagerAnimator();

    VillagerAnimationRenderAdapter(@Nonnull ObservationalVillagerRenderFlag observationalRenderFlag) {
        this.observationalRenderFlag = observationalRenderFlag;
    }

    /**
     * Starts a render observation, raising the observational render flag if the sample is not live.
     * If this call throws, any flag it raised has already been lowered.
     *
     * @return whether this call raised the flag; pass the result to {@link #finishRender} once the render ends
     */
    boolean startRender(@Nonnull BaseVillager villager, float partialTicks) {
        boolean raisesFlag = !VillagerAnimator.isLiveSample(partialTicks, villager.level().tickRateManager().isEntityFrozen(villager));
        if (raisesFlag) {
            this.observationalRenderFlag.raise();
        }
        try {
            this.observeBeforeRender(villager, partialTicks);
        } catch (RuntimeException exception) {
            // A failed start cannot return the cleanup token, and a stuck flag would prevent subsequent live advances
            this.finishRender(raisesFlag);
            throw exception;
        }
        return raisesFlag;
    }

    /**
     * Ends a render, lowering the observational render flag when its start raised it.
     */
    void finishRender(boolean startRaisedFlag) {
        if (startRaisedFlag) {
            this.observationalRenderFlag.lower();
        }
    }

    private void observeBeforeRender(@Nonnull BaseVillager villager, float partialTicks) {
        VillagerAnimationObservation observation = this.observationFor(villager);
        observation.setSampleKey(villager.tickCount, partialTicks);
        observation.setNavigationType(villager.getLocomotionNavigationType());
        observation.setMotion(villager.getMotion(), villager.getMotionGeneration());
        ItemStack mainHandItem = EquipmentLookup.findEquippedOrEmpty(villager, EquipmentSlot.MAIN_HAND);
        ItemStack offHandItem = EquipmentLookup.findEquippedOrEmpty(villager, EquipmentSlot.OFF_HAND);
        observation.setSelectionContext(AnimationSelectionContext.of(ItemCategory.of(mainHandItem)));
        observation.setHandItemShown(!mainHandItem.isEmpty() || !offHandItem.isEmpty());
        observation.setUmbrellaRequested(villager.isUmbrellaRequested());
        observation.setLanternRequested(villager.isLanternRequested());
        observation.setLanternCheckDecided(villager.isLanternCheckDecided());
        observation.setSleeping(villager.isSleeping());
        observation.setUnhappy(villager.getUnhappyCounter() > 0);
        observation.setUpsideDown(LivingEntityRenderer.isEntityUpsideDown(villager));
    }

    /**
     * Records this render's resolved body heading in degrees, including any riding adjustment.
     */
    void observeBodyHeading(@Nonnull BaseVillager villager, float yBodyRot) {
        this.observationFor(villager).setBodyHeadingDegrees(yBodyRot);
    }

    /**
     * Completes the current render's observation with resolved look and limb-swing inputs and returns its snapshot.
     * The returned buffer is borrowed and may be overwritten by a later render.
     */
    @Override
    public VillagerAnimationSnapshot snapshotForSetupAnim(@Nonnull BaseVillager villager,
                                                          float limbSwing,
                                                          float limbSwingAmount,
                                                          float netHeadYaw,
                                                          float headPitch) {
        VillagerAnimator committingAnimator = this.committingAnimatorOrNull(villager);
        if (committingAnimator != null) {
            observeSetupAnim(committingAnimator.getObservation(), limbSwing, limbSwingAmount, netHeadYaw, headPitch);
            return committingAnimator.advance();
        }

        observeSetupAnim(this.uncommittedObservation, limbSwing, limbSwingAmount, netHeadYaw, headPitch);
        ClientSessionComponent session = SettlementsDagger.clientSessionOrNull();
        if (session == null) {
            this.statelessAnimator.evaluate(this.uncommittedObservation, this.uncommittedSnapshot);
        } else {
            villager.getAnimator(session.villagerAnimatorFactory())
                    .evaluateObservationally(this.uncommittedObservation, this.uncommittedSnapshot);
        }
        return this.uncommittedSnapshot;
    }

    /**
     * Returns the borrowed snapshot produced by setupAnim for the current render.
     */
    VillagerAnimationSnapshot snapshotFor(@Nonnull BaseVillager villager) {
        VillagerAnimator committingAnimator = this.committingAnimatorOrNull(villager);
        return committingAnimator == null ? this.uncommittedSnapshot : committingAnimator.getSnapshot();
    }

    private VillagerAnimationObservation observationFor(@Nonnull BaseVillager villager) {
        VillagerAnimator committingAnimator = this.committingAnimatorOrNull(villager);
        return committingAnimator == null ? this.uncommittedObservation : committingAnimator.getObservation();
    }

    /**
     * The villager's own animator when this render commits to it, or null for a render that commits nothing.
     */
    @Nullable
    private VillagerAnimator committingAnimatorOrNull(@Nonnull BaseVillager villager) {
        if (this.observationalRenderFlag.isRaised()) {
            return null;
        }
        ClientSessionComponent session = SettlementsDagger.clientSessionOrNull();
        return session == null ? null : villager.getAnimator(session.villagerAnimatorFactory());
    }

    private static void observeSetupAnim(@Nonnull VillagerAnimationObservation observation,
                                         float limbSwing,
                                         float limbSwingAmount,
                                         float netHeadYaw,
                                         float headPitch) {
        // Re-deriving look from entity fields would lose the riding adjustment and upside-down signs in these inputs
        observation.setLook(netHeadYaw, headPitch);
        observation.setLimbSwing(limbSwing, limbSwingAmount);
    }

}
