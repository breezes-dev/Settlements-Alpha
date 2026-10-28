package dev.breezes.settlements.bootstrap.event;

import dev.breezes.settlements.bootstrap.registry.effects.MobEffectRegistry;
import dev.breezes.settlements.infrastructure.rendering.effects.BindRingRenderer;
import dev.breezes.settlements.infrastructure.rendering.highlight.SupplementaryEntityPass;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import javax.annotation.Nonnull;

/**
 * Connects the bind rings to living entity rendering.
 */
@ClientSide
@EventBusSubscriber(Dist.CLIENT)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BindRingClientEvents {

    /**
     * Draws the bind rings around a rendered entity whose client copy holds Bound.
     */
    @SubscribeEvent
    public static <T extends LivingEntity> void onRenderLiving(@Nonnull RenderLivingEvent.Post<T, ? extends EntityModel<T>> event) {
        // A supplementary pass draws the entity again to feed an effect buffer, which has no use for the rings
        if (event.getMultiBufferSource() instanceof SupplementaryEntityPass) {
            return;
        }

        LivingEntity entity = event.getEntity();
        MobEffectInstance bound = entity.getEffect(MobEffectRegistry.BOUND);
        if (bound != null) {
            BindRingRenderer.render(entity, bound, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource());
        }
    }

}
