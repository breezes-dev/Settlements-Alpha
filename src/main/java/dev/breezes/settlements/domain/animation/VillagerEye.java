package dev.breezes.settlements.domain.animation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The villager's two eyes and their geometry, in eyeball-local model units, where x runs toward the villager's left and
 * y points down. Each pupil rests just outward of its eye's inner edge, the edge nearer the middle of the face, and the
 * two eyes mirror each other.
 */
@Getter
@AllArgsConstructor
public enum VillagerEye {

    LEFT(1.0F),
    RIGHT(-1.0F);

    /**
     * Half the eye white's width. The white is centered on its eyeball's origin.
     */
    public static final float WHITE_HALF_WIDTH = 1.0F;

    /**
     * Half the eye white's height.
     */
    public static final float WHITE_HALF_HEIGHT = 0.5F;

    /**
     * The width and height of a pupil at rest, which is square.
     */
    public static final float PUPIL_REST_SIZE = 0.9F;

    /**
     * How far a pupil at rest sits outward from its eye's inner edge.
     */
    public static final float PUPIL_OUTWARD_OFFSET = 0.05F;

    /**
     * The vertical position of a pupil's center at rest, level with the white's center.
     */
    public static final float PUPIL_REST_Y = 0.0F;

    /**
     * The sign of x that points from this eye's inner edge toward its outer edge.
     */
    private final float outwardDirection;

    /**
     * The horizontal position of this eye's pupil center at rest.
     */
    public float pupilRestX() {
        return this.outwardDirection * (PUPIL_REST_SIZE / 2.0F + PUPIL_OUTWARD_OFFSET - WHITE_HALF_WIDTH);
    }

    /**
     * The horizontal position of this eye white's inner edge, the edge nearer the middle of the face.
     */
    public float innerEdgeX() {
        return -this.outwardDirection * WHITE_HALF_WIDTH;
    }

}
