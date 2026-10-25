package dev.breezes.settlements.domain.attachment;

import dev.breezes.settlements.domain.animation.AnimationPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public sealed interface AttachmentContent permits AttachmentContent.ItemContent,
        AttachmentContent.ModelContent,
        AttachmentContent.BillboardContent {

    record ItemContent(ItemStack stack) implements AttachmentContent {
    }

    /**
     * @param pose the model's pose for this draw, on the model's own rig
     */
    record ModelContent(ResourceLocation modelId,
                        ResourceLocation texture,
                        AnimationPose pose) implements AttachmentContent {
    }

    record BillboardContent(ResourceLocation texture) implements AttachmentContent {
    }

}
