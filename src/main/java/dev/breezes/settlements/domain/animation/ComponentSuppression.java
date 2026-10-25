package dev.breezes.settlements.domain.animation;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Mutable remaining weights for suppressible components during an animation evaluation.
 */
public final class ComponentSuppression {

    private final float[] remaining = new float[SuppressibleComponent.COUNT];

    /**
     * Creates a suppression state with every component unsuppressed.
     */
    public ComponentSuppression() {
        this.reset();
    }

    /**
     * Restores every component to unsuppressed.
     */
    public void reset() {
        Arrays.fill(this.remaining, 1.0F);
    }

    /**
     * The fraction of the component left after suppression, in [0, 1].
     */
    public float remaining(@Nonnull SuppressibleComponent component) {
        return this.remaining[component.ordinal()];
    }

    /**
     * The component's unsuppressed weight multiplied by its remaining fraction.
     */
    public float effectiveWeight(@Nonnull SuppressibleComponent component, float componentWeight) {
        return componentWeight * this.remaining[component.ordinal()];
    }

    /**
     * Multiplies the component's remaining fraction by one minus the clamped amount.
     *
     * @param amount the suppression amount, clamped to [0, 1], where NaN suppresses nothing
     */
    void suppress(int componentOrdinal, float amount) {
        // Negated so a NaN amount returns rather than poisoning the component's remaining weight
        if (!(amount > 0.0F)) {
            return;
        }
        // Cap at complete suppression so rounding above one cannot make the remaining weight negative
        this.remaining[componentOrdinal] *= 1.0F - Math.min(amount, 1.0F);
    }

}
