package dev.breezes.settlements.infrastructure.minecraft.entities.villager.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.UmbrellaAnimationTargets;
import dev.breezes.settlements.domain.attachment.UmbrellaPattern;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.rendering.AttachmentModel;
import dev.breezes.settlements.infrastructure.rendering.animation.ModelPartRigBinding;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class UmbrellaModel implements AttachmentModel {

    public static final ResourceLocation ID = ResourceLocationUtil.mod("umbrella");
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ID, "main");

    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;

    private static final ResourceLocation[] TEXTURES = Arrays.stream(UmbrellaPattern.values())
            .map(pattern -> ResourceLocationUtil.mod("textures/entity/umbrella/" + pattern.name().toLowerCase(Locale.ROOT) + ".png"))
            .toArray(ResourceLocation[]::new);

    private final ModelPart root;
    private final ModelPartRigBinding rigBinding;

    /**
     * The rig's root offset, which no channel animates.
     */
    private final Vec3 rootPivotOffset;

    /**
     * @throws IllegalArgumentException if a model-part channel of the umbrella rig names a part the layer lacks
     */
    public UmbrellaModel(@Nonnull ModelPart root) {
        this.root = root.getChild("umbrella");
        ModelPart canopy = this.root.getChild("canopy");
        Map<String, ModelPart> partsByName = Map.of(
                "canopy", canopy,
                "north", canopy.getChild("north"),
                "south", canopy.getChild("south"),
                "east", canopy.getChild("east"),
                "west", canopy.getChild("west"));
        this.rigBinding = new ModelPartRigBinding(UmbrellaAnimationTargets.RIG, this.root,
                name -> Optional.ofNullable(partsByName.get(name)));
        this.rootPivotOffset = new Vec3(this.root.x / MODEL_UNITS_PER_BLOCK, this.root.y / MODEL_UNITS_PER_BLOCK,
                this.root.z / MODEL_UNITS_PER_BLOCK);
    }

    /**
     * @return the texture that paints the given skin onto this model
     */
    public static ResourceLocation textureFor(@Nonnull UmbrellaPattern pattern) {
        return TEXTURES[pattern.ordinal()];
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // The root pivot sits on the shaft just above the crook, where the villager grips it
        PartDefinition umbrella = partdefinition.addOrReplaceChild("umbrella", CubeListBuilder.create(), PartPose.offset(0.0F, 19.0F, 0.0F));

        PartDefinition canopy = umbrella.addOrReplaceChild("canopy", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -24.0F, -0.1F, -3.1416F, 0.0F, 3.1416F));

        addPanel(canopy, "north", PartPose.offset(0.0F, -1.0F, -0.4F), PartPose.rotation(-1.5708F, -1.1345F, 1.5708F), -21, 32);
        addPanel(canopy, "east", PartPose.offset(-0.3F, -1.0F, -0.1F), PartPose.rotation(0.0F, 0.0F, 0.4363F), -21, 7);
        addPanel(canopy, "south", PartPose.offset(0.0F, -1.0F, 0.2F), PartPose.rotation(1.5708F, 1.1345F, 1.5708F), 9, 7);
        addPanel(canopy, "west", PartPose.offset(0.3F, -1.0F, -0.1F), PartPose.rotation(3.1416F, 0.0F, 2.7053F), 9, 32);

        PartDefinition handle = umbrella.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -19.0F, -0.5F, 1.0F, 30.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(12, 0).addBox(-0.5F, 10.0F, 0.5F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(18, 0).addBox(-0.5F, 9.0F, 1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(4, 0).addBox(-1.0F, 3.0F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    /**
     * Adds one canopy panel as two nested bones: an outer bone carrying no geometry and a child holding
     * the panel mesh at its fan-out rotation.
     *
     * @param texOffsX box-UV origin of this panel's own region of the texture, horizontally
     * @param texOffsY box-UV origin of this panel's own region of the texture, vertically
     */
    private static void addPanel(PartDefinition canopy,
                                 String name,
                                 PartPose animatedPose,
                                 PartPose panelPose,
                                 int texOffsX,
                                 int texOffsY) {
        PartDefinition panel = canopy.addOrReplaceChild(name, CubeListBuilder.create(), animatedPose);
        panel.addOrReplaceChild(name + "_panel",
                CubeListBuilder.create().texOffs(texOffsX, texOffsY).addBox(0.0F, 0.0F, -12.5F, 15.0F, 0.0F, 25.0F, new CubeDeformation(0.0F)),
                panelPose);
    }

    @Override
    public Vec3 rootPivotOffset() {
        return this.rootPivotOffset;
    }

    @Override
    public void render(@Nonnull AnimationPose pose,
                       @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer,
                       int packedLight,
                       @Nonnull ResourceLocation texture) {
        this.rigBinding.resetToRest();
        this.rigBinding.apply(pose);

        // entityCutoutNoCull, not a culling cutout: only one face of each canopy panel is painted, so
        // culling would make the canopy vanish when viewed from the unpainted side.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        this.root.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
    }

}
