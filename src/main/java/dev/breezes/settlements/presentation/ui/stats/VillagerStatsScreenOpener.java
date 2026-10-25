package dev.breezes.settlements.presentation.ui.stats;

import dev.breezes.settlements.application.ui.sync.UiScreenOpener;
import dev.breezes.settlements.infrastructure.rendering.animation.ObservationalVillagerRenderFlag;
import dev.breezes.settlements.presentation.ui.sync.UiClientState;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.client.Minecraft;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class VillagerStatsScreenOpener implements UiScreenOpener {

    private final UiClientState uiClientState;
    private final ObservationalVillagerRenderFlag observationalRenderFlag;

    @Override
    public void openScreen(long sessionId, int villagerEntityId) {
        Minecraft.getInstance().setScreen(new VillagerStatsScreen(sessionId, this.uiClientState,
                this.observationalRenderFlag, villagerEntityId));
    }

}
