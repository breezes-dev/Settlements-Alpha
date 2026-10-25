package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnimationTargets {

    // Keep bones before their targets so static initialization supplies initialized bone references
    private static final AnimationBone ARMS_CROSSED_BONE = AnimationBone.part("arms_crossed");
    private static final AnimationBone ARMS_STRAIGHT_BONE = AnimationBone.part("arms_straight");
    private static final AnimationBone ARM_CROSSED_LEFT_BONE = AnimationBone.part("arm_crossed_left");
    private static final AnimationBone ARM_CROSSED_RIGHT_BONE = AnimationBone.part("arm_crossed_right");
    private static final AnimationBone ARM_STRAIGHT_LEFT_BONE = AnimationBone.part("arm_straight_left");
    private static final AnimationBone ARM_STRAIGHT_RIGHT_BONE = AnimationBone.part("arm_straight_right");
    private static final AnimationBone HEAD_BONE = AnimationBone.part("head");
    private static final AnimationBone TORSO_BONE = AnimationBone.part("torso");
    private static final AnimationBone NOSE_BONE = AnimationBone.part("nose");
    private static final AnimationBone LEG_LEFT_BONE = AnimationBone.part("leg_left");
    private static final AnimationBone LEG_RIGHT_BONE = AnimationBone.part("leg_right");
    private static final AnimationBone ROOT_BONE = AnimationBone.part("root");
    private static final AnimationBone MONOBROW_BONE = AnimationBone.part("monobrow");
    private static final AnimationBone MOUTH_BONE = AnimationBone.part("mouth");
    private static final AnimationBone EYELID_LEFT_BONE = AnimationBone.part("eyelid_left");
    private static final AnimationBone EYELID_RIGHT_BONE = AnimationBone.part("eyelid_right");
    private static final AnimationBone EYEBALL_LEFT_BONE = AnimationBone.part("eyeball_left");
    private static final AnimationBone EYEBALL_RIGHT_BONE = AnimationBone.part("eyeball_right");
    private static final AnimationBone PUPIL_LEFT_BONE = AnimationBone.part("pupil_left");
    private static final AnimationBone PUPIL_RIGHT_BONE = AnimationBone.part("pupil_right");

    public static final AnimationTarget<Vector3f> ARMS_CROSSED_ROTATION =
            AnimationTarget.rotation("model_part:arms_crossed.rotation", ARMS_CROSSED_BONE);
    public static final AnimationTarget<Vec3> ARMS_CROSSED_TRANSLATION =
            AnimationTarget.translation("model_part:arms_crossed.translation", ARMS_CROSSED_BONE);

    public static final AnimationTarget<Vector3f> ARMS_STRAIGHT_ROTATION =
            AnimationTarget.rotation("model_part:arms_straight.rotation", ARMS_STRAIGHT_BONE);
    public static final AnimationTarget<Vec3> ARMS_STRAIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:arms_straight.translation", ARMS_STRAIGHT_BONE);

    public static final AnimationTarget<Vector3f> ARM_CROSSED_LEFT_ROTATION =
            AnimationTarget.rotation("model_part:arm_crossed_left.rotation", ARM_CROSSED_LEFT_BONE);
    public static final AnimationTarget<Vec3> ARM_CROSSED_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:arm_crossed_left.translation", ARM_CROSSED_LEFT_BONE);

    public static final AnimationTarget<Vector3f> ARM_CROSSED_RIGHT_ROTATION =
            AnimationTarget.rotation("model_part:arm_crossed_right.rotation", ARM_CROSSED_RIGHT_BONE);
    public static final AnimationTarget<Vec3> ARM_CROSSED_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:arm_crossed_right.translation", ARM_CROSSED_RIGHT_BONE);

    public static final AnimationTarget<Vector3f> ARM_STRAIGHT_LEFT_ROTATION =
            AnimationTarget.rotation("model_part:arm_straight_left.rotation", ARM_STRAIGHT_LEFT_BONE);
    public static final AnimationTarget<Vec3> ARM_STRAIGHT_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:arm_straight_left.translation", ARM_STRAIGHT_LEFT_BONE);

    public static final AnimationTarget<Vector3f> ARM_STRAIGHT_RIGHT_ROTATION =
            AnimationTarget.rotation("model_part:arm_straight_right.rotation", ARM_STRAIGHT_RIGHT_BONE);
    public static final AnimationTarget<Vec3> ARM_STRAIGHT_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:arm_straight_right.translation", ARM_STRAIGHT_RIGHT_BONE);

    public static final AnimationTarget<Vector3f> HEAD_ROTATION =
            AnimationTarget.rotation("model_part:head.rotation", HEAD_BONE);
    public static final AnimationTarget<Vec3> HEAD_TRANSLATION =
            AnimationTarget.translation("model_part:head.translation", HEAD_BONE);

    public static final AnimationTarget<Vector3f> BODY_ROTATION =
            AnimationTarget.rotation("model_part:body.rotation", TORSO_BONE);
    public static final AnimationTarget<Vec3> BODY_TRANSLATION =
            AnimationTarget.translation("model_part:body.translation", TORSO_BONE);

    /**
     * Rotation of the nose about its pivot near the top, in radians.
     * Positive z roll swings the hanging tip toward the villager's right.
     */
    public static final AnimationTarget<Vector3f> NOSE_ROTATION =
            AnimationTarget.rotation("model_part:nose.rotation", NOSE_BONE);
    public static final AnimationTarget<Vec3> NOSE_TRANSLATION =
            AnimationTarget.translation("model_part:nose.translation", NOSE_BONE);

    public static final AnimationTarget<Vector3f> LEG_LEFT_ROTATION =
            AnimationTarget.rotation("model_part:leg_left.rotation", LEG_LEFT_BONE);
    public static final AnimationTarget<Vector3f> LEG_RIGHT_ROTATION =
            AnimationTarget.rotation("model_part:leg_right.rotation", LEG_RIGHT_BONE);
    public static final AnimationTarget<Vec3> LEG_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:leg_left.translation", LEG_LEFT_BONE);
    public static final AnimationTarget<Vec3> LEG_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:leg_right.translation", LEG_RIGHT_BONE);

    /**
     * Translation of the whole model in model space.
     */
    public static final AnimationTarget<Vec3> ROOT_TRANSLATION =
            AnimationTarget.translation("model_part:root.translation", ROOT_BONE);

    /**
     * Rotation of the whole model about its root pivot.
     */
    public static final AnimationTarget<Vector3f> ROOT_ROTATION =
            AnimationTarget.rotation("model_part:root.rotation", ROOT_BONE);

    // Face channels move in the head's model axes: x runs toward the villager's left, y points down, and z points back
    // into the head, so a negative y translation raises a face part and a z rotation rolls it in the face's plane
    public static final AnimationTarget<Vec3> MONOBROW_TRANSLATION =
            AnimationTarget.translation("model_part:monobrow.translation", MONOBROW_BONE);
    public static final AnimationTarget<Vector3f> MONOBROW_ROTATION =
            AnimationTarget.rotation("model_part:monobrow.rotation", MONOBROW_BONE);

    public static final AnimationTarget<Vec3> MOUTH_TRANSLATION =
            AnimationTarget.translation("model_part:mouth.translation", MOUTH_BONE);
    public static final AnimationTarget<Vector3f> MOUTH_ROTATION =
            AnimationTarget.rotation("model_part:mouth.rotation", MOUTH_BONE);
    public static final AnimationTarget<Vector3f> MOUTH_SCALE =
            AnimationTarget.scale("model_part:mouth.scale", MOUTH_BONE);

    public static final AnimationTarget<Vec3> EYELID_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:eyelid_left.translation", EYELID_LEFT_BONE);
    public static final AnimationTarget<Vec3> EYELID_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:eyelid_right.translation", EYELID_RIGHT_BONE);

    public static final AnimationTarget<Vec3> EYEBALL_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:eyeball_left.translation", EYEBALL_LEFT_BONE);
    public static final AnimationTarget<Vec3> EYEBALL_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:eyeball_right.translation", EYEBALL_RIGHT_BONE);
    public static final AnimationTarget<Vector3f> EYEBALL_LEFT_SCALE =
            AnimationTarget.scale("model_part:eyeball_left.scale", EYEBALL_LEFT_BONE);
    public static final AnimationTarget<Vector3f> EYEBALL_RIGHT_SCALE =
            AnimationTarget.scale("model_part:eyeball_right.scale", EYEBALL_RIGHT_BONE);

    /**
     * Translation of the left pupil within its eyeball.
     * The pupil rests against the eye's inner edge, so a positive x moves it outward.
     */
    public static final AnimationTarget<Vec3> PUPIL_LEFT_TRANSLATION =
            AnimationTarget.translation("model_part:pupil_left.translation", PUPIL_LEFT_BONE);

    /**
     * Translation of the right pupil within its eyeball.
     * The pupil rests against the eye's inner edge, so a negative x moves it outward.
     */
    public static final AnimationTarget<Vec3> PUPIL_RIGHT_TRANSLATION =
            AnimationTarget.translation("model_part:pupil_right.translation", PUPIL_RIGHT_BONE);
    public static final AnimationTarget<Vector3f> PUPIL_LEFT_SCALE =
            AnimationTarget.scale("model_part:pupil_left.scale", PUPIL_LEFT_BONE);
    public static final AnimationTarget<Vector3f> PUPIL_RIGHT_SCALE =
            AnimationTarget.scale("model_part:pupil_right.scale", PUPIL_RIGHT_BONE);

}
