package dev.breezes.settlements.infrastructure.rendering.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationalVillagerRenderFlagTest {

    @Test
    void flag_staysRaised_untilEveryRaiseIsLowered() {
        // Arrange: a preview drawn inside another preview's render
        ObservationalVillagerRenderFlag flag = new ObservationalVillagerRenderFlag();
        flag.raise();
        flag.raise();

        // Act
        flag.lower();
        boolean raisedAfterTheInnerLower = flag.isRaised();
        flag.lower();

        // Assert
        assertTrue(raisedAfterTheInnerLower);
        assertFalse(flag.isRaised());
    }

    @Test
    void lower_withoutARaise_isRefused() {
        // Arrange
        ObservationalVillagerRenderFlag flag = new ObservationalVillagerRenderFlag();

        // Act, Assert
        assertThrows(IllegalStateException.class, flag::lower);
    }

}
