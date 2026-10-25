package dev.breezes.settlements.infrastructure.rendering.attachment;

import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.domain.attachment.AttachmentContent;
import dev.breezes.settlements.domain.attachment.AttachmentProvider;
import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import dev.breezes.settlements.domain.attachment.RenderableAttachment;
import dev.breezes.settlements.domain.attachment.UmbrellaPattern;
import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.UmbrellaModel;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class UmbrellaAttachmentProvider implements AttachmentProvider {

    /**
     * The umbrella while the snapshot shows it, posed with the snapshot's umbrella pose; otherwise nothing.
     */
    @Override
    public List<RenderableAttachment> attachmentsFor(@Nonnull BaseVillager villager,
                                                     @Nonnull VillagerAnimationSnapshot snapshot) {
        if (!snapshot.isUmbrellaVisible()) {
            return List.of();
        }

        UUID uuid = villager.getUUID();
        AttachmentSlot slot = isLeftSide(uuid) ? UmbrellaSlot.UMBRELLA_LEFT : UmbrellaSlot.UMBRELLA_RIGHT;
        ResourceLocation texture = UmbrellaModel.textureFor(UmbrellaPattern.forVillager(uuid));
        return List.of(RenderableAttachment.builder()
                .slot(slot)
                .content(new AttachmentContent.ModelContent(UmbrellaModel.ID, texture, snapshot.getUmbrellaPose()))
                .build());
    }

    /**
     * Picks a stable carry side per villager.
     */
    static boolean isLeftSide(@Nonnull UUID uuid) {
        // Villager UUIDs are randomly generated, so a single bit is already an even coin flip
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return mixed < 0;
    }

}
