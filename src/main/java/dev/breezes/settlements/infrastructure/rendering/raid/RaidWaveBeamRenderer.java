package dev.breezes.settlements.infrastructure.rendering.raid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.breezes.settlements.di.ClientScope;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import javax.annotation.Nonnull;

/**
 * Draws red next-wave beams to build height with normal depth occlusion and no distance cutoff.
 */
@ClientSide
@ClientScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RaidWaveBeamRenderer {

    private static final int COLOR = DyeColor.RED.getTextureDiffuseColor();
    private static final float BEAM_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.25F;

    private final RaidWaveBeamStore beams;

    /**
     * Draws current-level markers during the block-entity render stage.
     */
    public void render(@Nonnull RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !this.beams.hasBeams(level)) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType core = RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false);
        RenderType glow = RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        // Switching shared render types flushes queued geometry, which must retain the world's fog and clipping.
        buffers.endLastBatch();
        endBeamBatches(buffers, core, glow);
        float fogStart = RenderSystem.getShaderFogStart();
        boolean depthClampWasEnabled = GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        try {
            // Extending the projection would misalign the world's depth buffer; clamping preserves terrain occlusion.
            GL11.glEnable(GL32.GL_DEPTH_CLAMP);
            FogRenderer.setupNoFog();
            Vec3 camera = event.getCamera().getPosition();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            this.beams.forEachPosition(position -> drawBeam(event.getPoseStack(), buffers, level, camera, position, partialTick));
        } finally {
            try {
                endBeamBatches(buffers, core, glow);
            } finally {
                if (!depthClampWasEnabled) {
                    GL11.glDisable(GL32.GL_DEPTH_CLAMP);
                }
                RenderSystem.setShaderFogStart(fogStart);
            }
        }
    }

    private static void endBeamBatches(@Nonnull MultiBufferSource.BufferSource buffers,
                                       @Nonnull RenderType core,
                                       @Nonnull RenderType glow) {
        try {
            buffers.endBatch(core);
        } finally {
            buffers.endBatch(glow);
        }
    }

    private static void drawBeam(@Nonnull PoseStack pose,
                                 @Nonnull MultiBufferSource.BufferSource buffers,
                                 @Nonnull ClientLevel level,
                                 @Nonnull Vec3 camera,
                                 @Nonnull BlockPos position,
                                 float partialTick) {
        int height = level.getMaxBuildHeight() - position.getY();
        if (height <= 0) {
            return;
        }

        pose.pushPose();
        try {
            // Subtract before narrowing to floats so beams remain anchored far from the world origin.
            pose.translate(position.getX() - camera.x, position.getY() - camera.y, position.getZ() - camera.z);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick,
                    1.0F, level.getGameTime(), 0, height, COLOR, BEAM_RADIUS, GLOW_RADIUS);
        } finally {
            pose.popPose();
        }
    }

}
