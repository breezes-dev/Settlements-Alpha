package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UmbrellaAnimationTargets {

    // Keep bones before their targets so static initialization supplies initialized bone references
    private static final AnimationBone CANOPY_BONE = AnimationBone.part("canopy");
    private static final AnimationBone NORTH_BONE = AnimationBone.part("north");
    private static final AnimationBone SOUTH_BONE = AnimationBone.part("south");
    private static final AnimationBone EAST_BONE = AnimationBone.part("east");
    private static final AnimationBone WEST_BONE = AnimationBone.part("west");

    public static final AnimationTarget<Vec3> CANOPY_TRANSLATION =
            AnimationTarget.translation("model_part:umbrella_canopy.translation", CANOPY_BONE);

    public static final AnimationTarget<Vector3f> NORTH_ROTATION =
            AnimationTarget.rotation("model_part:umbrella_north.rotation", NORTH_BONE);
    public static final AnimationTarget<Vec3> NORTH_TRANSLATION =
            AnimationTarget.translation("model_part:umbrella_north.translation", NORTH_BONE);
    public static final AnimationTarget<Vector3f> NORTH_SCALE =
            AnimationTarget.scale("model_part:umbrella_north.scale", NORTH_BONE);

    public static final AnimationTarget<Vector3f> SOUTH_ROTATION =
            AnimationTarget.rotation("model_part:umbrella_south.rotation", SOUTH_BONE);
    public static final AnimationTarget<Vec3> SOUTH_TRANSLATION =
            AnimationTarget.translation("model_part:umbrella_south.translation", SOUTH_BONE);
    public static final AnimationTarget<Vector3f> SOUTH_SCALE =
            AnimationTarget.scale("model_part:umbrella_south.scale", SOUTH_BONE);

    public static final AnimationTarget<Vector3f> EAST_ROTATION =
            AnimationTarget.rotation("model_part:umbrella_east.rotation", EAST_BONE);
    public static final AnimationTarget<Vec3> EAST_TRANSLATION =
            AnimationTarget.translation("model_part:umbrella_east.translation", EAST_BONE);
    public static final AnimationTarget<Vector3f> EAST_SCALE =
            AnimationTarget.scale("model_part:umbrella_east.scale", EAST_BONE);

    public static final AnimationTarget<Vector3f> WEST_ROTATION =
            AnimationTarget.rotation("model_part:umbrella_west.rotation", WEST_BONE);
    public static final AnimationTarget<Vec3> WEST_TRANSLATION =
            AnimationTarget.translation("model_part:umbrella_west.translation", WEST_BONE);
    public static final AnimationTarget<Vector3f> WEST_SCALE =
            AnimationTarget.scale("model_part:umbrella_west.scale", WEST_BONE);

    // Keep after every channel so static initialization supplies initialized channel references

    /**
     * The umbrella prop's rig.
     */
    public static final AnimationRig RIG = AnimationRig.of("umbrella", List.of(
            CANOPY_TRANSLATION,
            NORTH_ROTATION,
            NORTH_TRANSLATION,
            NORTH_SCALE,
            SOUTH_ROTATION,
            SOUTH_TRANSLATION,
            SOUTH_SCALE,
            EAST_ROTATION,
            EAST_TRANSLATION,
            EAST_SCALE,
            WEST_ROTATION,
            WEST_TRANSLATION,
            WEST_SCALE));

}
