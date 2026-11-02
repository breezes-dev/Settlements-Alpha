package dev.breezes.settlements.infrastructure.rendering.toss;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.breezes.settlements.di.ClientScope;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Renders visual item tosses between villagers as dropped-item clusters.
 */
@ClientSide
@ClientScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class ItemTossFlightRenderer {

    /**
     * Height of the flight's arc above the straight line from hand to hand, at its midpoint, in blocks.
     */
    private static final double ARC_HEIGHT = 0.6;

    /**
     * Final share of the flight over which the item bends toward the receiver's current hands.
     */
    private static final double CATCH_PULL_SHARE = 0.3;

    private static final float SPIN_RADIANS_PER_TICK = 0.15F;

    private final ItemTossFlightStore flights;

    // Reuse scratch state across flights and frames to avoid per-item vector allocations.
    private final Vector3d itemPosition = new Vector3d();
    private final Vector3d liveCatchPoint = new Vector3d();
    private final Quaternionf itemSpin = new Quaternionf();
    private final BlockPos.MutableBlockPos lightProbe = new BlockPos.MutableBlockPos();
    private final RandomSource clusterRandom = RandomSource.create();

    /**
     * Draws the current level's flights at the after-entities stage and ignores every other stage.
     */
    public void render(@Nonnull RenderLevelStageEvent event) {
        // Render outside the entity pass so supplementary outline renders cannot draw a toss twice.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        List<ItemFlight> active = this.flights.flightsIn(level);
        if (active.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        for (ItemFlight flight : active) {
            // An unlaunched flight has no valid hand positions yet.
            if (!flight.launched()) {
                continue;
            }

            BaseVillager giver = ItemFlight.resolvePresentVillager(level, flight.giverEntityId());
            BaseVillager receiver = ItemFlight.resolvePresentVillager(level, flight.receiverEntityId());
            if (!isAnyParticipantVisible(minecraft.getEntityRenderDispatcher(), event.getFrustum(), camera, giver, receiver)) {
                continue;
            }

            float partialTick = partialTickFor(event.getPartialTick(), level, receiver);
            this.drawFlight(event.getPoseStack(), minecraft, level, camera, partialTick, flight, receiver);
        }
    }

    private void drawFlight(@Nonnull PoseStack pose,
                            @Nonnull Minecraft minecraft,
                            @Nonnull ClientLevel level,
                            @Nonnull Vec3 camera,
                            float partialTick,
                            @Nonnull ItemFlight flight,
                            @Nullable BaseVillager receiver) {
        double sinceLaunch = flight.elapsedTicks() + partialTick;
        float itemScale = 1.0F;
        if (flight.elapsedTicks() < flight.flightTicks()) {
            this.locateInFlight(flight, Mth.clamp(sinceLaunch / flight.flightTicks(), 0.0, 1.0), receiver, partialTick);
        } else if (flight.missed()) {
            double fade = (sinceLaunch - flight.flightTicks()) / ItemFlight.MISSED_CATCH_FADE.getTicks();
            this.itemPosition.set(flight.predictedCatchPoint());
            itemScale = 1.0F - (float) Mth.clamp(fade, 0.0, 1.0);
        } else {
            return;
        }

        this.drawItem(pose, minecraft, level, camera, flight.stack(), (float) sinceLaunch * SPIN_RADIANS_PER_TICK, itemScale);
    }

    /**
     * Writes into itemPosition the point along the flight's arc at the given progress, from 0 at launch to 1 at catch.
     */
    private void locateInFlight(@Nonnull ItemFlight flight,
                                double progress,
                                @Nullable BaseVillager receiver,
                                float partialTick) {
        // Keep the lift zero at both hands so the arc does not displace the launch or catch position.
        double lift = 4.0 * ARC_HEIGHT * progress * (1.0 - progress);
        this.itemPosition.set(flight.launchPoint()).lerp(flight.predictedCatchPoint(), progress).add(0.0, lift, 0.0);

        // Pull toward the live hand position near arrival so a moving receiver can catch the item without shifting
        // the entire arc.
        double pullStart = 1.0 - CATCH_PULL_SHARE;
        if (receiver == null || progress <= pullStart) {
            return;
        }
        double pullRamp = (progress - pullStart) / CATCH_PULL_SHARE;
        ItemFlight.locateHands(receiver, partialTick, this.liveCatchPoint);
        this.itemPosition.lerp(this.liveCatchPoint, pullRamp * pullRamp);
    }

    @SuppressWarnings("deprecation")
    private void drawItem(@Nonnull PoseStack pose,
                          @Nonnull Minecraft minecraft,
                          @Nonnull ClientLevel level,
                          @Nonnull Vec3 camera,
                          @Nonnull ItemStack stack,
                          float spinRadians,
                          float itemScale) {
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, level, null, 0);
        int packedLight = LevelRenderer.getLightColor(level,
                this.lightProbe.set(this.itemPosition.x, this.itemPosition.y, this.itemPosition.z));
        // Leave shared item buffers open for the level's normal batch flush after block entities.
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        pose.pushPose();
        try {
            // Subtract before narrowing to floats so items stay put far from the world origin.
            pose.translate(this.itemPosition.x - camera.x, this.itemPosition.y - camera.y, this.itemPosition.z - camera.z);
            pose.mulPose(this.itemSpin.rotationY(spinRadians));
            pose.scale(itemScale, itemScale, itemScale);
            // Cancel the ground translation to center the item at hand height. The deprecated accessor is needed
            // because applying a transform cannot report this offset alone.
            pose.translate(0.0F, -model.getTransforms().ground.translation.y(), 0.0F);
            // Use a stable seed per stack so the cluster keeps its shape from frame to frame.
            this.clusterRandom.setSeed(ItemEntityRenderer.getSeedForItemStack(stack));
            ItemEntityRenderer.renderMultipleFromCount(itemRenderer, pose, buffers, packedLight, stack, model,
                    model.isGui3d(), this.clusterRandom);
        } finally {
            pose.popPose();
        }
    }

    private static boolean isAnyParticipantVisible(@Nonnull EntityRenderDispatcher dispatcher,
                                                   @Nonnull Frustum frustum,
                                                   @Nonnull Vec3 camera,
                                                   @Nullable BaseVillager giver,
                                                   @Nullable BaseVillager receiver) {
        if (giver == null && receiver == null) {
            // Do not cull solely because both participants disappeared; the flight or fade can still be active.
            return true;
        }
        return (giver != null && dispatcher.shouldRender(giver, frustum, camera.x, camera.y, camera.z))
                || (receiver != null && dispatcher.shouldRender(receiver, frustum, camera.x, camera.y, camera.z));
    }

    /**
     * Returns the receiver's render partial tick, or the level's partial tick when the receiver is absent.
     */
    private static float partialTickFor(@Nonnull DeltaTracker deltaTracker,
                                        @Nonnull ClientLevel level,
                                        @Nullable BaseVillager receiver) {
        // Match the receiver's freeze state so the item and its catch position use the same interpolation time.
        boolean runsNormally = receiver == null
                ? level.tickRateManager().runsNormally()
                : !level.tickRateManager().isEntityFrozen(receiver);
        return deltaTracker.getGameTimeDeltaPartialTick(runsNormally);
    }

}
