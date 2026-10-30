package dev.breezes.settlements.domain.attachment;

import dev.breezes.settlements.domain.animation.AnimationPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public sealed interface AttachmentContent permits AttachmentContent.ItemContent,
        AttachmentContent.BlockStateContent,
        AttachmentContent.ModelContent,
        AttachmentContent.BillboardContent {

    record ItemContent(ItemStack stack) implements AttachmentContent {
    }

    /**
     * A block drawn from its top center, with full block light when glowing.
     */
    record BlockStateContent(BlockState state, boolean glowing) implements AttachmentContent {
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
