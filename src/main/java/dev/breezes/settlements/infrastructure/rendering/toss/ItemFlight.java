package dev.breezes.settlements.infrastructure.rendering.toss;

import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Client-side lifecycle state for a visual item toss between two villagers.
 */
@ClientSide
final class ItemFlight {

    /**
     * Duration of the missed-catch fade, from the scheduled catch until the flight ends with a puff.
     */
    static final ClockTicks MISSED_CATCH_FADE = ClockTicks.of(4);

    private static final int PUFF_PARTICLE_COUNT = 3;
    private static final double PUFF_PARTICLE_SPREAD = 0.05;
    private static final double PUFF_PARTICLE_SPEED = 0.02;

    private final int giverEntityId;
    private final int receiverEntityId;
    private final ItemStack stack;
    private final int flightTicks;
    private final Vector3d launchPoint = new Vector3d();
    private final Vector3d predictedCatchPoint = new Vector3d();

    private boolean launched;
    private boolean missed;
    private long elapsedTicks;

    /**
     * Creates an unlaunched flight.
     *
     * @param stack       a non-empty stack the flight takes ownership of
     * @param flightTicks client ticks from launch to catch; must be at least 1
     */
    ItemFlight(int giverEntityId, int receiverEntityId, @Nonnull ItemStack stack, int flightTicks) {
        this.giverEntityId = giverEntityId;
        this.receiverEntityId = receiverEntityId;
        this.stack = stack;
        this.flightTicks = flightTicks;
    }

    /**
     * Returns the living BaseVillager with the given entity ID in level, or null if none matches.
     */
    @Nullable
    static BaseVillager resolvePresentVillager(@Nonnull ClientLevel level, int entityId) {
        return level.getEntity(entityId) instanceof BaseVillager villager && villager.isAlive() ? villager : null;
    }

    /**
     * Writes the villager's interpolated world position at hand height into destination.
     */
    static void locateHands(@Nonnull BaseVillager villager, float partialTick, @Nonnull Vector3d destination) {
        destination.set(
                Mth.lerp(partialTick, villager.xOld, villager.getX()),
                Mth.lerp(partialTick, villager.yOld, villager.getY()) + villager.getHandHeight(),
                Mth.lerp(partialTick, villager.zOld, villager.getZ()));
    }

    int giverEntityId() {
        return this.giverEntityId;
    }

    int receiverEntityId() {
        return this.receiverEntityId;
    }

    /**
     * The item in flight. Callers must not modify the stack.
     */
    ItemStack stack() {
        return this.stack;
    }

    /**
     * Client ticks from launch to catch.
     */
    int flightTicks() {
        return this.flightTicks;
    }

    /**
     * Returns the giver's hand position sampled at launch, in world coordinates.
     * Valid only after launch; callers must not modify the returned vector.
     */
    Vector3d launchPoint() {
        return this.launchPoint;
    }

    /**
     * Returns the receiver's hand position sampled at launch, in world coordinates.
     * Valid only after launch; callers must not modify the returned vector.
     */
    Vector3d predictedCatchPoint() {
        return this.predictedCatchPoint;
    }

    /**
     * Whether the item has left the giver's hands.
     */
    boolean launched() {
        return this.launched;
    }

    /**
     * Whether the receiver was absent or dead when the catch was due.
     */
    boolean missed() {
        return this.missed;
    }

    /**
     * Client ticks since launch, including the missed-catch fade; zero before launch.
     */
    long elapsedTicks() {
        return this.elapsedTicks;
    }

    /**
     * Advances one client tick, launching on the first call only if both villagers are present and alive.
     * A missed catch delays completion until its puff is spawned.
     * <p>
     * Call with the same level throughout the flight, and stop calling after this method returns true.
     *
     * @return true when the flight has ended or cannot launch; false while it remains active
     */
    boolean advanceOneTick(@Nonnull ClientLevel level) {
        if (!this.launched) {
            return !this.launch(level);
        }

        this.elapsedTicks++;
        // Check only at arrival so a receiver returning during the fade cannot catch the item retroactively.
        if (this.elapsedTicks == this.flightTicks) {
            if (resolvePresentVillager(level, this.receiverEntityId) != null) {
                return true;
            }
            this.missed = true;
        }
        if (this.missed && this.elapsedTicks == this.flightTicks + MISSED_CATCH_FADE.getTicks()) {
            this.puffAtPredictedCatch(level);
            return true;
        }
        return false;
    }

    /**
     * Launches the flight when both villagers are present and alive.
     *
     * @return whether the flight launched
     */
    private boolean launch(@Nonnull ClientLevel level) {
        BaseVillager giver = resolvePresentVillager(level, this.giverEntityId);
        BaseVillager receiver = resolvePresentVillager(level, this.receiverEntityId);
        if (giver == null || receiver == null) {
            return false;
        }

        // Sample at partial tick zero so the hand positions and flight progress share the same time origin.
        locateHands(giver, 0.0F, this.launchPoint);
        locateHands(receiver, 0.0F, this.predictedCatchPoint);
        this.launched = true;
        return true;
    }

    /**
     * Spawns a puff around the predicted catch point.
     */
    private void puffAtPredictedCatch(@Nonnull ClientLevel level) {
        RandomSource random = level.getRandom();
        // Randomize each particle's position and velocity to match the spread of a puff broadcast by the server.
        for (int i = 0; i < PUFF_PARTICLE_COUNT; i++) {
            level.addParticle(ParticleTypes.POOF,
                    this.predictedCatchPoint.x + random.nextGaussian() * PUFF_PARTICLE_SPREAD,
                    this.predictedCatchPoint.y + random.nextGaussian() * PUFF_PARTICLE_SPREAD,
                    this.predictedCatchPoint.z + random.nextGaussian() * PUFF_PARTICLE_SPREAD,
                    random.nextGaussian() * PUFF_PARTICLE_SPEED,
                    random.nextGaussian() * PUFF_PARTICLE_SPEED,
                    random.nextGaussian() * PUFF_PARTICLE_SPEED);
        }
    }

}
