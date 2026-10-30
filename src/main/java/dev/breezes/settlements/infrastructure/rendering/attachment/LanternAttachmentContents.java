package dev.breezes.settlements.infrastructure.rendering.attachment;

import dev.breezes.settlements.domain.attachment.AttachmentContent;
import dev.breezes.settlements.domain.entities.OwnedLanternType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared hanging, glowing block content for vanilla lanterns.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LanternAttachmentContents {

    private static final AttachmentContent.BlockStateContent LANTERN = new AttachmentContent.BlockStateContent(
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), true);
    private static final AttachmentContent.BlockStateContent SOUL_LANTERN = new AttachmentContent.BlockStateContent(
            Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), true);

    /**
     * Returns the hanging block content, or null for no owned lantern.
     */
    @Nullable
    static AttachmentContent.BlockStateContent forType(@Nonnull OwnedLanternType type) {
        return switch (type) {
            case NONE -> null;
            case LANTERN -> LANTERN;
            case SOUL_LANTERN -> SOUL_LANTERN;
        };
    }

}
