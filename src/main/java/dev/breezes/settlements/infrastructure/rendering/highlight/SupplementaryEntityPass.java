package dev.breezes.settlements.infrastructure.rendering.highlight;

import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Marks a {@link MultiBufferSource} that belongs to a supplementary pass — an extra draw of an entity in a frame, to
 * feed an effect buffer, at the same sample key as the frame's own pass would use.
 * <p>
 * Such a pass re-enters the entity render callbacks. State advanced once per sample key is unaffected: whichever call
 * reaches the key first advances it, and any other finds it already there. Work done once per call, and decoration the
 * effect has no use for, must skip a marked pass: it is the same frame drawn again, not a new one.
 */
@ClientSide
public interface SupplementaryEntityPass {
}
