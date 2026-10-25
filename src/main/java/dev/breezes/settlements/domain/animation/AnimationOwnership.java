package dev.breezes.settlements.domain.animation;

import org.apache.commons.lang3.Validate;

/**
 * A clip's declaration that it overrides every channel of a mask, moving each toward its keyed value, or toward rest
 * where it keys none, by the coverage.
 *
 * @param coverage how far an owned channel moves at full weight, in (0, 1]
 */
public record AnimationOwnership(AnimationMask mask, float coverage) {

    public AnimationOwnership {
        Validate.notNull(mask, "Owned mask must not be null");
        Validate.isTrue(coverage > 0.0F && coverage <= 1.0F, "Ownership coverage must be in (0, 1], not %s", coverage);
    }

}
