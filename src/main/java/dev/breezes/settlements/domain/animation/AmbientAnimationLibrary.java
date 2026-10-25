package dev.breezes.settlements.domain.animation;

import java.util.List;

public interface AmbientAnimationLibrary {

    /**
     * The looping breathing cycle, keyed as offsets onto the standing pose.
     */
    KeyframeAnimation breathingCycle();

    /**
     * The single-shot eyelid blink.
     */
    KeyframeAnimation blink();

    /**
     * The single-shot brow raises and tilts a villager plays one at a time, each keying only brow channels; empty for
     * none.
     */
    List<KeyframeAnimation> browMotions();

}
