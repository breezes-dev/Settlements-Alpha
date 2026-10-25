package dev.breezes.settlements.domain.animation;

import lombok.Builder;

import javax.annotation.Nonnull;
import java.util.List;

public final class InMemoryAmbientAnimationLibrary implements AmbientAnimationLibrary {

    private final KeyframeAnimation breathingCycle;
    private final KeyframeAnimation blink;
    private final List<KeyframeAnimation> browMotions;

    @Builder
    private InMemoryAmbientAnimationLibrary(@Nonnull KeyframeAnimation breathingCycle,
                                            @Nonnull KeyframeAnimation blink,
                                            @Nonnull List<KeyframeAnimation> browMotions) {
        this.breathingCycle = breathingCycle;
        this.blink = blink;
        this.browMotions = List.copyOf(browMotions);
    }

    @Override
    public KeyframeAnimation breathingCycle() {
        return this.breathingCycle;
    }

    @Override
    public KeyframeAnimation blink() {
        return this.blink;
    }

    @Override
    public List<KeyframeAnimation> browMotions() {
        return this.browMotions;
    }

}
