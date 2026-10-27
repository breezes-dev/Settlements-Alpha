package dev.breezes.settlements.infrastructure.rendering.raid;

import dev.breezes.settlements.di.ClientScope;
import dev.breezes.settlements.di.ClientSessionResettable;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Holds next-wave markers only for the current client level, each expiring a fixed number of running client ticks
 * after its last update.
 */
@ClientSide
@ClientScope
@NoArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RaidWaveBeamStore implements ClientSessionResettable {

    /**
     * How long a marker lasts after its last update while the client simulation runs.
     */
    private static final ClockTicks BEAM_LIFETIME = ClockTicks.seconds(20);

    private final Map<Integer, Beam> beams = new HashMap<>();

    @Nullable
    private ClientLevel level;

    /**
     * Shows a raid's marker for a full lifetime, or clears it, ignoring updates for a dimension the client is not in.
     */
    public void update(@Nonnull ResourceKey<Level> dimension, int raidId, @Nullable BlockPos position) {
        ClientLevel current = Minecraft.getInstance().level;
        this.discardOtherLevel(current);
        if (current == null || !current.dimension().equals(dimension)) {
            return;
        }

        if (position == null) {
            this.beams.remove(raidId);
        } else {
            this.beams.put(raidId, new Beam(position.immutable()));
            this.level = current;
        }
        if (this.beams.isEmpty()) {
            this.level = null;
        }
    }

    /**
     * Ages markers while the simulation runs and drops markers from a departed level.
     */
    public void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        this.discardOtherLevel(minecraft.level);
        ClientLevel current = this.level;
        if (current == null || minecraft.isPaused() || !current.tickRateManager().runsNormally()) {
            return;
        }

        this.beams.values().removeIf(beam -> --beam.remainingTicks <= 0);
        if (this.beams.isEmpty()) {
            this.level = null;
        }
    }

    @Override
    public void onClientSessionEnded() {
        this.beams.clear();
        this.level = null;
    }

    boolean hasBeams(@Nonnull ClientLevel current) {
        this.discardOtherLevel(current);
        return !this.beams.isEmpty();
    }

    void forEachPosition(@Nonnull Consumer<BlockPos> action) {
        this.beams.values().forEach(beam -> action.accept(beam.position));
    }

    private void discardOtherLevel(@Nullable ClientLevel current) {
        if (current != this.level) {
            // Raid ids repeat across levels, so a level change invalidates every retained id.
            this.onClientSessionEnded();
        }
    }

    private static final class Beam {

        private final BlockPos position;
        private long remainingTicks = BEAM_LIFETIME.getTicks();

        private Beam(@Nonnull BlockPos position) {
            this.position = position;
        }

    }

}
