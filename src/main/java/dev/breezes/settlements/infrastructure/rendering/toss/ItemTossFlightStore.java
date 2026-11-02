package dev.breezes.settlements.infrastructure.rendering.toss;

import dev.breezes.settlements.di.ClientScope;
import dev.breezes.settlements.di.ClientSessionResettable;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds visual item tosses for a single client level.
 */
@ClientSide
@ClientScope
@NoArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class ItemTossFlightStore implements ClientSessionResettable {

    private final List<ItemFlight> flights = new ArrayList<>();
    private final List<ItemFlight> readOnlyFlights = Collections.unmodifiableList(this.flights);

    /**
     * The level shared by all stored flights, or null when the store is empty.
     */
    @Nullable
    private ClientLevel level;

    /**
     * Queues a toss in the current level if both villagers are present and alive.
     *
     * @param stack       a non-empty stack the store takes ownership of
     * @param flightTicks client ticks from launch to catch; must be at least 1
     */
    public void start(int giverEntityId, int receiverEntityId, @Nonnull ItemStack stack, int flightTicks) {
        ClientLevel current = Minecraft.getInstance().level;
        this.discardOtherLevel(current);
        if (current == null
                || ItemFlight.resolvePresentVillager(current, giverEntityId) == null
                || ItemFlight.resolvePresentVillager(current, receiverEntityId) == null) {
            return;
        }

        this.flights.add(new ItemFlight(giverEntityId, receiverEntityId, stack, flightTicks));
        this.level = current;
    }

    /**
     * Advances flights by one client tick while the simulation runs, retires completed flights, and discards flights
     * from a departed level.
     */
    public void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        this.discardOtherLevel(minecraft.level);
        ClientLevel current = this.level;
        // Keep tosses synchronized with villager motion during pauses and tick freezes.
        if (current == null || minecraft.isPaused() || !current.tickRateManager().runsNormally()) {
            return;
        }

        this.flights.removeIf(flight -> flight.advanceOneTick(current));
        if (this.flights.isEmpty()) {
            this.level = null;
        }
    }

    /**
     * Returns a live, unmodifiable view of the flights in the given level, in start order. Flights from another level
     * are discarded before returning the view.
     */
    List<ItemFlight> flightsIn(@Nonnull ClientLevel current) {
        this.discardOtherLevel(current);
        return this.readOnlyFlights;
    }

    @Override
    public void onClientSessionEnded() {
        this.flights.clear();
        this.level = null;
    }

    private void discardOtherLevel(@Nullable ClientLevel current) {
        if (current != this.level) {
            // Entity IDs repeat across levels, so a level change invalidates every flight.
            this.onClientSessionEnded();
        }
    }

}
