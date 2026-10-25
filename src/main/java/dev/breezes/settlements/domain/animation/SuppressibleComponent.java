package dev.breezes.settlements.domain.animation;

/**
 * An ambient or procedural behavior that contributions may suppress.
 */
public enum SuppressibleComponent {

    /**
     * The ambient breathing cycle.
     */
    BREATH,

    /**
     * The ambient blink.
     */
    BLINK,

    /**
     * Ambient and look-reactive brow motion.
     */
    BROW,

    /**
     * Procedural pupil motion toward the look.
     */
    GAZE,

    /**
     * The heading-lag nose roll.
     */
    NOSE_LAG;

    /**
     * The number of components, for arrays indexed by ordinal.
     */
    // Cache the count because values() copies its array on every call
    static final int COUNT = values().length;

}
