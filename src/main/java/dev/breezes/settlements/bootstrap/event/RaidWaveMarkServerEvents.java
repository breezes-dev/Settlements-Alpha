package dev.breezes.settlements.bootstrap.event;

import dev.breezes.settlements.application.ai.raid.RaidWaveMarkRegistry;
import dev.breezes.settlements.di.ServerScope;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Publishes next-wave marker changes after the server has advanced its raids and entities.
 */
@ServerScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RaidWaveMarkServerEvents {

    private final RaidWaveMarkRegistry registry;

    /**
     * Advances the marker registry after each server tick.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        this.registry.tick();
    }

}
