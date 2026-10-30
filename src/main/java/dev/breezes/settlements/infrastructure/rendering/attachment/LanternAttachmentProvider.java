package dev.breezes.settlements.infrastructure.rendering.attachment;

import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.domain.attachment.AttachmentContent;
import dev.breezes.settlements.domain.attachment.AttachmentProvider;
import dev.breezes.settlements.domain.attachment.LanternSlot;
import dev.breezes.settlements.domain.attachment.RenderableAttachment;
import dev.breezes.settlements.domain.entities.OwnedLanternType;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws an owned lantern while the animation snapshot shows it.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class LanternAttachmentProvider implements AttachmentProvider {

    private static final Map<OwnedLanternType, List<RenderableAttachment>> ATTACHMENTS_BY_TYPE = attachmentsByType();

    @Override
    public List<RenderableAttachment> attachmentsFor(@Nonnull BaseVillager villager,
                                                    @Nonnull VillagerAnimationSnapshot snapshot) {
        return snapshot.isLanternVisible() ? ATTACHMENTS_BY_TYPE.get(villager.getOwnedLanternType()) : List.of();
    }

    private static Map<OwnedLanternType, List<RenderableAttachment>> attachmentsByType() {
        Map<OwnedLanternType, List<RenderableAttachment>> attachments = new EnumMap<>(OwnedLanternType.class);
        for (OwnedLanternType type : OwnedLanternType.values()) {
            AttachmentContent.BlockStateContent content = LanternAttachmentContents.forType(type);
            attachments.put(type, content == null ? List.of() : List.of(RenderableAttachment.builder()
                    .slot(LanternSlot.LANTERN)
                    .content(content)
                    .build()));
        }
        return Map.copyOf(attachments);
    }

}
