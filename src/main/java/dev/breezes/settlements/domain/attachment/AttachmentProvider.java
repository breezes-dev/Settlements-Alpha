package dev.breezes.settlements.domain.attachment;

import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;

import javax.annotation.Nonnull;
import java.util.List;

public interface AttachmentProvider {

    default int renderOrder() {
        return 0;
    }

    /**
     * The attachments to draw on the villager in this render; an attachment left out is not drawn.
     *
     * @param snapshot the villager's evaluated animation for this render
     */
    List<RenderableAttachment> attachmentsFor(@Nonnull BaseVillager villager,
                                              @Nonnull VillagerAnimationSnapshot snapshot);

}
