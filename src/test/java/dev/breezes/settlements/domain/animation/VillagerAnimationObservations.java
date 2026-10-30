package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * Builds animation observations with explicitly selectable test inputs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VillagerAnimationObservations {

    @Builder(builderMethodName = "observation", builderClassName = "ObservationBuilder")
    private static VillagerAnimationObservation create(long ageTicks,
                                                       float partialTicks,
                                                       float netHeadYawDegrees,
                                                       float headPitchDegrees,
                                                       float bodyHeadingDegrees,
                                                       boolean upsideDown,
                                                       float limbSwing,
                                                       float limbSwingAmount,
                                                       NavigationType navigationType,
                                                       AnimationArchetype motionArchetype,
                                                       byte motionGeneration,
                                                       AnimationSelectionContext selectionContext,
                                                       boolean sleeping,
                                                       boolean unhappy,
                                                       boolean umbrellaRequested,
                                                       boolean lanternRequested,
                                                       boolean lanternCheckDecided,
                                                       boolean handItemShown) {
        VillagerAnimationObservation observation = new VillagerAnimationObservation();
        copy(ageTicks, partialTicks, netHeadYawDegrees, headPitchDegrees, bodyHeadingDegrees, upsideDown, limbSwing,
                limbSwingAmount, navigationType, motionArchetype, motionGeneration, selectionContext, sleeping, unhappy,
                umbrellaRequested, lanternRequested, lanternCheckDecided, handItemShown, observation);
        return observation;
    }

    /**
     * Writes every value of the source into the target.
     */
    static void copyInto(VillagerAnimationObservation source, VillagerAnimationObservation target) {
        copy(source.getAgeTicks(), source.getPartialTicks(), source.getNetHeadYawDegrees(),
                source.getHeadPitchDegrees(), source.getBodyHeadingDegrees(), source.isUpsideDown(),
                source.getLimbSwing(), source.getLimbSwingAmount(), source.getNavigationType(),
                source.getMotionArchetype(), source.getMotionGeneration(), source.getSelectionContext(),
                source.isSleeping(), source.isUnhappy(), source.isUmbrellaRequested(), source.isLanternRequested(),
                source.isLanternCheckDecided(), source.isHandItemShown(), target);
    }

    private static void copy(long ageTicks,
                             float partialTicks,
                             float netHeadYawDegrees,
                             float headPitchDegrees,
                             float bodyHeadingDegrees,
                             boolean upsideDown,
                             float limbSwing,
                             float limbSwingAmount,
                             NavigationType navigationType,
                             AnimationArchetype motionArchetype,
                             byte motionGeneration,
                             AnimationSelectionContext selectionContext,
                             boolean sleeping,
                             boolean unhappy,
                             boolean umbrellaRequested,
                             boolean lanternRequested,
                             boolean lanternCheckDecided,
                             boolean handItemShown,
                             VillagerAnimationObservation target) {
        target.setSampleKey(ageTicks, partialTicks);
        target.setLook(netHeadYawDegrees, headPitchDegrees);
        target.setBodyHeadingDegrees(bodyHeadingDegrees);
        target.setUpsideDown(upsideDown);
        target.setLimbSwing(limbSwing, limbSwingAmount);
        target.setNavigationType(navigationType);
        target.setMotion(motionArchetype, motionGeneration);
        target.setSelectionContext(selectionContext);
        target.setSleeping(sleeping);
        target.setUnhappy(unhappy);
        target.setUmbrellaRequested(umbrellaRequested);
        target.setLanternRequested(lanternRequested);
        target.setLanternCheckDecided(lanternCheckDecided);
        target.setHandItemShown(handItemShown);
    }

}
