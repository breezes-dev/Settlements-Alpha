package dev.breezes.settlements.infrastructure.minecraft.blocks.ballista;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.BallistaAnimationTargets;
import dev.breezes.settlements.domain.ballista.BallistaGeometry;
import dev.breezes.settlements.infrastructure.rendering.animation.ModelPartRigBinding;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ballista's rig, authored in entity-model space with y pointing down.
 */
@ClientSide
public class BallistaModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocationUtil.mod("ballista"), "main");

    private static final ResourceLocation TEXTURE = ResourceLocationUtil.mod("textures/entity/ballista.png");

    private final ModelPart root;
    private final ModelPart turret;
    private final ModelPartRigBinding rigBinding;

    /**
     * Every bone from the root down to the bolt socket, outermost first.
     */
    private final List<ModelPart> boltSocketChain;

    /**
     * @throws IllegalArgumentException if a model-part channel of the ballista rig names a part the layer lacks
     */
    public BallistaModel(@Nonnull ModelPart root) {
        this.root = root;
        this.turret = root.getChild("turret");
        ModelPart gimbal = this.turret.getChild("gimbal");
        ModelPart cradle = gimbal.getChild("cradle");
        ModelPart limbLeftOuter = cradle.getChild("limb_left").getChild("limb_left_outer");
        ModelPart limbRightOuter = cradle.getChild("limb_right").getChild("limb_right_outer");
        ModelPart pusher = cradle.getChild("pusher");

        Map<String, ModelPart> partsByName = Map.ofEntries(
                Map.entry("gimbal", gimbal),
                Map.entry("limb_left_outer", limbLeftOuter),
                Map.entry("limb_left_tip", limbLeftOuter.getChild("limb_left_tip")),
                Map.entry("string_left", limbLeftOuter.getChild("string_left")),
                Map.entry("limb_right_outer", limbRightOuter),
                Map.entry("limb_right_tip", limbRightOuter.getChild("limb_right_tip")),
                Map.entry("string_right", limbRightOuter.getChild("string_right")),
                Map.entry("pusher", pusher),
                Map.entry("connector", cradle.getChild("connector")),
                Map.entry("crankshaft", cradle.getChild("crankshaft")));
        this.rigBinding = new ModelPartRigBinding(BallistaAnimationTargets.RIG, root,
                name -> Optional.ofNullable(partsByName.get(name)));

        this.boltSocketChain = List.of(root, this.turret, gimbal, cradle, pusher, pusher.getChild("bolt_socket"));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("base", CubeListBuilder.create()
                        .texOffs(46, 73).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.05F))
                        .texOffs(74, 51).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(74, 36).addBox(-2.0F, -2.0F, -6.0F, 4.0F, 2.0F, 12.0F, new CubeDeformation(0.01F))
                        .texOffs(74, 61).addBox(-6.0F, -2.0F, -2.0F, 12.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition turret = root.addOrReplaceChild("turret", CubeListBuilder.create(),
                offset(BallistaGeometry.TURRET_OFFSET));

        PartDefinition gimbal = turret.addOrReplaceChild("gimbal", CubeListBuilder.create(),
                offset(BallistaGeometry.GIMBAL_OFFSET));

        gimbal.addOrReplaceChild("mount_front_r1", CubeListBuilder.create()
                        .texOffs(52, 84).addBox(-1.0F, -4.6F, -1.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(-0.01F)),
                PartPose.offsetAndRotation(0.0F, -3.4F, -5.0F, 1.5708F, -0.3927F, -1.5708F));

        gimbal.addOrReplaceChild("mount_back_r1", CubeListBuilder.create()
                        .texOffs(46, 84).addBox(-1.0F, -4.5F, -1.0F, 1.0F, 11.0F, 2.0F, new CubeDeformation(-0.01F)),
                PartPose.offsetAndRotation(0.0F, -3.0F, 5.0F, 1.5708F, 0.3054F, -1.5708F));

        gimbal.addOrReplaceChild("gimbal_r1", CubeListBuilder.create()
                        .texOffs(80, 12).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

        PartDefinition cradle = gimbal.addOrReplaceChild("cradle", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, 0.0F, -17.0F, 6.0F, 2.0F, 34.0F, new CubeDeformation(-0.01F)),
                offset(BallistaGeometry.CRADLE_OFFSET));

        cradle.addOrReplaceChild("cradle_left_rail_r1", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-1.0F, -1.0F, -16.0F, 2.0F, 2.0F, 35.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.7854F));

        cradle.addOrReplaceChild("cradle_right_rail_r1", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-1.0F, -1.0F, -16.0F, 2.0F, 2.0F, 35.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, -1.0F, -1.0F, 0.0F, 0.0F, -0.7854F));

        PartDefinition limbLeft = cradle.addOrReplaceChild("limb_left", CubeListBuilder.create()
                        .texOffs(80, 20).addBox(-2.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(58, 84).addBox(6.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(5.0F, -1.0F, -13.0F));

        PartDefinition limbLeftOuter = limbLeft.addOrReplaceChild("limb_left_outer", CubeListBuilder.create()
                        .texOffs(74, 67).addBox(-0.5F, -1.0F, -1.0F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.5F, 0.0F, 0.0F, 0.0F, -0.4363F, 0.0F));

        PartDefinition limbLeftTip = limbLeftOuter.addOrReplaceChild("limb_left_tip", CubeListBuilder.create(),
                PartPose.offset(10.0F, 0.0F, 0.0F));

        limbLeftTip.addOrReplaceChild("limb_tip_r1", CubeListBuilder.create()
                        .texOffs(80, 24).addBox(-1.5F, -1.4F, -1.5F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.4363F, 0.0F));

        limbLeftOuter.addOrReplaceChild("string_left", CubeListBuilder.create()
                        .texOffs(74, 50).addBox(-20.5F, 0.0F, -0.25F, 21.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(10.0F, 0.25F, 1.5F, 0.0F, 0.4363F, 0.0F));

        PartDefinition limbRight = cradle.addOrReplaceChild("limb_right", CubeListBuilder.create()
                        .texOffs(80, 20).addBox(-6.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(58, 84).addBox(-9.0F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-5.0F, -1.0F, -13.0F));

        PartDefinition limbRightOuter = limbRight.addOrReplaceChild("limb_right_outer", CubeListBuilder.create()
                        .texOffs(74, 67).addBox(-10.5F, -1.0F, -1.0F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-8.5F, 0.0F, 0.0F, 0.0F, 0.4363F, 0.0F));

        PartDefinition limbRightTip = limbRightOuter.addOrReplaceChild("limb_right_tip", CubeListBuilder.create(),
                PartPose.offset(-10.0F, 0.0F, 0.0F));

        limbRightTip.addOrReplaceChild("limb_tip_r2", CubeListBuilder.create()
                        .texOffs(80, 24).addBox(-1.5F, -1.4F, -1.5F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.4363F, 0.0F));

        limbRightOuter.addOrReplaceChild("string_right", CubeListBuilder.create()
                        .texOffs(74, 50).addBox(-0.5F, 0.0F, -0.25F, 21.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-10.0F, 0.25F, 1.5F, 0.0F, -0.4363F, 0.0F));

        PartDefinition pusher = cradle.addOrReplaceChild("pusher", CubeListBuilder.create()
                        .texOffs(80, 31).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                offset(BallistaGeometry.PUSHER_OFFSET));

        pusher.addOrReplaceChild("bolt_socket", CubeListBuilder.create(),
                offset(BallistaGeometry.BOLT_SOCKET_OFFSET));

        cradle.addOrReplaceChild("connector", CubeListBuilder.create()
                        .texOffs(0, 73).addBox(-0.5F, 0.5F, -22.0F, 1.0F, 0.0F, 22.0F, new CubeDeformation(-0.01F)),
                PartPose.offsetAndRotation(0.0F, -1.0F, 15.0F, -0.0175F, 0.0F, 0.0F));

        PartDefinition crankshaft = cradle.addOrReplaceChild("crankshaft", CubeListBuilder.create()
                        .texOffs(80, 0).addBox(-5.0F, -1.0F, -1.0F, 10.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 15.0F, -0.5236F, 0.0F, 0.0F));

        PartDefinition crankHandleRight = crankshaft.addOrReplaceChild("crank_handle_right", CubeListBuilder.create()
                        .texOffs(80, 4).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-6.0F, 0.0F, 0.0F));

        crankHandleRight.addOrReplaceChild("vertical_r1", CubeListBuilder.create()
                        .texOffs(80, 4).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.01F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        PartDefinition crankHandleLeft = crankshaft.addOrReplaceChild("crank_handle_left", CubeListBuilder.create()
                        .texOffs(80, 4).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(6.0F, 0.0F, 0.0F));

        crankHandleLeft.addOrReplaceChild("vertical_r2", CubeListBuilder.create()
                        .texOffs(80, 4).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.01F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static PartPose offset(@Nonnull Vec3 offset) {
        return PartPose.offset((float) offset.x, (float) offset.y, (float) offset.z);
    }

    /**
     * Poses the whole rig from its rest pose: the aim bone turned about its own pivot by the given model-space
     * rotations, in radians, and every clip-driven bone moved by the pose. Whatever an earlier call left on the rig is
     * discarded rather than added to. Allocates nothing.
     *
     * @throws IllegalArgumentException if the pose is not on the ballista rig
     */
    public void pose(float turretYRot, float turretXRot, @Nonnull AnimationPose pose) {
        this.rigBinding.resetToRest();
        this.turret.yRot += turretYRot;
        this.turret.xRot += turretXRot;
        this.rigBinding.apply(pose);
    }

    public void render(@Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        // A culling cutout, not entityCutoutNoCull: the strings and the connector rope are zero-thickness planes,
        // whose coincident top and bottom faces z-fight unless the one facing away is culled
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        this.root.render(poseStack, consumer, packedLight, packedOverlay);
    }

    /**
     * Moves the pose from the rig's draw origin to the bolt socket as the rig was last posed, so a bolt drawn from
     * there sits in the socket and rides the pusher through the clips.
     */
    public void translateToBoltSocket(@Nonnull PoseStack poseStack) {
        for (ModelPart part : this.boltSocketChain) {
            part.translateAndRotate(poseStack);
        }
    }

}
