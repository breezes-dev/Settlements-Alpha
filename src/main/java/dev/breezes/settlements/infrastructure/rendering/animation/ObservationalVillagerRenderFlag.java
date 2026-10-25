package dev.breezes.settlements.infrastructure.rendering.animation;

import dev.breezes.settlements.di.ClientScope;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import jakarta.inject.Inject;
import org.apache.commons.lang3.Validate;

/**
 * Marks villager renders that may evaluate animation but must not tick or commit animation state. Raise this flag for
 * GUI previews and other extra render passes to prevent them from advancing animation again within the same game tick.
 * <p>
 * Raises may stack: the flag stays raised until every raise is lowered. Render thread only.
 */
@ClientSide
@ClientScope
public final class ObservationalVillagerRenderFlag {

    private int raisedDepth;

    @Inject
    ObservationalVillagerRenderFlag() {
    }

    public void raise() {
        this.raisedDepth++;
    }

    /**
     * Undoes one raise.
     *
     * @throws IllegalStateException if the flag is not raised
     */
    public void lower() {
        Validate.validState(this.raisedDepth > 0, "The observational villager render flag is not raised");
        this.raisedDepth--;
    }

    public boolean isRaised() {
        return this.raisedDepth > 0;
    }

}
