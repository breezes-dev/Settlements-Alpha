package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Animation targets for ballista model parts, with values relative to their rest transforms.
 * <p>
 * The aim bone has no clip target.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BallistaAnimationTargets {

    public static final AnimationTarget<Vector3f> GIMBAL_ROTATION = rotation("gimbal");
    public static final AnimationTarget<Vec3> GIMBAL_TRANSLATION = translation("gimbal");

    public static final AnimationTarget<Vector3f> LIMB_LEFT_OUTER_ROTATION = rotation("limb_left_outer");
    public static final AnimationTarget<Vector3f> LIMB_LEFT_TIP_ROTATION = rotation("limb_left_tip");
    public static final AnimationTarget<Vector3f> STRING_LEFT_ROTATION = rotation("string_left");

    public static final AnimationTarget<Vector3f> LIMB_RIGHT_OUTER_ROTATION = rotation("limb_right_outer");
    public static final AnimationTarget<Vector3f> LIMB_RIGHT_TIP_ROTATION = rotation("limb_right_tip");
    public static final AnimationTarget<Vector3f> STRING_RIGHT_ROTATION = rotation("string_right");

    public static final AnimationTarget<Vec3> PUSHER_TRANSLATION = translation("pusher");

    public static final AnimationTarget<Vector3f> CRANKSHAFT_ROTATION = rotation("crankshaft");

    /**
     * Scale factors for the connector rope, relative to its authored length.
     */
    public static final AnimationTarget<Vector3f> CONNECTOR_SCALE =
            AnimationTarget.scale("model_part:ballista_connector.scale", AnimationBone.part("connector"));

    // Keep after every channel so static initialization supplies initialized channel references

    /**
     * The ballista's rig.
     */
    public static final AnimationRig RIG = AnimationRig.of("ballista", List.of(
            GIMBAL_ROTATION,
            GIMBAL_TRANSLATION,
            LIMB_LEFT_OUTER_ROTATION,
            LIMB_LEFT_TIP_ROTATION,
            STRING_LEFT_ROTATION,
            LIMB_RIGHT_OUTER_ROTATION,
            LIMB_RIGHT_TIP_ROTATION,
            STRING_RIGHT_ROTATION,
            PUSHER_TRANSLATION,
            CRANKSHAFT_ROTATION,
            CONNECTOR_SCALE));

    private static AnimationTarget<Vector3f> rotation(String bone) {
        return AnimationTarget.rotation("model_part:ballista_" + bone + ".rotation", AnimationBone.part(bone));
    }

    private static AnimationTarget<Vec3> translation(String bone) {
        return AnimationTarget.translation("model_part:ballista_" + bone + ".translation", AnimationBone.part(bone));
    }

}
