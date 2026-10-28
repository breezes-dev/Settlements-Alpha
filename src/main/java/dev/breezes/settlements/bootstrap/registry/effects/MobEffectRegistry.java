package dev.breezes.settlements.bootstrap.registry.effects;

import dev.breezes.settlements.SettlementsMod;
import dev.breezes.settlements.infrastructure.minecraft.effects.BoundMobEffect;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MobEffectRegistry {

    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, SettlementsMod.MOD_ID);

    public static final DeferredHolder<MobEffect, BoundMobEffect> BOUND = REGISTRY.register("bound", BoundMobEffect::new);

    public static void register(IEventBus eventBus) {
        REGISTRY.register(eventBus);
    }

}
