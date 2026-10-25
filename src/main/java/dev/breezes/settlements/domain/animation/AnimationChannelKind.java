package dev.breezes.settlements.domain.animation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A bone transform property animated by a channel.
 * Channel values are relative to the bone's rest transform.
 */
@Getter
@AllArgsConstructor
public enum AnimationChannelKind {

    /**
     * Euler-angle offsets in radians, added componentwise to the bone's rest angles.
     * Pitch, yaw, and roll operate around the X, Y, and Z axes.
     * This convention approximates rotation composition for small angles.
     */
    ROTATION(0.0F),

    /**
     * Offsets in model units added to the bone's rest position along its X, Y, and Z axes.
     */
    TRANSLATION(0.0F),

    /**
     * Factors multiplied by the bone's rest scale along its X, Y, and Z axes.
     */
    SCALE(1.0F);

    /**
     * The neutral value of each component, which preserves the bone's rest transform.
     */
    private final float rest;

}
