package dev.breezes.settlements.bootstrap.event;

import dev.breezes.settlements.di.ClientComponent;
import dev.breezes.settlements.di.SettlementsDagger;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nonnull;

/**
 * Connects next-wave markers to client ticks and level rendering.
 */
@ClientSide
@EventBusSubscriber(Dist.CLIENT)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RaidWaveBeamClientEvents {

    /**
     * Advances the current client's marker lifetimes.
     */
    @SubscribeEvent
    public static void onClientTick(@Nonnull ClientTickEvent.Post event) {
        ClientComponent client = SettlementsDagger.clientOrNull();
        if (client != null) {
            client.raidWaveBeamStore().onClientTick();
        }
    }

    /**
     * Forwards level render stages to the marker renderer.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(@Nonnull RenderLevelStageEvent event) {
        ClientComponent client = SettlementsDagger.clientOrNull();
        if (client != null) {
            client.raidWaveBeamRenderer().render(event);
        }
    }

}
