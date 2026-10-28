package dev.breezes.settlements.bootstrap.event;

import dev.breezes.settlements.bootstrap.registry.effects.MobEffectRegistry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nonnull;

/**
 * Gives every client tracking an entity the server's Bound effect for it, with its remaining duration, through
 * vanilla's own effect packets.
 * <p>
 * Vanilla sends an entity's effects only to that entity when it is a player, so without this no client knows another
 * entity is bound.
 */
@EventBusSubscriber
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BoundEffectSyncServerEvents {

    @SubscribeEvent
    public static void onEffectAdded(@Nonnull MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance added = event.getEffectInstance();
        if (!(entity.level() instanceof ServerLevel level) || !added.is(MobEffectRegistry.BOUND)) {
            return;
        }

        // The event fires before vanilla merges the new instance into the held one, so the merge is replayed on a
        // copy to send the state the entity ends up with
        MobEffectInstance held = event.getOldEffectInstance();
        MobEffectInstance result = added;
        if (held != null) {
            result = new MobEffectInstance(held);
            if (!result.update(added)) {
                return;
            }
        }

        level.getChunkSource().broadcast(entity, new ClientboundUpdateMobEffectPacket(entity.getId(), result, true));
    }

    // Removal and expiry listen at the lowest priority, and a canceled event never reaches them, so a removal another
    // listener vetoes is not sent
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(@Nonnull MobEffectEvent.Remove event) {
        // The event also fires for an effect the entity does not hold
        if (event.getEffectInstance() != null && event.getEffect().is(MobEffectRegistry.BOUND.getKey())) {
            broadcastRemoval(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(@Nonnull MobEffectEvent.Expired event) {
        MobEffectInstance expired = event.getEffectInstance();
        if (expired != null && expired.is(MobEffectRegistry.BOUND)) {
            broadcastRemoval(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(@Nonnull PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity target)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MobEffectInstance bound = target.getEffect(MobEffectRegistry.BOUND);
        if (bound != null) {
            // Already bound when it comes into view, so its rings show at once instead of closing in
            player.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), bound, false));
        }
    }

    private static void broadcastRemoval(@Nonnull LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcast(entity,
                    new ClientboundRemoveMobEffectPacket(entity.getId(), MobEffectRegistry.BOUND));
        }
    }

}
