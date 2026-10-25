package dev.breezes.settlements.infrastructure.minecraft.entities.villager.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.breezes.settlements.domain.animation.VillagerAnimationRig;
import dev.breezes.settlements.domain.animation.VillagerAnimationSnapshot;
import dev.breezes.settlements.domain.animation.VillagerEye;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.domain.presentation.ModelPartRef;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.rendering.animation.ModelPartRigBinding;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.VillagerHeadModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.Validate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Villager model posed from animation snapshots, with no pose carried over between setupAnim calls.
 */
@OnlyIn(Dist.CLIENT)
public class SettlementsVillagerModel
        extends HierarchicalModel<BaseVillager>
        implements HeadedModel, VillagerHeadModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocationUtil.mod("base_villager"), "main");

    // The side of the square the pupil's texture covers, centered on the pupil's origin
    private static final float PUPIL_TEXTURE_SIZE = 1.0F;

    /**
     * Supplies the snapshot the model applies in one setupAnim call, given the values vanilla passes to it.
     */
    @FunctionalInterface
    public interface SnapshotSource {

        VillagerAnimationSnapshot snapshotForSetupAnim(@Nonnull BaseVillager villager,
                                                       float limbSwing,
                                                       float limbSwingAmount,
                                                       float netHeadYaw,
                                                       float headPitch);

    }

    private final SnapshotSource snapshotSource;

    private final ModelPart root;
    private final ModelPart villager;
    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart monobrow;
    private final ModelPart eyes;
    private final ModelPart eye_left;
    private final ModelPart eyelid_left;
    private final ModelPart eyeball_left;
    private final ModelPart pupil_left;
    private final ModelPart eye_right;
    private final ModelPart eyelid_right;
    private final ModelPart eyeball_right;
    private final ModelPart pupil_right;
    private final ModelPart nose;
    private final ModelPart mouth;
    private final ModelPart headwear;
    private final ModelPart hat;
    private final ModelPart arms;
    private final ModelPart arms_crossed;
    private final ModelPart arms_crossed_socket;
    private final ModelPart arm_crossed_center_whole;
    private final ModelPart arm_crossed_left;
    private final ModelPart arm_crossed_center_left;
    private final ModelPart arm_crossed_right;
    private final ModelPart arm_crossed_center_right;
    private final ModelPart arms_straight;
    private final ModelPart arm_straight_left;
    private final ModelPart arm_straight_left_socket;
    private final ModelPart arm_straight_right;
    private final ModelPart arm_straight_right_socket;
    private final ModelPart bodywear;
    private final ModelPart legs;
    private final ModelPart leg_left;
    private final ModelPart leg_right;
    private final ModelPart feet_center_socket;

    private final ModelPartRigBinding rigBinding;

    // Cache the complete chains so applying a bone transform allocates nothing
    private final ModelPart[][] chainsByBone;

    // Kept out of the rendered hierarchy, so neither the reset nor the render ever reaches it
    private final ModelPart drawnHead = new ModelPart(List.of(), Map.of());

    // Scratch for composing the drawn head, so composing allocates nothing
    private final Quaternionf composedRotation = new Quaternionf();
    private final Quaternionf partRotation = new Quaternionf();
    private final Vector3f composedOffset = new Vector3f();
    private final Vector3f partOffset = new Vector3f();
    private final Vector3f drawnHeadAngles = new Vector3f();

    /**
     * @param root           the baked layer's root
     * @param snapshotSource supplies the snapshot each setupAnim applies
     * @throws IllegalArgumentException if a model-part channel of the villager rig names a part the layer lacks, or a
     *                                  part from the root down to the head has a rest rotation
     */
    public SettlementsVillagerModel(@Nonnull ModelPart root, @Nonnull SnapshotSource snapshotSource) {
        this.snapshotSource = snapshotSource;
        this.root = root.getChild("root");
        this.villager = this.root.getChild("villager");
        this.torso = this.villager.getChild("torso");
        this.head = this.torso.getChild("head");
        this.monobrow = this.head.getChild("monobrow");
        this.eyes = this.head.getChild("eyes");
        this.eye_left = this.eyes.getChild("eye_left");
        this.eyelid_left = this.eye_left.getChild("eyelid_left");
        this.eyeball_left = this.eye_left.getChild("eyeball_left");
        this.pupil_left = this.eyeball_left.getChild("pupil_left");
        this.eye_right = this.eyes.getChild("eye_right");
        this.eyelid_right = this.eye_right.getChild("eyelid_right");
        this.eyeball_right = this.eye_right.getChild("eyeball_right");
        this.pupil_right = this.eyeball_right.getChild("pupil_right");
        this.nose = this.head.getChild("nose");
        this.mouth = this.head.getChild("mouth");
        this.headwear = this.head.getChild("headwear");
        this.hat = this.head.getChild("hat");
        this.arms = this.torso.getChild("arms");
        this.arms_crossed = this.arms.getChild("arms_crossed");
        this.arms_crossed_socket = this.arms_crossed.getChild("arms_crossed_socket");
        this.arm_crossed_center_whole = this.arms_crossed.getChild("arm_crossed_center_whole");
        this.arm_crossed_left = this.arms_crossed.getChild("arm_crossed_left");
        this.arm_crossed_center_left = this.arms_crossed.getChild("arm_crossed_center_left");
        this.arm_crossed_right = this.arms_crossed.getChild("arm_crossed_right");
        this.arm_crossed_center_right = this.arms_crossed.getChild("arm_crossed_center_right");
        this.arms_straight = this.arms.getChild("arms_straight");
        this.arm_straight_left = this.arms_straight.getChild("arm_straight_left");
        this.arm_straight_left_socket = this.arm_straight_left.getChild("arm_straight_left_socket");
        this.arm_straight_right = this.arms_straight.getChild("arm_straight_right");
        this.arm_straight_right_socket = this.arm_straight_right.getChild("arm_straight_right_socket");
        this.bodywear = this.torso.getChild("bodywear");
        this.legs = this.villager.getChild("legs");
        this.leg_left = this.legs.getChild("leg_left");
        this.leg_right = this.legs.getChild("leg_right");
        this.feet_center_socket = this.legs.getChild("feet_center_socket");

        // Rest rotations would make the drawn head disagree with the orientation represented by animation channels
        requireNoRestRotation(this.root, "root");
        requireNoRestRotation(this.villager, "villager");
        requireNoRestRotation(this.torso, "torso");
        requireNoRestRotation(this.head, "head");

        this.arms_crossed.visible = true;
        this.arms_straight.visible = true;

        this.rigBinding = new ModelPartRigBinding(VillagerAnimationRig.RIG, this.root, this::getAnyDescendantWithName);
        this.chainsByBone = new ModelPart[ModelPartRef.values().length][];
        for (ModelPartRef bone : ModelPartRef.values()) {
            this.chainsByBone[bone.ordinal()] = this.chainTo(bone);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition villager = root.addOrReplaceChild("villager", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition torso = villager.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, -12.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        PartDefinition monobrow = head.addOrReplaceChild("monobrow", CubeListBuilder.create().texOffs(24, 0).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.05F)), PartPose.offset(0.0F, -4.5F, -3.6875F));

        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(2.0F, -3.5F, -4.0F));

        PartDefinition eye_left = eyes.addOrReplaceChild("eye_left", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition eyelid_left = eye_left.addOrReplaceChild("eyelid_left", CubeListBuilder.create().texOffs(24, 4).addBox(-1.0F, -0.5F, -0.0625F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0F, 0.0F));

        PartDefinition eyeball_left = eye_left.addOrReplaceChild("eyeball_left", eyeballBox(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition pupil_left = eyeball_left.addOrReplaceChild("pupil_left", pupilBox(),
                PartPose.offset(VillagerEye.LEFT.pupilRestX(), VillagerEye.PUPIL_REST_Y, 0.0F));

        PartDefinition eye_right = eyes.addOrReplaceChild("eye_right", CubeListBuilder.create(), PartPose.offset(-4.0F, 0.0F, 0.0F));

        PartDefinition eyelid_right = eye_right.addOrReplaceChild("eyelid_right", CubeListBuilder.create().texOffs(24, 4).addBox(-1.0F, -0.5F, -0.0625F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0F, 0.0F));

        PartDefinition eyeball_right = eye_right.addOrReplaceChild("eyeball_right", eyeballBox(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition pupil_right = eyeball_right.addOrReplaceChild("pupil_right", pupilBox(),
                PartPose.offset(VillagerEye.RIGHT.pupilRestX(), VillagerEye.PUPIL_REST_Y, 0.0F));

        PartDefinition nose = head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, -4.0F));

        PartDefinition mouth = head.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(24, 2).addBox(-2.0F, -0.5F, -0.125F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, -4.0F));

        PartDefinition headwear = head.addOrReplaceChild("headwear", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(30, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        PartDefinition arms = torso.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, -9.525F, 0.0F));

        PartDefinition arms_crossed = arms.addOrReplaceChild("arms_crossed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.475F, -1.05F, -0.7854F, 0.0F, 0.0F));

        PartDefinition arms_crossed_socket = arms_crossed.addOrReplaceChild("arms_crossed_socket", CubeListBuilder.create(), PartPose.offset(0.0F, 3.7123F, -2.6517F));

        PartDefinition arm_crossed_center_whole = arms_crossed.addOrReplaceChild("arm_crossed_center_whole", CubeListBuilder.create().texOffs(40, 38).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition arm_crossed_left = arms_crossed.addOrReplaceChild("arm_crossed_left", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-2.0F, -4.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(6.0F, 2.0F, 0.0F));

        PartDefinition arm_crossed_center_left = arms_crossed.addOrReplaceChild("arm_crossed_center_left", CubeListBuilder.create().texOffs(7, 10).addBox(4.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(46, 38).addBox(6.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, 4.0F, 0.0F));

        PartDefinition arm_crossed_right = arms_crossed.addOrReplaceChild("arm_crossed_right", CubeListBuilder.create().texOffs(44, 22).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 2.0F, 0.0F));

        PartDefinition arm_crossed_center_right = arms_crossed.addOrReplaceChild("arm_crossed_center_right", CubeListBuilder.create().texOffs(9, 10).addBox(-2.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(46, 38).addBox(-4.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition arms_straight = arms.addOrReplaceChild("arms_straight", CubeListBuilder.create(), PartPose.offset(0.0F, -0.475F, 0.0F));

        PartDefinition arm_straight_left = arms_straight.addOrReplaceChild("arm_straight_left", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.05F, -2.05F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(44, 38).mirror().addBox(-1.0F, 5.95F, -2.05F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(4, 12).mirror().addBox(-1.0F, 7.95F, -2.05F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, 0.0F, 0.0F));

        PartDefinition arm_straight_left_socket = arm_straight_left.addOrReplaceChild("arm_straight_left_socket", CubeListBuilder.create(), PartPose.offset(1.0F, 8.0F, 0.0F));

        PartDefinition arm_straight_right = arms_straight.addOrReplaceChild("arm_straight_right", CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.05F, -2.05F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(44, 38).addBox(-3.0F, 5.95F, -2.05F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(4, 12).addBox(-3.0F, 7.95F, -2.05F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 0.0F, 0.0F));

        PartDefinition arm_straight_right_socket = arm_straight_right.addOrReplaceChild("arm_straight_right_socket", CubeListBuilder.create(), PartPose.offset(-1.0F, 8.0F, 0.0F));

        PartDefinition bodywear = torso.addOrReplaceChild("bodywear", CubeListBuilder.create().texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        PartDefinition legs = villager.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(2.0F, -12.0F, 0.0F));

        PartDefinition leg_left = legs.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition leg_right = legs.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, 0.0F, 0.0F));

        PartDefinition feet_center_socket = legs.addOrReplaceChild("feet_center_socket", CubeListBuilder.create(), PartPose.offset(-2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    private static CubeListBuilder eyeballBox() {
        return CubeListBuilder.create().texOffs(24, 6).addBox(
                -VillagerEye.WHITE_HALF_WIDTH, -VillagerEye.WHITE_HALF_HEIGHT, -0.0125F,
                2.0F * VillagerEye.WHITE_HALF_WIDTH, 2.0F * VillagerEye.WHITE_HALF_HEIGHT, 1.0F,
                CubeDeformation.NONE);
    }

    private static CubeListBuilder pupilBox() {
        // Each pose reset returns a part's scale to one, so the rest size lives in the box rather than the part's scale
        float faceGrowth = (VillagerEye.PUPIL_REST_SIZE - PUPIL_TEXTURE_SIZE) / 2.0F;
        return CubeListBuilder.create().texOffs(30, 4).addBox(
                -PUPIL_TEXTURE_SIZE / 2.0F, -PUPIL_TEXTURE_SIZE / 2.0F, -0.0375F,
                PUPIL_TEXTURE_SIZE, PUPIL_TEXTURE_SIZE, 1.0F,
                new CubeDeformation(faceGrowth, faceGrowth, 0.0F));
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Returns a geometry-free part carrying the drawn head's full transform in model space as of the last setupAnim.
     * Changing its visibility does not change the rendered head's visibility; use {@link #hatVisible} for that.
     */
    @Override
    public ModelPart getHead() {
        return this.drawnHead;
    }

    @Override
    public void setupAnim(@Nonnull BaseVillager villager,
                          float limbSwing,
                          float limbSwingAmount,
                          float ageInTicks,
                          float netHeadYaw,
                          float headPitch) {
        VillagerAnimationSnapshot snapshot = this.snapshotSource.snapshotForSetupAnim(villager, limbSwing,
                limbSwingAmount, netHeadYaw, headPitch);
        this.rigBinding.resetToRest();
        this.rigBinding.apply(snapshot.getPose());
        this.showArmGeometry(snapshot.getArmConfiguration());
        this.composeDrawnHead();
    }

    /**
     * Applies the requested bone's cumulative transform to a pose stack currently in model space.
     * Does not push or pop the stack.
     */
    public void applyBoneTransform(@Nonnull PoseStack pose, @Nonnull ModelPartRef ref) {
        // The input stack has no model-part transforms, so omitting any ancestor would misplace the bone
        for (ModelPart part : this.chainsByBone[ref.ordinal()]) {
            part.translateAndRotate(pose);
        }
    }

    @Override
    public void hatVisible(boolean visible) {
        // Head geometry and its overlays must share visibility to avoid drawing either in isolation
        this.head.visible = visible;
        this.headwear.visible = visible;
        this.hat.visible = visible;
    }

    private static void requireNoRestRotation(@Nonnull ModelPart part, @Nonnull String name) {
        PartPose rest = part.getInitialPose();
        Validate.isTrue(rest.xRot == 0.0F && rest.yRot == 0.0F && rest.zRot == 0.0F,
                "Villager model part %s has a rest rotation of (%s, %s, %s), which animation's head frame cannot see",
                name, rest.xRot, rest.yRot, rest.zRot);
    }

    private ModelPart[] chainTo(@Nonnull ModelPartRef bone) {
        return switch (bone) {
            case ROOT -> new ModelPart[]{this.root};
            case BODY -> new ModelPart[]{this.root, this.villager, this.torso};
            case HEAD -> new ModelPart[]{this.root, this.villager, this.torso, this.head};
            case ARMS -> new ModelPart[]{this.root, this.villager, this.torso, this.arms, this.arms_crossed};
            case ARMS_CROSSED_SOCKET -> new ModelPart[]{this.root, this.villager, this.torso, this.arms,
                    this.arms_crossed, this.arms_crossed_socket};
            case ARM_STRAIGHT_RIGHT_SOCKET -> new ModelPart[]{this.root, this.villager, this.torso, this.arms,
                    this.arms_straight, this.arm_straight_right, this.arm_straight_right_socket};
            case ARM_STRAIGHT_LEFT_SOCKET -> new ModelPart[]{this.root, this.villager, this.torso, this.arms,
                    this.arms_straight, this.arm_straight_left, this.arm_straight_left_socket};
            case FEET_CENTER_SOCKET -> new ModelPart[]{this.root, this.villager, this.legs, this.feet_center_socket};
        };
    }

    private void showArmGeometry(@Nonnull ArmConfiguration armConfiguration) {
        boolean leftCrossed = armConfiguration.left() == ArmPose.CROSSED;
        boolean rightCrossed = armConfiguration.right() == ArmPose.CROSSED;
        this.arm_crossed_left.visible = leftCrossed;
        this.arm_straight_left.visible = !leftCrossed;
        this.arm_crossed_right.visible = rightCrossed;
        this.arm_straight_right.visible = !rightCrossed;
        // Half-caps close the crossed bar's exposed end when the opposing arm is straight
        this.arm_crossed_center_whole.visible = leftCrossed && rightCrossed;
        this.arm_crossed_center_left.visible = leftCrossed && !rightCrossed;
        this.arm_crossed_center_right.visible = rightCrossed && !leftCrossed;
    }

    private void composeDrawnHead() {
        this.composedOffset.zero();
        this.composedRotation.identity();
        // Offsets must be rotated in their parent's frame to reproduce the full hierarchy with one part.
        // This assumes unit scale throughout the chain; non-unit scale would require composing scale too.
        for (ModelPart part : this.chainsByBone[ModelPartRef.HEAD.ordinal()]) {
            this.partOffset.set(part.x, part.y, part.z);
            this.composedOffset.add(this.composedRotation.transform(this.partOffset));
            this.composedRotation.mul(this.partRotation.rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        // A ZYX rotation's inverse is an XYZ rotation with negated angles, so extract from the inverse and negate
        this.composedRotation.conjugate(this.partRotation).getEulerAnglesXYZ(this.drawnHeadAngles).negate();
        this.drawnHead.setPos(this.composedOffset.x(), this.composedOffset.y(), this.composedOffset.z());
        this.drawnHead.setRotation(this.drawnHeadAngles.x(), this.drawnHeadAngles.y(), this.drawnHeadAngles.z());
    }

}
