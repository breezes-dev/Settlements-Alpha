package dev.breezes.settlements.infrastructure.rendering.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;

/**
 * Draws the two glyph rings around a bound entity: flat bands about its bounding box's center, tilted opposite ways and
 * spinning in opposite directions.
 */
@ClientSide
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BindRingRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocationUtil.mod("textures/entity/bind_ring.png");

    // Translucent, double-sided, and unlit, with the outline off so a glowing entity's outline pass skips the rings
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE, false);

    /**
     * A ring's radius per block of the entity's larger bounding box dimension.
     */
    private static final float RADIUS_PER_ENTITY_SIZE = 0.75F;

    /**
     * A band's width as a fraction of its ring's radius.
     */
    private static final float BAND_WIDTH_PER_RADIUS = 0.25F;

    /**
     * How much larger than its bound size a ring is at a blend factor of zero, the size it closes in from and widens
     * back out to.
     */
    private static final float OPEN_SCALE_GAIN = 1.0F;

    private static final float TILT_RADIANS = 45.0F * Mth.DEG_TO_RAD;
    private static final float TILT_SINE = Mth.sin(TILT_RADIANS);
    private static final float TILT_COSINE = Mth.cos(TILT_RADIANS);

    /**
     * The ticks a ring takes to spin one full turn.
     */
    private static final int SPIN_PERIOD_TICKS = 80;

    private static final int SEGMENTS = 48;

    /**
     * How many times the texture strip runs around a ring.
     * <p>
     * A strip's length over the band's width must stay close to the texture's width over its height, or the glyphs
     * stretch along the ring. This count and the band width set that ratio.
     */
    private static final int STRIP_REPEATS = 3;
    private static final int SEGMENTS_PER_STRIP = SEGMENTS / STRIP_REPEATS;

    /**
     * Draws the rings for the given Bound instance, sized and faded by its blend factor.
     *
     * @param poseStack posed at the entity's render origin
     */
    public static void render(@Nonnull LivingEntity entity,
                              @Nonnull MobEffectInstance bound,
                              float partialTick,
                              @Nonnull PoseStack poseStack,
                              @Nonnull MultiBufferSource buffers) {
        float blend = bound.getBlendFactor(entity, partialTick);
        if (blend <= 0.0F) {
            return;
        }

        float scale = 1.0F + OPEN_SCALE_GAIN * (1.0F - blend);
        float radius = RADIUS_PER_ENTITY_SIZE * Math.max(entity.getBbWidth(), entity.getBbHeight()) * scale;
        float halfBandWidth = radius * BAND_WIDTH_PER_RADIUS * 0.5F;
        int alpha = Math.round(Mth.clamp(blend, 0.0F, 1.0F) * 255.0F);
        // Reduced to one period before adding the partial tick, so the angle keeps its precision on a long-lived entity
        float spin = ((entity.tickCount % SPIN_PERIOD_TICKS) + partialTick) / SPIN_PERIOD_TICKS * Mth.TWO_PI;

        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
            PoseStack.Pose pose = poseStack.last();
            drawRing(pose, consumer, radius, halfBandWidth, TILT_SINE, spin, alpha);
            drawRing(pose, consumer, radius, halfBandWidth, -TILT_SINE, -spin, alpha);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Emits one ring as a band in its own plane, the horizontal plane tilted about the X axis.
     *
     * @param tiltSine the sine of the tilt, whose sign picks the tilt's direction
     */
    private static void drawRing(@Nonnull PoseStack.Pose pose,
                                 @Nonnull VertexConsumer consumer,
                                 float radius,
                                 float halfBandWidth,
                                 float tiltSine,
                                 float spin,
                                 int alpha) {
        float innerRadius = radius - halfBandWidth;
        float outerRadius = radius + halfBandWidth;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float startAngle = spin + Mth.TWO_PI * segment / SEGMENTS;
            float endAngle = spin + Mth.TWO_PI * (segment + 1) / SEGMENTS;
            float startCosine = Mth.cos(startAngle);
            float startSine = Mth.sin(startAngle);
            float endCosine = Mth.cos(endAngle);
            float endSine = Mth.sin(endAngle);
            // Each segment maps its own slice of [0, 1], so the strip never depends on texture wrap
            int stripSlot = segment % SEGMENTS_PER_STRIP;
            float startU = (float) stripSlot / SEGMENTS_PER_STRIP;
            float endU = (float) (stripSlot + 1) / SEGMENTS_PER_STRIP;

            vertex(pose, consumer, innerRadius * startCosine, innerRadius * startSine, tiltSine, startU, 1.0F, alpha);
            vertex(pose, consumer, outerRadius * startCosine, outerRadius * startSine, tiltSine, startU, 0.0F, alpha);
            vertex(pose, consumer, outerRadius * endCosine, outerRadius * endSine, tiltSine, endU, 0.0F, alpha);
            vertex(pose, consumer, innerRadius * endCosine, innerRadius * endSine, tiltSine, endU, 1.0F, alpha);
        }
    }

    /**
     * Emits a vertex given in the ring's plane, tilting it about the X axis into place.
     */
    private static void vertex(@Nonnull PoseStack.Pose pose,
                               @Nonnull VertexConsumer consumer,
                               float planeX,
                               float planeZ,
                               float tiltSine,
                               float u,
                               float v,
                               int alpha) {
        consumer.addVertex(pose, planeX, -planeZ * tiltSine, planeZ * TILT_COSINE)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, TILT_COSINE, tiltSine);
    }

}
