package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nonnull;

/**
 * One animatable channel of a bone: its rotation, translation, or scale, keyed with values of type V.
 * Separately constructed targets with the same id represent the same channel and compare equal.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnimationTarget<V> {

    /**
     * The globally unique identifier for this channel.
     */
    private final String id;

    private final AnimationBone bone;

    private final AnimationChannelKind kind;

    /**
     * Creates the bone's rotation channel.
     */
    public static AnimationTarget<Vector3f> rotation(@Nonnull String id, @Nonnull AnimationBone bone) {
        return new AnimationTarget<>(id, bone, AnimationChannelKind.ROTATION);
    }

    /**
     * Creates the bone's translation channel.
     */
    public static AnimationTarget<Vec3> translation(@Nonnull String id, @Nonnull AnimationBone bone) {
        return new AnimationTarget<>(id, bone, AnimationChannelKind.TRANSLATION);
    }

    /**
     * Creates the bone's scale channel.
     */
    public static AnimationTarget<Vector3f> scale(@Nonnull String id, @Nonnull AnimationBone bone) {
        return new AnimationTarget<>(id, bone, AnimationChannelKind.SCALE);
    }

    /**
     * Reads one component of a keyed value, X, Y, or Z by axis 0, 1, or 2.
     *
     * @throws IllegalStateException    if the value is neither a Vector3f nor a Vec3
     * @throws IllegalArgumentException if the value is a vector and the axis is outside [0, 2]
     */
    float component(@Nonnull V value, int axis) {
        if (value instanceof Vector3f vector) {
            return switch (axis) {
                case 0 -> vector.x();
                case 1 -> vector.y();
                case 2 -> vector.z();
                default -> throw new IllegalArgumentException("Axis must be 0, 1, or 2, not " + axis);
            };
        }
        if (value instanceof Vec3 vector) {
            return switch (axis) {
                case 0 -> (float) vector.x;
                case 1 -> (float) vector.y;
                case 2 -> (float) vector.z;
                default -> throw new IllegalArgumentException("Axis must be 0, 1, or 2, not " + axis);
            };
        }
        throw new IllegalStateException("Target %s holds no vector components".formatted(this.id));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AnimationTarget<?> other)) {
            return false;
        }
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

}
