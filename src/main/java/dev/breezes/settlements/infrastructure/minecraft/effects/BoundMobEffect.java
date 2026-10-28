package dev.breezes.settlements.infrastructure.minecraft.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Zeroes its entity's velocity on every tick it lasts.
 */
public final class BoundMobEffect extends MobEffect {

    private static final int COLOR = 0xB28DFF;

    /**
     * The ticks over which a client copy's blend factor rises after the bind and falls before the bind expires.
     */
    private static final int BLEND_DURATION_TICKS = 10;

    public BoundMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        this.setBlendDuration(BLEND_DURATION_TICKS);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        // Both sides zero their own copy. Only the side that simulates the entity moves it, the server for a mob and
        // the client for its own player, and the other copy takes its position from that side
        livingEntity.setDeltaMovement(Vec3.ZERO);
        return true;
    }

}
