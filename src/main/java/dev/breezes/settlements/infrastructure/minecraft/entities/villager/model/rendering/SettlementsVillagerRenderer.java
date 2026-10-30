package dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.breezes.settlements.di.ClientComponent;
import dev.breezes.settlements.di.SettlementsDagger;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.SettlementsVillagerModel;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.model.UmbrellaModel;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Map;

public final class SettlementsVillagerRenderer extends MobRenderer<BaseVillager, SettlementsVillagerModel> {

    private static final float SHADOW_RADIUS = 0.5F;
    // Compensate for the model's dimensions so its rendered size matches vanilla villagers
    private static final float VANILLA_MODEL_SCALE = 0.9375F;

    private final VillagerAnimationRenderAdapter animationAdapter;

    public SettlementsVillagerRenderer(@Nonnull EntityRendererProvider.Context context) {
        this(context, SettlementsDagger.client(),
                new VillagerAnimationRenderAdapter(SettlementsDagger.client().observationalVillagerRenderFlag()));
    }

    private SettlementsVillagerRenderer(@Nonnull EntityRendererProvider.Context context,
                                        @Nonnull ClientComponent clientComponent,
                                        @Nonnull VillagerAnimationRenderAdapter animationAdapter) {
        super(context,
                new SettlementsVillagerModel(context.bakeLayer(SettlementsVillagerModel.LAYER), animationAdapter),
                SHADOW_RADIUS);
        this.animationAdapter = animationAdapter;

        // Baked attachment models belong to this renderer's context, so the registry is built with it
        UmbrellaModel umbrellaModel = new UmbrellaModel(context.bakeLayer(UmbrellaModel.LAYER));
        AttachmentModelRegistry attachmentModelRegistry = new InMemoryAttachmentModelRegistry(Map.of(UmbrellaModel.ID, umbrellaModel));
        this.addLayer(new AttachmentRenderLayer(
                this,
                animationAdapter,
                context.getItemInHandRenderer(),
                context.getBlockRenderDispatcher(),
                attachmentModelRegistry,
                clientComponent.attachmentProviders(),
                clientComponent.slotAnchorRegistry(),
                clientComponent.socketRegistry(),
                clientComponent.attachmentDisplayProfileRegistry()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
        this.addLayer(new SootOverlayRenderLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(@Nonnull BaseVillager villager) {
        return ResourceLocationUtil.mod("textures/entity/base_villager.png");
    }

    @Override
    protected void scale(@Nonnull BaseVillager villager, @Nonnull PoseStack poseStack, float partialTickTime) {
        float finalScale = VANILLA_MODEL_SCALE * villager.getAgeScale();
        poseStack.scale(finalScale, finalScale, finalScale);
    }

    @Override
    protected float getShadowRadius(@Nonnull BaseVillager villager) {
        float f = super.getShadowRadius(villager);
        return villager.isBaby() ? f * 0.5F : f;
    }

    @Override
    protected void setupRotations(@Nonnull BaseVillager villager,
                                  @Nonnull PoseStack poseStack,
                                  float bob,
                                  float yBodyRot,
                                  float partialTick,
                                  float scale) {
        this.animationAdapter.observeBodyHeading(villager, yBodyRot);
        super.setupRotations(villager, poseStack, bob, yBodyRot, partialTick, scale);
    }

    @Override
    public void render(@Nonnull BaseVillager villager,
                       float yaw,
                       float partialTicks,
                       @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer,
                       int packedLight) {
        boolean startRaisedFlag = this.animationAdapter.startRender(villager, partialTicks);
        try {
            super.render(villager, yaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            this.animationAdapter.finishRender(startRaisedFlag);
        }
    }

}
