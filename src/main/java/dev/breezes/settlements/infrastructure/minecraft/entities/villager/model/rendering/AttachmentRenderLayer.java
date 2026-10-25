package dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.breezes.settlements.domain.animation.AnimationBone;
import dev.breezes.settlements.domain.animation.AnimationChannelKind;
import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.AnimationRig;
import dev.breezes.settlements.domain.animation.VillagerAnimationRig;
import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.domain.attachment.AttachmentContent;
import dev.breezes.settlements.domain.attachment.AttachmentProvider;
import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import dev.breezes.settlements.domain.attachment.RenderableAttachment;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.domain.presentation.AttachmentDisplayProfile;
import dev.breezes.settlements.domain.presentation.AttachmentDisplayProfileRegistry;
import dev.breezes.settlements.domain.presentation.SlotAnchor;
import dev.breezes.settlements.domain.presentation.SlotAnchorRegistry;
import dev.breezes.settlements.domain.presentation.Socket;
import dev.breezes.settlements.domain.presentation.SocketRegistry;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.SettlementsVillagerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws all attachments the providers emit for a villager, placed by its socket, display profile, and the slot's
 * channels in the snapshot for this render.
 */
public final class AttachmentRenderLayer extends RenderLayer<BaseVillager, SettlementsVillagerModel> {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int NO_CHANNEL = -1;

    private final VillagerAnimationRenderAdapter animationAdapter;
    private final ItemInHandRenderer itemInHandRenderer;
    private final AttachmentModelRegistry attachmentModelRegistry;
    private final AttachmentProvider[] attachmentProviders;
    private final SlotAnchorRegistry slotAnchorRegistry;
    private final SocketRegistry socketRegistry;
    private final AttachmentDisplayProfileRegistry displayProfileRegistry;

    // Each slot's channel indexes, resolved once so a render reads them without building the slot's targets
    private final Map<AttachmentSlot, Integer> translationChannelBySlot;
    private final Map<AttachmentSlot, Integer> rotationChannelBySlot;

    // Scratch for applying rotations, so placing an attachment allocates no quaternion
    private final Quaternionf rotationScratch = new Quaternionf();

    public AttachmentRenderLayer(@Nonnull RenderLayerParent<BaseVillager, SettlementsVillagerModel> renderer,
                                 @Nonnull VillagerAnimationRenderAdapter animationAdapter,
                                 @Nonnull ItemInHandRenderer itemInHandRenderer,
                                 @Nonnull AttachmentModelRegistry attachmentModelRegistry,
                                 @Nonnull Set<AttachmentProvider> attachmentProviders,
                                 @Nonnull SlotAnchorRegistry slotAnchorRegistry,
                                 @Nonnull SocketRegistry socketRegistry,
                                 @Nonnull AttachmentDisplayProfileRegistry displayProfileRegistry) {
        super(renderer);
        this.animationAdapter = animationAdapter;
        this.itemInHandRenderer = itemInHandRenderer;
        this.attachmentModelRegistry = attachmentModelRegistry;
        this.attachmentProviders = attachmentProviders.stream()
                .sorted(Comparator.comparingInt(AttachmentProvider::renderOrder)
                        .thenComparing(provider -> provider.getClass().getName()))
                .toArray(AttachmentProvider[]::new);
        this.slotAnchorRegistry = slotAnchorRegistry;
        this.socketRegistry = socketRegistry;
        this.displayProfileRegistry = displayProfileRegistry;
        this.translationChannelBySlot = slotChannels(AnimationChannelKind.TRANSLATION);
        this.rotationChannelBySlot = slotChannels(AnimationChannelKind.ROTATION);
    }

    @Override
    public void render(@Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer,
                       int packedLight,
                       @Nonnull BaseVillager villager,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTicks,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        VillagerAnimationSnapshot snapshot = this.animationAdapter.snapshotFor(villager);
        for (AttachmentProvider provider : this.attachmentProviders) {
            List<RenderableAttachment> attachments = provider.attachmentsFor(villager, snapshot);
            for (int index = 0; index < attachments.size(); index++) {
                this.renderAttachment(poseStack, buffer, packedLight, villager, attachments.get(index), snapshot);
            }
        }
    }

    private void renderAttachment(@Nonnull PoseStack poseStack,
                                  @Nonnull MultiBufferSource buffer,
                                  int packedLight,
                                  @Nonnull BaseVillager villager,
                                  @Nonnull RenderableAttachment attachment,
                                  @Nonnull VillagerAnimationSnapshot snapshot) {
        SlotAnchor anchor = this.slotAnchorRegistry.get(attachment.slot());
        ArmConfiguration arms = snapshot.getArmConfiguration();
        ArmPose handPose = EquipmentSlot.OFF_HAND.equals(attachment.slot()) ? arms.left() : arms.right();
        Socket socket = this.socketRegistry.get(anchor.socketFor(handPose));
        AttachmentDisplayProfile profile = this.displayProfileRegistry.get(attachment.slot(), attachment.category());

        poseStack.pushPose();
        if (socket.isInheritsBoneTransform()) {
            this.getParentModel().applyBoneTransform(poseStack, socket.getBone());
        }

        this.applyTransform(poseStack, socket.getLocalTranslation(), socket.getLocalRotation(), socket.getLocalScale());
        this.applyTransform(poseStack, profile.getTranslation(), profile.getRotation(), profile.getScale());
        this.applySlotChannels(poseStack, snapshot.getPose(), attachment.slot(),
                this.rootPivotOffsetFor(attachment.content()));

        ItemDisplayContext displayContext = profile.getDisplayContextOverride() == null
                ? anchor.getDefaultDisplayContext()
                : profile.getDisplayContextOverride();
        this.renderContent(attachment.content(), villager, displayContext, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderContent(@Nonnull AttachmentContent content,
                               @Nonnull BaseVillager villager,
                               @Nonnull ItemDisplayContext displayContext,
                               @Nonnull PoseStack poseStack,
                               @Nonnull MultiBufferSource buffer,
                               int packedLight) {
        switch (content) {
            case AttachmentContent.ItemContent itemContent ->
                    this.itemInHandRenderer.renderItem(villager, itemContent.stack(), displayContext, false, poseStack,
                            buffer, packedLight);
            case AttachmentContent.ModelContent modelContent -> {
                AttachmentModel model = this.attachmentModelRegistry.get(modelContent.modelId());
                if (model != null) {
                    model.render(modelContent.pose(), poseStack, buffer, packedLight, modelContent.texture());
                }
            }
            case AttachmentContent.BillboardContent billboardContent -> {
                // TODO: Render camera-facing quads once transient visual props are introduced.
            }
        }
    }

    private void applyTransform(@Nonnull PoseStack poseStack,
                                @Nonnull Vec3 translation,
                                @Nonnull Vector3f rotation,
                                float scale) {
        poseStack.translate(translation.x, translation.y, translation.z);
        poseStack.mulPose(this.rotationScratch.rotationXYZ(rotation.x(), rotation.y(), rotation.z()));
        poseStack.scale(scale, scale, scale);
    }

    /**
     * Applies the slot's translation and rotation channels from the pose, turning about the given pivot.
     * A slot the rig has no channel for stays as is.
     */
    private void applySlotChannels(@Nonnull PoseStack poseStack,
                                   @Nonnull AnimationPose pose,
                                   @Nonnull AttachmentSlot slot,
                                   @Nonnull Vec3 pivot) {
        int translationChannel = channelOf(this.translationChannelBySlot, slot);
        if (translationChannel != NO_CHANNEL) {
            poseStack.translate(pose.get(translationChannel, 0), pose.get(translationChannel, 1),
                    pose.get(translationChannel, 2));
        }

        int rotationChannel = channelOf(this.rotationChannelBySlot, slot);
        if (rotationChannel != NO_CHANNEL) {
            poseStack.translate(pivot.x, pivot.y, pivot.z);
            poseStack.mulPose(this.rotationScratch.rotationXYZ(pose.get(rotationChannel, 0), pose.get(rotationChannel, 1),
                    pose.get(rotationChannel, 2)));
            poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
        }
    }

    /**
     * Only a model attachment has a rig whose root can sit off the draw origin.
     * An item is rendered by the item pipeline in its own space, which owns its own centering.
     */
    private Vec3 rootPivotOffsetFor(@Nonnull AttachmentContent content) {
        if (!(content instanceof AttachmentContent.ModelContent modelContent)) {
            return Vec3.ZERO;
        }

        AttachmentModel model = this.attachmentModelRegistry.get(modelContent.modelId());
        return model == null ? Vec3.ZERO : model.rootPivotOffset();
    }

    private static int channelOf(@Nonnull Map<AttachmentSlot, Integer> channelBySlot, @Nonnull AttachmentSlot slot) {
        Integer channel = channelBySlot.get(slot);
        return channel == null ? NO_CHANNEL : channel;
    }

    private static Map<AttachmentSlot, Integer> slotChannels(@Nonnull AnimationChannelKind kind) {
        Map<AttachmentSlot, Integer> channelBySlot = new HashMap<>();
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            if (RIG.kind(channel) == kind
                    && RIG.channel(channel).getBone() instanceof AnimationBone.Slot(AttachmentSlot slot)) {
                channelBySlot.put(slot, channel);
            }
        }
        return Map.copyOf(channelBySlot);
    }

}
