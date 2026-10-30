package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import dev.breezes.settlements.domain.attachment.LanternSlot;
import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The villager's rig and its named masks.
 * <p>
 * Masks select only their listed channels; model hierarchy does not extend membership.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VillagerAnimationRig {

    /**
     * The villager's model-part channels and attachment-slot channels.
     */
    public static final AnimationRig RIG = AnimationRig.of("villager", channels());

    public static final AnimationMask FULL_BODY = RIG.getFullBody();

    public static final AnimationMask HEAD = RIG.maskOf(List.of(
            AnimationTargets.HEAD_ROTATION,
            AnimationTargets.HEAD_TRANSLATION));

    public static final AnimationMask TORSO = RIG.maskOf(List.of(
            AnimationTargets.BODY_ROTATION,
            AnimationTargets.BODY_TRANSLATION));

    public static final AnimationMask LEGS = RIG.maskOf(List.of(
            AnimationTargets.LEG_LEFT_ROTATION,
            AnimationTargets.LEG_LEFT_TRANSLATION,
            AnimationTargets.LEG_RIGHT_ROTATION,
            AnimationTargets.LEG_RIGHT_TRANSLATION));

    /**
     * The left arm in both its crossed and its straight geometry.
     */
    public static final AnimationMask LEFT_ARM = RIG.maskOf(List.of(
            AnimationTargets.ARM_CROSSED_LEFT_ROTATION,
            AnimationTargets.ARM_CROSSED_LEFT_TRANSLATION,
            AnimationTargets.ARM_STRAIGHT_LEFT_ROTATION,
            AnimationTargets.ARM_STRAIGHT_LEFT_TRANSLATION));

    /**
     * The right arm in both its crossed and its straight geometry.
     */
    public static final AnimationMask RIGHT_ARM = RIG.maskOf(List.of(
            AnimationTargets.ARM_CROSSED_RIGHT_ROTATION,
            AnimationTargets.ARM_CROSSED_RIGHT_TRANSLATION,
            AnimationTargets.ARM_STRAIGHT_RIGHT_ROTATION,
            AnimationTargets.ARM_STRAIGHT_RIGHT_TRANSLATION));

    /**
     * Both arms, plus the crossed and straight arm groups that carry them.
     */
    public static final AnimationMask ARMS = LEFT_ARM.union(RIGHT_ARM).union(RIG.maskOf(List.of(
            AnimationTargets.ARMS_CROSSED_ROTATION,
            AnimationTargets.ARMS_CROSSED_TRANSLATION,
            AnimationTargets.ARMS_STRAIGHT_ROTATION,
            AnimationTargets.ARMS_STRAIGHT_TRANSLATION)));

    public static final AnimationMask ROOT = RIG.maskOf(List.of(
            AnimationTargets.ROOT_ROTATION,
            AnimationTargets.ROOT_TRANSLATION));

    public static final AnimationMask EYELIDS = RIG.maskOf(List.of(
            AnimationTargets.EYELID_LEFT_TRANSLATION,
            AnimationTargets.EYELID_RIGHT_TRANSLATION));

    /**
     * Channels for both eyeballs and pupils.
     */
    public static final AnimationMask EYES = RIG.maskOf(List.of(
            AnimationTargets.EYEBALL_LEFT_TRANSLATION,
            AnimationTargets.EYEBALL_LEFT_SCALE,
            AnimationTargets.EYEBALL_RIGHT_TRANSLATION,
            AnimationTargets.EYEBALL_RIGHT_SCALE,
            AnimationTargets.PUPIL_LEFT_TRANSLATION,
            AnimationTargets.PUPIL_LEFT_SCALE,
            AnimationTargets.PUPIL_RIGHT_TRANSLATION,
            AnimationTargets.PUPIL_RIGHT_SCALE));

    public static final AnimationMask BROW = RIG.maskOf(List.of(
            AnimationTargets.MONOBROW_ROTATION,
            AnimationTargets.MONOBROW_TRANSLATION));

    public static final AnimationMask NOSE = RIG.maskOf(List.of(
            AnimationTargets.NOSE_ROTATION,
            AnimationTargets.NOSE_TRANSLATION));

    public static final AnimationMask MOUTH = RIG.maskOf(List.of(
            AnimationTargets.MOUTH_ROTATION,
            AnimationTargets.MOUTH_TRANSLATION,
            AnimationTargets.MOUTH_SCALE));

    public static final AnimationMask FACE = EYELIDS.union(EYES).union(BROW).union(NOSE).union(MOUTH);

    /**
     * The head's rotation without its translation.
     */
    public static final AnimationMask HEAD_ROTATION = RIG.maskOf(List.of(AnimationTargets.HEAD_ROTATION));

    /**
     * Both legs' rotations without their translations.
     */
    public static final AnimationMask LEG_ROTATION = RIG.maskOf(List.of(
            AnimationTargets.LEG_LEFT_ROTATION,
            AnimationTargets.LEG_RIGHT_ROTATION));

    private static List<AnimationTarget<?>> channels() {
        List<AnimationTarget<?>> channels = new ArrayList<>(List.of(
                AnimationTargets.ROOT_ROTATION,
                AnimationTargets.ROOT_TRANSLATION,
                AnimationTargets.BODY_ROTATION,
                AnimationTargets.BODY_TRANSLATION,
                AnimationTargets.HEAD_ROTATION,
                AnimationTargets.HEAD_TRANSLATION,
                AnimationTargets.ARMS_CROSSED_ROTATION,
                AnimationTargets.ARMS_CROSSED_TRANSLATION,
                AnimationTargets.ARMS_STRAIGHT_ROTATION,
                AnimationTargets.ARMS_STRAIGHT_TRANSLATION,
                AnimationTargets.ARM_CROSSED_LEFT_ROTATION,
                AnimationTargets.ARM_CROSSED_LEFT_TRANSLATION,
                AnimationTargets.ARM_CROSSED_RIGHT_ROTATION,
                AnimationTargets.ARM_CROSSED_RIGHT_TRANSLATION,
                AnimationTargets.ARM_STRAIGHT_LEFT_ROTATION,
                AnimationTargets.ARM_STRAIGHT_LEFT_TRANSLATION,
                AnimationTargets.ARM_STRAIGHT_RIGHT_ROTATION,
                AnimationTargets.ARM_STRAIGHT_RIGHT_TRANSLATION,
                AnimationTargets.LEG_LEFT_ROTATION,
                AnimationTargets.LEG_LEFT_TRANSLATION,
                AnimationTargets.LEG_RIGHT_ROTATION,
                AnimationTargets.LEG_RIGHT_TRANSLATION,
                AnimationTargets.MONOBROW_ROTATION,
                AnimationTargets.MONOBROW_TRANSLATION,
                AnimationTargets.EYELID_LEFT_TRANSLATION,
                AnimationTargets.EYELID_RIGHT_TRANSLATION,
                AnimationTargets.EYEBALL_LEFT_TRANSLATION,
                AnimationTargets.EYEBALL_LEFT_SCALE,
                AnimationTargets.EYEBALL_RIGHT_TRANSLATION,
                AnimationTargets.EYEBALL_RIGHT_SCALE,
                AnimationTargets.PUPIL_LEFT_TRANSLATION,
                AnimationTargets.PUPIL_LEFT_SCALE,
                AnimationTargets.PUPIL_RIGHT_TRANSLATION,
                AnimationTargets.PUPIL_RIGHT_SCALE,
                AnimationTargets.NOSE_ROTATION,
                AnimationTargets.NOSE_TRANSLATION,
                AnimationTargets.MOUTH_ROTATION,
                AnimationTargets.MOUTH_TRANSLATION,
                AnimationTargets.MOUTH_SCALE));
        addSlotChannels(channels, UmbrellaSlot.values());
        addSlotChannels(channels, LanternSlot.values());
        addSlotChannels(channels, EquipmentSlot.values());
        return channels;
    }

    private static void addSlotChannels(List<AnimationTarget<?>> channels, AttachmentSlot[] slots) {
        for (AttachmentSlot slot : slots) {
            channels.add(SlotTargets.translation(slot));
            channels.add(SlotTargets.rotation(slot));
        }
    }

}
