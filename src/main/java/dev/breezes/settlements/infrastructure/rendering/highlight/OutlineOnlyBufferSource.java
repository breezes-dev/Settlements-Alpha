package dev.breezes.settlements.infrastructure.rendering.highlight;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;

import javax.annotation.Nonnull;

/**
 * Wraps {@link OutlineBufferSource} to restrict supplementary entity renders to the outline pass without writing to
 * the main buffer.
 */
@ClientSide
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class OutlineOnlyBufferSource implements MultiBufferSource, SupplementaryEntityPass {

    private final OutlineBufferSource outlineBufferSource;

    static MultiBufferSource wrapping(@Nonnull OutlineBufferSource outlineBufferSource) {
        return new OutlineOnlyBufferSource(outlineBufferSource);
    }

    /**
     * Accepts outline render types directly and maps other types to their outline variant, discarding vertices when no
     * variant exists.
     */
    @Override
    public VertexConsumer getBuffer(@Nonnull RenderType renderType) {
        if (renderType.isOutline()) {
            return outlineBufferSource.getBuffer(renderType);
        }

        // Passing through a non-outline type would also write to the main buffer, risking duplicate draws and
        // z-fighting.
        return renderType.outline()
                .map(outlineBufferSource::getBuffer)
                .orElse(NoOpVertexConsumer.INSTANCE);
    }

    private enum NoOpVertexConsumer implements VertexConsumer {

        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }

}
