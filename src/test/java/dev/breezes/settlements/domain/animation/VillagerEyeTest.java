package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerEyeTest {

    private static final float TOLERANCE = 1.0E-6F;

    @Test
    void pupilsAtRest_sitNearerTheirEyesInnerEdges_andMirrorEachOther() {
        // Arrange: x runs toward the villager's left, so the left eye's inner edge is its negative one
        float halfSize = VillagerEye.PUPIL_REST_SIZE / 2.0F;

        // Act
        float leftCenter = VillagerEye.LEFT.pupilRestX();
        float rightCenter = VillagerEye.RIGHT.pupilRestX();

        // Assert: a flipped outward direction would rest each pupil against its eye's outer edge
        float innerGap = (leftCenter - halfSize) + VillagerEye.WHITE_HALF_WIDTH;
        float outerGap = VillagerEye.WHITE_HALF_WIDTH - (leftCenter + halfSize);
        assertTrue(innerGap >= 0.0F, "the left pupil rests past its eye's inner edge: " + innerGap);
        assertTrue(innerGap < outerGap, "the left pupil rests nearer its eye's outer edge");
        assertEquals(-leftCenter, rightCenter, TOLERANCE);
    }

    @Test
    void innerEdges_areTheWhitesEdgesNearerEachRestingPupil_andMirrorEachOther() {
        for (VillagerEye eye : VillagerEye.values()) {
            // Act
            float innerEdge = eye.innerEdgeX();

            // Assert: an inner edge on the outer side would pin and squash a pupil against the wrong edge
            assertEquals(VillagerEye.WHITE_HALF_WIDTH, Math.abs(innerEdge), TOLERANCE, eye.name());
            assertTrue(Math.abs(eye.pupilRestX() - innerEdge) < Math.abs(eye.pupilRestX() + innerEdge),
                    eye + " rests nearer its outer edge");
        }
        assertEquals(-VillagerEye.LEFT.innerEdgeX(), VillagerEye.RIGHT.innerEdgeX(), 0.0F);
    }

}
