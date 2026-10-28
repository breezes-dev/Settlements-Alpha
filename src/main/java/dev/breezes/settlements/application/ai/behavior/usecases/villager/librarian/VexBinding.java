package dev.breezes.settlements.application.ai.behavior.usecases.villager.librarian;

import dev.breezes.settlements.bootstrap.registry.effects.MobEffectRegistry;
import dev.breezes.settlements.domain.tags.SettlementsEntityTypeTags;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

/**
 * Which of the hostiles a librarian senses it can still bind.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VexBinding {

    /**
     * Whether the entity is a living, bindable entity in the given level that is not already bound.
     */
    public static boolean isBindable(@Nonnull LivingEntity entity, @Nonnull Level level) {
        return entity.isAlive()
                && entity.level() == level
                && entity.getType().is(SettlementsEntityTypeTags.BINDABLE)
                && !entity.hasEffect(MobEffectRegistry.BOUND);
    }

}
